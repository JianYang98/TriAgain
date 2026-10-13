#!/usr/bin/env bash
# 맥에서 Prometheus(1초 scrape) + Grafana(기존 대시보드 복사본) 기동 — C8 §3-2. 세션 동안 켜 두고 run마다 재기동하지 않는다.
set -euo pipefail
USAGE='사용법: APP_PUBLIC_IP=<앱 공인 IP> PROM_DATA_DIR=<TSDB 보존 폴더> prometheus-up.sh
  선택: APP_TARGET(기본 $APP_PUBLIC_IP:8080. 주면 APP_PUBLIC_IP 불필요)
        NODE_TARGETS(기본 host.docker.internal:9100, 쉼표로 여러 개 — 9100=instance app, 9101=instance redis)
        DOCKER_NETWORK(기본 <접두어>-monitoring, 없으면 만든다) CONTAINER_PREFIX(기본 dbb) PROM_RETENTION(기본 14d)
        GF_SECURITY_ADMIN_PASSWORD(주면 Grafana admin 비밀번호로 넘긴다. 익명 Viewer로도 대시보드를 볼 수 있다)
  컨테이너 안의 localhost는 그 컨테이너 자신이다: 맥의 SSH 터널 = host.docker.internal:<port>,
  다른 컨테이너(로컬 앱) = 같은 DOCKER_NETWORK의 <컨테이너 이름>:<port>.
  PROM_DATA_DIR 예: load-test/results/<YYYYMMDD>/prometheus (gitignore 대상) 또는 저장소 밖. 지우지 않는다.
  결과: 127.0.0.1:9090 Prometheus, 127.0.0.1:3000 Grafana (loopback에만 바인드). 끄기: prometheus-down.sh
  종료 코드: 0 · 1 입력 오류 · 2 실행 실패(이름·포트 충돌, promtool 실패, 기동 실패)'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"

PROM_IMAGE=prom/prometheus:v3.15.0  # 2026-09-25 빌드. latest 금지 (C8 §3-2)
GRAFANA_IMAGE=grafana/grafana:13.2.3 # 2026-09-28 빌드

[[ -n "${APP_TARGET:-}${APP_PUBLIC_IP:-}" ]] || { printf '%s\n' "$USAGE" >&2; die 1 "APP_PUBLIC_IP 또는 APP_TARGET이 필요하다"; }
require_env PROM_DATA_DIR
APP_TARGET=${APP_TARGET:-$APP_PUBLIC_IP:8080}
NODE_TARGETS=${NODE_TARGETS:-host.docker.internal:9100}
PREFIX=${CONTAINER_PREFIX:-dbb}
NET=${DOCKER_NETWORK:-$PREFIX-monitoring}
PROM=$PREFIX-prometheus
GRAF=$PREFIX-grafana
OWNER_LABEL=dbb.owner=c8-prometheus-up # prometheus-down.sh는 이 라벨이 붙은 네트워크만 지운다

target_re='^[A-Za-z0-9._-]+:[0-9]+$'
[[ "$APP_TARGET" =~ $target_re ]] || die 1 "APP_TARGET 형식은 host:port: '$APP_TARGET'"
IFS=, read -r -a nodes <<<"$NODE_TARGETS"
node_list=""
for t in "${nodes[@]}"; do
	[[ "$t" =~ $target_re ]] || die 1 "NODE_TARGETS 항목 형식은 host:port: '$t'"
	node_list="$node_list${node_list:+, }'$t'"
done
[[ "$PREFIX" =~ ^[A-Za-z0-9-]+$ && "$NET" =~ ^[A-Za-z0-9_.-]+$ ]] || die 1 "CONTAINER_PREFIX·DOCKER_NETWORK 형식 위반"

# 충돌 확인 — 이미 있는 모니터링 컨테이너·포트는 덮어쓰거나 지우지 않는다
for c in "$PROM" "$GRAF" triagain-prometheus triagain-grafana; do
	if docker container inspect "$c" >/dev/null 2>&1; then
		die 2 "컨테이너 '$c'가 이미 있다 — 덮어쓰거나 지우지 않는다. 직접 확인 후 정리한다 (docker ps -a)"
	fi
