#!/usr/bin/env bash
# scripts/load-test.sh — Local load test orchestrator.
# Usage: TIER=baseline|target|stress ./scripts/load-test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TIER="${TIER:-baseline}"
COMPOSE_FILE="${ROOT}/deploy/docker-compose.yml"
LOG_DIR="${ROOT}/logs"
WORKER_BG_LOG="/tmp/load-test-worker.log"

mkdir -p "${LOG_DIR}"

check_url() {
  local name="$1" url="$2"
  echo "Waiting for ${name} ..."
  for _ in $(seq 1 30); do
    curl -fsS "$url" >/dev/null 2>&1 && echo "  READY ${name}" && return 0
    sleep 3
  done
  echo "  TIMEOUT ${name} (${url})" >&2
  return 1
}

echo "==> Starting platform (tier=${TIER}) ..."
docker compose -f "${COMPOSE_FILE}" up -d

check_url "Temporal UI"    "http://localhost:${TEMPORAL_UI_PORT:-8080}/"
check_url "Grafana"        "http://localhost:${GRAFANA_PORT:-3000}/api/health"
check_url "Prometheus"     "http://localhost:${PROMETHEUS_PORT:-9090}/-/healthy"
check_url "Loki"           "http://localhost:${LOKI_PORT:-3100}/ready"
check_url "OTel Collector" "http://localhost:${OTEL_HEALTH_PORT:-13133}/"

echo "==> Starting worker ..."
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"
: >"${WORKER_BG_LOG}"
./gradlew :worker:run --no-daemon 2>&1 | tee -a "${LOG_DIR}/worker.log" >"${WORKER_BG_LOG}" &
WORKER_PID=$!
trap 'kill -TERM "${WORKER_PID}" 2>/dev/null || true' EXIT

echo "  Waiting for worker to be ready ..."
for _ in $(seq 1 60); do
  grep -q "Worker started" "${WORKER_BG_LOG}" 2>/dev/null && echo "  READY worker" && break
  sleep 1
done

echo "==> Running load tests (tier=${TIER}) ..."
./gradlew :load-tests:loadTest -Ptier="${TIER}" --no-daemon

echo ""
echo "==> Done."
echo "    Gatling report: ${ROOT}/load-tests/build/reports/gatling/"
echo "    k6 summary:     ${ROOT}/load-tests/build/reports/k6/summary.json"
