# Architecture — Remove the Legacy Agent Classifier Backfill

Single source of truth for this change. `removal-plan.md` and
`removal-testing.md` reuse the identifiers, file paths, and rules defined here
**verbatim**.

> Naming note: this repository already contains unrelated design docs named
> `architecture.md` and `testing.md` (the "Agent Worker" feature). This change
> set deliberately uses the `removal-` prefix so it does not clobber them.

## 1. Overview

Two review comments on the open PR define the entire scope:

1. > Fix the following failing test:
>    `deployment contract test > legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`
>    `java.lang.NoSuchMethodException: ...AgentService.backfillLegacyAgentExecutionClassifiers()`
2. > do not add back `backfillLegacyAgentExecutionClassifiers` method. This method
>    should not be added back.

Both comments resolve to a single decision:

> **The `backfillLegacyAgentExecutionClassifiers` method and everything that
> exists only to serve it are removed from production code. The test that
> reflectively invoked the method is removed with it. The method is NOT
> re-added.**

The test failed because it reaches for the method by reflection
(`getDeclaredMethod("backfillLegacyAgentExecutionClassifiers")`). Comment 1 asks
for the failure to stop; comment 2 forbids the obvious "add the method back" fix.
The only resolution satisfying both is to delete the method and delete the test
that depends on it. This reverses the prior commit `code_subtask add-backfill-method`.

### Non-goals

- No new production behavior, no replacement backfill, no migration utility.
- No refactoring of unrelated `AgentService` members.
- No changes to any module other than the two files in section 3.

## 2. Tech stack (as relevant to this change)

- **Language / build**: Java 21, Gradle. Format with `./gradlew spotlessApply`.
- **Production module**: embedded agent runtime.
- **Test module**: `test-harness` (integration tests).
- **Lombok**: `AgentService` uses `@RequiredArgsConstructor`, so the constructor
  is derived from its `private final` fields. Removing a `private final` field
  removes it from the constructor automatically — no constructor edit needed.

## 3. Complete file layout

This change edits exactly **two existing source files** and creates **no source
files**.

| File | Module | Action |
|---|---|---|
| `embedded agent runtime service` | embedded agent runtime | Delete the backfill method, its two private helpers, its two constants, and the one field + import that becomes dead as a result. |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java` | `test-harness` | Delete the `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds` test method and its now-unused `java.lang.reflect.Method` import. |

Design docs (this set), under `docs/design/`:

| Doc | Responsibility |
|---|---|
| `removal-architecture.md` | This file. Source of truth: decision, identifiers, dead-code rules. |
| `removal-plan.md` | File-by-file, line-anchored edit plan derived from this doc. |
| `removal-testing.md` | What the test change is and how to verify it compiles and passes. |

## 4. Shared contracts

### 4a. Identifiers to remove

All identifiers below live in `AgentService.java`, copied verbatim from the
current source. Line numbers are current positions (anchors, not guarantees).

| Kind | Identifier / signature | Current line |
|---|---|---|
| Method | `private void backfillLegacyAgentExecutionClassifiers()` | 420 |
| Helper | `private static int backfillVersionOf(Map<String, Object> metadata)` | 463 |
| Helper | `private void reindexAgentExecutions(String agentName)` | 476 |
| Constant | `private static final String the agent classifier backfill version = "agent_classifier_backfill_version";` | 64 |
| Constant | `private static final int the agent classifier backfill version_VALUE = 2;` | 68 |
| Field | `private final IndexDAO indexDAO;` | 75 |
| Import | the `IndexDAO` import backing the field above | imports block |

Remove the Javadoc/comment blocks attached to each of the above (the method
Javadoc at 407–419, the `reindexAgentExecutions` Javadoc at 471–475, and the two
comment lines above the `the agent classifier backfill version_VALUE` constant).

In the test file:

| Kind | Identifier / signature | Current line |
|---|---|---|
| Test method | `void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` (incl. `@Test`) | 295–338 |
| Import | `import java.lang.reflect.Method;` | 18 |

### 4b. Identifiers that MUST stay (do not delete)

This is the critical correctness constraint and the correction over any earlier
plan. Each identifier below is referenced by code **outside** the removed
backfill block, verified by repository-wide grep against the current source.
Removing any of them breaks compilation.

| Identifier | Still used at (outside the backfill block) |
|---|---|
| `WorkflowClassifiers` import + `WorkflowClassifiers.isAgent(...)` | `AgentService.java:316`, `:366` |
| `SearchResult` import | `:600`, `:728`, `:973`, `:1548`, `:1553`, `:1555` |
| `WorkflowSummary` import | `:600`, `:728`, `:973`, `:976`, plus search methods |
| `WorkflowModel` import | `:765`, `:780` |
| `executionDAO` field (`ExecutionDAO`) | `:765`, `:767`, `:780`, `:782` |
| `workflowService` field (`WorkflowService`) | throughout the class |

> Any plan that proposes removing `WorkflowClassifiers`, `SearchResult`,
> `WorkflowSummary`, `WorkflowModel`, or `executionDAO` is **wrong for the
> current source**. They are live.

### 4c. The dead-code rule (governs every deletion)

Remove a helper, constant, field, or import **only** when its last remaining
reference is one of the members already scheduled for deletion in 4a. Confirm
each "last reference" with a repository-wide search immediately before deleting.

Grep against the current source establishes exactly one field — `indexDAO` — as
newly dead:

- `indexDAO` is referenced only inside `reindexAgentExecutions`
  (`AgentService.java:513`, `indexDAO.indexWorkflow(...)`). Once that helper is
  gone, the field and its `IndexDAO` import are orphaned and are removed.

If a future grep contradicts these findings, the dead-code rule wins over the
tables above: keep anything with a surviving reference; delete only what is
truly orphaned.

### 4d. Naming convention

No new names are introduced. This change is subtractive only.

## 5. Correctness argument

- Comment 1 (failing test) is satisfied: the test is deleted, so its
  `NoSuchMethodException` can no longer occur.
- Comment 2 (do not re-add the method) is satisfied: the method is removed and
  stays removed; nothing re-introduces it.
- Compilation is preserved because 4b enumerates every reference that survives,
  and 4c removes only genuinely orphaned members (`indexDAO` + its import).

## 6. Change budget

Two source files edited, zero source files created, zero production behaviors
added. This is the minimal set that satisfies both review comments at once.
