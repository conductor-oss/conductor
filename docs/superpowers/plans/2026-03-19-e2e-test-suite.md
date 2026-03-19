# E2E Test Suite Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `conductor/e2e/` Gradle submodule containing ~50 end-to-end tests ported from orkes-conductor's e2e suite, runnable against any conductor-oss docker-compose backend.

**Architecture:** New `e2e/` Gradle submodule excluded from normal `./gradlew build`. Tests connect to a real Conductor server via `SERVER_ROOT_URI` env var. Shell scripts per backend spin up docker-compose, wait for health, run tests, and tear down.

**Tech Stack:** Java 21, JUnit 5.10.3, Awaitility 4.2.0, `org.conductoross:conductor-client:5.0.1`, `org.conductoross:java-sdk:5.0.1`, Logback 1.5.6

---

## Source & Target Paths

- **Source:** `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/java/io/orkes/conductor/client/`
- **Target:** `conductor/e2e/src/test/java/io/conductor/e2e/`
- **Source metadata:** `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/resources/metadata/`
- **Target metadata:** `conductor/e2e/src/test/resources/metadata/`

## Universal Import / API Substitution Rules

Apply these to every file ported:

| From (orkes) | To (conductor-oss) |
|---|---|
| `io.orkes.conductor.client.ApiClient` | `com.netflix.conductor.client.http.ConductorClient` |
| `io.orkes.conductor.client.OrkesClients` | *(remove — use `ApiUtil` statics)* |
| `io.orkes.conductor.client.WorkflowClient` | `com.netflix.conductor.client.http.WorkflowClient` |
| `io.orkes.conductor.client.TaskClient` | `com.netflix.conductor.client.http.TaskClient` |
| `io.orkes.conductor.client.MetadataClient` | `com.netflix.conductor.client.http.MetadataClient` |
| `io.orkes.conductor.client.EventClient` | `com.netflix.conductor.client.http.EventClient` (if present) |
| `io.orkes.conductor.client.http.OrkesWorkflowClient` | `com.netflix.conductor.client.http.WorkflowClient` |
| `io.orkes.conductor.client.http.OrkesMetadataClient` | `com.netflix.conductor.client.http.MetadataClient` |
| `io.orkes.conductor.client.http.OrkesTaskClient` | `com.netflix.conductor.client.http.TaskClient` |
| `io.orkes.conductor.client.http.ApiException` | `com.netflix.conductor.client.exception.ConductorClientException` |
| `io.orkes.conductor.client.model.WorkflowStatus` | `com.netflix.conductor.common.run.Workflow` (use `Workflow.WorkflowStatus`) |
| `WorkflowStatus.StatusEnum.FAILED.name()` | `Workflow.WorkflowStatus.FAILED.name()` |
| `WorkflowStatus.StatusEnum.RUNNING.name()` | `Workflow.WorkflowStatus.RUNNING.name()` |
| `WorkflowStatus.StatusEnum.COMPLETED.name()` | `Workflow.WorkflowStatus.COMPLETED.name()` |
| `WorkflowStatus.StatusEnum.TIMED_OUT.name()` | `Workflow.WorkflowStatus.TIMED_OUT.name()` |
| `WorkflowStatus.StatusEnum.PAUSED.name()` | `Workflow.WorkflowStatus.PAUSED.name()` |
| `org.testcontainers.shaded.org.awaitility.Awaitility` | `org.awaitility.Awaitility` |
| `io.orkes.conductor.client.util.*` | `io.conductor.e2e.util.*` |
| `io.orkes.conductor.client.e2e.*` | *(remove — already in same package)* |
| `io.orkes.conductor.client.api.*` | `io.conductor.e2e.metadata.*` |
| `new OrkesWorkflowClient(apiClient)` | `ApiUtil.WORKFLOW_CLIENT` |
| `new OrkesMetadataClient(apiClient)` | `ApiUtil.METADATA_CLIENT` |
| `new OrkesTaskClient(apiClient)` | `ApiUtil.TASK_CLIENT` |
| `new OrkesClients(apiClient).getWorkflowClient()` | `ApiUtil.WORKFLOW_CLIENT` |
| `new OrkesClients(apiClient).getMetadataClient()` | `ApiUtil.METADATA_CLIENT` |
| `new OrkesClients(apiClient).getTaskClient()` | `ApiUtil.TASK_CLIENT` |
| `ApiUtil.getApiClientWithCredentials()` | *(remove — not needed)* |
| `ApiUtil.getOrkesClient()` | *(remove — not needed)* |
| Package `io.orkes.conductor.client.e2e` | `io.conductor.e2e` |
| Package `io.orkes.conductor.client.util` | `io.conductor.e2e.util` |
| Package `io.orkes.conductor.client.api` | `io.conductor.e2e.metadata` |

