#!/usr/bin/env python3
"""부하 테스트 수집기의 ON/OFF와 워커 실행을 관리한다.

- on: 결과 폴더와 상태 파일을 만들고 수집을 시작한다.
- off: 실행 중인 수집을 정지하고 종료 시점 자료를 저장한다.
- __worker: on이 수집 항목별로 실행하는 내부 명령어다.

전체 실행 흐름과 파일 관계는 MANUAL-COLLECT.md를 참고한다.
"""

import argparse
import datetime
import fcntl
import json
import math
import os
from pathlib import Path
import shlex
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

# ID의 프로세스 상태, 그룹 ID, 시작 ticks를 조회
def process(pid):
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
    """
    해당 프로세스 그룹에 좀비가 아닌 프로세스가 남아 있는지 확인한다.
    워커뿐 아니라 워커가 실행한 측정 명령까지 확인한다.
    """
    for entry in Path("/proc").iterdir():
        if entry.name.isdigit():
            info = process(int(entry.name))
            if info and info["group"] == pid and info["state"] != "Z":
                return True
    return False


def stop_workers(folder, run):
    """워커 프로세스 그룹을 종료하고 결과를 run.json에 기록한다.

    1. PID, 시작 ticks, 그룹 ID, 실행 명령을 대조해 워커 신원을 확인한다.
       PID가 다른 프로세스에 재사용된 경우 잘못된 종료 신호를 보내지 않기 위해서다.
    2. 살아 있는 워커 그룹에 SIGTERM을 보낸다. 권한이 필요한 그룹은 sudo를 사용한다.
    3. 그룹 종료를 항목별 최대 10초 기다리고 status.json을 읽어 결과를 기록한다.

    신원 불일치·종료 실패·그룹 잔존이 있으면 stop-incomplete 상태로 남긴다.
    반환값은 모든 워커 그룹의 종료 확인 여부다.
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

# 수집 시작
def on(args):

    """선택한 수집 항목을 시작하고 RUN 상태를 기록한다.

    main()이 ON/OFF 락을 보유한 상태에서 실행한다.
    워커의 시작 상태를 확인한 뒤 반환하며, 시작된 워커는 백그라운드에서 계속 수집한다.

    실행 흐름
    1. 입력값과 기존 활성 RUN을 검증한다.
    2. 결과 폴더를 생성하고 run.json, active.json을 기록한다.
    3. 선택한 항목에 따라 컨테이너 정보를 확인하고 시작 시점의 자료를 수집한다.
    4. 수집 워커를 실행하고 시작 결과를 run.json에 기록한다.

    실패 처리
    - 개별 수집 항목의 실패는 errors에 기록하고 나머지 작업을 계속한다.
    - 처리되지 않은 예외가 발생하면 시작한 워커의 정리를 시도하고 예외를 다시 발생시킨다.
    - 정상적으로 반환할 경우, 오류 기록이 없으면 0, 있으면 2를 반환한다.
    - 시작된 수집은 collect off로 마무리한다.
    """

    if not Path("/proc/self/stat").exists():
        raise ValueError("앱 EC2의 Linux에서 실행하세요.")

    # RUN_ID가 추후에 폴더로 쓰이기 때문에 백슬레시와, 오타가 없어야한다.
    run_id = args.run_id
    if (run_id in (".", "..") or not run_id or "/" in run_id or "\\" in run_id
            or any(ord(char) < 32 or ord(char) == 127 for char in run_id)):
        raise ValueError("RUN_ID는 폴더 이름 한 칸으로 지정하세요.")


    # 이전 RUN을 종료하지 않으면, 새 수집을 하지 않습니다!!
    active = STATE / "active.json"
    if active.exists():
        raise ValueError("활성 RUN이 있습니다. collect off로 먼저 종료하세요.")

    # 선택한 수집 항목의 중복 및 유효성 검사
    selected = args.only.split(",")

    if len(set(selected)) != len(selected) or any(
            name not in (*COLLECTORS, *LIFECYCLE_COLLECTORS) for name in selected):
        raise ValueError("--only에 알 수 없거나 중복된 수집 항목이 있습니다.")

    #  수집 옵션 정리 및 값 검증
    # main 에서 정한 --port ~  등 option 값을 가져옵니다.
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

    #  RUN 시작 시각 기록 및 결과 폴더 생성
    started_at = now()
    date = datetime.datetime.fromisoformat(started_at).strftime("%Y%m%d")
    folder = ROOT / "test-results" / date / run_id
    folder.mkdir(parents=True, exist_ok=False, mode=0o700)
    run = {"run_id": run_id, "container": args.container, "started_at": started_at,
           "date": date, "output": str(folder), "state": "starting", "workers": {},
           "selected": selected, "options": options, "errors": {}, "outcomes": {}}

    # # RUN별 상세 정보는 결과 폴더의 run.json에 기록
    save(folder / "run.json", run)

    # OFF가 이 RUN을 찾을 수 있도록 결과 폴더 경로를 active.json에 저장한다. !!
    save(active, {"output": str(folder)})

    # 시작한 워커 프로세스를 기록
    launched = {}
    try:
        # 선택한 수집 항목 중 컨테이너가 필요한 항목이 있으면  실행 중인지 확인한다.
        if any(name in selected for name in ("ss", "nstat", "tcpdump", "jfr", "app-log")):
            info = json.loads(command(["docker", "inspect", args.container]))[0]
            if not info["State"]["Running"]:
                raise ValueError("대상 앱 컨테이너가 실행 중이어야 합니다.")

            # 컨테이너 ID와 프로세스 식별 정보를 run에 업데이트하고, run.json으로 저장한다.
            run.update(container_id=info["Id"], container_pid=info["State"]["Pid"],
                       container_started_at=info["State"]["StartedAt"],
                       container_ticks=process(info["State"]["Pid"])["ticks"])
            save(folder / "run.json", run)

        # 네트워크 기록
        if any(name in selected for name in ("ss", "nstat", "tcpdump")):
            try:
                capture_limits(folder, run, now)
            except Exception as exc:
                run["errors"]["network-limits"] = str(exc)

        # nstat  시작 값 기록
        if "nstat" in selected:
            try:
                snapshot = snapshot_nstat(folder, run, "before", now)
                if any(snapshot[scope]["returncode"] for scope in ("host", "container")):
                    run["errors"]["nstat-before"] = "일부 namespace의 시작 스냅샷 실패"
            except Exception as exc:
                run["errors"]["nstat-before"] = str(exc)

        # jfr 캡쳐
        if "jfr" in selected:
            try:
                start_jfr(run, lambda: save(folder / "run.json", run))
            except Exception as exc:
                run["errors"]["jfr-start"] = str(exc)
        save(folder / "run.json", run)


        # 워커 실행하고, 오류나면 기록
        for name in selected:
            if name not in COLLECTORS:
                continue
            try:
                launch_worker(name, folder, run, launched)
            except Exception as exc:
                run["errors"][name] = str(exc)
                save(folder / "run.json", run)

        # 수집 과정에 오류 있는지 없는지 기록
        run["state"] = "running" if not run["errors"] else "running-partial"

        save(folder / "run.json", run)

    # 시작 실패 시 이미 실행 중인 워커 프로세스 그룹에 종료를 요청한다.
    except BaseException:
        for child in launched.values():
            if child.poll() is None:
                try:
                    name = next(name for name, value in launched.items() if value is child)
                    if COLLECTORS[name].get("privileged"): # 관리자 권한이 필요하다면
                        command(["sudo", "-n", "kill", "-TERM", "--", "-" + str(child.pid)], timeout=5)
                    else: # 관리자 권이 필요하지 않는경우
                        os.killpg(child.pid, signal.SIGTERM)
                except (OSError, RuntimeError):
                    pass

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


# 워커  프로세스를 실행
def launch_worker(name, folder, run, launched):
    """ 선택한 수집 항목을 별도의 Python 워커 프로세스로 실행한다.

    실행 흐름
    1. 워커 실행에 필요한 명령어와 권한을 확인한다.
    2. run.json을 저장하고, 워커 로그 파일을 생성한 뒤 새 프로세스를 실행한다.
    3. 워커의 PID와 시작 ticks를 run.json에 기록한다.
    4. 워커의 status.json이 running 상태인지 최대 5초 동안 확인한다.

    실패 처리
    - 실패하면 예외를 발생시키며, 여기서 워커를 직접 종료하지는 않는다.
    - 정리는 on()의 오류 처리 또는 off에서 수행한다.

    """

    spec = COLLECTORS[name]

    #  워커에 대한 필요한 명령어가 Path에 있는지 확인
    if spec.get("command") and shutil.which(spec["command"][0]) is None:
        raise RuntimeError("필요한 명령이 없습니다: " + spec["command"][0])
    # 권한이 필요한 항목은 sudo -n true가 비밀번호 입력 없이 실행되는지 확인한다.
    if spec.get("privileged"):
        command(["sudo", "-n", "true"], timeout=5)

    #  워커가 실행 설정과 수집 대상을 읽을 수 있도록 run.json에 저장
    save(folder / "run.json", run)

    # manual_collect.py에서 별도의 Python 워커 프로세스 실행 및 로그 연결
    # start_new_session=True새 세션·프로세스 그룹으로 워커를 실행해 부모 터미널과 분리하여 백그라운드 수집하도록함
    with (folder / (name + ".worker.log")).open("xb") as errors:

        #  워커 실행
        # main 에서 정의한 내부 워커용 인자값도 같이 넘긴다.
        child = subprocess.Popen(
            [sys.executable, str(HERE / "manual_collect.py"), "__worker",
             "--name", name, "--run-id", run["run_id"], "--folder", str(folder)],
            stdin=subprocess.DEVNULL, stdout=errors, stderr=errors,
            start_new_session=True, close_fds=True)

    #  실행한 워커(child)를 부모 프로세스에 저장
    launched[name] = child

    # 새로 실행한 워커의 PID로 프로세스 조회
    info = process(child.pid)
    if info is None:
        raise RuntimeError(name + " 프로세스 시작 확인 실패")

    # 프로세스 OFF 할때 동일한 프로세스인지 확인 가능 하도록 pid랑 ticks(pid 생긴 tick 지점)을 저장
    run["workers"][name] = {"pid": child.pid, "ticks": info["ticks"]}
    # 변경된 RUN 정보를
    save(folder / "run.json", run)


    # 자식 워커가 생성할 상태 파일의 경로를 지정하고, 자식 워커가 바로 실행이 안될 수 있어서 5초까지 기다림
    # 자식 프로세스가 생성할 파일의 경로를 정한다. 생존 여부와 상태 파일을 확인해 워커 시작
    # 상태 파일이 아직 없다면 최대 5초 동안 대기한다.
    ready = folder / (name + ".status.json")
    deadline = time.monotonic() + 5

    # 자식이 프로세스가 생기면
    while not ready.exists() and child.poll() is None and time.monotonic() < deadline:
        time.sleep(0.05)
    if not ready.exists() or read(ready).get("state") != "running" or child.poll() is not None:
        raise RuntimeError(name + " 시작 실패. worker 로그를 확인하세요.")


def off():
    """활성 RUN을 끝낸다. 실패한 단계가 있으면 active.json을 남겨 같은 명령으로 다시 시도할 수 있다.

    순서
    1. 첫 OFF 요청 시각을 기록한다( (app.log 구간의 끝).
    2. 워커 종료 전에 nstat 종료 스냅샷과 TCP 카운터 차이를 기록한다.
    3. stop_workers: 워커를 하나씩 멈추고 outcome을 모은다.
    4. 결과를 run.json에 기록하고, 종료 처리가 완료되면 active.json을 지운다.

    최종 state
    - stop-incomplete: 워커 정지 미확인, 또는 JFR·app-log 저장 실패. active.json 유지 → OFF 재시도 가능.
    - stopped: errors가 없고 모든 outcome이 stopped·saved·exists.
    - stopped-with-errors: 그 밖(capture-limit-reached·failed·nstat 오류 등). active.json은 지운다.
    반환코드는 stopped일 때만 0, 나머지는 2.

    """
    #  현재 활성 RUN 확인
    active = STATE / "active.json"
    if not active.exists():
        print("활성 수집 RUN이 없습니다.")
        return 0
    folder = Path(read(active)["output"])
    run = read(folder / "run.json")
    run.setdefault("off_requested_at", now())
    save(folder / "run.json", run)

    # 현재 실행 중인 워커 기록하기
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

    # 워커 정지 처리
    complete = stop_workers(folder, run)

    # 워커 정지(complete)와 이 처리가 모두 끝나면 active.json을 삭제할 수 있다.
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

    # 종료 작업이 완료되지 않았다면 active.json을 남겨 OFF를 재시도할 수 있게 한다.
    # 워커 종료와 JFR·앱 로그 처리가 완료되면 active.json 삭제한다.
    if complete and lifecycle_ok:
        active.unlink()

    print("수집기 OFF · " + run["run_id"])
    for name, outcome in run["outcomes"].items():
        print(name + " · " + outcome)
    print("종료 시각 · " + run["finished_at"])
    print("결과 폴더 · " + str(folder))
    for name, error in run.get("errors", {}).items():
        print("FAIL " + name + " · " + str(error))
    if run["state"] == "stopped":
        print("\n[회수창 · Mac] 아래 세 줄을 붙여넣으세요.")
        print("APP_OUT=" + shlex.quote(str(folder)))
        print("export START_AT=" + shlex.quote(run["started_at"]))
        print("export END_AT=" + shlex.quote(run["finished_at"]))
        return 0
    return 2


# TODO: nstat 카운터 차이 계산과 예외 상황을 추가 검토한다.
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
    """ 각 수집 항목의 실제 수집 작업을 수행한다.

    워커는 데이터를 계속 수집하다 종료 신호(SIGTERM·SIGINT)를 받으면 stopping을 True로 바꾼다.
    수집이 끝나면 최종 상태를 <name>.status.json에 저장한다.

    - kind 방식(ss·nstat·tcpdump): 해당 run_* 함수에 수집과 종료 확인을 맡긴다.
    - command 방식(mpstat·vmstat): 측정 명령을 실행하고 출력 줄을 로그에 기록한다.

    두 경로
    - kind 방식(ss·nstat·tcpdump): run.json을 읽고 same_container로 컨테이너가 ON 때와 같은지 확인한 뒤
      status=running을 쓰고, kind에 맞는 run_* 함수에 stopped 콜백을 넘겨 반복 수집을 맡긴다.
    - command 방식(mpstat·vmstat): 측정 명령의 표준 출력을 로그 파일에 직접 연결한다.

    """
    # 수집기 이름과 프로세스, 상태 값 세팅
    folder = Path(args.folder)
    spec = COLLECTORS[args.name]
    status_file = folder / (args.name + ".status.json")
    stopping = False

    def request_stop(signum, frame):
        nonlocal stopping
        stopping = True

    # 종료 신호를 받으면 request_stop() 실행하도록 등록
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
            # stopping도 True가 된다. 그 경우 한도 도달을 우선 기록한다

            # 제한 확인
            if result and result.get("limited"):
                state = "capture-limit-reached"
            save(status_file, {"state": state, "ended_at": now(), "detail": result})

        # 실패가 나면 기록하고, 호출부로
        except BaseException as exc:
            save(status_file, {"state": "failed", "ended_at": now(), "error": str(exc)})
            raise  # 토스
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

# main 함수 중요 옵션 설정
def main():

    parser = argparse.ArgumentParser(description="앱 EC2 수집기 수동 ON/OFF")
    sub = parser.add_subparsers(dest="action", required=True)

    # ON 명령어 및 옵션 정의
    start = sub.add_parser("on")
    start.add_argument("--run-id", required=True)
    start.add_argument("--container", required=True,
                       help="관측할 앱 컨테이너 이름")
    start.add_argument("--only", default=",".join((*COLLECTORS, "app-log")),
                       help="쉼표로 구분한 항목 (기본 mpstat,vmstat,ss,nstat,tcpdump,app-log; JFR은 명시 선택)")
    # 수집 옵션 설정
    start.add_argument("--port", type=int, default=8080)
    start.add_argument("--ss-interval", type=float, default=0.3)
    start.add_argument("--nstat-interval", type=float, default=1.0)
    start.add_argument("--snaplen", type=int, default=128)
    start.add_argument("--peer-ip", default="")
    start.add_argument("--pcap-max-mb", type=int, default=128,
                       help="PCAP 약정 크기 도달 시 캡처 종료 (마지막 버퍼만 초과 가능)")
    # JFR 수집 옵션
    start.add_argument("--jcmd", default="jcmd", help="앱 컨테이너 안 jcmd 경로")
    start.add_argument("--jvm-pid", type=int, default=1)
    start.add_argument("--jfr-settings", default="default")
    start.add_argument("--jfr-max-mb", type=int, default=64)

    # OFF 명령어 및 옵션 정의
    sub.add_parser("off")

    # 내부 워커 명령어 정의 , launch_worker에서 프로세스 새로 띄울때 사용
    internal = sub.add_parser("__worker", help=argparse.SUPPRESS) # 명령어 등록
    internal.add_argument("--name", choices=COLLECTORS, required=True) # 옵션등록
    internal.add_argument("--run-id", required=True)
    internal.add_argument("--folder", required=True)


    args = parser.parse_args()

    # 파일 권한과 접근 제한
    os.umask(0o077)


    # 내부 워커는 락을 잡지 않고 바로 실행한다.
    # ON이 락을 잡은 채 워커의 status=running을 기다리기 때문이다.
    # 부모 프로세스가! 자식 프로세스 프로세스를 띄웠으면 args.action == "__worker" 이기에 worker 호출
    if args.action == "__worker":
        worker(args)
        return 0

    #  ON/OFF 상태 파일을 관리할 상태 관리 디렉터리를 준비(.collector-state 디렉토리 )
    #  lock 파일을 연다(없으면 만든다)
    STATE.mkdir(mode=0o700, exist_ok=True)
    with (STATE / "lock").open("a") as lock:
        # lock 파일에 락을 걸어서  on/off를 한 번에 하나만 실행하도록하다.
        # 배타락을 건다.
        fcntl.flock(lock, fcntl.LOCK_EX)
        if args.action == "on":
            return on(args)
        return off()


# Python 파일을 직접 실행하면 main()을 호출한다.
# 실행 중 예외가 발생하면 오류를 출력하고 종료한다.
if __name__ == "__main__":
    try:
        sys.exit(main()) # main() 시작
    except (OSError, ValueError, RuntimeError, KeyboardInterrupt) as error:
        print("수집 오류 · " + str(error), file=sys.stderr)
        sys.exit(2)
