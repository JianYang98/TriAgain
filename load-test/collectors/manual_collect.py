#!/usr/bin/env python3
"""Manual Linux collector control. All timestamps and run paths are owned here.

진입점 3개 (collect 셸 스크립트가 인자를 그대로 넘긴다):
- on       : RUN 폴더·run.json·active.json을 만들고 수집 워커를 띄운 뒤 바로 끝난다.
- off      : active.json이 가리키는 RUN의 워커를 멈추고 OFF 시점 자료(nstat-after·JFR·app.log)를 저장한다.
- __worker : on이 수집 항목마다 띄우는 내부 서브커맨드. 사용자가 직접 실행하지 않는다.
전체 흐름과 상태 파일 관계는 MANUAL-COLLECT.md의 "코드 구조" 절에 있다.
"""
import argparse
import datetime
import fcntl
import json
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

from manual_collectors import COLLECTORS, LIFECYCLE_COLLECTORS
from manual_diagnostics import (capture_limits, command, same_container,
                                start_jfr, stop_jfr, run_tcpdump)
from manual_network import (collect_app_log, run_nstat, run_ss, snapshot_nstat)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
STATE = ROOT / ".collector-state"
KST = datetime.timezone(datetime.timedelta(hours=9))


def now():
    return datetime.datetime.now(KST).isoformat(timespec="milliseconds")


def save(path, value):
    # 임시 파일에 다 쓴 뒤 replace로 바꿔 끼운다. 다른 프로세스(ON의 ready 대기, 워커의 run.json 읽기,
    # OFF)가 반쯤 쓰인 JSON을 읽지 않는다.
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def read(path):
    return json.loads(path.read_text())


def process(pid):
    """Read Linux process identity including boot-relative start ticks."""
    try:
        stat = Path("/proc/%d/stat" % pid).read_text()
        # comm(2번째 필드)에 공백·괄호가 들어갈 수 있어 마지막 ")" 뒤부터 나눈다.
        # fields[0]=state(3번째 필드), [2]=pgrp(5번째), [19]=starttime(22번째, 부팅 후 clock tick).
        # starttime은 같은 PID가 다른 프로세스에 재사용되면 달라지므로 pid와 함께 신원 확인에 쓴다.
        fields = stat[stat.rindex(")") + 2:].split()
        return {"state": fields[0], "group": int(fields[2]), "ticks": fields[19]}
    except (OSError, ValueError, IndexError):
        return None


def group_alive(pid):
    """pid를 프로세스 그룹 ID로 쓰는 살아 있는(좀비 아닌) 프로세스가 하나라도 있으면 True.

    워커 자신만이 아니라 워커가 띄운 측정 명령·sudo 자식까지 그룹 전체가 끝났는지 보려고
    /proc 전체를 훑는다.
    """
    for entry in Path("/proc").iterdir():
        if entry.name.isdigit():
            info = process(int(entry.name))
            if info and info["group"] == pid and info["state"] != "Z":
                return True
    return False


