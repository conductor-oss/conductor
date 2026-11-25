#!/usr/bin/env bash
# Start the authenticated HTTP/MCP fixtures; --check only checks existing services.
set -euo pipefail
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
conductor_dir=$(cd "$script_dir/../../.." && pwd)
export CONDUCTOR_RECORDINGS_DIR="$conductor_dir/llm-recordings"
playback_dir=${CONDUCTOR_PLAYBACK_WORK_DIR:-"$PWD/tmp/agent-playback"}

check_services() {
  curl --fail --silent --show-error --max-time 3 \
    -H 'Authorization: Bearer playback-test-key' -H 'Content-Type: application/json' \
    --data '{"text":"hello world"}' http://localhost:3001/api/string/reverse > /dev/null &&
  curl --fail --silent --show-error --max-time 3 \
    -H 'Authorization: Bearer playback-test-key' -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"sdk-playback","version":"1"}}}' \
    http://localhost:3001/mcp > /dev/null &&
  curl --fail --silent --show-error --max-time 3 \
    -H 'Authorization: Bearer playback-test-key' \
    'http://localhost:3002/users/Conductor/repos?per_page=5&sort=updated' > /dev/null
}

if [[ "${1:-}" == --check ]]; then
  check_services
  exit
fi
if [[ -n "${1:-}" ]]; then
  echo "Unknown option: $1" >&2
  exit 2
fi
command -v mcp-testkit > /dev/null
# Never replace or stop an existing service.
python3 - <<'PY'
import socket
for port in (3001, 3002):
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('127.0.0.1', port))
PY
mkdir -p "$playback_dir"
pids=()
cleanup_on_error() {
  result=$?
  if ((result != 0)); then
    for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
  fi
}
trap cleanup_on_error EXIT
nohup python3 "$script_dir/http_fixture.py" < /dev/null > "$playback_dir/http.log" 2>&1 &
pids+=("$!")
echo "$!" > "$playback_dir/http.pid"
nohup mcp-testkit --transport http --host 127.0.0.1 --port 3001 --auth playback-test-key < /dev/null > "$playback_dir/mcp.log" 2>&1 &
pids+=("$!")
echo "$!" > "$playback_dir/mcp.pid"
for attempt in $(seq 1 30); do
  for pid in "${pids[@]}"; do
    if ! kill -0 "$pid" 2>/dev/null; then
      cat "$playback_dir/http.log" "$playback_dir/mcp.log"
      exit 1
    fi
  done
  if check_services > "$playback_dir/services-check.log" 2>&1; then
    echo 'Authenticated HTTP and MCP fixtures are ready on ports 3001 and 3002.'
    exit 0
  fi
  sleep 1
done
cat "$playback_dir/services-check.log"
exit 1
