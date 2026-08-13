# Change spec — exact edits

This document enumerates the exact, line-anchored deletions for the removal described
in [architecture.md](./architecture.md). All symbol names match that document verbatim.
Line numbers reflect the files at the base of this change; treat them as anchors, not as
absolutes after each edit.

## Edit set 1 — `AgentService.java`

Path:
`embedded agent runtime service`

### 1a. Delete the two constants and their comment (currently lines 64–68)

Remove:

```java
    private static final String the agent classifier backfill version =
            "agent_classifier_backfill_version";
    // Version 2 additionally reindexes generated router sub-workflows, which older compiler
    // output persisted as ordinary workflow executions.
    private static final int the agent classifier backfill version_VALUE = 2;
```

Leave the preceding `MAPPER` constant and the following field declarations intact.

### 1b. Delete the `indexDAO` field (currently line 75)

Remove:

```java
    private final IndexDAO indexDAO;
```

Because the class uses Lombok `@RequiredArgsConstructor`, removing this `final` field
also removes it from the generated constructor. No other constructor edits are needed.
Confirm no remaining reference to `indexDAO` exists in the file after 1d.

### 1c. Delete the now-unused import (currently line 46)

Remove:

```java
import com.netflix.conductor.dao.IndexDAO;
```

Keep `import com.netflix.conductor.dao.ExecutionDAO;` and
`import com.netflix.conductor.dao.MetadataDAO;` — both remain in use.

### 1d. Delete the backfill method and helpers (currently lines 407–521)

Remove, in one contiguous block, the Javadoc-and-body for all three methods:

- `backfillLegacyAgentExecutionClassifiers()` (method + its Javadoc, currently 407–460)
- `backfillVersionOf(Map<String, Object> metadata)` (method + its Javadoc, 462–469)
- `reindexAgentExecutions(String agentName)` (method + its Javadoc, 471–521)

The block begins at the Javadoc line:

```java
    /**
     * Backfill the agent execution classifier index for legacy agent definitions.
```

and ends at the closing brace of `reindexAgentExecutions`, immediately before:

```java
    /** Search agent executions with optional filters. */
    public Map<String, Object> searchAgentExecutions(
```

`searchAgentExecutions` and everything after it is retained.

### Imports to KEEP in `AgentService.java`

Do **not** remove these — they are used by retained methods:

- `com.netflix.conductor.common.run.SearchResult`
- `com.netflix.conductor.common.run.WorkflowSummary`
- `com.netflix.conductor.model.WorkflowModel`

## Edit set 2 — `deployment contract test.java`

Path:
`test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java`

### 2a. Delete the failing test method (currently lines 295–338)

Remove the whole method, including its `@Test` annotation:

```java
    @Test
    void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() throws Exception {
        ...
        assertEquals(
                2,
                metadataService
                        .getWorkflowDef(agent, null)
                        .getMetadata()
                        .get("agent_classifier_backfill_version"));
    }
```

The preceding test method (ending at line 293) and the following
`deploymentPersistsSdkLifecycleCallbacksAtTheirConfiguredWorkflowBoundaries()` test
(starting at line 340) are retained.

### 2b. Delete the now-unused import (currently line 18)

Remove:

```java
import java.lang.reflect.Method;
```

This import's only use was inside the deleted test method. Verify no other reflection
usage remains in the file before removing it.

## Post-edit checklist

1. `grep -R backfillLegacyAgentExecutionClassifiers` returns **no matches** in the repo.
2. `grep -R the agent classifier backfill version` returns **no matches** in the repo.
3. `grep -R reindexAgentExecutions` and `grep -R backfillVersionOf` return **no matches**.
4. No `indexDAO` / `IndexDAO` reference remains in `AgentService.java`.
5. No `java.lang.reflect.Method` reference remains in the test file.
6. Run `./gradlew spotlessApply`.

Verification steps and expected results are in [testing.md](./testing.md).
