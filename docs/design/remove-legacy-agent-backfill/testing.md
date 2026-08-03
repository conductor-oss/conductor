# Testing & Verification — Remove the Legacy Agent Classifier Backfill

Verification plan for the change described in [`architecture.md`](architecture.md) and
executed by [`change-plan.md`](change-plan.md). All symbol names are reused verbatim from
architecture.md §5.

## 1. What the failing test was

`AgentSpanDeploymentContractEndToEndTest.legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`
deployed a legacy agent, stripped the `agent_classifier_backfill_version` metadata key,
then reflectively invoked the private method:

```java
Method backfill =
        AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
backfill.setAccessible(true);
backfill.invoke(agentService);
```

and asserted the metadata key was re-stamped to `2`. It failed with
`NoSuchMethodException` whenever the method was absent. Per the review, the method must
stay absent, so this test is **deleted**, not repaired (see architecture.md §6).

## 2. Completion checks (must all pass)

### 2.1 Forbidden identifier is gone everywhere

A repository-wide search for the method name must return **no results** in any `.java`
file:

- Search term: `backfillLegacyAgentExecutionClassifiers`
- Expected: 0 matches (production and test).

### 2.2 Backfill metadata key is gone from both touched source files

- Search term: `agent_classifier_backfill_version`
- Expected: 0 matches in `AgentService.java` and in
  `AgentSpanDeploymentContractEndToEndTest.java`. (Both its producer and its consumer are
  removed.)

### 2.3 Helpers and constants are gone from `AgentService.java`

- `AGENT_CLASSIFIER_BACKFILL_VERSION` — 0 matches.
- `AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE` — 0 matches.
- `backfillVersionOf` — 0 matches.
- `reindexAgentExecutions` — 0 matches.

### 2.4 Orphaned collaborator removed cleanly

- `indexDAO` — 0 matches in `AgentService.java` (field and import both removed).
- `IndexDAO` import — 0 matches in `AgentService.java`.
- **Retained (must still be present and used):** `workflowService`, `executionDAO`,
  `metadataDAO`.

### 2.5 Unused test import removed

- `java.lang.reflect.Method` — 0 matches in
  `AgentSpanDeploymentContractEndToEndTest.java`.

## 3. Build / test gates

Because the change is deletion-only, the goal is to prove nothing else regressed:

| Gate | Command | Expectation |
|---|---|---|
| Formatting | `./gradlew spotlessApply` | No manual fixups needed afterward. |
| Production compiles | `./gradlew :agentspan:compileJava` | Success; no unused-symbol or missing-symbol errors from the removals. |
| agentspan tests | `./gradlew :agentspan:test` | All remaining tests pass. |
| End-to-end contract suite | `./gradlew :test-harness:test --tests "com.netflix.conductor.test.integration.agent.AgentSpanDeploymentContractEndToEndTest"` | Class compiles without the reflection import; all remaining tests pass; the deleted test no longer runs. |

## 4. Regression guard for the review instruction

The reviewer's second instruction — "do not add `backfillLegacyAgentExecutionClassifiers`
back" — is a standing constraint, not a one-time action. The check in §2.1 is the
guard: any future PR that reintroduces the identifier (in production or as a
reflectively-invoked test) violates the review decision and should be rejected. This
document records that the correct resolution of the original `NoSuchMethodException` is
to **remove the caller**, never to restore the callee.

## 5. Why no new test is added

No behavior is introduced, so there is nothing new to assert. Adding a test that checks
"the method does not exist" would re-encode the forbidden symbol in source (failing the
§2.1 check) and is therefore explicitly avoided. The existing, unrelated tests in
`AgentSpanDeploymentContractEndToEndTest` continue to cover agent deployment and
execution behavior.
