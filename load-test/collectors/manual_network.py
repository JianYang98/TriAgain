"""Network and application log adapters for a manually controlled Linux run."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

# nstat 출력에서 누적값과 표본 간 증가량을 기록할 TCP 카운터 이름.

NSTAT_COUNTERS = (
    "TcpExtListenOverflows",
    "TcpExtListenDrops",
    "TcpRetransSegs",
    "TcpExtTCPReqQFullDoCookies",
    "TcpExtTCPReqQFullDrop",
    "TcpExtTCPSynRetrans",
    "TcpAttemptFails",
    "TcpOutRsts",
    "TcpEstabResets",
    "TcpExtTCPBacklogDrop",
)


def _write_json_line(stream, value):
    stream.write(json.dumps(value, ensure_ascii=False, sort_keys=True) + "\n")
    stream.flush()


def _port_options(run):
    options = run["options"]
    port = options["port"]
    if type(port) is not int or not 1 <= port <= 65535:
        raise ValueError("port must be an integer between 1 and 65535")
    return options, port


def _run_ss_command(pid, port, arguments):
    command = [
        "sudo", "-n", "nsenter", "-t", str(pid), "-n", "ss", "-H",
        *arguments, "sport", "=", ":" + str(port),
    ]
    try:
        result = subprocess.run(
            command, capture_output=True, text=True, errors="replace", timeout=3,
            check=False,
        )
        return result.returncode, result.stdout, result.stderr
    except subprocess.TimeoutExpired as error:
        stdout = error.stdout or ""
        stderr = error.stderr or ""
        if isinstance(stdout, bytes):
            stdout = stdout.decode("utf-8", errors="replace")
        if isinstance(stderr, bytes):
            stderr = stderr.decode("utf-8", errors="replace")
        return 124, stdout, stderr + "command timed out\n"
    except OSError as error:
        return 127, "", type(error).__name__ + ": " + str(error) + "\n"


def _count_lines(output):
    return sum(1 for line in output.splitlines() if line.strip())


def _check_target(run):
    # 표본마다 부르는 컨테이너 확인. docker inspect 없이 /proc의 state와 시작 ticks만
    # ON 때 기록(container_ticks)과 비교한다. 다르면 예외 → 워커 status=failed.
    path = Path("/proc") / str(run["container_pid"]) / "stat"
    fields = path.read_text().rsplit(")", 1)[1].split()
    if fields[19] != run["container_ticks"] or fields[0] == "Z":
        raise RuntimeError("앱 컨테이너 프로세스가 종료되거나 바뀌었습니다.")


def _parse_listen_queues(output):
    queues = []
    for line in output.splitlines():
        fields = line.split()
        if len(fields) < 5:
            continue
        try:
            queues.append({
                "recv_q": int(fields[1]),
                "send_q": int(fields[2]),
                "local": fields[3],
            })
        except ValueError:
            continue
    return queues


def run_ss(folder: Path, run: dict, now: callable, stopped: callable):
    """앱 컨테이너 netns에서 대상 포트의 LISTEN 큐와 SYN-RECV 소켓 수를 반복 기록한다.

    - 표본 하나 = `sudo -n nsenter -t <컨테이너 PID> -n ss` 2회.
      `-lnt`의 LISTEN 행에서 Recv-Q·Send-Q를, `state syn-recv` 결과에서 행 수를 얻는다.
      파싱 결과와 함께 원문(listen_raw·syn_raw)을 남긴다.
    - 명령이 실패한 표본도 returncode·stderr와 함께 한 행으로 쓴다. 빠진 구간이 행으로 보인다.
    - 표본마다 _check_target으로 컨테이너 프로세스가 바뀌지 않았는지 확인한다(바뀌면 예외).
    - timestamp는 두 조회가 끝난 뒤의 시각이다(run_nstat은 조회 전 시각).
    - stopped()는 표본 사이에서만 확인한다. OFF의 SIGTERM은 워커 그룹 전체에 가므로 그 순간 실행 중이던
      ss 조회도 함께 끝나 마지막 행이 실패 행이 될 수 있다.
    - 성공 표본(두 조회 모두 rc 0)이 하나도 없으면 예외 → status=failed. 반환값은 없다.
    """
    options, port = _port_options(run)
    interval = options["ss_interval"]
    if not isinstance(interval, (int, float)) or interval <= 0:
        raise ValueError("ss_interval must be positive")
    pid = run["container_pid"]
    if type(pid) is not int or pid <= 0:
        raise ValueError("container_pid must be a positive integer")

    output = Path(folder) / "ss-samples.jsonl"
    deadline = time.monotonic()
    sample = 0
    successful_samples = 0
    with output.open("x", encoding="utf-8") as stream:
        while not stopped():
            _check_target(run)
            listen_rc, listen_raw, listen_err = _run_ss_command(
                pid, port, ["-lnt"]
            )
            syn_rc, syn_raw, syn_err = _run_ss_command(
                pid, port, ["-nt", "state", "syn-recv"]
            )
            row = {
                "timestamp": now(),
                "run_id": run["run_id"],
                "sample": sample,
                "listen_rc": listen_rc,
                "syn_rc": syn_rc,
                "listen_queues": _parse_listen_queues(listen_raw),
                "listen_raw": listen_raw,
                "syn_recv": _count_lines(syn_raw),
                "syn_raw": syn_raw,
            }
            if listen_err:
                row["listen_error"] = listen_err
            if syn_err:
                row["syn_error"] = syn_err
            _write_json_line(stream, row)
            if listen_rc == 0 and syn_rc == 0:
                successful_samples += 1
            sample += 1
            # 시작 시각 기준 고정 간격 스케줄. 밀리지 않는 동안은 누적 지연이 없다. 표본이 간격보다
            # 오래 걸리면 놓친 눈금을 몰아 찍지 않고 지금 시각을 새 기준으로 다시 센다
            # (common.sh sleep_to_next_tick은 원래 눈금에 다시 맞춘다는 점이 다르다).
            deadline += float(interval)
            delay = deadline - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            else:
                deadline = time.monotonic()
    if successful_samples == 0:
        raise RuntimeError("ss produced no successful samples")


def _parse_nstat(output):
    counters = {}
    for line in output.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[0] in NSTAT_COUNTERS:
            try:
                counters[fields[0]] = int(fields[1])
            except ValueError:
                counters[fields[0]] = None
    return counters


def _nstat_command(pid=None):
    # pid 없음 = 호스트 netns(sudo 없이), pid 있음 = 그 프로세스의 netns(nsenter, sudo 필요).
    # -asz: 누적 절대값(-a)·0인 카운터 포함(-z)·history 파일 미갱신(-s, tcp-collect.sh 주석과 같음).
    if pid is None:
        return ["nstat", "-asz"]
    return ["sudo", "-n", "nsenter", "-t", str(pid), "-n", "nstat", "-asz"]


def snapshot_nstat(folder: Path, run: dict, phase: str, now: callable):
    """Write host and container absolute nstat snapshots for one phase."""
    if phase not in ("before", "after"):
        raise ValueError("phase must be before or after")
    _check_target(run)
    pid = run["container_pid"]
    if type(pid) is not int or pid <= 0:
        raise ValueError("container_pid must be a positive integer")

    snapshots = {}
    for scope, command in (
        ("host", _nstat_command()),
        ("container", _nstat_command(pid)),
    ):
        try:
            result = subprocess.run(
                command, capture_output=True, text=True, errors="replace", timeout=10,
                check=False,
            )
            rc, raw, error = result.returncode, result.stdout, result.stderr
        except subprocess.TimeoutExpired as exc:
            raw = exc.stdout or ""
            error = exc.stderr or ""
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8", errors="replace")
            if isinstance(error, bytes):
                error = error.decode("utf-8", errors="replace")
            error += "command timed out\n"
            rc = 124
        except OSError as exc:
            rc, raw = 127, ""
            error = type(exc).__name__ + ": " + str(exc) + "\n"
        snapshots[scope] = {
            "returncode": rc,
            "counters": _parse_nstat(raw),
            "raw": raw,
        }
        if error:
            snapshots[scope]["error"] = error

    record = {
        "timestamp": now(),
        "run_id": run["run_id"],
        "phase": phase,
        "host": snapshots["host"],
        "container": snapshots["container"],
    }
    # 파일명: 컨테이너는 접두어 없음(nstat-<phase>.txt), 호스트는 nstat-host-<phase>.txt.
    # 모두 "x" 모드라 같은 phase를 두 번 쓰면 실패한다(OFF 재시도는 nstat-after.json 존재로 건너뜀).
    suffix = "-" + phase + ".txt"
    for scope in ("container", "host"):
        base_name = "nstat" if scope == "container" else "nstat-host"
        raw_path = Path(folder) / (base_name + suffix)
        with raw_path.open("x", encoding="utf-8") as stream:
            stream.write(snapshots[scope]["raw"])
    path = Path(folder) / ("nstat-" + phase + ".json")
    with path.open("x", encoding="utf-8") as stream:
        json.dump(record, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")
    return record


def _counter_delta(before, after):
    delta = {}
    for key in NSTAT_COUNTERS:
        first = before.get(key)
        last = after.get(key)
        if first is None or last is None:
            delta[key] = None
        elif last < first:
            delta[key] = None
        else:
            delta[key] = last - first
    return delta


def run_nstat(folder: Path, run: dict, now: callable, stopped: callable):
    """호스트와 앱 컨테이너 netns의 관심 TCP 카운터(NSTAT_COUNTERS)를 nstat_interval마다 기록한다.

    - 표본 하나 = host(`nstat -asz`, sudo 없음) → container(nsenter) 순서로 2회 조회.
      timestamp·elapsed_s는 두 조회 전에 잡으므로 두 scope의 실제 조회 시각은 조금씩 다르다.
    - counters는 누적값. delta는 같은 scope의 직전 행 대비 증분이며 다음이면 None이다:
      첫 행, 직전 행이 실패, 이번 값이 없음, 값이 줄어듦(reset 등).
      그래서 숫자로 나온 delta는 항상 delta_elapsed_s와 같은 두 행(직전 행↔이번 행) 사이의 값이다.
    - 조회 rc가 0이 아니면 그 scope의 카운터를 모두 None으로 쓰고 직전 값도 비운다.
    - 성공 표본은 두 scope 모두 rc 0인 행이다. 0개면 예외 → status=failed.
    - 표본마다 _check_target으로 컨테이너 확인. 정지 확인·스케줄·마지막 행 주의는 run_ss와 같다.
    - 반환 dict는 worker()가 status.json의 detail에 넣는다.
    """
    interval = run["options"]["nstat_interval"]
    if not isinstance(interval, (int, float)) or interval <= 0:
        raise ValueError("nstat_interval must be positive")
    pid = run["container_pid"]
    if type(pid) is not int or pid <= 0:
        raise ValueError("container_pid must be a positive integer")

    previous = {"host": None, "container": None}
    previous_elapsed = None
    successful_samples = 0
    started = time.monotonic()
    deadline = time.monotonic()
    output = Path(folder) / "nstat-ts.jsonl"
    with output.open("x", encoding="utf-8") as stream:
        while not stopped():
            _check_target(run)
            timestamp = now()
            elapsed = time.monotonic() - started
            scopes = {}
            sample_succeeded = True
            for scope, command in (
                ("host", _nstat_command()),
                ("container", _nstat_command(pid)),
            ):
                try:
                    result = subprocess.run(
                        command, capture_output=True, text=True, errors="replace",
                        timeout=10, check=False,
                    )
                    rc, raw, error = result.returncode, result.stdout, result.stderr
                except subprocess.TimeoutExpired as exc:
                    raw = exc.stdout or ""
                    error = exc.stderr or ""
                    if isinstance(raw, bytes):
                        raw = raw.decode("utf-8", errors="replace")
                    if isinstance(error, bytes):
                        error = error.decode("utf-8", errors="replace")
                    error += "command timed out\n"
                    rc = 124
                except OSError as exc:
                    rc, raw = 127, ""
                    error = type(exc).__name__ + ": " + str(exc) + "\n"

                counters = _parse_nstat(raw)
                for key in NSTAT_COUNTERS:
                    counters.setdefault(key, None)
                if rc != 0:
                    counters = {key: None for key in NSTAT_COUNTERS}
                current = counters
                prior = previous[scope]
                delta = {}
                for key in NSTAT_COUNTERS:
                    old_value = prior.get(key) if prior is not None else None
                    new_value = current.get(key)
                    if old_value is None or new_value is None or new_value < old_value:
                        delta[key] = None
                    else:
                        delta[key] = new_value - old_value
                scopes[scope] = {
                    "returncode": rc,
                    "counters": current,
                    "delta": delta,
                }
                if error:
                    scopes[scope]["error"] = error
                if rc != 0:
                    sample_succeeded = False
                # 실패한 표본은 기준값으로 쓰지 않는다. 다음 행의 delta는 실패 구간을 건너뛰어
                # 계산하지 않고 None이 된다.
                previous[scope] = current if rc == 0 else None

            if sample_succeeded:
                successful_samples += 1
            row = {
                "timestamp": timestamp,
                "run_id": run["run_id"],
                "elapsed_s": elapsed,
                "delta_elapsed_s": (
                    elapsed - previous_elapsed if previous_elapsed is not None else None
                ),
                "host": scopes["host"],
                "container": scopes["container"],
            }
            _write_json_line(stream, row)
            previous_elapsed = elapsed

            deadline += float(interval)
            delay = deadline - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            else:
                deadline = time.monotonic()

    if successful_samples == 0:
        raise RuntimeError("nstat produced no successful samples")
    return {"state": "stopped", "samples": successful_samples,
            "artifact": str(output)}


def collect_app_log(folder: Path, run: dict) -> dict:
    """Copy timestamped Docker logs for the manually recorded run interval."""
    container_id = run["container_id"]
    started_at = run["started_at"]
    ended_at = run.get("off_requested_at")
    if not container_id or not started_at or not ended_at:
        raise ValueError("container_id, started_at, and off_requested_at are required")
    docker = shutil.which("docker")
    if docker is None:
        return {"state": "skipped", "reason": "docker-unavailable"}

    destination = Path(folder) / "app.log"
    if destination.exists():
        return {"state": "exists", "artifact": str(destination)}
    failed_path = Path(folder) / "app-log.failed.txt"
    fd, temporary_name = tempfile.mkstemp(
        prefix=".app-log-", suffix=".tmp", dir=str(folder)
    )
    temporary = Path(temporary_name)
    if failed_path.exists():
        failed_path = temporary.with_suffix(".failed.txt")
    try:
        with os.fdopen(fd, "wb") as output:
            try:
                result = subprocess.run(
                    [docker, "logs", "--timestamps", "--since", started_at,
                     "--until", ended_at, str(container_id)],
                    stdout=output, stderr=subprocess.STDOUT, check=False, timeout=60,
                )
                returncode = result.returncode
            except subprocess.TimeoutExpired:
                returncode = 124
            output.flush()
            os.fsync(output.fileno())
        if returncode != 0:
            os.replace(str(temporary), str(failed_path))
            return {"state": "failed", "returncode": returncode,
                    "artifact": str(failed_path)}
        os.replace(str(temporary), str(destination))
        return {"state": "saved", "artifact": str(destination)}
    finally:
        if temporary.exists():
            temporary.unlink()
