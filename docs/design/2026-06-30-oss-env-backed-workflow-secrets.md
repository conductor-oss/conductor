# Plan: env-backed `${workflow.secrets.NAME}` resolution in conductor-oss

**Repo:** `/home/nicholascole/IdeaProjects/conductor` (conductor-oss)
**Companion agentspan checkout:** `/home/nicholascole/IdeaProjects/agentspan` (branch `feature/add_memory`)

## Context

AgentSpan was refactored (local agentspan, commit `fa64a9cc` "delegate all secret resolution to
host via `${workflow.secrets.NAME}`; rip out standalone credential machinery") so the library
**resolves nothing** — when embedded it only *emits* `${workflow.secrets.NAME}` references into task
input and expects the host to resolve them.

conductor-oss has **no resolver**. Today `ParametersUtils.replaceVariables` sees
`${workflow.secrets.X}`, fails the `EnvUtils.isEnvironmentVariable` check, calls
`documentContext.read("workflow.secrets.X")` which misses (no `secrets` node), catches the
exception, and substitutes `Replacement(null)` → the reference is persisted as the literal string
`"null"` at **task-scheduling** time (this happens in the task mappers, not at poll).

**Goal:** resolve `${workflow.secrets.NAME}` from OS environment variables at the **engine level**
(covers *both* AgentSpan agents and plain Conductor workflows), **transiently at the poll boundary**
— the DB keeps the reference, only the polled response carries plaintext. Then adopt the new local
agentspan and delete PR #1213's now-orphaned credential SPI stack.

**Confirmed decisions:** missing secret ⇒ **fail the task**; feature gate **default ON**; use the
local agentspan built to maven-local and delete the orphaned credential code.

**Coordination (already wired):** `AgentSpanEmbeddedEnvironmentPostProcessor` sets
`agentspan.embedded=true` whenever `conductor.integrations.ai.enabled=true`, and agentspan's emission
is embedded-gated (`EmbeddedMode.isEmbedded()`). So with AI enabled, references *are* produced and
this resolver has something to resolve.

---

## Part A — Engine-level resolver (`core` module)

### Step 1 — Preserve the reference (skip `workflow.secrets.*` during normal resolution)
`core/src/main/java/com/netflix/conductor/core/utils/ParametersUtils.java`, in `replaceVariables`
(line 233). After `paramPath` is computed (line 242) and **before** the `EnvUtils.isEnvironmentVariable`
check (line 249): if `paramPath.startsWith("workflow.secrets.")`, add a `Replacement` of the original
literal `${...}` match text (re-emit unchanged) and `continue` — do **not** call
`documentContext.read`. Effect: the reference survives into persisted task input instead of becoming
`"null"`. Scope strictly to the `workflow.secrets.` prefix so every other token
(`${workflow.input.x}`, `CPEWF_TASK_ID`, plain env via `EnvUtils`) is untouched (regression Step 8).

### Step 2 — `SecretsProvider` seam + env-backed default
- New interface in `core` (e.g. `core/.../core/secrets/SecretsProvider.java`):
  `String getSecret(String name);` — `null`/blank ⇒ unresolved.
- Default `EnvSecretsProvider` → `System.getenv(name)` then `System.getProperty(name)` fallback
  (mirror `common/.../utils/EnvUtils.java:31-33,41-44`).
- Register in `core/.../core/config/ConductorCoreConfiguration.java` as a
  `@Bean @ConditionalOnMissingBean(SecretsProvider.class)` (same idiom as `idGenerator()` there) so
  enterprise/orkes can override.

### Step 3 — `SecretSubstitutor` (resolve references, fail on missing)
New `core/.../core/secrets/SecretSubstitutor.java` (`@Component`, takes `SecretsProvider` +
`ConductorProperties`):
- Recursively walk a value (maps **and** lists); for any `String` containing
  `${workflow.secrets.X}`, resolve `X` via `SecretsProvider`. Support partial strings
  (`"Bearer ${workflow.secrets.TOKEN}"`) using the orkes split regex
  `(?=(?<!\$)\$\{workflow\.secrets\.)|(?<=})`.
- Collect any reference whose value is null/blank; if **any** is unresolved, throw a dedicated
  `UnresolvedSecretException(taskId, missingNames)` (per the "fail the task" decision — do **not**
  substitute empty string or leave the literal).
- Operate on the polled `Task` DTO's `inputData` only; **never re-persist** (Step 1 already
  guarantees the DB keeps the reference). Never `LOGGER` a resolved value.

