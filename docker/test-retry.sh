#!/usr/bin/env bash
# test-retry.sh — retest only the four previously failing configs
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=(); FAIL=()

wait_healthy() {
  local port="${1:-8000}" timeout=240 elapsed=0
  echo "  waiting for http://localhost:$port/health ..."
  while [ $elapsed -lt $timeout ]; do
    local resp
    resp=$(curl -sf "http://localhost:$port/health" 2>/dev/null || true)
    if echo "$resp" | grep -q '"healthy":true'; then
      echo "  healthy after ${elapsed}s"
      return 0
    fi
    sleep 5; elapsed=$((elapsed + 5))
  done
  echo "  TIMEOUT after ${timeout}s"
  return 1
}

run_compose() {
  local label="$1" file="$2" port="${3:-8000}"
  echo ""; echo "========================================"; echo "  $label"; echo "========================================"
  cd "$DOCKER_DIR"
  docker compose -f "$file" down -v --remove-orphans 2>/dev/null || true
  docker compose -f "$file" up -d 2>&1 | tail -8

  if wait_healthy "$port"; then
    local wf
    wf=$(curl -sf "http://localhost:$port/api/metadata/workflow" 2>/dev/null || true)
    if echo "$wf" | python3 -c "import json,sys; json.load(sys.stdin)" 2>/dev/null; then
      echo "  /api/metadata/workflow: OK"
      PASS+=("$label")
    else
      echo "  /api/metadata/workflow FAIL: ${wf:0:120}"
      docker compose -f "$file" logs conductor-server 2>&1 | tail -20
      FAIL+=("$label")
    fi
  else
    docker compose -f "$file" logs 2>&1 | tail -40
    FAIL+=("$label")
  fi
  docker compose -f "$file" down -v --remove-orphans 2>&1 | tail -3
}

run_compose "3. Redis + OpenSearch2" docker-compose-redis-os.yaml     8000
run_compose "4. Redis + OpenSearch3" docker-compose-redis-os3.yaml    8000
run_compose "6. Postgres + ES7"      docker-compose-postgres-es7.yaml 8000
run_compose "8. Cassandra + ES7"     docker-compose-cassandra-es7.yaml 8000

echo ""; echo "========================================"; echo "  RESULTS"; echo "========================================"
echo "PASS (${#PASS[@]}):"; for s in "${PASS[@]}"; do echo "  ✓  $s"; done
echo "FAIL (${#FAIL[@]}):"; for s in "${FAIL[@]}"; do echo "  ✗  $s"; done
[ ${#FAIL[@]} -eq 0 ] && exit 0 || exit 1
