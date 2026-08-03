# Architecture — Remove the legacy agent classifier backfill

## Overview

This change reverts an accidentally re-introduced feature. A prior commit
(`code_subtask add-backfill-method`) added a private maintenance routine,
`AgentService.backfillLegacyAgentExecutionClassifiers()`, together with its
supporting helpers and an end-to-end test that invokes the method via
reflection.

Reviewer feedback on the open PR is unambiguous:

> do not add back `backfillLegacyAgentExecutionClassifiers` method. This method
> should not be added back.

The CI failure the reviewer pointed at —

```
AgentSpanDeploymentContractEndToEndTest > legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() FAILED
    java.lang.NoSuchMethodException: org.conductoross.conductor.ai.agentspan.runtime.service.AgentService.backfillLegacyAgentExecutionClassifiers()
```

— is the symptom of a half-applied revert: the test still references the method
by name. The correct resolution is **not** to add the method back so the test
compiles, but to remove the entire feature — production code *and* test — so the
codebase converges on the reviewer's intended end state.

This is a deletion-only change. No new behavior, types, or files are
introduced. The design below enumerates exactly what to remove and the
compile-time fallout each removal creates, so the change is complete and leaves
no dangling references or unused imports.

The supporting docs in this folder — [implementation-plan.md](./implementation-plan.md)
and [testing.md](./testing.md) — reuse the exact symbol names defined here.

## Tech stack (unchanged)

- Java 21, Gradle multi-module build.
- `agentspan` module: Spring `@Component` beans, Lombok
  (`@RequiredArgsConstructor`, `@Slf4j`), constructor injection of DAOs.
- `test-harness` module: JUnit 5 integration tests running against real
  Conductor beans (no mocks).

## Scope

Two files are touched. Nothing else in the repository references the removed
symbols (verified: `new AgentService(` appears nowhere, and the removed
method/helpers are `private`).

| File | Module | Action |
|---|---|---|
| `agentspan/src/main/java/org/conductoross/conductor/ai/agentspan/runtime/service/AgentService.java` | `agentspan` | Remove the backfill method, its private helpers, its constants, and the now-orphaned `IndexDAO` dependency. |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/agent/AgentSpanDeploymentContractEndToEndTest.java` | `test-harness` | Remove the failing test method and its now-unused import. |

## Symbols removed (the shared contract for this change)

Every symbol below is deleted. All names are reproduced verbatim so the two
supporting docs reference the identical strings.

### In `AgentService.java`

Private constants (currently lines ~64–68):

- `AGENT_CLASSIFIER_BACKFILL_VERSION` — the metadata key
  `"agent_classifier_backfill_version"`.
- `AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE` — the `int` version stamp (`2`),
  and its explanatory comment about router sub-workflow reindexing.

Private methods:

- `void backfillLegacyAgentExecutionClassifiers()` (currently ~407–460),
  including its Javadoc.
- `static int backfillVersionOf(Map<String, Object> metadata)` (currently
  ~462–469) — only ever called by the backfill method.
- `void reindexAgentExecutions(String agentName)` (currently ~471–521) — only
  ever called by the backfill method.

Dependency and import that become orphaned once `reindexAgentExecutions` is
gone:

- Field `private final IndexDAO indexDAO;` (currently line 75). Because the
  class uses Lombok `@RequiredArgsConstructor`, deleting the `final` field also
  drops it from the generated constructor; Spring simply stops injecting it. No
  hand-written constructor exists to update.
- Import `com.netflix.conductor.dao.IndexDAO` (currently line 46).

### In `AgentSpanDeploymentContractEndToEndTest.java`

- Test method `void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`
  (currently ~295–337), including its `@Test` annotation. This is the method
  that reflects into the removed production method:

  ```java
  Method backfill =
          AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
  backfill.setAccessible(true);
  backfill.invoke(agentService);
  ```

- Import `java.lang.reflect.Method` (currently line 18) — used only by the
  reflection lookup inside the removed test.

## Symbols explicitly RETAINED (do not delete)

These are shared by the module and only *appeared* near the removed code; they
must survive:

- `AgentService` field `private final ExecutionDAO executionDAO;` — used by
  the pause/resume execution paths (lines ~765, ~780).
- Imports `com.netflix.conductor.common.run.SearchResult`,
  `com.netflix.conductor.common.run.WorkflowSummary`,
  `com.netflix.conductor.model.WorkflowModel`,
  `org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowClassifiers`,
  and `java.util.ArrayList` — all still referenced elsewhere in the class.
- In the test: `import org.conductoross...service.AgentService` and the
  `@Autowired private AgentService agentService;` field — other tests in the
  class exercise `agentService.deploy(...)` / `agentService.start(...)`.
- In the test: `@Autowired private ExecutionDAO executionDAO;` — used by
  unrelated tests in the same class.

## Naming conventions

No new names are created. The change is defined entirely by the delete-list
above; correctness is "the two files compile, the module builds, and no unused
import or dead private member referencing the backfill remains."

## Rationale for full removal over test-only fix

Two candidate fixes exist:

1. Delete only the test. This makes CI green but leaves
   `backfillLegacyAgentExecutionClassifiers()` and its helpers as dead,
   unreachable private code — directly contradicting the reviewer's "should not
   be added back."
2. Delete the whole feature (chosen). Both the method and its only caller (the
   test) disappear together, matching the reviewer's intent and leaving no dead
   code.

Option 2 is the minimal change that satisfies *all* the feedback rather than
just the visible failure.

## Verification (see [testing.md](./testing.md))

- `./gradlew :agentspan:compileJava` — confirms no orphaned reference in
  production code.
- `./gradlew :test-harness:compileTestJava` — confirms the test file compiles
  without `java.lang.reflect.Method`.
- `./gradlew spotlessApply` — formatting after edits.
- Full `./gradlew test` — confirms the previously failing suite passes and no
  other test regressed.
