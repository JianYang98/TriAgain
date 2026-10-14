# shellcheck shell=bash
# collectors 공용 함수 — 각 스크립트가 `source "$DIR/common.sh"`로만 쓴다 (C8 §3·§4).
# bash 3.2(맥 기본)와 5.x(Amazon Linux)에서 모두 돈다: 연관 배열·mapfile·${x,,}를 쓰지 않는다.
# 종료 코드: 0 성공 · 1 사용법/입력 오류 · 2 실행 실패(필수 수집기) · 3 경고(비차단 수집기 실패)

die() { local code=$1; shift; printf '%s\n' "$*" >&2; exit "$code"; }
usage() { printf '%s\n' "$USAGE" >&2; exit 1; }

# UTC epoch ms. GNU date(Linux)는 %3N → 13자리. 맥 BSD date·busybox date는 %N이 없어(10자리·문자 그대로) python3.
if [[ "$(date -u +%s%3N 2>/dev/null)" =~ ^[0-9]{13}$ ]]; then
	ts_ms() { date -u +%s%3N; }
elif command -v python3 >/dev/null 2>&1; then
	ts_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
else
	die 2 "ts_ms: GNU date(+%s%3N) 또는 python3가 필요하다"
fi

# require_env VAR... — 하나라도 비었으면 사용법과 빠진 이름을 찍고 1
require_env() {
	local v missing=""
	for v in "$@"; do [[ -n "${!v:-}" ]] || missing="$missing $v"; done
	[[ -z "$missing" ]] && return 0
	printf '%s\n' "$USAGE" >&2
	die 1 "필수 환경변수 없음:$missing"
}
require_uint() { local v; for v in "$@"; do [[ "${!v}" =~ ^[0-9]+$ ]] || die 1 "${v}는 0 이상의 정수여야 한다: '${!v}'"; done; }
# run-id·crew-id는 Redis 키와 경로에 그대로 들어간다 (test-plan §2: [A-Za-z0-9-]만)
require_id() { local v; for v in "$@"; do [[ "${!v}" =~ ^[A-Za-z0-9-]+$ ]] || die 1 "$v 형식 위반([A-Za-z0-9-]만): '${!v}'"; done; }

# run 폴더 기본값 — 날짜는 run-id 앞 8자리(시계가 아니라 run-id에서). 맥은 load-test/results, 그 외(EC2)는 ~/dbb-results
default_out_dir() {
	[[ "$RUN_ID" =~ ^[0-9]{8}- ]] || die 1 "RUN_ID가 <YYYYMMDD>-로 시작하지 않아 OUT_DIR 기본값을 만들 수 없다 — OUT_DIR을 직접 준다"
	if [[ "$(uname -s)" == Darwin ]]; then
		printf '%s\n' "$(cd "$DIR/.." && pwd)/results/${RUN_ID:0:8}/$RUN_ID"
	else
		printf '%s\n' "$HOME/dbb-results/${RUN_ID:0:8}/$RUN_ID"
	fi
}

LOG=""
log_line() { printf '%s\n' "$*" >>"$LOG"; }

# 오류 문장을 로그 한 칸으로 — 줄바꿈·쉼표 제거, 호스트명·IP 가림, 200자
clean_err() {
	printf '%s' "${1:-empty}" | tr '\n,' ' ;' \
		| sed -E 's/"[^"]*\.[^"]*"/"<host>"/g; s/(Redis at|server at) [^ :]+/\1 <host>/g; s/([0-9]{1,3}\.){3}[0-9]{1,3}/<ip>/g' \
		| cut -c1-200
}

