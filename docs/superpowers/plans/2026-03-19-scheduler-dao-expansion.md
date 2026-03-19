# Scheduler DAO Expansion Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SQLite and Redis persistence backends for the scheduler module, backed by real Testcontainers integration tests, replacing all mock-based service tests.

**Architecture:** Three small refactors to the abstract test bases enable non-JDBC backends. The SQLite module follows the existing Postgres/MySQL JDBC pattern (JdbcTemplate + TransactionTemplate + Flyway). The Redis module reuses the existing JedisProxy with two new methods, storing schedules as a Redis HASH and execution records as STRING keys with ZSET/SET indexes.

**Tech Stack:** JUnit 4, Mockito, Testcontainers (`redis:7-alpine`), SQLite JDBC (`org.xerial:sqlite-jdbc:3.49.0.0`), Flyway Core, JedisProxy (from `conductor-redis-persistence`), Spring Boot AutoConfiguration, Gradle `java-test-fixtures` plugin.

---

## File Structure

### Modified
| File | Change |
|------|--------|
| `scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/dao/AbstractSchedulerDAOTest.java` | Add `truncateStore()` hook; keep `dataSource()` abstract |
| `scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/service/AbstractSchedulerServiceIntegrationTest.java` | Same `truncateStore()` hook |
| `scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/config/AbstractSchedulerAutoConfigurationSmokeTest.java` | `baseRunner()`: `private` → `protected` |
| `redis-persistence/src/main/java/com/netflix/conductor/redis/jedis/JedisProxy.java` | Add `zscore()` and `zrevrangeByScore()` |
| `settings.gradle` | Add 2 `include` lines |

### Created (SQLite — directory: `scheduler-sqlite-persistence/`)
| File | Purpose |
|------|---------|
| `build.gradle` | Module build config |
| `src/main/java/org/conductoross/conductor/scheduler/sqlite/dao/SqliteSchedulerDAO.java` | Full SchedulerDAO impl |
| `src/main/java/org/conductoross/conductor/scheduler/sqlite/config/SqliteSchedulerConfiguration.java` | Auto-configuration |
| `src/main/resources/db/migration_scheduler_sqlite/V1__scheduler_tables.sql` | Clean schema |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | SPI registration |
| `src/test/java/org/conductoross/conductor/scheduler/sqlite/dao/SqliteSchedulerDAOTest.java` | Contract tests |
| `src/test/java/org/conductoross/conductor/scheduler/sqlite/service/SqliteSchedulerServiceIntegrationTest.java` | Service tests |
| `src/test/java/org/conductoross/conductor/scheduler/sqlite/config/SqliteSchedulerAutoConfigurationSmokeTest.java` | Smoke tests |

### Created (Redis — directory: `scheduler-redis-persistence/`)
| File | Purpose |
|------|---------|
| `build.gradle` | Module build config |
| `src/main/java/org/conductoross/conductor/scheduler/redis/dao/RedisSchedulerDAO.java` | Full SchedulerDAO impl |
| `src/main/java/org/conductoross/conductor/scheduler/redis/config/RedisSchedulerConfiguration.java` | Auto-configuration |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | SPI registration |
| `src/test/java/org/conductoross/conductor/scheduler/redis/dao/RedisSchedulerDAOTest.java` | Contract tests (no Spring) |
| `src/test/java/org/conductoross/conductor/scheduler/redis/service/RedisSchedulerServiceIntegrationTest.java` | Service tests (no Spring) |
| `src/test/java/org/conductoross/conductor/scheduler/redis/config/RedisSchedulerAutoConfigurationSmokeTest.java` | Smoke tests |

### Deleted
| File |
|------|
| `scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServiceTest.java` |
| `scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServicePhase2Test.java` |
| `scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServiceStressTest.java` |

---

## Chunk 1: Prerequisites

Refactor the three abstract test bases to support non-JDBC backends, register the two new modules in `settings.gradle`.

### Task 1: Add `truncateStore()` hook to `AbstractSchedulerDAOTest`

**Files:**
- Modify: `scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/dao/AbstractSchedulerDAOTest.java`

The `setUp()` method currently calls `dataSource().getConnection()` directly. Extract that into a `truncateStore()` method so Redis subclasses can override it with a FLUSHDB call instead.

- [ ] **Step 1: Edit `AbstractSchedulerDAOTest` — add `truncateStore()` and update `setUp()`**

Replace lines 72-78 (the `setUp()` method only — do not change anything else in the file):

```java
    @Before
    public final void setUp() throws Exception {
        truncateStore();
    }

    /**
     * Clears all scheduler data between tests. SQL subclasses rely on the default JDBC
     * implementation. Non-JDBC subclasses (e.g. Redis) override this method directly and must
     * also override {@link #dataSource()} to throw {@link UnsupportedOperationException}.
     */
    protected void truncateStore() throws Exception {
        try (Connection conn = dataSource().getConnection()) {
            conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
            conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
        }
    }
```

- [ ] **Step 2: Verify existing Postgres DAO tests still compile and pass**

```bash
./gradlew :conductor-scheduler-postgres-persistence:test --tests "*.PostgresSchedulerDAOTest" --info
```

Expected: all tests GREEN (no change in behavior — existing Postgres subclass still overrides `dataSource()`, and `truncateStore()` calls it exactly as before).

---

### Task 2: Add `truncateStore()` hook to `AbstractSchedulerServiceIntegrationTest`

**Files:**
- Modify: `scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/service/AbstractSchedulerServiceIntegrationTest.java`

- [ ] **Step 1: Read current `setUpService()` method to locate the exact lines**

```bash
grep -n "DELETE FROM\|setUpService\|dataSource\|truncate" \
  scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/service/AbstractSchedulerServiceIntegrationTest.java
```

- [ ] **Step 2: Replace the inline JDBC teardown in `setUpService()` with a `truncateStore()` call**

