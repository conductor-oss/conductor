#!/usr/bin/env bash
# Start a fresh playback server using this checkout’s JAR and recordings.
set -euo pipefail
conductor_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
port=${CONDUCTOR_PLAYBACK_PORT:-18080}
playback_dir=${CONDUCTOR_PLAYBACK_WORK_DIR:-"$PWD/tmp/agent-playback"}
mkdir -p "$playback_dir"
playback_dir=$(cd "$playback_dir" && pwd)
if [[ -e "$playback_dir/server.pid" || -e "$playback_dir/playback.db" ]]; then
  echo "Use a fresh CONDUCTOR_PLAYBACK_WORK_DIR for each playback run" >&2
  exit 1
fi
export CONDUCTOR_RECORDINGS_DIR="$conductor_dir/llm-recordings"
export CONDUCTOR_SECRET_GITHUB_TOKEN=playback-test-key
export CONDUCTOR_SECRET_HTTP_TEST_API_KEY=playback-test-key
export CONDUCTOR_SECRET_MCP_TEST_API_KEY=playback-test-key
java -Xmx2g -jar "$conductor_dir"/server/build/libs/*-boot.jar \
  --server.port="$port" \
  --spring.datasource.url="jdbc:sqlite:$playback_dir/playback.db" \
  --conductor.ai.enable-llm-mocks=true \
  --conductor.ai.recordings-directory="$CONDUCTOR_RECORDINGS_DIR" \
  --conductor.ai.outbound.allowed-origins=http://localhost:3001,http://localhost:3002 \
  --conductor.ai.outbound.allow-private-networks=true \
  > "$playback_dir/server.log" 2>&1 &
echo $! > "$playback_dir/server.pid"
for attempt in $(seq 1 90); do
  if curl -fsS "http://localhost:$port/health" > /dev/null 2>&1; then
    exit 0
  fi
  if ! kill -0 "$(cat "$playback_dir/server.pid")" 2>/dev/null; then
    cat "$playback_dir/server.log"
    exit 1
  fi
  sleep 2
done
cat "$playback_dir/server.log"
echo 'Conductor did not become ready' >&2
exit 1
