# Conductor-Agent Runtime Branch — Architecture & Source of Truth

Design for landing the `AGENT` task's embedded **Conductor-agent** branch and closing out
the PR review on [#1286]. This document is the single source of truth: every name, type,
constant, and file path below is reused **verbatim** by the supporting design docs
([data-model.md](./data-model.md), [test-plan.md](./test-plan.md), [testing.md](./testing.md),
[examples.md](./examples.md), [documentation.md](./documentation.md)).

The testing approach has two layers: [test-plan.md](./test-plan.md) is the authoritative
coverage matrix that maps the **entire** `conductor.ai.a2a` suite class-by-class onto this
branch (and records, with a transport reason, which a2a classes are `N/A` because the conductor
branch is poll-only and in-process); [testing.md](./testing.md) holds the per-method specs for
the three new e2e classes. The two agree on one class list; if they diverge, test-plan.md wins.

## 1. Overview

The `AGENT` system task (`org.conductoross.conductor.ai.a2a.AgentTask`) already drives remote
Agent2Agent (A2A) calls. This change adds a second branch, selected by the request field
`agentType`, that runs an agent on an **embedded Conductor-agent (agentspan) runtime** instead of
a remote HTTP endpoint:

- `agentType: "a2a"` (default) — remote A2A agent, existing behaviour.
- `agentType: "conductor"` — embedded agentspan runtime, the new branch.

The new branch is **non-blocking** and mirrors the A2A branch's state machine (start → poll while
running → complete/interrupt/fail), so a single `AGENT` task type serves both runtimes with one
consistent input/output contract.

### 1.1 What this PR review closes

