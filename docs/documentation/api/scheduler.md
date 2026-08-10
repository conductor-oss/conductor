---
description: "REST API reference for Conductor's workflow scheduler — create, list, search, pause, resume, delete schedules, preview cron execution times, and search execution history."
---

# Scheduler API

All scheduler endpoints are relative to `/api/scheduler`.

## Create or update a schedule

```
POST /api/scheduler/schedules
```

Creates a new schedule or updates an existing one (matched by `name`).

**Request body:**

```json
{
  "name": "daily-report-schedule",
  "cronExpression": "0 0 9 * * MON-FRI",
  "zoneId": "America/New_York",
  "startWorkflowRequest": {
    "name": "daily_report_workflow",
    "version": 1,
    "input": {},
    "correlationId": "daily-report-${scheduledTime}"
  },
  "runCatchupScheduleInstances": false,
  "paused": false,
  "scheduleStartTime": 0,
  "scheduleEndTime": 0,
  "description": "Triggers the daily report workflow on weekday mornings"
}
```

**Schedule fields:**

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | Yes | — | Unique schedule identifier |
| `cronExpression` | string | Yes | — | 6-field Spring cron expression (second precision) |
| `zoneId` | string | No | `UTC` | IANA timezone for cron evaluation |
| `startWorkflowRequest` | object | Yes | — | Workflow trigger configuration (see below) |
| `runCatchupScheduleInstances` | boolean | No | `false` | Fire missed slots on scheduler restart |
| `paused` | boolean | No | `false` | Create in paused state |
| `scheduleStartTime` | long | No | — | Earliest fire time (epoch ms) |
| `scheduleEndTime` | long | No | — | Latest fire time (epoch ms) |
| `description` | string | No | — | Free-text description |

**startWorkflowRequest fields:**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | Workflow name |
| `version` | integer | No | Workflow version (latest if omitted) |
| `input` | object | No | Static input merged with auto-injected `_scheduledTime` and `_executedTime` |
| `correlationId` | string | No | Supports `${scheduledTime}` template variable |
| `taskToDomain` | object | No | Task-to-domain mapping |
| `priority` | integer | No | Execution priority (0-99) |

**Response:** `200 OK` — returns the saved `WorkflowSchedule` object.

??? note "Example using cURL"
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
        }
      }'
    ```

---

## List all schedules

```
GET /api/scheduler/schedules
```

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `workflowName` | string | No | Filter by workflow name |

**Response:** `200 OK` — array of `WorkflowSchedule` objects.

??? note "Example using cURL"
    ```shell
    # List all
    curl 'http://localhost:8080/api/scheduler/schedules'

    # Filter by workflow
    curl 'http://localhost:8080/api/scheduler/schedules?workflowName=daily_report_workflow'
    ```

---

## Search schedules

```
GET /api/scheduler/schedules/search
```

**Query parameters:**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `workflowName` | string | No | — | Filter by workflow name |
| `scheduleName` | string | No | — | Filter by schedule name |
| `paused` | boolean | No | — | Filter by paused state |
| `freeText` | string | No | `*` | Free-text search |
| `start` | integer | No | `0` | Pagination offset |
| `size` | integer | No | `100` | Page size |
| `sort` | string | No | — | Sort fields |

**Response:** `200 OK` — `SearchResult` with `totalHits` and `results` array.

??? note "Example using cURL"
    ```shell
    curl 'http://localhost:8080/api/scheduler/schedules/search?workflowName=daily_report_workflow&size=10'
    ```

---

## Get a schedule by name

```
GET /api/scheduler/schedules/{name}
```

**Response:** `200 OK` — `WorkflowSchedule` object.

??? note "Example using cURL"
    ```shell
    curl 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule'
    ```

---

## Delete a schedule

```
DELETE /api/scheduler/schedules/{name}
```

**Response:** `204 No Content`

??? note "Example using cURL"
    ```shell
    curl -X DELETE 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule'
    ```

---

## Pause a schedule

```
PUT /api/scheduler/schedules/{name}/pause
```

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `reason` | string | No | Reason for pausing |

**Response:** `200 OK`

??? note "Example using cURL"
    ```shell
    curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/pause?reason=maintenance+window'
    ```

---

## Resume a schedule

```
PUT /api/scheduler/schedules/{name}/resume
```

**Response:** `200 OK`

??? note "Example using cURL"
    ```shell
    curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/resume'
    ```

---

## Preview next execution times

```
GET /api/scheduler/nextFewSchedules
```

Preview when a cron expression will fire next, without creating a schedule.

**Query parameters:**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `cronExpression` | string | Yes | — | Cron expression to evaluate |
| `scheduleStartTime` | long | No | — | Window start (epoch ms) |
| `scheduleEndTime` | long | No | — | Window end (epoch ms) |
| `limit` | integer | No | `5` | Number of times to return |

**Response:** `200 OK` — array of epoch-millisecond timestamps.

??? note "Example using cURL"
    ```shell
    curl 'http://localhost:8080/api/scheduler/nextFewSchedules?cronExpression=0+0+9+*+*+MON-FRI&limit=5'
    ```

---

## Search execution history

```
GET /api/scheduler/search/executions
```

Search past scheduled workflow executions.

**Query parameters:**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `query` | string | No | — | Structured query |
| `freeText` | string | No | `*` | Free-text search (matches schedule name, workflow name) |
| `start` | integer | No | `0` | Pagination offset |
| `size` | integer | No | `100` | Page size |
| `sort` | string | No | — | Sort fields |

**Response:** `200 OK` — `SearchResult` with execution records:

| Field | Description |
|---|---|
| `executionId` | Unique execution record ID |
| `scheduleName` | Parent schedule name |
| `scheduledTime` | Cron slot time (epoch ms) |
| `executionTime` | Actual dispatch time (epoch ms) |
| `workflowName` | Triggered workflow name |
| `workflowId` | Triggered workflow instance ID |
| `state` | `POLLED`, `EXECUTED`, or `FAILED` |
| `reason` | Failure reason (if `FAILED`) |

??? note "Example using cURL"
    ```shell
    curl 'http://localhost:8080/api/scheduler/search/executions?freeText=daily-report-schedule&size=20'
    ```
