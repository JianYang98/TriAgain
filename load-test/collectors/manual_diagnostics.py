"""Docker/JVM and packet capture operations for the manual controller."""
import ipaddress
import json
import os
from pathlib import Path
import selectors
import signal
import subprocess
import time
import uuid


def command(argv, timeout=30):
    result = subprocess.run(argv, capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(" ".join(argv[:3]) + ": " + result.stderr.strip()[-1000:])
    return result.stdout


def container_info(name):
    info = json.loads(command(["docker", "inspect", name]))[0]
    if not info["State"]["Running"] or info["State"]["Pid"] <= 0:
        raise ValueError("대상 앱 컨테이너가 실행 중이어야 합니다.")
    return info["Id"], info["State"]["Pid"]


def same_container(run):
    info = json.loads(command(["docker", "inspect", run["container_id"]]))[0]
    if (not info["State"]["Running"] or info["State"]["Pid"] != run["container_pid"]
            or info["State"]["StartedAt"] != run["container_started_at"]):
        raise RuntimeError("수집 시작 후 대상 앱 컨테이너가 종료되거나 재시작됐습니다.")


def start_jfr(run, persist):
    options = run["options"]
    name = "collect-" + uuid.uuid4().hex
    filename = "/tmp/" + name + ".jfr"
    base = ["docker", "exec", run["container_id"], options["jcmd"], str(options["jvm_pid"])]
    # Check tooling before creating a recording. No install or JVM restart.
    command(base + ["help", "JFR.start"])
    run["jfr"] = {"state": "start-uncertain", "name": name, "filename": filename,
                  "command": base}
    persist()
    output = command(base + ["JFR.start", "name=" + name,
                             "settings=" + options["jfr_settings"], "disk=true",
                             "maxsize=" + str(options["jfr_max_mb"]) + "m"])
    if "Started recording" not in output:
        raise RuntimeError(output.strip())
    run["jfr"].update(state="recording", start_output=output)
    persist()
    return run["jfr"]


def stop_jfr(folder, run):
    info = run["jfr"]
    same_container(run)
    if info["state"] in ("recording", "start-uncertain"):
        output = command(info["command"] + ["JFR.stop", "name=" + info["name"],
                                           "filename=" + info["filename"]], timeout=60)
        if "Stopped recording" not in output:
            raise RuntimeError(output.strip())
        info["state"] = "recording-stopped"
        info["stop_output"] = output
        # Persist before copying so a transfer retry never stops an unrelated recording.
        (folder / "jfr-stop.json").write_text(json.dumps(info, indent=2) + "\n")
    destination = folder / "recording.jfr"
    if not destination.exists():
        partial = folder / ".recording.jfr.partial"
        command(["docker", "cp", run["container_id"] + ":" + info["filename"], str(partial)],
                timeout=60)
        if partial.stat().st_size == 0:
            raise RuntimeError("JFR 파일이 비어 있습니다.")
        partial.chmod(0o600)
        partial.replace(destination)
    info["state"] = "saved"
    return info


def capture_limits(folder, run, now):
    prefix = ["sudo", "-n", "nsenter", "-t", str(run["container_pid"]), "-n"]
    queries = [
        ["ss", "-lnt", "sport", "=", ":" + str(run["options"]["port"])],
        ["sysctl", "net.core.somaxconn", "net.ipv4.tcp_max_syn_backlog",
         "net.ipv4.tcp_syncookies", "net.ipv4.tcp_abort_on_overflow"],
        ["cat", "/proc/net/sockstat"],
    ]
    with (folder / "network-limits.txt").open("x") as stream:
        stream.write(now() + " " + run["run_id"] + "\n")
        for query in queries:
            result = subprocess.run(prefix + query, capture_output=True, text=True, timeout=10)
            stream.write(" ".join(query) + "\n" + result.stdout + result.stderr)
            stream.write("exit=" + str(result.returncode) + "\n")


def run_tcpdump(folder, run, now, stopped):
    options = run["options"]
    expression = "tcp and port " + str(options["port"])
    if options["peer_ip"]:
        expression += " and host " + str(ipaddress.ip_address(options["peer_ip"]))
    argv = ["sudo", "-n", "nsenter", "-t", str(run["container_pid"]), "-n",
            "tcpdump", "-i", "any", "-nn", "-s", str(options["snaplen"]), "-U",
            "-w", "-", expression]
    limit = options["pcap_max_mb"] * 1000000
    limited = False
    termination_at = None
    with (folder / "tcpdump.stderr.log").open("xb") as errors, (folder / "network.pcap").open("xb") as output:
        child = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=errors)
        selector = selectors.DefaultSelector()
        selector.register(child.stdout, selectors.EVENT_READ)
        try:
            while True:
                if output.tell() >= limit and not limited:
                    limited = True
                    termination_at = time.monotonic()
                    subprocess.run(["sudo", "-n", "kill", "-TERM", "--", "-" + str(os.getpgrp())],
                                   check=True, timeout=5, start_new_session=True)
                if stopped() and termination_at is None:
                    termination_at = time.monotonic()
                if termination_at and time.monotonic() - termination_at > 15:
                    raise RuntimeError("tcpdump 종료 대기 시간 초과")
                if selector.select(timeout=0.2):
                    data = os.read(child.stdout.fileno(), 65536)
                    if not data:
                        break
                    output.write(data)
                    output.flush()
            code = child.wait(timeout=5)
        finally:
            selector.close()
        if output.tell() < 24 or code not in (0, -signal.SIGTERM):
            raise RuntimeError("tcpdump 캡처 실패. stderr 로그를 확인하세요.")
    return {"limited": limited, "command_exit": code, "ended_at": now()}
