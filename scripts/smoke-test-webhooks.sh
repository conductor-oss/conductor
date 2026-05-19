#!/usr/bin/env bash
# =============================================================================
# smoke-test-webhooks.sh — Live end-to-end smoke test for WAIT_FOR_WEBHOOK
#
# Requires: curl, jq
# Usage:
#   ./scripts/smoke-test-webhooks.sh [BASE_URL]
#   BASE_URL defaults to http://localhost:8099
#
# What this tests:
#   1. Basic  — single WAIT_FOR_WEBHOOK task completed by a matching event
#   2. Fan-out — two concurrent instances, one event completes both
#   3. Sequential — two WAIT_FOR_WEBHOOK tasks in series, different events each
#   4. Multi-criteria — partial match ignored; full match completes
#   5. Stale hash — terminated workflow's hash cleaned up; next instance unaffected
# =============================================================================

set -euo pipefail

BASE=${1:-http://localhost:8099}
API="${BASE}/api"
WEBHOOK="${BASE}/webhook"
PASS=0
FAIL=0

# ---- colour helpers ---------------------------------------------------------
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}  PASS${NC}  $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}  FAIL${NC}  $1"; FAIL=$((FAIL+1)); }
banner() { echo -e "\n${YELLOW}━━━  $1  ━━━${NC}"; }

# ---- curl helpers -----------------------------------------------------------
post_json() { curl -sf -X POST -H 'Content-Type: application/json' -d "$2" "$1"; }
get_json()  { curl -sf "$1"; }

# ---- wait for server health -------------------------------------------------
banner "Waiting for Conductor on ${BASE}"
for i in $(seq 1 60); do
  if curl -sf "${BASE}/health" > /dev/null 2>&1; then
    echo "  Server is up (attempt ${i})"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "  Server did not come up in 60s — aborting"; exit 1
  fi
  sleep 1
done

# ---- helpers ----------------------------------------------------------------

# Register a workflow def (POST /api/metadata/workflow for new, PUT for updates)
register_workflow() {
  local def="$1"
  # Try POST (create); if 409 Conflict, fall back to PUT (update array)
  local http
  http=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST -H 'Content-Type: application/json' -d "$def" "${API}/metadata/workflow")
  if [[ "$http" == "409" || "$http" == "500" ]]; then
    curl -sf -X PUT -H 'Content-Type: application/json' -d "[$def]" "${API}/metadata/workflow" \
      > /dev/null || true
  fi
}

# Create a webhook config, return the id
create_webhook_config() {
  local cfg="$1"
  post_json "${API}/metadata/webhook" "$cfg" | jq -r .id
}

# Start a workflow, return the workflow id
start_workflow() {
  local name="$1" version="${2:-1}" input="${3:-{}}"
  post_json "${API}/workflow" \
    "{\"name\":\"${name}\",\"version\":${version},\"input\":${input}}"
}

# Poll until a predicate is true or timeout (seconds)
wait_until() {
  local desc="$1" timeout="$2" check_cmd="$3"
  for i in $(seq 1 "$timeout"); do
    if eval "$check_cmd" > /dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "  TIMEOUT waiting for: $desc" >&2
  return 1
}

# Return task status for the first task of given type/ref in a workflow
task_status() {
  local wf_id="$1" ref="$2"
  get_json "${API}/workflow/${wf_id}?includeTasks=true" \
    | jq -r ".tasks[] | select(.referenceTaskName == \"${ref}\") | .status" 2>/dev/null \
    | head -1
}

# Return overall workflow status
wf_status() { get_json "${API}/workflow/$1" | jq -r .status; }

# Fire a webhook event (POST /webhook/{id})
fire_webhook() {
  local webhook_id="$1" body="$2"
  curl -sf -o /dev/null -w "%{http_code}" \
    -X POST -H 'Content-Type: application/json' -d "$body" \
    "${WEBHOOK}/${webhook_id}"
}

# ============================================================================
# SCENARIO 1 — Basic: single task, matching event → COMPLETED
# ============================================================================
banner "Scenario 1: Basic match"