The `setUpService()` method currently does:
```java
    @Before
    public final void setUpService() throws Exception {
        try (Connection conn = dataSource().getConnection()) {
            conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
            conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
        }
        workflowService = mock(WorkflowService.class);
        ...
    }
```

Change the teardown portion to:
```java
    @Before
    public final void setUpService() throws Exception {
        truncateStore();
        workflowService = mock(WorkflowService.class);
        ...
    }

    protected void truncateStore() throws Exception {
        try (Connection conn = dataSource().getConnection()) {
            conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
            conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
        }
    }
```

- [ ] **Step 3: Verify existing Postgres service integration tests still pass**

```bash
./gradlew :conductor-scheduler-postgres-persistence:test \
  --tests "*.PostgresSchedulerServiceIntegrationTest" --info
```

Expected: GREEN.

---

### Task 3: Make `baseRunner()` protected in `AbstractSchedulerAutoConfigurationSmokeTest`

**Files:**
- Modify: `scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/config/AbstractSchedulerAutoConfigurationSmokeTest.java`

This is a one-line visibility change so the Redis smoke test can override `baseRunner()` to configure Redis instead of JDBC.

- [ ] **Step 1: Change `private` to `protected` on `baseRunner()`**

In `AbstractSchedulerAutoConfigurationSmokeTest.java`, line ~93, change:
```java
    private ApplicationContextRunner baseRunner() {
```
to:
```java
    protected ApplicationContextRunner baseRunner() {
```

- [ ] **Step 2: Verify existing smoke tests still pass**

```bash
./gradlew :conductor-scheduler-postgres-persistence:test \
  --tests "*.PostgresSchedulerAutoConfigurationSmokeTest" \
  :conductor-scheduler-mysql-persistence:test \
  --tests "*.MySQLSchedulerAutoConfigurationSmokeTest" --info
```

