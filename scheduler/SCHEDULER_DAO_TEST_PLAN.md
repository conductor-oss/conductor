# Scheduler Test Plan

## Overview

This document describes the test strategy for the `SchedulerDAO` interface, its three persistence
implementations (PostgreSQL, MySQL, SQLite), the `SchedulerService` layer, and the `SchedulerResource`
REST API.

The goals are:
1. Ensure behavioral consistency across all three backends
2. Verify correctness at the DAO boundary, not just the happy path
3. Document non-obvious behavioral contracts (e.g. what happens when `updateSchedule` is called
   after `setNextRunTimeInEpoch`)
4. Verify Spring auto-configuration conditions wire the right beans for each backend
5. Provide a regression harness that automatically covers any future persistence implementations

---

## Architecture: Shared Contract Tests

### Problem

Before this plan was implemented, each persistence module had its own copy of the same ~339-line
test class. Adding a test required updating three files. Missing one meant inconsistent coverage.

### Solution: `java-test-fixtures` + Abstract Base Classes

`conductor-scheduler` uses Gradle's `java-test-fixtures` plugin to publish shared abstract test
classes. Each persistence module's test class extends the relevant abstract class and provides only
Spring wiring. There are three abstract bases:

```
conductor-scheduler/
  src/
    main/java/              <- SchedulerDAO interface + models + config
    test/java/              <- SchedulerResourceHttpTest (15 tests, MockMvc, no DB)
    testFixtures/java/
      .../dao/AbstractSchedulerDAOTest                       (34 tests)
      .../service/AbstractSchedulerServiceIntegrationTest    (8 tests)
      .../config/AbstractSchedulerAutoConfigurationSmokeTest (5 tests)

conductor-scheduler-postgres-persistence/
  src/test/java/
    .../dao/PostgresSchedulerDAOTest
    .../service/PostgresSchedulerServiceIntegrationTest
    .../config/PostgresSchedulerAutoConfigurationSmokeTest

conductor-scheduler-mysql-persistence/
  src/test/java/
    .../dao/MySQLSchedulerDAOTest
    .../service/MySQLSchedulerServiceIntegrationTest
    .../config/MySQLSchedulerAutoConfigurationSmokeTest

conductor-scheduler-sqlite-persistence/
  src/test/java/
    .../dao/SqliteSchedulerDAOTest
    .../service/SqliteSchedulerServiceIntegrationTest
    .../config/SqliteSchedulerAutoConfigurationSmokeTest
```

Each persistence module declares:
```gradle
testImplementation testFixtures(project(':conductor-scheduler'))
```

**Adding a test:** write it once in the relevant abstract class — all three backends run it
automatically on the next build.

---

## Test Environments

| Module | Database | How provisioned | Docker required |
|--------|----------|-----------------|-----------------|
| `scheduler-postgres-persistence` | PostgreSQL 15 | Testcontainers `jdbc:tc:postgresql:15-alpine:` | Yes |
| `scheduler-mysql-persistence` | MySQL 8.0 | Testcontainers `jdbc:tc:mysql:8.0:` | Yes |
| `scheduler-sqlite-persistence` | SQLite (in-memory) | `jdbc:sqlite::memory:` | No |
| `conductor-scheduler` (HTTP tests) | None | MockMvc standalone | No |

SQLite uses `hikari.maximum-pool-size=1` because in-memory databases are connection-scoped in
SQLite. A single HikariCP connection is kept alive for the duration of the test suite.

All modules run Flyway migrations in `@TestConfiguration` with a dedicated history table
(`flyway_schema_history_scheduler`) to avoid conflicting with any main Conductor migration history
present in the same schema.

---

## Test Categories and Rationale

### 1. Schedule CRUD (11 tests) — `AbstractSchedulerDAOTest`

Basic create/read/update/delete coverage plus edge cases:

| Test | What it checks |
|------|---------------|
| `testSaveAndFindSchedule` | Basic insert + lookup; verifies name, cronExpression, zoneId, workflowRequest |
| `testFindScheduleByName_notFound_returnsNull` | Miss case returns null, not exception |
| `testUpdateSchedule_upserts` | Second save with same name updates, not inserts |
| `testGetAllSchedules` | List returns all rows |
| `testFindAllSchedulesByWorkflow` | Filter by workflow_name column |
| `testFindAllByNames` | Bulk lookup; missing names omitted from result |
| `testFindAllByNames_emptySet_returnsEmpty` | Empty input guard |
| `testFindAllByNames_nullSet_returnsEmpty` | Null input guard |
| `testDeleteSchedule_removesScheduleAndExecutions` | Cascade delete of execution rows |
| `testDeleteSchedule_cascadesMultipleExecutions` | Cascade works with >1 execution row |
| `testDeleteSchedule_nonExistent_doesNotThrow` | Idempotent delete — must not throw |

### 2. JSON Round-trip Fidelity (3 tests) — `AbstractSchedulerDAOTest`

Each DAO stores models as JSON blobs. This category verifies no field silently drops during
serialization/deserialization. The original tests checked only 4 fields; these cover the rest.

| Test | What it checks |
|------|---------------|
| `testScheduleJsonRoundTrip_allFields` | All `WorkflowSchedule` fields survive: `paused`, `pausedReason`, `scheduleStartTime`, `scheduleEndTime`, `runCatchupScheduleInstances`, `createTime`, `updatedTime`, `createdBy`, `updatedBy`, `description`, `nextRunTime` |
| `testScheduleExtensionFields_roundTrip` | `@JsonAnySetter`/`@JsonAnyGetter` fields survive (Orkes compatibility mechanism — unknown fields like `orgId` must not be dropped) |
| `testExecutionJsonRoundTrip_allFields` | All `WorkflowScheduleExecution` fields survive: `workflowId`, `workflowName`, `reason`, `stackTrace`, `startWorkflowRequest` |

### 3. Execution Tracking (10 tests) — `AbstractSchedulerDAOTest`

The `POLLED -> EXECUTED/FAILED` lifecycle plus state-dependent queries:

| Test | What it checks |
|------|---------------|
| `testSaveAndReadExecutionRecord` | Basic insert + lookup |
| `testSaveExecutionRecord_idempotent` | Saving the same record twice must not create duplicate rows (upsert semantics) |
| `testUpdateExecutionRecord_transitionToExecuted` | State + workflowId persist after transition |
| `testUpdateExecutionRecord_transitionToFailed` | FAILED state with reason and stackTrace persists |
| `testRemoveExecutionRecord` | Delete leaves no trace |
| `testGetPendingExecutionRecordIds` | POLLED records appear; EXECUTED records do not |
| `testGetPendingExecutionRecordIds_afterTransition` | After POLLED->EXECUTED update, the ID drops from the pending list |
| `testGetExecutionRecords_orderedByTimeDesc` | Returned newest-first; limit is honored |
| `testGetExecutionRecords_reverseInsertionOrder` | Records inserted in reverse time order are still returned newest-first (verifies `ORDER BY` in the query, not insertion order) |
| `testGetExecutionRecords_limitOne` | Limit=1 returns exactly the most recent record |

### 4. Next-run Time Management (3 tests) — `AbstractSchedulerDAOTest`

The `next_run_time` column is written by two separate operations; their interaction needs explicit
documentation:

| Test | What it checks |
|------|---------------|
| `testSetAndGetNextRunTime` | `setNextRunTimeInEpoch` + `getNextRunTimeInEpoch` round-trip |
| `testGetNextRunTime_notSet_returnsMinusOne` | Returns -1 when no value is set (null in DB) |
| `testUpdateSchedule_resetsNextRunTime` | **Behavioral contract:** calling `updateSchedule` with `schedule.nextRunTime == null` writes null to the `next_run_time` column, resetting the cached value. Callers that edit a schedule and want to preserve the cached time must carry the existing value forward. |

### 5. Volume (1 test) — `AbstractSchedulerDAOTest`

| Test | What it checks |
|------|---------------|
| `testVolume_getAllSchedules_largeCount` | Create 100 schedules, `getAllSchedules` returns all 100. Guards against implicit row limits, OOM, or query pagination bugs. |

### 6. Concurrency (1 test) — `AbstractSchedulerDAOTest`