WF_BASIC='{
  "name": "smoke_basic_wfwh",
  "version": 1,
  "schemaVersion": 2,
  "ownerEmail": "smoke@test.local",
  "tasks": [{
    "name": "WAIT_FOR_WEBHOOK",
    "taskReferenceName": "waitEvent",
    "type": "WAIT_FOR_WEBHOOK",
    "inputParameters": {"matches": {"$['"'"'event'"'"']['"'"'type'"'"']": "smoke_trigger"}}
  }]
}'

WEBHOOK_BASIC='{
  "name": "smoke-basic-hook",
  "verifier": "NONE",
  "receiverWorkflowNamesToVersions": {"smoke_basic_wfwh": 1}
}'

register_workflow "$WF_BASIC"
HOOK_ID=$(create_webhook_config "$WEBHOOK_BASIC")
echo "  webhook id: ${HOOK_ID}"

WF_ID=$(start_workflow "smoke_basic_wfwh" 1 '{"orderId":"smoke-001"}')
echo "  workflow id: ${WF_ID}"

wait_until "WAIT_FOR_WEBHOOK IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_ID} waitEvent)\" == 'IN_PROGRESS' ]]"

HTTP=$(fire_webhook "$HOOK_ID" '{"event":{"type":"smoke_trigger"},"data":"hello"}')
[[ "$HTTP" == "200" ]] && pass "POST /webhook/{id} returns 200" || fail "POST /webhook/{id} returned ${HTTP}"

TSTATUS=$(task_status "$WF_ID" waitEvent)
[[ "$TSTATUS" == "COMPLETED" ]] \
  && pass "WAIT_FOR_WEBHOOK task is COMPLETED" \
  || fail "WAIT_FOR_WEBHOOK task is ${TSTATUS}"

WSTATUS=$(wf_status "$WF_ID")
[[ "$WSTATUS" == "COMPLETED" ]] \
  && pass "Workflow is COMPLETED" \
  || fail "Workflow is ${WSTATUS}"

# Verify payload propagated to task output
OUTPUT=$(get_json "${API}/workflow/${WF_ID}?includeTasks=true" \
  | jq -r '.tasks[] | select(.referenceTaskName=="waitEvent") | .outputData.data')
[[ "$OUTPUT" == "hello" ]] \
  && pass "Payload key 'data' present in task output" \
  || fail "Payload not in task output (got: ${OUTPUT})"

# ============================================================================
# SCENARIO 2 — Fan-out: two instances, one event → both COMPLETED
# ============================================================================
banner "Scenario 2: Fan-out (two instances, one event)"

WF_FANOUT='{
  "name": "smoke_fanout_wfwh",
  "version": 1,
  "schemaVersion": 2,
  "ownerEmail": "smoke@test.local",
  "tasks": [{
    "name": "WAIT_FOR_WEBHOOK",
    "taskReferenceName": "waitFanout",
    "type": "WAIT_FOR_WEBHOOK",
    "inputParameters": {"matches": {"$['"'"'event'"'"']['"'"'type'"'"']": "fanout_smoke"}}
  }]
}'

WEBHOOK_FANOUT='{
  "name": "smoke-fanout-hook",
  "verifier": "NONE",
  "receiverWorkflowNamesToVersions": {"smoke_fanout_wfwh": 1}
}'

register_workflow "$WF_FANOUT"
HOOK_FANOUT=$(create_webhook_config "$WEBHOOK_FANOUT")
echo "  webhook id: ${HOOK_FANOUT}"

WF_A=$(start_workflow "smoke_fanout_wfwh"); echo "  instance A: ${WF_A}"
WF_B=$(start_workflow "smoke_fanout_wfwh"); echo "  instance B: ${WF_B}"

wait_until "instance A IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_A} waitFanout)\" == 'IN_PROGRESS' ]]"
wait_until "instance B IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_B} waitFanout)\" == 'IN_PROGRESS' ]]"

HTTP=$(fire_webhook "$HOOK_FANOUT" '{"event":{"type":"fanout_smoke"}}')
[[ "$HTTP" == "200" ]] && pass "POST /webhook/{id} returns 200" || fail "POST /webhook/{id} returned ${HTTP}"

