#!/bin/sh
# Seed script for the Conductor Scheduler Demo.
# Registers the sample workflow and a 1-minute demo schedule.
# Runs once inside the conductor-seed container after Conductor is healthy.

set -e

BASE_URL="http://conductor-server:8080"

echo "==> Registering daily_report_workflow..."
curl -sf -X POST "${BASE_URL}/api/metadata/workflow" \
  -H "Content-Type: application/json" \
  -d @/examples/daily-report-workflow.json
echo ""

echo "==> Creating every-minute-demo-schedule..."
curl -sf -X POST "${BASE_URL}/api/scheduler/schedules" \
  -H "Content-Type: application/json" \
  -d @/examples/every-minute-schedule.json
echo ""

echo ""
echo "=========================================="
echo " Scheduler demo is ready!"
echo ""
echo " Conductor UI:  http://localhost:5000"
echo " Conductor API: http://localhost:8080"
echo " Swagger:       http://localhost:8080/swagger-ui/index.html"
echo ""
echo " Watch executions:"
echo "   curl -s 'http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/executions?limit=5' | jq ."
echo "=========================================="