| Test | What it checks |
|------|---------------|
| `testConcurrentUpserts_sameSchedule` | 10 threads simultaneously call `updateSchedule` with the same schedule name. After all complete, exactly 1 row must exist. Exercises `ON CONFLICT ... DO UPDATE` (Postgres, SQLite) and `ON DUPLICATE KEY UPDATE` (MySQL) under concurrent load. SQLite serializes through HikariCP pool-size=1 — the test validates correctness, not parallelism. |

### 7. Case Sensitivity + Error Conditions (5 tests) — `AbstractSchedulerDAOTest`

| Test | What it checks |
|------|---------------|
| `testFindAllSchedules_caseSensitive` | `findAllSchedules(workflowName)` is case-sensitive. Requires `COLLATE utf8mb4_bin` on MySQL's `workflow_name` column to match Postgres/SQLite behavior. |
| `testFindAllByNames_largeSet` | 50 existing + 50 non-existent names in a single query; result contains exactly the 50 existing rows. Guards against IN-clause limits and off-by-one errors. |
| `testGetNextRunTime_nonExistentSchedule_returnsMinusOne` | Miss case returns -1, not exception |
| `testSetNextRunTime_nonExistentSchedule_doesNotThrow` | UPDATE on a non-existent schedule silently no-ops; verifies no phantom row is created |
| `testGetExecutionRecords_nonExistentSchedule_returnsEmpty` | Empty list returned, not exception |

### 8. Service Layer Integration (8 tests) — `AbstractSchedulerServiceIntegrationTest`

Wires a real `SchedulerDAO` (from the concrete subclass) with a real `SchedulerService` and a
mocked `WorkflowService`. No background scheduler thread starts. Tests service logic against actual
SQL:

| Test | What it checks |
|------|---------------|
| `testPruneExecution_withRealDAO_removesOldestRecords` | `pruneExecutions` removes oldest records, retaining only the configured max |
| `testStalePollRecord_withRealDAO_transitionsToFailed` | A POLLED record older than the stale threshold is transitioned to FAILED with a reason |
| `testPollCycle_advancesNextRunPointer` | After a poll cycle, `getNextRunTimeInEpoch` returns a future epoch |
| `testSaveSchedule_createTimePreservedOnUpdate` | `createTime` is set on first save and not overwritten on subsequent updates |
| `testSaveSchedule_nextRunTimeStoredAndRetrievable` | After saving a schedule, `getNextRunTimeInEpoch` returns the value the service computed |
| `testGetNextExecutionTimes_withEndTimeBound` | Returns no times after `scheduleEndTime` |
| `testGetNextExecutionTimes_withStartTimeBound` | Returns no times before `scheduleStartTime` |
| `testConcurrentPoll_documentsDoubleFireRisk` | Documents that concurrent polls on the same schedule can both claim the same execution slot (no DB-level lock); callers should be aware of this behavior |

### 9. HTTP Layer (15 tests) — `SchedulerResourceHttpTest`

Uses `MockMvcBuilders.standaloneSetup` with a local exception handler. No database needed — all
`SchedulerService` calls are mocked. Lives in `conductor-scheduler/src/test` (runs once, not
per-backend):

| Endpoint | Tests |
|----------|-------|
| `POST /scheduler/schedules` | 201 on success, 400 on invalid input |
| `GET /scheduler/schedules` | 200 with schedule list |
| `GET /scheduler/schedules/{name}` | 200 on found, 404 on missing |
| `DELETE /scheduler/schedules/{name}` | 204 on success, 404 on missing |
| `PUT /scheduler/schedules/{name}/pause` | 204 on success, 404 on missing |
| `PUT /scheduler/schedules/{name}/resume` | 204 on success, 404 on missing |
| `GET /scheduler/schedules/{name}/executions` | 200 with execution list |
| `GET /scheduler/schedules/{name}/nextExecutionTimes` | 200 on found, 404 on missing |

### 10. Auto-configuration Smoke Tests (5 tests) — `AbstractSchedulerAutoConfigurationSmokeTest`

Uses Spring Boot's `ApplicationContextRunner` to verify that `@ConditionalOnExpression` guards on
each persistence module's `@AutoConfiguration` class work correctly. These tests catch bugs the DAO
and service integration tests cannot, because those tests bypass auto-configuration and wire beans
manually:

