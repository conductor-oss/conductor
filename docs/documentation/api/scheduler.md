---
description: "Conductor Scheduler API — create, update, search, pause, resume, and delete workflow schedules, and query execution history via REST."
---

# Scheduler API

The Scheduler API manages cron-based workflow schedules. All endpoints use the base path `/api/scheduler`.

Schedules are powered by a 6-field cron expression (second-level precision) and fire by starting a configured workflow. The scheduler is optional — it is only available when a `SchedulerService` bean is present in the application context.

## Schedules

| Endpoint | Method | Description |
|---|---|---|
| `/schedules` | `POST` | Create or update a schedule |
| `/schedules` | `GET` | List all schedules, optionally filtered by workflow name |
| `/schedules/{name}` | `GET` | Get a schedule by name |
| `/schedules/{name}` | `DELETE` | Delete a schedule |
| `/schedules/search` | `GET` | Paginated, filtered schedule search |

### WorkflowSchedule Object

All schedule endpoints consume and produce `WorkflowSchedule` objects:

| Field | Type | Description |
|---|---|---|
| `name` | `string` | **Required.** Unique name for this schedule. |
| `cronExpression` | `string` | **Required.** 6-field cron expression (second precision), e.g. `"0 0 9 * * MON-FRI"`. |
| `zoneId` | `string` | Timezone for cron evaluation, e.g. `"America/New_York"`. Defaults to `"UTC"`. |
| `paused` | `boolean` | When `true`, the schedule will not trigger new executions. Defaults to `false`. |
| `pausedReason` | `string` | Optional explanation for why the schedule is paused. |
| `startWorkflowRequest` | `object` | **Required.** The workflow to start when this schedule fires. See below. |
| `scheduleStartTime` | `number` | Epoch millis. If set, the schedule will not trigger before this time. |
| `scheduleEndTime` | `number` | Epoch millis. If set, the schedule will not trigger after this time. |
| `runCatchupScheduleInstances` | `boolean` | When `true`, missed executions during server downtime are replayed sequentially before resuming normal cadence. Defaults to `false`. |
| `description` | `string` | Optional human-readable description. |
| `createdBy` | `string` | Optional. Who created this schedule. Not enforced in OSS. |
| `updatedBy` | `string` | Optional. Who last updated this schedule. Not enforced in OSS. |
| `createTime` | `number` | Epoch millis when this schedule was created. Set by the server. |
| `updatedTime` | `number` | Epoch millis when this schedule was last updated. Set by the server. |
| `nextRunTime` | `number` | Epoch millis of the next planned execution. Computed and cached by the server after each trigger. |

The `startWorkflowRequest` field is a standard `StartWorkflowRequest` object:

| Field | Type | Description |
|---|---|---|
| `name` | `string` | **Required.** Name of the workflow definition to start. |
| `version` | `number` | Workflow definition version. Defaults to the latest version. |
| `input` | `object` | JSON map of input data to pass to the workflow. |
| `correlationId` | `string` | Optional correlation ID. |
| `taskToDomain` | `object` | Optional map of task reference name to domain. |

### Create or Update Schedule

```
POST /api/scheduler/schedules
```

Creates a new schedule, or replaces an existing schedule with the same `name`. The operation is idempotent — posting an existing schedule name overwrites it entirely.

```shell
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "nightly_report",
    "description": "Run the nightly report workflow every day at 02:00 UTC",
    "cronExpression": "0 0 2 * * *",
    "zoneId": "UTC",
    "startWorkflowRequest": {
      "name": "generate_report",
      "version": 1,
      "input": {
        "reportType": "daily"
      }
    },
    "paused": false,
    "runCatchupScheduleInstances": false
  }'
```

**Response** `200 OK` — no response body.

### List All Schedules

```
GET /api/scheduler/schedules?workflowName=
```

Returns all defined schedules. Pass the optional `workflowName` parameter to filter results to schedules that target a specific workflow definition.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `workflowName` | `string` | No | Filter schedules by the workflow definition name they target. |

