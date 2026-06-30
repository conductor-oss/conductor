#!/usr/bin/env bash
#
# Durable A2A — the money shot.
#
# Places an order through a Conductor workflow that calls a remote A2A "restaurant" agent
# (AGENT). While the order is being prepared, we KILL the Conductor server, then RESTART it.
# Because the workflow/task state is durably persisted (SQLite here), the order RESUMES and
# COMPLETES after the restart. An in-memory A2A host (the reference samples) would have lost it.
#
# Requires: Java 21+, python3, curl. No Docker, no Redis, no API keys.
# Usage:    ./run-durable-demo.sh
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE" && git rev-parse --show-toplevel)"
PORT=7001
DB="/tmp/conductor-durable-demo.db"
SERVER_LOG="/tmp/conductor-durable-demo.server.log"
SELLER_DELAY="${SELLER_DELAY:-45}"   # seconds the order stays "in preparation"
SERVER_PID=""
SELLER_PID=""

say() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }

cleanup() {
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true
  [ -n "$SELLER_PID" ] && kill "$SELLER_PID" 2>/dev/null || true
}
trap cleanup EXIT

wf_status() { curl -sS "localhost:$PORT/api/workflow/$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])'; }

wait_health() {
  for _ in $(seq 1 60); do
    curl -sS -m2 -o /dev/null "localhost:$PORT/health" 2>/dev/null && { ok "Conductor is up on :$PORT"; return; }
    sleep 1
  done
  echo "Conductor did not become healthy — see $SERVER_LOG"; exit 1
}

start_server() {
  java -Dserver.port="$PORT" \
       -Dspring.datasource.url="jdbc:sqlite:$DB?busy_timeout=15000&journal_mode=WAL" \
       -Dconductor.integrations.ai.enabled=true \
       -Dconductor.a2a.client.allow-private-network=true \
       -jar "$JAR" > "$SERVER_LOG" 2>&1 &
  SERVER_PID=$!
}

# ── 0. fresh state ────────────────────────────────────────────────────────────────────────
say "Cleaning previous demo state"
rm -f "$DB"* "$SERVER_LOG"

# ── 1. start the remote A2A seller agent (separate process — survives Conductor's restart) ──
say "Starting the remote A2A restaurant agent (SELLER_DELAY=${SELLER_DELAY}s)"
SELLER_DELAY="$SELLER_DELAY" python3 "$HERE/seller_agent.py" > /tmp/conductor-durable-demo.seller.log 2>&1 &
SELLER_PID=$!
for _ in $(seq 1 20); do curl -sS -m2 -o /dev/null localhost:9999/.well-known/agent.json 2>/dev/null && break; sleep 0.3; done
ok "Restaurant agent up on :9999"

# ── 2. build + start Conductor (SQLite = durable, zero external deps) ────────────────────────
say "Building the Conductor server-lite jar (this branch, with A2A)"
"$ROOT/gradlew" -p "$ROOT" :conductor-server-lite:bootJar -q
JAR="$(ls "$ROOT"/server-lite/build/libs/*.jar | head -1)"
say "Starting Conductor (SQLite at $DB)"
start_server
wait_health

# ── 3. register the concierge workflow ──────────────────────────────────────────────────────
say "Registering the durable_purchase workflow"
curl -sS -X POST "localhost:$PORT/api/metadata/workflow" \
     -H 'Content-Type: application/json' -d @"$HERE/durable_purchase.json"
ok "Registered"

# ── 4. place an order ─────────────────────────────────────────────────────────────────────
say "Placing an order (the concierge calls the restaurant agent via AGENT)"
WFID="$(curl -sS -X POST "localhost:$PORT/api/workflow/durable_purchase" \
     -H 'Content-Type: application/json' \
     -d '{"orderText":"1 large pepperoni pizza","agentUrl":"http://localhost:9999"}')"
ok "Order started — workflowId=$WFID"

# ── 5. wait until the order is being prepared (AGENT IN_PROGRESS) ───────────────────────
say "Waiting for the order to be in preparation (workflow RUNNING, task IN_PROGRESS)"
for _ in $(seq 1 30); do
  [ "$(wf_status "$WFID")" = "RUNNING" ] && break; sleep 1
done
ok "Order in preparation. Status=$(wf_status "$WFID")"

# ── 6. 💥 CRASH ─────────────────────────────────────────────────────────────────────────────
say "💥 Killing the Conductor server MID-ORDER (kill -9)"
kill -9 "$SERVER_PID"; SERVER_PID=""
sleep 2
ok "Conductor is DOWN. The order is in flight; an in-memory host would have just lost it."

# ── 7. restart on the same SQLite store ──────────────────────────────────────────────────────
say "Restarting Conductor on the SAME persistent store"
start_server
wait_health

# ── 8. the order resumes and completes ───────────────────────────────────────────────────────
say "Waiting for the order to COMPLETE after the restart"
for _ in $(seq 1 60); do
  S="$(wf_status "$WFID")"; [ "$S" = "COMPLETED" ] || [ "$S" = "FAILED" ] && break; sleep 2
done
echo
curl -sS "localhost:$PORT/api/workflow/$WFID" | python3 -c '
import sys, json
wf = json.load(sys.stdin)
print("  workflow status :", wf["status"])
print("  receipt         :", (wf.get("output") or {}).get("receipt"))
'
[ "$(wf_status "$WFID")" = "COMPLETED" ] \
  && ok "ORDER SURVIVED THE CRASH AND COMPLETED. That is durable A2A." \
  || { echo "Demo did not complete — see $SERVER_LOG"; exit 1; }
