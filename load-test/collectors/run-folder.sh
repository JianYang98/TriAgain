#!/usr/bin/env bash
# run 폴더 생성 (테스트 EC2 / 맥). 맥에서만 run-card.md를 만든다 — EC2 폴더를 맥으로 rsync해 합칠 때 덮어쓰지 않게.
set -euo pipefail
USAGE='사용법: RUN_ID=<YYYYMMDD>-<HHMM>-<db|rd>-<rps> ARM=db|rd RPS=50|200 run-folder.sh
  선택: OUT_DIR(기본: 맥 load-test/results/<YYYYMMDD>/<RUN_ID>/, EC2 ~/dbb-results/<YYYYMMDD>/<RUN_ID>/ — 날짜는 RUN_ID 앞 8자리)
  만드는 것: $OUT_DIR, $OUT_DIR/warmup/, (맥) $OUT_DIR/run-card.md (run-card.template.md, 이미 있으면 두고 알린다)
  마지막 줄에 OUT_DIR 절대 경로를 출력한다 → OUT_DIR=$(… run-folder.sh | tail -n1)
  종료 코드: 0 · 1 입력 오류'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID ARM RPS
require_id RUN_ID
require_uint RPS
[[ "$ARM" == db || "$ARM" == rd ]] || die 1 "ARM은 db|rd: '$ARM'"
[[ "$RUN_ID" =~ ^[0-9]{8}-[0-9]{4}-(db|rd)-[0-9]+$ ]] || echo "주의: RUN_ID가 <YYYYMMDD>-<HHMM>-<arm>-<rps> 형식이 아니다 (test-plan §2): $RUN_ID" >&2
OUT_DIR=${OUT_DIR:-$(default_out_dir)}
mkdir -p "$OUT_DIR/warmup"
OUT_DIR=$(cd "$OUT_DIR" && pwd)
if [[ "$(uname -s)" == Darwin ]]; then
	if [[ -e "$OUT_DIR/run-card.md" ]]; then
		echo "run-card.md가 이미 있다 — 그대로 둔다" >&2
	else
		sed -e "s|{{RUN_ID}}|$RUN_ID|g" -e "s|{{ARM}}|$ARM|g" -e "s|{{RPS}}|$RPS|g" \
			"$DIR/run-card.template.md" >"$OUT_DIR/run-card.md"
		echo "run-card.md 생성" >&2
	fi
fi
echo "$OUT_DIR"
