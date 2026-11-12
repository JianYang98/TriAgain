#!/usr/bin/env bash
# D6 — DB 인원 1초 폴링 (테스트 EC2). T_db_confirm의 원천 (measurement-contract §1·§2-D).
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… CREW_ID=… ARM=db|rd EXPECTED_TOTAL=1000 MAX_WAIT_SEC=60 poll-db.sh
  DB 접속: libpq 표준 PGHOST PGPORT PGUSER PGDATABASE + ~/.pgpass (비밀번호를 인자·로그에 쓰지 않는다)
  선택: K6_DONE_FILE(기본 $OUT_DIR/k6-done.txt) STOPFILE(기본 $OUT_DIR/stopfile.txt) PGCONNECT_TIMEOUT(기본 3)
        PSQL(기본 psql. 호스트에 없으면 예: "docker exec -i -e PGHOST -e PGUSER -e PGDATABASE <컨테이너> psql")
  ARM=db: 이 폴러가 종료를 정한다(k6 종료 뒤 DB 인원>=EXPECTED_TOTAL → 5초 더 → END reason=confirmed)
  ARM=rd: poll-redis.sh가 정한 종료(poll-end.txt)를 따른다 — DB 정원만으로 끝내지 않는다
  출력: $OUT_DIR/poll-db.log  헤더 ts_poller_ms,ts_db_ms,current_members,members (실패 행: ts,ERR,사유)
        이벤트 줄: T_DB_CONFIRM(정원 첫 도달) DONE_SEEN CONFIRM_AFTER_K6 END STOP
        $OUT_DIR/poll-db.latest.txt  최신 성공 표본 1행 (poll-redis.sh의 DB_SAMPLE_FILE)
  종료 코드: 0(END·STOP) 1(입력 오류·중복 시작) 2(psql 없음)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID OUT_DIR CREW_ID ARM EXPECTED_TOTAL MAX_WAIT_SEC
require_id RUN_ID CREW_ID
require_uint EXPECTED_TOTAL MAX_WAIT_SEC
case "$ARM" in db) IS_LEADER=1 ;; rd) IS_LEADER=0 ;; *) die 1 "ARM은 db|rd: '$ARM'" ;; esac
PSQL=${PSQL:-psql}
command -v "${PSQL%% *}" >/dev/null 2>&1 || die 2 "psql 명령이 없다: ${PSQL%% *} (PSQL로 바꿀 수 있다)"
export PGCONNECT_TIMEOUT=${PGCONNECT_TIMEOUT:-3}

LOG=$OUT_DIR/poll-db.log
LATEST=$OUT_DIR/poll-db.latest.txt
poller_files
start_guard poll-db "$LOG"
echo "ts_poller_ms,ts_db_ms,current_members,members" >"$LOG"
log_line "START run_id=$RUN_ID crew_id=$CREW_ID arm=$ARM expected_total=$EXPECTED_TOTAL max_wait_sec=$MAX_WAIT_SEC"
END_EXTRA="t_db_confirm_ms=none"
T_DB=""
poller_traps

sample() {
	$PSQL -X -q -At -F , -v ON_ERROR_STOP=1 -v crew="$CREW_ID" 2>&1 <<'SQL'
SELECT floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint,
       c.current_members,
       (SELECT count(*) FROM crew_members m WHERE m.crew_id = c.id)
FROM crews c
WHERE c.id = :'crew';
SQL
}

TICK0=$(ts_ms)
while :; do
	ts=$(ts_ms)
	ok=0
	if out=$(LC_ALL=C sample) && [[ "$out" =~ ^[0-9]+,[0-9]+,[0-9]+$ ]]; then
		log_line "$ts,$out"
		printf '%s\n' "$ts,$out" >"$LATEST.tmp" && mv -f "$LATEST.tmp" "$LATEST"
		members=${out##*,}
		if ((members >= EXPECTED_TOTAL)); then
			ok=1
			if [[ -z "$T_DB" ]]; then
				T_DB=$ts
				END_EXTRA="t_db_confirm_ms=$T_DB"
				log_line "T_DB_CONFIRM t_db_confirm_ms=$T_DB ts_db_ms=$(echo "$out" | cut -d, -f1) members=$members"
			fi
		fi
	else
		log_line "$ts,ERR,$(clean_err "${out:-crew $CREW_ID not found}")"
	fi
	end_check "$ts" "$ok"
	sleep_to_next_tick
done
