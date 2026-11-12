#!/usr/bin/env bash
# I2 — RDS Enhanced Monitoring 1초 로그에서 run 구간 추출 (사용자 PC, AWS 자격이 있는 곳). 비차단 (C8 §3-3).
set -euo pipefail
USAGE='사용법: START_MS=… END_MS=… DB_INSTANCE_ID=… OUT_DIR=… rds-os-export.sh [--dry-run]
  1) aws rds describe-db-instances로 DbiResourceId(= 로그 스트림 이름)를 구한다
  2) aws logs filter-log-events --log-group-name RDSOSMetrics --log-stream-names <DbiResourceId>
     --start-time START_MS --end-time END_MS → $OUT_DIR/rds-os-metrics.json
     (AWS CLI v2의 기본 자동 페이지 처리가 nextToken을 끝까지 따라간다 — --no-paginate를 주지 않는다)
  --dry-run: 실제 호출 없이 실행할 명령만 출력한다.
  레코드 수와 첫·끝 레코드의 timestamp 필드를 출력한다. 자격증명 파일은 열거나 출력하지 않는다.
  종료 코드: 0 · 1 입력 오류 · 3 경고(aws 없음·자격 없음·호출 실패·로그 비어 있음 — 진행을 막지 않는다)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

DRY=""
[[ "${1:-}" == --dry-run ]] && DRY=1
[[ $# -eq 0 || -n "$DRY" ]] || usage
require_env START_MS END_MS DB_INSTANCE_ID OUT_DIR
[[ "$START_MS" =~ ^[0-9]{13}$ && "$END_MS" =~ ^[0-9]{13}$ ]] || die 1 "START_MS·END_MS는 13자리 UTC epoch ms"
((END_MS > START_MS)) || die 1 "END_MS가 START_MS보다 커야 한다"
[[ "$DB_INSTANCE_ID" =~ ^[A-Za-z0-9-]+$ ]] || die 1 "DB_INSTANCE_ID 형식 위반: '$DB_INSTANCE_ID'"
OUT=$OUT_DIR/rds-os-metrics.json
warn() { echo "rds-os-export: $* (비차단 — run card에 사유)" >&2; exit 3; }
mask() { sed -E 's/[0-9]{12}/<account>/g; s/arn:aws:[^ ]*/<arn>/g' | head -n1 | cut -c1-200; } # 오류 문장의 계정 id·ARN을 가린다

describe=(aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE_ID"
	--query 'DBInstances[0].DbiResourceId' --output text)
filter() { filter_cmd=(aws logs filter-log-events --log-group-name RDSOSMetrics --log-stream-names "$1"
	--start-time "$START_MS" --end-time "$END_MS" --output json); }

if [[ -n "$DRY" ]]; then
	filter '<DbiResourceId>'
	printf '[dry-run]'; printf ' %q' "${describe[@]}"; echo
	printf '[dry-run]'; printf ' %q' "${filter_cmd[@]}"; echo " > $OUT"
	exit 0
fi

command -v aws >/dev/null 2>&1 || warn "aws CLI 없음"
command -v jq >/dev/null 2>&1 || warn "jq 없음"
if ! rid=$("${describe[@]}" 2>&1); then warn "describe-db-instances 실패: $(printf '%s' "$rid" | mask)"; fi
[[ "$rid" =~ ^db-[A-Za-z0-9]+$ ]] || warn "DbiResourceId 형식이 예상과 다르다: $(printf '%s' "$rid" | mask)"
mkdir -p "$OUT_DIR"
filter "$rid"
if ! err=$("${filter_cmd[@]}" 2>&1 >"$OUT"); then warn "filter-log-events 실패: $(printf '%s' "$err" | mask)"; fi
n=$(jq '.events | length' "$OUT" 2>/dev/null || echo 0)
((n > 0)) || warn "로그가 비어 있다 (구간·Enhanced Monitoring 켜짐 여부 확인, G11)"
first=$(jq -r '.events[0].message | fromjson | .timestamp' "$OUT" 2>/dev/null || echo "?")
last=$(jq -r '.events[-1].message | fromjson | .timestamp' "$OUT" 2>/dev/null || echo "?")
echo "rds-os-export: records=$n first.timestamp=$first last.timestamp=$last → $OUT"
