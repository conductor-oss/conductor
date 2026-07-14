# Test Plan — Conductor-Agent Runtime Branch

Authoritative test-strategy design for the `agentType: "conductor"` branch. It closes review
item 2 ("add more e2e tests, **following the `conductor.ai.a2a` suite**") and item 5 ("run e2e
and attach the output") by mapping the **entire** `conductor.ai.a2a` suite class-by-class onto
the conductor branch, so nothing in the reference suite is silently skipped.

All names, keys, states, and the idempotency-key shape are reused verbatim from
[architecture.md](./architecture.md) §3.2/§4 and [data-model.md](./data-model.md) §1/§4.
[testing.md](./testing.md) holds the per-method specs for the three new e2e classes; this
document is the coverage matrix and the rationale that governs it. The two files agree on the
same class list — if they ever diverge, this matrix wins.

## 1. Why the conductor suite is smaller than the a2a suite (and correctly so)

The a2a branch is a **remote, wire-protocol** integration: JSON-RPC 2.0 over HTTP, SSE
streaming, push-notification callbacks, agent-card discovery, and a Conductor-hosted A2A
*server*. Its tests must stand up a real socket (`EmbeddedA2AAgent`), exercise a real SDK
(`A2ASdkInteropTest`), and cover a callback receiver and server resources.

The conductor branch is a **poll-only, in-process** integration against a Java interface
(`ConductorAgentRuntime`). It has: no socket, no wire format, no streaming, no push callbacks,
no discovery endpoint, and no server side. So the reference classes that exist *only* to test
those transport concerns have no analogue here — and inventing one would test framework code,
not this branch. The matrix in §2 records that decision explicitly rather than leaving it
implied.

## 2. Coverage matrix — every `conductor.ai.a2a` class mapped

Reference suite (verified present under
`ai/src/test/java/org/conductoross/conductor/ai/a2a/`): `EmbeddedA2AAgent`,
`A2AEndToEndTest`, `A2ADurabilityTest`, `AgentTaskTest`, `A2AServiceTest`,
`A2AObservabilityTest`, `A2ARealAgentIntegrationTest`, `A2ASdkInteropTest`,
`A2ACallbackResourceTest`, and `server/{A2AServerResourceTest, A2ALoopbackTest,
A2AWorkflowAgentTest}`.

| a2a reference class | Concern it covers | Conductor-branch equivalent | Status |
|---|---|---|---|
| `EmbeddedA2AAgent` | Real test double (embedded HTTP agent) | `FakeConductorAgentRuntime` — a real, in-process `ConductorAgentRuntime` | **Mirror** |
| `A2AEndToEndTest` | Full lifecycle: start → poll → complete/interrupt | `ConductorAgentEndToEndTest` | **Mirror (new)** |
| `A2ADurabilityTest` | Idempotency, deadline, retry/failure caps, cancel | `ConductorAgentDurabilityTest` | **Mirror (new)** |
| `AgentTaskTest` | System-task entrypoints + branch dispatch + eval offset | `AgentTaskConductorBranchTest` | **Mirror (new)** |
| `A2AServiceTest` | Service helpers, request building, prompt resolution | `ConductorAgentDelegateTest` (exists) + predicate assertions (§4.3) | **Mirror (extend)** |
| `A2AObservabilityTest` | Micrometer counters + MDC log scope | Branch emits **no metrics** — only `log.warn` on transient poll failures | **N/A — no metric surface** (§4.4) |
| `A2ARealAgentIntegrationTest` | Opt-in run vs. a real external agent over HTTP | No remote endpoint; a real run needs `agentspan-server`, which `:ai` must not depend on | **N/A — module boundary** (§4.5) |
| `A2ASdkInteropTest` | Interop with a non-Conductor A2A SDK agent | No wire protocol; the runtime is a Java interface, not a protocol | **N/A — no wire format** |
| `A2ACallbackResourceTest` | Push-notification callback receiver | Branch is poll-only; there is no callback/push path | **N/A — poll-only** |
| `server/A2AServerResourceTest` | A2A *server* REST resource | Branch has no server component | **N/A — no server side** |
| `server/A2ALoopbackTest` | Conductor-calling-Conductor over A2A | No self-hosted protocol to loop back through | **N/A — no server side** |
| `server/A2AWorkflowAgentTest` | Workflow-as-A2A-agent adapter | Not part of the AGENT-task branch | **N/A — out of scope** |

Net: the four "Mirror" rows (`FakeConductorAgentRuntime` + three e2e classes) plus the extended
`ConductorAgentDelegateTest` are the complete in-scope suite. Seven a2a classes are `N/A` for a
transport reason recorded above — not because they were overlooked.

## 3. Test artifacts to create / change

Under `ai/src/test/java/org/conductoross/conductor/ai/agent/` (architecture.md §3.2):

| File | Kind | Source of specs |
|---|---|---|
| `FakeConductorAgentRuntime.java` | Promoted helper (real `ConductorAgentRuntime`) | testing.md §2 |
| `ConductorAgentDelegateTest.java` | Exists; extended per §4.2–§4.4 | this doc §4 |
| `ConductorAgentEndToEndTest.java` | New | testing.md §3.1 |
| `ConductorAgentDurabilityTest.java` | New | testing.md §3.2 |
| `AgentTaskConductorBranchTest.java` | New | testing.md §3.3 |

No new production code — these test only the shipped classes in
`ai/src/main/java/org/conductoross/conductor/ai/agent/` and `a2a/AgentTask.java`.

## 4. Coverage obligations (the assertions each concern must prove)

Numbered to the invariants in [data-model.md §4](./data-model.md) and the guards in
[architecture.md §4.5–§4.8](./architecture.md).

### 4.1 Lifecycle (E2E)
- `RUNNING` snapshot → task `IN_PROGRESS`; outputs `executionId`, `agentName`, `sessionId`,
  `state == "RUNNING"`, `agentStartedAt` written once (invariant 2).
- `COMPLETED` snapshot → task `COMPLETED`; `output` + `text` surfaced via `writeCompleted`.
- `WAITING` snapshot → task `COMPLETED` with `waiting == true`, `pendingTool`, `state ==
  "WAITING"` (invariant 4); resume via a second AGENT call with `executionId` sends
  `respond(executionId, {"result": <prompt>})` and the resumed snapshot routes to `COMPLETED`.

### 4.2 Durability & guards (Durability)
- **Idempotency (invariant 5):** identical `(workflowInstanceId, referenceTaskName, iteration)`
  yields byte-for-byte `conductor-agent-<wf>:<ref>:<iter>`; changing `iteration` changes it.
- **Guard 1 (deadline):** back-dated `agentStartedAt` + small `maxDurationSeconds` →
  `FAILED_WITH_TERMINAL_ERROR` with the "exceeded max duration" reason.
- **Guard 2 (poll-failure cap, invariant 3):** `throwOnStatus=true` increments
  `agentPollFailures` and fails terminally at `maxPollFailures`; a later success resets it to 0.
- **Runtime-absent:** `AgentTask` built with `Optional.empty()` fails terminally with the exact
  message in architecture.md §4.8.
- **Cancel:** `cancel(...)` propagates the reason to the fake and sets task `CANCELED`; a
  best-effort cancel still marks the task `CANCELED` even if the runtime throws.

### 4.3 Dispatch & predicates (AgentTask branch + service assertions)
- `agentType: "conductor"` routes `start`/`execute`/`cancel` to the delegate (proven via the
  fake's recorded calls); `agentType: "a2a"` and blank/null do **not** (invariant: default is
  a2a).
- `getEvaluationOffset` returns the poll cadence (`pollIntervalSeconds`, default 5) for the
  conductor branch — no push backstop.
- Predicate contract from architecture.md §4.1: `A2AService.isConductorAgentType("conductor")`
  is true (case-insensitive); `isConductorAgentType(null/blank/"a2a")` is false;
  `isA2aAgentType(null/blank/"a2a")` is true. Assert these two constants/predicates directly
  (the a2a-suite `A2AServiceTest` analogue) so the dispatch keys can't silently drift.

### 4.4 Prompt resolution (delegate unit)
Assert `resolvePrompt` precedence from architecture.md §4.4 with a table-style case per source:
`message` parts win over `parts`, which win over `text`, which wins over `prompt`; all-blank
inputs yield a blank prompt and a fresh start fails with "requires 'text' or 'prompt'".

### 4.5 Deliberately not covered (with reason)
- **Metrics/observability:** the branch has no Micrometer counters (unlike `A2AMetrics`); the
  only observable side effect is a `log.warn` per transient poll failure, already asserted
  indirectly by the Guard-2 counter. A log-appender assertion would test slf4j, not this
  branch — skipped per AGENTS.md "test actual behavior."
- **Real external runtime:** an opt-in integration test against a live agentspan runtime would
  require a compile/runtime dependency `:ai` must not take (architecture.md §2, principle 1).
  If such a test is ever added it belongs in `agentspan-server`, not `:ai`, and must be
  `@EnabledIfEnvironmentVariable`-gated like `A2ARealAgentIntegrationTest`.

## 5. Running the suite and capturing output (review item 5)

```bash
./gradlew spotlessApply
./gradlew :ai:test --tests "org.conductoross.conductor.ai.agent.*"
```

Confirm the module coordinate (`:ai`) against `ai/build.gradle` before pasting into the PR.
The whole suite runs in-process against `FakeConductorAgentRuntime`; no socket, container, or
external service is required (this is the payoff of the poll-only design). Attach the Gradle
console summary plus the per-class results from
`ai/build/reports/tests/test/index.html`, e.g.:

```
> Task :ai:test
ConductorAgentDelegateTest      PASSED
ConductorAgentEndToEndTest      PASSED
ConductorAgentDurabilityTest    PASSED
AgentTaskConductorBranchTest    PASSED

BUILD SUCCESSFUL
```

If a step cannot be verified at authoring time, mark it
`<!-- TODO: verify against live server -->` and call it out in the PR description (AGENTS.md).

## 6. Conventions honored
- No mock frameworks; `FakeConductorAgentRuntime` is a real `ConductorAgentRuntime` (AGENTS.md).
- One concern per class, mirroring the `conductor.ai.a2a` package layout.
- Assertions verify observable task status/output, never re-implement delegate logic
  (data-model.md §4).
- Every `N/A` in §2 names the transport reason, so the smaller suite is a reviewed decision.
</content>
</invoke>
