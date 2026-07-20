# Testing — Remove the legacy agent-classifier backfill

Verification plan for the removal described in [architecture.md](./architecture.md) and
[change-spec.md](./change-spec.md). Symbol and file names match those documents verbatim.

## Why the failing test is deleted, not repaired

The failing test `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` exists
only to exercise `backfillLegacyAgentExecutionClassifiers()` via reflection:

```java
Method backfill =
        AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
backfill.setAccessible(true);
backfill.invoke(agentService);
```

Since reviewer comment (2) forbids re-adding the method, the behavior under test no
longer exists. A test that asserts on a deliberately removed capability has no valid
subject, so it is deleted rather than rewritten. There is no replacement assertion:
nothing in the retained code performs classifier backfill, so there is nothing new to
cover.

## Static verification (must all hold after the edits)

Run from the repository root:

| Check | Expected |
|---|---|
| `grep -R "backfillLegacyAgentExecutionClassifiers" .` | no matches |
| `grep -R "reindexAgentExecutions" .` | no matches |
| `grep -R "backfillVersionOf" .` | no matches |
| `grep -R "AGENT_CLASSIFIER_BACKFILL_VERSION" .` | no matches |
| `grep -n "indexDAO\|IndexDAO" agentspan/.../service/AgentService.java` | no matches |
| `grep -n "java.lang.reflect.Method" test-harness/.../AgentSpanDeploymentContractEndToEndTest.java` | no matches |

## Build & test verification

| Command | Expected result |
|---|---|
| `./gradlew spotlessApply` | Reformats touched files; no manual formatting needed. |
| `./gradlew :agentspan:compileJava` | Compiles — confirms the `IndexDAO` import/field removal left no dangling reference and the Lombok constructor still resolves. |
| `./gradlew :test-harness:compileTestJava` | Compiles — confirms the `Method` import removal left no dangling reference. |
| `./gradlew :test-harness:test --tests "com.netflix.conductor.test.integration.agent.AgentSpanDeploymentContractEndToEndTest"` | Passes; the `legacyAgentClassifierBackfill...` case is no longer present, so it can neither fail nor error with `NoSuchMethodException`. |

## Regression surface

The removal is subtractive and touches no production caller (see the
[Reachability analysis](./architecture.md#reachability-analysis)). The remaining tests
in `AgentSpanDeploymentContractEndToEndTest` — deployment, metadata stamping, callback
placement, and the sibling classifier/routing cases — are unaffected because they do not
reference any removed symbol. No new test is added, consistent with a
capability-removal change.

## Acceptance criteria

1. Both reviewer comments are satisfied: the backfill method is **not** present, and the
   test suite no longer fails with `NoSuchMethodException`.
2. `agentspan` and `test-harness` compile.
3. `AgentSpanDeploymentContractEndToEndTest` passes with the backfill case removed.
4. All static-verification greps return no matches.
