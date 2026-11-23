"""
수동 부하 테스트에서 사용하는 수집 항목 정의

COLLECTORS는 항목별 워커 설정과 명령어 정의
LIFECYCLE_COLLECTORS는 ON/OFF에서 직접 처리하는 JFR과 앱 로그를 담는다.

vmstat의 첫 데이터 행에서 CPU와 초당 변화량 항목은 부팅 이후의
평균이다. 이후 행은 직전 1초 구간의 값을 보여 준다.
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

# 이 두 항목은 별도 워커 없이 On OFF 시 수집
LIFECYCLE_COLLECTORS = ("jfr", "app-log")