# 같은 OUT_DIR에서의 중복 시작·덮어쓰기를 거부한다. PID 파일은 종료 때 지운다.
# ps로 확인한다 — kill -0은 root 소유 프로세스(sudo 루프)를 "없음"으로 오판한다.
PIDFILE=""
pid_alive() { [[ -n "$1" ]] && ps -p "$1" >/dev/null 2>&1; }
start_guard() { # $1 이름(PID 파일 접두어) $2 출력 파일
	PIDFILE="$OUT_DIR/$1.pid"
	if [[ -f "$PIDFILE" ]] && pid_alive "$(head -n1 "$PIDFILE")"; then
		die 1 "$1: 이미 실행 중 (pid $(head -n1 "$PIDFILE"), $PIDFILE)"
	fi
	[[ -e "$2" ]] && die 1 "$1: $2 가 이미 있다 — 덮어쓰지 않는다. 새 run-id(OUT_DIR)로 시작한다"
	mkdir -p "$OUT_DIR"
	echo $$ >"$PIDFILE"
	trap 'rm -f "$PIDFILE"' EXIT
}

# 1초 루프: 시작 시각(TICK0) 기준 다음 눈금까지 잔다. 누적 지연이 없고, 밀리면 놓친 눈금은 건너뛴다.
# 각 행의 시각은 눈금이 아니라 실제 ts_ms 값으로 기록한다 (C8 §4).
TICK0=""
sleep_to_next_tick() {
	local now n d s
	now=$(ts_ms)
	n=$(((now - TICK0) / 1000 + 1))
	d=$((TICK0 + n * 1000 - now))
	printf -v s '%d.%03d' $((d / 1000)) $((d % 1000))
	sleep "$s"
}

# ---- 폴러 종료 계약 (C8 §3-5, measurement-contract §1) ----
# K6_DONE_FILE 내용 (box-app-stop.sh가 쓴다, 한 줄에 key=value):
#   k6_end_ms=<맥에서 잰 실제 k6 종료 UTC epoch ms>  offset_ms=<G6 오프셋 = EC2 − 맥>  written_ms=<EC2에서 쓴 시각>
# 마감(EC2 시계) = k6_end_ms + offset_ms + MAX_WAIT_SEC*1000. 표식을 받은 시각부터 다시 세지 않는다.
# 리더(IS_LEADER=1: DB 팔 poll-db, Redis 팔 poll-redis)만 확정을 판정하고 사유를 END_FILE에 쓴다.
# 나머지 폴러는 END_FILE·STOPFILE·시간 초과를 따른다.
IS_LEADER=0
GRACE_MS=5000 # 확정 뒤 "약 5초" 더 수집
DEADLINE=""
CONFIRM_MS=""
DONE_BAD=""
END_EXTRA=""

poller_files() { # OUT_DIR 확정 뒤 호출
	K6_DONE_FILE=${K6_DONE_FILE:-$OUT_DIR/k6-done.txt}
	STOPFILE=${STOPFILE:-$OUT_DIR/stopfile.txt}
	END_FILE=$OUT_DIR/poll-end.txt
}

read_done() { # 표식을 처음 읽을 때 마감을 계산하고 DONE_SEEN 줄을 남긴다
	local now=$1 k6 off wr k6e
	[[ -f "$K6_DONE_FILE" ]] || return 0
	k6=$(sed -n 's/^k6_end_ms=//p' "$K6_DONE_FILE")
	off=$(sed -n 's/^offset_ms=//p' "$K6_DONE_FILE")
	wr=$(sed -n 's/^written_ms=//p' "$K6_DONE_FILE")
	if ! [[ "$k6" =~ ^[0-9]{13}$ && "$off" =~ ^-?[0-9]+$ ]]; then
		if [[ -z "$DONE_BAD" ]]; then
			DONE_BAD=1
			log_line "WARN ts_poller_ms=$now k6-done 형식 오류(k6_end_ms·offset_ms) — 종료 판정 없이 계속 수집"
		fi
		return 0
	fi
	k6e=$((k6 + off))
	DEADLINE=$((k6e + MAX_WAIT_SEC * 1000))
	[[ "$wr" =~ ^[0-9]{13}$ ]] && wr="$wr deliver_delay_ms=$((wr - k6e))" || wr="NA"
	log_line "DONE_SEEN ts_poller_ms=$now k6_end_ms=$k6 offset_ms=$off k6_end_ec2_ms=$k6e written_ms=$wr seen_delay_ms=$((now - k6e)) deadline_ms=$DEADLINE"
}