**`@BeforeAll` init pattern — replace every occurrence of:**
```java
apiClient = ApiUtil.getApiClientWithCredentials();
workflowClient = new OrkesWorkflowClient(apiClient);
metadataClient = new OrkesMetadataClient(apiClient);
taskClient = new OrkesTaskClient(apiClient);
```
**With:**
```java
workflowClient = ApiUtil.WORKFLOW_CLIENT;
metadataClient = ApiUtil.METADATA_CLIENT;
taskClient = ApiUtil.TASK_CLIENT;
```
And remove the `static ApiClient apiClient;` field declaration.

---

## Chunk 1: Module Scaffold

### Task 1: Create `e2e/build.gradle` and directory structure

**Files:**
- Create: `e2e/build.gradle`
- Create: `e2e/src/test/java/io/conductor/e2e/.gitkeep`
- Create: `e2e/src/test/resources/.gitkeep`
- Modify: `settings.gradle` (add `include 'e2e'` at end)

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/util
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/workflow
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/task
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/control
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/metadata
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/processing
mkdir -p conductor/e2e/src/test/java/io/conductor/e2e/event
mkdir -p conductor/e2e/src/test/resources/metadata
```

- [ ] **Step 2: Create `e2e/build.gradle`**

```groovy
plugins {
    id 'java'
}

dependencies {
    testImplementation 'org.conductoross:conductor-client:5.0.1'
    testImplementation 'org.conductoross:java-sdk:5.0.1'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.3'
    testRuntimeOnly  'org.junit.jupiter:junit-jupiter-engine:5.10.3'
    testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.3'
    testImplementation 'org.awaitility:awaitility:4.2.0'
    testImplementation 'ch.qos.logback:logback-classic:1.5.6'
    testImplementation 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
}

test {
    useJUnitPlatform()
    maxParallelForks = 4
    minHeapSize = '512m'
    maxHeapSize = '2g'
    testLogging {
        events = ['SKIPPED', 'FAILED']
        exceptionFormat = 'short'
        showStandardStreams = true
    }
    // Forward all system properties (e.g. -DSERVER_ROOT_URI=...)
    systemProperties System.properties

    // Skip during normal ./gradlew build
    // Enable with: RUN_E2E=true ./gradlew :e2e:test
    // or: ./gradlew :e2e:test -PrunE2E
    onlyIf { System.getenv('RUN_E2E') == 'true' || project.hasProperty('runE2E') }

    // Support excluding specific tests:
    // ./gradlew :e2e:test -PrunE2E -PexcludeTests="**/HTTPTaskTests*"
    if (project.hasProperty('excludeTests')) {
        project.property('excludeTests').toString().split(',').each { pattern ->
            exclude pattern.trim()
        }
    }
}
```

- [ ] **Step 3: Add `e2e` to `settings.gradle`**

Open `conductor/settings.gradle`. Append `include 'e2e'` at the end of the file.

- [ ] **Step 4: Create `e2e/src/test/resources/logback-test.xml`**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
    <logger name="io.conductor.e2e" level="DEBUG"/>
</configuration>
```

- [ ] **Step 5: Verify Gradle recognizes the module**

