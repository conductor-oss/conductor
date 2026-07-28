# Testing — After Removing the Backfill

Test-facing consequences of the removal described in
`agent-classifier-backfill-removal-architecture.md`. Names and paths match that
document exactly.

## Why the failing test is deleted, not repaired

The CI failure is:

```
deployment contract test > legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() FAILED
    java.lang.NoSuchMethodException: embedded agent runtime service.backfillLegacyAgentExecutionClassifiers()
```

The test obtains the method reflectively:

```java
Method backfill =
        AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
```

There are only two ways to make it green:

1. Re-add `backfillLegacyAgentExecutionClassifiers()` — **explicitly forbidden**
   by review.
2. Delete the test.

Option 1 contradicts the reviewer, so option 2 is the only consistent fix. The
test exists solely to characterize the forbidden method; with the method gone it
has no subject and is removed with it.

## What remains covered

`deployment contract test` keeps all other cases unchanged,
including the deployment-contract assertions immediately preceding the deleted
test (task-def presence/absence for guardrails, handoff checks, and transfer
tasks) and the SDK lifecycle-callback boundary test that follows it. Deployment
and classifier stamping on the normal `deploy()` path are unaffected by this
change and continue to be exercised by those tests.

No new test is added: the change removes a capability, and the design goal is a
minimal, review-scoped edit rather than a feature.

## Verification steps (design-level)

Run from the repository root:

| Step | Command | Expected |
|---|---|---|
| Module compiles without the removed members | `./gradlew embedded agent runtime module (compileJava)` | Success; no "unused" or "cannot find symbol" errors. |
| End-to-end contract tests pass | `./gradlew :test-harness:test --tests '*deployment contract test*'` | Green; the deleted test is absent and no `NoSuchMethodException` is thrown. |
| Formatting normalized | `./gradlew spotlessApply` | No diff on re-run. |

## Regression guard

The consistency invariants in
`agent-classifier-backfill-removal-architecture.md` are the acceptance criteria:
after the change, no `AgentService` member named or aliased `backfill*Classifiers`
exists, and no test references such a member. A repository-wide search for
`backfillLegacyAgentExecutionClassifiers` must return zero results in both
embedded agent runtime and `test-harness`.
