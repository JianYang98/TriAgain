#!/usr/bin/env bash
# N1~N3 — TCP 보조 계측 (앱 EC2, sudo 필요, 비차단). measurement-contract §2-N, C8 §3-1.
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… APP_CONTAINER=…(start만) [PORT=8080] [TCP_TAIL_SEC=10] tcp-collect.sh start|stop
  읽는 곳은 앱 컨테이너의 네트워크 네임스페이스다 (호스트의 8080은 docker-proxy라 다른 큐가 보인다).
  start: before 스냅샷(컨테이너 nstat -asz · ss -s, 호스트 nstat -asz) → 1초 루프를 백그라운드로.
         루프 한 줄: "<UTC epoch ms> <run-id> acceptQ=<Recv-Q>/<Send-Q>" (리슨 소켓 없으면 acceptQ=NA)
  stop : k6 종료 직후 호출 — after 스냅샷 즉시 → TCP_TAIL_SEC초 뒤 루프 정지(STOP 줄) → tcp-delta.sh
  출력: tcp-listen.log nstat-before.txt nstat-after.txt nstat-host-before.txt nstat-host-after.txt
        ss-s-before.txt ss-s-after.txt tcp-delta.txt (+ tcp-collect.pid 실행 중 상태, tcp-collect.stderr.log)
  nstat은 -s로 history 파일을 건드리지 않는다(값은 -az와 같은 절대값 전체).
  종료 코드: 0 성공 · 1 사용법/중복 시작 · 3 경고(sudo 비밀번호 필요, nsenter·ss·nstat·docker 없음,
             앱 컨테이너 없음, 스냅샷 실패 — 설치하지 않는다, 진행을 막지 않는다)'
DIR=$(cd "$(dirname "$0")" && pwd)
SELF=$DIR/$(basename "$0")
# shellcheck source=common.sh
source "$DIR/common.sh"

[[ $# -ge 1 ]] || usage
MODE=$1
PORT=${PORT:-8080}
[[ "$PORT" =~ ^[0-9]+$ ]] || die 1 "PORT는 숫자: '$PORT'"

# 내부 모드: sudo nsenter로 컨테이너 netns 안에서 root로 돈다. stdout = tcp-listen.log
# 반복마다 ss 1회 + 시각 1회만 실행한다 (sudo·nsenter를 매초 띄우지 않는다).
if [[ "$MODE" == __loop ]]; then
	trap 'printf "%s %s STOP signal=TERM\n" "$(ts_ms)" "$RUN_ID"; exit 0' TERM INT
	TICK0=$(ts_ms)
	while :; do
		ts=$(ts_ms)
		q=NA rq="" sq=""
		out=$(ss -lnt "sport = :$PORT" 2>/dev/null) || out=""
		case "$out" in *$'\n'*) read -r _ rq sq _ <<<"${out#*$'\n'}" || true ;; esac # 둘째 줄의 둘째·셋째 열
		if [[ "$rq" =~ ^[0-9]+$ && "$sq" =~ ^[0-9]+$ ]]; then q=$rq/$sq; fi
		printf '%s %s acceptQ=%s\n' "$ts" "$RUN_ID" "$q"
		sleep_to_next_tick
	done
fi

require_env RUN_ID OUT_DIR
require_id RUN_ID
STATE=$OUT_DIR/tcp-collect.pid # "<루프 pid> <컨테이너 pid>"
warn() { echo "tcp-collect: $* (비차단 — run card에 사유를 적고 진행)" >&2; exit 3; }

check_tools() {
	local c missing=""
	for c in sudo nsenter ss nstat docker; do command -v "$c" >/dev/null 2>&1 || missing="$missing $c"; done
	[[ -z "$missing" ]] || warn "명령 없음:$missing — 설치하지 않는다"
	sudo -n true 2>/dev/null || warn "sudo가 비밀번호를 요구한다 (sudo -n true 실패)"
}
ns() { sudo -n nsenter -t "$CPID" -n "$@"; }

case "$MODE" in
start)
	require_env APP_CONTAINER # stop은 tcp-collect.pid의 컨테이너 pid를 쓴다
	check_tools
	if [[ -f "$STATE" ]] && pid_alive "$(cut -d' ' -f1 "$STATE")"; then die 1 "tcp-collect: 이미 실행 중 ($STATE)"; fi
	[[ -e "$OUT_DIR/tcp-listen.log" ]] && die 1 "tcp-collect: $OUT_DIR/tcp-listen.log 가 이미 있다 — 덮어쓰지 않는다"
	CPID=$(sudo -n docker inspect -f '{{.State.Pid}}' "$APP_CONTAINER" 2>/dev/null) || CPID=""
	[[ "$CPID" =~ ^[1-9][0-9]*$ ]] || warn "앱 컨테이너 '$APP_CONTAINER'가 없거나 실행 중이 아니다 (State.Pid='$CPID')"
	mkdir -p "$OUT_DIR"
	ns nstat -asz >"$OUT_DIR/nstat-before.txt" || warn "컨테이너 netns nstat 실패"
	nstat -asz >"$OUT_DIR/nstat-host-before.txt" || warn "호스트 nstat 실패"
	ns ss -s >"$OUT_DIR/ss-s-before.txt" || warn "컨테이너 netns ss -s 실패"
	# setsid: 터미널과 분리해 sudo가 pty를 잡거나 백그라운드에서 멈추지 않게 한다 (util-linux, 없으면 생략)
	SETSID=""
	command -v setsid >/dev/null 2>&1 && SETSID=setsid
	$SETSID sudo -n nsenter -t "$CPID" -n env RUN_ID="$RUN_ID" PORT="$PORT" bash "$SELF" __loop \
		</dev/null >>"$OUT_DIR/tcp-listen.log" 2>>"$OUT_DIR/tcp-collect.stderr.log" &
	echo "$! $CPID" >"$STATE"
	echo "tcp-collect: 시작 (루프 pid $!, 컨테이너 pid $CPID, port $PORT)"
	;;
stop)
	[[ -f "$STATE" ]] || warn "시작 기록이 없다 ($STATE) — start가 실패했거나 이미 멈췄다"
	read -r LPID CPID <"$STATE"
	check_tools
	rc=0
	ns nstat -asz >"$OUT_DIR/nstat-after.txt" || { echo "tcp-collect: 컨테이너 netns nstat after 실패" >&2; rc=3; }
	nstat -asz >"$OUT_DIR/nstat-host-after.txt" || { echo "tcp-collect: 호스트 nstat after 실패" >&2; rc=3; }
	ns ss -s >"$OUT_DIR/ss-s-after.txt" || { echo "tcp-collect: ss -s after 실패" >&2; rc=3; }
	echo "tcp-collect: after 스냅샷 완료 — ${TCP_TAIL_SEC:-10}초 뒤 루프 정지"
	sleep "${TCP_TAIL_SEC:-10}"
	if pid_alive "$LPID"; then
		sudo -n kill -TERM "$LPID" 2>/dev/null || true # 루프는 root(sudo) 소유
		for _ in 1 2 3 4 5 6 7 8 9 10; do pid_alive "$LPID" || break; sleep 0.5; done
		pid_alive "$LPID" && { echo "tcp-collect: 루프(pid $LPID)가 멈추지 않았다" >&2; rc=3; }
	else
		echo "tcp-collect: 루프가 이미 끝나 있었다 (pid $LPID)" >&2
		rc=3
	fi
	rm -f "$STATE"
	"$DIR/tcp-delta.sh" "$OUT_DIR" || rc=3
	exit "$rc"
	;;
*) usage ;;
esac
