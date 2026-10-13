#!/usr/bin/env bash
# 맥 Prometheus·Grafana 종료 (세션 끝, prom-export.sh 덤프 뒤). TSDB 폴더(PROM_DATA_DIR)는 지우지 않는다.
set -euo pipefail
USAGE='사용법: prometheus-down.sh
  선택: CONTAINER_PREFIX(기본 dbb) — prometheus-up.sh와 같은 값
  <접두어>-grafana·<접두어>-prometheus를 멈춘다(--rm이라 함께 지워진다). 네트워크는
  prometheus-up.sh가 만든 것(라벨 dbb.owner=c8-prometheus-up)만, 남은 컨테이너가 없을 때 지운다.
  TSDB 폴더가 남으므로 덤프(prom-export.sh)는 끈 뒤에도 같은 폴더로 다시 띄워 할 수 있다.
  종료 코드: 0 · 1 사용법 · 2 컨테이너가 멈추지 않음'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

[[ $# -eq 0 ]] || usage
PREFIX=${CONTAINER_PREFIX:-dbb}
PROM=$PREFIX-prometheus
GRAF=$PREFIX-grafana

nets=""
if docker container inspect "$PROM" >/dev/null 2>&1; then
	nets=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' "$PROM")
fi
for c in "$GRAF" "$PROM"; do
	if docker container inspect "$c" >/dev/null 2>&1; then
		docker stop -t 30 "$c" >/dev/null && echo "== $c 멈춤"
	else
		echo "== $c 없음"
	fi
done
for c in "$GRAF" "$PROM"; do # --rm 제거가 끝날 때까지
	for _ in 1 2 3 4 5 6 7 8 9 10; do docker container inspect "$c" >/dev/null 2>&1 || break; sleep 1; done
	docker container inspect "$c" >/dev/null 2>&1 && die 2 "${c}가 아직 남아 있다"
done
for n in $nets; do
	[[ "$(docker network inspect -f '{{index .Labels "dbb.owner"}}' "$n" 2>/dev/null)" == c8-prometheus-up ]] || continue
	if [[ -z "$(docker network inspect -f '{{range .Containers}}x{{end}}' "$n")" ]]; then
		docker network rm "$n" >/dev/null && echo "== 네트워크 $n 삭제"
	else
		echo "== 네트워크 ${n}에 다른 컨테이너가 남아 있어 두었다"
	fi
done
echo "== 완료. TSDB 폴더는 그대로 둔다."
