#!/usr/bin/env bash
# 테스트 EC2 한 줄 시작 래퍼 — CPU·TCP·폴러를 백그라운드로 켠다 (C8 §3). 끄기는 box-app-stop.sh.
set -euo pipefail
USAGE='사용법 (테스트 EC2): RUN_ID=… ARM=db|rd CREW_ID=… EXPECTED_TOTAL=1000 APP_CONTAINER=… box-app-start.sh
  DB 접속: PGHOST PGPORT PGUSER(앱과 같은 계정) PGDATABASE + ~/.pgpass — export된 상태로 실행한다
  rd 팔: REDIS_CLI 필수, NAMESPACE(기본 dbb)
  선택: OUT_DIR(기본 ~/dbb-results/<YYYYMMDD>/<RUN_ID>/ — 워밍업은 OUT_DIR=<run 폴더>/warmup)
        MAX_WAIT_SEC(기본 db 60 · rd 180) PORT(기본 8080) PSQL K6_DONE_FILE STOPFILE
  켜는 것: mpstat-run.sh · vmstat-run.sh · tcp-collect.sh start · poll-db.sh · poll-lock.sh · (rd) poll-redis.sh
           각자 $OUT_DIR/<이름>.pid, 오류 출력은 $OUT_DIR/<이름>.stderr.log
  폴러는 k6 종료 표식(box-app-stop.sh가 쓴다) 뒤 확정·시간 초과·STOPFILE로 스스로 끝난다.
  worker 중단: 신규 부하를 멈추고 전송 요청 종료를 확인한 뒤 echo worker-stopped > $OUT_DIR/stopfile.txt
  종료 코드: 0 · 1 입력 오류 · 2 필수 폴러 시작 실패 · 3 비차단 수집기(mpstat·vmstat·TCP) 경고만'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID ARM CREW_ID EXPECTED_TOTAL APP_CONTAINER
require_id RUN_ID CREW_ID
require_uint EXPECTED_TOTAL
case "$ARM" in
db) wait_default=60 ;;
rd) wait_default=180; require_env REDIS_CLI ;;
*) die 1 "ARM은 db|rd: '$ARM'" ;;
esac
OUT_DIR=${OUT_DIR:-$(default_out_dir)}
MAX_WAIT_SEC=${MAX_WAIT_SEC:-$wait_default}
export RUN_ID ARM CREW_ID EXPECTED_TOTAL APP_CONTAINER OUT_DIR MAX_WAIT_SEC
mkdir -p "$OUT_DIR"

bg() { # $1 이름, 나머지 명령 — 터미널·ssh 세션과 분리해 백그라운드로
	local name=$1
	shift
	nohup "$@" </dev/null >>"$OUT_DIR/$name.stderr.log" 2>&1 &
}

bg mpstat "$DIR/mpstat-run.sh"
bg vmstat "$DIR/vmstat-run.sh"
tcp_rc=0
"$DIR/tcp-collect.sh" start </dev/null >>"$OUT_DIR/tcp-collect.stderr.log" 2>&1 || tcp_rc=$?
bg poll-db "$DIR/poll-db.sh"
bg poll-lock "$DIR/poll-lock.sh"
pollers="poll-db poll-lock"
if [[ "$ARM" == rd ]]; then
	bg poll-redis "$DIR/poll-redis.sh"
	pollers="$pollers poll-redis"
fi
sleep 2

rc=0
check() { # $1 이름(PID 파일) $2 필수(2)/비차단(3)
	local pid=""
	[[ -f "$OUT_DIR/$1.pid" ]] && pid=$(cut -d' ' -f1 "$OUT_DIR/$1.pid")
	if pid_alive "$pid"; then
		echo "OK   $1 (pid $pid)"
		return 0
	fi
	echo "FAIL $1 — $(tail -n 2 "$OUT_DIR/$1.stderr.log" 2>/dev/null | tr '\n' ' ')"
	if [[ "$2" == 2 ]]; then rc=2; elif [[ "$rc" == 0 ]]; then rc=3; fi
}
check mpstat 3
check vmstat 3
if [[ "$tcp_rc" == 0 ]]; then check tcp-collect 3; else
	echo "WARN tcp-collect (코드 $tcp_rc) — $(tail -n 1 "$OUT_DIR/tcp-collect.stderr.log" 2>/dev/null)"
	[[ "$rc" == 0 ]] && rc=3
fi
for p in $pollers; do check "$p" 2; done
echo "== OUT_DIR=$OUT_DIR · arm=$ARM · MAX_WAIT_SEC=$MAX_WAIT_SEC · 로그 보기: tail -f $OUT_DIR/poll-db.log"
if [[ "$ARM" == rd ]]; then
	echo "== Redis 박스에서 따로 켠다 (같은 RUN_ID, Redis 박스의 run 폴더):"
	echo "   RUN_ID=$RUN_ID OUT_DIR=<Redis 박스 run 폴더> OUT_NAME=mpstat-redis.log nohup ./mpstat-run.sh </dev/null >/dev/null 2>&1 &"
	echo "   RUN_ID=$RUN_ID OUT_DIR=<Redis 박스 run 폴더> OUT_NAME=vmstat-redis.log nohup ./vmstat-run.sh </dev/null >/dev/null 2>&1 &"
fi
exit "$rc"