# $1 = 이번 표본 시각, $2 = 이번 반복의 확정 조건(1/0 — 리더만 의미, 이번 반복의 성공 표본 기준).
# 끝낼 때면 END 줄을 마지막 줄로 쓰고 exit 0.
end_check() {
	local now=$1 ok=$2 reason=""
	if [[ -f "$STOPFILE" ]]; then
		if grep -q worker-stopped "$STOPFILE"; then reason=worker-stopped; else reason=stopfile; fi
	elif [[ -f "$END_FILE" ]]; then
		reason=$(head -n1 "$END_FILE")
	else
		[[ -n "$DEADLINE" ]] || read_done "$now"
		if [[ -n "$CONFIRM_MS" ]]; then
			if ((now - CONFIRM_MS >= GRACE_MS)); then reason=confirmed; fi
		elif [[ -n "$DEADLINE" ]]; then
			if ((now >= DEADLINE)); then
				reason=timeout
			elif [[ "$IS_LEADER" == 1 && "$ok" == 1 ]]; then
				CONFIRM_MS=$now
				log_line "CONFIRM_AFTER_K6 ts_poller_ms=$now (${GRACE_MS}ms 더 수집 후 종료)"
			fi
		fi
	fi
	[[ -n "$reason" ]] || return 0
	if [[ "$IS_LEADER" == 1 && ! -f "$END_FILE" ]]; then
		printf '%s\n' "$reason" >"$END_FILE.tmp" && mv -f "$END_FILE.tmp" "$END_FILE"
	fi
	log_line "END reason=$reason ts_poller_ms=$now${END_EXTRA:+ $END_EXTRA}"
	exit 0
}

poller_traps() {
	trap 'log_line "STOP ts_poller_ms=$(ts_ms) signal=TERM${END_EXTRA:+ $END_EXTRA}"; exit 0' TERM INT
}

# ---- 줄 앞에 "<epoch_ms> <run-id> "를 붙이는 장기 실행 명령 (mpstat·vmstat) ----
# stamp_run <로그> <all|data> <실패 코드> <명령...>
#   all: 모든 줄에 붙인다. data: 공백 뒤 숫자로 시작하는 줄(데이터)에만 붙이고 헤더는 원본 그대로.
#   "Linux <커널> (<호스트명>) …" 배너의 호스트명은 가린다 (EC2 호스트명에 사설 IP가 들어 있다).
#   SIGTERM → 자식 종료 후 STOP 줄, 0. 명령이 스스로 끝나면 END 줄과 <실패 코드>.
stamp_run() {
	local log=$1 mode=$2 fail=$3 buf=""
	shift 3
	command -v stdbuf >/dev/null 2>&1 && buf="stdbuf -oL" # 파이프에서도 줄 단위로 내보내게 (GNU coreutils)
	trap 'pkill -TERM -P $$ 2>/dev/null || true; wait 2>/dev/null || true; printf "%s %s STOP signal=TERM\n" "$(ts_ms)" "$RUN_ID" >>"$log"; exit 0' TERM INT
	LC_ALL=C $buf "$@" 2>&1 | while IFS= read -r line; do
		if [[ "$line" == Linux\ * ]]; then line="${line%%\(*}(host)${line#*)}"; fi
		if [[ "$mode" == all || "$line" =~ ^[[:space:]]*[0-9] ]]; then
			printf '%s %s %s\n' "$(ts_ms)" "$RUN_ID" "$line"
		else
			printf '%s\n' "$line"
		fi
	done >>"$log" &
	wait $! || true
	printf '%s %s END reason=command-exited\n' "$(ts_ms)" "$RUN_ID" >>"$log"
	exit "$fail"
}
