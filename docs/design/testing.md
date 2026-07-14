# Conductor-Agent Branch — e2e Test Plan

Addresses review item 2 ("add more e2e tests, following the `conductor.ai.a2a` suite") and
item 5 ("run e2e and attach the output"). Test-class names, states, keys, and the
idempotency-key shape are reused verbatim from [architecture.md](./architecture.md) §3.2/§4/§5
and [data-model.md](./data-model.md).

## 1. Pattern to mirror

The `conductor.ai.a2a` suite pairs a hand-written, in-package fake with focused one-concern
tests (`A2AEndToEndTest`, `A2ADurabilityTest`, `AgentTaskTest`) — **no mock frameworks**
(AGENTS.md). The conductor branch is exercised through the same `WorkflowSystemTask` entry
points on `AgentTask` (`start` / `execute` / `cancel` / `getEvaluationOffset`) against a real
`ConductorAgentRuntime` fake.

## 1a. Coverage mapping to the `conductor.ai.a2a` suite

Review item 2 asks for tests "following the a2a suite." The a2a package has nine test artifacts;
the conductor branch mirrors the ones whose concern also exists on this branch and intentionally
omits the a2a-only ones. This table is the explicit scope decision — it is the answer to "did you
cover everything the a2a suite covers?"

| `conductor.ai.a2a` artifact | Concern | Conductor-branch counterpart |
|---|---|---|
| `EmbeddedA2AAgent` | In-process real agent to test against | `FakeConductorAgentRuntime` (§2) |
| `A2AEndToEndTest` | Full start→poll→complete lifecycle | `ConductorAgentEndToEndTest` (§3.1) |
| `A2ADurabilityTest` | Idempotency, deadline, retries, cancel | `ConductorAgentDurabilityTest` (§3.2) |
| `AgentTaskTest` | `agentType` dispatch + eval offset | `AgentTaskConductorBranchTest` (§3.3) |
| `A2AServiceTest` | Predicate/helper coverage | Folded into `AgentTaskConductorBranchTest` (dispatch asserts `isConductorAgentType`); the shared `A2AService` predicates are already covered by `A2AServiceTest`, so they are not re-tested. |
| `A2ACallbackResourceTest` | Push-notification callback HTTP resource | **Omitted** — the conductor branch is poll-only; it has no callback resource. |
| `A2ASdkInteropTest` | Wire-compat with the A2A client SDK | **Omitted** — no external protocol; the branch calls the in-process `ConductorAgentRuntime` interface. |
| `A2AObservabilityTest` | `A2AMetrics`/`A2ALogging` emission | **Omitted for now** — the branch adds no metrics/logging component of its own; add an analog only if `ConductorAgent*` observability is introduced. Called out as a known gap in the PR description. |
| `A2ARealAgentIntegrationTest` | Live remote agent (opt-in) | **Out of scope** — requires a live agentspan runtime; covered by the manual `agentspan.embedded=true` verification in §4, not an automated test. |

The three mirrored classes plus the existing `ConductorAgentDelegateTest` give the branch the same
lifecycle/durability/dispatch coverage the a2a suite has, without inventing tests for machinery this
branch does not have.

## 2. Shared test fake — `FakeConductorAgentRuntime`

`FakeConductorAgentRuntime` already exists as a nested class inside `ConductorAgentDelegateTest`.
Promote it to a package-visible top-level helper so the new classes share one fake
(architecture.md §3.2):

- **File:** `ai/src/test/java/org/conductoross/conductor/ai/agent/FakeConductorAgentRuntime.java`
- **Behavior (unchanged from the nested version):** a real `ConductorAgentRuntime` returning
  pre-set `startResult` / `statusResult` snapshots; can throw from `getStatus`
  (`throwOnStatus`) to drive Guard 2; records `lastStartRequest`, `lastRespondExecutionId`,
  `lastRespondMessage`, `lastCancelExecutionId`, `lastCancelReason`.
