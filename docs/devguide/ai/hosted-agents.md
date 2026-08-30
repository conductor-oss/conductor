---
description: "Hosted platform agents — call an agent you already run on Microsoft Foundry, AWS Bedrock, or the OpenAI Assistants API from an AGENT task. Runtime selection, rawConfig keys, credential shape, and the tool-call resume loop."
---

# Hosted platform agents

The `AGENT` task can drive an agent that lives on someone else's platform. The agent stays where it is — you keep authoring it in Microsoft Foundry, Bedrock, or the OpenAI dashboard — and Conductor supplies the durability around it: retries, timeouts, cancellation, human-in-the-loop pauses, and a persisted execution history.

Three runtimes ship today:

| `agentType` | Platform | Client |
|---|---|---|
| `microsoft-foundry` | Microsoft Foundry Agents | `AzureFoundryAgentClient` |
| `openai-assistants` | OpenAI Assistants API | `OpenAiAssistantsAgentClient` |
| `bedrock` | AWS Bedrock Agent Runtime | `BedrockAgentClient` |

!!! note "Microsoft Foundry was Azure AI Foundry"
    `agentType: azure-foundry` still routes to the same runtime, so workflows saved under the old
    name keep working and need no edit. New workflows should use `microsoft-foundry`, which is what
    the UI writes and what agent discovery reports back.

All three require the AI integration:

```properties
conductor.integrations.ai.enabled=true
```

Without it the client beans are absent, and a task routed to one of these runtimes fails as an unsupported `agentType`.

For the other two `agentType` values, see [A2A integration](a2a-integration.md) (`a2a`, remote Agent2Agent endpoints) and [Conductor agents](conductor-agents.md) (`conductor`, the embedded agentspan runtime).

!!! note "Vertex AI"
    There is no `vertex` runtime. Vertex AI agents speak A2A natively, so call them with `agentType: "a2a"` and the agent's A2A endpoint as `agentUrl`.

!!! note "Credentials are values, not secret names"
    Conductor substitutes `${workflow.secrets.NAME}` — and `${workflow.secrets.NAME.sub_key}` — in task
    input before the task runs, the same way an HTTP task takes an `Authorization` header. Agent
    clients are handed the resolved values and never read the secret store themselves, so a
    credential belongs in `credentials` as a reference, and the engine does the rest.

## They show up on their own

Store a secret with an `endpoint` key (Azure) or a `region` key (Bedrock) and the agents that credential can see appear in the agent list alongside agents defined in Conductor — no separate registration step. Discovery is best effort: a credential that cannot list contributes nothing rather than breaking the listing.

## What they have in common

Every hosted runtime takes the same core inputs.

| Field | Description |
|---|---|
| `agentType` | Selects the runtime (required). |
| `prompt` | The message to send. **Required** on every runtime except A2A, and checked when the workflow definition is saved, not just when it runs. Unlike A2A, a hosted runtime does not fall back to `text`, `parts` or `message`. |
| `credentials` | The platform credential, as values. Reference secrets with `${workflow.secrets.NAME.key}`; Conductor substitutes them before the task runs. Which keys matter differs per platform — see below. |
| `agentUrl` | Names the endpoint and the agent in one field, the way an A2A task does. Anything set in `rawConfig` wins over what the URL implies. |
| `rawConfig` | Platform-specific configuration: which agent, where it lives. |
| `executionId` | Set on a **later** `AGENT` task to resume an existing conversation instead of starting one. |
| `autoRunTools` | Run the agent's tool calls as tasks in this workflow instead of handing them back (default false). See below. |
| `toolTaskNames` | Optional `tool name -> task name` overrides for `autoRunTools`. |
| `pollIntervalSeconds` | Poll cadence while the run is not terminal (default 5). |
| `maxDurationSeconds` | Absolute deadline before the task fails and the run is cancelled (default 86400). |
| `maxPollFailures` | Consecutive transient poll failures tolerated before failing (default 30). |

### The secret has to be JSON

`${workflow.secrets.AZURE_CRED.client_id}` reads the secret named `AZURE_CRED` and extracts
`client_id` from it, so that secret must hold a JSON object:

```json
{"client_id": "…", "client_secret": "…", "tenant_id": "…"}
```

If the secret does not exist, or does not hold that key, the reference resolves to **nothing** —
the credential simply arrives empty.

Two ways a *correct* JSON document arrives unreadable, both of which Conductor recovers from and
logs a warning about. The document picks up literal quote characters, which is what a `.env` file
read verbatim leaves behind — the quotes a shell would have stripped stay in the value:

