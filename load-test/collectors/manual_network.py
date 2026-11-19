"""Network and application log adapters for a manually controlled Linux run."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time


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
    """Sample listen queues and SYN-RECV rows in the app container netns."""
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
    """Record one-second cumulative host and netns counters until stopped."""
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
