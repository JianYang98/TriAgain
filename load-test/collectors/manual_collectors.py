"""Manual load-test collector command definitions.

The inventory includes Linux host CPU/memory and container network collectors.
The CPU and rate fields of the first vmstat report describe the interval
since boot; distinguish them from subsequent one-second reports.
"""

# 워커 하나로 도는 수집 항목. 키 의미:
# - command 방식: command(장기 실행 명령), filename(출력 파일). worker()는 stdout을 로그 파일에 직접 연결한다.
# - kind 방식: kind(worker()의 functions에서 run_ss·run_nstat·run_tcpdump로 연결),
#   privileged(sudo 필요 → launch_worker의 sudo 사전 확인, 정지 시 sudo kill).
#   kind 항목의 filename은 코드가 읽지 않는다. 실제 파일명은 각 run_* 함수 안에 있다.
COLLECTORS = {
    "mpstat": {
        "command": ("mpstat", "-P", "ALL", "1"),
        "filename": "mpstat.log",
    },
    "vmstat": {
        "command": ("vmstat", "-n", "-t", "1"),
        "filename": "vmstat.log",
    },
    "ss": {"kind": "ss", "filename": "ss-samples.jsonl", "privileged": True},
    "nstat": {"kind": "nstat", "filename": "nstat-ts.jsonl", "privileged": True},
    "tcpdump": {"kind": "tcpdump", "filename": "network.pcap", "privileged": True},
}

# Lifecycle collectors are handled at ON/OFF instead of as logging workers.
LIFECYCLE_COLLECTORS = ("jfr", "app-log")