### Step 4 — Invoke at the poll boundary + fail-on-missing
`core/src/main/java/com/netflix/conductor/service/TaskServiceImpl.java` (constructor currently takes
`ExecutionService` + `QueueDAO` only — add `SecretSubstitutor`):
- `poll(...)` (line 63): after `executionService.getLastPollTask(...)`, run substitution. On
  `UnresolvedSecretException` → fail the task via
  `executionService.updateTask(new TaskResult(task))` with status `FAILED` and a
  `reasonForIncompletion` listing the missing secret names (`ExecutionService.updateTask(TaskResult)`
  exists at line 299), and `return null` so the worker receives nothing.
- `batchPoll(...)` (line 88): map each task through the same; fail+drop any unresolved task, return
  the rest.
- REST `TaskResource.poll`/`batchPoll` need **no** change — the service layer covers both endpoints
  and the in-process system-task poller.

### Step 5 — Gate (default ON)
Add a boolean to `core/.../core/config/ConductorProperties.java` (prefix `conductor.app`), e.g.
`workflowSecretsEnvResolutionEnabled = true` → property
`conductor.app.workflow-secrets-env-resolution-enabled`. When `false`, Step 1's skip **and** Step 3/4
substitution are no-ops (references pass through unresolved = today's behavior). Keep skip and
substitute coupled to the one flag so persisted shape stays consistent.

---

## Part B — Adopt local agentspan + delete orphaned credential stack (`ai` module)

### Step 6a — Build & repoint the dependency
- In `/home/nicholascole/IdeaProjects/agentspan`, publish the library to maven-local:
  `./gradlew :conductor-agentspan:publishToMavenLocal` (module path is `server/conductor-agentspan`).
- **Version alignment:** local publishes `0.1.0`; conductor pins `0.2.0` in `ai/build.gradle:24`
  (literal `'org.conductoross:conductor-agentspan:0.2.0'`). Either publish with `-Pversion=<v>` or
  edit that line to the published version. Ensure `mavenLocal()` is in the repositories block
  (root `build.gradle`) — it is **not** currently present, so the local artifact won't resolve until
  added.

### Step 6b — Delete genuinely-orphaned code (types removed from agentspan)
Removed types: `CredentialStoreProvider`, `KnownProviderEnvVars`, `CredentialMeta`,
`ExecutionTokenService`, `WorkerController`/`/api/workers/secrets`.
- **DELETE** `ai/.../agentspan/EnvBackedCredentialStore.java` and its test
  `ai/src/test/.../agentspan/EnvBackedCredentialStoreTest.java` (entirely credential SPI).
- **EDIT** `ai/.../agentspan/ConductorAgentSpanConfiguration.java`:
  - remove `import dev.agentspan.runtime.spi.CredentialStoreProvider;` (line 33);
  - remove `agentSpanCredentialStoreProvider()` bean (lines 99-108) and the now-dead
    `conductor.integrations.ai.secrets.env-names` key;
  - remove `agentSpanCredentialMasterKey()` bean (lines 77-92) + `MASTER_KEY_BYTES` (line 64) and the
    `AGENTSPAN_MASTER_KEY` key — its consumer (`ExecutionTokenService`) is gone. **Verify** whether
    the new embedded library instead requires a host `MasterKeyProvider`/`ExecutionTokenIssuer` bean
    (new SPIs added in agentspan `cb1c774c`); if so, provide them mirroring `conductor-agentspan-server`,
    else drop.

### Step 6c — KEEP (their agentspan types still exist; do not delete)
- `agentSpanSecretOutputMasker()` bean — `SecretOutputMasker` SPI still exists, and its NoOp impl
  ships only in `conductor-agentspan-server` (which conductor does **not** depend on), so the host
  must supply this bean. **Keep.**
- `AgentSpanPrincipalFilter.java` — `RequestContext`/`RequestContextHolder` still exist, so it still
  compiles and is functional in single-tenant. **Keep** (removal would be optional cleanup, out of scope).
- Skill SPI adapters (`SkillMetadataDaoAdapter`, `SkillPackageStoreAdapter`) and the listener classes
  — unaffected. **Keep.**

### Step 6d — Compile-driven verification of embedded bean wiring
After the bump, build the `ai` module; resolve any "no bean of type X" by mirroring the host beans
`conductor-agentspan-server` provides. This is build-driven discovery, not guesswork.

---

## Part C — Output masking (follow-up)
Resolved env secrets can land in task **output**/logs. Minimum now: substitution never logs resolved
values. Full masking of persisted output is a **follow-up**, noted explicitly rather than silently
skipped (orkes uses a `SecretOutputMasker` for this; the OSS host masker bean is currently a no-op).

---

## Critical files
- `core/.../core/utils/ParametersUtils.java` — Step 1 skip (replaceVariables, line 233-264).
- `core/.../core/secrets/SecretsProvider.java`, `EnvSecretsProvider.java`, `SecretSubstitutor.java`,
  `UnresolvedSecretException.java` — new (Steps 2-3).
- `core/.../core/config/ConductorCoreConfiguration.java` — provider bean (Step 2).
- `core/.../core/config/ConductorProperties.java` — gate flag (Step 5).
- `core/.../service/TaskServiceImpl.java` — poll/batchPoll wiring + fail-on-missing (Step 4).
- `ai/build.gradle` (line 24) + root `build.gradle` repositories — dependency bump (Step 6a).
- `ai/.../agentspan/ConductorAgentSpanConfiguration.java` — surgical edits (Step 6b/6c).
- `ai/.../agentspan/EnvBackedCredentialStore.java` (+ test) — delete (Step 6b).
- `common/.../utils/EnvUtils.java` — reuse reference for env-read order (read-only).

## Verification
**Unit** (write test → fail first → pass):
1. `ParametersUtils` preserves `${workflow.secrets.FOO}` through `getTaskInput` (Step 1) — resolved
   input still contains the literal reference, not `"null"`.
2. `SecretSubstitutor`: env `FOO=bar` ⇒ `"${workflow.secrets.FOO}"`→`"bar"`;
   `"Bearer ${workflow.secrets.FOO}"`→`"Bearer bar"`; nested map/list resolves recursively; missing
   env ⇒ `UnresolvedSecretException` (fail decision).
3. `EnvSecretsProvider.getSecret` reads `System.getenv` then property fallback.

**Integration / e2e:**
4. **Transient-resolution proof:** env `TEST_SECRET=s3cr3t`; workflow task input
   `"x":"${workflow.secrets.TEST_SECRET}"`; start → **poll** ⇒ polled input `x=="s3cr3t"`; then
   `GET /api/tasks/{taskId}` ⇒ persisted input still shows the reference (proves resolve-at-poll +
   no-persist).
5. **Missing secret:** unset env ⇒ polled task is FAILED with a reason naming the missing secret;
   worker receives nothing.
6. **AgentSpan e2e** (local agentspan, `conductor.integrations.ai.enabled=true` ⇒ embedded):
   LLM key from env (`OPENAI_API_KEY`) ⇒ agent `COMPLETED`; a tool whose declared credential is in
   env ⇒ tool task `COMPLETED`, absent ⇒ fails cleanly. Assert on task status/output, not LLM prose.
7. **Plain (non-agent) workflow** with `${workflow.secrets.X}` in an HTTP task header resolves from
   env at poll — proves the "agents *and* workflows" requirement.

**Regression:**
8. A token that is NOT `workflow.secrets.*` (`${workflow.input.x}`, `${CPEWF_TASK_ID}`, plain env
   `${SOME_VAR}`) resolves exactly as before — Step 1's skip must be narrowly scoped.

**Build:** agentspan publishes to maven-local; conductor `:ai` and `:server` compile against it with
no references to removed types (`CredentialStoreProvider`, `KnownProviderEnvVars`, `CredentialMeta`).
