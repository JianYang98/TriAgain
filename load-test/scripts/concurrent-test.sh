#!/bin/bash
# ============================================================
# k6 마감 피크 + 스케줄러 동시 부하 테스트
#
# 마감 직전에는 API 트래픽(인증 60%)과 스케줄러가 동시에
# DB 커넥션을 먹는다. 이 시나리오를 재현하여 경합을 측정.
#
# 동작:
#   1. k6 load-peak 백그라운드 실행
#   2. 1분 대기 (ramp-up 완료, 부하 충분히 걸린 상태)
#   3. 스케줄러 트리거 (API + 스케줄러 동시 실행)
#   4. k6 종료 대기
#   5. 결과 요약
#
# 사용법:
#   ./concurrent-test.sh http://<EC2_HOST>:8080 L
# ============================================================

set -euo pipefail

SERVER_URL="${1:?Usage: $0 <SERVER_URL> <SCALE>}"
SCALE="${2:?Usage: $0 <SERVER_URL> <SCALE>}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
K6_DIR="$SCRIPT_DIR/../k6"
K6_SUMMARY="$SCRIPT_DIR/../k6/concurrent-summary.json"

echo "=============================================="
echo " Concurrent Test: k6 Peak + Scheduler"
echo " Server: $SERVER_URL"
echo " Scale:  $SCALE"
echo "=============================================="
echo ""

# 1. k6 load-peak 백그라운드 실행
echo "[1/4] k6 load-peak 시작 (백그라운드)..."
k6 run \
    --env BASE_URL="$SERVER_URL" \
    --env SCALE="$SCALE" \
    --summary-export="$K6_SUMMARY" \
    "$K6_DIR/load-peak.js" &
K6_PID=$!
echo "k6 PID: $K6_PID"

# 2. ramp-up 완료 대기 (1분)
echo "[2/4] ramp-up 대기 중 (60초)..."
sleep 60

# 3. 스케줄러 트리거
echo "[3/4] FailExpiredChallengesScheduler 트리거..."
SCHED_RESPONSE=$(curl -s -X POST "$SERVER_URL/internal/scheduler/fail-expired")
SCHED_DURATION=$(echo "$SCHED_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['durationMs'])" 2>/dev/null || echo "?")
echo "스케줄러 완료: ${SCHED_DURATION}ms"

# 4. k6 종료 대기
echo "[4/4] k6 종료 대기..."
wait $K6_PID
K6_EXIT=$?

# 5. 결과 요약
echo ""
echo "=============================================="
echo " Concurrent Test Results"
echo "=============================================="
echo ""
echo "--- Scheduler ---"
echo "Duration: ${SCHED_DURATION} ms"
echo ""

if [ -f "$K6_SUMMARY" ]; then
    echo "--- k6 Summary ---"
    python3 -c "
import json, sys
with open('$K6_SUMMARY') as f:
    d = json.load(f)
metrics = d.get('metrics', {})

req_dur = metrics.get('http_req_duration', {}).get('values', {})
p95 = req_dur.get('p(95)', '?')
p99 = req_dur.get('p(99)', '?')
avg = req_dur.get('avg', '?')

failed = metrics.get('http_req_failed', {}).get('values', {})
err_rate = failed.get('rate', 0)

print(f'p95:       {p95:.1f} ms' if isinstance(p95, float) else f'p95: {p95}')
print(f'p99:       {p99:.1f} ms' if isinstance(p99, float) else f'p99: {p99}')
print(f'avg:       {avg:.1f} ms' if isinstance(avg, float) else f'avg: {avg}')
print(f'Error:     {err_rate*100:.2f}%' if isinstance(err_rate, float) else f'Error: {err_rate}')
" 2>/dev/null || echo "(k6 summary 파싱 실패 — $K6_SUMMARY 직접 확인)"
    echo ""
    rm -f "$K6_SUMMARY"
else
    echo "k6 summary 파일 없음 (exit code: $K6_EXIT)"
fi

echo "=============================================="
