# 8. A2A and Conductor — Implications (Analysis)

> **This doc is forward-looking analysis, not a description of existing functionality and not
> a committed design.** It maps A2A concepts onto Conductor to frame where the two could meet.
> Verified facts about this repo are cited; everything proposed is labeled as such. Validate
> against the codebase before building anything.

## 8.1 Where Conductor already sits

Conductor is a durable **workflow orchestration** engine: a workflow is a graph of **tasks**;
**workers** execute `SIMPLE` tasks; the engine runs **system tasks** (`HTTP`, `SUB_WORKFLOW`,
`FORK_JOIN`/`JOIN`, `DO_WHILE`, `SWITCH`, `WAIT`, `HUMAN`, `EVENT`, …) and persists every
execution. (Task types verified in `common/.../tasks/TaskType.java`.)

The `ai/` module already makes Conductor an **agentic execution substrate**:
- **LLM tasks:** `LLM_CHAT_COMPLETE`, `LLM_TEXT_COMPLETE`
- **MCP (tool) integration:** `CALL_MCP_TOOL`, `LIST_MCP_TOOLS`, `MCP`
- **RAG / vector:** `LLM_GENERATE_EMBEDDINGS`, `LLM_GET_EMBEDDINGS`, `LLM_INDEX_TEXT`,
  `LLM_STORE_EMBEDDINGS`, `LLM_SEARCH_EMBEDDINGS`, `LLM_SEARCH_INDEX`
- **Multimodal generation:** `GENERATE_IMAGE`, `GENERATE_AUDIO`, `GENERATE_VIDEO`, `GENERATE_PDF`
- **Multi-provider:** Anthropic, Gemini (GenAI + Vertex), Azure OpenAI, Bedrock, Cohere
- Conversation history handling (`GetConversationHistoryRequest`)

(All verified by grepping `ai/src/main/java`.)

**The takeaway:** Conductor already covers the **MCP half** of the picture from
[02-a2a-vs-mcp.md](02-a2a-vs-mcp.md) — an orchestrated agent *using tools*. What A2A adds is
the **peer half** — Conductor-built agents *partnering* with external agents, and external
clients treating Conductor workflows *as* agents.

## 8.2 Conceptual mapping: A2A ↔ Conductor

| A2A concept | Closest Conductor concept | Notes |
|---|---|---|
| Remote agent (A2A server) | A **workflow definition** exposed over an A2A endpoint | A workflow *is* a long-running, stateful capability |
| Agent Card | Workflow/task **metadata** (name, version, description, input/output schemas) | Skills ≈ registered workflows; would need an Agent Card renderer |
| Agent skill | A registered **workflow** (or task) | `id`/`name`/`description`/`tags` map to workflow metadata |
| A2A **Task** | A **workflow execution** | Both are stateful, durable, long-running, with history + outputs |
| `contextId` | `correlationId` (or a session id) | Groups related executions/turns |
| `Message` / `Part` | Workflow **input/output JSON**; `DataPart` ≈ a JSON payload; `FilePart` ≈ document storage refs | Conductor already has external payload storage for large blobs |
| `Artifact` | Workflow **output** / task output | A finished workflow's `output` ≈ an Artifact |
| `AgentExecutor.execute()` | A **worker** polling and completing a task | Both are "do the work, report status/results" |
| `TaskStore` | Conductor's **execution persistence** | The engine already durably stores executions |
| SSE streaming | (no native client-facing task stream) | Could be layered on existing status APIs |
| Push notifications | Workflow-status **webhooks / `EVENT` task / event handlers** | Conductor already emits lifecycle events |

## 8.3 Two integration directions

### Direction A — Conductor as an A2A **server** ("expose a workflow as an agent")

Let external A2A clients discover and invoke a Conductor workflow as if it were an agent:

1. **Serve an Agent Card** at `/.well-known/agent-card.json` derived from workflow metadata —
   `skills[]` populated from registered workflows (name, version, description, tags;
   input/output modes from schemas).
2. **`message/send` → start a workflow.** Map to the existing sync endpoint
   `POST execute/{name}/{version}` (verified in `WorkflowResource.java:99`) for short tasks, or
   the async start for long ones. Return an A2A `Task` whose `id` is the Conductor workflow id.
3. **`tasks/get` → poll execution status**, returning A2A status + artifacts built from
   workflow output.
4. **`tasks/cancel` → terminate the workflow.**
5. **Streaming / push** → bridge Conductor's workflow lifecycle events to SSE / webhooks.

This is attractive because a Conductor workflow is *natively* the kind of durable, long-running,
human-in-the-loop task A2A's lifecycle was designed for.

### Direction B — Conductor as an A2A **client** ("call a remote agent from a workflow")

A new system task — call it **`CALL_AGENT`** (proposed name) — directly analogous to the
existing `CALL_MCP_TOOL`:

1. Task input: a remote Agent Card URL (or pre-resolved card) + the `Message`/payload to send.
2. The task resolves the card (discovery), picks a transport, and calls `message/send`.
3. For long-running remote work, the Conductor task goes **`IN_PROGRESS`** and either polls
   `tasks/get` (via `callbackAfterSeconds`) or completes on a push webhook — reusing Conductor's
   existing async-task machinery.
