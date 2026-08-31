#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OVERRIDE_FILE="$SCRIPT_DIR/docker/docker-compose-e2e-overrides.yaml"
COMPOSE_FILE="$SCRIPT_DIR/../docker/docker-compose.yaml"
export SERVER_ROOT_URI="${SERVER_ROOT_URI:-http://localhost:8000}"

echo "Starting Conductor (Redis + Elasticsearch 7)..."
# CI builds the server image once (build-server-image job) and pre-loads it;
# SKIP_SERVER_BUILD=1 skips the per-flavor rebuild of the identical image.
if [ "${SKIP_SERVER_BUILD:-0}" != "1" ]; then
    docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" build conductor-server
fi
docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" up -d

echo "Waiting for Conductor server at $SERVER_ROOT_URI/health ..."
for i in $(seq 1 60); do
    if curl -sf "$SERVER_ROOT_URI/health" > /dev/null 2>&1; then
        echo "Conductor is up after ${i} attempt(s)!"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "ERROR: Conductor did not start in time"
        docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" logs conductor-server 2>/dev/null || docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" logs
        docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" down -v
        exit 1
    fi
    echo "  Attempt $i/60 — waiting 5s..."
    sleep 5
done

cd "$SCRIPT_DIR/.."
./gradlew :conductor-e2e:test -PrunE2E -DSERVER_ROOT_URI="$SERVER_ROOT_URI" "$@"
EXIT_CODE=$?

docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" down -v
exit $EXIT_CODE
