---
description: "Conductor cookbook — practical patterns for scheduling workflows with cron expressions, timezone-aware firing, bounded time windows, pause/resume, catchup mode, and execution history."
---

# Scheduled workflow patterns

The Conductor scheduler lets you trigger workflow executions on a recurring cron schedule without any external cron daemon or glue code. Each schedule stores a `StartWorkflowRequest` alongside scheduling metadata and is persisted independently of the workflows it launches.

For installation and configuration options see [Scheduler configuration](../../documentation/configuration/scheduler.md). For a full reference of every endpoint see [Scheduler API](../../documentation/api/scheduler.md).

---

## Run a workflow on a fixed schedule

The simplest case: fire a workflow every weekday at 09:00 UTC. Send a `POST` to `/api/scheduler/schedules` with a JSON body that contains at minimum a `name`, a `cronExpression`, and a `startWorkflowRequest`.

The `cronExpression` field uses a **six-field Spring cron format**: `seconds minutes hours day-of-month month day-of-week`. The expression below means "at second 0, minute 0, hour 9, any day-of-month, any month, Monday through Friday."

```shell
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d @daily-report-schedule.json
```

```json
{
  "name": "daily_report_schedule",
  "description": "Generate the daily sales report every weekday at 9 AM UTC.",
  "cronExpression": "0 0 9 * * MON-FRI",
  "startWorkflowRequest": {
    "name": "generate_sales_report",
    "version": 1
  }
}
```

!!! tip
    To update an existing schedule, `POST` the same payload with the same `name`. The service overwrites the record and recalculates the next-run time automatically.

---

## Using timezones

By default the cron expression is evaluated in UTC. Set `zoneId` to any [IANA timezone identifier](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) to evaluate the schedule in a different timezone. The scheduler converts the resulting wall-clock time to UTC internally before comparing it to the current time, so daylight-saving transitions are handled automatically — the job fires at 09:00 local time year-round.

```json
{
  "name": "daily_report_schedule_est",
  "description": "Generate the daily sales report every weekday at 9 AM New York time.",
  "cronExpression": "0 0 9 * * MON-FRI",
  "zoneId": "America/New_York",
  "startWorkflowRequest": {
    "name": "generate_sales_report",
    "version": 1
  }
}
```

!!! note
    If `zoneId` is omitted or blank the service falls back to the value of the `conductor.scheduler.schedulerTimeZone` property (default `UTC`). An invalid zone ID causes the schedule to be rejected at creation time with a 400 error.

---

## Limit execution to a time window

Set `scheduleStartTime` and `scheduleEndTime` (both in **epoch milliseconds**) to restrict the schedule to a finite window. The scheduler will not fire before `scheduleStartTime` and will stop firing after `scheduleEndTime`. This is useful for seasonal jobs, quarter-end processing, or campaign workflows that have a known start and expiry.

The example below runs an hourly ETL job throughout Q1 2026 (UTC):

- `scheduleStartTime`: `1735689600000` — 2026-01-01 00:00:00 UTC
- `scheduleEndTime`:   `1743379199000` — 2026-03-31 23:59:59 UTC

```shell
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d @q1-etl-schedule.json
```

```json
{
  "name": "q1_2026_hourly_etl",
  "description": "Hourly ETL job — Q1 2026 only.",
  "cronExpression": "0 0 * * * *",
  "scheduleStartTime": 1735689600000,
  "scheduleEndTime": 1743379199000,
  "startWorkflowRequest": {
    "name": "etl_pipeline",
    "version": 2
  }
}
```

!!! tip
    Once `scheduleEndTime` is passed, the scheduler advances its internal next-run pointer past the end time so the last slot is never re-fired, even if the server restarts.

---

## Pause and resume a schedule

Pausing a schedule prevents new workflow executions from being triggered without deleting the schedule definition. The scheduler preserves the `nextRunTime` pointer while the schedule is paused, so it knows exactly where in the cron sequence to resume from (subject to the `runCatchupScheduleInstances` setting described in the next section).

**Pause a schedule:**

```shell
curl 'http://localhost:8080/api/scheduler/schedules/daily_report_schedule/pause'
```

The `pausedReason` field can be set by updating the schedule via `POST /api/scheduler/schedules` with `paused: true` and a `pausedReason` string. It is a free-text string intended for operators — useful for audit trails or for surfacing a human-readable explanation in a dashboard.

**Resume a schedule:**

```shell
curl 'http://localhost:8080/api/scheduler/schedules/daily_report_schedule/resume'
```

Resuming clears `paused` and `pausedReason` and recalculates the next-run time according to the `runCatchupScheduleInstances` flag (see below). No slots are lost in the internal pointer — the service knows exactly which slot should have fired next.

---

## Catchup mode

When a schedule is paused (or the server is down) for an extended period, multiple cron slots are missed. The `runCatchupScheduleInstances` flag controls what happens when the schedule becomes active again.

| `runCatchupScheduleInstances` | Behaviour on resume |
|---|---|
| `false` (default) | All missed slots are skipped. The pointer jumps to the next future cron slot and normal cadence resumes immediately. |
| `true` | The pointer is left at the first missed slot. The polling loop fires one missed execution per poll cycle, working through each slot sequentially until it reaches the current time. |

**Example: catchup enabled.** A daily schedule was paused for three days. On resume it will fire three times (once per poll cycle) for the three missed 09:00 slots before resuming normal daily execution.

