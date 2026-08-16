# Implementation Plan — Remove the legacy agent classifier backfill

This plan is the step-by-step realization of `architecture.md`. It uses the
exact symbol names listed there. It is a deletion-only change across two files.

## Step 1 — `AgentService.java`

Path:
`agentspan/src/main/java/org/conductoross/conductor/ai/agentspan/runtime/service/AgentService.java`

Perform these deletions:

1. **Constants block.** Remove `AGENT_CLASSIFIER_BACKFILL_VERSION` and
   `AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE` (and the two-line comment above the
   latter). Keep `MAPPER` and the surrounding declarations.

   ```java
   // DELETE:
   private static final String AGENT_CLASSIFIER_BACKFILL_VERSION =
           "agent_classifier_backfill_version";
   // Version 2 additionally reindexes generated router sub-workflows, which older compiler
   // output persisted as ordinary workflow executions.
   private static final int AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE = 2;
   ```

2. **`indexDAO` field.** Remove the field. `@RequiredArgsConstructor`
   regenerates the constructor without it.

   ```java
   // DELETE:
   private final IndexDAO indexDAO;
   ```

3. **`IndexDAO` import.** Remove:

   ```java
   // DELETE:
   import com.netflix.conductor.dao.IndexDAO;
   ```

4. **`backfillLegacyAgentExecutionClassifiers()`.** Remove the method and its
   full Javadoc (the block that begins `Backfill the agent execution classifier
   index for legacy agent definitions.`).

5. **`backfillVersionOf(Map<String, Object> metadata)`.** Remove the method and
   its one-line Javadoc. It has no other caller.

6. **`reindexAgentExecutions(String agentName)`.** Remove the method and its
   Javadoc. It is the sole user of `indexDAO`; removing it is what makes Step 2
   and Step 3 safe.

### Retention checklist for Step 1

Do **not** touch these — they remain in use elsewhere in the class:

- `private final ExecutionDAO executionDAO;` and its uses at ~765/~780.
- Imports `SearchResult`, `WorkflowSummary`, `WorkflowModel`,
  `WorkflowClassifiers`, `java.util.ArrayList`.

After the edits, `IndexDAO` and the two `AGENT_CLASSIFIER_BACKFILL_*` symbols
must not appear anywhere in the file.

## Step 2 — `AgentSpanDeploymentContractEndToEndTest.java`

Path:
`test-harness/src/test/java/com/netflix/conductor/test/integration/agent/AgentSpanDeploymentContractEndToEndTest.java`

1. **Remove the test method** `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`
   in full, including its `@Test` annotation. This is the method that calls:

   ```java
   Method backfill =
           AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
   backfill.setAccessible(true);
   backfill.invoke(agentService);
   ```

2. **Remove the now-unused import:**

   ```java
   // DELETE:
   import java.lang.reflect.Method;
   ```

### Retention checklist for Step 2

- Keep `import org.conductoross...service.AgentService;` and
  `@Autowired private AgentService agentService;` — other tests use them.
- Keep `@Autowired private ExecutionDAO executionDAO;` — other tests use it.
- Leave every other `@Test` method in the class untouched.

## Step 3 — Format and verify

Run, in order (see `testing.md` for the acceptance criteria):

```bash
./gradlew spotlessApply
./gradlew :agentspan:compileJava
./gradlew :test-harness:compileTestJava
./gradlew test
```

## Post-conditions

- `grep -rn "backfillLegacyAgentExecutionClassifiers" .` returns nothing.
- `grep -rn "AGENT_CLASSIFIER_BACKFILL_VERSION" .` returns nothing.
- `grep -rn "reindexAgentExecutions\|backfillVersionOf" .` returns nothing.
- No unused-import or unreachable-code warnings from the two edited files.
- The change diff contains only deletions (plus whatever whitespace
  normalization `spotlessApply` produces).
