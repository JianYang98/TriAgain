"""
수동 부하 테스트에서 사용하는 수집 항목 정의

COLLECTORS는 항목별 워커 설정과 명령어 정의
LIFECYCLE_COLLECTORS는 ON/OFF에서 직접 처리하는 JFR과 앱 로그를 담는다.

vmstat의 첫 데이터 행에서 CPU와 초당 변화량 항목은 부팅 이후의
평균이다. 이후 행은 직전 1초 구간의 값을 보여 준다.
"""


#  워커가 실행할 수집 항목 설정
# command: 실행할 명령어
# filename: 결과 파일명 (kind 방식에서는 사용하지 않음)
# stamp_all: 모든 출력 줄에 시각과 RUN ID를 붙일지 여부
# kind: 실행할 수집 함수의 종류
# privileged: 실행 전 sudo 권한 확인 여부
COLLECTORS = {
    "mpstat": {
        "command": ("mpstat", "-P", "ALL", "1"),
        "filename": "mpstat.log",
        "stamp_all": True,
    },
    "vmstat": {
        "command": ("vmstat", "-n", "1"),
        "filename": "vmstat.log",
        "stamp_all": False,
    },
    "ss": {"kind": "ss", "filename": "ss-samples.jsonl", "privileged": True},
    "nstat": {"kind": "nstat", "filename": "nstat-ts.jsonl", "privileged": True},
    "tcpdump": {"kind": "tcpdump", "filename": "network.pcap", "privileged": True},
}

# 이 두 항목은 별도 워커 없이 On OFF 시 수집
LIFECYCLE_COLLECTORS = ("jfr", "app-log")
