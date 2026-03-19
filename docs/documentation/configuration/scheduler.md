---
description: "Workflow Scheduler configuration in Conductor — cron expressions, WorkflowSchedule schema, persistence backends, and tunable properties for reliable time-based workflow execution."
---

# Workflow Scheduler

The Conductor Scheduler triggers workflow executions on a time-based cadence defined by a 6-field cron expression. Each schedule stores a complete `StartWorkflowRequest` and fires it at every matching cron slot, respecting optional start/end time bounds, pause state, timezone, and catchup rules for missed executions.

## Enabling the Scheduler

The scheduler is activated by setting the `conductor.scheduler.enabled` property to `true` alongside a compatible persistence backend. The `WorkflowSchedulerConfiguration` auto-configuration class activates only when this flag is present.

```properties
conductor.scheduler.enabled=true
conductor.db.type=postgres
```

!!! note "Persistence module required"
    The scheduler service will not start unless a `SchedulerDAO` bean is present on the classpath. Each supported database backend ships as a separate Gradle module. You must include exactly one persistence module in your application dependencies. See [Persistence Backends](#persistence-backends) for the full list.

---

## WorkflowSchedule Schema

A `WorkflowSchedule` is the top-level object created, updated, and returned by the scheduler REST API. The fields below correspond directly to `WorkflowSchedule.java`.

| Field | Type | Required | Description |
| :---- | :--- | :------- | :---------- |
| `name` | string | Yes | Unique identifier for this schedule. Used as the primary key across all API operations. |
| `cronExpression` | string | Yes | 6-field Spring cron expression evaluated against the schedule's timezone. See [Cron Expression Format](#cron-expression-format). |
| `startWorkflowRequest` | object | Yes | The workflow to start each time the schedule fires. Must include at minimum a `name` field identifying the workflow definition. |
| `zoneId` | string | No | IANA timezone for cron evaluation, e.g. `America/New_York`. Defaults to `UTC`. If omitted or blank, falls back to `conductor.scheduler.schedulerTimeZone`. |
| `paused` | boolean | No | When `true`, the schedule will not trigger new executions. Defaults to `false`. |
| `pausedReason` | string | No | Optional human-readable explanation for why the schedule is paused. Cleared automatically on resume. |
| `scheduleStartTime` | number (epoch ms) | No | Do not trigger before this Unix timestamp in milliseconds. Cron slots before this boundary are skipped. |
| `scheduleEndTime` | number (epoch ms) | No | Do not trigger after this Unix timestamp in milliseconds. The scheduler stops advancing the pointer once no future slot exists within this boundary. Must be after `scheduleStartTime` if both are set. |
| `runCatchupScheduleInstances` | boolean | No | When `true` and the server restarts after downtime, missed cron slots are executed sequentially (one per poll cycle) before resuming the normal cadence. When `false` (default), all missed slots are skipped and the next future slot is used. |
| `description` | string | No | Optional human-readable description of the schedule's purpose. |
| `extensionFields` | object | No | Arbitrary key-value pairs preserved during JSON round-trips for forward compatibility. Unknown fields on ingest are stored here and returned verbatim. |

### Read-only Fields

The following fields are set and maintained by the server and should not be included in create or update payloads.

| Field | Type | Description |
| :---- | :--- | :---------- |
| `createTime` | number (epoch ms) | Epoch milliseconds when the schedule was first saved. |
| `updatedTime` | number (epoch ms) | Epoch milliseconds of the most recent save or pause/resume operation. |
| `nextRunTime` | number (epoch ms) | Cached epoch milliseconds of the next planned execution. Recalculated after every trigger and after resume. |
| `createdBy` | string | Identity of the principal that created the schedule. Not enforced in the open-source build. |
| `updatedBy` | string | Identity of the principal that last updated the schedule. Not enforced in the open-source build. |

### startWorkflowRequest Fields

The `startWorkflowRequest` object is a standard Conductor `StartWorkflowRequest`. The scheduler injects two additional keys into the `input` map at dispatch time — these are merged with any input you define and override is not possible for these keys:

| Injected key | Value |
| :----------- | :---- |
| `scheduledTime` | Exact epoch milliseconds of the cron slot that triggered this execution. |
| `executionTime` | Actual wall-clock epoch milliseconds at the moment the workflow was dispatched (may differ from `scheduledTime` when jitter is enabled). |

---

## Complete Example

The following JSON creates a schedule that runs a workflow named `etl_pipeline` every weekday at 09:00 Eastern Time, from a fixed start date through the end of the year.

```json
{
  "name": "etl-weekday-morning",
  "cronExpression": "0 0 9 * * MON-FRI",
  "zoneId": "America/New_York",
  "paused": false,
  "description": "Runs the ETL pipeline every weekday morning at 9 AM Eastern.",
  "scheduleStartTime": 1735689600000,
  "scheduleEndTime": 1767225599000,
  "runCatchupScheduleInstances": false,
  "startWorkflowRequest": {
    "name": "etl_pipeline",
    "version": 1,
    "correlationId": "etl-scheduled",
    "input": {
      "environment": "production",
      "source": "s3://my-bucket/incoming"
    }
  }
}
```

---

## Cron Expression Format

The scheduler uses Spring's `CronExpression` (available since Spring Framework 5.3), which requires a **6-field** expression where the first field is **seconds**. This differs from the traditional 5-field Unix cron format.

```
┌────────────── second       (0–59)
│ ┌──────────── minute       (0–59)
│ │ ┌────────── hour         (0–23)
│ │ │ ┌──────── day-of-month (1–31)
│ │ │ │ ┌────── month        (1–12 or JAN–DEC)
│ │ │ │ │ ┌──── day-of-week  (0–7 or SUN–SAT, where both 0 and 7 represent Sunday)
│ │ │ │ │ │
* * * * * *
```

Special characters supported include `*` (any), `,` (list), `-` (range), `/` (step), `L` (last), `W` (weekday nearest), and `#` (nth occurrence).

!!! note "Spring CronExpression vs. Quartz"
    Spring's `CronExpression` does not permit both a day-of-month and a day-of-week restriction to be active simultaneously. If one is specified, the other must be set to `?`. For example, `0 0 9 ? * MON-FRI` is valid while `0 0 9 1 * MON-FRI` is not.

### Common Patterns

| Expression | Meaning |
| :--------- | :------ |
| `0 * * * * *` | Every minute, on the minute |
| `0 */5 * * * *` | Every 5 minutes |
| `0 0 * * * *` | Every hour, on the hour |
| `0 0 9 * * *` | Every day at 09:00 |
| `0 0 9 * * MON-FRI` | Every weekday at 09:00 |
| `0 0 0 * * SUN` | Every Sunday at midnight |
| `0 0 6 1 * ?` | First day of every month at 06:00 |
| `0 0 12 ? * 2#1` | First Monday of every month at noon |

---

## Tunable Properties

All properties are prefixed with `conductor.scheduler` and are bound from `SchedulerProperties.java`.

| Property | Type | Default | Description |
| :------- | :--- | :------ | :---------- |
| `conductor.scheduler.enabled` | boolean | `true` | Enables or disables the scheduler module entirely. When `false`, the polling loop does not start and no `SchedulerService` or `SchedulerResource` bean is created. |
| `conductor.scheduler.pollingInterval` | long (ms) | `100` | Milliseconds between polling cycles. Lower values reduce trigger latency but increase database load. |
| `conductor.scheduler.pollingThreadCount` | int | `1` | Number of threads in the polling executor. Increase only when `pollBatchSize` is large and dispatch latency is a concern. |
| `conductor.scheduler.pollBatchSize` | int | `5` | Maximum number of due schedules processed per polling cycle. Schedules beyond this limit are deferred to the next cycle. |
| `conductor.scheduler.schedulerTimeZone` | string | `UTC` | Default IANA timezone applied to schedules whose `zoneId` field is absent or blank. |
| `conductor.scheduler.archivalMaxRecords` | int | `5` | Maximum number of execution history records retained per schedule after pruning. Must be less than `archivalMaxRecordThreshold`. |
| `conductor.scheduler.archivalMaxRecordThreshold` | int | `10` | Number of execution records that must exist before the pruning job activates. Must be greater than `archivalMaxRecords`. |
| `conductor.scheduler.jitterMaxMs` | int (ms) | `0` | When greater than zero, each due schedule is dispatched after a random delay in `[0, jitterMaxMs]` milliseconds. Spreads concurrent workflow starts to reduce peak contention on the database connection pool and workflow execution threads. Dispatch is fully asynchronous when jitter is enabled — the poll thread is never blocked. Set to `0` to disable jitter and dispatch synchronously. |

!!! note "archivalMaxRecords constraint"
    The application will fail to start if `archivalMaxRecords >= archivalMaxRecordThreshold`. The threshold must always be strictly greater than the keep limit.

### Example Configuration

```properties
conductor.scheduler.enabled=true
conductor.scheduler.pollingInterval=500
conductor.scheduler.pollBatchSize=10
conductor.scheduler.schedulerTimeZone=America/Chicago
conductor.scheduler.archivalMaxRecords=20
conductor.scheduler.archivalMaxRecordThreshold=50
conductor.scheduler.jitterMaxMs=2000
```

---

## Persistence Backends

Each persistence backend is packaged as an independent Gradle module. Add exactly one to your application's dependency list. The corresponding `conductor.db.type` value activates the matching auto-configuration class, which runs Flyway migrations for the scheduler tables using a dedicated migration history table to avoid conflicts with the main Conductor schema migrations.

| Backend | Gradle module | `conductor.db.type` value(s) | Notes |
| :------ | :------------ | :--------------------------- | :---- |
| PostgreSQL | `org.conductoross:scheduler-postgres-persistence` | `postgres` | Includes deadlock/serialization-failure retry (up to 3 attempts). |
| MySQL | `org.conductoross:scheduler-mysql-persistence` | `mysql` | Includes deadlock retry (up to 3 attempts, error code 1213). |
| SQLite | `org.conductoross:scheduler-sqlite-persistence` | `sqlite` | Intended for development and single-node deployments. The DataSource must use a single-connection HikariCP pool (`maximumPoolSize=1`) to prevent in-memory database isolation issues. |
| Redis | `org.conductoross:scheduler-redis-persistence` | `redis_standalone`, `redis_cluster`, `redis_sentinel` | Reuses the existing `JedisProxy` bean — no additional Redis connection configuration required. |

### PostgreSQL Example

```properties
conductor.db.type=postgres
conductor.scheduler.enabled=true

spring.datasource.url=jdbc:postgresql://localhost:5432/conductor
spring.datasource.username=conductor
spring.datasource.password=secret
```

### MySQL Example

```properties
conductor.db.type=mysql
conductor.scheduler.enabled=true

spring.datasource.url=jdbc:mysql://localhost:3306/conductor
spring.datasource.username=conductor
spring.datasource.password=secret
```

### SQLite Example

```properties
conductor.db.type=sqlite
conductor.scheduler.enabled=true

spring.datasource.url=jdbc:sqlite:conductor.db
spring.datasource.hikari.maximum-pool-size=1
```

### Redis Example

```properties
conductor.db.type=redis_standalone
conductor.scheduler.enabled=true

spring.redis.host=localhost
spring.redis.port=6379
```
