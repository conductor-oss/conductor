# Architecture — Remove the Legacy Agent Classifier Backfill

> These documents live in `docs/design/remove-legacy-agent-backfill/` to avoid colliding
> with the pre-existing, unrelated `docs/design/architecture.md` and
> `docs/design/testing.md` (which describe the Agent Worker task types). This design set
> covers only the PR-feedback change described below.

## 1. Purpose

This design addresses PR review feedback on the `agentspan` module. A prior commit
(`code_subtask add-backfill-method`) reintroduced a private maintenance routine,
`AgentService.backfillLegacyAgentExecutionClassifiers()`, together with its supporting
private helpers and constants, in order to satisfy an end-to-end test that invokes the
method reflectively.

The reviewer (@v1r3n) gave two explicit, mutually reinforcing instructions:

1. Fix the failing test
   `AgentSpanDeploymentContractEndToEndTest.legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`,
   which fails with:

   ```
   java.lang.NoSuchMethodException:
     org.conductoross.conductor.ai.agentspan.runtime.service.AgentService.backfillLegacyAgentExecutionClassifiers()
   ```

2. **Do not add `backfillLegacyAgentExecutionClassifiers` back.** This method must not
   be reintroduced.

Because instruction (2) forbids restoring the method, the only way to satisfy instruction
(1) is to **remove the test that depends on the method** (and, for consistency, the
production code that was re-added solely to support it). The failing test asserts on a
method that, by the reviewer's decision, is not part of the intended API surface; the
test therefore encodes an unwanted contract and must be deleted rather than repaired.

This is a **deletion-only** change. No new behavior, types, or endpoints are introduced.

## 2. Scope

### In scope

- Remove the private method `backfillLegacyAgentExecutionClassifiers()` and the two
  private helpers introduced to support it from `AgentService`.
- Remove the two private constants introduced to support it from `AgentService`.
- Remove the now-orphaned injected field and its unused import if the backfill code was
  its only consumer.
- Delete the end-to-end test method that reflectively invokes the removed method, and
  clean up any import left unused by that deletion.

### Out of scope

- Any change to agent deployment, compilation, execution, search, or indexing behavior
  that is unrelated to the backfill routine.
- Reintroducing the backfill under a different name or as public API. Explicitly
  forbidden by the review.
- Modifying other tests in `AgentSpanDeploymentContractEndToEndTest` or any other test
  class.
- The pre-existing `docs/design/architecture.md` / `docs/design/testing.md` — left
  untouched.

## 3. Tech stack (context only)

- **Language / build:** Java 21, Gradle.
- **Affected modules:** `agentspan` (production code) and `test-harness` (end-to-end
  test).
- **Frameworks in play at the touch points:** Spring (`@Component`,
  `@ConditionalOnProperty`), Lombok (`@RequiredArgsConstructor`, `@Slf4j`), JUnit 5 /
  Testcontainers for the e2e test.
- **Formatting:** Spotless. `./gradlew spotlessApply` must be run after the edits (per
  AGENTS.md), though this design doc does not itself run any build.

## 4. Complete file layout

No source files are created. Two existing source files are edited; both edits are
removals.

