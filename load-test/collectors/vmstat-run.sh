#!/usr/bin/env bash
# I5 — 호스트 메모리·스왑·대기·CPU 1초 (`vmstat -n 1`). 앱 EC2, Redis 단계에는 Redis EC2에서도 (OUT_NAME=vmstat-redis.log).
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… [OUT_NAME=vmstat.log] vmstat-run.sh
  vmstat 원본 열(r b swpd free buff cache si so bi bo in cs us sy id wa st)을 그대로 두고
  데이터 줄 앞에만 "<UTC epoch ms> <run-id> "를 붙인다. 헤더 2줄은 시작 때 1회 원본 그대로.
  CPU 열이 mpstat과 겹쳐도 지우지 않는다. 첫 데이터 줄은 vmstat 규칙상 부팅 이후 평균이다(분석에서 뺀다).
  SIGTERM으로 끝낸다 → STOP 줄. vmstat(procps)이 없으면 코드 3(비차단) — 설치하지 않는다.
  종료 코드: 0(STOP) 1(입력 오류·중복 시작) 3(vmstat 없음 또는 스스로 끝남)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID OUT_DIR
require_id RUN_ID
OUT_NAME=${OUT_NAME:-vmstat.log}
command -v vmstat >/dev/null 2>&1 || die 3 "vmstat 없음(procps 미설치) — 설치하지 않는다 (I5 비차단)"
LOG=$OUT_DIR/$OUT_NAME
start_guard "${OUT_NAME%.log}" "$LOG"
stamp_run "$LOG" data 3 vmstat -n 1
