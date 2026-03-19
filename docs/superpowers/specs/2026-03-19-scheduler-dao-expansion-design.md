# Scheduler DAO Expansion Design

**Date:** 2026-03-19
**Status:** Under Review

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
    │   └── resources/
    │       ├── db/migration_scheduler_sqlite/
    │       │   └── V1__scheduler_tables.sql
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/.../scheduler/sqlite/
            ├── dao/SqliteSchedulerDAOTest.java
            ├── service/SqliteSchedulerServiceIntegrationTest.java
            └── config/SqliteSchedulerAutoConfigurationSmokeTest.java
```

### DAO implementation

- `SqliteSchedulerDAO` uses `JdbcTemplate` + `TransactionTemplate(DataSourceTransactionManager)` — same pattern as Postgres/MySQL DAOs.
- `findAllByNames(Set<String> names)` builds `IN (?, ?, ...)` placeholders manually (SQLite does not have array binding).
- `getExecutionRecords` orders by `execution_time DESC` — no `NULLS LAST` (SQLite sorts NULLs last by default for DESC).
- `saveExecutionRecord` uses `INSERT OR REPLACE INTO` (SQLite upsert syntax).
- `updateSchedule` uses `INSERT OR REPLACE INTO` (SQLite upsert syntax).

### Schema

Clean V1 schema — `execution_time` column included from the start; no `workflow_scheduled_executions` table. Secondary indexes are intentionally omitted for the SQLite module since it targets dev/test workloads where query plan overhead is negligible.

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

`SqliteSchedulerConfiguration` matches the existing Postgres/MySQL conditional pattern exactly:
```java
@ConditionalOnExpression(
    "'${conductor.db.type:}' == 'sqlite' && '${conductor.scheduler.enabled:false}' == 'true'"
)
```

Note: the default for `conductor.scheduler.enabled` is `false` — the scheduler does not activate unless explicitly opted in via config. This matches the existing Postgres and MySQL configuration pattern.

Uses HikariCP with `maximumPoolSize=1` (SQLite in-memory databases are connection-scoped; a single connection guarantees all operations share the same in-memory database).

Flyway uses a dedicated history table: `flyway_schema_history_scheduler_sqlite`.

The `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file must contain:
```
org.conductoross.conductor.scheduler.sqlite.config.SqliteSchedulerConfiguration
```

### Module registration

`settings.gradle` must include:
```
include ':conductor-scheduler-sqlite-persistence'
```

### Tests

- `SqliteSchedulerDAOTest` extends `AbstractSchedulerDAOTest` from `scheduler` testFixtures — no Docker, SQLite in-memory.
- `SqliteSchedulerServiceIntegrationTest` extends `AbstractSchedulerServiceIntegrationTest` from `scheduler` testFixtures — no Docker.
- `SqliteSchedulerAutoConfigurationSmokeTest` extends `AbstractSchedulerAutoConfigurationSmokeTest` from `scheduler` testFixtures.
- Tests are fast; no Testcontainers dependency needed for SQLite.

---

## Section 2: Redis Module (`conductor-scheduler-redis-persistence`)

### Module structure

```
conductor-scheduler-redis-persistence/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/.../scheduler/redis/
    │   │   ├── dao/RedisSchedulerDAO.java
    │   │   └── config/RedisSchedulerConfiguration.java
    │   └── resources/
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/.../scheduler/redis/
            ├── dao/RedisSchedulerDAOTest.java
            ├── service/RedisSchedulerServiceIntegrationTest.java
            └── config/RedisSchedulerAutoConfigurationSmokeTest.java
```

### Build dependencies

`build.gradle` dependencies (in addition to standard compile/runtime dependencies):

```groovy
dependencies {
    implementation project(':conductor-core')
    implementation project(':conductor-redis-persistence')  // provides JedisProxy

    testFixtures(project(':conductor-scheduler'))           // abstract test bases
    testImplementation "org.testcontainers:testcontainers:${revTestContainer}"
}
```

No JDBC, no Flyway, no SQL driver dependencies — Redis module uses no relational database.

### Module registration

`settings.gradle` must include:
```
include ':conductor-scheduler-redis-persistence'
```

### Dependencies

- Reuses `conductor-redis-persistence` for the `JedisProxy` bean (which already handles standalone, cluster, and sentinel Redis).
- No additional Redis client dependencies needed.

### JedisProxy extensions required

The existing `JedisProxy` exposes `hget`, `hset`, `hgetAll`, `hvals`, `zadd`, `zrem`, `zrangeByScore`, `smembers`, `sadd`, `srem`, `get`, `set`, `del`. Two additional methods must be added to `JedisProxy` to support the scheduler DAO:

```java
// For getNextRunTimeInEpoch — retrieve score of a single member
public Double zscore(String key, String member) {
    return jedisCommands.zscore(key, member);
}

// For getExecutionRecords — retrieve execution IDs by score range, newest first
public List<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
    return jedisCommands.zrevrangeByScore(key, max, min, offset, count);
}
```

No `hmget` or `mget` batch methods are needed — the Redis DAO uses individual `hget`/`get` calls in a loop. For scheduler scale (typically tens to low hundreds of schedules), this N+1 pattern is acceptable.