```bash
# the quotes end up inside the value
CONDUCTOR_SECRET_AZURE_CRED='{"client_id":"…"}'

# store it without them
CONDUCTOR_SECRET_AZURE_CRED={"client_id":"…"}
```

Or the document was JSON-encoded on the way in, making it a JSON string that holds JSON. Either
way the reference still resolves, but fix the stored value — the warning is there because the next
thing to read that secret may not be as forgiving.

A single flat value needs no sub-key: write `${workflow.secrets.OPENAI_KEY}` and store the key on
its own. A flat value has no JSON to unwrap and is handed over exactly as stored, so the same
mistake cannot be recovered from there. A credential that arrives wrapped in quotes is rejected,
naming the field, rather than sent to the provider to come back as an opaque authentication
failure.

**Nothing falls back to the server's own identity.** When a task names credential keys and they do
not resolve, the task fails and says which ones. Falling through to the identity the server runs as
belongs to a task that deliberately supplies no credentials; using it for a broken secret would run
the agent as somebody else, successfully, with no error anywhere.

None of these clients keep per-run state in memory. The `executionId` returned by the start call is the platform's own conversation handle, and Conductor persists it in the task output; everything else needed to reach the run is re-derived from the task input on each call. A status poll, a tool reply, or a cancellation is therefore served correctly by any server replica, including one that never saw the run start.

### Configuring this in the UI

The `AGENT` task form does the above for you. Pick a runtime and it offers the authentication
methods that runtime actually supports; pick one and it shows only that method's fields, with a
picker that fills them from a stored secret. You never type the `${workflow.secrets.…}` syntax or
guess which keys a provider wants, and switching methods clears the previous one's keys — which
matters, because the server chooses its auth mode by *which keys are present*.

### Authentication at a glance

| Provider | API key | Other credentials |
|---|---|---|
| Microsoft Foundry | ✅ `apiKey` / `api_key` | Service principal, user-assigned managed identity, default Azure chain, or the caller's own identity |
| OpenAI Assistants | ✅ `api_key` / `apiKey` — the only mode | — |
| AWS Bedrock | ✅ `apiKey` / `api_key` | Static keys, `roleArn` to assume, or the default AWS chain |

Where an API key is accepted, either spelling works, so one habit carries across providers.

### Output

| Key | When | Description |
|---|---|---|
| `executionId` | Always | The platform's conversation handle. Pass it back to resume. |
| `output` | Completed | Provider output — `{"result": "<the agent's reply>"}`. |
| `text` | Completed | The reply text, lifted out of `output` for convenience. |
| `waiting` | Tool call pending | `true` — the agent asked for a tool and is waiting. |
| `pendingTool` | Tool call pending | `tool_name`, `tool_call_id`, and `arguments` (a JSON string). |
| `pendingTools` | Tool call pending | Every outstanding call, when the agent asked for more than one. |
| `toolDispatchId` | `autoRunTools`, tools running | The run executing the tools, linked from the execution view. |
| `reasonForIncompletion` | Failed | The platform's own error message. |

### The tool-call loop

A function tool is one the platform **cannot** run: you registered only its schema, so the provider stops the run and asks you to execute it. (Tools the platform runs itself — Code Interpreter, an OpenAPI tool, an Azure Function, an MCP connection — never reach Conductor; the run simply takes longer.)

There are two ways to answer. `autoRunTools` is the recommended one.

#### Let Conductor run the tools (`autoRunTools`)

Set `autoRunTools: true` and the `AGENT` task **stays `IN_PROGRESS`** while each tool the agent asked for is scheduled as an ordinary Conductor task — one per call, in parallel — and the agent is resumed with their results automatically:

```json
{
  "name": "ask_the_analyst",
  "taskReferenceName": "ask_the_analyst",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "microsoft-foundry",
    "prompt": "compare Q3 revenue per engineer against Q2",
    "autoRunTools": true,
    "credentials": {
      "client_id":     "${workflow.secrets.AZURE_CRED.client_id}",
      "client_secret": "${workflow.secrets.AZURE_CRED.client_secret}",
      "tenant_id":     "${workflow.secrets.AZURE_CRED.tenant_id}"
    },
    "rawConfig": {
      "endpoint": "https://my-project.services.ai.azure.com/api/projects/p1",
      "assistantId": "asst_abc123"
    }
  }
}
```

**A tool runs as a task of its own name.** The agent's `get_revenue` tool becomes a `SIMPLE` task named `get_revenue`, so a worker already registered for `get_revenue` serves it with no further configuration. Override the mapping with `toolTaskNames` when the names should differ:

```json
"toolTaskNames": { "get_revenue": "finance_revenue_lookup" }
```