4. On the remote A2A task reaching a terminal state, the Conductor task records the
   `Artifact`(s) as its output and transitions accordingly (§8.4).

This turns any A2A-speaking agent (built on ADK, CrewAI, LangGraph, …) into a first-class step
in a Conductor workflow — multi-agent orchestration with Conductor as the durable coordinator.

## 8.4 Lifecycle mapping (the crux)

A2A's `TaskState` and Conductor's status enums (both verified) line up cleanly. For **Direction
B** (a `CALL_AGENT` task wrapping a remote A2A task), map the remote A2A state onto the
Conductor *task* status:

| A2A `TaskState` | Conductor `Task.Status` | Handling |
|---|---|---|
| `submitted`, `working` | `IN_PROGRESS` | async task; poll `tasks/get` or await webhook |
| `input-required` | **`COMPLETED`** (with `state="input-required"` in output) | The Conductor task completes so the workflow can branch via `SWITCH` on `output.state`. The `taskId` and `contextId` are surfaced in output so a subsequent `CALL_AGENT` step can continue the conversation. Setting `IN_PROGRESS` here would cause the engine to spin-poll `tasks/get` indefinitely — the remote agent is waiting for a new message, so no poll will ever self-resolve. |
| `auth-required` | **`COMPLETED`** (with `state="auth-required"` in output) | Same rationale as `input-required`. The workflow routes to credential-gathering logic, then issues a new `CALL_AGENT` with the same `taskId`/`contextId`. |
| `completed` | `COMPLETED` | store `Artifact`s as task output |
| `failed` | `FAILED` | propagate error |
| `rejected` | `FAILED` | agent declined |
| `canceled` | `CANCELED` | |

For **Direction A** (a workflow execution exposed as an A2A task), map the Conductor
*workflow* status onto A2A `TaskState`:

| Conductor `WorkflowStatus` | A2A `TaskState` | Notes |
|---|---|---|
| `RUNNING` | `working` (or `submitted` before first task) | |
| `RUNNING` blocked on `WAIT`/`HUMAN` | `input-required` | Conductor has no distinct "waiting" status; it's `RUNNING` with a blocking task |
| `COMPLETED` | `completed` | workflow `output` → `Artifact`(s) |
| `FAILED` / `TIMED_OUT` | `failed` | |
| `TERMINATED` | `canceled` | |
| `PAUSED` | (no clean A2A analog) | `PAUSED` is an admin/operator pause, **not** the same as `input-required` — don't conflate |

> Two mismatches worth flagging:
> - A2A's `input-required`/`auth-required` are **resumable, in-flight** states. Conductor models
>   "blocked, waiting for a signal" as a `RUNNING` workflow sitting on a `WAIT`/`HUMAN` task, not
>   as a workflow status. The bridge must translate "blocked-on-WAIT" ↔ `input-required`.
> - Conductor's `PAUSED` is operator-initiated and does **not** map to any A2A state.

## 8.5 Why this is a natural fit

- **Durability & long-running tasks** are A2A's hardest requirements and Conductor's core
  competency — persistent state, retries, timeouts, human-in-the-loop (`HUMAN` task), resumable
  executions. A2A's `Task` lifecycle is almost a subset of what Conductor already guarantees.
- **The MCP precedent exists.** `CALL_MCP_TOOL` shows the pattern for a system task that speaks
  an external agent protocol; `CALL_AGENT` would mirror it for A2A.
- **Eventing exists.** Conductor already emits workflow lifecycle events and supports webhooks
  / `EVENT` tasks — the substrate for A2A push notifications.
- **Multi-agent orchestration is the differentiator.** A2A standardizes *talking* to agents;
  Conductor adds *durable, observable, retryable orchestration* across many of them — the
  purchasing-concierge pattern ([07](07-ecosystem-and-samples.md)) but with a real execution
  engine underneath instead of an in-memory host.

## 8.6 Open questions / things to verify before building
- **Transport choice:** start with JSON-RPC over HTTP (least friction, default); gRPC later.
- **Version target:** v0.3.x for interop breadth vs v1.0 for current SDKs (see
  [06-versioning.md](06-versioning.md)). The Java SDK (`a2aproject/a2a-java`) could back
  Direction A/B rather than hand-rolling the protocol.
- **Identity propagation:** A2A puts identity in **HTTP headers, not the payload**
  ([05](05-security.md)) — confirm how that threads through Conductor's auth and into worker
  context.
- **Artifact ↔ payload storage:** map A2A `FilePart`/large `DataPart` onto Conductor's external
  payload storage.
- **`input-required` round-trips:** design how a remote agent's mid-task question surfaces to a
  Conductor workflow and how the answer is sent back with the same `taskId`.
- **Streaming:** whether to expose SSE at all for Direction A, or rely on polling + webhooks.

> None of the above is implemented today. It is a map of the territory, drawn so that if/when
> Conductor takes on A2A, the protocol's concepts already have homes in the engine.