```bash
cd conductor && ./gradlew projects 2>&1 | grep e2e
```
Expected: `+--- Project ':e2e'`

---

## Chunk 2: Utility Classes

### Task 2: Create `ApiUtil.java`

**Files:**
- Create: `e2e/src/test/java/io/conductor/e2e/util/ApiUtil.java`

- [ ] **Step 1: Write `ApiUtil.java`**

```java
package io.conductor.e2e.util;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;

public class ApiUtil {

    public static final String SERVER_ROOT_URI =
            System.getenv().getOrDefault("SERVER_ROOT_URI", "http://localhost:8080");

    public static final ConductorClient CLIENT = ConductorClient.builder()
            .basePath(SERVER_ROOT_URI)
            .build();

    public static final WorkflowClient  WORKFLOW_CLIENT  = new WorkflowClient(CLIENT);
    public static final TaskClient      TASK_CLIENT      = new TaskClient(CLIENT);
    public static final MetadataClient  METADATA_CLIENT  = new MetadataClient(CLIENT);
    public static final EventClient     EVENT_CLIENT     = new EventClient(CLIENT);
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

### Task 3: Create `RegistrationUtil.java`, `Commons.java`, `SimpleWorker.java`, `TestUtil.java`, `WorkflowUtil.java`

**Files:**
- Create: `e2e/src/test/java/io/conductor/e2e/util/RegistrationUtil.java`
- Create: `e2e/src/test/java/io/conductor/e2e/util/Commons.java`
- Create: `e2e/src/test/java/io/conductor/e2e/util/SimpleWorker.java`
- Create: `e2e/src/test/java/io/conductor/e2e/util/TestUtil.java`
- Create: `e2e/src/test/java/io/conductor/e2e/util/WorkflowUtil.java`

Source files to read and adapt:
- `util/RegistrationUtil.java` → change package, change `io.orkes.conductor.client.MetadataClient` → `com.netflix.conductor.client.http.MetadataClient`, change `io.orkes.conductor.client.EventClient` → `com.netflix.conductor.client.http.EventClient`
- `util/Commons.java` → change package, **remove** `TagObject`/`TagString` imports and methods (`getTagObject()`, `getTagString()`), remove `GROUP_ID`, `USER_NAME`, `USER_EMAIL`, `APPLICATION_ID`, `SECRET_MANAGER_*` fields (keep `WORKFLOW_NAME`, `TASK_NAME`, `OWNER_EMAIL`, `WORKFLOW_VERSION`), remove `Category` enum
- `util/SimpleWorker.java` → change package only
- `util/TestUtil.java` → read source, change package and imports per substitution rules
- `util/WorkflowUtil.java` → read source, change package and imports per substitution rules

- [ ] **Step 1: Read source files**

Read each source file from:
- `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/java/io/orkes/conductor/client/util/RegistrationUtil.java`
- `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/java/io/orkes/conductor/client/util/Commons.java`
- `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/java/io/orkes/conductor/client/util/SimpleWorker.java`
- `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/java/io/orkes/conductor/client/util/TestUtil.java`
- `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/java/io/orkes/conductor/client/util/WorkflowUtil.java`

- [ ] **Step 2: Write adapted versions to target paths, applying substitution rules**

For `Commons.java` specifically, the adapted version should only keep:
```java
package io.conductor.e2e.util;

import java.util.UUID;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

public class Commons {
    public static String WORKFLOW_NAME = "test_wf_" + UUID.randomUUID().toString().replace("-", "");
    public static String TASK_NAME = "test-sdk-java-task";
    public static String OWNER_EMAIL = "test@conductor.io";
    public static int WORKFLOW_VERSION = 1;

    public static TaskDef getTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(Commons.TASK_NAME);
        return taskDef;
    }

    public static StartWorkflowRequest getStartWorkflowRequest() {
        return new StartWorkflowRequest().withName(WORKFLOW_NAME).withVersion(WORKFLOW_VERSION);
    }
}
```

- [ ] **Step 3: Compile**

```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

