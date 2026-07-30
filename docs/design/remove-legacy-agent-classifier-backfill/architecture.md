# Architecture — Remove the legacy agent-classifier backfill

> This is the single source of truth for this change. Supporting docs
> ([change-spec.md](./change-spec.md), [testing.md](./testing.md)) reuse the names,
> symbols, and file layout defined here verbatim.
>
> Note: this document set lives in its own subdirectory to avoid colliding with the
> unrelated top-level `docs/design/architecture.md` (Agent Worker Architecture), which
> is a different, pre-existing document and is not modified by this change.

## Purpose

This design covers a **removal / cleanup** change that resolves review feedback on an
open pull request. The change is strictly subtractive: it deletes code and one test.

### Review feedback being addressed

Two comments from `@v1r3n` on the PR:

1. A failing integration test:

   ```
   AgentSpanDeploymentContractEndToEndTest > legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() FAILED
       java.lang.NoSuchMethodException:
       org.conductoross.conductor.ai.agentspan.runtime.service.AgentService.backfillLegacyAgentExecutionClassifiers()
           at AgentSpanDeploymentContractEndToEndTest.java:328
   ```

2. > do not add back `backfillLegacyAgentExecutionClassifiers` method. This method
   > should not be added back.

### Interpretation (the design decision)

The previous commit (`c1cfaf255 code_subtask add-backfill-method`) re-introduced a
116-line private method `backfillLegacyAgentExecutionClassifiers()` on `AgentService`,
plus its private helpers and constants. The reviewer explicitly rejects re-adding this
method. The test at line 328 reflectively looks the method up, so with the method
absent the test throws `NoSuchMethodException`.

There are two internally consistent ways to satisfy both comments, and only one is
correct:

- ~~Re-add the method so the test passes~~ — **rejected**: comment (2) forbids it.
- **Remove the backfill code entirely AND remove the test that depends on it** —
  **chosen**: satisfies both comments and leaves no dead code or dangling test.

Therefore the change is: **delete the backfill method, its now-dead private helpers,
its now-dead constants and field, the import that becomes unused, and delete the test
that reflected on it** — nothing else.

## Scope

- **In scope:** removing the backfill method, its transitive private-only helpers
  (`reindexAgentExecutions`, `backfillVersionOf`), the two constants and the one field
  that become unused *only because of* this removal, the now-unused `IndexDAO` import,
  and the single failing test method plus its now-unused `Method` import.
- **Out of scope:** any production caller wiring (there is none — see
  [Reachability analysis](#reachability-analysis)), the behavior of the execution
  classifier index itself, migrations, and any other test.

## Tech stack

Unchanged by this design. For context only:

| Concern | Technology |
|---|---|
| Language / build | Java 21, Gradle |
| Module under change (prod) | `agentspan` |
| Module under change (test) | `test-harness` |
| DI / lifecycle | Spring Boot (`@Component`, `@RequiredArgsConstructor`) |
| Test framework | JUnit 5 (`@Test`), Testcontainers-backed integration harness |
| Formatting gate | Spotless (`./gradlew spotlessApply`) |

## Reachability analysis

`backfillLegacyAgentExecutionClassifiers()` is `private` and is invoked **only**
reflectively by the one failing test. A repository-wide search for the symbol returns
exactly two files — the production class that declares it and the test that reflects on
it:

```
agentspan/.../runtime/service/AgentService.java
test-harness/.../integration/agent/AgentSpanDeploymentContractEndToEndTest.java
```

There is **no Spring bean method, scheduled task, startup listener, REST endpoint, or
other production caller**. Removing it therefore changes no production behavior. Its two
private helpers and the `indexDAO` field are used *only* from inside the backfill path
(the sole `indexDAO` usage is on the `indexWorkflow` line inside
`reindexAgentExecutions`), so they become dead on removal and are removed with it.

## File layout

No files are created in the source tree. Two existing source files are edited; both
edits are deletions.

| File | Change |
|---|---|
| `agentspan/src/main/java/org/conductoross/conductor/ai/agentspan/runtime/service/AgentService.java` | Delete the backfill method, its two private helpers, the two constants (and their comment), the `indexDAO` field, and the now-unused `IndexDAO` import. |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/agent/AgentSpanDeploymentContractEndToEndTest.java` | Delete the `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` test method and its now-unused `java.lang.reflect.Method` import. |

The exact, line-anchored edits are enumerated in [change-spec.md](./change-spec.md).
The verification and test rationale are in [testing.md](./testing.md).

## Shared contracts (symbols removed — referenced verbatim by every doc)

Every other document in this set refers to these symbols with exactly these names.

### Symbols deleted from `AgentService`

| Symbol | Declared kind | Removal reason |
|---|---|---|
| `backfillLegacyAgentExecutionClassifiers()` | `private void` method | Rejected by reviewer; only caller is the deleted test. |
| `reindexAgentExecutions(String agentName)` | `private void` method | Called only by the backfill method. |
| `backfillVersionOf(Map<String, Object> metadata)` | `private static int` method | Called only by the backfill method. |
| `AGENT_CLASSIFIER_BACKFILL_VERSION` | `private static final String` = `"agent_classifier_backfill_version"` | Read only by the removed methods. |
| `AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE` | `private static final int` = `2` | Read only by the removed methods. |
| `indexDAO` | `private final IndexDAO` field | Used only on the `indexWorkflow` line inside `reindexAgentExecutions`. |
| `import com.netflix.conductor.dao.IndexDAO;` | import | Becomes unused once the field is removed. |

### Symbols deleted from `AgentSpanDeploymentContractEndToEndTest`

| Symbol | Declared kind | Removal reason |
|---|---|---|
| `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` | `@Test void` method | Depends on the removed backfill method via reflection. |
| `import java.lang.reflect.Method;` | import | Its only use is inside the removed test method. |

### Symbols deliberately KEPT (do not touch)

These share types/imports with the removed code but are used elsewhere, so they must
remain:

- Imports `com.netflix.conductor.common.run.SearchResult`,
  `com.netflix.conductor.common.run.WorkflowSummary`,
  `com.netflix.conductor.model.WorkflowModel` — still used by other `AgentService`
  methods (search / execution lookups).
- The `metadataDAO` field and `WorkflowClassifiers` usage — used across compile/deploy.
- No data migration is required: the `agent_classifier_backfill_version` metadata stamp
  was only ever written by the removed code path; leaving stray stamps on old defs is
  harmless because nothing reads them after this change.

## Constraints & conventions

- **Purely subtractive.** Do not add any replacement method, comment, shim, or
  `@Deprecated` marker.
- **No behavior change** to any retained method; deletions must leave the remaining
  code compiling and semantically identical.
- Run `./gradlew spotlessApply` after editing (constructor/field ordering and import
  ordering are Spotless-formatted).
- Do not remove `SearchResult`, `WorkflowSummary`, or `WorkflowModel` imports — they
  remain in use.