```shell
# List all schedules
curl 'http://localhost:8080/api/scheduler/schedules'

# List only schedules that target the "generate_report" workflow
curl 'http://localhost:8080/api/scheduler/schedules?workflowName=generate_report'
```

**Response** `200 OK`

```json
[
  {
    "name": "nightly_report",
    "description": "Run the nightly report workflow every day at 02:00 UTC",
    "cronExpression": "0 0 2 * * *",
    "zoneId": "UTC",
    "paused": false,
    "runCatchupScheduleInstances": false,
    "startWorkflowRequest": {
      "name": "generate_report",
      "version": 1,
      "input": {
        "reportType": "daily"
      }
    },
    "createTime": 1700000000000,
    "updatedTime": 1700000000000,
    "nextRunTime": 1700082000000
  }
]
```

### Get Schedule by Name

```
GET /api/scheduler/schedules/{name}
```

Returns the full schedule definition for the given name.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | Yes | Unique schedule name. |

```shell
curl 'http://localhost:8080/api/scheduler/schedules/nightly_report'
```

**Response** `200 OK` — returns a single `WorkflowSchedule` object (same format as the list response above).

### Delete Schedule

```
DELETE /api/scheduler/schedules/{name}
```

Permanently deletes the named schedule. Any in-flight workflow executions already started by the schedule are not affected.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | Yes | Unique schedule name. |

```shell
curl -X DELETE 'http://localhost:8080/api/scheduler/schedules/nightly_report'
```

**Response** `200 OK` — no response body.

---

## Search

### Search Schedules

```
GET /api/scheduler/schedules/search?start=0&size=100&sort=&workflowName=&name=&paused=&freeText=*
```

Returns a paginated `SearchResult<WorkflowSchedule>` with optional filtering by workflow name, schedule name prefix, and paused state.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `start` | `integer` | No | Page offset. Defaults to `0`. |
| `size` | `integer` | No | Number of results per page. Defaults to `100`. |
| `sort` | `string` | No | Sort order as `<field>:ASC` or `<field>:DESC`. Multiple values can be comma-separated, e.g. `name:ASC,createTime:DESC`. If direction is omitted, defaults to `DESC`. |
| `workflowName` | `string` | No | Filter by the target workflow definition name. |
| `name` | `string` | No | Filter by schedule name. |
| `paused` | `boolean` | No | Filter by paused state. Omit to return both paused and active schedules. |
| `freeText` | `string` | No | Full-text search query. Defaults to `*` (match all). |

```shell
# Find all paused schedules, most recently created first
curl 'http://localhost:8080/api/scheduler/schedules/search?paused=true&sort=createTime:DESC'

# Find schedules for a specific workflow, page 2
curl 'http://localhost:8080/api/scheduler/schedules/search?workflowName=generate_report&start=100&size=100'
```

**Response** `200 OK`

```json
{
  "totalHits": 3,
  "results": [
    {
      "name": "nightly_report",
      "cronExpression": "0 0 2 * * *",
      "zoneId": "UTC",
      "paused": false,
      "startWorkflowRequest": {
        "name": "generate_report",
        "version": 1,
        "input": {"reportType": "daily"}
      },
      "createTime": 1700000000000,
      "updatedTime": 1700000000000,
      "nextRunTime": 1700082000000
    }
  ]
}
```

---

## Pause and Resume

Pause and resume use `GET` requests to match the Orkes Conductor API contract.

### Pause Schedule

```
GET /api/scheduler/schedules/{name}/pause
```

Pauses the named schedule. No further workflow executions will be triggered until the schedule is resumed. Any workflow executions already in progress are not affected.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | Yes | Unique schedule name. |

```shell
curl 'http://localhost:8080/api/scheduler/schedules/nightly_report/pause'
```

**Response** `200 OK` — no response body.

### Resume Schedule

