#!/usr/bin/env bash
#
# A2A interop showcase — Conductor calling a REAL, non-Conductor agent.
#
# Starts the official a2a-sdk reference "echo" agent (a genuine third-party A2A server), then runs a
# Conductor workflow that (1) discovers the agent's Agent Card (GET_AGENT_CARD) and (2) calls it
# (AGENT) — proving end-to-end interop over the real A2A wire protocol.
#
# Requires: Java 21+, curl, and either `uv` (to auto-create a Python venv) OR set A2A_VENV to a
# Python that already has `a2a-sdk` + `uvicorn`. No Docker, no Redis, no API keys.
# Usage:    ./run-interop-demo.sh
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE" && git rev-parse --show-toplevel)"
PORT=7002
AGENT_PORT=9998
DB="/tmp/conductor-a2a-interop.db"
SERVER_LOG="/tmp/conductor-a2a-interop.server.log"
AGENT_LOG="/tmp/conductor-a2a-interop.agent.log"
VENV="${A2A_VENV:-/tmp/a2a-interop-venv}"
SERVER_PID=""
AGENT_PID=""

say() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }

cleanup() {
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true
  [ -n "$AGENT_PID" ] && kill "$AGENT_PID" 2>/dev/null || true
}
trap cleanup EXIT

wf_status() { curl -sS "localhost:$PORT/api/workflow/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])'; }

# ── 0. fresh state ────────────────────────────────────────────────────────────────────────
say "Cleaning previous demo state"
rm -f "$DB"* "$SERVER_LOG" "$AGENT_LOG"

# ── 1. a real, non-Conductor A2A agent on the official a2a-sdk ──────────────────────────────
if [ ! -x "$VENV/bin/python" ]; then
  command -v uv >/dev/null 2>&1 || {
    echo "Need 'uv' to create the agent venv (https://docs.astral.sh/uv/), or set A2A_VENV to a"
    echo "Python that already has a2a-sdk + uvicorn installed."; exit 1; }
  say "Creating a Python venv with the official a2a-sdk (one-time)"
  uv venv --python 3.12 "$VENV"
  uv pip install --python "$VENV" "a2a-sdk>=0.2,<0.3" uvicorn
fi
PYTHON="$VENV/bin/python"

say "Starting the REAL a2a-sdk echo agent on :$AGENT_PORT (a non-Conductor A2A server)"
A2A_AGENT_PORT="$AGENT_PORT" AGENT_MODE=task "$PYTHON" "$HERE/../echo_agent.py" > "$AGENT_LOG" 2>&1 &
AGENT_PID=$!
for _ in $(seq 1 40); do
  curl -sS -m2 -o /dev/null "localhost:$AGENT_PORT/.well-known/agent-card.json" 2>/dev/null && break
  sleep 0.3
done
ok "Echo agent up — card: http://localhost:$AGENT_PORT/.well-known/agent-card.json"

# ── 2. build + start Conductor (SQLite = zero external deps) ─────────────────────────────────
say "Building the Conductor server jar (this branch, with A2A)"
"$ROOT/gradlew" -p "$ROOT" :conductor-server:bootJar -q
JAR="$(ls "$ROOT"/server/build/libs/*-boot.jar | head -1)"

say "Starting Conductor on :$PORT"
java -Dserver.port="$PORT" \
     -Dspring.datasource.url="jdbc:sqlite:$DB?busy_timeout=15000&journal_mode=WAL" \
     -Dconductor.integrations.ai.enabled=true \
     -Dconductor.a2a.client.allow-private-network=true \
     -jar "$JAR" > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 60); do
  curl -sS -m2 -o /dev/null "localhost:$PORT/health" 2>/dev/null && { ok "Conductor is up on :$PORT"; break; }
  sleep 1
done

# ── 3. register + run the interop workflow ───────────────────────────────────────────────────
say "Registering the a2a_interop_echo workflow (GET_AGENT_CARD → AGENT)"
curl -sS -X POST "localhost:$PORT/api/metadata/workflow" \
     -H 'Content-Type: application/json' -d @"$HERE/a2a_interop_echo.json" >/dev/null
ok "Registered"

say "Running it against the real agent at http://localhost:$AGENT_PORT"
WFID="$(curl -sS -X POST "localhost:$PORT/api/workflow/a2a_interop_echo" \
     -H 'Content-Type: application/json' \
     -d "{\"agentUrl\":\"http://localhost:$AGENT_PORT\",\"prompt\":\"convert 100 USD to EUR\"}")"
ok "Started — workflowId=$WFID"

# ── 4. wait for completion + show what the real agent returned ───────────────────────────────
say "Waiting for the workflow to COMPLETE"
for _ in $(seq 1 30); do
  S="$(wf_status "$WFID")"; { [ "$S" = "COMPLETED" ] || [ "$S" = "FAILED" ]; } && break; sleep 1
done
echo
curl -sS "localhost:$PORT/api/workflow/$WFID" | python3 -c '
import sys, json
wf = json.load(sys.stdin)
print("  workflow status :", wf["status"])
for t in wf.get("tasks", []):
    out = t.get("outputData") or {}
    if t.get("taskType") == "GET_AGENT_CARD":
        card = out.get("agentCard") or out
        print("  discovered agent:", (card.get("name") if isinstance(card, dict) else card))
    if t.get("taskType") == "AGENT":
        print("  agent state     :", out.get("state"))
        print("  agent reply     :", out.get("text"))
'
[ "$(wf_status "$WFID")" = "COMPLETED" ] \
  && ok "Conductor discovered and called a real third-party A2A agent. That is A2A interop." \
  || { echo "Demo did not complete — see $SERVER_LOG and $AGENT_LOG"; exit 1; }
