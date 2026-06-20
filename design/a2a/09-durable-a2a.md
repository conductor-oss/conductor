# 9. Durable A2A — What the Claim Means, and How We Make It Hold

> **Status:** **implemented** (Direction B — the A2A client). This doc defines what "durable
> A2A" must mean for the claim to be defensible, and the durability mechanisms it describes are
> now in the code and validated by `ai/src/test/.../a2a/A2ADurabilityTest.java`. Property status
> tags: **HOLDS** = satisfied & tested; **PARTIAL** = satisfied with a documented caveat;
> **GAP** = not yet addressed. (Earlier revisions of this doc used these tags to flag the work;
> they now reflect shipped state.)

## 9.1 The claim, stated precisely

> **Durable A2A:** once a Conductor workflow initiates an A2A interaction with a remote agent,
> that interaction is guaranteed to run to a **terminal outcome** (completed, failed, canceled,
> or a clean input/auth hand-off) **despite crashes or restarts of the Conductor server, loss of
> the worker, network partitions, and transient failure or slowness of the remote agent** —
> **without losing the conversation, without hanging forever, and without duplicating
> irreversible agent-side actions** beyond what at-least-once delivery plus an idempotency key
> can prevent.

Three load-bearing words: **guaranteed**, **terminal**, **without losing/hanging/duplicating**.
Every one of them is a testable obligation, enumerated in §9.7. If any fails, we don't get to
say "durable."

A claim "holds" when it is (1) precisely scoped, (2) backed by a mechanism, and (3) proven by a
test that injects the failure. §9.8 is explicit about the **boundary of the promise** — the
honest line past which no client can go — because a claim that overreaches doesn't hold, it just
hasn't been caught yet.

## 9.2 Why this is a real differentiator, not marketing

A2A itself is a thin, mostly stateless request/response protocol over HTTP. **Durability is not
a protocol feature — it is a property of the implementation that orchestrates the interaction
lifecycle across failures.** The reference A2A hosts (the ADK/CrewAI/LangGraph samples, the
purchasing-concierge demo) orchestrate from an **in-memory** host process: if that process
crashes mid-order, the order — and the agent conversation behind it — is gone. There is no
resume.

Conductor is a durable execution engine. By implementing A2A as **native system tasks driven by
the durable task queue and persisted execution store**, the orchestration of every A2A
interaction inherits crash-safety, automatic resumption, at-least-once execution, bounded
retries, and full execution visibility. That is the entire story in one line:

> **The same purchasing-concierge demo, run on Conductor, survives a server restart mid-order.
> The in-memory host does not. That difference is "durable A2A."**

This positioning holds in **both** directions of [08-conductor-implications.md](08-conductor-implications.md):
- **Direction B (client — what we built):** an `AGENT` step is a durable unit of work that
  drives a remote agent to completion across failures. This doc is about Direction B.
- **Direction A (server — future):** when a Conductor workflow is *exposed* as an A2A agent, the
  agent's task **is** a durable workflow execution. Durability is native, not bolted on. Noted
  here only to show the positioning is coherent end-to-end.

## 9.3 What "durable" decomposes into

Eight properties. Each guards a specific failure mode. For each: what Conductor gives us for
free, and the current status of the A2A code.

