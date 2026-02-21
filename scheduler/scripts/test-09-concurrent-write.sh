#!/usr/bin/env bash
# Test #9: Simultaneous schedule registration from two clients.
# Both styx.local and spartacus.local POST the same schedule name at the same epoch second.
# Expected: UPSERT handles it — no constraint violations, final state is consistent.
#
# Usage: ./test-09-concurrent-write.sh <conductor_url>
# Example (styx):     ./test-09-concurrent-write.sh http://localhost:8080
# Example (spartacus): ./test-09-concurrent-write.sh http://192.168.65.221:8080

CONDUCTOR_URL="${1:-http://localhost:8080}"
SCHEDULE_NAME="test09-concurrent-write"
TARGET=$(($(date +%s) + 12))

echo "[$(hostname)] Firing at epoch $TARGET ($(date -r $TARGET 2>/dev/null || date -d @$TARGET))"
sleep $((TARGET - $(date +%s)))

START_MS=$(python3 -c "import time; print(int(time.time()*1000))")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$CONDUCTOR_URL/api/scheduler/schedules" \
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
  }")
END_MS=$(python3 -c "import time; print(int(time.time()*1000))")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -1)
LATENCY=$((END_MS - START_MS))

echo "[$(hostname)] HTTP $HTTP_CODE in ${LATENCY}ms"
echo "[$(hostname)] Body: $BODY"
