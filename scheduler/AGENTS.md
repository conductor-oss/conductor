# AGENTS.md — conductor-scheduler

AI agent context for the `conductor-scheduler` module. Read the root `AGENTS.md` first for
project-wide conventions; this file adds scheduler-specific detail.

## Module Overview

`conductor-scheduler` is a self-contained Gradle module that adds cron-based workflow scheduling
to Conductor OSS. It is **convergence-compatible** with Orkes Conductor's enterprise scheduler:
the DAO interface, data model, and REST API paths are intentionally identical so Orkes can adopt
the OSS implementation with minimal changes (orgId injection only).

The scheduler is enabled via `conductor.scheduler.enabled=true` and requires PostgreSQL. It is
inactive (no-op) when disabled or when no `WorkflowService` bean is present.

## Key Files

| File | Purpose |
|------|---------|
| `src/main/java/.../config/SchedulerProperties.java` | All tunable knobs (`poll-batch-size`, `polling-interval`, `jitter-max-ms`, etc.) |
| `src/main/java/.../service/SchedulerService.java` | Core logic: poll loop, cron evaluation, dispatch, jitter, pruning |
| `src/main/java/.../dao/SchedulerDAO.java` | DAO interface (mirrors Orkes signatures exactly) |
| `src/main/java/.../dao/PostgresSchedulerDAO.java` | JdbcTemplate Postgres implementation |
| `src/main/java/.../api/SchedulerResource.java` | REST controller at `/api/scheduler` |
| `src/main/resources/db/migration/` | Flyway SQL migrations (separate history table: `flyway_schema_history_scheduler`) |
| `examples/` | Eight verified example schedules and workflows |
| `scripts/` | Live concurrency test scripts (#9-12) |
| `TEST_SCENARIOS.md` | Full log of all 19 test scenarios: done, planned, and deferred |
| `docker/docker-compose-scheduler-demo.yaml` | Self-contained Docker stack (Conductor + Postgres) |
| `docker/server/config/config-scheduler-demo.properties` | Demo stack config (poll-batch-size=50, polling-interval=1000ms) |

## Build and Test Commands

```bash
# Run all scheduler tests (83 total)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :conductor-scheduler:test

# Apply spotless formatting (required before commit)
./gradlew :conductor-scheduler:spotlessApply

# Build the module JAR (skip tests)
./gradlew :conductor-scheduler:build -x test

# Start the live Docker demo stack
docker compose -f docker/docker-compose-scheduler-demo.yaml up -d

# Rebuild the Docker image after Java code changes
./gradlew :conductor-scheduler:build -x test
docker compose -f docker/docker-compose-scheduler-demo.yaml up -d --build conductor-server

# After changing only config (not Java), copy config and restart instead of full rebuild:
docker cp docker/server/config/config-scheduler-demo.properties conductor-server:/app/config/
docker compose -f docker/docker-compose-scheduler-demo.yaml restart conductor-server
```

## Critical Design Invariants

These must not be broken. Each exists to prevent a specific correctness bug.

### 1. Pointer advancement is synchronous — always before dispatch

In `SchedulerService.handleSchedule()`, the call to `setNextRunTimeInEpoch()` happens
**before** `startWorkflow()` (or the jitter executor task). This prevents a second poll cycle
from firing the same slot while the workflow start is still in flight.

Do not reorder these operations. Do not move pointer advancement into `dispatchWorkflow()`.

### 2. Jitter dispatch is async; pointer advancement is not

When `jitter-max-ms > 0`, workflow dispatch is submitted to a `ScheduledExecutorService` with
a random delay. The poll thread returns immediately after submitting — it must not block on the
jitter sleep. `dispatchWorkflow()` runs on the jitter executor, not the poll thread.

The `jitterExecutor` is created in `initExecutors()`, which is called from `start()`. Tests
use `initExecutors()` directly to create the executor without starting the background poll loop.

### 3. Catchup mode steps one slot per poll cycle

When `runCatchupScheduleInstances=true`, `handleSchedule()` computes the next run from
`scheduledTime` (the current overdue slot), not from `now`. This causes the pointer to advance
by exactly one cron slot per cycle, replaying each missed slot in order.

If you change the `computeNextRunTime` call, verify that catchup still steps slot-by-slot and
does not jump to the current time.

### 4. Bounded schedule end-time handling

When `computeNextRunTime()` returns null (no future slot within `scheduleEndTime`), the pointer
must be set to `scheduleEndTime + 1`. Without this, the last slot before `endTime` re-fires
on every poll cycle until `now > endTime`.

### 5. orgId is always "default" in OSS

`WorkflowSchedule.DEFAULT_ORG_ID = "default"` is passed to every DAO call. The field exists
for Orkes convergence (Orkes passes real tenant IDs). Do not remove it or add an API surface
that exposes it to users.

## Known Gotchas (from Live Testing)

- **WAIT task duration**: Use `"90s"`, `"2m"`, `"1h"` — not ISO-8601 `PT90S`. Conductor's
  `DateTimeUtils.parseDuration` uses its own regex, not the Java Duration parser.

- **HTTP task URL encoding**: `%2F` in query params is passed literally. Use literal `/` in
  timezone strings (e.g., `America/New_York`, not `America%2FNew_York`).

- **DO_WHILE output keys**: Output is keyed by iteration number as a string (`"1"`, `"2"`, `"3"`),
  not by task reference name at the top level. Reference the last iteration via
  `${poll_loop.output.3.fetch_task.response.body.field}`.

- **`poll-batch-size` default is 5**: With the default config, only 5 schedules fire per poll
  cycle. For thundering-herd scenarios (many schedules at the same cron tick), increase this
  and consider enabling `jitter-max-ms`.

- **`polling-interval` in demo config is 1000ms** (changed from the original 10000ms default).
  The Java default in `SchedulerProperties` is 100ms. The demo config overrides this.

- **`resumeSchedule()` with catchup=false**: Advances the pointer to the next *future* slot.
  A paused schedule with a stale `nextRunTime` will not fire immediately on resume — it skips
  to the next upcoming cron tick. This is by design.

- **`scheduleNextStartTime` vs `nextRunTime`**: The REST API response field is `nextRunTime`
  (not `scheduleNextStartTime`). Do not confuse these when writing API clients.

## SchedulerService: Poll Loop Flow

```
pollAndExecuteSchedules()               ← runs every {polling-interval}ms
  for each schedule (up to pollBatchSize):
    isDue(schedule, now)?
      getNextRunTimeInEpoch()           ← DB call per schedule (O(N))
      check paused, startTime, endTime
    handleSchedule(schedule, now)
      getNextRunTimeInEpoch()           ← fetch exact slot
      computeNextRunTime()              ← advance pointer
      saveExecutionRecord(POLLED)       ← record dispatch attempt
      setNextRunTimeInEpoch(next)       ← SYNCHRONOUS — prevents double-fire
      if jitter-max-ms > 0:
        jitterExecutor.schedule(dispatchWorkflow, random delay)
      else:
        dispatchWorkflow()              ← SYNCHRONOUS
          startWorkflow(req)
          saveExecutionRecord(EXECUTED or FAILED)
          pruneExecutionHistory()
  cleanupStalePollRecords()             ← POLLED > 5min → FAILED
```

## scheduledTime and executionTime Injection

Every triggered workflow receives two extra input keys injected by the scheduler:

- `scheduledTime` — epoch ms of the cron slot (exact, always on the minute/second boundary)
- `executionTime` — epoch ms when `startWorkflow()` was actually called (includes poll delay
  and any jitter; use this for auditing, not for time-window calculations)

This matches Orkes Conductor's behavior. Do not remove these injections — they are part of the
convergence contract and are tested in `SchedulerServicePhase2Test`.

## Execution State Machine

```
POLLED  →  EXECUTED   (normal path)
POLLED  →  FAILED     (startWorkflow threw, or stale cleanup after 5 minutes)
```

Records are stored in `workflow_schedule_execution`. Pruning keeps the last
`archival-max-records` (default 5) per schedule, triggered when count exceeds
`archival-max-record-threshold` (default 10).

## Adding a New Example

1. Create `examples/{name}-workflow.json` (workflow definition) and `examples/{name}-schedule.json`
2. Register the workflow via `POST /api/metadata/workflow`
3. Register the schedule via `POST /api/scheduler/schedules`
4. Add a seed step to `examples/seed.sh` so the Docker demo picks it up automatically
5. Document it in `TEST_SCENARIOS.md` and in the PR/README

## Test Classes

| Class | Tests | What it covers |
|-------|-------|----------------|
| `PostgresSchedulerDAOTest` | 13 | Real Postgres via Testcontainers; UPSERT, execution records, cleanup |
| `SchedulerServiceTest` | 20 | Unit; basic CRUD, cron math, catchup, bounded, next-execution preview |
| `SchedulerServicePhase2Test` | 23 | Edge cases: failure modes, injection, pruning, jitter, invalid inputs |
| `SchedulerServiceStressTest` | 11 | Concurrency: no double-fire, catchup step-by-step, bounded repeat-fire |
| `SchedulerResourceTest` | 16 | REST layer: request/response mapping, error codes |
