# Scheduler DAO Expansion Design

**Date:** 2026-03-19
**Status:** Approved

## Overview

Expand the Conductor scheduler persistence layer with two new backends (SQLite and Redis), replace all mock-based scheduler service tests with real integration tests using Testcontainers, and add shared abstract contract test bases via the `java-test-fixtures` Gradle plugin.

## Context

The scheduler module currently has two persistence backends:
- `scheduler-postgres-persistence` — PostgreSQL via JdbcTemplate
- `scheduler-mysql-persistence` — MySQL via JdbcTemplate

Both were recently refactored (bug fixes including transactional deletes, `execution_time` column addition, N+1 query elimination).

Existing mock-based service tests (`SchedulerServiceTest`, `SchedulerServicePhase2Test`, `SchedulerServiceStressTest`) test against mocked DAOs and will be replaced by real integration tests that run against actual databases/stores.

## Goals

1. Add `conductor-scheduler-sqlite-persistence` module (embedded, no Docker, ideal for dev/test)
2. Add `conductor-scheduler-redis-persistence` module (reuses existing `JedisProxy` infrastructure)
3. Replace all mock-based service tests with Testcontainers-backed integration tests
4. Share abstract contract tests across all persistence modules via `java-test-fixtures`

## Non-Goals

- Cassandra DAO (explicitly excluded)
- Execution record indexing improvements (deferred)
- Changing the `SchedulerDAO` interface shape

---

## Section 1: SQLite Module (`conductor-scheduler-sqlite-persistence`)

### Module structure

```
conductor-scheduler-sqlite-persistence/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/.../scheduler/sqlite/
    │   │   ├── dao/SqliteSchedulerDAO.java
    │   │   └── config/SqliteSchedulerConfiguration.java
    │   └── resources/db/migration_scheduler_sqlite/
    │       └── V1__scheduler_tables.sql
    └── test/
        └── java/.../scheduler/sqlite/
            ├── dao/SqliteSchedulerDAOTest.java
            └── service/SqliteSchedulerServiceIntegrationTest.java
```

### DAO implementation

- `SqliteSchedulerDAO` uses `JdbcTemplate` + `TransactionTemplate(DataSourceTransactionManager)` — same pattern as Postgres/MySQL DAOs.
- `findAllByNames(Set<String> names)` builds `IN (?, ?, ...)` placeholders manually (SQLite does not have array binding).
- `getExecutionRecords` orders by `execution_time DESC` — no `NULLS LAST` (SQLite sorts NULLs last by default for DESC).
- `saveExecutionRecord` uses `INSERT OR REPLACE INTO` (SQLite upsert syntax).
- `updateSchedule` uses `INSERT OR REPLACE INTO` (SQLite upsert syntax).

### Schema

Clean V1 schema — `execution_time` column included from the start; no `workflow_scheduled_executions` table.

```sql
CREATE TABLE IF NOT EXISTS scheduler (
    scheduler_name TEXT PRIMARY KEY,
    workflow_name  TEXT,
    json_data      TEXT NOT NULL,
    next_run_time  INTEGER
);

CREATE TABLE IF NOT EXISTS scheduler_execution (
    execution_id   TEXT PRIMARY KEY,
    schedule_name  TEXT NOT NULL,
    state          TEXT,
    execution_time INTEGER,
    json_data      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS scheduler_execution_sched_time_idx
    ON scheduler_execution (schedule_name, execution_time DESC);
```

### Auto-configuration

`SqliteSchedulerConfiguration` is annotated:
```java
@ConditionalOnExpression(
    "'${conductor.db.type:}' == 'memory' && '${conductor.scheduler.enabled:true}' != 'false'"
)
```

Uses HikariCP with `maximumPoolSize=1` (SQLite in-memory databases are connection-scoped; a single connection guarantees all operations share the same in-memory database).

Flyway uses a dedicated history table: `flyway_schema_history_scheduler_sqlite`.

### Tests

