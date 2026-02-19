#!/usr/bin/env bash
# Test #10: Simultaneous schedule resume from two clients.
# A paused schedule with nextRunTime already in the past is resumed
# by both machines at the same second. Expected: fires exactly once.
#
# Usage (styx, runs setup + fires):
#   ./test-10-concurrent-resume.sh setup http://localhost:8080
#   ./test-10-concurrent-resume.sh fire   http://localhost:8080 <TARGET_EPOCH>
#
# Usage (spartacus, fires only):
#   ./test-10-concurrent-resume.sh fire   http://192.168.65.221:8080 <TARGET_EPOCH>

MODE="${1:-fire}"
CONDUCTOR_URL="${2:-http://localhost:8080}"
TARGET="${3:-0}"
SCHEDULE_NAME="test10-concurrent-resume"

if [[ "$MODE" == "setup" ]]; then
  echo "[setup] Registering and pausing schedule '$SCHEDULE_NAME'..."
  # Delete if exists
  curl -s -X DELETE "$CONDUCTOR_URL/api/scheduler/schedules/$SCHEDULE_NAME" > /dev/null

  # Register
  curl -s -X POST "$CONDUCTOR_URL/api/scheduler/schedules" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"$SCHEDULE_NAME\",
      \"cronExpression\": \"0 * * * * *\",
      \"zoneId\": \"UTC\",
      \"runCatchupScheduleInstances\": false,
      \"startWorkflowRequest\": {
        \"name\": \"concurrent_demo_workflow\",
        \"version\": 1,
        \"input\": {}
      }
    }" > /dev/null

  echo "[setup] Waiting 65s for one slot to fire then pausing..."
  sleep 65

  # Pause it
  curl -s -X PUT "$CONDUCTOR_URL/api/scheduler/schedules/$SCHEDULE_NAME/pause" > /dev/null
  echo "[setup] Paused. Waiting another 65s for nextRunTime to fall into the past..."
  sleep 65

  # Print the target second for both machines to use (15s from now)
  TARGET_EPOCH=$(($(date +%s) + 15))
  echo ""
  echo "================================================================"
  echo "READY. Run on both machines at the same time:"
  echo "  styx:     ./test-10-concurrent-resume.sh fire http://localhost:8080 $TARGET_EPOCH"
  echo "  spartacus: ./test-10-concurrent-resume.sh fire http://192.168.65.221:8080 $TARGET_EPOCH"
  echo "================================================================"
  exit 0
fi

# fire mode
if [[ "$TARGET" == "0" ]]; then
  echo "ERROR: must provide TARGET_EPOCH in fire mode"
  exit 1
fi

echo "[$(hostname)] Resuming '$SCHEDULE_NAME' at epoch $TARGET ($(date -r $TARGET 2>/dev/null || date -d @$TARGET))"
REMAINING=$((TARGET - $(date +%s)))
if [[ $REMAINING -gt 0 ]]; then sleep $REMAINING; fi

START_MS=$(python3 -c "import time; print(int(time.time()*1000))")
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$CONDUCTOR_URL/api/scheduler/schedules/$SCHEDULE_NAME/resume")
END_MS=$(python3 -c "import time; print(int(time.time()*1000))")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
LATENCY=$((END_MS - START_MS))
echo "[$(hostname)] HTTP $HTTP_CODE in ${LATENCY}ms"