- `ConductorAgentDelegateTest` is updated to reference the promoted helper instead of its inner
  copy; its assertions are unchanged.

Snapshots are built with `ConductorAgentExecution.builder()` (data-model.md §1.3) using the
`ConductorAgentState` values from data-model.md §1.4.

## 3. New test files

All under `ai/src/test/java/org/conductoross/conductor/ai/agent/` (architecture.md §3.2).

### 3.1 `ConductorAgentEndToEndTest` (mirrors `A2AEndToEndTest`)

Full lifecycle through `AgentTask` built with `Optional.of(fakeRuntime)`:

1. Input map `{ "agentType": "conductor", "agentName": "planner", "text": "..." }`.
2. `start(...)` with a `RUNNING` snapshot → task `IN_PROGRESS`; assert outputs `executionId`,
   `agentName`, `sessionId`, `state == "RUNNING"`, and `agentStartedAt` set once.
3. Advance the fake to `COMPLETED`; `execute(...)` returns `true`, task `COMPLETED`, outputs
   carry `output` and `text` (`KEY_OUTPUT`, `KEY_TEXT`).
4. **Waiting + resume** (folded into this class, per architecture.md's file list): a `WAITING`
   snapshot completes the task with `waiting == true`, `pendingTool` present, `state ==
   "WAITING"`; a second `AGENT` call with `executionId` + `text` asserts `respond` received
   `{"result": "<text>"}` and the resumed snapshot routes to `COMPLETED`.
5. `getEvaluationOffset` returns `pollIntervalSeconds` (default 5) for this branch.

### 3.2 `ConductorAgentDurabilityTest` (mirrors `A2ADurabilityTest`)

- **Idempotency:** two `start` calls on the same `(workflowInstanceId, referenceTaskName,
  iteration)` produce the byte-for-byte key `conductor-agent-<wf>:<ref>:<iter>`
  (architecture.md §4.6); changing `iteration` changes the key.
- **Guard 1 (deadline):** small `maxDurationSeconds` + back-dated `agentStartedAt` → `execute`
  fails with `FAILED_WITH_TERMINAL_ERROR` and the "exceeded max duration" reason.
- **Guard 2 (poll-failure cap):** `throwOnStatus=true` + low `maxPollFailures` →
  `agentPollFailures` increments, terminal failure at the cap; a subsequent success resets the
  counter to `0`.
- **Runtime-absent:** `AgentTask` built with `Optional.empty()` fails terminally with the exact
  message in architecture.md §4.8.
- **Cancel:** `cancel(...)` propagates the reason to the fake and sets task `CANCELED`.

### 3.3 `AgentTaskConductorBranchTest` (mirrors `AgentTaskTest`)

Dispatch-only coverage on `AgentTask`:

- `agentType: "conductor"` routes `start` / `execute` / `cancel` to the delegate (confirmed via
  the fake's recorded calls); `agentType: "a2a"` / blank does **not**.
- `getEvaluationOffset` uses the poll cadence for the conductor branch (no push backstop).

## 4. Running the suite and attaching output (review item 5)

```bash
./gradlew spotlessApply
./gradlew :ai:test --tests "org.conductoross.conductor.ai.agent.*"
```

Confirm the module coordinate (`:ai`) against `ai/build.gradle` before pasting into the PR.
Attach the console summary, e.g.:

```
> Task :ai:test
ConductorAgentEndToEndTest     PASSED
ConductorAgentDurabilityTest   PASSED
AgentTaskConductorBranchTest   PASSED
ConductorAgentDelegateTest     PASSED

BUILD SUCCESSFUL
```

No external service is needed — `FakeConductorAgentRuntime` runs in-process. If any manual step
can't be verified live, mark it `<!-- TODO: verify against live server -->` per AGENTS.md and
note it in the PR.

## 5. Conventions honored

- No mock frameworks; the fake is a real `ConductorAgentRuntime`.
- Assertions verify observable task status/output, not re-implemented logic (data-model.md §4).
- One concern per class, matching the `conductor.ai.a2a` package.