def stop_workers(folder, run):
    """run.json에 기록된 워커를 하나씩 멈추고 항목별 outcome과 run state를 저장한다.

    항목을 순서대로 처리하며(병렬 아님) 항목마다 최대 약 10초를 기다린다.
    1. 워커가 살아 있으면 신원을 확인한다. 시작 ticks가 ON 때 기록과 같고, 그룹 리더이며
       (group == pid), cmdline에 이 파일 경로와 "__worker"가 있어야 한다. 하나라도 다르면 신호를
       보내지 않고 identity-mismatch로 둔다. PID가 재사용된 경우 남의 그룹에 신호를 보내지 않는다.
    2. 그룹 전체에 SIGTERM을 보낸다. privileged 항목의 그룹에는 `sudo -n`으로 띄운 root 소유
       자식이 있고, 일반 사용자 권한의 killpg는 root 프로세스에 닿지 않으므로 `sudo -n kill`을 쓴다.
    3. 그룹이 사라질 때까지 0.1초 간격으로 최대 10초 기다린 뒤 워커가 남긴 <name>.status.json을 읽는다.

    이미 끝난 워커는 신호 없이 3번만 한다. outcome은 still-running(그룹 잔존) → status의 state →
    status 파일 없음이면 ended-unexpectedly 순으로 정한다. 워커가 최종 status를 쓰지 못하고
    죽으면(SIGKILL·재부팅 등) 시작 때 쓴 "running"이 그대로 outcome이 된다.
    identity-mismatch·still-running·stop-failed가 하나라도 있으면 run state는 stop-incomplete,
    아니면 stopped다. 반환값은 "모든 워커 정지 확인" 여부다.
    """
    outcomes = {}
    for name, identity in run["workers"].items():
        pid = identity["pid"]
        current = process(pid)
        if current and current["state"] != "Z":
            cmdline = Path("/proc/%d/cmdline" % pid).read_bytes().split(b"\0")
            expected = str(HERE / "manual_collect.py").encode()
            if (current["ticks"] != identity["ticks"] or current["group"] != pid
                    or expected not in cmdline or b"__worker" not in cmdline):
                outcomes[name] = "identity-mismatch"
                continue
            try:
                if COLLECTORS[name].get("privileged"):
                    command(["sudo", "-n", "kill", "-TERM", "--", "-" + str(pid)], timeout=5)
                else:
                    os.killpg(pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
                outcomes[name] = "stop-failed"
                run.setdefault("errors", {})[name + "-stop"] = str(exc)
                continue
        deadline = time.monotonic() + 10
        while group_alive(pid) and time.monotonic() < deadline:
            time.sleep(0.1)
        status_file = folder / (name + ".status.json")
        status = read(status_file) if status_file.exists() else {}
        outcomes[name] = ("still-running" if group_alive(pid) else
                          "stopped" if status.get("state") == "stopped" else
                          status.get("state", "ended-unexpectedly"))
    run["outcomes"] = outcomes
    run["finished_at"] = now()
    run["state"] = ("stop-incomplete" if any(
        result in ("identity-mismatch", "still-running", "stop-failed") for result in outcomes.values())
        else "stopped")
    save(folder / "run.json", run)
    return run["state"] == "stopped"


def on(args):
    """수집을 시작한다. 워커를 띄운 뒤 기다리지 않고 반환한다(main()의 락을 쥔 채 실행).

    순서
    1. 입력 검증: Linux(/proc), RUN_ID, 활성 RUN 없음(active.json), --only 이름, 옵션 범위.
    2. 한국 시각 started_at의 날짜로 test-results/<날짜>/<RUN_ID>/를 만든다(이미 있으면 실패).
    3. run.json(state=starting)과 active.json을 기록한다. 이 시점부터 이 RUN은 OFF 대상이다.
    4. 컨테이너가 필요한 항목이 있으면 docker inspect로 ID·PID·StartedAt과 그 PID의 시작 ticks를
       기록한다. 워커와 OFF는 이 값과 비교해 컨테이너 종료·재시작을 감지한다.
    5. ON 시점 자료: network-limits.txt, nstat-before 스냅샷, JFR 시작.
    6. COLLECTORS 항목마다 launch_worker. lifecycle 항목(jfr·app-log)은 워커를 만들지 않는다.
    7. state를 running(오류 없음) 또는 running-partial로 저장한다.

    실패 처리
    - 5·6번의 항목별 실패는 run["errors"]에 적고 나머지를 계속 시작한다(반환코드 2).
      이미 시작된 워커는 계속 돌므로 OFF로 마무리해야 한다.
    - 그 밖의 예외(4번 docker inspect 실패·컨테이너 미실행, KeyboardInterrupt 등)는 롤백 후 다시 던진다.
      롤백은 띄운 워커 그룹에 SIGTERM → stop_workers로 정지 확인 → 정지가 확인되고 run["jfr"]가
      없을 때만 active.json 삭제 순이다. run.json에는 이 예외 내용이 기록되지 않는다(stderr 출력만).
    """
    if not Path("/proc/self/stat").exists():
        raise ValueError("앱 EC2의 Linux에서 실행하세요.")
    run_id = args.run_id
    if (run_id in (".", "..") or not run_id or "/" in run_id or "\\" in run_id
            or any(ord(char) < 32 or ord(char) == 127 for char in run_id)):
        raise ValueError("RUN_ID는 폴더 이름 한 칸으로 지정하세요.")
    active = STATE / "active.json"
    if active.exists():
        raise ValueError("활성 RUN이 있습니다. collect off로 먼저 종료하세요.")
    selected = args.only.split(",")
    if len(set(selected)) != len(selected) or any(
            name not in (*COLLECTORS, *LIFECYCLE_COLLECTORS) for name in selected):
        raise ValueError("--only에 알 수 없거나 중복된 수집 항목이 있습니다.")
    options = {key: getattr(args, key) for key in (
        "port", "ss_interval", "nstat_interval", "snaplen", "peer_ip",
        "pcap_max_mb", "jcmd", "jvm_pid", "jfr_settings", "jfr_max_mb")}
    if not 1 <= options["port"] <= 65535:
        raise ValueError("port 범위는 1..65535입니다.")
    if any(not math.isfinite(options[key]) or options[key] <= 0
           for key in ("ss_interval", "nstat_interval")):
        raise ValueError("수집 간격은 유한한 양수여야 합니다.")
    if any(options[key] <= 0 for key in ("snaplen", "pcap_max_mb", "jvm_pid", "jfr_max_mb")):
        raise ValueError("캡처·JFR 크기와 JVM PID는 양수여야 합니다.")
    started_at = now()
    date = datetime.datetime.fromisoformat(started_at).strftime("%Y%m%d")
    folder = ROOT / "test-results" / date / run_id
    folder.mkdir(parents=True, exist_ok=False, mode=0o700)
    run = {"run_id": run_id, "container": args.container, "started_at": started_at,
           "date": date, "output": str(folder), "state": "starting", "workers": {},
           "selected": selected, "options": options, "errors": {}, "outcomes": {}}
    save(folder / "run.json", run)
    save(active, {"output": str(folder)})
    # Retain process handles until startup completes so rollback also handles
    # a worker whose ready status has not appeared yet.
    launched = {}
    try:
        # (4) 여기서 나는 예외는 항목별로 잡지 않으므로 아래 except의 롤백으로 간다.
        if any(name in selected for name in ("ss", "nstat", "tcpdump", "jfr", "app-log")):
            info = json.loads(command(["docker", "inspect", args.container]))[0]
            if not info["State"]["Running"]:
                raise ValueError("대상 앱 컨테이너가 실행 중이어야 합니다.")
            run.update(container_id=info["Id"], container_pid=info["State"]["Pid"],
                       container_started_at=info["State"]["StartedAt"],
                       container_ticks=process(info["State"]["Pid"])["ticks"])
            save(folder / "run.json", run)
        # (5)·(6) 항목별 try: 실패는 errors에 남기고 다음 항목으로 넘어간다.
        if any(name in selected for name in ("ss", "nstat", "tcpdump")):
            try:
                capture_limits(folder, run, now)
            except Exception as exc:
                run["errors"]["network-limits"] = str(exc)
        if "nstat" in selected:
            try:
                snapshot = snapshot_nstat(folder, run, "before", now)
                if any(snapshot[scope]["returncode"] for scope in ("host", "container")):
                    run["errors"]["nstat-before"] = "일부 namespace의 시작 스냅샷 실패"
            except Exception as exc:
                run["errors"]["nstat-before"] = str(exc)
        if "jfr" in selected:
            try:
                start_jfr(run, lambda: save(folder / "run.json", run))
            except Exception as exc:
                run["errors"]["jfr-start"] = str(exc)
        save(folder / "run.json", run)
        for name in selected:
            if name not in COLLECTORS:
                continue
            try:
                launch_worker(name, folder, run, launched)
            except Exception as exc:
                run["errors"][name] = str(exc)
                save(folder / "run.json", run)
        run["state"] = "running" if not run["errors"] else "running-partial"
        save(folder / "run.json", run)
    except BaseException:
        for child in launched.values():
            if child.poll() is None:
                try:
                    name = next(name for name, value in launched.items() if value is child)
                    if COLLECTORS[name].get("privileged"):
                        command(["sudo", "-n", "kill", "-TERM", "--", "-" + str(child.pid)], timeout=5)
                    else:
                        os.killpg(child.pid, signal.SIGTERM)
                except (OSError, RuntimeError):
                    pass
        # run["jfr"]가 있으면(시작됐거나 시작 여부가 불확실하면) active.json을 남겨
        # OFF가 같은 recording 이름으로 JFR.stop을 시도하게 한다.
        if stop_workers(folder, run) and not run.get("jfr"):
            active.unlink()
        raise
    print("수집기 ON · " + run_id)
    print("수집 항목 · " + ", ".join(selected))
    for name, error in run["errors"].items():
        print("FAIL " + name + " · " + error)
    print("시작 시각 · " + started_at)
    print("결과 폴더 · " + str(folder))
    return 2 if run["errors"] else 0


def launch_worker(name, folder, run, launched):
    """워커 프로세스 하나를 띄우고 <name>.status.json이 running이 될 때까지 최대 5초 기다린다.

    - 사전 확인: command 방식은 명령이 PATH에 있는지, privileged는 `sudo -n true`(비밀번호 없이 sudo 가능).
    - start_new_session=True라 워커가 새 세션·프로세스 그룹의 리더가 된다(pgid = 워커 pid).
      OFF는 이 그룹 단위로 신호를 보낸다. 제어 터미널이 없어 SSH 접속이 끊겨도 SIGHUP을 받지 않는다.
    - stdout·stderr는 <name>.worker.log로 간다. "xb" 모드라 같은 항목을 같은 폴더에 두 번 시작하지 못한다.
    - pid·시작 ticks는 ready 대기 전에 run.json에 저장한다. 그래서 시작 확인이 실패해도 프로세스가
      살아 있으면 OFF가 정지 대상으로 삼는다. 이 함수는 실패해도 띄운 프로세스를 죽이지 않는다.
    """
    spec = COLLECTORS[name]
    if spec.get("command") and shutil.which(spec["command"][0]) is None:
        raise RuntimeError("필요한 명령이 없습니다: " + spec["command"][0])
    if spec.get("privileged"):
        command(["sudo", "-n", "true"], timeout=5)
    # Worker reads the immutable options/target from this run's manifest.
    save(folder / "run.json", run)
    with (folder / (name + ".worker.log")).open("xb") as errors:
        child = subprocess.Popen(
            [sys.executable, str(HERE / "manual_collect.py"), "__worker",
             "--name", name, "--run-id", run["run_id"], "--folder", str(folder)],
            stdin=subprocess.DEVNULL, stdout=errors, stderr=errors,
            start_new_session=True, close_fds=True)
    launched[name] = child
    info = process(child.pid)
    if info is None:
        raise RuntimeError(name + " 프로세스 시작 확인 실패")
    run["workers"][name] = {"pid": child.pid, "ticks": info["ticks"]}
    save(folder / "run.json", run)
    ready = folder / (name + ".status.json")
    deadline = time.monotonic() + 5
    while not ready.exists() and child.poll() is None and time.monotonic() < deadline:
        time.sleep(0.05)
    if not ready.exists() or read(ready).get("state") != "running" or child.poll() is not None:
        raise RuntimeError(name + " 시작 실패. worker 로그를 확인하세요.")


def off():
    """활성 RUN을 끝낸다. 실패한 단계가 있으면 active.json을 남겨 같은 명령으로 다시 시도할 수 있다.

    순서
    1. off_requested_at 기록. setdefault라 재시도해도 첫 OFF 시각이 유지된다(app.log 구간의 끝).
    2. 워커를 멈추기 전에 nstat-after 스냅샷과 tcp-delta.txt를 만든다(파일이 이미 있으면 건너뜀).
    3. stop_workers: 워커를 하나씩 멈추고 outcome을 모은다.
    4. JFR 정지·복사(saved가 아닐 때), app.log 추출(started_at~off_requested_at, 이미 있으면 exists).

    최종 state
    - stop-incomplete: 워커 정지 미확인, 또는 JFR·app-log 저장 실패. active.json 유지 → OFF 재시도 가능.
    - stopped: errors가 없고 모든 outcome이 stopped·saved·exists.
    - stopped-with-errors: 그 밖(capture-limit-reached·failed·nstat 오류 등). active.json은 지운다.
    반환코드는 stopped일 때만 0, 나머지는 2.

    주의: errors는 run.json에 누적되고 재시도 때 지우지 않는다. 앞선 시도의 오류가 남아 있으면
    재시도가 성공해도 stopped-with-errors가 된다. nstat·tcp-delta 오류는 active.json 유지 조건이 아니다.
    """
    active = STATE / "active.json"
    if not active.exists():
        print("활성 수집 RUN이 없습니다.")
        return 0
    folder = Path(read(active)["output"])
    run = read(folder / "run.json")
    run.setdefault("off_requested_at", now())
    save(folder / "run.json", run)
    selected = run.get("selected", list(run["workers"]))
    if "nstat" in selected and not (folder / "nstat-after.json").exists():
        try:
            same_container(run)
            snapshot = snapshot_nstat(folder, run, "after", now)
            if any(snapshot[scope]["returncode"] for scope in ("host", "container")):
                run.setdefault("errors", {})["nstat-after"] = "일부 namespace의 종료 스냅샷 실패"
        except Exception as exc:
            run.setdefault("errors", {})["nstat-after"] = str(exc)
    if "nstat" in selected and not (folder / "tcp-delta.txt").exists():
        try:
            write_delta(folder, run)
        except Exception as exc:
            run.setdefault("errors", {})["tcp-delta"] = str(exc)
    complete = stop_workers(folder, run)
    # lifecycle_ok: JFR·app-log 저장 성공 여부. complete와 함께 active.json 삭제 조건이다.
    lifecycle_ok = True
    if run.get("jfr") and run["jfr"]["state"] != "saved":
        try:
            stop_jfr(folder, run)
            run["outcomes"]["jfr"] = "saved"
        except Exception as exc:
            run["outcomes"]["jfr"] = "failed"
            run.setdefault("errors", {})["jfr-stop"] = str(exc)
            lifecycle_ok = False
    elif "jfr" in selected:
        run["outcomes"]["jfr"] = "saved" if run.get("jfr") else "unavailable"
    if "app-log" in selected:
        try:
            result = collect_app_log(folder, run)
            run["outcomes"]["app-log"] = result["state"]
            lifecycle_ok = lifecycle_ok and result["state"] in ("saved", "exists")
            if result["state"] not in ("saved", "exists"):
                run.setdefault("errors", {})["app-log"] = result
        except Exception as exc:
            run["outcomes"]["app-log"] = "failed"
            run.setdefault("errors", {})["app-log"] = str(exc)
            lifecycle_ok = False
    successful = not run.get("errors") and all(
        value in ("stopped", "saved", "exists") for value in run["outcomes"].values())
    run["state"] = ("stop-incomplete" if not complete or not lifecycle_ok else
                    "stopped" if successful else "stopped-with-errors")
    run["finished_at"] = now()
    save(folder / "run.json", run)
    if complete and lifecycle_ok:
        active.unlink()
    print("수집기 OFF · " + run["run_id"])
    for name, outcome in run["outcomes"].items():
        print(name + " · " + outcome)
    print("종료 시각 · " + run["finished_at"])
    print("결과 폴더 · " + str(folder))
    for name, error in run.get("errors", {}).items():
        print("FAIL " + name + " · " + str(error))
    return 0 if complete and lifecycle_ok and not run.get("errors") and all(
        x in ("stopped", "saved", "exists") for x in run["outcomes"].values()) else 2


def write_delta(folder, run):
    """Preserve the whole counter inventory; missing/reset values stay explicit."""
    def counters(path):
        values = {}
        for line in path.read_text().splitlines():
            fields = line.split()
            if len(fields) >= 2 and not fields[0].startswith("#"):
                try:
                    values[fields[0]] = int(fields[1])
                except ValueError:
                    pass
        return values

    with (folder / "tcp-delta.txt").open("x") as stream:
        stream.write(now() + " " + run["run_id"] + "\n")
        for prefix in ("nstat", "nstat-host"):
            before = counters(folder / (prefix + "-before.txt"))
            after = counters(folder / (prefix + "-after.txt"))
            stream.write(prefix + " name before after delta\n")
            for key in sorted(before.keys() | after.keys()):
                first, last = before.get(key), after.get(key)
                delta = ("MISSING" if first is None or last is None else
                         "RESET" if last < first else str(last - first))
                stream.write("%s %s %s %s\n" % (key, first, last, delta))


def worker(args):
    """`__worker` 서브커맨드 본체. 수집 항목 하나를 OFF의 SIGTERM이 올 때까지 실행한다.

    SIGTERM·SIGINT 핸들러는 stopping 플래그만 세운다. 워커는 바로 죽지 않고 수집 루프나 명령 출력이
    끝난 뒤 최종 status를 쓰고 끝난다. OFF는 그룹 전체에 신호를 보내므로 측정 명령도 같은 신호를 받는다.

    두 경로
    - kind 방식(ss·nstat·tcpdump): run.json을 읽고 same_container로 컨테이너가 ON 때와 같은지 확인한 뒤
      status=running을 쓰고, kind에 맞는 run_* 함수에 stopped 콜백을 넘겨 반복 수집을 맡긴다.
    - command 방식(mpstat·vmstat): 측정 명령의 표준 출력을 로그 파일에 직접 연결한다.

    status.json state: running → stopped(종료 요청에 의한 종료) | ended-unexpectedly(요청 없이 끝남,
    command 방식은 exit 코드가 0·-SIGTERM·-SIGINT 밖) | capture-limit-reached(tcpdump 크기 한도) |
    failed(예외). 예외는 failed를 기록한 뒤 다시 던져 traceback이 <name>.worker.log에 남는다.
    """
    folder = Path(args.folder)
    spec = COLLECTORS[args.name]
    status_file = folder / (args.name + ".status.json")
    stopping = False

    def request_stop(signum, frame):
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)
    kind = spec.get("kind")
    if kind:
        try:
            run = read(folder / "run.json")
            same_container(run)
            # ON의 launch_worker가 이 running을 최대 5초 기다린다. 컨테이너 확인을 통과한 뒤에만 쓴다.
            save(status_file, {"state": "running", "started_at": now(), "pid": os.getpid()})
            functions = {"ss": run_ss, "nstat": run_nstat, "tcpdump": run_tcpdump}
            result = functions[kind](folder, run, now, lambda: stopping)
            state = "stopped" if stopping else "ended-unexpectedly"
            # run_tcpdump는 한도 도달 시 자기 프로세스 그룹(이 워커 포함)에 SIGTERM을 보내므로
            # stopping도 True가 된다. 그 경우 한도 도달을 우선 기록한다.
            if result and result.get("limited"):
                state = "capture-limit-reached"
            save(status_file, {"state": state, "ended_at": now(), "detail": result})
        except BaseException as exc:
            save(status_file, {"state": "failed", "ended_at": now(), "error": str(exc)})
            raise
        return
    env = dict(os.environ, LC_ALL="C", TZ="Asia/Seoul")
    command = list(spec["command"])
    # 수집 중 tail -f로 표본을 볼 수 있도록 줄 단위 출력을 사용한다.
    if shutil.which("stdbuf"):
        command = ["stdbuf", "-oL", *command]
    try:
        # 측정 결과를 저장할 로그 파일을 생성한다.
        with (folder / spec["filename"]).open("xb") as log:
            if stopping:
                raise RuntimeError("시작 중 종료 요청")

            # start_new_session 없이 띄워 워커와 같은 프로세스 그룹에 둔다.
            # 그래서 OFF의 그룹 SIGTERM이 측정 명령에도 직접 간다.
            child = subprocess.Popen(command, stdout=log, env=env)
            # 측정 시작 상태를 기록한다.
            save(status_file, {"state": "running", "started_at": now(), "pid": os.getpid()})
            code = child.wait()
            state = ("stopped" if stopping and code in (0, -signal.SIGTERM, -signal.SIGINT)
                     else "ended-unexpectedly")
        save(status_file, {"state": state, "ended_at": now(), "command_exit": code})
    except BaseException as exc:
        # 이 경로는 status에 예외 타입 이름만 남긴다(kind 경로는 메시지). 전체 traceback은 worker.log에 있다.
        save(status_file, {"state": "failed", "ended_at": now(), "error": type(exc).__name__})
        raise


