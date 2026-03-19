# E2E Test Suite for Conductor OSS

**Date:** 2026-03-19
**Status:** Approved

## Overview

Add a comprehensive end-to-end test suite to conductor-oss by porting ~45 compatible tests from orkes-conductor's e2e suite. Tests run against a real Conductor server spun up via docker-compose, covering all open-source workflow/task features.

## Module Structure

New Gradle submodule at `conductor/e2e/`:

```
conductor/
└── e2e/
    ├── build.gradle
    ├── src/
    │   └── test/
    │       ├── java/
    │       │   └── io/conductor/e2e/
    │       │       ├── util/          ← ApiUtil, RegistrationUtil, TestUtil, Commons, SimpleWorker
    │       │       ├── workflow/      ← retry, rerun, restart, input, priority, search, sync, failure, kitchensink
    │       │       ├── task/          ← timeout, rate-limit, HTTP, wait, poll-timeout
    │       │       ├── control/       ← switch, dynamic-fork, subworkflow, do-while
    │       │       ├── metadata/      ← metadata client, idempotency
    │       │       ├── processing/    ← JSON/JQ, GraalJS, set-variable, concurrency, backoff
    │       │       └── event/         ← webhook, on-state-change
    │       └── resources/
    │           └── metadata/          ← workflow/task definition JSON files
    ├── run_tests.sh                   ← Redis + ES7 (default)
    ├── run_tests-postgres.sh
    ├── run_tests-mysql.sh
    ├── run_tests-es8.sh
    ├── run_tests-postgres-es7.sh
    ├── run_tests-redis-os2.sh
    └── run_tests-redis-os3.sh
```

`settings.gradle` at root: add `include 'e2e'`.

## Client Library

`org.conductoross:conductor-client:5.0.1` — the official Conductor OSS Java SDK.

```java
// ApiUtil.java — no auth, no multi-user, conductor-oss has no RBAC
public class ApiUtil {
    static final String SERVER_ROOT_URI =
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

## Test Selection (~45 tests)

### Ported from orkes-conductor

| Package | Tests |
|---|---|
| `workflow/` | WorkflowRetryTests, WorkflowRerunTests, WorkflowRestartTests, WorkflowInputTests, WorkflowPriorityTests, WorkflowSearchTests, WorkflowUpgradeTests, SyncWorkflowExecutionTest, FailureWorkflowTests, KitchensinkEndToEndTest |
| `task/` | TaskTimeoutTests, TaskRateLimitTests, HTTPTaskTests, WaitTaskTest, PollTimeoutTests |
| `control/` | SwitchTests, DynamicForkTests, SubWorkflowTests, SubWorkflowInlineTests, SubWorkflowVersionTests, SubWorkflowTimeoutRetryTests, DoWhileTests, DoWhileEdgeCasesTests |
| `metadata/` | MetadataClientTests, IdempotencyTests, IdempotencyStrategyTests |
| `processing/` | JSONJQTests, GraaljsTests, SetVariableTests, ConcurrentExecLimitTests, BackoffTests |
| `event/` | WebhookTests, OnStateChangeTests |

### Excluded (enterprise-only)

- **RBAC:** `*PermissionTests`, `AuthorizationClientTests`, `ResourceSharingTests`, `AbstractMultiUserTests`
- **API Gateway:** all `api_gateway/*` tests
- **API Orchestration:** all `apiorch/*` tests (bulkhead, circuit breaker, hedging)
- **AI integrations:** all `external/ai/*` tests
- **Enterprise tasks:** `JWTTaskTest`, `SecretClientTests`, `HumanTaskSearchE2ETest`
- **NATS-specific:** `EventHandlerNatsE2ETest`, `NatsEventTests`, `OnStateChangeNATSTests`
- **S3/archival:** `PostgresPrimaryS3ArchivalE2ETest`

## build.gradle

```groovy
plugins {
    id 'java'
}

dependencies {
    testImplementation 'org.conductoross:conductor-client:5.0.1'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.3'
    testRuntimeOnly  'org.junit.jupiter:junit-jupiter-engine:5.10.3'
    testImplementation 'org.awaitility:awaitility:4.2.0'
    testImplementation 'ch.qos.logback:logback-classic:1.5.6'
}

test {
    useJUnitPlatform()
    maxParallelForks = 4
    maxHeapSize = '2g'
    systemProperties System.properties

    // Skip during normal ./gradlew build; enable with RUN_E2E=true or -PrunE2E
    onlyIf { System.getenv('RUN_E2E') == 'true' || project.hasProperty('runE2E') }
}
```

## Test Runner Scripts

All scripts follow the same lifecycle: spin up → health check → run tests → tear down.

```bash
#!/bin/bash
set -e
COMPOSE_FILE="../docker/docker-compose.yaml"
export SERVER_ROOT_URI="http://localhost:8080"

docker compose -f $COMPOSE_FILE up -d

echo "Waiting for Conductor server..."
for i in $(seq 1 60); do
  curl -sf http://localhost:8080/health && break || true
  sleep 5
done

cd "$(dirname "$0")"
../gradlew :e2e:test -DSERVER_ROOT_URI=$SERVER_ROOT_URI "$@"
EXIT_CODE=$?

docker compose -f $COMPOSE_FILE down -v
exit $EXIT_CODE
```

`"$@"` passthrough allows targeted test runs: `./run_tests.sh --tests "io.conductor.e2e.workflow.*"`

## Key Adaptation Rules (orkes → oss)

1. Replace `OrkesClients.getWorkflowClient()` → `ApiUtil.WORKFLOW_CLIENT`
2. Drop all API key / JWT / multi-user setup
3. Remove `@Assumptions.assumeFalse(securityEnabled)` guards
4. Remove any `IntegrationClient`, `PromptClient`, AI resource setup from `BaseIntegrationE2ETest`
5. Retain all workflow JSON metadata files that tests reference
6. Keep `RegistrationUtil` and `SimpleWorker` (no auth changes needed)
