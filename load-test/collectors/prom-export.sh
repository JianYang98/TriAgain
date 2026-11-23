#!/usr/bin/env bash
# run 구간 Prometheus 덤프 (맥). query_range step=1s, queries.promql 한 줄 = 파일 하나. 이 덤프가 정본이다 (C8 §3-2).
set -euo pipefail
USAGE='사용법: START_AT=<시간대 포함 시작 시각> END_AT=<시간대 포함 종료 시각> OUT_DIR=<run 폴더> prom-export.sh
  2026-11-20T12:08:21.215+09:00 같은 형식을 사용한다. 앞뒤 공백은 제거한다 (python3 필요).
  숫자 입력: START_MS=<13자리 UTC epoch ms> END_MS=<13자리 UTC epoch ms> OUT_DIR=<run 폴더> prom-export.sh
  필요한 경우 종료 시각에 여유를 두어 마지막 scrape까지 포함한다.
  선택: PROM_URL(기본 http://127.0.0.1:9090) QUERIES(기본 이 폴더의 queries.promql)
  출력: $OUT_DIR/prom-<이름>.json (Prometheus API 응답 그대로). 실패한 쿼리는 prom-<이름>.json.failed로 남긴다.
  판정: HTTP≠200 또는 status≠success → 실패. 결과가 비어도 실패. 단 node_* 이름은 경고.
  OUT_DIR에 prom-*.json·prom-*.json.failed가 하나라도 있으면 아무것도 쓰지 않고 1 (덮어쓰기 방지, 강제 옵션 없음).
  재추출은 새 하위 폴더로: OUT_DIR="<run 폴더>/reexport-$(date -u +%Y%m%dT%H%M%SZ)"
  범위가 11000초(약 3시간)를 넘으면 Prometheus가 step=1 요청을 거절한다.
  종료 코드: 0 전부 성공 · 1 입력 오류 · 2 하나 이상 실패 · 3 node_* 경고만 있음'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env OUT_DIR
if [[ -n "${START_AT:-}${END_AT:-}" ]]; then
	if [[ -n "${START_MS:-}${END_MS:-}" ]]; then
		die 1 "START_AT·END_AT 또는 START_MS·END_MS 중 한 쌍만 지정하세요."
	fi
	command -v python3 >/dev/null 2>&1 || die 1 "START_AT·END_AT을 변환하려면 python3가 필요합니다."
	range_ms=$(python3 - "${START_AT:-}" "${END_AT:-}" <<'PY'
from datetime import datetime, timezone
import re
import sys

timestamp = (
    r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
    r"(?:\.[0-9]{1,3})?(?:Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])"
)
epoch = datetime(1970, 1, 1, tzinfo=timezone.utc)
values = []
for name, raw in zip(("START_AT", "END_AT"), sys.argv[1:]):
    value = raw.strip()
    if not value:
        sys.exit(f"{name}에 시각을 지정하세요.")
    if not re.fullmatch(timestamp, value):
        sys.exit(f"{name}는 2026-11-20T12:08:21.215+09:00처럼 시간대를 포함해야 합니다.")
    time_format = "%Y-%m-%dT%H:%M:%S" + (".%f" if "." in value else "") + "%z"
    try:
        elapsed = datetime.strptime(value, time_format) - epoch
    except ValueError:
        sys.exit(f"{name}에 유효한 날짜와 시각을 지정하세요.")
    values.append((elapsed.days * 86400 + elapsed.seconds) * 1000 + elapsed.microseconds // 1000)
print(*values)
PY
	) || exit 1
	read -r START_MS END_MS <<< "$range_ms"
else
	require_env START_MS END_MS
fi
[[ "$START_MS" =~ ^[0-9]{13}$ && "$END_MS" =~ ^[0-9]{13}$ ]] || die 1 "START_MS·END_MS는 13자리 UTC epoch ms"
((10#$END_MS > 10#$START_MS)) || die 1 "종료 시각은 시작 시각보다 늦어야 합니다."
PROM_URL=${PROM_URL:-http://127.0.0.1:9090}
QUERIES=${QUERIES:-$DIR/queries.promql}
[[ -f "$QUERIES" ]] || die 1 "쿼리 파일 없음: $QUERIES"
command -v jq >/dev/null 2>&1 || die 2 "jq가 필요하다"
if compgen -G "$OUT_DIR/prom-*.json" >/dev/null || compgen -G "$OUT_DIR/prom-*.json.failed" >/dev/null; then
	die 1 "이미 prom 덤프가 있다 — 덮어쓰지 않는다: $OUT_DIR
  재추출은 새 하위 폴더로: OUT_DIR=\"<run 폴더>/reexport-\$(date -u +%Y%m%dT%H%M%SZ)\""
fi
mkdir -p "$OUT_DIR"

sec() { local ms=$((10#$1)); printf '%d.%03d' "$((ms / 1000))" "$((ms % 1000))"; }
START=$(sec "$START_MS")
END=$(sec "$END_MS")
fails=0 warns=0 ok=0
while IFS= read -r line || [[ -n "$line" ]]; do
	[[ -z "${line// /}" || "$line" == \#* ]] && continue
	name=${line%%|*}
	expr=${line#*|}
	[[ "$name" =~ ^[A-Za-z0-9_]+$ && "$expr" != "$line" ]] || die 1 "쿼리 줄 형식 오류(이름|식): $line"
	f=$OUT_DIR/prom-$name.json
	code=$(curl -sS --max-time 30 -o "$f" -w '%{http_code}' -G "$PROM_URL/api/v1/query_range" \
		--data-urlencode "query=$expr" --data-urlencode "start=$START" --data-urlencode "end=$END" \
		--data-urlencode "step=1") || code=000
	status=$(jq -r '.status // "?"' "$f" 2>/dev/null || echo "?")
	series=$(jq -r '.data.result | length' "$f" 2>/dev/null || echo 0)
	why=""
	if [[ "$code" != 200 ]]; then why="HTTP $code"
	elif [[ "$status" != success ]]; then why="status=$status $(jq -r '.error // ""' "$f" 2>/dev/null | cut -c1-120)"
	elif [[ "$series" == 0 ]]; then why="빈 결과"
	fi
	if [[ -z "$why" ]]; then
		ok=$((ok + 1))
		echo "OK   $name series=$series"
		continue
	fi
	mv -f "$f" "$f.failed"
	if [[ "$name" == node_* ]]; then
		warns=$((warns + 1)); echo "WARN $name — $why (I5 비차단)"
	else
		fails=$((fails + 1)); echo "FAIL $name — $why"
	fi
done <"$QUERIES"
echo "== prom-export: ok=$ok fail=$fails warn=$warns · 구간 $START_MS~$END_MS · $OUT_DIR"
((fails == 0)) || exit 2
((warns == 0)) || exit 3
