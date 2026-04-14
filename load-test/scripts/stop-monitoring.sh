#!/bin/bash
# ============================================================
# Prometheus + Grafana 종료 + 정리
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MONITORING_DIR="$SCRIPT_DIR/../monitoring"

echo "=== TriAgain Monitoring Cleanup ==="

docker stop triagain-prometheus triagain-grafana 2>/dev/null || true
docker rm triagain-prometheus triagain-grafana 2>/dev/null || true
docker network rm triagain-loadtest 2>/dev/null || true
rm -f "$MONITORING_DIR/prometheus-resolved.yml"

echo "모니터링 종료 및 정리 완료"
