# Conductor E2E Tests

End-to-end tests for conductor-oss covering workflow execution, task types, control flow, event handling, metadata operations, and data processing.

## Prerequisites

- Docker and Docker Compose
- Java 17+
- Gradle (use the `./gradlew` wrapper at the repo root)

## Quick Start

The recommended way to run the full suite is via a convenience script. Each script starts the required Docker services, waits for the server to be healthy, runs all tests, then tears down.

From the **repo root**:

```bash
# Postgres + Elasticsearch 7 (recommended for this project)
./e2e/run_tests-postgres.sh

# Other backends:
./e2e/run_tests.sh                  # Redis + Elasticsearch 7 (default)
./e2e/run_tests-es8.sh              # Redis + Elasticsearch 8
./e2e/run_tests-postgres-es7.sh     # Postgres + Elasticsearch 7 (explicit)
./e2e/run_tests-redis-os2.sh        # Redis + OpenSearch 2.x
./e2e/run_tests-redis-os3.sh        # Redis + OpenSearch 3.x
./e2e/run_tests-mysql.sh            # MySQL
```

The server listens on `http://localhost:8000` by default. Override with:

```bash
SERVER_ROOT_URI=http://localhost:9090 ./e2e/run_tests-postgres.sh
```

## Manual Setup

If you already have a Conductor server running, skip the scripts and run Gradle directly:

```bash
# Using the environment variable (recommended)
RUN_E2E=true SERVER_ROOT_URI=http://localhost:6000 ./gradlew :conductor-e2e:test

# Or using Gradle properties
./gradlew :conductor-e2e:test -PrunE2E -DSERVER_ROOT_URI=http://localhost:8000
```

> Tests are **skipped by default** during a normal `./gradlew build` to avoid requiring a running server. The `RUN_E2E=true` env var or `-PrunE2E` flag is required to enable them.

### Building a local server image

If you need to test against a locally built server (e.g., after changing `core/` code):

```bash
# 1. Build the server JAR
./gradlew :conductor-server:build -x test

# 2. Build the Docker image
docker build -t conductor:server -f docker/server/Dockerfile .

# 3. Start with the e2e compose file (maps port 6000 → 8080)
docker compose -f docker/docker-compose-postgres-e2e.yaml up -d

# 4. Run tests
RUN_E2E=true SERVER_ROOT_URI=http://localhost:6000 ./gradlew :conductor-e2e:test
```

## Test Options

### Run a specific test class or method

```bash
RUN_E2E=true SERVER_ROOT_URI=http://localhost:6000 \
  ./gradlew :conductor-e2e:test --tests "io.conductor.e2e.control.SubWorkflowTests"

# Single method
RUN_E2E=true SERVER_ROOT_URI=http://localhost:6000 \
  ./gradlew :conductor-e2e:test --tests "io.conductor.e2e.control.DoWhileTests.testDoWhileSetVariableFix"
```

### Exclude a test class

```bash
./gradlew :conductor-e2e:test -PrunE2E -PexcludeTests="**/HTTPTaskTests*"

# Multiple patterns (comma-separated)
./gradlew :conductor-e2e:test -PrunE2E -PexcludeTests="**/HTTPTaskTests*,**/GraaljsTests*"
```

### Parallelism

Tests run with `maxParallelForks = 4` by default. Reduce if the server is under-resourced:

```bash
./gradlew :conductor-e2e:test -PrunE2E --max-workers=1
```

## Test Suite Overview

| Package | What it covers |
|---------|----------------|
| `control` | DO_WHILE, SWITCH, SUB_WORKFLOW, DYNAMIC_FORK |
| `task` | WAIT, HTTP, concurrency limit, task timeout, backoff |
| `workflow` | Retry, restart, rerun, search, priority, failure workflows |
| `processing` | GraalJS inline tasks, SET_VARIABLE, JSON_JQ |
| `event` | Event handlers |
| `metadata` | Workflow/task definition CRUD, event handler registration |