done
for p in 9090 3000; do
	if nc -z 127.0.0.1 "$p" >/dev/null 2>&1; then die 2 "127.0.0.1:${p}가 이미 사용 중이다 — 다른 Prometheus/Grafana가 떠 있는지 확인한다"; fi
done

mkdir -p "$PROM_DATA_DIR/data"
PROM_DATA_DIR=$(cd "$PROM_DATA_DIR" && pwd)
CFG=$PROM_DATA_DIR/prometheus.yml
sed -e "s|__APP_TARGET__|$APP_TARGET|" -e "s|__NODE_TARGETS__|$node_list|" "$DIR/prometheus.yml" >"$CFG"

echo "== promtool check config ($PROM_IMAGE)"
docker run --rm --entrypoint promtool -v "$CFG:/etc/prometheus/prometheus.yml:ro" "$PROM_IMAGE" \
	check config /etc/prometheus/prometheus.yml || die 2 "promtool check config 실패 — $CFG"

if ! docker network inspect "$NET" >/dev/null 2>&1; then
	docker network create --label "$OWNER_LABEL" "$NET" >/dev/null
	echo "== 네트워크 $NET 생성"
fi

docker run -d --rm --name "$PROM" --network "$NET" -p 127.0.0.1:9090:9090 \
	-v "$CFG:/etc/prometheus/prometheus.yml:ro" -v "$PROM_DATA_DIR/data:/prometheus" \
	"$PROM_IMAGE" --config.file=/etc/prometheus/prometheus.yml \
	--storage.tsdb.path=/prometheus --storage.tsdb.retention.time="${PROM_RETENTION:-14d}" >/dev/null ||
	die 2 "Prometheus 기동 실패"

# 외부로 나가는 요청(업데이트 확인·사용 통계·플러그인 자동 설치)을 끈다
docker run -d --rm --name "$GRAF" --network "$NET" -p 127.0.0.1:3000:3000 \
	-e DBB_PROM_HOST="$PROM" -e GF_SECURITY_ADMIN_PASSWORD \
	-e GF_AUTH_ANONYMOUS_ENABLED=true -e GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer \
	-e GF_ANALYTICS_REPORTING_ENABLED=false -e GF_ANALYTICS_CHECK_FOR_UPDATES=false \
	-e GF_ANALYTICS_CHECK_FOR_PLUGIN_UPDATES=false -e GF_NEWS_NEWS_FEED_ENABLED=false \
	-e GF_PLUGINS_PREINSTALL_DISABLED=true \
	-v "$DIR/grafana/provisioning:/etc/grafana/provisioning:ro" \
	-v "$DIR/grafana/dashboards:/var/lib/grafana/dashboards:ro" \
	"$GRAFANA_IMAGE" >/dev/null || die 2 "Grafana 기동 실패 (Prometheus는 떠 있다 — prometheus-down.sh로 정리)"

wait_http() { # $1 이름 $2 URL $3 초
	local i
	for ((i = 0; i < $3; i++)); do
		if curl -fsS --max-time 2 "$2" >/dev/null 2>&1; then echo "== $1 준비됨 ($2)"; return 0; fi
		sleep 1
	done
	die 2 "$1가 ${3}초 안에 준비되지 않았다 — docker logs $4"
}
wait_http Prometheus http://127.0.0.1:9090/-/ready 30 "$PROM"
wait_http Grafana http://127.0.0.1:3000/api/health 90 "$GRAF"

echo "== 바인드 (127.0.0.1만이어야 한다)"
docker port "$PROM"
docker port "$GRAF"
echo "== 대상: triagain=$APP_TARGET node=$NODE_TARGETS · 설정 $CFG · TSDB $PROM_DATA_DIR/data (보존 ${PROM_RETENTION:-14d})"
echo "   확인: curl -s http://127.0.0.1:9090/api/v1/targets (health=up, lastError 빈 값) · Grafana http://127.0.0.1:3000"