| File | Module | Change |
|---|---|---|
| `agentspan/src/main/java/org/conductoross/conductor/ai/agentspan/runtime/service/AgentService.java` | `agentspan` | Remove the backfill method, its two private helper methods, its two private constants, and the now-unused `IndexDAO` field + import (see §5). |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/agent/AgentSpanDeploymentContractEndToEndTest.java` | `test-harness` | Remove the `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` test method and the now-unused `java.lang.reflect.Method` import (see §5). |

Design docs created by this change (all under `docs/design/remove-legacy-agent-backfill/`):

| Doc | Responsibility |
|---|---|
| `architecture.md` (this file) | Single source of truth: scope, file layout, exact symbols to remove, rationale. |
| `change-plan.md` | Ordered, mechanical removal steps per file. |
| `testing.md` | Verification: the deleted test, grep completion checks, build/test gates. |

## 5. Shared contract — the exact symbols to remove

Every supporting document references the symbols below **verbatim**. These are the names
as they exist in the current source.

### 5.1 In `AgentService.java` (module `agentspan`)

Remove all of the following, and **nothing else**:

| Symbol | Kind | Notes |
|---|---|---|
| `AGENT_CLASSIFIER_BACKFILL_VERSION` | `private static final String` constant (value `"agent_classifier_backfill_version"`) | Only referenced by the backfill code being removed. |
| `AGENT_CLASSIFIER_BACKFILL_VERSION_VALUE` | `private static final int` constant (value `2`) | Only referenced by the backfill code being removed. Remove its preceding explanatory comment too. |
| `backfillLegacyAgentExecutionClassifiers()` | `private void` method (with its Javadoc) | The method named in the `NoSuchMethodException`. Must not be re-added. |
| `backfillVersionOf(Map<String, Object> metadata)` | `private static int` method | Helper used only by the backfill method. |
| `reindexAgentExecutions(String agentName)` | `private void` method | Helper used only by the backfill method. |
| `private final IndexDAO indexDAO;` | injected field | Only used inside `reindexAgentExecutions` (`indexDAO.indexWorkflow(...)`). Remove the field **and** the `IndexDAO` import once its sole consumer is gone. |

**Fields that MUST be retained** — these are used elsewhere in `AgentService` and are
NOT part of this change:

- `workflowService` — used by search, status, pause/resume/terminate/delete, restart,
  retry, rerun, and other operations.
- `executionDAO` — used by workflow-model read/update paths outside the backfill.
- `metadataDAO` — used across deployment/listing.

Verification rule for the `IndexDAO` field removal: remove the field only after
confirming `indexDAO` has no remaining references in `AgentService` once
`reindexAgentExecutions` is deleted. If any other reference exists, keep the field and
its import.

### 5.2 In `AgentSpanDeploymentContractEndToEndTest.java` (module `test-harness`)

Remove:

| Symbol | Kind | Notes |
|---|---|---|
| `legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()` | `@Test void` method | The failing test. It reflectively looks up and invokes the removed method via `AgentService.class.getDeclaredMethod("backfillLegacyAgentExecutionClassifiers")`. |
| `import java.lang.reflect.Method;` | import | Remove only if no other test in the file uses `Method` after the deletion. |

**Imports that MUST be retained:** `java.util.Map`, `java.util.List`, `java.util.UUID`,
and every other import — they are used by the many remaining tests in the class. Only
`java.lang.reflect.Method` is affected.

### 5.3 Naming and consistency conventions

- The forbidden identifier is exactly `backfillLegacyAgentExecutionClassifiers`. It must
  appear in **no** production or test source after this change. A repository-wide search
  for this string is the primary completion check (see `testing.md`).
- The metadata key string literal `"agent_classifier_backfill_version"` must also
  disappear from both source files, since both its producer (the constant/method) and its
  consumer (the test) are being removed.

## 6. Design rationale

- **Why delete rather than repair the test.** The test's sole purpose is to assert that
  `backfillLegacyAgentExecutionClassifiers` exists and stamps a version into workflow
  metadata. The reviewer has decided this method should not exist. A "repaired" test
  would necessarily either (a) re-assert the forbidden method, or (b) test unrelated
  behavior under a misleading name. Both are worse than deletion. Deleting the test
  removes the unwanted contract cleanly.
- **Why remove the production code too.** Leaving a private, never-invoked method plus
  its private helpers and constants would be dead code. `reindexAgentExecutions`,
  `backfillVersionOf`, the two constants, and the `IndexDAO` field exist only to serve
  the backfill; with the backfill gone they are unreachable. Removing them keeps
  `AgentService` free of dead code and honors the "do not add it back" instruction in
  spirit as well as letter.
- **Minimality.** Nothing outside the symbols in §5 is touched. All other agent
  behavior, all other tests, and all other injected collaborators remain exactly as they
  are.

## 7. Supporting documents

- [`change-plan.md`](change-plan.md) — the ordered, mechanical removal steps for each
  file, using the exact symbols from §5.
- [`testing.md`](testing.md) — how the change is verified: the deleted test, the
  grep-based completion checks, and the compile/format gates.
