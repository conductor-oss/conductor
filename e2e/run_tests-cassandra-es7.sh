#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# docker compose reads COMPOSE_FILE; the e2e overlay turns on the features the tests need.
export COMPOSE_FILE="$SCRIPT_DIR/../docker/docker-compose-cassandra-es7.yaml:$SCRIPT_DIR/../docker/docker-compose-e2e.yaml"
export SERVER_ROOT_URI="${SERVER_ROOT_URI:-http://localhost:8000}"

echo "Starting Conductor (Cassandra + Elasticsearch 7)..."
# CI builds the server image once (build-server-image job) and pre-loads it;
# SKIP_SERVER_BUILD=1 skips the per-flavor rebuild of the identical image.
if [ "${SKIP_SERVER_BUILD:-0}" != "1" ]; then
    docker compose build conductor-server
fi
docker compose up -d

echo "Waiting for Conductor server at $SERVER_ROOT_URI/health ..."
for i in $(seq 1 60); do
    if curl -sf "$SERVER_ROOT_URI/health" > /dev/null 2>&1; then
        echo "Conductor is up after ${i} attempt(s)!"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "ERROR: Conductor did not start in time"
        docker compose logs conductor-server 2>/dev/null || docker compose logs
        docker compose down -v
        exit 1
    fi
    echo "  Attempt $i/60 — waiting 5s..."
    sleep 5
done

cd "$SCRIPT_DIR/.."
# ai: config-cassandra-es7.properties sets conductor.integrations.ai.enabled=false (cassandra has
#     no skill DAOs), so the agent endpoints do not exist on this flavor.
# filestorage: the /api/files resource is not available on the cassandra server.
./gradlew :conductor-e2e:test -PrunE2E -DSERVER_ROOT_URI="$SERVER_ROOT_URI" -DE2E_DISABLED_CAPABILITIES=ai,filestorage "$@"
EXIT_CODE=$?

docker compose down -v
exit $EXIT_CODE