| # | Property | Guards against | Inherited from Conductor | A2A status |
|---|---|---|---|---|
| P1 | **Crash-safe persistence & resumption** | server restart / redeploy mid-interaction | Persistent execution store; durable decider queue; IN_PROGRESS system tasks re-evaluated on a new instance | **HOLDS** — resume state in task output; proven by T1 |
| P2 | **Guaranteed progress to terminal** (liveness) | agent down/silent forever; lost webhook | `timeoutSeconds` / `responseTimeoutSeconds` *when set* | **HOLDS** — absolute deadline + consecutive-failure cap in `execute()`; push backstop poll; proven by T4a/T4b/T5 |
| P3 | **Effectively-once side effects** | double-send after crash between send and persist | at-least-once + retry (the hazard, not the cure) | **HOLDS** (for cooperating agents) — deterministic, restart-stable `messageId` idempotency key; proven by T2/T3; boundary in §9.8 |
| P4 | **At-least-once execution + bounded retry w/ backoff** | transient network/5xx/429 | task retry (3×, linear backoff); HTTP RetryInterceptor; 429 `Retry-After` | **HOLDS** |
| P5 | **Idempotent, replay-safe callbacks** | duplicate / replayed push webhooks | — (our code) | **HOLDS** — IN_PROGRESS status-guard + constant-time token compare + token expiry; concurrent-callback race settled by the engine rejecting `updateTask` on a terminal task |
| P6 | **Durable multi-turn continuity** | losing the conversation across turns | persistent workflow variables | **HOLDS** (contextId/taskId surfaced in output, threaded by the workflow) |
| P7 | **Observability of in-flight state** | "is it stuck or working?" | task status, execution history, UI, metrics | **HOLDS** — `state`/`taskId`/`contextId` + `a2aStartedAt`/`a2aPollFailures` in output; Micrometer counters via the shared `Monitors` registry (`a2a_client_calls`, `a2a_client_poll_failures`, `a2a_rpc_errors`, `a2a_ssrf_blocked`, `a2a_server_requests`, `a2a_server_resumes`); MDC correlation keys (`a2aWorkflowId`/`a2aTaskId`/`a2aRemoteTaskId`/`a2aContextId`/…); structured warn logs |
| P8 | **Durable secrets at rest** | auth headers persisted in plaintext | Conductor secret references / external payload storage | **PARTIAL** — no header logging; **use `${workflow.secrets...}` / Conductor secrets for auth headers** rather than inline cleartext (usage guidance, not enforced) |

