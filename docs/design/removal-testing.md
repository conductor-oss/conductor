# Testing — Legacy Agent Classifier Backfill Removal

How the test change is made and how to verify the result. Identifiers and paths
are used exactly as defined in [`removal-architecture.md`](./removal-architecture.md);
the concrete edits are in [`removal-plan.md`](./removal-plan.md).

## 1. The failing test and why it is deleted (not fixed in place)

The failure reported in review is:

```
Gradle Test Executor 2 > AgentSpanDeploymentContractEndToEndTest > legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() FAILED
    java.lang.NoSuchMethodException: org.conductoross.conductor.ai.agentspan.runtime.service.AgentService.backfillLegacyAgentExecutionClassifiers()
        at java.base/java.lang.Class.getDeclaredMethod(Class.java:2850)
        at com.netflix.conductor.test.integration.agent.AgentSpanDeploymentContractEndToEndTest.legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds(AgentSpanDeploymentContractEndToEndTest.java:328)
```

The test exists **only** to exercise `backfillLegacyAgentExecutionClassifiers`,
which it invokes reflectively:

```java
Method backfill =
        AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
backfill.setAccessible(true);
backfill.invoke(agentService);
```

The reviewer forbids re-adding the method, so there is nothing left for the test
to assert. Deleting the test is the correct fix; keeping it would either fail
(method absent) or force the forbidden method back. See `removal-architecture.md`
§1.

## 2. Test edit

Per `removal-plan.md` §2:

- Delete the method `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds`
  (`@Test` at current line 295 through its closing brace at line 338).
- Delete `import java.lang.reflect.Method;` (current line 18), which becomes
  unused once the test is gone.

No other test in `AgentSpanDeploymentContractEndToEndTest` references the backfill
method, the `Method` type, or the `agent_classifier_backfill_version` metadata key,
so no other test needs to change.

## 3. What is NOT re-tested

There is no replacement test. The removed method was maintenance-only code with no
surviving caller, so there is no production behavior left to cover. Adding a new
test would require reintroducing the method, which the review explicitly forbids.

## 4. Verification steps

Run in order; each must pass before the change is considered complete.

1. **Formatting**

   ```
   ./gradlew spotlessApply
   ```

2. **Production compiles** (proves the field/import removal in §1d of the plan is
   consistent and nothing else referenced the deleted members):

   ```
   ./gradlew :agentspan:compileJava
   ```

3. **Test module compiles** (proves the deleted test left no dangling references):

   ```
   ./gradlew :test-harness:compileTestJava
   ```

4. **The former failure is gone.** The class must no longer contain
   `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds`; run the
   remaining tests in the class:

   ```
   ./gradlew :test-harness:test --tests "com.netflix.conductor.test.integration.agent.AgentSpanDeploymentContractEndToEndTest"
   ```

   Expected: the suite runs without the deleted test and without a
   `NoSuchMethodException`.

## 5. Regression guard

A grep-based sanity check after editing, expecting **zero** matches in production
source, confirms the method and its exclusive members are gone:

```
grep -rn "backfillLegacyAgentExecutionClassifiers\|AGENT_CLASSIFIER_BACKFILL_VERSION\|reindexAgentExecutions\|backfillVersionOf" agentspan/src/main
```

The metadata key string `agent_classifier_backfill_version` may still appear in
other modules or docs; only its use inside `AgentService.java` is removed.