def main():
    parser = argparse.ArgumentParser(description="앱 EC2 수집기 수동 ON/OFF")
    sub = parser.add_subparsers(dest="action", required=True)
    start = sub.add_parser("on")
    start.add_argument("--run-id", required=True)
    start.add_argument("--container", required=True,
                       help="관측할 앱 컨테이너 이름")
    start.add_argument("--only", default=",".join((*COLLECTORS, "app-log")),
                       help="쉼표로 구분한 항목 (기본 mpstat,vmstat,ss,nstat,tcpdump,app-log; JFR은 명시 선택)")
    start.add_argument("--port", type=int, default=8080)
    start.add_argument("--ss-interval", type=float, default=0.3)
    start.add_argument("--nstat-interval", type=float, default=1.0)
    start.add_argument("--snaplen", type=int, default=128)
    start.add_argument("--peer-ip", default="")
    start.add_argument("--pcap-max-mb", type=int, default=128,
                       help="PCAP 약정 크기 도달 시 캡처 종료 (마지막 버퍼만 초과 가능)")
    start.add_argument("--jcmd", default="jcmd", help="앱 컨테이너 안 jcmd 경로")
    start.add_argument("--jvm-pid", type=int, default=1)
    start.add_argument("--jfr-settings", default="default")
    start.add_argument("--jfr-max-mb", type=int, default=64)
    sub.add_parser("off")
    internal = sub.add_parser("__worker", help=argparse.SUPPRESS)
    internal.add_argument("--name", choices=COLLECTORS, required=True)
    internal.add_argument("--run-id", required=True)
    internal.add_argument("--folder", required=True)
    args = parser.parse_args()
    # 이후 만드는 파일·폴더는 소유자만 읽고 쓸 수 있다(워커 프로세스에도 상속).
    os.umask(0o077)
    # 워커는 락을 잡지 않는다. ON이 락을 쥔 채 워커의 status=running을 기다리므로
    # 워커가 같은 락을 기다리면 ON의 시작 확인이 시간 초과된다.
    if args.action == "__worker":
        worker(args)
        return 0
    STATE.mkdir(mode=0o700, exist_ok=True)
    with (STATE / "lock").open("a") as lock:
        # on/off를 한 번에 하나만 실행한다. LOCK_NB가 없어 앞선 실행이 끝날 때까지 기다린다(거절 아님).
        fcntl.flock(lock, fcntl.LOCK_EX)
        if args.action == "on":
            return on(args)
        return off()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, KeyboardInterrupt) as error:
        print("수집 오류 · " + str(error), file=sys.stderr)
        sys.exit(2)
