#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/../docker/docker-compose-postgres.yaml"
export SERVER_ROOT_URI="${SERVER_ROOT_URI:-http://localhost:8000}"

echo "Starting Conductor (Postgres + Elasticsearch 7)..."
docker compose -f "$COMPOSE_FILE" build conductor-server
docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for Conductor server at $SERVER_ROOT_URI/health ..."
for i in $(seq 1 60); do
    if curl -sf "$SERVER_ROOT_URI/health" > /dev/null 2>&1; then
        echo "Conductor is up after ${i} attempt(s)!"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "ERROR: Conductor did not start in time"
        docker compose -f "$COMPOSE_FILE" logs conductor-server 2>/dev/null || docker compose -f "$COMPOSE_FILE" logs
        docker compose -f "$COMPOSE_FILE" down -v
        exit 1
    fi
    echo "  Attempt $i/60 — waiting 5s..."
    sleep 5
done

cd "$SCRIPT_DIR/.."
./gradlew :conductor-e2e:test -PrunE2E -DSERVER_ROOT_URI="$SERVER_ROOT_URI" "$@"
EXIT_CODE=$?

docker compose -f "$COMPOSE_FILE" down -v
exit $EXIT_CODE