```
GET /api/scheduler/schedules/{name}/resume
```

Resumes a paused schedule. The schedule will fire again at the next cron interval.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | Yes | Unique schedule name. |

```shell
curl 'http://localhost:8080/api/scheduler/schedules/nightly_report/resume'
```

**Response** `200 OK` — no response body.

---

## Execution History

### WorkflowScheduleExecution Object

Every time a schedule fires, Conductor records a `WorkflowScheduleExecution` entry:

| Field | Type | Description |
|---|---|---|
| `executionId` | `string` | Unique ID for this execution record. |
| `scheduleName` | `string` | Name of the schedule that triggered this execution. |
| `workflowId` | `string` | Conductor workflow instance ID. Populated once the workflow is started successfully. |
| `scheduledTime` | `number` | Epoch millis of when this execution was scheduled to fire. |
| `executionTime` | `number` | Epoch millis of when the execution was actually processed. |
| `state` | `string` | One of `POLLED`, `EXECUTED`, or `FAILED`. See below. |
| `reason` | `string` | Error message or explanation when `state` is `FAILED`. |
| `stackTrace` | `string` | Stack trace when `state` is `FAILED`. |
| `zoneId` | `string` | Timezone that was in effect when this execution fired. |
| `workflowName` | `string` | Name of the workflow definition that was triggered. |
| `startWorkflowRequest` | `object` | The `StartWorkflowRequest` that was used to trigger this execution. |

**Execution states:**

| State | Description |
|---|---|
| `POLLED` | The schedule was dequeued; workflow start has not been confirmed yet. |
| `EXECUTED` | The workflow was started successfully. |
| `FAILED` | The workflow could not be started. See `reason` for details. |

### Get Execution History for a Schedule

```
GET /api/scheduler/schedules/{name}/executions?limit=10
```

Returns recent execution records for the named schedule, ordered by most recent first.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | Yes | Unique schedule name. |
| `limit` | `integer` | No | Maximum number of records to return. Defaults to `10`. |

```shell
# Get the last 25 executions for a schedule
curl 'http://localhost:8080/api/scheduler/schedules/nightly_report/executions?limit=25'
```

**Response** `200 OK`

```json
[
  {
    "executionId": "exec-uuid-1",
    "scheduleName": "nightly_report",
    "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
    "scheduledTime": 1700082000000,
    "executionTime": 1700082000123,
    "state": "EXECUTED",
    "zoneId": "UTC",
    "workflowName": "generate_report",
    "startWorkflowRequest": {
      "name": "generate_report",
      "version": 1,
      "input": {"reportType": "daily"}
    }
  },
  {
    "executionId": "exec-uuid-2",
    "scheduleName": "nightly_report",
    "workflowId": null,
    "scheduledTime": 1699995600000,
    "executionTime": 1699995600089,
    "state": "FAILED",
    "reason": "Workflow definition 'generate_report' version 1 not found",
    "zoneId": "UTC",
    "workflowName": "generate_report"
  }
]
```

### Search Executions (Cross-Schedule)

```
GET /api/scheduler/search/executions?start=0&size=100&sort=&freeText=*&query=
```

Searches execution records across all schedules with pagination support. Useful for auditing, monitoring dashboards, and incident investigation.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `start` | `integer` | No | Page offset. Defaults to `0`. |
| `size` | `integer` | No | Number of results per page. Defaults to `100`. |
| `sort` | `string` | No | Sort order as `<field>:ASC` or `<field>:DESC`, e.g. `startTime:DESC`. If direction is omitted, defaults to `DESC`. |
| `freeText` | `string` | No | Full-text search query. Defaults to `*` (match all). |
| `query` | `string` | No | SQL-like filter expression, e.g. `scheduleName = 'nightly_report' AND state = 'FAILED'`. |

```shell
# Find all failed executions across all schedules, most recent first
curl 'http://localhost:8080/api/scheduler/search/executions?query=state%3D%27FAILED%27&sort=scheduledTime:DESC&size=50'

# Free-text search for a specific schedule name
curl 'http://localhost:8080/api/scheduler/search/executions?freeText=nightly_report'
```