---

## Chunk 3: Copy Metadata JSON Resources

### Task 4: Copy portable metadata JSON files

**Files:**
- Copy all files listed below to `e2e/src/test/resources/metadata/`

Source directory: `/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/resources/metadata/`

Files to copy (exclude AI/NATS/enterprise ones):
- `broken_idempotency_logic_wf.json`
- `concurrency_check_http.json`
- `concurrency_check_wait30.json`
- `context_concurrency_issue.json`
- `cpewf_task_id_dyn_fork_task_def.json`
- `cpewf_task_id_dyn_fork_wf.json`
- `do_while_early_script_eval_wf.json`
- `do_while_keep_last_n_fix.json`
- `dyn_fork_test.json`
- `e2e_on_state_change_tests.json`
- `e2e_on_state_change_tests_sub.json`
- `e2e_on_state_change_tests_sub_set_variable.json`
- `end_time_workflow.json`
- `onstate_fix_test.json`
- `re_run_test_workflow.json`
- `signal_subworkflow.json`
- `stackoverflower.json`
- `sub_workflow_tests.json`
- `switch_rerun_issue.json`
- `switch_rerun_issue_2.json`
- `sync_task_variable_updates.json`
- `sync_workflows.json`
- `tasks_data.json`
- `timed-out-tasks-not-removed.json`
- `vialtodoowhile.json`
- `vialtodoowhile3.json`
- `workflow_data.json`
- `workflow_rate_limit_in_progress_cleanup.json`
- `workflows.json`

- [ ] **Step 1: Copy files using bash**

```bash
SRC=/Users/viren/workspace/github/orkes/branches/orkes-conductor/e2e/src/test/resources/metadata
DST=/Users/viren/workspace/github/conductoross/conductor/e2e/src/test/resources/metadata

for f in broken_idempotency_logic_wf.json concurrency_check_http.json concurrency_check_wait30.json \
  context_concurrency_issue.json cpewf_task_id_dyn_fork_task_def.json cpewf_task_id_dyn_fork_wf.json \
  do_while_early_script_eval_wf.json do_while_keep_last_n_fix.json dyn_fork_test.json \
  e2e_on_state_change_tests.json e2e_on_state_change_tests_sub.json e2e_on_state_change_tests_sub_set_variable.json \
  end_time_workflow.json onstate_fix_test.json re_run_test_workflow.json signal_subworkflow.json \
  stackoverflower.json sub_workflow_tests.json switch_rerun_issue.json switch_rerun_issue_2.json \
  sync_task_variable_updates.json sync_workflows.json tasks_data.json timed-out-tasks-not-removed.json \
  vialtodoowhile.json vialtodoowhile3.json workflow_data.json workflow_rate_limit_in_progress_cleanup.json \
  workflows.json; do
  cp "$SRC/$f" "$DST/$f"
done
echo "Done: $(ls $DST | wc -l) files copied"
```

---

## Chunk 4: Workflow Tests (Batch 1)

For each test in this chunk: read the source file, apply substitution rules, write to target, compile.

### Task 5: Port `WorkflowRetryTests`, `WorkflowRetryTests2`, `WorkflowRerunTests`, `WorkflowRestartTests`

**Source files:**
- `e2e/WorkflowRetryTests.java` → `workflow/WorkflowRetryTests.java`
- `e2e/WorkflowRetryTests2.java` (if exists) → `workflow/WorkflowRetryTests2.java`
- `e2e/WorkflowRerunTests.java` → `workflow/WorkflowRerunTests.java`
- `e2e/WorkflowRestartTests.java` → `workflow/WorkflowRestartTests.java`

- [ ] **Step 1: Read all 4 source files from orkes**
- [ ] **Step 2: Write adapted versions — apply all substitution rules from the table above**

