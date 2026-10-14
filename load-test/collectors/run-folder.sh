#!/usr/bin/env bash
# run 폴더 생성 (테스트 EC2 / 맥). 맥에서만 run-card.md를 만든다 — EC2 폴더를 맥으로 rsync해 합칠 때 덮어쓰지 않게.
set -euo pipefail
USAGE='사용법: RUN_ID=<YYYYMMDD>-<HHMMSS>-<db|rd>-<rps>-<hex4> ARM=db|rd RPS=50|200 run-folder.sh
  RUN_ID: UTC 시각 + 팔 + RPS + 16진 4자리 (예 20261014-103012-db-50-a3f9). 만들기 예:
          RUN_ID=$(date -u +%Y%m%d-%H%M%S)-db-50-$(openssl rand -hex 2)
          형식이 다르거나 RUN_ID의 팔·RPS가 ARM·RPS와 다르면 1.
  선택: OUT_DIR(기본: 맥 load-test/results/<YYYYMMDD>/<RUN_ID>/, EC2 ~/dbb-results/<YYYYMMDD>/<RUN_ID>/ — 날짜는 RUN_ID 앞 8자리)
  OUT_DIR을 주면 마지막 경로가 RUN_ID와 같아야 한다(다르면 1). OUT_DIR가 이미 있고 비어 있지 않으면 1 — 아무것도 만들거나 지우지 않는다 (RUN_ID 재사용 덮어쓰기 방지).
  만드는 것: $OUT_DIR, $OUT_DIR/warmup/, (맥) $OUT_DIR/run-card.md (run-card.template.md)
  마지막 줄에 OUT_DIR 절대 경로를 출력한다 → OUT_DIR=$(… run-folder.sh | tail -n1)
  종료 코드: 0 · 1 입력 오류·이미 있는 run 폴더'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env RUN_ID ARM RPS
require_id RUN_ID
require_uint RPS
[[ "$ARM" == db || "$ARM" == rd ]] || die 1 "ARM은 db|rd: '$ARM'"
[[ "$RUN_ID" =~ ^[0-9]{8}-[0-9]{6}-(db|rd)-([0-9]+)-[0-9a-f]{4}$ ]] ||
	die 1 "RUN_ID 형식 위반: '$RUN_ID' (<YYYYMMDD>-<HHMMSS>-<db|rd>-<rps>-<hex4>, 예 20261014-103012-db-50-a3f9)"
[[ "${BASH_REMATCH[1]}" == "$ARM" && "${BASH_REMATCH[2]}" == "$RPS" ]] ||
	die 1 "RUN_ID의 팔·RPS(${BASH_REMATCH[1]}·${BASH_REMATCH[2]})가 ARM·RPS(${ARM}·${RPS})와 다르다"
# 셸에 남은 이전 run의 OUT_DIR을 그대로 쓰지 않게 — 주어진 OUT_DIR은 마지막 경로가 RUN_ID여야 한다
if [[ -n "${OUT_DIR:-}" && "$(basename "$OUT_DIR")" != "$RUN_ID" ]]; then
	die 1 "OUT_DIR의 마지막 경로($(basename "$OUT_DIR"))가 RUN_ID($RUN_ID)와 다르다 — 이전 run의 OUT_DIR이 남아 있지 않은지 확인한다 (unset OUT_DIR)"
fi
OUT_DIR=${OUT_DIR:-$(default_out_dir)}
if [[ -e "$OUT_DIR" ]] && [[ ! -d "$OUT_DIR" || -n "$(ls -A "$OUT_DIR")" ]]; then
	die 1 "이미 있는 run 폴더 — 새 RUN_ID를 만든다 (아무것도 바꾸지 않았다): $OUT_DIR"
fi
mkdir -p "$OUT_DIR/warmup"
OUT_DIR=$(cd "$OUT_DIR" && pwd)
if [[ "$(uname -s)" == Darwin ]]; then
	sed -e "s|{{RUN_ID}}|$RUN_ID|g" -e "s|{{ARM}}|$ARM|g" -e "s|{{RPS}}|$RPS|g" \
		"$DIR/run-card.template.md" >"$OUT_DIR/run-card.md"
	echo "run-card.md 생성" >&2
fi
echo "$OUT_DIR"
