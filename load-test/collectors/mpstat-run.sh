#!/usr/bin/env bash
# I1 — 호스트 CPU 1초 (`mpstat -P ALL 1`). 앱 EC2, Redis 단계에는 Redis EC2에서도 (OUT_NAME=mpstat-redis.log).
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… [OUT_NAME=mpstat.log] mpstat-run.sh
  각 줄 앞에 "<UTC epoch ms> <run-id> "를 붙여 $OUT_DIR/$OUT_NAME에 쓴다 (배너의 호스트명은 가린다).
  SIGTERM으로 끝낸다 → STOP 줄. mpstat(sysstat)이 없으면 코드 3 — 설치하지 않는다.
  종료 코드: 0(STOP) 1(입력 오류·중복 시작) 2(mpstat이 스스로 끝남) 3(mpstat 없음)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID OUT_DIR
require_id RUN_ID
OUT_NAME=${OUT_NAME:-mpstat.log}
command -v mpstat >/dev/null 2>&1 || die 3 "mpstat 없음(sysstat 미설치) — 설치하지 않는다. 초 단위 CPU는 process_cpu_usage(S5)로 본다 (G5)"
LOG=$OUT_DIR/$OUT_NAME
start_guard "${OUT_NAME%.log}" "$LOG"
stamp_run "$LOG" all 2 mpstat -P ALL 1
