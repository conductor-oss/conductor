# 10. A2A Server — Conductor Workflows as A2A Agents (Direction A)

> **Status: implemented.** The other half of [08-conductor-implications.md](08-conductor-implications.md)
> §8.3: Conductor as an A2A **server**. Any A2A client (Google ADK, CrewAI, LangGraph, another
> Conductor) can discover and invoke a Conductor **workflow as an A2A agent**. This is where
> Conductor's durability is *native* — a workflow execution **is** a durable, resumable,
> observable A2A task (see [09-durable-a2a.md](09-durable-a2a.md)).

## 10.1 Model — one A2A agent per workflow

Each exposed workflow is its own focused A2A agent. The URL path is the router — no skill-selection
convention to invent, and each Agent Card describes exactly one capability.

```
GET  {basePath}/{workflow}/.well-known/agent-card.json   (+ /agent.json, v0.2.x)  → discovery
POST {basePath}/{workflow}                               → JSON-RPC: message/send | tasks/get | tasks/cancel
GET  {basePath}                                          → convenience listing of exposed agents
```

`basePath` defaults to `/a2a` (`conductor.a2a.server.basePath`). Lives in the `ai` module
(`org.conductoross.conductor.ai.a2a.server`), component-scanned by the server, gated by
`conductor.a2a.server.enabled=true` (independent of the LLM/AI integration flag — the server side
only needs core `WorkflowService`/`MetadataService`).

## 10.2 Exposure — opt-in, never everything

A workflow is exposed iff **either**:
- its name is in `conductor.a2a.server.exposed-workflows`, **or**
- its `WorkflowDef.metadata` carries `a2a.enabled: true`.

Neither set ⇒ nothing is exposed. Optional `WorkflowDef.metadata."a2a.tags"` populates the skill
tags. (`A2AWorkflowAgent.isExposed`.)

## 10.3 Mapping

**`message/send` → start workflow.** Input = the first `DataPart.data` (structured case) merged
with `{_a2a_text, _a2a_message_id, _a2a_context_id}` so the workflow can read the raw message;
`correlationId = contextId`. Returns an A2A `Task { id=workflowId, contextId=correlationId, status }`.

**`WorkflowStatus` → A2A `TaskState`** (the reverse of the client mapping in §8.4):

| Conductor | A2A | Notes |
|---|---|---|
| RUNNING, blocked on `HUMAN`/`WAIT` | `input-required` | non-terminal HUMAN/WAIT task present; a follow-up `message/send` carrying this task's id resumes the execution (see §10.4) |
| RUNNING (not blocked) | `working` | |
| COMPLETED | `completed` | `workflow.getOutput()` → an `Artifact` (a `DataPart`) |
| FAILED / TIMED_OUT | `failed` | `reasonForIncompletion` in the status message |
| TERMINATED | `canceled` | |
| PAUSED | `working` | admin pause has no clean A2A analog |

**`tasks/get`** → `getExecutionStatus(id, true)` → map as above. **`tasks/cancel`** →
`terminateWorkflow(id)` → return the canceled task. An agent only manages its own workflow's
executions (the task's workflow name must match the path agent).

**`WorkflowDef` → Agent Card:** one skill (`id`/`name` = workflow name, description from the def,
tags from metadata, input/output modes from properties); `url` = `{publicUrl|request-derived}{basePath}/{name}`;
`version` = def version; `protocolVersion` `0.3.0`; `capabilities.streaming/pushNotifications=false`
(server-side streaming/push are follow-ups).

## 10.4 Durability — why this is the differentiator

A Conductor workflow execution is already crash-safe, resumable, retryable, and observable. By
mapping an A2A task onto a workflow execution, **the A2A agent inherits all of that for free** — no
host loop to lose state. Two concrete properties:

- **Crash-safe agent tasks.** If Conductor restarts mid-execution, the A2A task survives and
  `tasks/get` keeps returning correct state — the in-memory reference hosts (the purchasing-concierge
  demo) lose the task on crash.
- **Effectively-once start (idempotent `message/send`).** The inbound A2A `messageId` is used as the
  workflow `StartWorkflowRequest.idempotencyKey` (namespaced with the workflow name) with
  `idempotencyStrategy = RETURN_EXISTING`, so a client's retried `message/send` returns the
  **existing** execution instead of starting a duplicate — the server-side mirror of the client's
  deterministic-`messageId` work (§09 P3). `tasks/get` is a read; `tasks/cancel` no-ops once terminal.
  So the whole surface is safe under at-least-once delivery.
- **Durable multi-turn (input-required → resume).** When the workflow blocks on a `HUMAN`/`WAIT`
  task the agent reports `input-required`. A follow-up `message/send` carrying that task's id (the
  workflow id) completes the pending task with the message content and **resumes the same
  execution** — no duplicate workflow is started; an already-terminal or non-blocked workflow just
  returns its current state. `A2AWorkflowAgent.resume()` via `TaskService.updateTask`.
- **Observability.** Server requests and resumes are counted (`a2a_server_requests{method}`,
  `a2a_server_resumes`) through the shared `Monitors` registry (counter emitted only for recognized
  methods — never the client-controlled `method` string), and the dispatch path sets MDC correlation
  keys (`a2aAgent`/`a2aMethod`/`a2aMessageId`/`a2aContextId`/`a2aRemoteTaskId`) for greppable logs.

## 10.5 Security

OSS Conductor REST is open by default; the A2A server matches that. Optional shared secret
`conductor.a2a.server.api-key` — when set, the card + JSON-RPC + listing endpoints require it as
`Authorization: Bearer <key>` (or `X-A2A-Server-Key`), constant-time compared. For production,
layer gateway/mTLS/OIDC in front (A2A puts identity in HTTP headers — see [05-security.md](05-security.md)).

## 10.6 Configuration

```properties
conductor.a2a.server.enabled=true
conductor.a2a.server.basePath=/a2a
conductor.a2a.server.exposed-workflows=order_pizza,book_flight
conductor.a2a.server.public-url=https://conductor.example.com   # optional; else request-derived
conductor.a2a.server.api-key=...                                # optional shared secret
conductor.a2a.server.provider-organization=Acme
```
Or per-workflow opt-in in the definition: `"metadata": { "a2a.enabled": true, "a2a.tags": [...] }`
(see `ai/examples/12-a2a-server-workflow.json`).

## 10.7 Code & tests
- `ai/.../a2a/server/`: `A2AServerProperties`, `A2AWorkflowAgent` (service), `A2AServerResource`
  (`@RestController`), `A2AServerException`; `config/A2AServerEnabledCondition`.
- Tests: `A2AWorkflowAgentTest` (exposure, card, send-with-idempotency-key, **multi-turn resume**,
  status mapping incl. blocked→input-required, wrong-agent isolation, cancel), `A2AServerResourceTest`
  (JSON-RPC dispatch, error codes, api-key gating, card serving), and `A2ALoopbackTest` (Conductor
  calling Conductor over A2A end-to-end against a stateful fake engine).

## 10.8 Out of scope (v1, follow-ups)
- Server-side streaming (`message/stream`/SSE) and push-notification config endpoints.
- Per-workflow OAuth scopes; Agent Card JWS signing.
- Full client↔server loopback e2e through the **real** engine (decider/sweeper/persistence) in
  `test-harness` — the mocked-engine loopback (`A2ALoopbackTest`) ships now.