Key changes for these files:
- Change package to `io.conductor.e2e.workflow`
- Replace imports per substitution table
- In `@BeforeAll`: replace `apiClient = ApiUtil.getApiClientWithCredentials(); workflowClient = new OrkesWorkflowClient(apiClient);` etc. with `workflowClient = ApiUtil.WORKFLOW_CLIENT; ...`
- Replace `WorkflowStatus.StatusEnum.X.name()` with `Workflow.WorkflowStatus.X.name()`
- Replace `ApiException` with `ConductorClientException`
- Replace `org.testcontainers.shaded.org.awaitility.Awaitility` import with `org.awaitility.Awaitility`

- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`

### Task 6: Port `WorkflowInputTests`, `WorkflowPriorityTests`, `WorkflowSearchTests`, `WorkflowUpgradeTests`

**Source files** (same source/target pattern as Task 5, all → `workflow/` package)

- [ ] **Step 1: Read all 4 source files from orkes**
- [ ] **Step 2: Write adapted versions applying substitution rules**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`

### Task 7: Port `FailureWorkflowTests`, `StartWorkflowTests`, `WorkflowUpdateAndUpdateTaskTests`, `EndTimeIssueTests`, `WorkflowRateLimiterTests`

**Source files** → all to `workflow/` package.

Note: `WorkflowRateLimiterTests` may reference enterprise rate-limiting API. If it imports anything from `io.orkes.conductor.client.model.WorkflowRateLimit` or other orkes-specific models that have no conductor-oss equivalent, skip it.

- [ ] **Step 1: Read each source file**
- [ ] **Step 2: Check for enterprise-only dependencies — skip files that require orkes-specific models not available in conductor-common**
- [ ] **Step 3: Write adapted versions**
- [ ] **Step 4: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 8: Port `SyncWorkflowExecutionTest`, `KitchensinkEndToEndTest`, `WorkflowPriorityTests` (if missed), `SystemTaskTerminateWorkflowExceptionTests`

- [ ] **Step 1: Read source files**

Note for `KitchensinkEndToEndTest`: Check if it references `IntegrationClient`, `PromptClient`, or AI features. Remove those sections if they appear; keep the core workflow/task functionality tests.

- [ ] **Step 2: Write adapted versions**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

---

## Chunk 5: Task Tests

### Task 9: Port `TaskTimeoutTests`, `TaskRateLimitTests`, `HTTPTaskTests`, `WaitTaskTest`, `PollTimeoutTests`

**Source files** → all to `task/` package

- [ ] **Step 1: Read all 5 source files**
- [ ] **Step 2: Write adapted versions applying substitution rules, changing package to `io.conductor.e2e.task`**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 10: Port `TaskOnStateChangeTests`, `BackoffTests`, `ConcurrentExecLimitTests`

**Source files** → `task/` package

- [ ] **Step 1: Read source files**
- [ ] **Step 2: Write adapted versions**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

---

## Chunk 6: Control Flow Tests

### Task 11: Port `SwitchTests`, `DynamicForkTests`

**Source files** → `control/` package

Note: `SwitchTests` uses `WorkflowExecutor` from `com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor` — this is in `org.conductoross:java-sdk:5.0.1` which is already in build.gradle. Remove the `@Disabled` annotation from `SwitchTests`.

- [ ] **Step 1: Read source files**
- [ ] **Step 2: Write adapted versions with package `io.conductor.e2e.control`**

For `SwitchTests`:
- Change package
- `OrkesClients(apiClient).getTaskClient()` → `ApiUtil.TASK_CLIENT`
- `OrkesClients(apiClient).getWorkflowClient()` → `ApiUtil.WORKFLOW_CLIENT`
- `OrkesClients(apiClient).getMetadataClient()` → `ApiUtil.METADATA_CLIENT`
- Remove `@Disabled` annotation
- `executor.initWorkers("io.orkes.conductor.client.e2e")` → `executor.initWorkers("io.conductor.e2e.control")`

- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 12: Port `SubWorkflowTests`, `SubWorkflowInlineTests`, `SubWorkflowVersionTests`, `SubWorkflowTimeoutRetryTests`, `SubWorkflowPriorityTests`

**Source files** → `control/` package

- [ ] **Step 1: Read all 5 source files**
- [ ] **Step 2: Write adapted versions**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 13: Port `DoWhileTests`, `DoWhileEdgeCasesTests`, `DoWhileWithDomainTests`

**Source files** → `control/` package

- [ ] **Step 1: Read all 3 source files**
- [ ] **Step 2: Write adapted versions**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

---

## Chunk 7: Metadata & Processing Tests

### Task 14: Port `IdempotencyTests`, `IdempotencyStrategyTests`, `EventClientTests`

**Source files** → `metadata/` package

Note: `EventClientTests` may reference enterprise event handler types. Keep only SIMPLE/COMPLETE_TASK actions; skip anything referencing NATS integration.

For `IdempotencyStrategyTests`: the source directory also contains `com/netflix/conductor/common/metadata/workflow/IdempotencyStrategy.java` — check if this class already exists in `conductor-common`. If yes, don't copy it. If not, copy it to `e2e/src/test/java/com/netflix/conductor/common/metadata/workflow/IdempotencyStrategy.java`.

- [ ] **Step 1: Read source files, check if `IdempotencyStrategy` exists in conductor-common**

```bash
find /Users/viren/workspace/github/conductoross/conductor -name "IdempotencyStrategy.java" 2>/dev/null
```

- [ ] **Step 2: Write adapted versions with package `io.conductor.e2e.metadata`**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 15: Port `JSONJQTests`, `GraaljsTests`, `SetVariableTests`

**Source files** → `processing/` package

- [ ] **Step 1: Read source files**
- [ ] **Step 2: Write adapted versions with package `io.conductor.e2e.processing`**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 16: Port `MaskSensitiveDataInWorkflowE2ETest`, `JavaSDKTests`, `ClearContextTest`, `ContextPropagationTest`

**Source files:**
- `MaskSensitiveDataInWorkflowE2ETest` → `processing/`
- `JavaSDKTests` → `workflow/`
- `ClearContextTest` → `workflow/`
- `concurrency/ContextPropagationTest` → `processing/`

- [ ] **Step 1: Read source files**
- [ ] **Step 2: Write adapted versions**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

---

## Chunk 8: Event Tests

### Task 17: Port `OnStateChangeTests`, `WebhookTests`

**Source files** → `event/` package

Note for `OnStateChangeTests`:
- Remove any reference to NATS-specific event sinks
- Keep only HTTP/CONDUCTOR-based event handlers
- The metadata JSON files (`e2e_on_state_change_tests*.json`) are already copied in Task 4

Note for `WebhookTests`:
- This test requires an HTTP callback — check if it uses an external service or local httpbin
- If it requires an external service not present in docker-compose, mark it `@Disabled("requires external HTTP endpoint")` and leave a TODO

- [ ] **Step 1: Read source files**
- [ ] **Step 2: Write adapted versions with package `io.conductor.e2e.event`**
- [ ] **Step 3: Compile**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -15
```

### Task 18: Final compile check — all tests

- [ ] **Step 1: Full compile of e2e module**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL` with 0 errors.

If there are compile errors, fix them before proceeding.

---

## Chunk 9: Shell Scripts

### Task 19: Create test runner shell scripts

**Files to create** (all in `conductor/e2e/`):
- `run_tests.sh` — Redis + ES7 (default: `docker-compose.yaml`)
- `run_tests-postgres.sh` — Postgres (`docker-compose-postgres.yaml`)
- `run_tests-mysql.sh` — MySQL (`docker-compose-mysql.yaml`)
- `run_tests-es8.sh` — Redis + ES8 (`docker-compose-es8.yaml`)
- `run_tests-postgres-es7.sh` — Postgres + ES7 (`docker-compose-postgres-es7.yaml`)
- `run_tests-redis-os2.sh` — Redis + OpenSearch 2 (`docker-compose-redis-os2.yaml`)
- `run_tests-redis-os3.sh` — Redis + OpenSearch 3 (`docker-compose-redis-os3.yaml`)