**Response** `200 OK`

```json
{
  "totalHits": 12,
  "results": [
    {
      "executionId": "exec-uuid-1",
      "scheduleName": "nightly_report",
      "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
      "scheduledTime": 1700082000000,
      "executionTime": 1700082000123,
      "state": "EXECUTED",
      "zoneId": "UTC",
      "workflowName": "generate_report"
    }
  ]
}
```

---

## Utilities

### Preview Next Cron Slots

```
GET /api/scheduler/nextFewSchedules?cronExpression=&scheduleStartTime=&scheduleEndTime=&limit=3
```

Returns the next few scheduled fire times (as epoch millis) for a given cron expression without requiring a saved schedule. Useful for validating a cron expression before saving it.

!!! note
    The `limit` parameter is capped at `5` regardless of the value provided.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `cronExpression` | `string` | Yes | 6-field cron expression to evaluate, e.g. `0 0 9 * * MON-FRI`. |
| `scheduleStartTime` | `number` | No | Epoch millis. Only return times at or after this value. |
| `scheduleEndTime` | `number` | No | Epoch millis. Only return times at or before this value. |
| `limit` | `integer` | No | Maximum number of times to return. Defaults to `3`. Maximum `5`. |

```shell
# Preview the next 5 weekday 9 AM slots
curl 'http://localhost:8080/api/scheduler/nextFewSchedules?cronExpression=0+0+9+*+*+MON-FRI&limit=5'

# Preview within a specific window
curl 'http://localhost:8080/api/scheduler/nextFewSchedules?cronExpression=0+30+*+*+*+*&scheduleStartTime=1700000000000&scheduleEndTime=1700010000000&limit=5'
```

**Response** `200 OK` — list of epoch millis values.

```json
[
  1700038800000,
  1700125200000,
  1700211600000,
  1700298000000,
  1700384400000
]
```

### Preview Next Execution Times for a Named Schedule

```
GET /api/scheduler/schedules/{name}/next-execution-times?count=5
```

Returns the next `count` scheduled fire times (as epoch millis) for an existing saved schedule. Unlike `/nextFewSchedules`, this endpoint reads the schedule's stored cron expression, timezone, `scheduleStartTime`, and `scheduleEndTime` constraints automatically.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | `string` | Yes | Unique schedule name. |
| `count` | `integer` | No | Number of times to return. Defaults to `5`. |

```shell
# See the next 3 execution times for the nightly_report schedule
curl 'http://localhost:8080/api/scheduler/schedules/nightly_report/next-execution-times?count=3'
```

**Response** `200 OK` — list of epoch millis values.

```json
[
  1700082000000,
  1700168400000,
  1700254800000
]
```

---

## Cron Expression Format

Conductor uses 6-field cron expressions with second-level precision. Fields are ordered left to right as:

```
second  minute  hour  day-of-month  month  day-of-week
```

| Field | Allowed Values | Special Characters |
|---|---|---|
| Second | `0–59` | `, - * /` |
| Minute | `0–59` | `, - * /` |
| Hour | `0–23` | `, - * /` |
| Day of month | `1–31` | `, - * ? / L W` |
| Month | `1–12` or `JAN–DEC` | `, - * /` |
| Day of week | `0–7` or `SUN–SAT` | `, - * ? / L #` |

**Common examples:**

| Expression | Description |
|---|---|
| `0 0 * * * *` | Every hour, on the hour |
| `0 0 9 * * MON-FRI` | 9:00 AM every weekday |
| `0 0 2 * * *` | 2:00 AM every day |
| `0 */15 * * * *` | Every 15 minutes |
| `0 0 0 1 * *` | Midnight on the first of every month |
| `0 0 8,12,17 * * MON-FRI` | 8 AM, noon, and 5 PM on weekdays |