SA=$(wf_status "$WF_A"); SB=$(wf_status "$WF_B")
[[ "$SA" == "COMPLETED" ]] && pass "Instance A is COMPLETED" || fail "Instance A is ${SA}"
[[ "$SB" == "COMPLETED" ]] && pass "Instance B is COMPLETED" || fail "Instance B is ${SB}"

# ============================================================================
# SCENARIO 3 — Sequential: two tasks in one workflow, different events
# ============================================================================
banner "Scenario 3: Sequential tasks"

WF_SEQ='{
  "name": "smoke_seq_wfwh",
  "version": 1,
  "schemaVersion": 2,
  "ownerEmail": "smoke@test.local",
  "tasks": [
    {
      "name": "WAIT_FOR_WEBHOOK",
      "taskReferenceName": "waitApproval",
      "type": "WAIT_FOR_WEBHOOK",
      "inputParameters": {"matches": {"$['"'"'phase'"'"']": "approved"}}
    },
    {
      "name": "WAIT_FOR_WEBHOOK",
      "taskReferenceName": "waitShipment",
      "type": "WAIT_FOR_WEBHOOK",
      "inputParameters": {"matches": {"$['"'"'phase'"'"']": "shipped"}}
    }
  ]
}'

WEBHOOK_SEQ='{
  "name": "smoke-seq-hook",
  "verifier": "NONE",
  "receiverWorkflowNamesToVersions": {"smoke_seq_wfwh": 1}
}'

register_workflow "$WF_SEQ"
HOOK_SEQ=$(create_webhook_config "$WEBHOOK_SEQ")
echo "  webhook id: ${HOOK_SEQ}"

WF_SEQ_ID=$(start_workflow "smoke_seq_wfwh"); echo "  workflow id: ${WF_SEQ_ID}"

wait_until "waitApproval IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_SEQ_ID} waitApproval)\" == 'IN_PROGRESS' ]]"

fire_webhook "$HOOK_SEQ" '{"phase":"approved"}' > /dev/null
wait_until "waitApproval COMPLETED" 10 \
  "[[ \"\$(task_status ${WF_SEQ_ID} waitApproval)\" == 'COMPLETED' ]]"
pass "First WAIT_FOR_WEBHOOK (waitApproval) COMPLETED"

wait_until "waitShipment IN_PROGRESS" 10 \
  "[[ \"\$(task_status ${WF_SEQ_ID} waitShipment)\" == 'IN_PROGRESS' ]]"
pass "Second WAIT_FOR_WEBHOOK (waitShipment) registered and IN_PROGRESS"

fire_webhook "$HOOK_SEQ" '{"phase":"shipped"}' > /dev/null
wait_until "waitShipment COMPLETED" 10 \
  "[[ \"\$(task_status ${WF_SEQ_ID} waitShipment)\" == 'COMPLETED' ]]"
pass "Second WAIT_FOR_WEBHOOK (waitShipment) COMPLETED"

WSTATUS=$(wf_status "$WF_SEQ_ID")
[[ "$WSTATUS" == "COMPLETED" ]] \
  && pass "Workflow is COMPLETED after both tasks" \
  || fail "Workflow is ${WSTATUS}"

# ============================================================================
# SCENARIO 4 — Multi-criteria: AND semantics
# ============================================================================
banner "Scenario 4: Multi-criteria (AND logic)"

WF_MULTI='{
  "name": "smoke_multi_wfwh",
  "version": 1,
  "schemaVersion": 2,
  "ownerEmail": "smoke@test.local",
  "tasks": [{
    "name": "WAIT_FOR_WEBHOOK",
    "taskReferenceName": "waitMulti",
    "type": "WAIT_FOR_WEBHOOK",
    "inputParameters": {"matches": {
      "$['"'"'event'"'"']['"'"'type'"'"']": "order",
      "$['"'"'event'"'"']['"'"'region'"'"']": "us-west"
    }}
  }]
}'

WEBHOOK_MULTI='{
  "name": "smoke-multi-hook",
  "verifier": "NONE",
  "receiverWorkflowNamesToVersions": {"smoke_multi_wfwh": 1}
}'

