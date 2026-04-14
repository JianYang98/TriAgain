#!/bin/bash
# ============================================================
# 스케줄러 S -> M -> L -> XL 단계별 자동 테스트
#
# 각 스케일에서:
#   1. loadtest-sched-* 데이터만 초기화 (04 API 데이터 보호)
#   2. 해당 스케일로 05_challenges_scheduler.sql 재생성
#   3. scheduler-test.sh로 트리거 + 측정
#
# 사용법:
#   ./scheduler-progression.sh http://<EC2_HOST>:8080 "postgresql://user:pass@host:5432/db" fail-expired
# ============================================================

set -euo pipefail

SERVER_URL="${1:?Usage: $0 <SERVER_URL> <DB_URL> <SCHEDULER_NAME>}"
DB_URL="${2:?Usage: $0 <SERVER_URL> <DB_URL> <SCHEDULER_NAME>}"
SCHEDULER_NAME="${3:?Usage: $0 <SERVER_URL> <DB_URL> <SCHEDULER_NAME>}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_DIR="$SCRIPT_DIR/../sql"

echo "=============================================="
echo " Scheduler Progression Test"
echo " Server:    $SERVER_URL"
echo " Scheduler: $SCHEDULER_NAME"
echo "=============================================="
echo ""

for SCALE in S M L XL; do
    echo "----------------------------------------------"
    echo " Scale: $SCALE"
    echo "----------------------------------------------"

    # 1. loadtest-sched-* 데이터만 초기화
    #    loadtest-crew-* (04 API 테스트용)은 절대 건드리지 않음
    echo "Resetting loadtest-sched-* data..."
    psql "$DB_URL" -q <<'RESET_SQL'
DELETE FROM challenges WHERE crew_id LIKE 'loadtest-sched-%';
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-sched-%';
DELETE FROM crews WHERE id LIKE 'loadtest-sched-%';
RESET_SQL

    # 2. 해당 스케일로 스케줄러 테스트 데이터 생성
    echo "Inserting scale=$SCALE data..."
    psql "$DB_URL" -q \
        -c "SET app.scale = '$SCALE'" \
        -f "$SQL_DIR/05_challenges_scheduler.sql"

    # 3. 스케줄러 트리거 + 측정
    "$SCRIPT_DIR/scheduler-test.sh" "$SERVER_URL" "$SCHEDULER_NAME" "$SCALE"

    echo ""
done

echo "=============================================="
echo " Progression Test Complete"
echo "=============================================="
