#!/usr/bin/env bash
# D3 — 락 대기 세션 1초 폴링 (테스트 EC2). 앱과 같은 DB 계정으로 접속한다 (measurement-contract §2-D).
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… MAX_WAIT_SEC=60 poll-lock.sh
  DB 접속: libpq 표준 PGHOST PGPORT PGUSER(=앱과 같은 계정) PGDATABASE + ~/.pgpass
  선택: K6_DONE_FILE STOPFILE PSQL PGCONNECT_TIMEOUT (poll-db.sh와 같다)
  종료: 스스로 판정하지 않는다 — 리더 폴러의 poll-end.txt · STOPFILE · 시간 초과를 따른다
  출력: $OUT_DIR/poll-lock.log  헤더 ts_poller_ms,pid,wait_event_type,wait_event,state,query_ms,query
        대기가 없으면 ts_poller_ms,none (하트비트) · 실패 행 ts,ERR,사유 · 이벤트 줄 DONE_SEEN END STOP
  종료 코드: 0(END·STOP) 1(입력 오류·중복 시작) 2(psql 없음)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID OUT_DIR MAX_WAIT_SEC
require_id RUN_ID
require_uint MAX_WAIT_SEC
PSQL=${PSQL:-psql}
command -v "${PSQL%% *}" >/dev/null 2>&1 || die 2 "psql 명령이 없다: ${PSQL%% *} (PSQL로 바꿀 수 있다)"
export PGCONNECT_TIMEOUT=${PGCONNECT_TIMEOUT:-3}

LOG=$OUT_DIR/poll-lock.log
poller_files
start_guard poll-lock "$LOG"
echo "ts_poller_ms,pid,wait_event_type,wait_event,state,query_ms,query" >"$LOG"
log_line "START run_id=$RUN_ID max_wait_sec=$MAX_WAIT_SEC"
poller_traps

# pg_blocking_pids()는 쓰지 않는다 — 호출마다 락 관리자 공유 상태를 잠깐 독점해 측정 대상(락 경합)을 건드린다 [문서].
# query는 마지막 열, 쉼표는 ';'로 바꾼다. JDBC 문장은 $1 자리표시자라 값이 찍히지 않는다.
sample() {
	$PSQL -X -q -At -F , -v ON_ERROR_STOP=1 2>&1 <<'SQL'
SELECT a.pid, a.wait_event_type, a.wait_event, a.state,
       floor(extract(epoch FROM clock_timestamp() - a.query_start) * 1000)::bigint,
       translate(left(regexp_replace(a.query, '\s+', ' ', 'g'), 200), ',', ';')
FROM pg_stat_activity a
WHERE a.wait_event_type = 'Lock';
SQL
}

TICK0=$(ts_ms)
while :; do
	ts=$(ts_ms)
	if out=$(LC_ALL=C sample); then
		if [[ -z "$out" ]]; then
			log_line "$ts,none"
		else
			while IFS= read -r row; do printf '%s,%s\n' "$ts" "$row"; done <<<"$out" >>"$LOG"
		fi
	else
		log_line "$ts,ERR,$(clean_err "$out")"
	fi
	end_check "$ts" 0
	sleep_to_next_tick
done