## Disabled Tests

17 tests are `@Disabled` due to conductor-oss behavioural differences or infrastructure constraints. All other skips during `./gradlew build` (without `RUN_E2E=true`) are simply the suite waiting for a server — those tests are not broken.

### Rerun behaviour (conductor-oss does not support rerun from non-terminal or complex task states)

| Class | Method | Reason |
|-------|--------|--------|
| `WorkflowRerunTests` | `testRerunFromWaitTask` | Rerunning a RUNNING workflow is not allowed; conductor-oss requires a terminal state first |
| `WorkflowRerunTests` | `testRerunForkJoinWorkflow` | Rerun from a completed fork-branch task does not re-schedule sibling branches |
| `WorkflowRerunTests` | `testRerunForkJoinWorkflowWithLoopTask` | Rerun from a DO_WHILE task inside a fork terminates the workflow instead of resuming |
| `WorkflowRerunTests` | `testRerunForkJoinWorkflowWithLoopTask2` | Rerun from a DO_WHILE task inside a fork terminates the workflow instead of resuming |
| `WorkflowRerunTests` | `testRerunForkJoinWorkflowWithLoopOverTask` | Rerun from a DO_WHILE iteration task inside a fork terminates the workflow instead of resuming |
| `WorkflowRerunTests` | `testRerunSubWorkflowInsideFork` | Rerun from a SUB_WORKFLOW task inside a fork terminates the workflow instead of resuming |
| `WorkflowRerunTests` | `testRerunSubWorkflowInsideFork_SequentialBranch` | Rerun from a SUB_WORKFLOW task inside a fork terminates the workflow instead of resuming |
| `WorkflowRerunTests` | `testDoWhileRerun` | DO_WHILE task rerun leaves the workflow TERMINATED; sync task status is not reset to SCHEDULED |
| `WorkflowRerunTests` | `testSwitchTaskRerun` | SWITCH task rerun leaves the workflow TERMINATED; sync task status is not reset to SCHEDULED |
| `WorkflowRerunTests` | `testRerunForkJoinWithWaitAndSwitchTasks` | Rerun from fork-join with WAIT/SWITCH tasks terminates the workflow instead of resuming |
| `WorkflowRerunTests` | `switchRerunIssue` | SWITCH task rerun leaves the workflow TERMINATED; sync task status is not reset to SCHEDULED |
| `WorkflowRerunTests` | `switchRerunIssue2` | SWITCH inside DO_WHILE rerun leaves the workflow TERMINATED |

### Infrastructure / external dependencies

| Class | Method | Reason |
|-------|--------|--------|
| `HTTPTaskTests` | `HTTPAsyncCompleteTest` | Requires `httpbin-server` internal service (`http://httpbin-server:8081`) not in the conductor-oss e2e docker setup |
| `SyncWorkflowExecutionTest` | `testSyncWorkflowExecution6` | Depends on external HTTP services (`orkes-api-tester.orkesconductor.com`) not reliably accessible from conductor-oss e2e |
| `PollTimeoutTests` | `testPollTimeout` | Postgres-backed queue does not drain tasks from terminated workflows within the required window; cleanup timing is non-deterministic |

### SDK / test isolation

| Class | Method | Reason |
|-------|--------|--------|
| `JavaSDKTests` | `testSDK` | Shared `AnnotatedWorkerExecutor` thread pool is shut down by `SwitchTests.@AfterAll` when tests run in the same JVM; subsequent worker tasks are rejected |
| `SwitchTests` | `testSwitchNegetive` | SDK-based `executeDynamic` with a switch default case does not complete within the timeout in conductor-oss |

### Postgres-specific timing

The postgres-backed WAIT task sweeper adds roughly **10 seconds of overhead** on top of the configured wait duration. Several tests account for this with extended timeouts (e.g., a "2 second" WAIT task may take up to 15 seconds end-to-end). If tests are flaky on slower machines, check whether a timeout needs to be increased further.
