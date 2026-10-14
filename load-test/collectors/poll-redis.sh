#!/usr/bin/env bash
# R1·R3 — Redis 선승인·큐 1초 폴링 (Redis 팔, 테스트 EC2). Redis 팔의 종료 리더 (C8 §3-5).
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… CREW_ID=… REDIS_CLI="redis-cli -h <host> -p 6379" EXPECTED_TOTAL=1000 MAX_WAIT_SEC=180 poll-redis.sh
  REDIS_CLI: 표준 입력으로 명령을 받는 redis-cli 명령 (crew-join-redis.sh와 같은 방식, 단어 분리됨).
             매초 1회 실행되므로 지연이 작아야 한다 — 표본마다 "docker run --rm"은 쓰지 않는다 (R-G3에서 확인).
             인증이 있으면 -a 대신 REDISCLI_AUTH 환경변수 (docker exec면 -e REDISCLI_AUTH도).
  선택: NAMESPACE(기본 dbb) DB_SAMPLE_FILE(기본 $OUT_DIR/poll-db.latest.txt — 같은 run poll-db.sh의 최신 성공 표본)
        K6_DONE_FILE STOPFILE (poll-db.sh와 같다)
  키: <P>=triagain:crew-join:{<NAMESPACE>:<RUN_ID>}  ZCARD <P>:crew:<CREW_ID>:members · HGET <P>:crew:<CREW_ID>:meta seq
      LLEN <P>:pending · LLEN <P>:processing
  확정: k6 종료 뒤 같은 반복에서 pending=0 ∧ processing=0 ∧ DB 표본(3초 이내 성공 표본) members>=EXPECTED_TOTAL → 5초 더 → END
  출력: $OUT_DIR/poll-redis.log  헤더 ts_poller_ms,zcard,seq,pending,processing (seq 없음=nil, 실패 행 ts,ERR,사유)
        첫 표본 zcard<1이면 WARN 줄 (run-id·namespace 오타는 오류 없이 0이 나온다)
  종료 코드: 0(END·STOP) 1(입력 오류·중복 시작) 2(redis-cli 없음)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID OUT_DIR CREW_ID REDIS_CLI EXPECTED_TOTAL MAX_WAIT_SEC
require_id RUN_ID CREW_ID
require_uint EXPECTED_TOTAL MAX_WAIT_SEC
NAMESPACE=${NAMESPACE:-dbb}
[[ "$NAMESPACE" =~ ^[A-Za-z0-9_-]+$ ]] || die 1 "NAMESPACE 형식 위반([A-Za-z0-9_-]): '$NAMESPACE'"
command -v "${REDIS_CLI%% *}" >/dev/null 2>&1 || die 2 "redis-cli 명령이 없다: ${REDIS_CLI%% *}"
IS_LEADER=1
DB_SAMPLE_MAX_AGE_MS=3000

LOG=$OUT_DIR/poll-redis.log
DB_SAMPLE_FILE=${DB_SAMPLE_FILE:-$OUT_DIR/poll-db.latest.txt}
poller_files
P="triagain:crew-join:{$NAMESPACE:$RUN_ID}"
start_guard poll-redis "$LOG"
echo "ts_poller_ms,zcard,seq,pending,processing" >"$LOG"
log_line "START run_id=$RUN_ID crew_id=$CREW_ID namespace=$NAMESPACE expected_total=$EXPECTED_TOTAL max_wait_sec=$MAX_WAIT_SEC prefix=$P"
poller_traps

# 4개 명령을 한 번의 redis-cli 호출로 (표준 입력). 키는 따옴표로 감싼다 — 중괄호 해시태그가 들어 있다.
sample() {
	printf 'ZCARD "%s"\nHGET "%s" seq\nLLEN "%s"\nLLEN "%s"\n' \
		"$P:crew:$CREW_ID:members" "$P:crew:$CREW_ID:meta" "$P:pending" "$P:processing" | $REDIS_CLI 2>&1
}

db_confirmed() { # 같은 run DB 폴러의 최신 성공 표본이 신선하고 정원 이상인가
	local now=$1 dts rest dmem
	[[ -f "$DB_SAMPLE_FILE" ]] || return 1
	IFS=, read -r dts rest <"$DB_SAMPLE_FILE" || return 1
	dmem=${rest##*,}
	[[ "$dts" =~ ^[0-9]+$ && "$dmem" =~ ^[0-9]+$ ]] || return 1
	((now - dts <= DB_SAMPLE_MAX_AGE_MS && dmem >= EXPECTED_TOTAL))
}

first=1
TICK0=$(ts_ms)
while :; do
	ts=$(ts_ms)
	ok=0
	z="" s="" pe="" pr=""
	if out=$(sample); then
		{ IFS= read -r z; IFS= read -r s; IFS= read -r pe; IFS= read -r pr; } <<<"$out" || true
	fi
	if [[ "$z" =~ ^[0-9]+$ && "$s" =~ ^[0-9]*$ && "$pe" =~ ^[0-9]+$ && "$pr" =~ ^[0-9]+$ ]]; then
		log_line "$ts,$z,${s:-nil},$pe,$pr"
		if [[ -n "$first" ]]; then
			first=""
			if ((z < 1)); then
				log_line "WARN ts_poller_ms=$ts first sample zcard=$z (<1) — NAMESPACE·RUN_ID·CREW_ID·init 확인 (오타면 오류 없이 0)"
				echo "WARN poll-redis: 첫 표본 zcard=$z — 키 접두어 $P 확인" >&2
			fi
		fi
		if ((pe == 0 && pr == 0)) && db_confirmed "$ts"; then ok=1; fi
	else
		log_line "$ts,ERR,$(clean_err "$out")"
	fi
	end_check "$ts" "$ok"
	sleep_to_next_tick
done
