#!/bin/bash
# ============================================================
# Prometheus + Grafana 시작 (로컬 docker run)
#
# EC2에는 Spring Boot만 실행. 모니터링은 로컬에서 docker run.
# EC2에 모니터링 도구를 올리면 CPU/메모리 지표가 오염됨.
#
# 사용법:
#   로컬 테스트: ./start-monitoring.sh localhost
#   실제 테스트: ./start-monitoring.sh 15.164.69.243
# ============================================================

set -euo pipefail

EC2_HOST="${1:?Usage: $0 <EC2_HOST>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MONITORING_DIR="$SCRIPT_DIR/../monitoring"

echo "=== TriAgain Monitoring Setup ==="
echo "Target: $EC2_HOST:8080"

# 1. prometheus.yml에 EC2 주소 주입
sed "s/EC2_HOST_PLACEHOLDER/$EC2_HOST/g" \
    "$MONITORING_DIR/prometheus.yml" > "$MONITORING_DIR/prometheus-resolved.yml"
echo "prometheus.yml: EC2_HOST=$EC2_HOST 주입 완료"

# 2. Docker network 생성 (Prometheus <-> Grafana 통신)
docker network create triagain-loadtest 2>/dev/null || true

# 3. Prometheus (로컬에서 EC2 scrape)
docker run -d \
    --name triagain-prometheus \
    --network triagain-loadtest \
    -p 9090:9090 \
    -v "$MONITORING_DIR/prometheus-resolved.yml:/etc/prometheus/prometheus.yml:ro" \
    prom/prometheus:latest

echo "Prometheus: http://localhost:9090"

# 4. Grafana (프로비저닝 + 대시보드 자동 로드)
docker run -d \
    --name triagain-grafana \
    --network triagain-loadtest \
    -p 3000:3000 \
    -e "GF_SECURITY_ADMIN_PASSWORD=loadtest" \
    -v "$MONITORING_DIR/grafana/provisioning:/etc/grafana/provisioning:ro" \
    -v "$MONITORING_DIR/grafana/dashboards:/var/lib/grafana/dashboards:ro" \
    grafana/grafana:latest

echo "Grafana:    http://localhost:3000 (admin / loadtest)"
echo ""
echo "=== 모니터링 시작 완료 ==="
echo "종료: ./stop-monitoring.sh"
