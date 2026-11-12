#!/usr/bin/env bash
# G6 — 맥 ↔ 테스트 EC2 시각 오프셋 (맥에서 실행). measurement-contract §1 시계 규칙.
set -euo pipefail
USAGE='사용법: APP_SSH_TARGET=<ssh 대상(예: ~/.ssh/config의 별칭)> clock-check.sh
  선택: SSH(기본 ssh. 예: SSH="ssh -i <키 파일>") SAMPLES(기본 5)
  표본마다: 맥 시각 t0 → ssh로 EC2 `date -u +%s%3N` → 맥 시각 t1. 오프셋 = EC2 − (t0+t1)/2, 왕복 = t1 − t0.
  첫 ssh 연결로 ControlMaster를 열어 두 번째부터는 핸드셰이크 없이 잰다 (오차 ≤ 왕복/2).
  출력: 표본별 rtt_ms·offset_ms, 왕복이 가장 짧은 표본의 offset_ms(= box-app-stop.sh의 CLOCK_OFFSET_MS),
        EC2 chronyc tracking 요약(없으면 그 사실). 접속 대상·IP는 출력하지 않는다.
  종료 코드: 0 · 1 입력 오류 · 2 ssh 실패'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

require_env APP_SSH_TARGET
SAMPLES=${SAMPLES:-5}
require_uint SAMPLES
CTL=$(mktemp -d "${TMPDIR:-/tmp}/dbb-clock.XXXXXX")
# shellcheck disable=SC2206 # SSH는 사용자가 준 명령 문자열(단어 분리)
ssh_cmd=(${SSH:-ssh} -o BatchMode=yes -o ControlMaster=auto -o "ControlPath=$CTL/cm" -o ControlPersist=60)
cleanup() { "${ssh_cmd[@]}" -O exit "$APP_SSH_TARGET" >/dev/null 2>&1 || true; rm -rf "$CTL"; }
trap cleanup EXIT

"${ssh_cmd[@]}" "$APP_SSH_TARGET" true >/dev/null 2>&1 || die 2 "ssh 실패 (키·대상·BatchMode 확인)"
best_rtt="" best_off=""
for ((i = 1; i <= SAMPLES; i++)); do
	t0=$(ts_ms)
	e=$("${ssh_cmd[@]}" "$APP_SSH_TARGET" 'date -u +%s%3N' 2>/dev/null) || die 2 "ssh 실패 (표본 $i)"
	t1=$(ts_ms)
	[[ "$e" =~ ^[0-9]{13}$ ]] || die 2 "EC2 시각 형식 오류 (GNU date가 아닌가?): '$e'"
	rtt=$((t1 - t0))
	off=$((e - (t0 + t1) / 2))
	echo "sample=$i rtt_ms=$rtt offset_ms=$off"
	if [[ -z "$best_rtt" ]] || ((rtt < best_rtt)); then best_rtt=$rtt best_off=$off; fi
done
echo "offset_ms=$best_off (EC2 − 맥, 왕복 최소 표본 rtt_ms=$best_rtt, 오차 ≤ ±$((best_rtt / 2))ms)"
abs=${best_off#-}
if ((abs <= 200)); then echo "판정: ±200ms 이내 — run card에 값만 기록"; else echo "판정: ±200ms 초과 — 보정해서 보고 (measurement-contract §1)"; fi
echo "== EC2 chronyc tracking"
"${ssh_cmd[@]}" "$APP_SSH_TARGET" 'command -v chronyc >/dev/null && chronyc tracking || echo "chronyc 없음"' 2>/dev/null |
	command grep -E 'System time|Last offset|RMS offset|Leap status|Stratum|chronyc 없음' || echo "chronyc 출력 없음"
