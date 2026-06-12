---
description: "Conductor cookbook — wait and timer pattern recipes for fixed delays, scheduled execution, external signals, and human-in-the-loop approvals."
---

# Wait and timer patterns

### Wait for a fixed delay

Introduce a delay between workflow steps — useful for rate limiting, cool-down periods, or retry backoff.

```json
{
  "name": "delayed_notification",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "process_event",
      "taskReferenceName": "process",
      "type": "SIMPLE"
    },
    {
      "name": "wait_before_retry",
      "taskReferenceName": "cooldown",
      "type": "WAIT",
      "inputParameters": {
        "duration": "5 minutes"
      }
    },
    {
      "name": "send_notification",
      "taskReferenceName": "notify",
      "type": "HTTP",
      "inputParameters": {
        "uri": "https://api.example.com/notify",
        "method": "POST",
        "body": {"eventId": "${process.output.eventId}"}
      }
    }
  ]
}
```

The `duration` field supports human-readable formats: `30 seconds`, `5 minutes`, `2 hours`, `1 days`, or short forms like `30s`, `5m`, `2h`, `1d`. You can also combine them: `2 hours 30 minutes`.

---

### Wait until a specific time

Schedule workflow continuation for a specific date/time — useful for scheduled releases, SLA deadlines, or business-hours processing.

```json
{
  "name": "scheduled_report",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["reportDate"],
  "tasks": [
    {
      "name": "prepare_report",
      "taskReferenceName": "prepare",
      "type": "SIMPLE"
    },
    {
      "name": "wait_until_publish_time",
      "taskReferenceName": "schedule_wait",
      "type": "WAIT",
      "inputParameters": {
        "until": "${workflow.input.reportDate}"
      }
    },
    {
      "name": "publish_report",
      "taskReferenceName": "publish",
      "type": "HTTP",
      "inputParameters": {
        "uri": "https://api.example.com/reports/publish",
        "method": "POST",
        "body": {"reportId": "${prepare.output.reportId}"}
      }
    }
  ]
}
```

The `until` field supports formats: `yyyy-MM-dd HH:mm z` (e.g., `2025-06-15 09:00 GMT+00:00`), `yyyy-MM-dd HH:mm`, or `yyyy-MM-dd`.

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @scheduled_report.json

curl -X POST 'http://localhost:8080/api/workflow/scheduled_report' \
  -H 'Content-Type: application/json' \
  -d '{"reportDate": "2025-06-15 09:00 GMT+00:00"}'
```

---

### Wait for an external signal

Pause a workflow until an external system (or human) completes the task via API — useful for approvals, manual QA, or third-party callbacks.

```json
{
  "name": "order_with_manual_approval",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["orderId", "amount"],
  "tasks": [
    {
      "name": "validate_order",
      "taskReferenceName": "validate",
      "type": "HTTP",
      "inputParameters": {
        "uri": "https://api.example.com/orders/${workflow.input.orderId}/validate",
        "method": "GET"
      }
    },
    {
      "name": "wait_for_approval",
      "taskReferenceName": "approval",
      "type": "WAIT"
    },
    {
      "name": "fulfill_order",
      "taskReferenceName": "fulfill",
      "type": "HTTP",
      "inputParameters": {
        "uri": "https://api.example.com/orders/${workflow.input.orderId}/fulfill",
        "method": "POST",
        "body": {
          "approvedBy": "${approval.output.approvedBy}"
        }
      }
    }
  ]
}
```

Complete the WAIT task externally (e.g., from a UI or webhook):

```shell
# Complete the wait task and resume the workflow
curl -X POST 'http://localhost:8080/api/tasks/{workflowId}/approval/COMPLETED/sync' \
  -H 'Content-Type: application/json' \
  -d '{"approvedBy": "manager@example.com"}'
```

The output data you pass when completing the task is available in subsequent tasks via `${approval.output.approvedBy}`.