The code for the branch already exists (see §3). The remaining review items from [#1286] are:

1. **Spotless** — `./gradlew :ai:spotlessApply` over all new/changed files.
2. **E2E tests** — a `conductor.ai.agent` test package mirroring the `conductor.ai.a2a` suite.
3. **Examples** — workflow-definition JSON under `ai/examples/` mirroring the A2A examples.
4. **Docs** — a `conductor` section in the AI dev guide plus an examples README entry.
5. **E2E output** — capture and attach the `:ai:test` run for the new package to the PR.

This design covers items 2–5 as concrete file lists; item 1 is a mechanical formatting pass with no
design surface.

### 1.2 Tech stack

| Concern | Choice |
|---|---|
| Language / build | Java 21, Gradle (`:ai` module) |
| Task framework | `WorkflowSystemTask` (async, `isAsync() == true`) |
| Runtime abstraction | `ConductorAgentRuntime` interface (in `:ai`), implemented by `agentspan-server` |
| JSON | Jackson via `com.netflix.conductor.common.config.ObjectMapperProvider` |
| Models | Lombok `@Data`/`@Builder` |
| Tests | JUnit 5, **no mock frameworks** — hand-written in-package fakes (per AGENTS.md) |
| Examples | Workflow-definition JSON (schemaVersion 2) under `ai/examples/` |
| Docs | Markdown under `docs/devguide/ai/` |

## 2. Design principles (reused by every doc)

- **Interface-first / pluggable.** The `:ai` module never compile-depends on `agentspan-server`.
  It depends only on the `ConductorAgentRuntime` interface and its model types, injected as an
  `Optional<ConductorAgentRuntime>`. When the runtime is absent, the branch fails terminally.
- **One task, two branches.** `AgentTask` dispatches on `agentType` at the top of
  `start`/`execute`/`cancel`/`getEvaluationOffset`. Conductor-agent logic lives entirely in
  `ConductorAgentDelegate`; A2A logic stays in `AgentTask`.
- **Non-blocking.** No worker thread is held while an agent runs. `IN_PROGRESS` + re-poll at the
  evaluation cadence.
- **Idempotent at-least-once.** Retries reuse a deterministic idempotency key, from which the
  runtime derives a deterministic workflow id (§4.6). Because that id is the execution store's
  primary key, a re-issued start resolves to the same execution — the guarantee is a source-of-truth
  lookup by id (plus insert-race handling), not the async, best-effort search index. A legacy
  correlationId search is retained only as a back-compat fall-back for runs created before
  deterministic-id derivation.
- **No mocks in tests.** Tests use a hand-written `FakeConductorAgentRuntime` implementing the real
  interface (this is the pattern the review's linked a2a suite follows).

## 3. Complete module / file layout

### 3.1 Existing production files (do not recreate — reference only)

All under `ai/src/main/java/org/conductoross/conductor/ai/`:

| File | Responsibility |
|---|---|
| `agent/ConductorAgentRuntime.java` | Interface: `start`, `getStatus`, `respond`, `cancel`, `listAgents`. |
| `agent/ConductorAgentStartRequest.java` | `@Builder` start request handed to the runtime. |
| `agent/ConductorAgentExecution.java` | Immutable execution snapshot returned by the runtime. |
| `agent/ConductorAgentState.java` | Enum `RUNNING, WAITING, COMPLETED, FAILED, CANCELED`. |
| `agent/ConductorAgentResults.java` | Output-key constants + `writeCompleted`. |
| `agent/ConductorAgentSummary.java` | Descriptor returned by `listAgents()`. |
| `agent/ConductorAgentDelegate.java` | The branch state machine (start/execute/cancel). |
| `a2a/AgentTask.java` | System task; dispatches on `agentType`. |
| `a2a/A2AService.java` | Holds `AGENT_TYPE_A2A`/`AGENT_TYPE_CONDUCTOR` + type predicates. |
| `model/A2ACallRequest.java` | Shared task input for both branches. |

### 3.2 New files to create (this PR review)

**Tests** — `ai/src/test/java/org/conductoross/conductor/ai/agent/`:

| File | Responsibility |
|---|---|
| `ConductorAgentDelegateTest.java` | *Exists.* Unit-level state-machine + guard coverage, with a nested `FakeConductorAgentRuntime`. |
| `FakeConductorAgentRuntime.java` | **New.** The nested fake promoted to a package-visible top-level helper (real, scriptable `ConductorAgentRuntime`) so the e2e tests share one fake. |
| `ConductorAgentEndToEndTest.java` | **New.** Full `AgentTask` start→poll→complete against `FakeConductorAgentRuntime`, with the `WAITING`→resume flow folded in. Mirrors `A2AEndToEndTest`. |
| `ConductorAgentDurabilityTest.java` | **New.** Idempotency-key stability across retries; deadline + poll-failure guards; cancel propagation; runtime-absent failure. Mirrors `A2ADurabilityTest`. |
| `AgentTaskConductorBranchTest.java` | **New.** `agentType` dispatch: `conductor` routes to the delegate, `a2a`/blank does not; `getEvaluationOffset` uses the poll cadence. Mirrors `AgentTaskTest`. |

The e2e tests drive the real `AgentTask` entry points (`start`/`execute`/`cancel`) against the
promoted `FakeConductorAgentRuntime`, exercising the `agentType` dispatch and the delegate together.
No mock frameworks are used. See [testing.md](./testing.md).

**Examples** — `ai/examples/`:

| File | Responsibility |
|---|---|
| `31-conductor-agent-basic.json` | Minimal `agentType: "conductor"` single-task workflow (poll to completion). |
| `32-conductor-agent-human-in-loop.json` | `WAITING` → `HUMAN` task → resume with `executionId`. |
| `33-conductor-agent-multi-agent.json` | Two `AGENT` (conductor) branches via `FORK_JOIN` → `JOIN`. |
| `34-conductor-agent-cancel.json` | Start then cancel an embedded agent run. |
| `README.md` | *Exists.* Add a "Conductor agent (embedded runtime) examples" section with rows for 31–34. |

**Docs** — `docs/devguide/ai/`:

| File | Responsibility |
|---|---|
| `conductor-agents.md` | **New.** Full guide for the `conductor` branch: input/output, lifecycle, human-in-the-loop, guards, enablement. |
| `a2a-integration.md` | *Exists.* Add a short "agentType" cross-link pointing at `conductor-agents.md`. |
| `index.md` | *Exists.* Add `conductor-agents.md` to the AI dev-guide navigation list. |

**PR artifact** (not committed to `docs/`): the captured `:ai:test` output described in
[testing.md §5](./testing.md).

## 4. Shared contracts (verbatim across all docs)

### 4.1 Task type and agentType constants

```
AgentTask.TASK_TYPE             = "AGENT"
A2AService.AGENT_TYPE_A2A       = "a2a"        // default when agentType null/blank
A2AService.AGENT_TYPE_CONDUCTOR = "conductor"  // this branch
```

Dispatch predicates (do not re-implement — call these):

```
A2AService.isA2aAgentType(String)        // null || blank || equalsIgnoreCase "a2a"
A2AService.isConductorAgentType(String)  // equalsIgnoreCase "conductor"
```

### 4.2 Input contract — `A2ACallRequest` fields used by the conductor branch

Both branches share `model/A2ACallRequest`. The fields the conductor branch reads:

| Field | Type | Meaning (conductor branch) |
|---|---|---|
| `agentType` | `String` | Must be `"conductor"` to select this branch. |
| `agentName` | `String` | **Required** on a fresh start. Registered agent name. |
| `agentVersion` | `Integer` | Optional; runtime picks latest when null. |
| `sessionId` | `String` | Optional session/conversation association. |
| `runId` | `String` | Optional caller-supplied run id. |
| `executionId` | `String` | When set, **resume** an in-flight run instead of starting. |
| `context` | `Map<String,Object>` | Extra context values for the run. |
| `text` / `prompt` / `parts` / `message` | see §4.4 | Prompt source. |
| `pollIntervalSeconds` | `Integer` | Poll cadence; default 5. |
| `maxDurationSeconds` | `Integer` | Absolute deadline; default 86400 (24h). |
| `maxPollFailures` | `Integer` | Consecutive transient poll-failure cap; default 30. |

Fields consumed only by the A2A branch (`agentUrl`, `streaming`, `pushNotification`,
`pushBackstopPollSeconds`, `headers`, `historyLength`, `contextId`, `taskId`, `metadata`) are ignored
by the conductor branch and MUST NOT be documented as conductor inputs.

### 4.3 Output contract — `ConductorAgentResults` keys (verbatim)

```
KEY_EXECUTION_ID  = "executionId"       KEY_WAITING      = "waiting"
KEY_AGENT_NAME    = "agentName"         KEY_PENDING_TOOL = "pendingTool"
KEY_SESSION_ID    = "sessionId"         KEY_TEXT         = "text"
KEY_STATE         = "state"             KEY_OUTPUT       = "output"
KEY_STARTED_AT    = "agentStartedAt"    (bookkeeping: epoch millis)
KEY_POLL_FAILURES = "agentPollFailures" (bookkeeping: consecutive transient failures)
```

`KEY_STATE` is written as the uppercase `ConductorAgentState.name()`.

### 4.4 Prompt resolution precedence (verbatim — reused in examples & docs)

`ConductorAgentDelegate.resolvePrompt` picks the first non-blank of, in order:

1. text extracted from `message` (its parts),
2. text extracted from `parts`,
3. `text`,
4. `prompt`.

### 4.5 Lifecycle → Conductor task status mapping (verbatim)

| `ConductorAgentState` | Conductor `TaskModel.Status` | Notes |
|---|---|---|
| `RUNNING` | `IN_PROGRESS` | Keep polling at the evaluation cadence. |
| `WAITING` | `COMPLETED` | Sets `waiting=true`, surfaces `pendingTool`/`text`. Resume with a new `AGENT` call carrying `executionId`. |
| `COMPLETED` | `COMPLETED` | `writeCompleted` surfaces `output` + `text`. |
| `FAILED` | `FAILED` | Sets `reasonForIncompletion`. |
| `CANCELED` | `CANCELED` | Sets `reasonForIncompletion` when present. |

Helper predicates on the enum: `isTerminal()` (COMPLETED/FAILED/CANCELED), `isInterrupted()`
(WAITING). On `ConductorAgentExecution`: `isComplete()`, `isRunning()`, `isWaiting()`.

### 4.6 Idempotency key (verbatim)

```
"conductor-agent-" + workflowInstanceId + ":" + referenceTaskName + ":" + iteration
```

Built from retry-stable identity — **not** `taskId` (which changes per retry attempt).

When a key is present the runtime derives a deterministic workflow id from it
(`UUID.nameUUIDFromBytes(key)` in `AgentService.deriveWorkflowId`) and starts the run under that id.
Since the workflow id is the execution store's primary key, a re-issued start with the same key
either finds the existing run via a source-of-truth lookup by id (`existingWorkflowById`, which reads
the execution store directly, not the search index) or loses the duplicate-insert race and is then
resolved to the existing run. The index-backed correlationId search is a best-effort back-compat
fall-back only.

### 4.7 Liveness guards (verbatim)

- **Guard 1 — absolute deadline.** Anchored once in `KEY_STARTED_AT`; fail terminally after
  `maxDurationSeconds` (default 24h, `DEFAULT_MAX_DURATION_SECONDS = 24*60*60`).
- **Guard 2 — poll-failure cap.** Consecutive transient `getStatus` failures counted in
  `KEY_POLL_FAILURES`; reset to 0 on any success; fail terminally at `maxPollFailures`
  (default `DEFAULT_MAX_POLL_FAILURES = 30`).

### 4.8 Failure semantics (verbatim)

`ConductorAgentDelegate.fail(task, reason, nonRetryable)`:

- `nonRetryable == true` → `FAILED_WITH_TERMINAL_ERROR` (bad input, runtime absent, deadline,
  poll-failure cap, `NonRetryableException`).
- `nonRetryable == false` → `FAILED` (transient runtime call error; the engine retries).

Runtime-absent message (verbatim):
`"Conductor agents require the embedded agentspan runtime (agentspan.embedded=true)"`.

### 4.9 Enablement

The runtime bean is provided by `agentspan-server` when `agentspan.embedded=true`. On a deployment
without it, `Optional<ConductorAgentRuntime>` is empty and any `agentType: "conductor"` task fails
terminally with the §4.8 message. This is the single enablement fact repeated in docs and examples.

## 5. Naming conventions

- Test classes: `ConductorAgentEndToEndTest`, `ConductorAgentDurabilityTest`, and
  `AgentTaskConductorBranchTest`, plus the promoted `FakeConductorAgentRuntime`, in package
  `org.conductoross.conductor.ai.agent`, mirroring `conductor.ai.a2a`.
- Example files: `NN-conductor-agent-<slug>.json`, continuing the `ai/examples/` numbering after 30.
- Doc file: `docs/devguide/ai/conductor-agents.md` (plural, sibling to `a2a-integration.md`).
- JSON field names in examples/docs match `A2ACallRequest` and `ConductorAgentResults` **exactly**.
