---
description: Runnable schedule recipes backed by the canonical scheduler examples.
---

# Scheduled workflow recipes

These recipes reuse the checked-in fixtures under `scheduler/examples/`. Start with the [scheduling guide](../how-tos/Workflows/scheduling-workflows.md) for semantics and the [Scheduler API](../../documentation/api/scheduler.md) for the exact REST contract.

## Every minute

```json
--8<-- "scheduler/examples/every-minute-schedule.json"
```

```bash
conductor schedule create scheduler/examples/every-minute-schedule.json
```

## Weekdays in a named timezone

```json
--8<-- "scheduler/examples/daily-report-schedule.json"
```

The IANA zone follows local daylight-saving transitions. The correlation ID, if supplied, is literal; use the injected `_executionId` inside the workflow for per-run identity.

## Catch up missed cron slots

```json
--8<-- "scheduler/examples/catchup-schedule.json"
```

Catchup can create a burst after downtime. Make the target workflow idempotent and capacity-aware.

## Bound a schedule to a window

`scheduler/examples/bounded-schedule-template.json` contains `__START_MS__` and `__END_MS__` placeholders. Replace them with epoch-millisecond numbers before posting the file; the template itself is intentionally not valid as a final schedule payload.

```bash
curl -sS -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  --data-binary @bounded-schedule.json
```

## Read scheduler metadata in a workflow

The canonical workflow uses `_scheduledTime` and `_executedTime` to compute a reporting window:

```json
--8<-- "scheduler/examples/input-param-workflow.json"
```

Its paired schedule is:

```json
--8<-- "scheduler/examples/input-param-schedule.json"
```

The other injected values are `_startedByScheduler`, `_executionId`, and `_schedulerCron`.

## Demonstrate overlapping runs

```json
--8<-- "scheduler/examples/concurrent-schedule.json"
```

Conductor has no native overlap policy. The paired `concurrent-workflow.json` demonstrates that the next slot can start while the prior execution remains active.

## More canonical fixtures

The fixture family also includes retry, `DO_WHILE`, and parallel multi-step workflows. Register workflow files with the metadata API or CLI before creating their paired schedule. See [`scheduler/examples/README.md`](https://github.com/conductor-oss/conductor/blob/main/scheduler/examples/README.md) for the complete local walkthrough.