**The tool's own arguments become the task's input**, so a worker reads them as ordinary parameters rather than parsing a payload. Three extra keys travel alongside: `_toolCallId`, `_toolName`, and `_agentExecutionId`.

Each tool task is a real Conductor task — its own retry policy, timeout, worker, and row in the execution history. Independent tools requested in the same turn run concurrently. If the agent asks for tools again on the next turn, that is simply another batch.

**When a tool fails,** having exhausted its own retries, the `AGENT` task fails with that tool's reason and the agent run is cancelled. The failure is not reported back to the model; your workflow's error handling takes over.

The agent task's output carries `pendingTools` (what was asked for) and `toolDispatchId` (the run executing them), and the execution view links from the agent to that run.

!!! note "Needs the embedded runtime"
    Scheduling work requires the server. A remotely-polled SDK worker has no engine to schedule on, so `autoRunTools` is ignored there and the tool request is handed back to the workflow as below.

#### Hand the tools back to the workflow

Without `autoRunTools`, the `AGENT` task **completes** with `waiting: true` rather than blocking. Your workflow then runs the tool as ordinary tasks and hands the result back through a second `AGENT` task carrying the same `executionId`:

```json
{
  "name": "return_tool_result",
  "taskReferenceName": "return_tool_result",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "microsoft-foundry",
    "executionId": "${ask_the_analyst.output.executionId}",
    "prompt": "${lookup_revenue.output.response}",
    "credentials": { "apiKey": "${workflow.secrets.AZURE_CRED.apiKey}" },
    "rawConfig": {
      "endpoint": "${workflow.input.foundryEndpoint}",
      "assistantId": "asst_abc123"
    }
  }
}
```

The configuration has to be repeated on the resuming task, because that is where the client reads it from — nothing is remembered between tasks.

!!! tip "Answering several tools at once"
    A model may request several independent tools in one turn, and the provider will not resume the run until **every** call has an output. `autoRunTools` handles this for you. Answering manually, set `toolResults` keyed by `tool_call_id` — one entry per call in `pendingTools`. A reply that does not cover them all is rejected rather than having one result sent for every call.

---

## Microsoft Foundry

Foundry is three APIs behind one `agentType`, and the endpoint decides which:

| Endpoint | API | Behaviour |
|---|---|---|
| `…openai.azure.com/openai` | Classic Assistants — threads and runs | Polled; supports tool calls and multi-turn |
| `…services.ai.azure.com/api/projects/…` | The project's Responses API | Answers in one call |
| `…inference.ml.azure.com`, or `…services.ai.azure.com/models` | Model inference (chat completions) | Answers in one call |

The two one-shot surfaces complete the `AGENT` task on its first invocation — there is nothing to poll, so `pollIntervalSeconds` has no effect and the execution cannot be resumed. Only the classic Assistants surface has the tool-call loop described above.

For a project agent, its configured instructions and tools are read from the agent definition and forwarded, so its web search, code interpreter and file search actually run.

!!! note "Endpoints the hostname cannot classify"
    Sovereign clouds (`.azure.us`, `.azure.cn`), private endpoints and proxies do not match the public hostname patterns. Set `rawConfig.surface` to `assistants`, `responses`, or `inference` to say outright which API the endpoint serves.

```json
{
  "name": "ask_the_analyst",
  "taskReferenceName": "ask_the_analyst",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "microsoft-foundry",
    "prompt": "${workflow.input.question}",
    "credentials": {
      "client_id":     "${workflow.secrets.AZURE_CRED.client_id}",
      "client_secret": "${workflow.secrets.AZURE_CRED.client_secret}",
      "tenant_id":     "${workflow.secrets.AZURE_CRED.tenant_id}"
    },
    "rawConfig": {
      "endpoint": "https://my-project.services.ai.azure.com/api/projects/p1",
      "assistantId": "asst_abc123"
    }
  }
}
```

**`rawConfig`**

| Key | Required | Default |
|---|---|---|
| `endpoint` | Yes, unless given as `agentUrl` | — |
| `assistantId` | Yes, unless named in `agentUrl` (`agentId` is accepted as an alias) | — |
| `apiVersion` | No | `2025-01-01-preview` |
| `scope` | No | `credentials.scope`, else inferred from the endpoint |
| `surface` | No | Inferred from the endpoint — `assistants`, `responses`, or `inference` |
| `model` | No (one-shot surfaces) | `gpt-4o` |
| `instructions` | No (one-shot surfaces) | The agent definition's own instructions |

**Or name both at once.** A top-level `agentUrl` can carry the endpoint and the agent together, so every agent type names its location the same field way A2A does:

```json
"agentUrl": "https://my-resource.openai.azure.com/openai/assistants/asst_abc123"
```

Conductor splits the trailing `/assistants/asst_x` (or `/agents/NAME` on a Foundry project) off as the agent, leaving the rest as the endpoint. Anything set in `rawConfig` wins over what the URL implies.

**Credential.** Which keys `credentials` holds decides how Conductor authenticates. The first match wins:

| Keys present | Auth used |
|---|---|
| `apiKey` (or `api_key`) | Sent as an `api-key` header. No token exchange at all. |
| `client_id` + `client_secret` + `tenant_id` | Service principal (Entra ID client credentials). |
| `managedIdentityClientId` | User-assigned managed identity. |
| *none* | The default Azure credential chain — environment, workload identity, managed identity, Azure CLI. Reached only when `credentials` is omitted or holds no auth key; a credential that is present but did not resolve fails instead. |

So a service principal, with the values coming from a stored secret:

```json
"credentials": {
  "client_id":     "${workflow.secrets.AZURE_CRED.client_id}",
  "client_secret": "${workflow.secrets.AZURE_CRED.client_secret}",
  "tenant_id":     "${workflow.secrets.AZURE_CRED.tenant_id}"
}
```

and a deployment running on managed identity can omit `credentials` entirely.

Resolved credentials are cached for ten minutes, keyed by the credential and the scope — so two endpoints that resolve to the same scope share one token rather than exchanging two. A `401` or `403` from Foundry discards the cached credential, so a rotated secret is picked up on the next poll rather than at the end of that window.

**Scope** follows the endpoint — `ai.azure.com` for a Foundry project, `ml.azure.com` for a model-inference endpoint, `cognitiveservices.azure.com` for the classic Assistants surface — unless `rawConfig.scope` or the credential's `.scope` sub-key overrides it.

**Running as the caller.** `useCallerIdentity: true` makes the agent run as the person who triggered the workflow rather than as the deployment: their Entra ID token is exchanged, via the OAuth 2.0 on-behalf-of grant, for one scoped to Foundry, so the agent sees only what that person can. Their own token is never forwarded to Foundry, and the exchanged token is never cached or reused across requests.

This needs the cluster wired to Entra ID SSO — the caller's assertion is supplied by that layer, not by a workflow definition — and a service principal in `credentials` to perform the exchange.

With no assertion, the call is ordinary credential-based auth. With an assertion but only part of a service principal, the task fails: running as the deployment would silently ignore the request to act as the caller and use the server's own, wider privileges.

**`executionId`** is the Azure **thread** id. The run acted on is always the newest one on that thread, so continuing a conversation never invalidates the handle your workflow holds.

---

## OpenAI Assistants

Same thread-and-run protocol as Foundry — the difference is auth and the base URL.

```json
{
  "name": "ask_the_assistant",
  "taskReferenceName": "ask_the_assistant",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "openai-assistants",
    "prompt": "${workflow.input.question}",
    "credentials": { "api_key": "${workflow.secrets.OPENAI_KEY}" },
    "rawConfig": {
      "assistantId": "asst_abc123"
    }
  }
}
```

**`rawConfig`**

| Key | Required | Default |
|---|---|---|
| `assistantId` | Yes | — |
| `baseUrl` | No | `https://api.openai.com/v1` |

**Credential.** `credentials.api_key` holds the key — `apiKey` works too:

```json
"credentials": { "api_key": "${workflow.secrets.OPENAI_KEY}" }
```

Requests carry `Authorization: Bearer <key>` and `OpenAI-Beta: assistants=v2`.

**`executionId`** is the OpenAI thread id.

Use `baseUrl` to point at an Assistants-compatible gateway or proxy.

---

## AWS Bedrock

Bedrock behaves differently from the other two, and it is worth knowing why before you use it.

```json
{
  "name": "ask_the_bedrock_agent",
  "taskReferenceName": "ask_the_bedrock_agent",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "bedrock",
    "prompt": "${workflow.input.question}",
    "credentials": {
      "accessKeyId":     "${workflow.secrets.AWS_CRED.accessKeyId}",
      "secretAccessKey": "${workflow.secrets.AWS_CRED.secretAccessKey}"
    },
    "rawConfig": {
      "agentId": "AGENT123456",
      "agentAliasId": "ALIAS1234",
      "region": "us-east-1"
    }
  }
}
```

**`rawConfig`**

| Key | Required | Default |
|---|---|---|
| `agentId` | Yes | — |
| `agentAliasId` | Yes | — |
| `region` | No | `us-east-1` |

**Credential.** `credentials` holds either static keys:

```json
"credentials": {
  "accessKeyId":     "${workflow.secrets.AWS_CRED.accessKeyId}",
  "secretAccessKey": "${workflow.secrets.AWS_CRED.secretAccessKey}"
}
```

Or have Conductor assume a role instead, with `roleArn` — and optionally `roleSessionName` and `externalId`:

```json
"credentials": { "roleArn": "arn:aws:iam::123456789012:role/conductor-bedrock" }
```

The SDK refreshes the temporary credentials for as long as the agent runs.

Omit `credentials` to fall back to the server's default AWS credential chain — instance role, environment variables, or `~/.aws/credentials`. That fall-back is for a task that supplies nothing; a task that supplies credentials which do not resolve fails rather than running under the server's own role.

**Or use a Bedrock API key**, which takes precedence over everything above:

```json
"credentials": { "apiKey": "${workflow.secrets.BEDROCK_API_KEY}" }
```

A Bedrock API key is a bearer token rather than something SigV4 signs, so Conductor switches the
client to bearer auth for it. The AWS service model declares only SigV4, so left alone the SDK would
sign the request and ignore the key entirely.

**Or name the agent in one field**, as with Microsoft Foundry:

```json
"agentUrl": "bedrock://AGENT123456/ALIAS1234?region=us-west-2"
```

**No status API.** `InvokeAgent` streams the whole turn, so the agent has finished — or blocked on a tool — before the start call returns. The task therefore reaches a terminal state on its **first** invocation and is never polled; `pollIntervalSeconds` has no effect.

**No cancel API.** `maxDurationSeconds` and parent-workflow cancellation still fail the Conductor task, but nothing is sent to Bedrock, because the runtime offers no way to stop a run.

**`executionId`** is the Bedrock session id, which you may set yourself with `sessionId`. Bedrock holds the conversation against that id, subject to the idle-session TTL configured on the agent alias — so a resume that arrives after that window will not see the earlier turns.

---

## Observability

**What the agent did shows up in the task output.** Two kinds of tool call, reported differently:

| Kind | Who runs it | Where it appears |
|---|---|---|
| Function tools | Your workflow, or a worker | `pendingTools` while the run is paused, then `toolDispatchId` under `autoRunTools` |
| Built-in tools — web search, code interpreter, file search | The platform, inside the run | `executedTools` on completion |

A built-in tool never pauses the run, so it is invisible to `pendingTools`. On the Responses API
surface it is read from the reply's own output items; on the classic Assistants surface it is read
from the run's steps, fetched once when the run reaches a terminal state rather than on every poll.
Each call carries its type, id, status, and whatever field that tool puts its input in — the shape
differs per tool and Azure adds new ones, so it is carried across as it comes.

**Conductor emits no OpenTelemetry.** Microsoft Foundry's own Tracing view is fed by GenAI spans
sent to a connected Application Insights resource, normally by an Azure SDK with telemetry enabled.
Conductor calls the REST API directly and does not emit spans, so a run driven from an `AGENT` task
will not appear there. The Conductor execution is the record of the run — including `executedTools`
above.

## Failure handling

| Situation | Outcome |
|---|---|
| `prompt` missing | The workflow definition is rejected at save, naming the task. Nothing reaches the platform |
| Missing or malformed `rawConfig` / `credentials` | Task fails terminally — no retry, since a retry cannot fix it |
| `credentials` omitted entirely | Falls through to the platform's default credential chain — how a deployment on managed identity or an instance role is meant to be configured |
| `credentials` set, but no key resolved to a value | Task fails, naming the keys. A reference resolves to nothing when the secret is missing or does not hold that key, and running as the server's own identity instead would authenticate as somebody else |
| `credentials` set, some keys resolved and some not | Task fails, naming both groups — an incomplete credential is not a reason to fall back |
| A credential wrapped in quote characters | Task fails, naming the field. The quotes are part of the stored value; see [The secret has to be JSON](#the-secret-has-to-be-json) |
| A credential still holding `${workflow.secrets.…}` | Task fails. Conductor does not substitute secrets for input held in external payload storage, and running as the host's identity instead would be worse than failing |
| Platform returns `401` / `403` | Cached credential discarded, task retries; a rotated secret is picked up on the next poll |
| Platform unreachable or `5xx` | Counted as a transient poll failure, up to `maxPollFailures` |
| Run exceeds `maxDurationSeconds` | Cancellation attempted on the platform, task fails terminally |
| Agent run fails on the platform | Task fails, carrying the platform's error in `reasonForIncompletion` |
| A tool task exhausts its retries (`autoRunTools`) | Remaining tools are stopped, the agent run is cancelled, and the task fails with that tool's reason |
