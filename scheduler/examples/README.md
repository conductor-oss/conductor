# Workflow Scheduler — Quickstart

This guide walks through the full lifecycle of a scheduled workflow using `curl`.
All examples assume Conductor is running locally on port 8080.

For a fully pre-configured environment, see [Docker Compose demo](#docker-compose-demo).

---

## Prerequisites

- Conductor running with **PostgreSQL** persistence and `conductor.scheduler.enabled=true`
- The `http-task` worker available (or swap the first task for a `SIMPLE` task)

---

## Step 1 — Register the workflow

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d @daily-report-workflow.json
```

---

## Step 2 — Create a schedule

The `every-minute-schedule.json` fires every minute — useful for seeing results quickly.
Swap in `daily-report-schedule.json` for a realistic weekday 9 AM (New York) schedule.

```bash
curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" \
  -d @every-minute-schedule.json | jq .
```

Expected response:
```json
{
  "name": "every-minute-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "paused": false,
  "nextRunTime": 1708300860000,
  ...
}
```

---

## Step 3 — Preview next execution times

```bash
curl -s "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/next-execution-times?count=5" \
  | jq '[.[] | (. / 1000 | todate)]'
```

---

## Step 4 — Check execution history

After a minute or two, executions will appear:

```bash
curl -s "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/executions?limit=5" \
  | jq '[.[] | {state, workflowId, scheduledTime}]'
```

Expected output:
```json
[
  { "state": "EXECUTED", "workflowId": "abc123...", "scheduledTime": 1708300860000 },
  { "state": "EXECUTED", "workflowId": "def456...", "scheduledTime": 1708300800000 }
]
```

---

## Step 5 — Pause the schedule

```bash
curl -s -X PUT "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/pause?reason=testing+pause"
```

Verify it's paused:
```bash
curl -s http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule | jq '{paused, pausedReason}'
```

---

## Step 6 — Resume the schedule

```bash
curl -s -X PUT http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/resume
```

---

## Step 7 — List all schedules

```bash
curl -s http://localhost:8080/api/scheduler/schedules | jq '[.[] | {name, cronExpression, paused, nextRunTime}]'
```

Filter by workflow name:
```bash
curl -s "http://localhost:8080/api/scheduler/schedules?workflowName=daily_report_workflow" | jq .
```

---

## Step 8 — Delete the schedule

```bash
curl -s -X DELETE http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule
```

---

## API Reference

| Method   | Path                                              | Description                                |
|----------|---------------------------------------------------|--------------------------------------------|
| `POST`   | `/api/scheduler/schedules`                        | Create or update a schedule                |
| `GET`    | `/api/scheduler/schedules`                        | List all schedules (optional `?workflowName=`) |
| `GET`    | `/api/scheduler/schedules/{name}`                 | Get a schedule by name                     |
| `DELETE` | `/api/scheduler/schedules/{name}`                 | Delete a schedule                          |
| `PUT`    | `/api/scheduler/schedules/{name}/pause`           | Pause (optional `?reason=`)                |
| `PUT`    | `/api/scheduler/schedules/{name}/resume`          | Resume                                     |
| `GET`    | `/api/scheduler/schedules/{name}/executions`      | Execution history (`?limit=10`)            |
| `GET`    | `/api/scheduler/schedules/{name}/next-execution-times` | Preview next N times (`?count=5`)     |

---

## Cron expression format

The scheduler uses **6-field Spring cron** (second-level precision):

```
┌─────────────── second (0-59)
│ ┌───────────── minute (0-59)
│ │ ┌─────────── hour (0-23)
│ │ │ ┌───────── day of month (1-31)
│ │ │ │ ┌─────── month (1-12 or JAN-DEC)
│ │ │ │ │ ┌───── day of week (0-7 or MON-SUN)
│ │ │ │ │ │
* * * * * *
```

| Expression            | Meaning                        |
|-----------------------|--------------------------------|
| `0 * * * * *`         | Every minute                   |
| `0 0 9 * * MON-FRI`  | Weekdays at 9:00 AM            |
| `0 0 0 1 * *`         | First day of every month       |
| `0 0/30 9-17 * * MON-FRI` | Every 30 min, business hours |

---

## Docker Compose demo

See [`docker-compose-scheduler-demo.yaml`](../../docker/docker-compose-scheduler-demo.yaml) for
a single-command environment with Conductor + PostgreSQL + the scheduler pre-enabled.

```bash
# From the repo root
docker compose -f docker/docker-compose-scheduler-demo.yaml up

# Conductor UI: http://localhost:5000
# Conductor API: http://localhost:8080
```

Once the stack is healthy, run the steps above — or just watch the scheduler fire:

```bash
watch -n5 'curl -s "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/executions?limit=5" | jq "[.[] | {state, workflowId}]"'
```