- `SqliteSchedulerDAOTest` extends `AbstractSchedulerDAOTest` from `scheduler` testFixtures — no Docker, SQLite in-memory.
- `SqliteSchedulerServiceIntegrationTest` extends `AbstractSchedulerServiceIntegrationTest` from `scheduler` testFixtures — no Docker.
- Tests are fast; no Testcontainers dependency needed for SQLite.

---

## Section 2: Redis Module (`conductor-scheduler-redis-persistence`)

### Module structure

```
conductor-scheduler-redis-persistence/
├── build.gradle
└── src/
    ├── main/
    │   └── java/.../scheduler/redis/
    │       ├── dao/RedisSchedulerDAO.java
    │       └── config/RedisSchedulerConfiguration.java
    └── test/
        └── java/.../scheduler/redis/
            ├── dao/RedisSchedulerDAOTest.java
            └── service/RedisSchedulerServiceIntegrationTest.java
```

### Dependencies

- Reuses `conductor-redis-persistence` for the `JedisProxy` bean (which already handles standalone, cluster, and sentinel Redis).
- No additional Redis client dependencies needed.

### Key design

All keys are prefixed with `conductor_scheduler:`.

| Structure | Key | Purpose |
|-----------|-----|---------|
| HASH | `conductor_scheduler:schedules` | All schedule objects (field = schedule name, value = JSON) |
| SET | `conductor_scheduler:by_workflow:{wfName}` | Schedule names for a given workflow |
| ZSET | `conductor_scheduler:next_run` | Schedule names scored by next run time (epoch ms) — polling hot path |
| STRING | `conductor_scheduler:execution:{executionId}` | Individual execution record JSON |
| ZSET | `conductor_scheduler:exec_by_sched:{scheduleName}` | Execution IDs scored by execution time — enables `getExecutionRecords(name, limit)` |
| SET | `conductor_scheduler:pending_execs` | Execution IDs in POLLED state |

### DAO operations

- `updateSchedule`: `HSET schedules`, update `by_workflow` SET membership (remove from old, add to new), `ZADD next_run` scored with `schedule.getNextRunTime()`.
- `findScheduleByName`: `HGET schedules {name}`, deserialize.
- `getAllSchedules`: `HVALS schedules`, deserialize all.
- `findAllSchedules(workflowName)`: `SMEMBERS by_workflow:{workflowName}`, then `HMGET schedules {names}`.
- `findAllByNames(names)`: `HMGET schedules {names}`.
- `deleteWorkflowSchedule`: Remove from HASH, remove from `by_workflow` SET, `ZREM next_run`, delete all execution keys from `exec_by_sched`, remove from `pending_execs`, delete `exec_by_sched` key itself.
- `setNextRunTimeInEpoch`: `ZADD next_run {epoch} {name}`, update the JSON in `schedules` HASH.
- `getNextRunTimeInEpoch`: `ZSCORE next_run {name}` → parse as long, -1L if absent.
- `saveExecutionRecord`: `SET execution:{id}` (JSON), `ZADD exec_by_sched:{name}` scored with `executionTime`, conditional `SADD pending_execs {id}` if state == POLLED (remove on other states).
- `getExecutionRecords(name, limit)`: `ZREVRANGEBYSCORE exec_by_sched:{name} +inf -inf LIMIT 0 {limit}`, then `MGET execution:{id}` for each ID.
- `getPendingExecutionRecords()`: `SMEMBERS pending_execs`, then `MGET` each.
- `readExecutionRecord(id)`: `GET execution:{id}`.
- `removeExecutionRecord(id)`: `DEL execution:{id}`, `SREM pending_execs {id}`.
- `getPendingExecutionRecordIds()`: `SMEMBERS pending_execs`.

### Auto-configuration

`RedisSchedulerConfiguration` is annotated:
```java
@ConditionalOnExpression(
    "('${conductor.db.type:}' == 'redis_standalone' " +
    "|| '${conductor.db.type:}' == 'redis_cluster' " +
    "|| '${conductor.db.type:}' == 'redis_sentinel') " +
    "&& '${conductor.scheduler.enabled:true}' != 'false'"
)
```

