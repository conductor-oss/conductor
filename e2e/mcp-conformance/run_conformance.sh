#!/bin/bash
# Runs the MCP conformance suite (2026-07-28 requirements) against a running Conductor server
# with the MCP server enabled. Registers the fixture tools first.
# The e2e/run_tests*.sh scripts stop their server when they end, so start one yourself, from the
# repository root:
#   COMPOSE_FILE=docker/docker-compose-es8.yaml:docker/docker-compose-e2e.yaml docker compose up -d
#   ./e2e/mcp-conformance/run_conformance.sh
#   COMPOSE_FILE=docker/docker-compose-es8.yaml:docker/docker-compose-e2e.yaml docker compose down -v
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONDUCTOR_URL="${CONDUCTOR_URL:-http://localhost:8000}"
CONFORMANCE_VERSION="0.2.0-alpha.12"

echo "Registering fixture tools (greet, slow_compute, failing_job) at $CONDUCTOR_URL ..."
curl -sf -X PUT "$CONDUCTOR_URL/api/metadata/workflow" \
    -H "Content-Type: application/json" \
    --data @"$SCRIPT_DIR/fixtures.json" > /dev/null

npx --yes "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION" server \
    --url "$CONDUCTOR_URL/api/mcp" \
    --requirements 2026-07-28 \
    --expected-failures "$SCRIPT_DIR/baseline.yml"