```shell
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "daily_reconciliation",
    "cronExpression": "0 0 9 * * *",
    "runCatchupScheduleInstances": true,
    "startWorkflowRequest": {
      "name": "reconcile_accounts",
      "version": 1
    }
  }'
```

!!! note
    Catchup executions are fired sequentially at the polling interval, not all at once. The workflow `input` for each catchup execution will contain the original cron slot time in `scheduledTime` so the workflow can distinguish which period it is processing.

---

## Passing input to the workflow

Use `startWorkflowRequest.input` to supply static parameters that will be merged into every workflow execution triggered by this schedule. The scheduler also injects two reserved keys into the input automatically:

| Key | Description |
|---|---|
| `scheduledTime` | Epoch millis of the exact cron slot that fired. |
| `executionTime` | Epoch millis of the actual wall-clock dispatch time. |

These keys match the Orkes Conductor scheduler for cross-platform compatibility. You can reference them inside the workflow with `${workflow.input.scheduledTime}`.

```shell
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d @report-schedule-with-input.json
```

```json
{
  "name": "weekly_summary_report",
  "description": "Weekly summary report every Monday at 08:00 UTC.",
  "cronExpression": "0 0 8 * * MON",
  "zoneId": "UTC",
  "startWorkflowRequest": {
    "name": "generate_summary_report",
    "version": 1,
    "correlationId": "weekly-summary",
    "input": {
      "reportType": "weekly",
      "recipients": ["ops-team@example.com", "finance@example.com"],
      "format": "PDF"
    }
  }
}
```

The workflow will receive the merged input:

```json
{
  "reportType": "weekly",
  "recipients": ["ops-team@example.com", "finance@example.com"],
  "format": "PDF",
  "scheduledTime": 1742896800000,
  "executionTime": 1742896800412
}
```

!!! tip
    Use `correlationId` in `startWorkflowRequest` to make it easy to search for all executions belonging to this schedule. The value is passed through unchanged to every triggered workflow instance.

---

## Checking execution history

Retrieve a list of recent execution attempts for a schedule with `GET /api/scheduler/schedules/{name}/executions`. The `limit` query parameter controls how many records to return (newest first).

```shell
curl 'http://localhost:8080/api/scheduler/schedules/daily_report_schedule/executions?limit=10'
```

Each record in the response array describes one execution attempt:

```json
[
  {
    "executionId": "e3f2a1b0-1234-5678-abcd-000000000001",
    "scheduleName": "daily_report_schedule",
    "workflowId": "a9b8c7d6-abcd-ef01-2345-678900000001",
    "workflowName": "generate_sales_report",
    "scheduledTime": 1742896800000,
    "executionTime": 1742896800312,
    "state": "EXECUTED",
    "zoneId": "UTC",
    "reason": null
  },
  {
    "executionId": "e3f2a1b0-1234-5678-abcd-000000000002",
    "scheduleName": "daily_report_schedule",
    "workflowId": null,
    "workflowName": null,
    "scheduledTime": 1742810400000,
    "executionTime": 1742810400290,
    "state": "FAILED",
    "zoneId": "UTC",
    "reason": "Workflow definition 'generate_sales_report' version 1 not found."
  }
]
```

The three possible `state` values are:

| State | Meaning |
|---|---|
| `POLLED` | The scheduler picked up the slot and is in the process of starting the workflow. |
| `EXECUTED` | The workflow was started successfully; `workflowId` is populated. |
| `FAILED` | The workflow could not be started; see `reason` for the error message. |

**Stale POLLED records.** A record stays in `POLLED` only for the brief moment between the scheduler claiming the slot and confirming the workflow start. If a server crash interrupts that window, the record will be stuck in `POLLED` indefinitely. The scheduler automatically transitions any `POLLED` record older than **5 minutes** to `FAILED` with the reason `"Stale POLLED record — server may have crashed mid-execution"` on the next poll cycle.

To search across all schedules use the search endpoint:

```shell
curl 'http://localhost:8080/api/scheduler/search/executions?sort=scheduledTime:DESC&size=20'
```

---

## Previewing the next fire times

Before creating or updating a schedule, verify that your cron expression fires when you expect by calling the preview endpoint. This endpoint does not require an existing schedule — it evaluates the expression directly.

**Preview a raw cron expression:**

```shell
curl -G 'http://localhost:8080/api/scheduler/nextFewSchedules' \
  --data-urlencode 'cronExpression=0 0 9 * * MON-FRI' \
  --data-urlencode 'limit=5'
```

Response — a JSON array of epoch milliseconds:

```json
[
  1742896800000,
  1742983200000,
  1743069600000,
  1743156000000,
  1743242400000
]
```

You can also pass optional `scheduleStartTime` and `scheduleEndTime` parameters (epoch ms) to preview firing times within a bounded window.

**Preview the next fire times for an existing schedule** (honours `zoneId` and `scheduleEndTime`):

```shell
curl 'http://localhost:8080/api/scheduler/schedules/daily_report_schedule/next-execution-times?count=5'
```

!!! note
    The `/nextFewSchedules` endpoint evaluates the cron expression in UTC regardless of any `zoneId` you intend to set. Use the per-schedule endpoint `/schedules/{name}/next-execution-times` to see timezone-adjusted times once the schedule has been created.
