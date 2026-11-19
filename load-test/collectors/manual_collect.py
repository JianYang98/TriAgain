#!/usr/bin/env python3
"""Manual Linux collector control. All timestamps and run paths are owned here."""
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
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def read(path):
    return json.loads(path.read_text())


def process(pid):
    """Read Linux process identity including boot-relative start ticks."""
    try:
        stat = Path("/proc/%d/stat" % pid).read_text()
        fields = stat[stat.rindex(")") + 2:].split()
        return {"state": fields[0], "group": int(fields[2]), "ticks": fields[19]}
    except (OSError, ValueError, IndexError):
        return None


def group_alive(pid):
    for entry in Path("/proc").iterdir():
        if entry.name.isdigit():
            info = process(int(entry.name))
            if info and info["group"] == pid and info["state"] != "Z":
                return True
    return False


def stop_workers(folder, run):
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
        if any(name in selected for name in ("ss", "nstat", "tcpdump", "jfr", "app-log")):
            info = json.loads(command(["docker", "inspect", args.container]))[0]
            if not info["State"]["Running"]:
                raise ValueError("대상 앱 컨테이너가 실행 중이어야 합니다.")
            run.update(container_id=info["Id"], container_pid=info["State"]["Pid"],
                       container_started_at=info["State"]["StartedAt"],
                       container_ticks=process(info["State"]["Pid"])["ticks"])
            save(folder / "run.json", run)
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
            save(status_file, {"state": "running", "started_at": now(), "pid": os.getpid()})
            functions = {"ss": run_ss, "nstat": run_nstat, "tcpdump": run_tcpdump}
            result = functions[kind](folder, run, now, lambda: stopping)
            state = "stopped" if stopping else "ended-unexpectedly"
            if result and result.get("limited"):
                state = "capture-limit-reached"
            save(status_file, {"state": state, "ended_at": now(), "detail": result})
        except BaseException as exc:
            save(status_file, {"state": "failed", "ended_at": now(), "error": str(exc)})
            raise
        return
    env = dict(os.environ, LC_ALL="C")
    command = list(spec["command"])
    if shutil.which("stdbuf"):
        command = ["stdbuf", "-oL", *command]
    try:
        with (folder / spec["filename"]).open("x") as log:
            if stopping:
                raise RuntimeError("시작 중 종료 요청")
            child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                     text=True, errors="replace", env=env)
            save(status_file, {"state": "running", "started_at": now(), "pid": os.getpid()})
            for line in child.stdout:
                if line.startswith("Linux ") and "(" in line and ")" in line:
                    before, rest = line.split("(", 1)
                    line = before + "(host)" + rest.split(")", 1)[1]
                if spec["stamp_all"] or line.lstrip()[:1].isdigit():
                    line = now() + " " + args.run_id + " " + line
                log.write(line)
                log.flush()
            code = child.wait()
            state = ("stopped" if stopping and code in (0, -signal.SIGTERM, -signal.SIGINT)
                     else "ended-unexpectedly")
            log.write(now() + " " + args.run_id + (" STOP" if stopping else " END")
                      + " exit=" + str(code) + "\n")
        save(status_file, {"state": state, "ended_at": now(), "command_exit": code})
    except BaseException as exc:
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
    os.umask(0o077)
    if args.action == "__worker":
        worker(args)
        return 0
    STATE.mkdir(mode=0o700, exist_ok=True)
    with (STATE / "lock").open("a") as lock:
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
