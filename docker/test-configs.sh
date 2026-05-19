#!/usr/bin/env bash
# test-configs.sh — run conductor against every docker-compose config and verify health
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=()
FAIL=()
SKIP=()

wait_healthy() {
  local name="$1" port="${2:-8080}" timeout=180 elapsed=0
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

smoke_test() {
  local port="${1:-8080}"
  local wf
  wf=$(curl -sf "http://localhost:$port/api/metadata/workflow" 2>/dev/null || true)
  if echo "$wf" | python3 -c "import json,sys; json.load(sys.stdin)" 2>/dev/null; then
    echo "  /api/metadata/workflow: OK"
    return 0
  fi
  echo "  /api/metadata/workflow: FAIL (got: ${wf:0:120})"
  return 1
}

run_compose() {
  local label="$1" file="$2" port="${3:-8000}"
  echo ""
  echo "========================================"
  echo "  $label"
  echo "  file: $file"
  echo "========================================"

  cd "$DOCKER_DIR"
  docker compose -f "$file" down -v --remove-orphans 2>/dev/null || true

  if ! docker compose -f "$file" up -d 2>&1 | tail -5; then
    echo "  docker compose up FAILED"
    FAIL+=("$label")
    return
  fi

  if wait_healthy "$label" "$port" && smoke_test "$port"; then
    PASS+=("$label")
  else
    docker compose -f "$file" logs conductor-server 2>&1 | tail -30
    FAIL+=("$label")
  fi

  docker compose -f "$file" down -v --remove-orphans 2>&1 | tail -3
}

# ── 0. SQLite default (no compose, bare docker run) ─────────────────────────
echo ""
echo "========================================"
echo "  0. SQLite default (no CONFIG_PROP)"
echo "========================================"
docker rm -f conductor-sqlite-test 2>/dev/null || true
docker run -d --name conductor-sqlite-test -p 8080:8080 conductor:server
if wait_healthy "sqlite" 8080 && smoke_test 8080; then
  PASS+=("0. SQLite default")
else
  docker logs conductor-sqlite-test 2>&1 | tail -30
  FAIL+=("0. SQLite default")
fi
docker rm -f conductor-sqlite-test 2>/dev/null || true

# ── 1–9. Compose setups ──────────────────────────────────────────────────────
run_compose "1. Redis + ES7"         docker-compose.yaml              8000
run_compose "2. Redis + ES8"         docker-compose-es8.yaml          8000
run_compose "3. Redis + OpenSearch2" docker-compose-redis-os.yaml     8000
run_compose "4. Redis + OpenSearch3" docker-compose-redis-os3.yaml    8000
run_compose "5. Postgres"            docker-compose-postgres.yaml     8000
run_compose "6. Postgres + ES7"      docker-compose-postgres-es7.yaml 8000
run_compose "7. MySQL + Redis + ES7" docker-compose-mysql.yaml        8000
run_compose "8. Cassandra + ES7"     docker-compose-cassandra-es7.yaml 8000

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  RESULTS"
echo "========================================"
echo "PASS (${#PASS[@]}):"
for s in "${PASS[@]}"; do echo "  ✓  $s"; done
echo "FAIL (${#FAIL[@]}):"
for s in "${FAIL[@]}"; do echo "  ✗  $s"; done
[ ${#FAIL[@]} -eq 0 ] && exit 0 || exit 1