### Key design

All keys are prefixed with `conductor_scheduler:` (underscore separator is intentional — colons are used as secondary separators within key names, avoiding confusion with Spring property path dots).

| Structure | Key | Purpose |
|-----------|-----|---------|
| HASH | `conductor_scheduler:schedules` | All schedule objects (field = schedule name, value = JSON) |
| SET | `conductor_scheduler:by_workflow:{wfName}` | Schedule names for a given workflow |
| ZSET | `conductor_scheduler:next_run` | Schedule names scored by next run time (epoch ms) — polling hot path |
| STRING | `conductor_scheduler:execution:{executionId}` | Individual execution record JSON |
| ZSET | `conductor_scheduler:exec_by_sched:{scheduleName}` | Execution IDs scored by execution_time — enables `getExecutionRecords(name, limit)` with ordering |
| SET | `conductor_scheduler:pending_execs` | Execution IDs in POLLED state |

### DAO operations

- `updateSchedule(schedule)`:
  - `HSET schedules {name} {json}`
  - Remove from old `by_workflow` SET if workflow name changed (read old JSON first)
  - `SADD by_workflow:{workflowName} {name}`
  - `ZADD next_run {nextRunTime} {name}` (score = `schedule.getNextRunTime()`, 0 if null)

- `findScheduleByName(name)`:
  - `HGET schedules {name}` → deserialize

- `getAllSchedules()`:
  - `HVALS schedules` → deserialize all

- `findAllSchedules(workflowName)`:
  - `SMEMBERS by_workflow:{workflowName}` → list of names
  - `HGET schedules {name}` for each name (loop)

- `findAllByNames(names)`:
  - `HGET schedules {name}` for each name (loop)

- `deleteWorkflowSchedule(name)`:
  - `HDEL schedules {name}`
  - `SREM by_workflow:{workflowName} {name}` (read JSON first to get workflow name)
  - `ZREM next_run {name}`
  - `SMEMBERS exec_by_sched:{name}` style: use `zrevrangeByScore exec_by_sched:{name} +inf -inf` to get all execution IDs
  - `DEL execution:{id}` for each, `SREM pending_execs {id}` for each
  - `DEL exec_by_sched:{name}`

- `setNextRunTimeInEpoch(name, epochMillis)`:
  - `ZADD next_run {epochMillis} {name}`
  - Read schedule JSON, update `nextRunTime` field, re-serialize, `HSET schedules {name} {json}` (keeps JSON consistent)

- `getNextRunTimeInEpoch(name)`:
  - `ZSCORE next_run {name}` → parse as long, return -1L if null

- `saveExecutionRecord(execution)`:
  - `SET execution:{id} {json}`
  - `ZADD exec_by_sched:{scheduleName} {executionTime} {id}` (score = `execution.getExecutionTime()`, 0 if null)
  - If state == POLLED: `SADD pending_execs {id}`
  - Otherwise: `SREM pending_execs {id}`

- `getExecutionRecords(name, limit)`:
  - `ZREVRANGEBYSCORE exec_by_sched:{name} +inf -inf LIMIT 0 {limit}` → list of execution IDs
  - `GET execution:{id}` for each → deserialize

- `getPendingExecutionRecords()`:
  - `SMEMBERS pending_execs` → set of execution IDs
  - `GET execution:{id}` for each → deserialize

- `readExecutionRecord(id)`:
  - `GET execution:{id}` → deserialize

- `removeExecutionRecord(id)`:
  - `GET execution:{id}` → deserialize to get `scheduleName`
  - `DEL execution:{id}`
  - `SREM pending_execs {id}`
  - `ZREM exec_by_sched:{scheduleName} {id}` (requires scheduleName from deserialized record above)

- `getPendingExecutionRecordIds()`:
  - `SMEMBERS pending_execs`

### Auto-configuration

`RedisSchedulerConfiguration` matches the existing `AnyRedisCondition` pattern from the `conductor-redis-persistence` module:
```java
@ConditionalOnExpression(
    "('${conductor.db.type:}' == 'redis_standalone' " +
    "|| '${conductor.db.type:}' == 'redis_cluster' " +
    "|| '${conductor.db.type:}' == 'redis_sentinel') " +
    "&& '${conductor.scheduler.enabled:false}' == 'true'"
)
```

Note: `scheduler.enabled` defaults to `false` — consistent with all other scheduler persistence modules.

Injects the existing `JedisProxy` bean — no new connection infrastructure.