Expected: GREEN (zero behavioral change — the method visibility change only adds access, doesn't alter execution).

---

### Task 4: Register new modules in `settings.gradle`

**Files:**
- Modify: `settings.gradle`

- [ ] **Step 1: Add two `include` lines after the existing scheduler entries**

Find these lines in `settings.gradle`:
```groovy
include 'scheduler'
include 'scheduler-postgres-persistence'
include 'scheduler-mysql-persistence'
```

Add immediately after:
```groovy
include 'scheduler-sqlite-persistence'
include 'scheduler-redis-persistence'
```

Note: The line `rootProject.children.each {it.name="conductor-${it.name}"}` (further down in `settings.gradle`) auto-prefixes all module names with `conductor-`, so these directories on disk are named without the prefix.

- [ ] **Step 2: Verify Gradle can resolve the new modules (directories don't exist yet — expect an error about missing `build.gradle`, not about unknown module)**

```bash
./gradlew projects 2>&1 | grep "scheduler"
```

Expected output includes `conductor-scheduler-sqlite-persistence` and `conductor-scheduler-redis-persistence` in the project tree (even if their `build.gradle` doesn't exist yet — Gradle lists them).

---

### Task 5: Commit prerequisites

- [ ] **Commit**

```bash
git add \
  scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/dao/AbstractSchedulerDAOTest.java \
  scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/service/AbstractSchedulerServiceIntegrationTest.java \
  scheduler/src/testFixtures/java/org/conductoross/conductor/scheduler/config/AbstractSchedulerAutoConfigurationSmokeTest.java \
  settings.gradle
git commit -m "refactor: add truncateStore() hook and protected baseRunner() for non-JDBC test backends"
```

---

## Chunk 2: SQLite Module

### Task 6: Create SQLite module build file and schema

**Files:**
- Create: `scheduler-sqlite-persistence/build.gradle`
- Create: `scheduler-sqlite-persistence/src/main/resources/db/migration_scheduler_sqlite/V1__scheduler_tables.sql`
- Create: `scheduler-sqlite-persistence/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Create `scheduler-sqlite-persistence/build.gradle`**

```groovy
dependencies {
    implementation project(':conductor-scheduler')
    implementation project(':conductor-common')
    implementation project(':conductor-core')

    compileOnly 'org.springframework.boot:spring-boot-starter'

    implementation "com.fasterxml.jackson.core:jackson-databind"
    implementation "com.fasterxml.jackson.core:jackson-core"
    implementation "org.apache.commons:commons-lang3"
    implementation 'org.xerial:sqlite-jdbc:3.49.0.0'
    implementation "org.springframework.boot:spring-boot-starter-jdbc"
    implementation "org.flywaydb:flyway-core:${revFlyway}"

    testImplementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation testFixtures(project(':conductor-scheduler'))
}

test {
    maxParallelForks = 1
}
```

Note: No Flyway dialect JAR needed for SQLite — `flyway-core` supports it natively.

- [ ] **Step 2: Create V1 schema migration**

Create `scheduler-sqlite-persistence/src/main/resources/db/migration_scheduler_sqlite/V1__scheduler_tables.sql`:

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

Note: No secondary indexes on the `scheduler` table — SQLite is dev/test scope, full scans are fine.
Note: `execution_time` is `INTEGER` not `BIGINT` — SQLite maps all integer types to INTEGER internally.
Note: `state TEXT` not `state TEXT NOT NULL` — allows saving a record before state is set (POLLED flow).

- [ ] **Step 3: Create SPI registration file**

Create `scheduler-sqlite-persistence/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
org.conductoross.conductor.scheduler.sqlite.config.SqliteSchedulerConfiguration
```

- [ ] **Step 4: Verify the schema file parses (quick sanity check)**

```bash
sqlite3 :memory: < scheduler-sqlite-persistence/src/main/resources/db/migration_scheduler_sqlite/V1__scheduler_tables.sql && echo "Schema OK"
```

Expected: `Schema OK` (no SQL errors).

---

### Task 7: Implement `SqliteSchedulerConfiguration`

**Files:**
- Create: `scheduler-sqlite-persistence/src/main/java/org/conductoross/conductor/scheduler/sqlite/config/SqliteSchedulerConfiguration.java`

- [ ] **Step 1: Write the configuration class**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.conductoross.conductor.scheduler.sqlite.config;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerDAO;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Auto-configures the SQLite-backed {@link SchedulerDAO}.
 *
 * <p>Active when {@code conductor.db.type=sqlite} AND {@code conductor.scheduler.enabled=true}.
 * The DataSource is expected to be a single-connection HikariCP pool ({@code maximumPoolSize=1})
 * to prevent in-memory SQLite database isolation issues.
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${conductor.db.type:}' == 'sqlite' && '${conductor.scheduler.enabled:false}' == 'true'")
public class SqliteSchedulerConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flywayForScheduler(DataSource dataSource) {
        return Flyway.configure()
                .locations("classpath:db/migration_scheduler_sqlite")
                .dataSource(dataSource)
                .table("flyway_schema_history_scheduler_sqlite")
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    @Bean
    @DependsOn("flywayForScheduler")
    public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        return new SqliteSchedulerDAO(dataSource, objectMapper);
    }
}
```

---

### Task 8: Implement `SqliteSchedulerDAO`

**Files:**
- Create: `scheduler-sqlite-persistence/src/main/java/org/conductoross/conductor/scheduler/sqlite/dao/SqliteSchedulerDAO.java`

- [ ] **Step 1: Write the DAO**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.sqlite.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.netflix.conductor.core.exception.NonTransientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SQLite implementation of {@link SchedulerDAO}.
 *
 * <p>Uses Spring {@link JdbcTemplate} and Flyway-managed migrations ({@code
 * db/migration_scheduler_sqlite}). Functionally equivalent to the MySQL implementation but uses
 * SQLite-compatible SQL syntax ({@code INSERT OR REPLACE INTO} for upserts; no {@code NULLS LAST}
 * in ORDER BY; manual {@code IN} placeholder expansion).
 *
 * <p><b>Pool size constraint:</b> The DataSource must be configured with {@code maximumPoolSize=1}.
 * SQLite in-memory databases are connection-scoped; a second connection creates an independent
 * database instance.
 */
public class SqliteSchedulerDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchedulerDAO.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    public SqliteSchedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = objectMapper;
    }

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        String sql =
                "INSERT OR REPLACE INTO scheduler "
                        + "(scheduler_name, workflow_name, json_data, next_run_time) "
                        + "VALUES (?, ?, ?, ?)";
        jdbc.update(
                sql,
                schedule.getName(),
                schedule.getStartWorkflowRequest() != null
                        ? schedule.getStartWorkflowRequest().getName()
                        : null,
                toJson(schedule),
                schedule.getNextRunTime());
    }

    @Override
    public WorkflowSchedule findScheduleByName(String name) {
        String sql = "SELECT json_data FROM scheduler WHERE scheduler_name = ?";
        List<WorkflowSchedule> results = jdbc.query(sql, scheduleRowMapper(), name);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules() {
        return jdbc.query("SELECT json_data FROM scheduler", scheduleRowMapper());
    }

    @Override
    public List<WorkflowSchedule> findAllSchedules(String workflowName) {
        String sql = "SELECT json_data FROM scheduler WHERE workflow_name = ?";
        return jdbc.query(sql, scheduleRowMapper(), workflowName);
    }

    @Override
    public Map<String, WorkflowSchedule> findAllByNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = names.stream().map(n -> "?").collect(Collectors.joining(","));
        String sql =
                "SELECT json_data FROM scheduler WHERE scheduler_name IN (" + placeholders + ")";
        List<WorkflowSchedule> schedules = jdbc.query(sql, scheduleRowMapper(), names.toArray());
        Map<String, WorkflowSchedule> result = new HashMap<>();
        for (WorkflowSchedule s : schedules) {
            result.put(s.getName(), s);
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        txTemplate.executeWithoutResult(
                status -> {
                    jdbc.update(
                            "DELETE FROM scheduler_execution WHERE schedule_name = ?", name);
                    jdbc.update("DELETE FROM scheduler WHERE scheduler_name = ?", name);
                });
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecution execution) {
        String sql =
                "INSERT OR REPLACE INTO scheduler_execution "
                        + "(execution_id, schedule_name, state, execution_time, json_data) "
                        + "VALUES (?, ?, ?, ?, ?)";
        jdbc.update(
                sql,
                execution.getExecutionId(),
                execution.getScheduleName(),
                execution.getState() != null ? execution.getState().name() : null,
                execution.getExecutionTime(),
                toJson(execution));
    }

    @Override
    public WorkflowScheduleExecution readExecutionRecord(String executionId) {
        String sql = "SELECT json_data FROM scheduler_execution WHERE execution_id = ?";
        List<WorkflowScheduleExecution> results =
                jdbc.query(sql, executionRowMapper(), executionId);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        jdbc.update("DELETE FROM scheduler_execution WHERE execution_id = ?", executionId);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        return jdbc.queryForList(
                "SELECT execution_id FROM scheduler_execution WHERE state = 'POLLED'",
                String.class);
    }

    @Override
    public List<WorkflowScheduleExecution> getPendingExecutionRecords() {
        return jdbc.query(
                "SELECT json_data FROM scheduler_execution WHERE state = 'POLLED'",
                executionRowMapper());
    }

    @Override
    public List<WorkflowScheduleExecution> getExecutionRecords(String scheduleName, int limit) {
        String sql =
                "SELECT json_data FROM scheduler_execution "
                        + "WHERE schedule_name = ? "
                        + "ORDER BY execution_time DESC "
                        + "LIMIT ?";
        return jdbc.query(sql, executionRowMapper(), scheduleName, limit);
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        String sql = "SELECT next_run_time FROM scheduler WHERE scheduler_name = ?";
        List<Long> results = jdbc.queryForList(sql, Long.class, scheduleName);
        if (results.isEmpty() || results.get(0) == null) {
            return -1L;
        }
        return results.get(0);
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        jdbc.update(
                "UPDATE scheduler SET next_run_time = ? WHERE scheduler_name = ?",
                epochMillis,
                scheduleName);
    }

    private RowMapper<WorkflowSchedule> scheduleRowMapper() {
        return (rs, rowNum) -> {
            try {
                return objectMapper.readValue(rs.getString("json_data"), WorkflowSchedule.class);
            } catch (Exception e) {
                throw new NonTransientException("Failed to deserialize WorkflowSchedule", e);
            }
        };
    }

    private RowMapper<WorkflowScheduleExecution> executionRowMapper() {
        return (rs, rowNum) -> {
            try {
                return objectMapper.readValue(
                        rs.getString("json_data"), WorkflowScheduleExecution.class);
            } catch (Exception e) {
                throw new NonTransientException(
                        "Failed to deserialize WorkflowScheduleExecution", e);
            }
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize to JSON", e);
        }
    }
}
```

---

### Task 9: Create `SqliteSchedulerDAOTest`

**Files:**
- Create: `scheduler-sqlite-persistence/src/test/java/org/conductoross/conductor/scheduler/sqlite/dao/SqliteSchedulerDAOTest.java`

Pattern: mirrors `PostgresSchedulerDAOTest` but uses `jdbc:sqlite::memory:` with pool-size=1 and no Testcontainers.

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.sqlite.dao;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.AbstractSchedulerDAOTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.sqlite.config.SqliteSchedulerConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration(
        classes = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            SqliteSchedulerDAOTest.SqliteTestConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:sqlite::memory:",
            "spring.datasource.driver-class-name=org.sqlite.JDBC",
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.flyway.enabled=false"
        })
public class SqliteSchedulerDAOTest extends AbstractSchedulerDAOTest {

    @TestConfiguration
    static class SqliteTestConfiguration {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapperProvider().getObjectMapper();
        }

        @Bean(initMethod = "migrate")
        public Flyway flywayForScheduler(DataSource dataSource) {
            return Flyway.configure()
                    .locations("classpath:db/migration_scheduler_sqlite")
                    .dataSource(dataSource)
                    .table("flyway_schema_history_scheduler_sqlite")
                    .outOfOrder(true)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load();
        }

        @Bean
        @DependsOn("flywayForScheduler")
        public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
            return new SqliteSchedulerDAO(dataSource, objectMapper);
        }
    }

    @Autowired private SchedulerDAO schedulerDAO;
    @Autowired private DataSource dataSource;

    @Override
    protected SchedulerDAO dao() {
        return schedulerDAO;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :conductor-scheduler-sqlite-persistence:test \
  --tests "*.SqliteSchedulerDAOTest" --info
```

Expected: All tests pass. SQLite in-memory — no Docker required.

---

### Task 10: Create `SqliteSchedulerServiceIntegrationTest`

**Files:**
- Create: `scheduler-sqlite-persistence/src/test/java/org/conductoross/conductor/scheduler/sqlite/service/SqliteSchedulerServiceIntegrationTest.java`

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.sqlite.service;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.service.AbstractSchedulerServiceIntegrationTest;
import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerDAO;
import org.flywaydb.core.Flyway;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration(
        classes = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            SqliteSchedulerServiceIntegrationTest.SqliteTestConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:sqlite::memory:",
            "spring.datasource.driver-class-name=org.sqlite.JDBC",
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.flyway.enabled=false"
        })
