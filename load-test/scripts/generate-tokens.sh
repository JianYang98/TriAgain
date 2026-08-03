#!/bin/bash
# ============================================================
# 부하테스트용 JWT 토큰 발급 스크립트
# test-login API를 호출하여 유저별 accessToken을 tokens.csv에 저장
#
# 사용법:
#   ./generate-tokens.sh <서버URL> <유저수>
#   ./generate-tokens.sh http://localhost:8080 50
#
# 선행 조건:
#   1. SQL 스크립트로 loadtest-user-N 유저가 DB에 존재해야 함
#   2. 서버가 loadtest 프로필로 기동 중이어야 함 (24시간 토큰)
#      java -jar triagain.jar --spring.profiles.active=local,loadtest
#   3. jq 설치 필요 (brew install jq)
#
# API 스펙 (POST /auth/test-login):
#   Request:  { "userId": "loadtest-user-1" }
#   Response: { "code": "OK", "data": { "accessToken": "eyJ...", ... } }
# ============================================================

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "사용법: $0 <서버URL> <유저수>"
    echo "예시:  $0 http://localhost:8080 50"
    exit 1
fi

SERVER_URL="${1%/}"  # trailing slash 제거
USER_COUNT="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_FILE="$SCRIPT_DIR/../tokens.csv"

# jq 설치 확인
if ! command -v jq &> /dev/null; then
    echo "ERROR: jq가 설치되어 있지 않습니다. brew install jq"
    exit 1
fi

# 서버 헬스체크
echo "서버 연결 확인: $SERVER_URL/actuator/health"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$SERVER_URL/actuator/health" 2>/dev/null || true)
if [ "$HTTP_STATUS" != "200" ]; then
    echo "WARNING: 서버 헬스체크 실패 (HTTP $HTTP_STATUS). 계속 진행합니다..."
fi

echo "token" > "$OUTPUT_FILE"

SUCCESS=0
FAIL=0

echo "토큰 발급 시작: $USER_COUNT 명"
for i in $(seq 1 "$USER_COUNT"); do
    USER_ID="loadtest-user-$i"

    RESPONSE=$(curl -s -X POST "$SERVER_URL/auth/test-login" \
        -H "Content-Type: application/json" \
        -d "{\"userId\": \"$USER_ID\"}" 2>/dev/null)

    TOKEN=$(echo "$RESPONSE" | jq -r '.data.accessToken // empty' 2>/dev/null)

    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        echo "$TOKEN" >> "$OUTPUT_FILE"
        SUCCESS=$((SUCCESS + 1))
    else
        ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // .error // "unknown"' 2>/dev/null)
        echo "  FAIL: $USER_ID — $ERROR_MSG"
        FAIL=$((FAIL + 1))
    fi

    # 진행률 표시 (50명마다)
    if [ $((i % 50)) -eq 0 ]; then
        echo "  진행: $i / $USER_COUNT"
    fi
done

echo ""
echo "완료: 성공=$SUCCESS, 실패=$FAIL"
echo "토큰 파일: $OUTPUT_FILE"
echo "토큰 수: $(tail -n +2 "$OUTPUT_FILE" | wc -l | tr -d ' ')"