- [ ] **Step 1: Create `run_tests.sh`**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/../docker/docker-compose.yaml"
export SERVER_ROOT_URI="${SERVER_ROOT_URI:-http://localhost:8080}"

echo "Starting Conductor (Redis + ES7)..."
docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for Conductor server at $SERVER_ROOT_URI..."
for i in $(seq 1 60); do
    if curl -sf "$SERVER_ROOT_URI/health" > /dev/null 2>&1; then
        echo "Conductor is up!"
        break
    fi
    echo "  Attempt $i/60 — waiting 5s..."
    sleep 5
done

if ! curl -sf "$SERVER_ROOT_URI/health" > /dev/null 2>&1; then
    echo "ERROR: Conductor did not start in time"
    docker compose -f "$COMPOSE_FILE" logs conductor-server
    docker compose -f "$COMPOSE_FILE" down -v
    exit 1
fi

cd "$SCRIPT_DIR/.."
./gradlew :e2e:test -PrunE2E -DSERVER_ROOT_URI="$SERVER_ROOT_URI" "$@"
EXIT_CODE=$?

docker compose -f "$COMPOSE_FILE" down -v
exit $EXIT_CODE
```

- [ ] **Step 2: Create the other 6 scripts** — same content, only the `COMPOSE_FILE` and echo line change:

| Script | COMPOSE_FILE | Echo |
|---|---|---|
| `run_tests-postgres.sh` | `../docker/docker-compose-postgres.yaml` | `Postgres + ES7` |
| `run_tests-mysql.sh` | `../docker/docker-compose-mysql.yaml` | `MySQL` |
| `run_tests-es8.sh` | `../docker/docker-compose-es8.yaml` | `Redis + ES8` |
| `run_tests-postgres-es7.sh` | `../docker/docker-compose-postgres-es7.yaml` | `Postgres + ES7 explicit` |
| `run_tests-redis-os2.sh` | `../docker/docker-compose-redis-os2.yaml` | `Redis + OpenSearch 2` |
| `run_tests-redis-os3.sh` | `../docker/docker-compose-redis-os3.yaml` | `Redis + OpenSearch 3` |

- [ ] **Step 3: Make all scripts executable**
```bash
chmod +x conductor/e2e/run_tests*.sh
```

- [ ] **Step 4: Smoke-test script syntax**
```bash
bash -n conductor/e2e/run_tests.sh && echo "syntax OK"
bash -n conductor/e2e/run_tests-postgres.sh && echo "syntax OK"
```

---

## Chunk 10: Final Verification

### Task 20: Full module build verification

- [ ] **Step 1: Ensure normal build is unaffected**
```bash
cd conductor && ./gradlew :server:build 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL` — e2e module does not interfere.

- [ ] **Step 2: Verify e2e module compiles cleanly**
```bash
cd conductor && ./gradlew :e2e:compileTestJava -PrunE2E 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Count ported test files**
```bash
find conductor/e2e/src/test/java -name "*Test*.java" -o -name "*Tests*.java" | wc -l
```
Expected: 40+

- [ ] **Step 4: Verify metadata resources are present**
```bash
ls conductor/e2e/src/test/resources/metadata/ | wc -l
```
Expected: 25+

- [ ] **Step 5: Commit**
```bash
cd conductor
git add e2e/ settings.gradle docs/superpowers/
git commit -m "feat: add e2e test suite module with ~50 tests ported from orkes-conductor

- New :e2e Gradle submodule excluded from default build
- ~50 tests covering workflow, task, control flow, processing, metadata, events
- Uses org.conductoross:conductor-client:5.0.1 SDK
- Shell scripts to spin up each docker-compose backend and run tests
- Run with: RUN_E2E=true ./gradlew :e2e:test or ./e2e/run_tests.sh

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```
