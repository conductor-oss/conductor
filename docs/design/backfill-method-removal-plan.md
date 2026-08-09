# Implementation Plan

Concrete, ordered edits to implement the change described in
[`backfill-method-removal-architecture.md`](./backfill-method-removal-architecture.md).
Names and paths are used verbatim from that document. Two files change; none are
created.

## 1. Edit `AgentService.java`

Path:
`embedded agent runtime service`

Delete, in this order (deleting methods before fields keeps the file compiling
between steps if applied incrementally):

1. **The backfill method and its Javadoc.** Remove the block from the
   `/** Backfill the agent execution classifier index ... */` Javadoc through the
   closing brace of `private void backfillLegacyAgentExecutionClassifiers()`
   (currently `AgentService.java:407`–`460`).

2. **The `backfillVersionOf` helper and its Javadoc.** Remove
   `/** Read the stored backfill version ... */` through the closing brace of
   `private static int backfillVersionOf(Map<String, Object> metadata)`
   (currently `AgentService.java:462`–`469`).

3. **The `reindexAgentExecutions` helper and its Javadoc.** Remove
   `/** Reindex an agent's persisted executions ... */` through the closing brace
   of `private void reindexAgentExecutions(String agentName)`
   (currently `AgentService.java:471`–`521`).

4. **The two constants** (currently `AgentService.java:64`–`68`), including the
   two-line comment above `the agent classifier backfill version_VALUE`:

   ```java
   private static final String the agent classifier backfill version =
           "agent_classifier_backfill_version";
   // Version 2 additionally reindexes generated router sub-workflows, which older compiler
   // output persisted as ordinary workflow executions.
   private static final int the agent classifier backfill version_VALUE = 2;
   ```

5. **The now-unused `indexDAO` field** (currently `AgentService.java:75`):

   ```java
   private final IndexDAO indexDAO;
   ```

6. **The now-unused import** (currently `AgentService.java:46`):

   ```java
   import com.netflix.conductor.dao.IndexDAO;
   ```

### Do not touch

Leave the `SearchResult`, `WorkflowSummary`, and `WorkflowModel` imports and the
`executionDAO` / `metadataDAO` / `workflowService` fields in place — they are
used by `searchExecutionsRaw`, `searchAgentExecutions`, and other surviving
methods (verified references at `AgentService.java:600, 728, 765, 780, 973,
1548, 1553`).

## 2. Edit `deployment contract test.java`

Path:
`test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java`

1. **Delete the failing test method in full** — from its `@Test` annotation
   through the closing brace of
   `void legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds() throws Exception`
   (currently `deployment contract test.java:295`–`338`). This
   removes the reflective calls:

   ```java
   Method backfill =
           AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers");
   backfill.setAccessible(true);
   backfill.invoke(agentService);
   ```

2. **Remove the unused reflection import** (currently
   `deployment contract test.java:18`):

   ```java
   import java.lang.reflect.Method;
   ```

   Only remove it after confirming no other method in the file uses
   `java.lang.reflect.Method` (see §3). Keep the `AgentService` import — the
   surrounding test class still autowires and uses `AgentService` elsewhere.

## 3. Pre-removal verification (grep)

Before deleting the import in step 2 of §2, confirm the symbol is not used
elsewhere in the test file:

```bash
grep -n "\bMethod\b" test-harness/src/test/java/com/netflix/conductor/test/integration/agent/deployment contract test.java
```

Expect matches only inside the deleted method. If any remain outside it, keep
the import.

After editing both files, confirm the removed symbols are gone repo-wide:

```bash
grep -rn "backfillLegacyAgentExecutionClassifiers\|backfillVersionOf\|reindexAgentExecutions\|the agent classifier backfill version" \
  embedded agent runtime source test-harness/src
```

Expect **no output**. (The string literal `"agent_classifier_backfill_version"`
may still appear in unrelated data such as previously deployed metadata, but not
in these source files.)

## 4. Format and build

Per `AGENTS.md`:

```bash
./gradlew spotlessApply
./gradlew embedded agent runtime module (compileJava)
./gradlew :test-harness:compileTestJava
```

## 5. Ordering note

Both edits should land in the **same commit**. The test edit alone would leave
dead code the reviewer asked to remove; the production edit alone would leave
`deployment contract test` throwing `NoSuchMethodException` again
at runtime. Applying them together satisfies both review comments at once.