register_workflow "$WF_MULTI"
HOOK_MULTI=$(create_webhook_config "$WEBHOOK_MULTI")
echo "  webhook id: ${HOOK_MULTI}"

WF_MULTI_ID=$(start_workflow "smoke_multi_wfwh"); echo "  workflow id: ${WF_MULTI_ID}"

wait_until "waitMulti IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_MULTI_ID} waitMulti)\" == 'IN_PROGRESS' ]]"

# Partial match (wrong region) — must NOT complete the task
fire_webhook "$HOOK_MULTI" '{"event":{"type":"order","region":"us-east"}}' > /dev/null
sleep 1
STATUS_AFTER_PARTIAL=$(task_status "$WF_MULTI_ID" waitMulti)
[[ "$STATUS_AFTER_PARTIAL" == "IN_PROGRESS" ]] \
  && pass "Partial match ignored — task still IN_PROGRESS" \
  || fail "Partial match incorrectly completed task (status: ${STATUS_AFTER_PARTIAL})"

# Full match — must complete
fire_webhook "$HOOK_MULTI" '{"event":{"type":"order","region":"us-west"}}' > /dev/null
wait_until "waitMulti COMPLETED" 10 \
  "[[ \"\$(task_status ${WF_MULTI_ID} waitMulti)\" == 'COMPLETED' ]]"
pass "Full multi-criteria match COMPLETED the task"

# ============================================================================
# SCENARIO 5 — Stale hash: terminated workflow, hash cleaned up
# ============================================================================
banner "Scenario 5: Stale hash after workflow termination"

WF_STALE='{
  "name": "smoke_stale_wfwh",
  "version": 1,
  "schemaVersion": 2,
  "ownerEmail": "smoke@test.local",
  "tasks": [{
    "name": "WAIT_FOR_WEBHOOK",
    "taskReferenceName": "waitStale",
    "type": "WAIT_FOR_WEBHOOK",
    "inputParameters": {"matches": {"$['"'"'event'"'"']['"'"'type'"'"']": "stale_event"}}
  }]
}'

WEBHOOK_STALE='{
  "name": "smoke-stale-hook",
  "verifier": "NONE",
  "receiverWorkflowNamesToVersions": {"smoke_stale_wfwh": 1}
}'

register_workflow "$WF_STALE"
HOOK_STALE=$(create_webhook_config "$WEBHOOK_STALE")
echo "  webhook id: ${HOOK_STALE}"

WF_STALE_ID=$(start_workflow "smoke_stale_wfwh"); echo "  stale workflow id: ${WF_STALE_ID}"
wait_until "waitStale IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_STALE_ID} waitStale)\" == 'IN_PROGRESS' ]]"

# Terminate the workflow (cancel() deregisters the hash)
curl -sf -X DELETE "${API}/workflow/${WF_STALE_ID}?reason=smoke-test" > /dev/null
sleep 1

# Fire webhook against the now-gone hash — must return 200, not 500
HTTP=$(fire_webhook "$HOOK_STALE" '{"event":{"type":"stale_event"}}')
[[ "$HTTP" == "200" ]] \
  && pass "Stale-hash POST returns 200 (no error)" \
  || fail "Stale-hash POST returned ${HTTP}"

# New instance must still be completable
WF_FRESH_ID=$(start_workflow "smoke_stale_wfwh"); echo "  fresh workflow id: ${WF_FRESH_ID}"
wait_until "fresh waitStale IN_PROGRESS" 15 \
  "[[ \"\$(task_status ${WF_FRESH_ID} waitStale)\" == 'IN_PROGRESS' ]]"

fire_webhook "$HOOK_STALE" '{"event":{"type":"stale_event"}}' > /dev/null
wait_until "fresh waitStale COMPLETED" 10 \
  "[[ \"\$(task_status ${WF_FRESH_ID} waitStale)\" == 'COMPLETED' ]]"
pass "Fresh instance unaffected — completed normally after stale cleanup"

# ============================================================================
# SUMMARY
# ============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  RESULTS:  ${GREEN}${PASS} passed${NC}  /  ${RED}${FAIL} failed${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
[[ "$FAIL" -eq 0 ]] && exit 0 || exit 1