public class SqliteSchedulerServiceIntegrationTest extends AbstractSchedulerServiceIntegrationTest {

    @TestConfiguration
    static class SqliteTestConfiguration {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapperProvider().getObjectMapper();
        }

        @Bean(initMethod = "migrate")
        public Flyway flywayForScheduler(DataSource dataSource) {
            return Flyway.configure()
                    .locations("classpath:db/migration_scheduler_sqlite")
                    .dataSource(dataSource)
                    .table("flyway_schema_history_scheduler_sqlite")
                    .outOfOrder(true)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load();
        }

        @Bean
        @DependsOn("flywayForScheduler")
        public SchedulerDAO schedulerDAO(DataSource dataSource, ObjectMapper objectMapper) {
            return new SqliteSchedulerDAO(dataSource, objectMapper);
        }
    }

    @Autowired private SchedulerDAO schedulerDAO;
    @Autowired private DataSource dataSource;

    @Override
    protected SchedulerDAO dao() {
        return schedulerDAO;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
```

- [ ] **Step 2: Run**

```bash
./gradlew :conductor-scheduler-sqlite-persistence:test \
  --tests "*.SqliteSchedulerServiceIntegrationTest" --info
```

Expected: GREEN.

---

### Task 11: Create `SqliteSchedulerAutoConfigurationSmokeTest`

**Files:**
- Create: `scheduler-sqlite-persistence/src/test/java/org/conductoross/conductor/scheduler/sqlite/config/SqliteSchedulerAutoConfigurationSmokeTest.java`

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.sqlite.config;

import org.conductoross.conductor.scheduler.config.AbstractSchedulerAutoConfigurationSmokeTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.sqlite.dao.SqliteSchedulerDAO;

public class SqliteSchedulerAutoConfigurationSmokeTest
        extends AbstractSchedulerAutoConfigurationSmokeTest {

    @Override
    protected String dbTypeValue() {
        return "sqlite";
    }

    @Override
    protected String datasourceUrl() {
        return "jdbc:sqlite::memory:";
    }

    @Override
    protected String driverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected Class<?> persistenceAutoConfigClass() {
        return SqliteSchedulerConfiguration.class;
    }

    @Override
    protected Class<? extends SchedulerDAO> expectedDaoClass() {
        return SqliteSchedulerDAO.class;
    }

    /**
     * SQLite in-memory requires maximumPoolSize=1 (a second connection creates an independent
     * in-memory database, causing Flyway to migrate a different DB than the DAO queries).
     * The abstract baseRunner() does not set this, so override it here unconditionally.
     */
    @Override
    protected org.springframework.boot.test.context.runner.ApplicationContextRunner baseRunner() {
        return super.baseRunner()
                .withPropertyValues("spring.datasource.hikari.maximum-pool-size=1");
    }
}
```

- [ ] **Step 2: Run the smoke tests**

```bash
./gradlew :conductor-scheduler-sqlite-persistence:test \
  --tests "*.SqliteSchedulerAutoConfigurationSmokeTest" --info
```

Expected: all 5 smoke tests pass.

---

### Task 12: Run full SQLite module test suite and commit

- [ ] **Step 1: Run all SQLite tests**

```bash
./gradlew :conductor-scheduler-sqlite-persistence:test --info
```

Expected: all tests GREEN.

- [ ] **Step 2: Commit**

```bash
git add scheduler-sqlite-persistence/
git commit -m "feat: add conductor-scheduler-sqlite-persistence module with Flyway schema and tests"
```

---

## Chunk 3: JedisProxy Extensions and Redis Module

### Task 13: Add `zscore()` and `zrevrangeByScore()` to `JedisProxy`

**Files:**
- Modify: `redis-persistence/src/main/java/com/netflix/conductor/redis/jedis/JedisProxy.java`

These two methods are needed by `RedisSchedulerDAO` and are not currently in `JedisProxy`.

- [ ] **Step 1: Read `JedisProxy.java` to locate the best insertion point**

```bash
grep -n "public.*zrem\|public.*zadd\|public.*zrange" \
  redis-persistence/src/main/java/com/netflix/conductor/redis/jedis/JedisProxy.java
```

- [ ] **Step 2: Add `zscore()` after the existing `zrem()` method**

```java
    public Double zscore(String key, String member) {
        LOGGER.trace("zscore {} {}", key, member);
        return jedisCommands.zscore(key, member);
    }
```

- [ ] **Step 3: Add `zrevrangeByScore()` after `zscore()`**

Use String bounds so callers can pass `"+inf"` and `"-inf"`. `JedisCommands.zrevrangeByScore` returns `Set<String>` — wrap in `new ArrayList<>()` to preserve insertion order (Jedis returns `LinkedHashSet` maintaining Redis's descending-score ordering) and return `List<String>` for convenient indexed access in the DAO.

```java
    public List<String> zrevrangeByScore(
            String key, String max, String min, int offset, int count) {
        LOGGER.trace("zrevrangeByScore {} {} {} {} {}", key, max, min, offset, count);
        return new ArrayList<>(jedisCommands.zrevrangeByScore(key, max, min, offset, count));
    }
```

Also add `import java.util.ArrayList;` and `import java.util.List;` to `JedisProxy.java` if not already present (check existing imports first).

- [ ] **Step 4: Verify `redis-persistence` compiles**

```bash
./gradlew :conductor-redis-persistence:compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add redis-persistence/src/main/java/com/netflix/conductor/redis/jedis/JedisProxy.java
git commit -m "feat: add zscore() and zrevrangeByScore() to JedisProxy for scheduler Redis DAO"
```

---

### Task 14: Create Redis module build file and auto-configuration

**Files:**
- Create: `scheduler-redis-persistence/build.gradle`
- Create: `scheduler-redis-persistence/src/main/java/org/conductoross/conductor/scheduler/redis/config/RedisSchedulerConfiguration.java`
- Create: `scheduler-redis-persistence/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Create `scheduler-redis-persistence/build.gradle`**

```groovy
dependencies {
    implementation project(':conductor-scheduler')
    implementation project(':conductor-common')
    implementation project(':conductor-core')
    implementation project(':conductor-redis-persistence')

    compileOnly 'org.springframework.boot:spring-boot-starter'

    implementation "com.fasterxml.jackson.core:jackson-databind"
    implementation "com.fasterxml.jackson.core:jackson-core"
    implementation "org.apache.commons:commons-lang3"

    testImplementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation "org.testcontainers:testcontainers:${revTestContainer}"
    testImplementation testFixtures(project(':conductor-scheduler'))
}

test {
    maxParallelForks = 1
    environment 'DOCKER_API_VERSION', '1.44'
}
```

Note: No JDBC driver, no Flyway — Redis needs neither.

- [ ] **Step 2: Create `RedisSchedulerConfiguration.java`**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.redis.config;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.redis.dao.RedisSchedulerDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;

import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Auto-configures the Redis-backed {@link SchedulerDAO}.
 *
 * <p>Active when {@code conductor.db.type} is one of {@code redis_standalone},
 * {@code redis_cluster}, or {@code redis_sentinel} AND {@code conductor.scheduler.enabled=true}.
 * Injects the existing {@link JedisProxy} bean — no new Redis connection infrastructure.
 */
@AutoConfiguration
@ConditionalOnExpression(
        "('${conductor.db.type:}' == 'redis_standalone' "
                + "|| '${conductor.db.type:}' == 'redis_cluster' "
                + "|| '${conductor.db.type:}' == 'redis_sentinel') "
                + "&& '${conductor.scheduler.enabled:false}' == 'true'")
public class RedisSchedulerConfiguration {

    @Bean
    public SchedulerDAO schedulerDAO(JedisProxy jedisProxy, ObjectMapper objectMapper) {
        return new RedisSchedulerDAO(jedisProxy, objectMapper);
    }
}
```

- [ ] **Step 3: Create SPI registration file**

Create `scheduler-redis-persistence/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
org.conductoross.conductor.scheduler.redis.config.RedisSchedulerConfiguration
```

---

### Task 15: Implement `RedisSchedulerDAO`

**Files:**
- Create: `scheduler-redis-persistence/src/main/java/org/conductoross/conductor/scheduler/redis/dao/RedisSchedulerDAO.java`

Redis key design recap:
- `conductor_scheduler:schedules` — HASH, field=scheduleName, value=JSON
- `conductor_scheduler:by_workflow:{wfName}` — SET of schedule names
- `conductor_scheduler:next_run` — ZSET, member=scheduleName, score=epochMs
- `conductor_scheduler:execution:{id}` — STRING, value=JSON
- `conductor_scheduler:exec_by_sched:{scheduleName}` — ZSET, member=executionId, score=executionTime
- `conductor_scheduler:pending_execs` — SET of executionIds in POLLED state

- [ ] **Step 1: Write the DAO**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.redis.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis implementation of {@link SchedulerDAO} using {@link JedisProxy}.
 *
 * <p>All keys use the prefix {@code conductor_scheduler:} (underscore separator). Data structures:
 *
 * <ul>
 *   <li>HASH {@code conductor_scheduler:schedules} — all schedule JSON blobs
 *   <li>SET {@code conductor_scheduler:by_workflow:{wfName}} — schedule names per workflow
 *   <li>ZSET {@code conductor_scheduler:next_run} — schedule names scored by next-run epoch ms
 *   <li>STRING {@code conductor_scheduler:execution:{id}} — individual execution JSON blobs
 *   <li>ZSET {@code conductor_scheduler:exec_by_sched:{name}} — execution IDs scored by time
 *   <li>SET {@code conductor_scheduler:pending_execs} — execution IDs in POLLED state
 * </ul>
 */
public class RedisSchedulerDAO implements SchedulerDAO {

    private static final Logger log = LoggerFactory.getLogger(RedisSchedulerDAO.class);

    static final String KEY_SCHEDULES = "conductor_scheduler:schedules";
    static final String KEY_NEXT_RUN = "conductor_scheduler:next_run";
    static final String KEY_PENDING_EXECS = "conductor_scheduler:pending_execs";

    private static String keyByWorkflow(String workflowName) {
        return "conductor_scheduler:by_workflow:" + workflowName;
    }

    private static String keyExecution(String executionId) {
        return "conductor_scheduler:execution:" + executionId;
    }

    private static String keyExecBySched(String scheduleName) {
        return "conductor_scheduler:exec_by_sched:" + scheduleName;
    }

    private final JedisProxy jedis;
    private final ObjectMapper objectMapper;

    public RedisSchedulerDAO(JedisProxy jedisProxy, ObjectMapper objectMapper) {
        this.jedis = jedisProxy;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Schedule CRUD
    // -------------------------------------------------------------------------

    @Override
    public void updateSchedule(WorkflowSchedule schedule) {
        // If a schedule already exists, update by_workflow membership if workflow changed
        String existingJson = jedis.hget(KEY_SCHEDULES, schedule.getName());
        if (existingJson != null) {
            WorkflowSchedule existing = fromJson(existingJson, WorkflowSchedule.class);
            String oldWf = existing.getStartWorkflowRequest() != null
                    ? existing.getStartWorkflowRequest().getName() : null;
            String newWf = schedule.getStartWorkflowRequest() != null
                    ? schedule.getStartWorkflowRequest().getName() : null;
            if (oldWf != null && !oldWf.equals(newWf)) {
                jedis.srem(keyByWorkflow(oldWf), schedule.getName());
            }
        }
        jedis.hset(KEY_SCHEDULES, schedule.getName(), toJson(schedule));
        if (schedule.getStartWorkflowRequest() != null
                && schedule.getStartWorkflowRequest().getName() != null) {
            jedis.sadd(
                    keyByWorkflow(schedule.getStartWorkflowRequest().getName()),
                    schedule.getName());
        }
        long score = schedule.getNextRunTime() != null ? schedule.getNextRunTime() : 0L;
        jedis.zadd(KEY_NEXT_RUN, (double) score, schedule.getName());
    }

    @Override
    public WorkflowSchedule findScheduleByName(String name) {
        String json = jedis.hget(KEY_SCHEDULES, name);
        return json != null ? fromJson(json, WorkflowSchedule.class) : null;
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules() {
        List<String> jsons = jedis.hvals(KEY_SCHEDULES);
        List<WorkflowSchedule> result = new ArrayList<>();
        for (String json : jsons) {
            result.add(fromJson(json, WorkflowSchedule.class));
        }
        return result;
    }

    @Override
    public List<WorkflowSchedule> findAllSchedules(String workflowName) {
        Set<String> names = jedis.smembers(keyByWorkflow(workflowName));
        List<WorkflowSchedule> result = new ArrayList<>();
        for (String name : names) {
            String json = jedis.hget(KEY_SCHEDULES, name);
            if (json != null) {
                result.add(fromJson(json, WorkflowSchedule.class));
            }
        }
        return result;
    }

    @Override
    public Map<String, WorkflowSchedule> findAllByNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, WorkflowSchedule> result = new HashMap<>();
        for (String name : names) {
            String json = jedis.hget(KEY_SCHEDULES, name);
            if (json != null) {
                result.put(name, fromJson(json, WorkflowSchedule.class));
            }
        }
        return result;
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        // Read JSON first (needed for by_workflow cleanup) before removing from HASH
        String json = jedis.hget(KEY_SCHEDULES, name);
        // Remove from HASH first — makes the schedule invisible to queries immediately,
        // bounding the inconsistency window if the process crashes mid-cleanup.
        jedis.hdel(KEY_SCHEDULES, name);
        jedis.zrem(KEY_NEXT_RUN, name);
        if (json != null) {
            WorkflowSchedule schedule = fromJson(json, WorkflowSchedule.class);
            if (schedule.getStartWorkflowRequest() != null
                    && schedule.getStartWorkflowRequest().getName() != null) {
                jedis.srem(
                        keyByWorkflow(schedule.getStartWorkflowRequest().getName()), name);
            }
        }
        // Remove all execution records for this schedule
        List<String> execIds =
                jedis.zrevrangeByScore(keyExecBySched(name), "+inf", "-inf", 0, Integer.MAX_VALUE);
        for (String execId : execIds) {
            jedis.del(keyExecution(execId));
            jedis.srem(KEY_PENDING_EXECS, execId);
        }
        jedis.del(keyExecBySched(name));
    }

    // -------------------------------------------------------------------------
    // Execution tracking
    // -------------------------------------------------------------------------

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecution execution) {
        jedis.set(keyExecution(execution.getExecutionId()), toJson(execution));
        long score = execution.getExecutionTime() != null ? execution.getExecutionTime() : 0L;
        jedis.zadd(keyExecBySched(execution.getScheduleName()), (double) score,
                execution.getExecutionId());
        if (execution.getState() == WorkflowScheduleExecution.ExecutionState.POLLED) {
            jedis.sadd(KEY_PENDING_EXECS, execution.getExecutionId());
        } else {
            jedis.srem(KEY_PENDING_EXECS, execution.getExecutionId());
        }
    }

    @Override
    public WorkflowScheduleExecution readExecutionRecord(String executionId) {
        String json = jedis.get(keyExecution(executionId));
        return json != null ? fromJson(json, WorkflowScheduleExecution.class) : null;
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        // Read first to get scheduleName for ZSET cleanup
        String json = jedis.get(keyExecution(executionId));
        if (json != null) {
            WorkflowScheduleExecution ex = fromJson(json, WorkflowScheduleExecution.class);
            if (ex.getScheduleName() != null) {
                jedis.zrem(keyExecBySched(ex.getScheduleName()), executionId);
            }
        }
        jedis.del(keyExecution(executionId));
        jedis.srem(KEY_PENDING_EXECS, executionId);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        Set<String> ids = jedis.smembers(KEY_PENDING_EXECS);
        return new ArrayList<>(ids);
    }

    @Override
    public List<WorkflowScheduleExecution> getPendingExecutionRecords() {
        Set<String> ids = jedis.smembers(KEY_PENDING_EXECS);
        List<WorkflowScheduleExecution> result = new ArrayList<>();
        for (String id : ids) {
            String json = jedis.get(keyExecution(id));
            if (json != null) {
                result.add(fromJson(json, WorkflowScheduleExecution.class));
            }
        }
        return result;
    }

    @Override
    public List<WorkflowScheduleExecution> getExecutionRecords(String scheduleName, int limit) {
        List<String> execIds =
                jedis.zrevrangeByScore(keyExecBySched(scheduleName), "+inf", "-inf", 0, limit);
        List<WorkflowScheduleExecution> result = new ArrayList<>();
        for (String id : execIds) {
            String json = jedis.get(keyExecution(id));
            if (json != null) {
                result.add(fromJson(json, WorkflowScheduleExecution.class));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Next-run time management
    // -------------------------------------------------------------------------

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        Double score = jedis.zscore(KEY_NEXT_RUN, scheduleName);
        return score != null ? score.longValue() : -1L;
    }

    @Override
    public void setNextRunTimeInEpoch(String scheduleName, long epochMillis) {
        // Guard: if the schedule does not exist, do nothing (contract: non-existent schedule
        // must still return -1L from getNextRunTimeInEpoch after this call).
        if (!jedis.hexists(KEY_SCHEDULES, scheduleName)) {
            return;
        }
        jedis.zadd(KEY_NEXT_RUN, (double) epochMillis, scheduleName);
        // Keep the JSON blob consistent with the ZSET score
        String json = jedis.hget(KEY_SCHEDULES, scheduleName);
        if (json != null) {
            WorkflowSchedule schedule = fromJson(json, WorkflowSchedule.class);
            schedule.setNextRunTime(epochMillis);
            jedis.hset(KEY_SCHEDULES, scheduleName, toJson(schedule));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NonTransientException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new NonTransientException("Failed to deserialize from JSON", e);
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :conductor-scheduler-redis-persistence:compileJava
```

Expected: BUILD SUCCESSFUL.

---

### Task 16: Create `RedisSchedulerDAOTest`

**Files:**
- Create: `scheduler-redis-persistence/src/test/java/org/conductoross/conductor/scheduler/redis/dao/RedisSchedulerDAOTest.java`

No Spring context — create DAO directly from a JedisPool connected to a Testcontainers Redis.

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.redis.dao;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.AbstractSchedulerDAOTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisSchedulerDAOTest extends AbstractSchedulerDAOTest {

    @ClassRule
    @SuppressWarnings("resource")
    public static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static JedisPool jedisPool;
    private static RedisSchedulerDAO redisDAO;

    @BeforeClass
    public static void setUpClass() {
        jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(6379));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        // JedisProxy takes a JedisCommands implementation — JedisStandalone wraps a JedisPool.
        // This mirrors how RedisStandaloneConfiguration constructs JedisProxy in production.
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        redisDAO = new RedisSchedulerDAO(jedisProxy, objectMapper);
    }

    @AfterClass
    public static void tearDownClass() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Override
    protected SchedulerDAO dao() {
        return redisDAO;
    }

    @Override
    protected DataSource dataSource() {
        throw new UnsupportedOperationException("Redis does not use a DataSource");
    }

    @Override
    protected void truncateStore() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDb();
        }
    }
}
```

- [ ] **Step 3: Run**

```bash
./gradlew :conductor-scheduler-redis-persistence:test \
  --tests "*.RedisSchedulerDAOTest" --info
```

Expected: all contract tests pass against a real Redis 7 instance.

---

### Task 17: Create `RedisSchedulerServiceIntegrationTest`

**Files:**
- Create: `scheduler-redis-persistence/src/test/java/org/conductoross/conductor/scheduler/redis/service/RedisSchedulerServiceIntegrationTest.java`

Same no-Spring-context approach as the DAO test.

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.redis.service;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.redis.dao.RedisSchedulerDAO;
import org.conductoross.conductor.scheduler.service.AbstractSchedulerServiceIntegrationTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisSchedulerServiceIntegrationTest extends AbstractSchedulerServiceIntegrationTest {

    @ClassRule
    @SuppressWarnings("resource")
    public static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static JedisPool jedisPool;
    private static RedisSchedulerDAO redisDAO;

    @BeforeClass
    public static void setUpClass() {
        jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(6379));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        redisDAO = new RedisSchedulerDAO(jedisProxy, objectMapper);
    }

    @AfterClass
    public static void tearDownClass() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Override
    protected SchedulerDAO dao() {
        return redisDAO;
    }

    @Override
    protected DataSource dataSource() {
        throw new UnsupportedOperationException("Redis does not use a DataSource");
    }

    @Override
    protected void truncateStore() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDb();
        }
    }
}
```

- [ ] **Step 2: Run**

```bash
./gradlew :conductor-scheduler-redis-persistence:test \
  --tests "*.RedisSchedulerServiceIntegrationTest" --info
```

Expected: GREEN.

---

### Task 18: Create `RedisSchedulerAutoConfigurationSmokeTest`

**Files:**
- Create: `scheduler-redis-persistence/src/test/java/org/conductoross/conductor/scheduler/redis/config/RedisSchedulerAutoConfigurationSmokeTest.java`

The Redis smoke test extends `AbstractSchedulerAutoConfigurationSmokeTest` but overrides `baseRunner()` to configure a mock `JedisProxy` bean instead of a DataSource. The abstract class's `baseRunner()` was made `protected` in Task 3.

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); ...
 */
package org.conductoross.conductor.scheduler.redis.config;

import org.conductoross.conductor.scheduler.config.AbstractSchedulerAutoConfigurationSmokeTest;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.redis.dao.RedisSchedulerDAO;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;

public class RedisSchedulerAutoConfigurationSmokeTest
        extends AbstractSchedulerAutoConfigurationSmokeTest {

    @Override
    protected ApplicationContextRunner baseRunner() {
        JedisProxy mockProxy = mock(JedisProxy.class);
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(persistenceAutoConfigClass()))
                .withBean(JedisProxy.class, () -> mockProxy)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withPropertyValues("spring.flyway.enabled=false");
    }

    @Override
    protected String dbTypeValue() {
        return "redis_standalone";
    }

    @Override
    protected String datasourceUrl() {
        return ""; // not used — Redis does not need a JDBC datasource
    }

    @Override
    protected String driverClassName() {
        return ""; // not used
    }

    @Override
    protected Class<?> persistenceAutoConfigClass() {
        return RedisSchedulerConfiguration.class;
    }

    @Override
    protected Class<? extends SchedulerDAO> expectedDaoClass() {
        return RedisSchedulerDAO.class;
    }
}
```

Note: `testNoSchedulerDAO_whenDbTypeIsWrongBackend()` in the abstract class computes `wrongType` as `"postgres"` when `dbTypeValue()` is neither `"postgres"` nor `"mysql"`. This is correct — `"postgres"` will not match the Redis conditional expression.

- [ ] **Step 2: Run**

```bash
./gradlew :conductor-scheduler-redis-persistence:test \
  --tests "*.RedisSchedulerAutoConfigurationSmokeTest" --info
```

Expected: 5 smoke tests pass.

---

### Task 19: Run full Redis module test suite and commit

- [ ] **Step 1: Run all Redis tests**

```bash
./gradlew :conductor-scheduler-redis-persistence:test --info
```

Expected: all tests GREEN.

- [ ] **Step 2: Commit**

```bash
git add scheduler-redis-persistence/
git commit -m "feat: add conductor-scheduler-redis-persistence module with JedisProxy-backed DAO and tests"
```

---

## Chunk 4: Mock Test Cleanup

### Task 20: Delete mock-based service tests

**Files:**
- Delete: `scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServiceTest.java`
- Delete: `scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServicePhase2Test.java`
- Delete: `scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServiceStressTest.java`

These three files test `SchedulerService` behavior against mocked DAOs. All scenarios they cover are now covered by `AbstractSchedulerServiceIntegrationTest` running against real storage backends (SQLite, Postgres, MySQL, Redis).

- [ ] **Step 1: Verify all four backend service integration tests are passing before deleting mocks**

```bash
./gradlew \
  :conductor-scheduler-sqlite-persistence:test --tests "*.SqliteSchedulerServiceIntegrationTest" \
  :conductor-scheduler-postgres-persistence:test --tests "*.PostgresSchedulerServiceIntegrationTest" \
  :conductor-scheduler-mysql-persistence:test --tests "*.MySQLSchedulerServiceIntegrationTest" \
  :conductor-scheduler-redis-persistence:test --tests "*.RedisSchedulerServiceIntegrationTest" \
  --info
```

Expected: GREEN on all four.

- [ ] **Step 2: Delete the files**

```bash
rm scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServiceTest.java
rm scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServicePhase2Test.java
rm scheduler/src/test/java/org/conductoross/conductor/scheduler/service/SchedulerServiceStressTest.java
```

- [ ] **Step 3: Verify the `conductor-scheduler` module still compiles and its remaining tests pass**

```bash
./gradlew :conductor-scheduler:test --info
```

Expected: BUILD SUCCESSFUL (the scheduler module has no more test classes in the service package, which is fine — behavior coverage lives in the persistence module tests).

- [ ] **Step 4: Commit**

```bash
git add -u scheduler/src/test/java/org/conductoross/conductor/scheduler/service/
git commit -m "refactor: delete mock-based scheduler service tests (replaced by Testcontainers integration tests)"
```

---

### Task 21: Final verification

- [ ] **Run all scheduler-related modules**

```bash
./gradlew \
  :conductor-scheduler:test \
  :conductor-scheduler-sqlite-persistence:test \
  :conductor-scheduler-postgres-persistence:test \
  :conductor-scheduler-mysql-persistence:test \
  :conductor-scheduler-redis-persistence:test \
  --info
```

Expected: all GREEN.

- [ ] **Verify no references to the deleted mock test classes remain**

```bash
grep -r "SchedulerServiceTest\|SchedulerServicePhase2Test\|SchedulerServiceStressTest" \
  --include="*.java" --include="*.gradle" .
```

Expected: no matches.

- [ ] **Verify `JedisProxy` extensions are used and tested**

```bash
grep -rn "zscore\|zrevrangeByScore" \
  scheduler-redis-persistence/src/main/java/ \
  redis-persistence/src/main/java/ \
  --include="*.java"
```

Expected: `zscore` and `zrevrangeByScore` found in both `JedisProxy.java` and `RedisSchedulerDAO.java`.
