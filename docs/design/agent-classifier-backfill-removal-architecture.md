# Architecture — Remove the Legacy Agent Classifier Backfill

## Status

Design for a review-driven change on an open pull request. This document is the
single source of truth; the supporting docs
(`agent-classifier-backfill-removal-plan.md` and
`agent-classifier-backfill-removal-testing.md`) reuse the names, method
signatures, and file paths defined here verbatim.

> Note: an unrelated `architecture.md` / `testing.md` already exist in this
> directory for the Agent Worker design. This change owns the
> `agent-classifier-backfill-removal-*` file set and does not touch those.

## Problem statement

The PR branch reintroduced a private backfill routine,
`AgentService.backfillLegacyAgentExecutionClassifiers()`, in commit
`add-backfill-method`, together with an end-to-end test that reflectively
invokes it. Review feedback is explicit and takes precedence over the test:

- @v1r3n: *"do not add back `backfillLegacyAgentExecutionClassifiers` method.
  This method should not be added back."*
- @v1r3n: the test
  `deployment contract test.legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`
  currently fails with `NoSuchMethodException` for that method and must be
  fixed.

These two statements are only mutually consistent one way: the method stays
removed, and the test that depends on it is removed as well. The test cannot be
"fixed" by restoring the method, because restoring the method is precisely what
the reviewer forbids. The correct resolution is therefore a **removal**, not an
addition.

## Scope and non-goals

This is a minimal, review-scoped change.

**In scope**

- Remove the `backfillLegacyAgentExecutionClassifiers()` method and every
  private helper and constant that exists **only** to serve it.
- Remove the end-to-end test that reflectively invokes the removed method.
- Remove any import or injected field that becomes unused **solely** as a result
  of the removals above.

**Out of scope**

- Any change to the compile/deploy classifier stamping that happens on the
  normal `deploy()` path.
- The unrelated task-name "backfill" helpers
  (`WorkflowTaskUtils.ensureAllTaskNames`, referenced by `MultiAgentCompiler`);
  these are a different concept and must not be touched.
- Adding a replacement backfill entry point of any kind.

## Overview & tech stack

- **Language / build**: Java 21, Gradle.
- **Affected runtime module**: embedded agent runtime — the embedded agent runtime.
- **Affected test module**: `test-harness` — cross-module integration tests.
- **Relevant collaborators of `AgentService`**: `MetadataDAO`, `ExecutionDAO`,
  `IndexDAO`, and the workflow search service. Which of these remain injected is
  determined in `agent-classifier-backfill-removal-plan.md` by checking for
  other usages; none is removed unless it becomes unused only because of this
  change.

## Complete file layout (files touched)

Only two source files are edited. No files are created; no new production code is
written.

| File | Responsibility | Change |
|---|---|---|
| `embedded agent runtime service` | Embedded agent deploy/start/search service | Delete the backfill method chain and its now-dead constants/helpers/imports (see below). |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java` | Deployment-contract end-to-end tests | Delete the single test that reflectively invokes the removed method. |

## Shared contract — the exact removal set

Every supporting document refers to the identifiers in this table by these exact
names. An identifier is removed **only if** its sole remaining caller is another
member of this set (transitive dead-code elimination rooted at the backfill
method).

### In `AgentService.java`

| Identifier | Kind | Current signature / value | Removed because |
|---|---|---|---|
| `backfillLegacyAgentExecutionClassifiers` | method | `private void backfillLegacyAgentExecutionClassifiers()` | Directly forbidden by review. Root of the dead-code chain. |
| `backfillVersionOf` | method | `private static int backfillVersionOf(Map<String, Object> metadata)` | Only caller is the backfill method. |
| `reindexAgentExecutions` | method | `private void reindexAgentExecutions(String agentName)` | Only caller is the backfill method (confirmed no other references in the file). |
| `the agent classifier backfill version` | constant | `private static final String the agent classifier backfill version = "agent_classifier_backfill_version"` | Only read by the removed methods. |
| `the agent classifier backfill version_VALUE` | constant | `private static final int the agent classifier backfill version_VALUE = 2` | Only read by the removed methods. |

Imports and injected fields (`indexDAO`, `executionDAO`, the workflow search
service, `WorkflowSummary`, `SearchResult`, `WorkflowClassifiers`, etc.) are
removed **case by case** and **only** when a repository-wide check shows the
removed methods were their last user. The concrete determination lives in
`agent-classifier-backfill-removal-plan.md`.

### In `deployment contract test.java`

| Identifier | Kind | Removed because |
|---|---|---|
| `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds` | `@Test` method | Its entire body exercises the forbidden method via reflection (`AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers")`). With the method gone the test can only fail; it is deleted rather than restored. |

Any `import` (e.g. `java.lang.reflect.Method`) left unused **only** by deleting
this test method is also removed.

## Consistency invariants

1. **No new entry point.** After the change there is no method — public,
   package-private, or private — named or aliased `backfill*Classifiers` on
   `AgentService`.
2. **Symmetric removal.** The production method and the test that references it
   are removed in the same change; neither is left orphaned.
3. **Metadata key untouched at runtime.** The string literal
   `"agent_classifier_backfill_version"` is only removed as an unused constant;
   the normal deploy path is not modified to write it (it did not, outside the
   backfill routine).
4. **Compiles clean.** No unused private members and no unused imports remain, so
   the module still builds under the project's strict formatting/lint settings.

## Verification (design-level, not run here)

- `./gradlew embedded agent runtime module (compileJava)` — module compiles with the members removed.
- `./gradlew :test-harness:test --tests '*deployment contract test*'`
  — the remaining tests pass; the deleted test no longer references a missing
  method.
- `./gradlew spotlessApply` — formatting normalized after edits.
