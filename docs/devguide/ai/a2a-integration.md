---
description: "A2A (Agent2Agent) integration with Conductor — call remote agents as durable workflow tasks, and expose Conductor workflows as A2A agents. Crash-safe, resumable, observable."
---

# A2A integration

[A2A (Agent2Agent)](https://a2a-protocol.org/) is an open protocol for agents to talk to one another over HTTP/JSON-RPC. Conductor integrates A2A in **both directions**:

- **Client** — call a remote A2A agent from a workflow as a durable system task (`AGENT`, `GET_AGENT_CARD`, `CANCEL_AGENT`).
- **Server** — expose any Conductor workflow as an A2A agent that other A2A clients (Google ADK, CrewAI, LangGraph, another Conductor) can discover and invoke.

The integration is **durable**: a remote agent call survives a server crash, restart, or redeploy, and resumes from where it left off — the call's state lives in the workflow execution, not in a thread.


## What is A2A

A2A standardizes three things: an **Agent Card** (a `/.well-known/agent-card.json` document describing an agent's skills), a **JSON-RPC** surface (`message/send`, `tasks/get`, `tasks/cancel`, …), and a **task lifecycle** (`submitted → working → input-required → completed/failed/canceled`). An agent runs a long task; the client polls (or is pushed) until it reaches a terminal state.

Conductor maps this lifecycle onto its own durable task model, so a remote agent task behaves like any other Conductor task — retried, timed out, observed, and resumed by the engine.


## Call a remote agent from a workflow (client)

*Direction A — Conductor is the A2A client.* These tasks require the AI integration to be enabled:

```properties
conductor.integrations.ai.enabled=true
```

Each task takes an **`agentType`** input that selects the agent runtime. It defaults to `"a2a"` (Agent2Agent — the only runtime in OSS today); native runtimes such as LangGraph and OpenAI are planned, and the field is the extension point for them. An unrecognized `agentType` fails the task with a clear error.

### AGENT — send a message to an agent

Sends an A2A `message/send` and works the resulting agent task to a terminal state. Non-blocking: a fast reply completes immediately; long-running work moves to `IN_PROGRESS` and is polled at a cadence (no worker thread is held).

```json
{
  "name": "call_currency_agent",
  "taskReferenceName": "agent",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "a2a",
    "agentUrl": "https://currency-agent.example.com",
    "text": "convert 100 USD to EUR",
    "pollIntervalSeconds": 5,
    "headers": {
      "Authorization": "Bearer ${workflow.input.agentToken}"
    }
  }
}
```

**Key inputs** (see `A2ACallRequest`):

| Field | Description |
|---|---|
| `agentType` | Agent runtime to use — defaults to `"a2a"`. Reserved for native runtimes (e.g. `langgraph`, `openai`) coming later; any other value is rejected today. |
| `agentUrl` | Base URL of the remote agent (required). |
| `text` / `prompt` | Convenience for a single text part. |
| `parts` / `message` | A full A2A message (multi-part / data parts) instead of `text`. |
| `contextId`, `taskId` | Continue an existing conversation / resume an agent task (multi-turn). |
| `headers` | Per-call HTTP headers (e.g. auth). Reference credentials via workflow inputs/secrets rather than hardcoding them. |
| `pollIntervalSeconds` | Poll cadence in poll mode (default 5). |
| `streaming` | `true` → consume `message/stream` (SSE) and aggregate to completion. |
| `pushNotification` | `true` → the agent calls back our webhook on completion (see below). |
| `maxDurationSeconds` | Absolute deadline (default 86400). |
| `maxPollFailures` | Consecutive transient poll failures tolerated before failing (default 30). |

**Output** (`agent.output`): `state` (the A2A task state), `taskId` and `contextId` (for resumption), `artifacts`, `text` (extracted text), `agentMessage`, and the full `task` object. For a completed call it looks like:

```json
{
  "state": "completed",
  "taskId": "task-7f3a",
  "contextId": "ctx-7f3a",
  "text": "100 USD = 92.40 EUR",
  "artifacts": [
    { "artifactId": "result", "parts": [ { "kind": "text", "text": "100 USD = 92.40 EUR" } ] }
  ]
}
```

Downstream tasks read these with `${agent.output.text}`, `${agent.output.taskId}`, etc.

#### Three execution modes

- **Poll** (default) — the task is `IN_PROGRESS` and polled via `tasks/get` at `pollIntervalSeconds`. No thread is held between polls; the call survives restarts.
- **Streaming** (`streaming: true`) — consumes the agent's SSE stream and aggregates events. Requires `capabilities.streaming=true` on the agent card. Holds a thread for the stream's duration.
- **Push** (`pushNotification: true`) — the agent calls Conductor's webhook when the task finishes, so nothing polls in the meantime. Requires `conductor.a2a.callback.url`. A slow **backstop poll** still runs (`pushBackstopPollSeconds`, default 300) so a lost webhook can't hang the task.

#### Push notifications — end to end

**1. Configure the externally-reachable callback base URL** (where the agent can reach Conductor):

```properties
conductor.integrations.ai.enabled=true
conductor.a2a.callback.url=https://conductor.example.com
```

**2. Ask for push on the task:**

```json
{
  "name": "call_research_agent",
  "taskReferenceName": "agent",
  "type": "AGENT",
  "inputParameters": {
    "agentUrl": "https://research-agent.example.com",
    "text": "research durable agent protocols",
    "pushNotification": true,
    "pushBackstopPollSeconds": 300
  }
}
```

**3. What Conductor sends** — the `message/send` carries a `pushNotificationConfig` pointing at a
per-task webhook, with a single-use bearer token (a `{uuid}:{expiryEpochMillis}` value, 24h TTL):

```json
{
  "method": "message/send",
  "params": {
    "message": { "role": "user", "messageId": "a2a-...", "parts": [ { "kind": "text", "text": "research durable agent protocols" } ] },
    "configuration": {
      "pushNotificationConfig": {
        "url": "https://conductor.example.com/api/a2a/callback/<conductorTaskId>",
        "token": "3f9c…:1750300000000",
        "authentication": { "schemes": ["Bearer"], "credentials": "3f9c…:1750300000000" }
      }
    }
  }
}
```

The `AGENT` task then **waits** (holds no worker thread) until the webhook arrives; the backstop
poll runs only as a safety net.

**4. The agent calls back** when the task reaches a terminal/interrupted state — Conductor verifies
the token (constant-time + expiry), fetches the final task via `tasks/get`, and completes the
workflow task:

```bash
curl -X POST https://conductor.example.com/api/a2a/callback/<conductorTaskId> \
  -H 'Authorization: Bearer 3f9c…:1750300000000' \
  -H 'Content-Type: application/json' \
  -d '{ "taskId": "<remoteAgentTaskId>", "status": { "state": "completed" } }'
# → 200 OK; the AGENT task is now COMPLETED with the agent's output.
```

Agents that don't support the `authentication` field fall back to a `?token=` query parameter on the
callback URL, which the endpoint still accepts (with a deprecation warning, since tokens in URLs land
in access logs).

### GET_AGENT_CARD — discover an agent

```json
{
  "name": "discover_agent",
  "taskReferenceName": "discover",
  "type": "GET_AGENT_CARD",
  "inputParameters": { "agentUrl": "https://currency-agent.example.com" }
}
```

Resolves the agent card from `/.well-known/agent-card.json` (falling back to the legacy `/.well-known/agent.json`) and returns the parsed skills/capabilities — feed it to an LLM so it can pick a skill at runtime.

### CANCEL_AGENT — cancel a running agent task

```json
{
  "name": "cancel_agent_task",
  "taskReferenceName": "cancel",
  "type": "CANCEL_AGENT",
  "inputParameters": {
    "agentUrl": "https://currency-agent.example.com",
    "taskId": "${agent.output.taskId}"
  }
}
```

### Multi-turn (input-required)

When a remote task reaches `input-required` (or `auth-required`), `AGENT` **completes** and surfaces the agent's question plus the `taskId`/`contextId` in its output. The workflow branches on that state and issues another `AGENT` task with the **same `taskId` and `contextId`** carrying the answer — resuming the same remote task rather than starting a new conversation:

```json
{
  "name": "branch_on_state",
  "taskReferenceName": "branch",
  "type": "SWITCH",
  "evaluatorType": "value-param",
  "expression": "state",
  "inputParameters": { "state": "${ask.output.state}" },
  "decisionCases": {
    "input-required": [
      {
        "name": "answer_agent", "taskReferenceName": "answer", "type": "AGENT",
        "inputParameters": {
          "agentUrl": "${workflow.input.agentUrl}",
          "text": "${workflow.input.answer}",
          "contextId": "${ask.output.contextId}",
          "taskId": "${ask.output.taskId}"
        }
      }
    ]
  },
  "defaultCase": []
}
```

Full example: `ai/examples/29-a2a-client-multi-turn.json`.

### Orchestrating multiple agents

Because each `AGENT` is an ordinary durable task, you compose agents with the usual Conductor operators — e.g. **`FORK_JOIN`** to call several agents in parallel, **`JOIN`** to gather results. Every branch is independently crash-safe: if Conductor restarts mid-flight, each in-flight agent call resumes from persisted state (`ai/examples/27-a2a-multi-agent.json`). To let an LLM pick which skill to use, chain `GET_AGENT_CARD → LLM_CHAT_COMPLETE → AGENT` (`ai/examples/28-a2a-llm-pick-skill.json`).

### Error handling & retries

`AGENT` maps remote outcomes onto Conductor task statuses, so the engine's normal retry/timeout machinery applies. Retryable failures become `FAILED` (the engine retries per the task def's `retryCount`); permanent failures become `FAILED_WITH_TERMINAL_ERROR` (no retry):

| Condition | Task status | Retried? |
|---|---|---|
| HTTP 408/429/5xx, connect/read timeout, dropped/empty stream | `FAILED` | yes |
| JSON-RPC transient error (e.g. `-32603` internal) | `FAILED` | yes |
| Remote agent task ends `failed` / `rejected` | `FAILED` | yes |
| HTTP 4xx (except 408/429) | `FAILED_WITH_TERMINAL_ERROR` | no |
| JSON-RPC terminal codes (`-32700/-32600/-32601/-32602/-3200{1..5,7}`) | `FAILED_WITH_TERMINAL_ERROR` | no |
| Missing `agentUrl` / empty message / **SSRF-blocked** URL | `FAILED_WITH_TERMINAL_ERROR` | no |
| Exceeds `maxDurationSeconds`, or `maxPollFailures` consecutive poll failures | `FAILED_WITH_TERMINAL_ERROR` | no |

Retries reuse the deterministic `messageId`, so agents that dedupe on it get effectively-once delivery. The failure reason is on `task.reasonForIncompletion`.

**Troubleshooting**

| Symptom | Cause / fix |
|---|---|
| `… SSRF blocked` | `agentUrl` resolves to a private/loopback/metadata address. Use a public URL, or set `conductor.a2a.client.allow-private-network=true` for trusted/dev (cloud-metadata stays blocked). |
| `streaming: true` behaves like poll | The agent card has `capabilities.streaming=false`; the client only streams when the agent advertises it. |
| Fails after N poll failures | The agent is unreachable — raise `maxPollFailures` or check connectivity. |
| Hangs, then fails at the deadline | The agent never reached a terminal state within `maxDurationSeconds`. |


## Expose a workflow as an A2A agent (server)

*Direction B — Conductor is the A2A server.* Any Conductor workflow can be published as an A2A agent
that other A2A clients (Google ADK, CrewAI, LangGraph, another Conductor) discover and invoke. The
workflow execution **is** the durable, resumable A2A task — that's the native fit.

Enable the server and opt the workflow in:

```properties
conductor.a2a.server.enabled=true
# Expose by name…
conductor.a2a.server.exposed-workflows=order_pizza,book_appointment
```

…or per-workflow via `WorkflowDef.metadata`:

```json
{
  "name": "order_pizza",
  "version": 1,
  "metadata": { "a2a.enabled": true, "a2a.tags": ["ordering"] },
  "tasks": [ ... ]
}
```

**Routing: one agent per workflow.** Each exposed workflow is its own focused agent at `{basePath}/{workflow}` (default basePath `/a2a`):

| Method & path | Purpose |
|---|---|
| `GET /a2a/{workflow}/.well-known/agent-card.json` | Agent Card (also `/agent.json`). |
| `POST /a2a/{workflow}` | JSON-RPC: `message/send`, `message/stream` (SSE), `tasks/get`, `tasks/cancel`. |
| `GET /a2a` | Convenience listing of exposed agents (non-spec). |

Exposed agents advertise `capabilities.streaming=true`.

### Discover and call

```bash
# 1. Discover
curl http://localhost:8080/a2a/order_pizza/.well-known/agent-card.json

# 2. Start a task (message/send → starts the workflow)
curl -X POST http://localhost:8080/a2a/order_pizza \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0", "id": 1, "method": "message/send",
    "params": { "message": {
      "role": "user", "messageId": "m-1",
      "parts": [ { "kind": "text", "text": "one large pepperoni" } ]
    } }
  }'
# → result is an A2A Task: { "id": "<workflowId>", "contextId": ..., "status": { "state": "working" } }

# 3. Poll
curl -X POST http://localhost:8080/a2a/order_pizza \
  -H 'Content-Type: application/json' \
  -d '{ "jsonrpc": "2.0", "id": 2, "method": "tasks/get", "params": { "id": "<workflowId>" } }'
```

The inbound A2A message is injected into the workflow input as `_a2a_text`, `_a2a_message_id`, `_a2a_context_id` (plus any data parts), and `contextId` becomes the workflow `correlationId`.

### Streaming (message/stream)

Use `message/stream` instead of `message/send` for a Server-Sent Events stream: the initial `Task`,
then `status-update` events as the workflow's A2A state changes and `artifact-update` events as
output is produced, ending with a `final` status-update at a terminal / input-required state.

```bash
curl -N -X POST http://localhost:8080/a2a/order_pizza \
  -H 'Content-Type: application/json' \
  -d '{ "jsonrpc":"2.0", "id":1, "method":"message/stream",
        "params": { "message": { "role":"user", "messageId":"m-1",
          "parts":[ {"kind":"text","text":"one large pepperoni"} ] } } }'
```

```text
data: {"jsonrpc":"2.0","id":1,"result":{"kind":"task","id":"wf-7f3a","status":{"state":"working"}}}

data: {"jsonrpc":"2.0","id":1,"result":{"kind":"artifact-update","taskId":"wf-7f3a","artifact":{"artifactId":"workflow-output","parts":[{"kind":"data","data":{"orderId":"ORD-42"}}]}}}

data: {"jsonrpc":"2.0","id":1,"result":{"kind":"status-update","taskId":"wf-7f3a","status":{"state":"completed"},"final":true}}
```

The stream is a live view of the durable execution — if the connection drops, resume tracking with
`tasks/get`. Tuning: `conductor.a2a.server.stream-poll-interval-millis` (default 500) and
`conductor.a2a.server.stream-max-duration-seconds` (default 300).

### Durable, idempotent start

`message/send` starts the workflow with `idempotencyKey = {workflow}:{messageId}` and `RETURN_EXISTING`, so a client's **retried** `message/send` returns the **existing** execution rather than starting a duplicate — server-side effectively-once. The execution's durability (crash-safe, resumable) is inherited from the engine.

### Status mapping

| Conductor workflow | A2A task state |
|---|---|
| RUNNING, blocked on a `HUMAN`/`WAIT` task | `input-required` |
| RUNNING (not blocked) / PAUSED | `working` |
| COMPLETED | `completed` (output → an artifact) |
| FAILED / TIMED_OUT | `failed` |
| TERMINATED | `canceled` |

### Multi-turn resume — worked example

If the workflow blocks on a `HUMAN`/`WAIT` task, the agent reports `input-required`. A follow-up
`message/send` carrying that task's `id` (the workflow id) **resumes** the paused execution — the
message content completes the pending task and the workflow continues. No duplicate workflow is
started; if the workflow is already terminal or not awaiting input, its current state is returned
unchanged.

Take this exposed workflow (`ai/examples/25-a2a-server-multi-turn.json`) — it asks a question, then
confirms:

```json
{
  "name": "book_appointment",
  "version": 1,
  "metadata": { "a2a.enabled": true },
  "tasks": [
    { "name": "ask_preferred_time", "taskReferenceName": "ask", "type": "HUMAN" },
    {
      "name": "confirm_appointment", "taskReferenceName": "confirm", "type": "INLINE",
      "inputParameters": {
        "evaluatorType": "graaljs",
        "expression": "({ status: 'confirmed', when: $.when })",
        "when": "${ask.output._a2a_text}"
      }
    }
  ]
}
```

**Turn 1 — start.** The workflow reaches the `HUMAN` task and parks at `input-required`:

```bash
curl -X POST http://localhost:8080/a2a/book_appointment \
  -H 'Content-Type: application/json' \
  -d '{ "jsonrpc":"2.0", "id":1, "method":"message/send",
        "params": { "message": { "role":"user", "messageId":"m-1",
          "parts":[ {"kind":"text","text":"Book me a dentist appointment"} ] } } }'
```

```json
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "kind": "task",
    "id": "wf-7f3a91",
    "contextId": "wf-7f3a91",
    "status": {
      "state": "input-required",
      "message": { "role": "agent", "parts": [ { "kind": "text",
        "text": "Workflow is awaiting input. Send another message/send carrying this task's id to provide the input and resume the execution." } ] }
    }
  }
}
```

**Turn 2 — resume.** Send the answer with the **same `taskId`** (`= result.id`); the `HUMAN` task
completes with the message as its input and the workflow finishes:

```bash
curl -X POST http://localhost:8080/a2a/book_appointment \
  -H 'Content-Type: application/json' \
  -d '{ "jsonrpc":"2.0", "id":2, "method":"message/send",
        "params": { "message": { "role":"user", "messageId":"m-2", "taskId":"wf-7f3a91",
          "parts":[ {"kind":"text","text":"Tuesday at 3pm"} ] } } }'
```

```json
{
  "jsonrpc": "2.0", "id": 2,
  "result": {
    "kind": "task",
    "id": "wf-7f3a91",
    "contextId": "wf-7f3a91",
    "status": { "state": "completed" },
    "artifacts": [
      { "artifactId": "workflow-output", "name": "output",
        "parts": [ { "kind": "data", "data": { "status": "confirmed", "when": "Tuesday at 3pm" } } ] }
    ]
  }
}
```

The answer (`Tuesday at 3pm`) arrives at the workflow as `${ask.output._a2a_text}`, exactly as if the
`HUMAN` task had been completed through the Conductor API.


## Durability

The "durable A2A" claim rests on a few concrete mechanisms:

- **Deterministic message id.** `AGENT` derives the A2A `messageId` from `workflowInstanceId + referenceTaskName + iteration` — stable across task retries and server restarts, distinct per `DO_WHILE` iteration. Agents that dedupe on `messageId` get effectively-once delivery despite at-least-once retries.
- **State in the execution, not the thread.** Poll mode holds no thread; the remote `taskId`, deadline, and poll-failure count live in the persisted task output, so a restart resumes the poll loop.
- **Liveness guards.** An absolute deadline (`maxDurationSeconds`) and a consecutive-poll-failure bound (`maxPollFailures`) ensure a dead or stuck agent can't hang a task forever.
- **Push backstop.** Push mode still backstop-polls, so a lost webhook degrades to polling rather than hanging.


## Security

- **SSRF guard.** Outbound `agentUrl`s that resolve to loopback, private (RFC-1918), link-local, IPv6 unique-local (`fc00::/7`), or cloud-metadata addresses are rejected. Cloud-metadata addresses are blocked **always**. To allow private-network agents (e.g. localhost in dev):

    ```properties
    conductor.a2a.client.allow-private-network=true
    ```

    Cloud metadata stays blocked even with this on. For production, prefer a network-layer egress firewall.
- **Server auth.** Like OSS Conductor REST, the A2A server is **open by default**. Front it with a gateway/firewall (or mTLS) to control access. Inbound authentication (API keys, OAuth/OIDC, mTLS, per-skill scopes, signed Agent Cards) is provided by the **enterprise** build.
- **Push tokens.** Push callbacks carry a single-use bearer token with an embedded 24h expiry, validated constant-time by the callback endpoint (`POST /api/a2a/callback/{taskId}`).


## Observability

A2A code paths emit metrics through the shared Conductor metrics registry and set MDC keys for log correlation.

**Metrics:** `a2a_client_calls{result}`, `a2a_client_poll_failures`, `a2a_rpc_errors{method,terminal}`, `a2a_ssrf_blocked`, `a2a_server_requests{method}`, `a2a_server_resumes`.

**MDC keys** (greppable in logs): `a2aWorkflowId`, `a2aTaskId`, `a2aRef`, `a2aRemoteTaskId`, `a2aContextId`, `a2aMessageId`, `a2aAgent`, `a2aMethod`.


## Configuration reference

| Property | Default | Purpose |
|---|---|---|
| `conductor.integrations.ai.enabled` | `false` | Enables the client tasks (`AGENT`, …). |
| `conductor.a2a.callback.url` | — | Externally-reachable base URL for push callbacks. |
| `conductor.a2a.client.allow-private-network` | `false` | Allow agent URLs on private/loopback networks (metadata still blocked). |
| `conductor.a2a.server.enabled` | `false` | Enables the A2A server endpoints. |
| `conductor.a2a.server.basePath` | `/a2a` | Base path for exposed agents. |
| `conductor.a2a.server.exposed-workflows` | — | Comma-separated workflow names to expose. |
| `conductor.a2a.server.public-url` | request-derived | Base URL advertised in the agent card. |
| `conductor.a2a.server.provider-organization` | `Conductor` | `provider.organization` on the card. |

## Examples

### A complete workflow: discover then call

This workflow discovers a remote agent's card, then calls it — passing the agent URL and prompt as
workflow inputs so the same definition works against any A2A agent:

```json
{
  "name": "a2a_interop_echo",
  "version": 1,
  "schemaVersion": 2,
  "description": "Discover a remote A2A agent, then call it.",
  "ownerEmail": "a2a@example.com",
  "tasks": [
    {
      "name": "discover_agent",
      "taskReferenceName": "discover",
      "type": "GET_AGENT_CARD",
      "inputParameters": { "agentUrl": "${workflow.input.agentUrl}" }
    },
    {
      "name": "call_agent",
      "taskReferenceName": "call",
      "type": "AGENT",
      "inputParameters": {
        "agentUrl": "${workflow.input.agentUrl}",
        "text": "${workflow.input.prompt}",
        "pollIntervalSeconds": 2
      }
    }
  ]
}
```

Register and run it (the AI integration must be enabled — `conductor.integrations.ai.enabled=true`;
for a localhost agent in dev also set `conductor.a2a.client.allow-private-network=true`):

```bash
# register
curl -X POST localhost:8080/api/metadata/workflow \
  -H 'Content-Type: application/json' -d @a2a_interop_echo.json

# run against a reachable A2A agent
curl -X POST localhost:8080/api/workflow/a2a_interop_echo \
  -H 'Content-Type: application/json' \
  -d '{"agentUrl":"http://localhost:9999","prompt":"convert 100 USD to EUR"}'
```

### Run it end to end (showcase demos)

Two self-contained demos under `ai/src/test/resources/a2a/` boot a real agent + Conductor and run a
workflow against it — no API keys:

```bash
# Interop: Conductor calls the official a2a-sdk reference agent (a real, non-Conductor A2A server)
ai/src/test/resources/a2a/interop-demo/run-interop-demo.sh

# Durability: kill the Conductor server mid-call; the workflow resumes and completes after restart
ai/src/test/resources/a2a/durable-demo/run-durable-demo.sh
```

### Example library

Runnable workflow definitions live in [`ai/examples/`](https://github.com/conductor-oss/conductor/tree/main/ai/examples):

| File | Shows |
|---|---|
| `10-a2a-call-agent.json` | Call a remote agent (poll mode) |
| `11-a2a-get-agent-card.json` | Discover an agent's skills |
| `12-a2a-server-workflow.json` | Expose a workflow as an A2A agent |
| `23-a2a-streaming.json` | Streaming (SSE) call |
| `24-a2a-push.json` | Push-notification mode |
| `25-a2a-server-multi-turn.json` | Multi-turn server agent (HUMAN task → resume) |
| `26-a2a-cancel.json` | Start then cancel a remote agent task |
| `27-a2a-multi-agent.json` | Call multiple agents in parallel (FORK_JOIN → JOIN) |
| `28-a2a-llm-pick-skill.json` | Discover → LLM picks the prompt → call |
| `29-a2a-client-multi-turn.json` | Client multi-turn (branch on input-required, re-call) |
