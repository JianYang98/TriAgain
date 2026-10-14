#!/usr/bin/env bash
# 테스트 EC2 한 줄 종료 래퍼 — k6 종료 직후 호출. 순서: 표식 전달 → TCP after·약 10초 뒤 루프 정지
#   → 폴러가 §3-5 조건으로 스스로 끝날 때까지 대기 → CPU 수집 정지. 한꺼번에 끄지 않는다 (C8 §3-5).
set -euo pipefail
USAGE='사용법 (테스트 EC2): RUN_ID=… K6_END_MS=<맥에서 잰 실제 k6 종료 UTC epoch ms> CLOCK_OFFSET_MS=<G6 offset_ms(EC2−맥)> box-app-stop.sh
  K6_END_MS: 맥에서 k6가 끝나고 전송 요청 종료를 확인한 직후 python3 -c '"'"'import time;print(int(time.time()*1000))'"'"'
  CLOCK_OFFSET_MS: clock-check.sh의 offset_ms
  선택: OUT_DIR(box-app-start.sh와 같은 값) K6_DONE_FILE(폴러와 같은 값) TCP_TAIL_SEC(기본 10) POLL_CAP_SEC(기본 600)
  표식 $OUT_DIR/k6-done.txt: k6_end_ms · offset_ms · written_ms. 폴러 마감 = k6_end_ms + offset_ms + MAX_WAIT_SEC.
  폴러가 POLL_CAP_SEC 안에 끝나지 않으면 SIGTERM(STOP 줄)으로 멈추고 코드 2.
  종료 코드: 0 · 1 입력 오류(표식이 이미 있음 포함) · 2 폴러 강제 정지 · 3 비차단 경고(TCP·CPU)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID K6_END_MS CLOCK_OFFSET_MS
require_id RUN_ID
[[ "$K6_END_MS" =~ ^[0-9]{13}$ ]] || die 1 "K6_END_MS는 13자리 UTC epoch ms: '$K6_END_MS'"
[[ "$CLOCK_OFFSET_MS" =~ ^-?[0-9]+$ ]] || die 1 "CLOCK_OFFSET_MS는 정수(ms): '$CLOCK_OFFSET_MS'"
OUT_DIR=${OUT_DIR:-$(default_out_dir)}
export OUT_DIR # 자식 tcp-collect.sh stop이 같은 폴더를 쓴다 (기본값을 계산한 경우에도)
[[ -d "$OUT_DIR" ]] || die 1 "OUT_DIR 없음: $OUT_DIR"
poller_files
CAP=${POLL_CAP_SEC:-600}
require_uint CAP
[[ -e "$K6_DONE_FILE" ]] && die 1 "표식이 이미 있다: $K6_DONE_FILE — 다시 쓰지 않는다"

now=$(ts_ms)
printf 'k6_end_ms=%s\noffset_ms=%s\nwritten_ms=%s\n' "$K6_END_MS" "$CLOCK_OFFSET_MS" "$now" >"$K6_DONE_FILE.tmp"
mv -f "$K6_DONE_FILE.tmp" "$K6_DONE_FILE"
echo "== 1. k6 종료 표식 전달 ($K6_DONE_FILE) — 전달 지연 $((now - K6_END_MS - CLOCK_OFFSET_MS))ms"

rc=0
pid_of() { [[ -f "$OUT_DIR/$1.pid" ]] && cut -d' ' -f1 "$OUT_DIR/$1.pid" || true; }
if [[ -f "$OUT_DIR/tcp-collect.pid" ]]; then
	echo "== 2. TCP after 스냅샷 → ${TCP_TAIL_SEC:-10}초 뒤 루프 정지 → tcp-delta"
	"$DIR/tcp-collect.sh" stop || { echo "WARN tcp-collect stop 코드 $?" >&2; rc=3; }
else
	echo "== 2. TCP 수집 기록 없음 (start가 실패했거나 미실행) — 건너뜀"
	rc=3
fi

echo "== 3. 폴러 종료 대기 (확정·시간 초과·STOPFILE, 최대 ${CAP}초)"
start=$(ts_ms)
while :; do
	alive=""
	for p in poll-db poll-lock poll-redis; do pid_alive "$(pid_of "$p")" && alive="$alive $p"; done
	[[ -z "$alive" ]] && break
	el=$((($(ts_ms) - start) / 1000))
	if ((el >= CAP)); then
		echo "FAIL 폴러가 ${CAP}초 안에 끝나지 않았다:$alive — SIGTERM" >&2
		for p in $alive; do kill -TERM "$(pid_of "$p")" 2>/dev/null || true; done
		sleep 2
		rc=2
		break
	fi
	((el % 10 == 0)) && echo "   대기 중:$alive (${el}s)"
	sleep 1
done
for p in poll-db poll-lock poll-redis; do
	[[ -f "$OUT_DIR/$p.log" ]] && echo "   $p: $(tail -n 1 "$OUT_DIR/$p.log")"
done
[[ -f "$OUT_DIR/poll-db.log" ]] && command grep -E '^(T_DB_CONFIRM|DONE_SEEN)' "$OUT_DIR/poll-db.log" | sed 's/^/   poll-db: /' || true

echo "== 4. CPU 수집 정지 (mpstat·vmstat)"
for p in mpstat vmstat; do
	pid=$(pid_of "$p")
	if pid_alive "$pid"; then
		kill -TERM "$pid"
		for _ in 1 2 3 4 5 6 7 8 9 10; do pid_alive "$pid" || break; sleep 0.5; done
		echo "   $p 정지"
	else
		echo "   $p 실행 중 아님" >&2
		[[ "$rc" == 0 ]] && rc=3
	fi
done
if [[ -f "$OUT_DIR/poll-redis.log" ]]; then
	echo "== Redis 박스의 mpstat-run.sh·vmstat-run.sh도 지금 멈춘다: kill -TERM \$(cat <Redis 박스 run 폴더>/mpstat-redis.pid <…>/vmstat-redis.pid)"
fi
exit "$rc"