Injects the existing `JedisProxy` bean — no new connection infrastructure.

### Tests

- `RedisSchedulerDAOTest` extends `AbstractSchedulerDAOTest` — uses Testcontainers `GenericContainer("redis:7-alpine")` on port 6379.
- `RedisSchedulerServiceIntegrationTest` extends `AbstractSchedulerServiceIntegrationTest`.
- Container is shared across test methods via `@ClassRule` / `@BeforeClass`.

---

## Section 3: Real Service Tests (Replacing Mocks)

### What is deleted

The following mock-based test classes are deleted:
- `scheduler/src/test/java/.../service/SchedulerServiceTest.java`
- `scheduler/src/test/java/.../service/SchedulerServicePhase2Test.java`
- `scheduler/src/test/java/.../service/SchedulerServiceStressTest.java`

### What replaces them

Two new abstract base classes added to `scheduler`'s `testFixtures` source set:

#### `AbstractSchedulerDAOTest`

Contract tests for the `SchedulerDAO` interface. Each persistence module subclasses this and provides a concrete DAO instance. Tests cover:
- CRUD operations for schedules and executions
- `findAllByNames` with empty set, single name, multiple names
- `getExecutionRecords` respects `limit` and returns newest first
- `getPendingExecutionRecords` returns only POLLED records
- `deleteWorkflowSchedule` removes execution records atomically
- `setNextRunTimeInEpoch` / `getNextRunTimeInEpoch` round-trip
- Archival/cleanup behavior

#### `AbstractSchedulerServiceIntegrationTest`

Behavior tests for `SchedulerService` running against a real DAO. Covers all scenarios previously in the mock tests plus DST and catchup edge cases:
- `saveSchedule` sets timestamps and nextRunTime
- Invalid cron / missing name / missing request all throw
- `pauseSchedule` / `resumeSchedule` state transitions
- `deleteSchedule` delegates to DAO
- `computeNextRunTime` with start/end time bounds
- `pollAndExecuteSchedules` fires due schedules, skips paused, records failure
- Catchup enabled vs disabled behavior
- DST spring-forward and fall-back edge cases
- Slow polling (poll interval > cron interval)

### Concrete subclasses per module

| Module | DAO test class | Service test class |
|--------|---------------|-------------------|
| `scheduler-sqlite-persistence` | `SqliteSchedulerDAOTest` | `SqliteSchedulerServiceIntegrationTest` |
| `scheduler-postgres-persistence` | `PostgresSchedulerDAOTest` | `PostgresSchedulerServiceIntegrationTest` |
| `scheduler-mysql-persistence` | `MySQLSchedulerDAOTest` | `MySQLSchedulerServiceIntegrationTest` |
| `conductor-scheduler-redis-persistence` | `RedisSchedulerDAOTest` | `RedisSchedulerServiceIntegrationTest` |

Postgres and MySQL tests use Testcontainers JDBC URL protocol (`jdbc:tc:postgresql:15-alpine:`, `jdbc:tc:mysql:8.0:`). Redis uses `GenericContainer("redis:7-alpine")`. SQLite uses in-memory (no container).

---

## Testing Strategy

- **No mock DAOs** in service tests — every service test runs against a real DAO backed by a real store.
- **Testcontainers** for Postgres, MySQL, and Redis — each test class starts its container once per class (not per test method).
- **SQLite in-memory** for the SQLite module — fast, no Docker required.
- **Abstract bases** in `testFixtures` ensure all backends cover identical scenarios.

## Architecture Constraints

- All new DAOs must implement the full `SchedulerDAO` interface.
- Auto-configuration uses `@ConditionalOnExpression` with dual gates: `db.type` AND `scheduler.enabled`.
- Redis DAO reuses existing `JedisProxy` — no new connection pool or client library.
- SQLite HikariCP pool size is capped at 1 to prevent in-memory DB isolation issues.
- Flyway migration history tables are per-backend to avoid conflicts in shared databases.