| Test | What it checks |
|------|---------------|
| `testSchedulerDAO_registeredWhenBothPropertiesSet` | Both `conductor.db.type=<backend>` and `conductor.scheduler.enabled=true` -> `SchedulerDAO` bean of the expected concrete type is registered |
| `testNoBeansRegistered_whenSchedulerEnabledAbsent` | Missing `conductor.scheduler.enabled` -> no `SchedulerDAO` |
| `testNoBeansRegistered_whenSchedulerEnabledFalse` | `conductor.scheduler.enabled=false` -> no `SchedulerDAO` |
| `testNoSchedulerDAO_whenDbTypeAbsent` | Missing `conductor.db.type` -> no `SchedulerDAO` even if scheduler is enabled |
| `testNoSchedulerDAO_whenDbTypeIsWrongBackend` | Wrong `conductor.db.type` (e.g. `postgres` against the SQLite config) -> no `SchedulerDAO` |

Bugs caught exclusively by these tests:
- Typo in the condition string (e.g. `'postgresql'` instead of `'postgres'`)
- Missing or wrong classname in `META-INF/spring/...AutoConfiguration.imports`

---

## Running the Tests

```bash
# All three backends (DAO + service integration + smoke):
DOCKER_API_VERSION=1.44 ./gradlew \
    :conductor-scheduler-postgres-persistence:test \
    :conductor-scheduler-mysql-persistence:test \
    :conductor-scheduler-sqlite-persistence:test

# HTTP layer tests (no Docker):
./gradlew :conductor-scheduler:test

# SQLite only — fastest full run, no Docker needed:
./gradlew :conductor-scheduler:test :conductor-scheduler-sqlite-persistence:test
```

> **macOS note:** Testcontainers (used for Postgres and MySQL) requires Docker Desktop to be
> running. The test builds set `environment 'DOCKER_API_VERSION', '1.44'` in their `test` blocks
> to avoid a Testcontainers/docker-java version mismatch on Docker Desktop.

---

## Results

| Backend | DAO | Service | Smoke | Total | Passed | Failed |
|---------|----:|--------:|------:|------:|-------:|-------:|
| PostgreSQL | 34 | 8 | 5 | 47 | 47 | 0 |
| MySQL | 34 | 8 | 5 | 47 | 47 | 0 |
| SQLite | 34 | 8 | 5 | 47 | 47 | 0 |
| HTTP (shared) | — | — | — | 85 | 85 | 0 |
| **Total** | | | | **226** | **226** | **0** |

---

## Adding New Tests

### DAO contract test (runs on all three backends automatically)
1. Open `scheduler/src/testFixtures/java/.../dao/AbstractSchedulerDAOTest.java`
2. Add a `@Test` method
3. Run any one of the three persistence modules to verify

### Service integration test (runs on all three backends automatically)
1. Open `scheduler/src/testFixtures/java/.../service/AbstractSchedulerServiceIntegrationTest.java`
2. Add a `@Test` method

### HTTP layer test (runs once, no database)
1. Open `scheduler/src/test/java/.../rest/SchedulerResourceHttpTest.java`
2. Add a `@Test` method using the existing `mockMvc` and `mockService` fields

### Auto-configuration smoke test (runs on all three backends automatically)
1. Open `scheduler/src/testFixtures/java/.../config/AbstractSchedulerAutoConfigurationSmokeTest.java`
2. Add a `@Test` method using `baseRunner()` and `withPropertyValues(...)`

If a test needs to be skipped for a specific backend (e.g. a SQL feature not available in SQLite),
the concrete test class can override and annotate with `@Ignore` or use `assumeTrue`.

---

## Relationship Between Test Layers

```
Layer                      | Scope                              | DB needed
---------------------------|------------------------------------|----------
DAO contract tests         | SQL correctness, data fidelity     | Yes (real DB)
Service integration tests  | Service logic against real storage | Yes (real DB)
HTTP tests                 | REST status codes, request mapping | No (MockMvc)
Auto-config smoke tests    | Spring wiring conditions           | Only for positive path
```

Each layer has blind spots the others fill:
- **DAO tests** bypass auto-config (can't catch condition string typos)
- **Service tests** bypass HTTP (can't catch routing or marshalling bugs)
- **HTTP tests** use mocked service (can't catch SQL bugs)
- **Smoke tests** use a fresh context (can't catch SQL bugs, but verify the wiring happens at all)
