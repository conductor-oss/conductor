# Change Plan — Remove the Legacy Agent Classifier Backfill

This document gives the ordered, mechanical edits that implement
[`architecture.md`](architecture.md). It reuses the symbol names from §5 of that document
verbatim. Every step is a **removal**; no code is added.

## Step 1 — `AgentService.java`

File:
`embedded agent runtime service`

### 1a. Remove the two private constants

Delete the field declaration:

```java
private static final String the agent classifier backfill version =
        "agent_classifier_backfill_version";
```

and the constant plus its explanatory comment:

```java
// Version 2 additionally reindexes generated router sub-workflows, which older compiler
// output persisted as ordinary workflow executions.
private static final int the agent classifier backfill version_VALUE = 2;
```

Leave the neighboring `MAPPER` constant and the injected `final` fields in place, except
as noted in step 1d.

### 1b. Remove the backfill method

Delete the entire method, **including its Javadoc block**:

```java
/**
 * Backfill the agent execution classifier index for legacy agent definitions.
 * ...
 */
private void backfillLegacyAgentExecutionClassifiers() {
    ...
}
```

### 1c. Remove the two private helpers

Delete `backfillVersionOf`:

```java
/** Read the stored backfill version from an agent def's metadata, defaulting to 0. */
private static int backfillVersionOf(Map<String, Object> metadata) {
    ...
}
```

and `reindexAgentExecutions`:

```java
/**
 * Reindex an agent's persisted executions within the concrete start/end time window ...
 */
private void reindexAgentExecutions(String agentName) {
    ...
}
```

### 1d. Remove the now-orphaned `IndexDAO` field and import

After steps 1b–1c, `indexDAO` is referenced nowhere in the class (its only use was
`indexDAO.indexWorkflow(...)` inside `reindexAgentExecutions`). Remove:

```java
private final IndexDAO indexDAO;
```

and the corresponding `import ...IndexDAO;` line.

> Verification before deleting: search the file for `indexDAO`. If the only remaining hit
> is the field declaration itself, remove both the field and the import. If any other
> reference survives, keep them.

Do **not** remove `workflowService`, `executionDAO`, or `metadataDAO`; they are used by
unrelated methods (search, status, lifecycle operations, deployment/listing).

## Step 2 — `deployment contract test.java`

File:
`test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java`

### 2a. Remove the failing test method

Delete the whole `@Test` method:

```java
@Test
void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() throws Exception {
    ...
    Method backfill =
            AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
    backfill.setAccessible(true);
    backfill.invoke(agentService);
    ...
}
```

Delete from the `@Test` annotation line through the method's closing brace, inclusive,
plus any trailing blank line so the surrounding test methods keep their spacing.

### 2b. Remove the now-unused reflection import

Delete:

```java
import java.lang.reflect.Method;
```

> Verification before deleting: search the file for `Method`. `java.lang.reflect.Method`
> is used only by the deleted test, so after step 2a the import is unused and must be
> removed to keep the file compiling cleanly under the project's checks. Retain all other
> imports — they serve the remaining tests.

## Step 3 — Formatting

Run `./gradlew spotlessApply` (per AGENTS.md) so the edited files match the project's
formatting rules. Removing methods can leave double blank lines; Spotless normalizes
them.

## Ordering and idempotency

- Steps 1 and 2 are independent and may be done in either order; both must be complete
  before compiling.
- The change is a pure deletion set. Re-running the completion checks in
  [`testing.md`](testing.md) after the edits must show zero remaining references to the
  forbidden identifier.
