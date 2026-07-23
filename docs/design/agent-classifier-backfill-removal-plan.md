# Removal Plan — Legacy Agent Classifier Backfill

Concrete, file-by-file edit plan. Identifiers and file paths are used exactly as
defined in `agent-classifier-backfill-removal-architecture.md`.

## Guiding rule

Remove the backfill method and then apply transitive dead-code elimination:
delete a helper, constant, field, or import **only** when its last remaining
reference was one of the members already scheduled for deletion. Confirm each
"last reference" with a repository-wide search before deleting.

## 1. `AgentService.java`

Path:
`embedded agent runtime service`

### 1a. Delete the backfill method

Remove the entire method and its Javadoc:

```java
private void backfillLegacyAgentExecutionClassifiers() { ... }
```

### 1b. Delete helpers that only the backfill method used

- `private static int backfillVersionOf(Map<String, Object> metadata)` — grep
  confirms its only caller is inside `backfillLegacyAgentExecutionClassifiers`.
- `private void reindexAgentExecutions(String agentName)` — grep confirms its
  only caller is inside `backfillLegacyAgentExecutionClassifiers`. Remove its
  Javadoc as well.

> If, contrary to the current grep, either helper is found to have another
> caller, keep it. The "Guiding rule" above governs.

### 1c. Delete constants that only the removed methods read

```java
private static final String the agent classifier backfill version =
        "agent_classifier_backfill_version";
// Version 2 additionally reindexes generated router sub-workflows, which older compiler
// output persisted as ordinary workflow executions.
private static final int the agent classifier backfill version_VALUE = 2;
```

Remove the two associated comment lines with them.

### 1d. Prune now-unused injected fields and imports

After 1a–1c, re-check each of the following for remaining usage in the file. Keep
any that is still used elsewhere; remove any whose last usage lived in the
deleted code:

- Injected fields: `indexDAO` (`IndexDAO`), `executionDAO` (`ExecutionDAO`), and
  the workflow search service used by `reindexAgentExecutions`
  (`workflowService.searchWorkflows(...)`).
- Types referenced only by the removed reindex logic: `SearchResult`,
  `WorkflowSummary`, `WorkflowModel` (as used in reindex), and
  `WorkflowClassifiers` (used only by `backfillLegacyAgentExecutionClassifiers`
  via `WorkflowClassifiers.isAgent`).

For each field removed, also remove it from the constructor injection list.
`AgentService` is annotated `@RequiredArgsConstructor`, which derives the
constructor from the `private final` fields, so deleting the field is
sufficient; then delete its now-orphaned `import`.

> Expectation from current sources: the reindex-only search path and the
> `WorkflowClassifiers.isAgent` call are strong candidates for pruning;
> `executionDAO`, `metadataDAO`, and `indexDAO` are used broadly across
> `AgentService` and are likely to stay. Verify, do not assume.

## 2. `deployment contract test.java`

Path:
`test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java`

### 2a. Delete the test method

Remove the whole method, annotation to closing brace:

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

### 2b. Prune test-only imports

Remove `import java.lang.reflect.Method;` **iff** no other test in the file uses
`Method`. If reflection is still used elsewhere in the class, keep it.

## 3. Formatting and build

- Run `./gradlew spotlessApply`.
- Confirm compile: `./gradlew embedded agent runtime module (compileJava)`.

## Change budget

Two files edited, zero files created, zero production behaviors added. This is the
minimal set that satisfies both review comments simultaneously.
