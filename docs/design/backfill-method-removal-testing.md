# Testing

Test strategy for the change in
[`backfill-method-removal-architecture.md`](./backfill-method-removal-architecture.md).
This is a removal, so the testing goal is to prove that (a) nothing depended on
the removed code and (b) the previously failing suite is now green with the
offending test gone.

## 1. What is removed and why no replacement is added

The deleted test —
`AgentSpanDeploymentContractEndToEndTest.legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds`
— asserted the behavior of `backfillLegacyAgentExecutionClassifiers`, a
`private` maintenance routine reached only via reflection. Per the reviewer, that
method must not exist. There is no public method, REST endpoint, CLI command, or
SDK call that exposes the behavior, so there is **no remaining contract to
assert** and no replacement test is written. Adding one would require
reintroducing the very method the reviewer rejected.

## 2. Regression checks (must pass)

Run the affected integration suite with the removed test absent:

```bash
./gradlew :test-harness:test --tests \
  "com.netflix.conductor.test.integration.agent.AgentSpanDeploymentContractEndToEndTest"
```

Expected: build succeeds; the class runs with the backfill test no longer
present, and every remaining test in the class passes. The prior failure

```
legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() FAILED
    java.lang.NoSuchMethodException: ...AgentService.backfillLegacyAgentExecutionClassifiers()
```

no longer appears because the test that produced it is gone.

Run the `agentspan` unit tests to confirm the production removal broke nothing:

```bash
./gradlew :agentspan:test
```

Expected: pass. No existing `agentspan` test references `backfill*`,
`reindexAgentExecutions`, or `indexDAO` on `AgentService` (verified by the grep
in `backfill-method-removal-plan.md` §3).

## 3. Compilation as a test of dead-code removal

Because Lombok's `@RequiredArgsConstructor` regenerates the constructor when the
`indexDAO` field is removed, a clean compile is itself evidence that no code
depended on that field or on `IndexDAO` being an `AgentService` constructor
argument:

```bash
./gradlew :agentspan:compileJava :test-harness:compileTestJava
```

Expected: both compile with no unused-import or missing-symbol errors.

## 4. Formatting

```bash
./gradlew spotlessApply
```

Expected: no diff produced by the formatter after the edits.

## 5. Definition of done

Aligned with `backfill-method-removal-architecture.md` §7:

1. Repo-wide grep for `backfillLegacyAgentExecutionClassifiers`,
   `backfillVersionOf`, `reindexAgentExecutions`, and
   `AGENT_CLASSIFIER_BACKFILL_VERSION` under `agentspan/src` and
   `test-harness/src` returns nothing.
2. `AgentSpanDeploymentContractEndToEndTest` compiles and passes without the
   removed test.
3. `:agentspan:test` passes.
4. `spotlessApply` leaves the tree clean.
