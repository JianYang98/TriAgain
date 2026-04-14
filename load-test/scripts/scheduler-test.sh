#!/bin/bash
# ============================================================
# 단일 스케줄러 수동 트리거 + 측정
#
# 사용법:
#   ./scheduler-test.sh http://<EC2_HOST>:8080 fail-expired XL
#
# 지원 스케줄러:
#   fail-expired, activate-crews, complete-crews,
#   send-reminders, crew-start-notifications, expire-upload-sessions
# ============================================================

set -euo pipefail

SERVER_URL="${1:?Usage: $0 <SERVER_URL> <SCHEDULER_NAME> [SCALE]}"
SCHEDULER_NAME="${2:?Usage: $0 <SERVER_URL> <SCHEDULER_NAME> [SCALE]}"
SCALE="${3:-unknown}"
WINDOW_MS=300000  # 5분

echo ""
echo "=== Scheduler Test ==="
echo "Server: $SERVER_URL"
echo "Name:   $SCHEDULER_NAME"
echo "Scale:  $SCALE"
echo ""

# 스케줄러 트리거
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$SERVER_URL/internal/scheduler/$SCHEDULER_NAME")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: HTTP $HTTP_CODE"
    echo "$BODY"
    exit 1
fi

# 응답 파싱
DURATION_MS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['durationMs'])" 2>/dev/null || echo "-1")
STARTED_AT=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['startedAt'])" 2>/dev/null || echo "?")
FINISHED_AT=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['finishedAt'])" 2>/dev/null || echo "?")

# 5분 윈도우 판정
if [ "$DURATION_MS" -lt "$WINDOW_MS" ]; then
    RESULT="PASS"
else
    RESULT="FAIL (exceeded 5m window)"
fi

echo "Started:  $STARTED_AT"
echo "Finished: $FINISHED_AT"
echo "Duration: ${DURATION_MS} ms"
echo "Window:   ${WINDOW_MS} ms (5m)"
echo "Result:   $RESULT"
echo ""
