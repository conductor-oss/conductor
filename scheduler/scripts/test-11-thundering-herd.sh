#!/usr/bin/env bash
# Test #11: Thundering herd — N schedules all fire at the same cron tick.
# Registers N schedules with "0 * * * * *", waits for the next minute,
# then checks that exactly N executions happened (no skips, no duplicates).
#
# Usage: ./test-11-thundering-herd.sh [count] [conductor_url]
# Example: ./test-11-thundering-herd.sh 50 http://localhost:8080

COUNT="${1:-50}"
CONDUCTOR_URL="${2:-http://localhost:8080}"
PREFIX="herd"

echo "=== Test #11: Thundering Herd (N=$COUNT) ==="
echo "Conductor: $CONDUCTOR_URL"
echo ""

# --- Cleanup any previous run ---
echo "Cleaning up previous herd schedules..."
for i in $(seq -w 0 $((COUNT - 1))); do
  curl -s -X DELETE "$CONDUCTOR_URL/api/scheduler/schedules/${PREFIX}-${i}" > /dev/null
done
sleep 2

# --- Register N schedules ---
echo "Registering $COUNT schedules..."
ERRORS=0
IDX=0
for i in $(seq -w 0 $((COUNT - 1))); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$CONDUCTOR_URL/api/scheduler/schedules" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"${PREFIX}-${i}\",
      \"cronExpression\": \"0 * * * * *\",
      \"zoneId\": \"UTC\",
      \"runCatchupScheduleInstances\": false,
      \"startWorkflowRequest\": {
        \"name\": \"concurrent_demo_workflow\",
        \"version\": 1,
        \"input\": {\"herdIndex\": $IDX}
      }
    }")
  # Both 200 (update) and 201 (create) are success
  if [[ "$CODE" != "200" && "$CODE" != "201" ]]; then
    echo "  ERROR registering ${PREFIX}-${i}: HTTP $CODE"
    ERRORS=$((ERRORS + 1))
  fi
  IDX=$((IDX + 1))
done
echo "Registered with $ERRORS errors."
echo ""

# --- Wait for next full minute tick ---
NOW=$(date +%s)
NEXT_MIN=$(( (NOW / 60 + 1) * 60 ))
WAIT=$((NEXT_MIN - NOW))
echo "Waiting ${WAIT}s for next minute tick at $(date -r $NEXT_MIN 2>/dev/null || date -d @$NEXT_MIN)..."
sleep $WAIT

# Give the scheduler time to process all schedules.
# pollBatchSize=50, pollingInterval=1s: all N schedules processed in ceil(N/50) cycles.
# Add 15s buffer for workflow dispatch latency.
DRAIN=$((COUNT / 50 + 15))
echo "Tick! Giving scheduler ${DRAIN}s to drain the herd (pollBatchSize=50, N=$COUNT)..."
sleep $DRAIN

# --- Check results ---
echo ""
echo "=== Results ==="

TOTAL_EXECUTIONS=0
MISSED=0
DUPLICATES=0

for i in $(seq -w 0 $((COUNT - 1))); do
  EXECS=$(curl -s "$CONDUCTOR_URL/api/scheduler/schedules/${PREFIX}-${i}/executions?count=5" | \
    python3 -c "import sys,json; recs=json.load(sys.stdin); print(len([r for r in recs if r.get('state')=='EXECUTED']))" 2>/dev/null || echo 0)
  TOTAL_EXECUTIONS=$((TOTAL_EXECUTIONS + EXECS))
  if [[ "$EXECS" == "0" ]]; then MISSED=$((MISSED + 1)); fi
  if [[ "$EXECS" -gt "1" ]]; then DUPLICATES=$((DUPLICATES + 1)); fi
done

echo "  Schedules registered: $COUNT"
echo "  Total EXECUTED records: $TOTAL_EXECUTIONS"
echo "  Schedules that fired 0 times (missed): $MISSED"
echo "  Schedules that fired >1 time (duplicate): $DUPLICATES"
echo ""
if [[ "$TOTAL_EXECUTIONS" == "$COUNT" && "$MISSED" == "0" && "$DUPLICATES" == "0" ]]; then
  echo "  PASS: All $COUNT schedules fired exactly once."
else
  echo "  FAIL: Expected $COUNT executions, got $TOTAL_EXECUTIONS (missed=$MISSED, duplicates=$DUPLICATES)."
fi

# --- Cleanup ---
echo ""
echo "Cleaning up..."
for i in $(seq -w 0 $((COUNT - 1))); do
  curl -s -X DELETE "$CONDUCTOR_URL/api/scheduler/schedules/${PREFIX}-${i}" > /dev/null
done
echo "Done."
