# Architecture — Remove `backfillLegacyAgentExecutionClassifiers` and fix its failing test

> Source of truth for this change set. `backfill-method-removal-plan.md` and
> `backfill-method-removal-testing.md` reuse the names, file paths, and
> identifiers defined here verbatim.
>
> Note: this repo already contains unrelated design docs named
> `architecture.md` and `testing.md` (Agent Worker). This change set uses the
> `backfill-method-removal-*` prefix to avoid clobbering them.

## 1. Overview

This is a **removal / cleanup** change, not a feature. It reverses a prior
commit (`code_subtask add-backfill-method`) that reintroduced a private
maintenance method purely to satisfy a reflection-based end-to-end test.

The reviewer's decision is explicit and takes precedence over the test:

- @v1r3n: *"do not add back `backfillLegacyAgentExecutionClassifiers` method.
  This method should not be added back."*

The originally reported failure —

```
deployment contract test > legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() FAILED
    java.lang.NoSuchMethodException: embedded agent runtime service.backfillLegacyAgentExecutionClassifiers()
```

— must therefore be resolved by **removing the test's dependency on the
method**, not by keeping the method alive. When both changes land together the
suite is green and the method stays gone.

### Guiding principle

The method is **dead production code**: it is `private`, is not called from any
production path (no `@PostConstruct`, no scheduler, no controller, no other
service), and its only caller is the test invoking it reflectively. Removing the
dead code plus the single test that pins it in place is the minimal, correct
resolution. No new behavior is introduced.

### Tech stack (unchanged)

- Java 21, Gradle multi-module build.
- Modules touched: embedded agent runtime (production) and `test-harness` (integration test).
- Spring Boot component model; `AgentService` is a `@Component` gated by
  `@ConditionalOnPropertyembedded agent runtime is enabled`
  and uses Lombok `@RequiredArgsConstructor`.
- JUnit 5 (`org.junit.jupiter`) for the affected test.

## 2. Scope — exact files to change

Only two files are edited. No source files are created. No new interfaces or types.

| File | Module | Action |
|---|---|---|
| `embedded agent runtime service` | embedded agent runtime | Delete the backfill method and everything used **only** by it (see §3). |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java` | `test-harness` | Delete the `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` test and its now-unused import (see §4). |

Nothing else in the repository references the removed symbols, so no other file
needs to change.

## 3. `AgentService.java` — members to remove

All of the following are removed. Each is verified (see the plan doc) to have
**no remaining reference** once the backfill method and the test are gone.

### 3.1 Methods

- `private void backfillLegacyAgentExecutionClassifiers()`
  — currently at `AgentService.java:420`. The Javadoc block immediately above it
  (starting `/** Backfill the agent execution classifier index ...`, currently
  `AgentService.java:407`) is removed with it.
- `private static int backfillVersionOf(Map<String, Object> metadata)`
  — currently at `AgentService.java:463`, plus its `/** Read the stored backfill
  version ... */` Javadoc. Only caller is the backfill method.
- `private void reindexAgentExecutions(String agentName)`
  — currently at `AgentService.java:476`, plus its Javadoc. Only caller is the
  backfill method.

### 3.2 Constants

- `private static final String the agent classifier backfill version = "agent_classifier_backfill_version";`
  — currently at `AgentService.java:64`.
- `private static final int the agent classifier backfill version_VALUE = 2;`
  — currently at `AgentService.java:68`, plus its explanatory comment.

Both constants are referenced **only** from the three methods above.

### 3.3 Field and import that become unused

Removing `reindexAgentExecutions` drops the sole use of the `IndexDAO`
dependency, so both of these are removed as well:

- Field `private final IndexDAO indexDAO;` — currently at `AgentService.java:75`.
- Import `import com.netflix.conductor.dao.IndexDAO;` — currently at
  `AgentService.java:46`.

> `AgentService` uses `@RequiredArgsConstructor` (Lombok). Removing the `final`
> field also removes it from the generated constructor. This is safe because
> `indexDAO` is Spring-injected and no test or production code depends on it
> being a constructor parameter of `AgentService`.

### 3.4 Members that MUST be kept

These are referenced elsewhere in `AgentService` and are **not** removed:

- Imports `SearchResult`, `WorkflowSummary`, `WorkflowModel` — still used by
  `searchExecutionsRaw`, `searchAgentExecutions`, and other methods (references
  at `AgentService.java:600, 728, 765, 780, 973, 1548, 1553`).
- Fields `executionDAO`, `metadataDAO`, `workflowService`, `taskService`,
  `workflowExecutor`, and all remaining collaborators.

## 4. `deployment contract test.java` — test to remove

- Delete the entire test method
  `void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() throws Exception`
  — currently at `deployment contract test.java:295`–`338`,
  including its `@Test` annotation.
- Remove the import `import java.lang.reflect.Method;`
  (`deployment contract test.java:18`) **only if** no other test
  method in the file uses `java.lang.reflect.Method`. It is verified unused
  elsewhere in the file; keep the `AgentService` import, which the class still
  autowires and uses.

No replacement test is added: the behavior it exercised (a private,
production-unreachable maintenance routine) no longer exists, so there is no
public contract left to assert.

## 5. Shared identifiers (use verbatim)

Every document in this set and every edit refers to these exact names:

| Concept | Exact identifier |
|---|---|
| Removed production method | `backfillLegacyAgentExecutionClassifiers` |
| Removed helper (version read) | `backfillVersionOf` |
| Removed helper (reindex) | `reindexAgentExecutions` |
| Removed constant (metadata key) | `the agent classifier backfill version` |
| Removed constant (version value) | `the agent classifier backfill version_VALUE` |
| Removed field | `indexDAO` (type `com.netflix.conductor.dao.IndexDAO`) |
| Removed test method | `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds` |
| Production class | `embedded agent runtime service` |
| Test class | `deployment contract test` |
| Metadata string literal (workflow def) | `"agent_classifier_backfill_version"` |

## 6. Non-goals

- Do **not** re-add the backfill method under any name or visibility.
- Do **not** introduce a public API, endpoint, or CLI command to replace it.
- Do **not** touch other tests in `deployment contract test`.
- Do **not** migrate or rewrite existing `"agent_classifier_backfill_version"`
  metadata already stamped on deployed definitions; leaving stale metadata is
  harmless (nothing reads it after this change).

## 7. Convergence checklist

The change is complete when all of the following hold:

1. `AgentService.java` contains zero occurrences of `backfill` (method, helpers,
   constants) and of the `indexDAO` field / `IndexDAO` import.
2. `deployment contract test.java` contains zero occurrences of
   `backfill` and no `getDeclaredMethod(...)` referencing the removed method.
3. Both modules compile; `./gradlew spotlessApply` leaves no diff.
4. `deployment contract test` runs with the removed test absent
   and every remaining test passing.
