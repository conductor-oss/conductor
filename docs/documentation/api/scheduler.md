---
description: Exact REST endpoints, request fields, defaults, and responses for the OSS Conductor scheduler.
---

# Scheduler API

The scheduler controller is mounted at `/api/scheduler`. It is present only when `conductor.scheduler.enabled=true`. All endpoints below return `200 OK` on success unless noted otherwise.

## Schedule model

| Field | Type | Required | Runtime default or behavior |
|---|---|---|---|
| `name` | string | Yes | Unique key used for create-or-update |
| `cronExpression` | string | One cron form required | Legacy single expression |
| `zoneId` | string | No | `UTC` |
| `cronSchedules` | array | One cron form required | Non-empty array takes precedence over `cronExpression`/`zoneId`; entry `zoneId` defaults to `UTC` |
| `startWorkflowRequest` | object | Yes | Standard workflow start request |
| `runCatchupScheduleInstances` | boolean | No | `false` |
| `paused` | boolean | No | `false` |
| `pausedReason` | string | No | Set by pause operation |
| `scheduleStartTime` | long | No | Epoch-millisecond lower bound |
| `scheduleEndTime` | long | No | Epoch-millisecond upper bound |
| `description` | string | No | User description |
| `createTime`, `updatedTime`, `createdBy`, `updatedBy`, `nextRunTime` | server fields | No | Populated by the service |

A `cronSchedules` entry contains `cronExpression` and optional `zoneId`. `startWorkflowRequest.correlationId` is copied literally. The scheduler adds `_startedByScheduler`, `_scheduledTime`, `_executedTime`, `_executionId`, and `_schedulerCron` to workflow input.

## Create or update

```http
POST /api/scheduler/schedules
Content-Type: application/json
```

The body is one schedule object. The response is the stored schedule, including computed state such as `nextRunTime`.

```bash
curl -sS -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  --data-binary @scheduler/examples/every-minute-schedule.json
```

## List and get

```http
GET /api/scheduler/schedules?workflowName={workflowName}
GET /api/scheduler/schedules/{name}
```

`workflowName` is optional. List returns an array; get returns one schedule or the service's not-found response.

## Search schedules

```http
GET /api/scheduler/schedules/search
```

| Query | Type | Default |
|---|---|---|
| `workflowName` | string | unset |
| `scheduleName` | string | unset |
| `paused` | boolean | unset |
| `freeText` | string | `*` |
| `start` | integer | `0` |
| `size` | integer | `100` |
| `sort` | comma-separated string | empty |

Returns `SearchResult<WorkflowSchedule>`.

## Pause and resume

```http
PUT /api/scheduler/schedules/{name}/pause?reason={reason}
PUT /api/scheduler/schedules/{name}/resume
```

`reason` is optional. Both operations return an empty `200 OK` response.

## Bulk pause and resume

```http
PUT /api/scheduler/bulk/pause
PUT /api/scheduler/bulk/resume
Content-Type: application/json
```

Each body is a JSON array of schedule names. The response is a `BulkResponse`, with successful names and per-name errors. These endpoints are registered with the same scheduler condition as the rest of the Scheduler API.

```json
["nightly-report", "hourly-cleanup"]
```

## Delete

```http
DELETE /api/scheduler/schedules/{name}
```

Returns an empty `200 OK` response.

## Preview next times

```http
GET /api/scheduler/nextFewSchedules?cronExpression={cron}&scheduleStartTime={ms}&scheduleEndTime={ms}&limit={n}
```

`cronExpression` is required. Bounds are optional. `limit` defaults to 5 and the implementation caps results at 5. Preview uses `conductor.scheduler.schedulerTimeZone`, not a request or schedule timezone, because this endpoint accepts no `zoneId`.

## Search scheduled executions

```http
GET /api/scheduler/search/executions
```

| Query | Type | Default |
|---|---|---|
| `query` | string | unset |
| `freeText` | string | `*` |
| `start` | integer | `0` |
| `size` | integer | `100` |
| `sort` | comma-separated string | empty |

Returns `SearchResult<WorkflowScheduleExecutionModel>`. Execution records include the scheduler execution ID, scheduled and execution times, workflow name/ID, state, and failure details where applicable.

## Administrator endpoints

```http
GET /api/scheduler/admin/requeue
GET /api/scheduler/admin/pause
GET /api/scheduler/admin/resume
```

These operate on scheduler internals for recovery/debugging. They are not per-schedule pause/resume endpoints and should be access-controlled.

## Unsupported operations

The controller has no run-now endpoint, manual-backfill endpoint, overlap-policy field, or correlation-template expansion. Use direct workflow start for an ad hoc run, and implement concurrency/idempotency policy in the workflow or downstream system.
