# Removal Plan — Legacy Agent Classifier Backfill

Concrete, file-by-file edit plan. Identifiers and file paths are used exactly as
defined in [`removal-architecture.md`](./removal-architecture.md).

## Guiding rule

Remove the backfill method, then apply transitive dead-code elimination: delete a
helper, constant, field, or import **only** when its last remaining reference was
one of the members already scheduled for deletion. Confirm each "last reference"
with a repository-wide search before deleting. See `removal-architecture.md` §4c.

## 1. `AgentService.java`

Path:
`embedded agent runtime service`

### 1a. Delete the backfill method

Remove the method and its Javadoc (current lines 407–460):

```java
private void backfillLegacyAgentExecutionClassifiers() { ... }
```

### 1b. Delete the two helpers used only by the backfill method

Grep confirms each helper's only caller is inside
`backfillLegacyAgentExecutionClassifiers`:

- `private static int backfillVersionOf(Map<String, Object> metadata)` — current
  line 463.
- `private void reindexAgentExecutions(String agentName)` — current line 476;
  remove its Javadoc (lines 471–475) as well.

### 1c. Delete the two constants read only by the removed methods

```java
private static final String the agent classifier backfill version =
        "agent_classifier_backfill_version";
// Version 2 additionally reindexes generated router sub-workflows, which older compiler
// output persisted as ordinary workflow executions.
private static final int the agent classifier backfill version_VALUE = 2;
```

Remove the two associated comment lines with them (current lines 64–68).

### 1d. Remove the one field + import that becomes dead

Per `removal-architecture.md` §4c, grep against the current source shows exactly
one newly-orphaned member:

- Field `private final IndexDAO indexDAO;` (current line 75). Its only reference
  is `indexDAO.indexWorkflow(...)` inside `reindexAgentExecutions` (line 513).
  After 1b it is dead. Delete the field; `@RequiredArgsConstructor` drops it from
  the constructor automatically. Delete the now-orphaned `IndexDAO` import.

### 1e. Do NOT remove these — they are still live

Verified against the current source. Removing any of these breaks compilation:

| Keep | Because it is still used at |
|---|---|
| `WorkflowClassifiers` import + `WorkflowClassifiers.isAgent(...)` | lines 316, 366 |
| `SearchResult` import | lines 600, 728, 973, 1548, 1553, 1555 |
| `WorkflowSummary` import | lines 600, 728, 973, 976, plus search methods |
| `WorkflowModel` import | lines 765, 780 |
| `executionDAO` field (`ExecutionDAO`) | lines 765, 767, 780, 782 |
| `workflowService` field (`WorkflowService`) | throughout the class |

> This corrects any earlier draft that flagged `WorkflowClassifiers`,
> `executionDAO`, `SearchResult`, `WorkflowSummary`, or `WorkflowModel` as
> removal candidates. They are live in the current source.

## 2. `deployment contract test.java`

Path:
`test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java`

### 2a. Delete the test method

Remove the whole method, `@Test` annotation (current line 295) through its closing
brace (current line 338):

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

### 2b. Remove the now-unused reflection import

Delete `import java.lang.reflect.Method;` (current line 18). Grep confirms `Method`
and `getDeclaredMethod` appear only inside the deleted test (lines 327–328), so the
import is orphaned. If any other test in the file uses `Method`, keep it — the
guiding rule governs.

## 3. Formatting and build

- Run `./gradlew spotlessApply`.
- Confirm compile: `./gradlew embedded agent runtime module (compileJava)`.
- Confirm the test module compiles: `./gradlew :test-harness:compileTestJava`.

## Change budget

Two files edited, zero files created, zero production behaviors added. This is the
minimal set that satisfies both review comments simultaneously.
