---
description: "Schedule workflows to run on a cron expression using Conductor's built-in scheduler. Create, pause, resume, and delete schedules via the REST API."
---

# Scheduling Workflows

Conductor includes a built-in scheduler that triggers workflow executions on a cron schedule. Schedules are managed through the REST API — no external cron daemon or job scheduler is needed.

## How it works

A **schedule** binds a cron expression to a `StartWorkflowRequest`. On every cron tick the scheduler starts a new workflow execution with the configured input. Two timestamps are automatically injected into every triggered workflow's input:

| Input key | Description |
|---|---|
| `_scheduledTime` | The exact cron slot time (epoch ms) |
| `_executedTime` | The actual dispatch time (epoch ms) |

## Cron expression format

Conductor uses Spring's 6-field cron format with **second-level precision**:

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

| Expression | Meaning |
|---|---|
| `0 * * * * *` | Every minute |
| `0 0 9 * * MON-FRI` | Weekdays at 9 AM |
| `0 0 0 1 * *` | First day of every month at midnight |
| `*/10 * * * * *` | Every 10 seconds |

## Creating a schedule

**1. Register the workflow** (if not already registered):

```shell
curl -X PUT 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d '[{
    "name": "daily_report_workflow",
    "version": 1,
    "schemaVersion": 2,
    "tasks": [{
      "name": "fetch_report_data",
      "taskReferenceName": "fetch_report_data_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://jsonplaceholder.typicode.com/todos?userId=1",
          "method": "GET"
        }
      }
    }],
    "timeoutPolicy": "TIME_OUT_WF",
    "timeoutSeconds": 120
  }]'
```

**2. Create the schedule:**

```shell
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "daily-report-schedule",
    "cronExpression": "0 0 9 * * MON-FRI",
    "zoneId": "America/New_York",
    "startWorkflowRequest": {
      "name": "daily_report_workflow",
      "version": 1,
      "correlationId": "daily-report-${scheduledTime}"
    },
    "runCatchupScheduleInstances": false,
    "paused": false
  }'
```

The response returns the saved schedule object including its computed `nextRunTime`.

## Schedule definition fields

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | Unique schedule identifier |
| `cronExpression` | string | Yes | 6-field Spring cron expression |
| `zoneId` | string | No | Timezone (default: `UTC`) |
| `startWorkflowRequest` | object | Yes | Workflow to trigger — includes `name`, `version`, `input`, `correlationId` |
| `runCatchupScheduleInstances` | boolean | No | Fire missed slots if the scheduler was offline (default: `false`) |
| `paused` | boolean | No | Create in paused state (default: `false`) |
| `scheduleStartTime` | long | No | Earliest time the schedule fires (epoch ms) |
| `scheduleEndTime` | long | No | Latest time the schedule fires (epoch ms) |
| `description` | string | No | Free-text description |

## Previewing execution times

Before creating a schedule, preview when it will fire:

```shell
curl 'http://localhost:8080/api/scheduler/nextFewSchedules?cronExpression=0+0+9+*+*+MON-FRI&limit=5'
```

Returns an array of epoch-millisecond timestamps for the next 5 execution times.

## Pausing and resuming

```shell
# Pause
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/pause'

# Pause with a reason
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/pause?reason=maintenance+window'

# Resume
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/resume'
```

## Listing and searching schedules

```shell
# List all schedules
curl 'http://localhost:8080/api/scheduler/schedules'

# Filter by workflow name
curl 'http://localhost:8080/api/scheduler/schedules?workflowName=daily_report_workflow'

# Search with pagination
curl 'http://localhost:8080/api/scheduler/schedules/search?workflowName=daily_report_workflow&size=10'
```

## Viewing execution history

```shell
curl 'http://localhost:8080/api/scheduler/search/executions?freeText=daily-report-schedule&size=20'
```

Returns a `SearchResult` with `totalHits` and a list of execution records, each containing the `scheduledTime`, `executionTime`, `workflowId`, and `state` (`POLLED`, `EXECUTED`, or `FAILED`).

## Deleting a schedule

```shell
curl -X DELETE 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule'
```

## Passing input to scheduled workflows

Static input parameters are merged with the auto-injected `_scheduledTime` and `_executedTime`:

```json
{
  "name": "input-param-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "startWorkflowRequest": {
    "name": "input_param_demo_workflow",
    "version": 1,
    "input": {
      "reportOwner": "platform-team",
      "alertThreshold": 100
    }
  }
}
```

Inside the workflow, access all values via `${workflow.input.*}`:

- `${workflow.input.reportOwner}` — your static input
- `${workflow.input._scheduledTime}` — injected cron slot time
- `${workflow.input._executedTime}` — injected actual dispatch time

## Configuration

The scheduler is configured under the `conductor.scheduler` prefix in your application properties:

| Property | Default | Description |
|---|---|---|
| `conductor.scheduler.enabled` | `true` | Enable/disable the scheduler |
| `conductor.scheduler.pollingInterval` | `100` | Poll interval in milliseconds |
| `conductor.scheduler.pollBatchSize` | `5` | Schedules processed per poll cycle |
| `conductor.scheduler.pollingThreadCount` | `1` | Number of polling threads |
| `conductor.scheduler.schedulerTimeZone` | `UTC` | Default timezone |
| `conductor.scheduler.initialDelayMs` | `15000` | Startup delay before first poll |
| `conductor.scheduler.maxScheduleJitterMs` | `1000` | Random jitter added to dispatch times to smooth load |

!!! note "Catchup mode"
    When `runCatchupScheduleInstances` is `true`, the scheduler fires all cron slots that were missed while it was offline. Use this for workflows where every execution matters (e.g., billing, compliance). Leave it `false` (default) for dashboards or monitoring where only the latest run matters.

!!! warning "Concurrent executions"
    The scheduler fires on every cron tick regardless of whether the previous execution has completed. If your workflow takes longer than the cron interval, multiple instances will run concurrently. Design your workflows to handle this, or use a longer interval.
