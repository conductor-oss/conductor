---
description: Create and operate cron schedules for Conductor workflows, including timezones, catchup, bounds, and injected input.
---

# Schedule workflows

A schedule creates a new workflow execution at each matching cron slot. Use it when the clock owns the decision to run; use [event orchestration](../event-bus.md) when a message owns that decision.

## Prerequisites

- The target workflow definition is registered.
- The scheduler is enabled on the server and its persistence module is configured.
- Workers required by the target workflow are running.
- The Conductor CLI is configured for simple CRUD, or REST is available for the complete scheduler model.

## Create a simple schedule

The canonical fixture runs once per minute in UTC:

```json
--8<-- "scheduler/examples/every-minute-schedule.json"
```

Create it with the CLI:

```bash
conductor schedule create scheduler/examples/every-minute-schedule.json
conductor schedule get every-minute-demo-schedule
```

Success is a saved schedule with a non-null `nextRunTime`, followed by a workflow execution after the next slot. Use REST for multi-expression cron schedules, bounds, catchup behavior, preview, and execution-history search; CLI releases do not expose every scheduler field or operation consistently.

## Use the complete REST interface

```bash
curl -sS -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  --data-binary @scheduler/examples/every-minute-schedule.json
```

The same `POST` creates or updates by schedule name and returns `200 OK` with the stored schedule. See the [Scheduler API](../../../documentation/api/scheduler.md) for exact bodies, query parameters, and status codes.

## Cron and timezone behavior

Conductor uses Spring six-field cron expressions: second, minute, hour, day of month, month, and day of week. Macros such as `@daily` are also accepted by Spring's parser.

The legacy single-expression form uses `cronExpression` plus `zoneId` (default `UTC`). The multi-expression form uses `cronSchedules`; when that array is non-empty it takes precedence over the legacy fields, and each entry has its own `zoneId` defaulting to UTC.

```json
{
  "name": "regional-report",
  "cronSchedules": [
    {"cronExpression": "0 0 9 * * MON-FRI", "zoneId": "America/New_York"},
    {"cronExpression": "0 0 9 * * MON-FRI", "zoneId": "Europe/London"}
  ],
  "startWorkflowRequest": {
    "name": "daily_report_workflow",
    "version": 1
  }
}
```

Cron evaluation follows the selected IANA timezone, including daylight-saving transitions. A local time that does not exist during a spring-forward transition is skipped by the cron engine; repeated local times follow the engine's next-instant calculation. Test business-sensitive schedules around DST boundaries.

The preview endpoint accepts no timezone parameter. It evaluates in `conductor.scheduler.schedulerTimeZone` (UTC by default), not a schedule's `zoneId`, and returns at most five times even if `limit` is larger.

## Catch up and bound execution

`runCatchupScheduleInstances: true` advances through missed cron slots after downtime. It can create a burst, so the workflow and dependencies must be idempotent and capacity-aware. With the default `false`, the scheduler advances from current time rather than replaying every missed slot.

Use `scheduleStartTime` and `scheduleEndTime` as epoch-millisecond inclusive bounds. A schedule outside its window stops producing new runs; it is not deleted automatically.

## Inputs added by the scheduler

The scheduler copies `startWorkflowRequest.input`, then adds these values to every execution:

| Input | Meaning |
|---|---|
| `_startedByScheduler` | Schedule name |
| `_scheduledTime` | Intended cron slot, epoch milliseconds |
| `_executedTime` | Actual dispatch time, epoch milliseconds |
| `_executionId` | Unique scheduler execution-record ID |
| `_schedulerCron` | Cron expression and zone that produced this run |

Use `${workflow.input._executionId}` when a downstream system needs per-run identity. `startWorkflowRequest.correlationId` is copied literally; the scheduler does **not** interpolate `${scheduledTime}` or other templates in it. If every workflow execution needs a unique correlation ID, derive it in the workflow from injected input or start the workflow through code that constructs the ID.

## Operate schedules

```bash
conductor schedule list
conductor schedule pause every-minute-demo-schedule
conductor schedule resume every-minute-demo-schedule
conductor schedule delete every-minute-demo-schedule
```

REST also supports filtering, search, a pause reason, and scheduled-execution history:

```bash
curl 'http://localhost:8080/api/scheduler/schedules/search?paused=false&size=20'
curl 'http://localhost:8080/api/scheduler/search/executions?freeText=every-minute-demo-schedule&size=20'
```

After pausing, verify the stored `paused` state and confirm no new execution appears after a cron slot. After resuming, confirm a new scheduled execution and inspect all five injected fields.

## Limitations

- There is no native overlap policy. If a prior workflow is still running, the next slot can start another execution.
- There is no scheduler endpoint for "run now" or manual backfill. Start the target workflow directly for an ad hoc run and pass the intended window explicitly.
- Preview is single-cron, capped at five, and uses the server scheduler timezone.
- Java, Python, TypeScript, and Go SDKs can call the REST surface through generated or low-level clients, but this repository does not define a consistent high-level scheduler API across all SDKs. Treat REST as the portable complete interface.
- `correlationId` is literal, not a schedule template.

For runnable catchup, bounded, concurrency, input, retry, and multi-step variants, use the [scheduled workflow recipes](../../cookbook/workflow-scheduling.md), which reuse `scheduler/examples/`.

<a id="how-it-works"></a>
<a id="cron-expression-format"></a>
<a id="creating-a-schedule"></a>
<a id="previewing-execution-times"></a>
<a id="pausing-and-resuming"></a>
<a id="listing-and-searching-schedules"></a>
<a id="viewing-execution-history"></a>
<a id="deleting-a-schedule"></a>
<a id="passing-input-to-scheduled-workflows"></a>
<a id="configuration"></a>