The honest headline: every property now **HOLDS** except P8, which is a usage-guidance item
(don't put raw credentials in task input — reference Conductor secrets). The two that were the
real work — P2 (liveness) and P3 (idempotency) — are closed and tested.

## 9.4 The hard one: exactly-once and the dual-write problem (P3)

This is where most "durable" claims quietly fail, so it gets its own section.

`message/send` is **not idempotent** in general — it can make the agent take an irreversible
action (charge a card, send an email, book a flight). The durable-execution hazard is the gap
between **performing the side effect** and **durably recording that we performed it**:

```
AgentTask.start():
  1. build message (messageId)
  2. a2aService.sendMessage(...)        ← agent may now START IRREVERSIBLE WORK
  3. task.addOutput(taskId); IN_PROGRESS
  ── return to engine ──
  4. executionDAOFacade.updateTask()    ← FIRST durable record of step 2
```

If the server crashes **between 2 and 4**, the agent has acted but Conductor has no record. The
task is still `SCHEDULED` in the store; the durable queue redelivers it (unack timeout); a worker
runs `start()` **again**. Today step 1 generates a **fresh random `messageId`** (`UUID.randomUUID()`
in `AgentTask.buildMessage`), so the re-send looks like a brand-new message → the agent does
the work **twice**.

You cannot eliminate this window from the client side alone — it is the same impossibility as
exactly-once delivery. What you *can* do is the industry-standard pattern (Stripe idempotency
keys, Temporal deterministic ids): **make the request carry a stable idempotency key so the
receiver can dedupe**, and make at-least-once + dedupe = effectively-once.

### The key insight: a deterministic, restart-stable `messageId`

The `messageId` must be:
- **identical across retries and restarts** of the same logical call (so a re-send is recognized
  as the same message), and
- **distinct per logical invocation** (so a different loop iteration is a genuinely new message).

Conductor's `TaskModel` gives us exactly the right stable identity (verified — all fields exist):

```
messageId = "a2a-" + sha256(workflowInstanceId + ":" + referenceTaskName + ":" + iteration)
```

- **Stable across retries:** Conductor task retry creates a *new* `taskId` but reuses the same
  `referenceTaskName` and `iteration` → same key. (Basing the key on `taskId` would be wrong —
  it changes per retry.)
- **Stable across restarts:** all three inputs are persisted before `start()` runs.
- **Unique per `DO_WHILE` iteration:** `iteration` differs → new key, as it should.
- **User-overridable:** if the caller sets `message.messageId`, honor it.

This turns the deterministic id into a true idempotency key. Combined with at-least-once delivery,
an A2A agent that dedupes on `messageId` gets **effectively-once**.

### The honest contract (this is what makes the claim hold)

A2A does **not** mandate that agents dedupe on `messageId`. So the precise, defensible promise is:

> Conductor guarantees a **stable idempotency key** and **at-least-once delivery** with a small,
> bounded duplication window. For agents that honor the key, the effect is **exactly-once**. For
> agents that do not, duplicates are minimized but not eliminated — because the side effect lives
> at the agent, true exactly-once is the agent's responsibility, as it must be in any distributed
> system.

Overstating this (claiming unconditional exactly-once) is exactly how the claim *fails to hold*.
Stating the boundary is how it holds.

### Optional hardening: recovery-by-query (capability-gated)

For agents on A2A **v1.0** that support `tasks/list` with a `contextId` filter, we can close the
window further: default `contextId = workflowInstanceId`-derived, and on a re-run of `start()`,
**query for an existing task in this context before re-sending**; if found, resume it instead of
re-sending. This is an enhancement (v1.0 + agent support required), not the baseline. The baseline
is the deterministic `messageId`.

## 9.5 Failure-mode catalog

The matrix the claim must survive. "Today" = current code; "Target" = with §9.6 changes.

| Failure | Today | Target |
|---|---|---|
| Crash **before** send | task still SCHEDULED → re-run `start()` → sends once. ✅ | unchanged ✅ |
| Crash **after** send, **before** persist | re-run `start()` → **re-sends with new random id** → possible double-action ❌ | re-send with **same deterministic `messageId`** → agent dedupes (effectively-once) ✅ |
| Crash **during poll** (IN_PROGRESS) | engine re-evaluates; `execute()` re-reads `taskId` from output, resumes polling ✅ | unchanged ✅ + persisted attempt counter survives |
| Crash **during streaming** | in-memory aggregation lost; re-run re-streams from scratch ❌ (best-effort) | document as best-effort; **degrade to poll** on disconnect; deterministic id limits duplication ✅ |
| Agent **down** while polling | `execute()` swallows error, returns false, **polls forever** (no effective timeout) ❌ | bounded: **max consecutive transient failures** → terminal `FAILED`; total **deadline** ✅ |
| Agent **slow** (valid, long) | keeps polling — correct ✅ | unchanged; covered by configurable deadline, not response-timeout ✅ |
| **Push webhook never arrives** (agent died / URL unreachable) | `isAsyncComplete` task waits **forever** (no poll, no default timeout) ❌ | **backstop poll** at a slow interval and/or mandatory deadline ✅ |
| **Duplicate push** callback | status-guard makes 2nd a no-op ✅; concurrent pair races on `updateTask` ⚠️ | guard + rely on engine's terminal-state rejection; document ✅ |
| **Replayed** push token | constant-time compare + 24h expiry ✅ | unchanged ✅ |
| Network **partition** mid-call | `A2AException` (retryable) → task retried; **re-send hazard** as above ❌→ | deterministic id makes retry safe ✅ |
| Poison agent (always 4xx) | `NonRetryableException` → `FAILED_WITH_TERMINAL_ERROR`, no retry ✅ | unchanged ✅ |

## 9.6 Proposed changes (concrete)

Ordered by importance to the claim.

**C1 — Deterministic `messageId` (closes P3). ✅ SHIPPED.**
`AgentTask.buildMessage`, when the caller hasn't supplied one, derives
`messageId = "a2a-" + workflowInstanceId + ":" + referenceTaskName + ":" + iteration` instead of
`UUID.randomUUID()`. (Readable concatenation rather than a hash — the value is an opaque string;
debuggability wins and uniqueness/stability are what matter.) Stable across retries/restarts,
unique per iteration. The single highest-value change.

**C2 — Liveness guards so nothing hangs (closes P2).**
- Track a **consecutive-transient-failure counter** in task output (e.g. `a2aPollFailures`).
  In `execute()`, increment on a transient poll error, reset on success; after `maxPollFailures`
  (default e.g. 10) → terminal `FAILED` with a clear reason instead of polling forever.
- Enforce a **deadline**: record the start time in output; if `now - start > maxDurationSeconds`
  (configurable; sensible default tied to the push-token TTL, e.g. 24h) → terminal `FAILED`
  ("A2A agent did not reach a terminal state within the deadline"). Do **not** rely on
  `responseTimeoutSeconds` — each poll's `updateTask` resets it, so it never fires for a
  polling task (verified).
- Have `AgentTaskMapper` default `timeoutSeconds`/`timeoutPolicy` to a finite, overridable
  value rather than 0 (unbounded), as a backstop independent of our own deadline logic.

**C3 — Durable push: backstop poll (closes the push hole in P2).**
Pure push (`isAsyncComplete=true`, no polling) hangs forever if the webhook is lost. For the
durable posture, push mode should **also poll at a slow backstop interval** (e.g. every few
minutes) so the task still completes if the callback never arrives — the webhook just makes it
faster. This means *not* setting `isAsyncComplete`, and instead returning a large
`getEvaluationOffset` while the push config is registered. Net: ~one backstop poll per N minutes
vs ~one per few seconds for pure polling — the efficiency win of push, without the liveness risk.
(Keep pure-push available as an explicit opt-in for users who accept the deadline as the only
backstop.)

**C4 — Default `contextId = workflowInstanceId`. ❌ DROPPED (spec-correctness).** On reflection
this is spec-questionable: A2A `contextId` is **server-generated** — the agent assigns it on the
first response. A client pre-assigning a `contextId` on a *new* conversation can confuse strict
agents. The durability mechanism (P3) is the deterministic **`messageId`**, which needs no
client-chosen `contextId`. We keep the spec-correct flow: the agent generates `contextId`, we
capture it in output, the workflow threads it into the next turn. (The recovery-by-query
enhancement in §9.4 remains a v1.0-only optional follow-up.)

**C5 — Streaming honesty + degrade-to-poll. ✅ SHIPPED.** Documented as **best-effort, not durable**
(in-memory aggregation is lost on crash). A stream that drops *after* yielding a `taskId`
degrades to `tasks/get` polling automatically (the aggregated non-terminal task → IN_PROGRESS →
`execute()` polls). A stream that yields **nothing** is treated as transient and retried (no
false COMPLETE). Durability-sensitive users should prefer poll/push.

**C6 — Secrets at rest (P8). ◐ PARTIAL (guidance).** No header values are logged. The remaining
item is usage guidance — auth headers in task input are persisted; reference Conductor
**secrets** / `${workflow.secrets...}` rather than inline cleartext. Not mechanically enforced.

**C7 — Callback idempotency + observability (P5/P7). ✅ SHIPPED.** IN_PROGRESS status-guard kept;
the engine rejecting `updateTask` on an already-terminal task settles the concurrent-callback
race. Counters (`a2aStartedAt`, `a2aPollFailures`) surfaced in output; structured warn logs on
transient poll failures with the failure count and bound.

## 9.7 Proof obligations — the claim holds only if these pass

Each maps to a property and must be an automated test that **injects the failure**.

Each maps to a property and an automated test that **injects the failure**. All green.

| Test | Proves | Where | Status |
|---|---|---|---|
| **T1 crash-recovery** | P1 | `A2ADurabilityTest.t1_crashRecovery_resumesOnAFreshInstance` (fresh `A2AService`+`AgentTask` resume the persisted `TaskModel`) **and** `t1b_crashRecovery_survivesPersistenceRoundTrip` (the durable task state is serialized to JSON — as the execution DAO stores it — and a **cold** `TaskModel` reconstructed from that JSON alone resumes to completion) | ✅ |
| **T2 idempotency key** | P3 | `t2_messageId_isStableAcrossRetries` — two attempts with the same `(workflowId, ref, iteration)` but different `taskId` send an **identical `messageId`** (+ `t2_callerCanOverrideMessageId`) | ✅ |
| **T3 distinct per iteration** | P3 | `t3_messageId_distinctPerIteration` — different iteration → different `messageId` | ✅ |
| **T4 liveness / dead agent** | P2 | `t4_deadAgent_failsWithinFailureCap` (failure cap) + `t4_deadline_failsTerminally` (absolute deadline) → terminal `FAILED`, not infinite polling | ✅ |
| **T5 push backstop** | P2 | `t5_pushBackstop_completesWithoutWebhook` — push mode, no webhook ever fires; backstop poll completes it; offset confirmed slow | ✅ |
| **T6 duplicate / expired callback** | P5 | `A2ACallbackResourceTest` — 2nd push is a no-op; expired & mismatched tokens rejected | ✅ |
| **T7 retry safety** | P3/P4 | folded into T2 (a Conductor retry is a new `taskId`, same identity → same `messageId`) | ✅ |

Why these prove crash-recovery without an OS-level kill: the engine's `AsyncSystemTaskExecutor`
**reloads the `TaskModel` from the persistence store on every execution cycle** (`loadTaskQuietly`
→ `getTaskModel(taskId)`) — it holds no in-memory state between cycles. So "a restarted worker
re-drives the task" is operationally identical to "T1b reconstructs a cold `TaskModel` from the
persisted JSON and a fresh `AgentTask` resumes it." T1b exercises exactly that data boundary in
CI.

**Full-process proof (demonstrated).** The OS-level version now exists as a runnable demo —
`ai/src/test/resources/a2a/durable-demo/run-durable-demo.sh`: it starts a real persistent
(SQLite) Conductor + a remote A2A agent, places an order via a `AGENT` workflow, **`kill -9`s
the server mid-order**, restarts it on the same store, and the order resumes and completes. Verified
output: `workflow status: COMPLETED — receipt: Order ORD-… confirmed`. This is the genuine
crash-survival proof (real process kill, real persistence, real resume). It uses the
`conductor.a2a.client.allow-private-network` opt-in (the SSRF guard blocks loopback/private agent
URLs by default; the flag is also a legitimate feature for agents on a trusted private network). A
CI-automated `test-harness` variant (cf. `AIReasoningEndToEndTest`) remains a nice-to-have.

## 9.8 The boundary of the promise (so the claim doesn't overreach)

What durable A2A on Conductor **does** guarantee:
- The interaction survives Conductor crashes/restarts and resumes automatically (P1).
- It always reaches a terminal state within a bounded time — it never hangs forever (P2).
- It is retried safely with a stable idempotency key; agents that dedupe get exactly-once (P3/P4).
- The conversation and its context persist across turns and failures (P6).
- Operators can see and reason about in-flight state (P7).

What it **cannot** guarantee, and why that's fine:
- **Unconditional exactly-once side effects at the agent.** Impossible for any client when the
  side effect is remote and the agent doesn't dedupe — this is the at-least-once-vs-exactly-once
  theorem, not a Conductor limitation. We provide the idempotency key; the agent must honor it.
- **Durability of an in-flight SSE stream's partial output.** Streaming trades durability for
  latency by design; poll/push are the durable paths.
- **Recovery of work an agent did but never reported and cannot be re-queried.** Mitigated by the
  deterministic key and (on v1.0) recovery-by-query, but bounded by what the agent exposes.

Stating these is not weakness — it is precisely what lets "durable A2A" be a claim that **holds**
under scrutiny rather than a slogan that fails on the first incident review.

## 9.9 One-line positioning

> **Durable A2A:** every agent interaction is a crash-safe, automatically-resumed, idempotently-keyed
> unit of durable work that is guaranteed to reach a terminal outcome — because Conductor runs it,
> not an in-memory host loop.