The `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file must contain:
```
org.conductoross.conductor.scheduler.redis.config.RedisSchedulerConfiguration
```

### Tests

- `RedisSchedulerDAOTest` extends `AbstractSchedulerDAOTest` — uses Testcontainers `GenericContainer("redis:7-alpine")` on port 6379.
- `RedisSchedulerServiceIntegrationTest` extends `AbstractSchedulerServiceIntegrationTest`.
- `RedisSchedulerAutoConfigurationSmokeTest` extends `AbstractSchedulerAutoConfigurationSmokeTest`.
- Container lifecycle is managed via a `static GenericContainer` field annotated with `@ClassRule` (JUnit 4). The container is started once per test class.

---

## Section 3: Real Service Tests (Replacing Mocks)

### What is deleted

The following mock-based test classes are deleted:
- `scheduler/src/test/java/.../service/SchedulerServiceTest.java`
- `scheduler/src/test/java/.../service/SchedulerServicePhase2Test.java`
- `scheduler/src/test/java/.../service/SchedulerServiceStressTest.java`

### What replaces them

Two abstract base classes in `scheduler`'s `testFixtures` source set (already exist, extend their coverage where needed):

#### `AbstractSchedulerDAOTest`

Contract tests for the `SchedulerDAO` interface. Each persistence module subclasses this and provides a concrete DAO instance. Tests cover:
- CRUD operations for schedules and executions
- `findAllByNames` with empty set, single name, multiple names
- `getExecutionRecords` respects `limit` and returns newest first
- `getPendingExecutionRecords` returns only POLLED records
- `deleteWorkflowSchedule` removes execution records atomically
- `setNextRunTimeInEpoch` / `getNextRunTimeInEpoch` round-trip
- Archival/cleanup behavior

**Prerequisite refactoring (must complete before implementing Redis module):** The current base classes call `dataSource().getConnection()` directly in `setUp()`. This must be refactored to a `protected void truncateStore() throws Exception` hook before the Redis module can subclass them:

```java
// New default implementation (JDBC path — SQL subclasses inherit as-is):
protected void truncateStore() throws Exception {
    try (Connection conn = dataSource().getConnection()) {
        conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
        conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
    }
}
// setUp() calls truncateStore() instead of calling dataSource() directly.
```

SQL subclasses require no changes. Redis subclass overrides `truncateStore()` with a FLUSHDB call and overrides `dataSource()` to throw `UnsupportedOperationException`. This refactoring applies to both `AbstractSchedulerDAOTest` and `AbstractSchedulerServiceIntegrationTest`.

#### `AbstractSchedulerServiceIntegrationTest`

Behavior tests for `SchedulerService` running against a real DAO. Same teardown hook pattern as `AbstractSchedulerDAOTest`. Covers all scenarios previously in the mock tests plus DST and catchup edge cases:
- `saveSchedule` sets timestamps and nextRunTime
- Invalid cron / missing name / missing request all throw
- `pauseSchedule` / `resumeSchedule` state transitions
- `deleteSchedule` delegates to DAO
- `computeNextRunTime` with start/end time bounds
- `pollAndExecuteSchedules` fires due schedules, skips paused, records failure
- Catchup enabled vs disabled behavior
- DST spring-forward and fall-back edge cases (future work — not yet in current testFixtures)
- Slow polling (poll interval > cron interval) (future work)

### Concrete subclasses per module

| Module | DAO test | Service test | Smoke test |
|--------|----------|-------------|------------|
| `scheduler-sqlite-persistence` | `SqliteSchedulerDAOTest` | `SqliteSchedulerServiceIntegrationTest` | `SqliteSchedulerAutoConfigurationSmokeTest` |
| `scheduler-postgres-persistence` | `PostgresSchedulerDAOTest` | `PostgresSchedulerServiceIntegrationTest` | `PostgresSchedulerAutoConfigurationSmokeTest` |
| `scheduler-mysql-persistence` | `MySQLSchedulerDAOTest` | `MySQLSchedulerServiceIntegrationTest` | `MySQLSchedulerAutoConfigurationSmokeTest` |
| `conductor-scheduler-redis-persistence` | `RedisSchedulerDAOTest` | `RedisSchedulerServiceIntegrationTest` | `RedisSchedulerAutoConfigurationSmokeTest` |

Postgres and MySQL tests use Testcontainers JDBC URL protocol (`jdbc:tc:postgresql:15-alpine:`, `jdbc:tc:mysql:8.0:`). Redis uses `GenericContainer("redis:7-alpine")` with `@ClassRule`. SQLite uses in-memory (no container).

---

## Testing Strategy

- **No mock DAOs** in service tests — every service test runs against a real DAO backed by a real store.
- **Testcontainers** for Postgres, MySQL, and Redis — each test class starts its container once per class.
- **SQLite in-memory** for the SQLite module — fast, no Docker required.
- **Abstract bases** in `testFixtures` ensure all backends cover identical scenarios.
- **Teardown hook** (`truncateStore()`) allows both JDBC and Redis subclasses to reset state between tests using their native mechanisms.

## Architecture Constraints

- All new DAOs must implement the full `SchedulerDAO` interface.
- Auto-configuration uses `@ConditionalOnExpression` with dual gates: `db.type` AND `'${conductor.scheduler.enabled:false}' == 'true'` — the scheduler does not activate by default.
- Redis DAO reuses existing `JedisProxy` — no new connection pool or client library. Two methods (`zscore`, `zrevrangeByScore`) must be added to `JedisProxy`.
- SQLite HikariCP pool size is capped at 1 to prevent in-memory DB isolation issues.
- Flyway migration history tables are per-backend to avoid conflicts in shared databases.
- Both new modules must register their auto-configuration class in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Both new modules must be added to `settings.gradle`.
