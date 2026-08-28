---
description: "Hosted platform agents — call an agent you already run on Azure AI Foundry, AWS Bedrock, or the OpenAI Assistants API from an AGENT task. Runtime selection, rawConfig keys, credential shape, and the tool-call resume loop."
---

# Hosted platform agents

The `AGENT` task can drive an agent that lives on someone else's platform. The agent stays where it is — you keep authoring it in Azure AI Foundry, Bedrock, or the OpenAI dashboard — and Conductor supplies the durability around it: retries, timeouts, cancellation, human-in-the-loop pauses, and a persisted execution history.

Three runtimes ship today:

| `agentType` | Platform | Client |
|---|---|---|
| `azure-foundry` | Azure AI Foundry Agents | `AzureFoundryAgentClient` |
| `openai-assistants` | OpenAI Assistants API | `OpenAiAssistantsAgentClient` |
| `bedrock` | AWS Bedrock Agent Runtime | `BedrockAgentClient` |

All three require the AI integration:

```properties
conductor.integrations.ai.enabled=true
```

Without it the client beans are absent, and a task routed to one of these runtimes fails as an unsupported `agentType`.

For the other two `agentType` values, see [A2A integration](a2a-integration.md) (`a2a`, remote Agent2Agent endpoints) and [Conductor agents](conductor-agents.md) (`conductor`, the embedded agentspan runtime).

!!! note "Vertex AI"
    There is no `vertex` runtime. Vertex AI agents speak A2A natively, so call them with `agentType: "a2a"` and the agent's A2A endpoint as `agentUrl`.

## They show up on their own

Store a secret with an `endpoint` key (Azure) or a `region` key (Bedrock) and the agents that credential can see appear in the agent list alongside agents defined in Conductor — no separate registration step. Discovery is best effort: a credential that cannot list contributes nothing rather than breaking the listing.

## What they have in common

Every hosted runtime takes the same core inputs.

| Field | Description |
|---|---|
| `agentType` | Selects the runtime (required). |
| `prompt` | The message to send. |
| `credentialRef` | Name of the stored secret holding the platform credential. Shape differs per platform — see below. |
| `rawConfig` | Platform-specific configuration: which agent, where it lives. |
| `executionId` | Set on a **later** `AGENT` task to resume an existing conversation instead of starting one. |
| `autoRunTools` | Run the agent's tool calls as tasks in this workflow instead of handing them back (default false). See below. |
| `toolTaskNames` | Optional `tool name -> task name` overrides for `autoRunTools`. |
| `pollIntervalSeconds` | Poll cadence while the run is not terminal (default 5). |
| `maxDurationSeconds` | Absolute deadline before the task fails and the run is cancelled (default 86400). |
| `maxPollFailures` | Consecutive transient poll failures tolerated before failing (default 30). |

None of these clients keep per-run state in memory. The `executionId` returned by the start call is the platform's own conversation handle, and Conductor persists it in the task output; everything else needed to reach the run is re-derived from the task input on each call. A status poll, a tool reply, or a cancellation is therefore served correctly by any server replica, including one that never saw the run start.

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
    "agentType": "azure-foundry",
    "prompt": "compare Q3 revenue per engineer against Q2",
    "autoRunTools": true,
    "credentialRef": "AZURE_FOUNDRY_CRED",
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
    "agentType": "azure-foundry",
    "executionId": "${ask_the_analyst.output.executionId}",
    "prompt": "${lookup_revenue.output.response}",
    "credentialRef": "AZURE_FOUNDRY_CRED",
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

## Azure AI Foundry

```json
{
  "name": "ask_the_analyst",
  "taskReferenceName": "ask_the_analyst",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "azure-foundry",
    "prompt": "${workflow.input.question}",
    "credentialRef": "AZURE_FOUNDRY_CRED",
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
| `endpoint` | Yes, unless given as `agentUrl` or the `AZURE_FOUNDRY_ENDPOINT` secret is set | — |
| `assistantId` | Yes, unless named in `agentUrl` (`agentId` is accepted as an alias) | — |
| `apiVersion` | No | `2025-01-01-preview` |
| `scope` | No | `credentialRef.scope`, else inferred from the endpoint |

**Or name both at once.** A top-level `agentUrl` can carry the endpoint and the agent together, so every agent type names its location the same field way A2A does:

```json
"agentUrl": "https://my-resource.openai.azure.com/openai/assistants/asst_abc123"
```

Conductor splits the trailing `/assistants/asst_x` (or `/agents/NAME` on a Foundry project) off as the agent, leaving the rest as the endpoint. Anything set in `rawConfig` wins over what the URL implies.

**Credential.** `credentialRef` names a secret whose contents decide how Conductor authenticates. The first match wins:

| Secret holds | Auth used |
|---|---|
| `apiKey` | Sent as an `api-key` header. No token exchange at all. |
| `client_id` + `client_secret` + `tenant_id` | Service principal (Entra ID client credentials). |
| `clientId` | User-assigned managed identity. |
| *nothing, or no `credentialRef` at all* | The default Azure credential chain — environment, workload identity, managed identity, Azure CLI. |

So a service principal looks like:

```json
{
  "client_id": "<application (client) id>",
  "client_secret": "<client secret value>",
  "tenant_id": "<directory (tenant) id>"
}
```

and a deployment running on managed identity can configure no credential at all.

Resolved credentials are cached for ten minutes per credential and scope, so a poll performs no secret-store read. A `401` or `403` from Foundry discards the cached credential, so a rotated secret is picked up on the next poll rather than at the end of that window.

**Scope** follows the endpoint — `ai.azure.com` for a Foundry project, `ml.azure.com` for a model-inference endpoint, `cognitiveservices.azure.com` for the classic Assistants surface — unless `rawConfig.scope` or the credential's `.scope` sub-key overrides it.

**Running as the caller.** `useCallerIdentity: true` makes the agent run as the person who triggered the workflow rather than as the deployment: their Entra ID token is exchanged, via the OAuth 2.0 on-behalf-of grant, for one scoped to Foundry, so the agent sees only what that person can. Their own token is never forwarded to Foundry, and the exchanged token is never cached or reused across requests.

This needs the cluster wired to Entra ID SSO — the caller's assertion is supplied by that layer, not by a workflow definition — and a service principal on the credential to perform the exchange. Without either, the call falls back to credential-based auth rather than failing, so a partially configured cluster still runs as the service identity.

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
    "credentialRef": "OPENAI_API_KEY",
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

**Credential.** `credentialRef` names a secret holding the API key, either directly:

```
sk-...
```

or under an `api_key` sub-key:

```json
{ "api_key": "sk-..." }
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
    "credentialRef": "AWS_BEDROCK_CRED",
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

**Credential.** `credentialRef` names a secret holding:

```json
{
  "accessKeyId": "<access key id>",
  "secretAccessKey": "<secret access key>"
}
```

Or have Conductor assume a role instead, with `roleArn` — and optionally `roleSessionName` and `externalId`:

```json
{ "roleArn": "arn:aws:iam::123456789012:role/conductor-bedrock", "externalId": "..." }
```

The SDK refreshes the temporary credentials for as long as the agent runs.

Leave `credentialRef` unset, or the secret incomplete, to fall back to the server's default AWS credential chain — instance role, environment variables, or `~/.aws/credentials`.

**Or name the agent in one field**, as with Azure:

```json
"agentUrl": "bedrock://AGENT123456/ALIAS1234?region=us-west-2"
```

**No status API.** `InvokeAgent` streams the whole turn, so the agent has finished — or blocked on a tool — before the start call returns. The task therefore reaches a terminal state on its **first** invocation and is never polled; `pollIntervalSeconds` has no effect.

**No cancel API.** `maxDurationSeconds` and parent-workflow cancellation still fail the Conductor task, but nothing is sent to Bedrock, because the runtime offers no way to stop a run.

**`executionId`** is the Bedrock session id, which you may set yourself with `sessionId`. Bedrock holds the conversation against that id, subject to the idle-session TTL configured on the agent alias — so a resume that arrives after that window will not see the earlier turns.

---

## Failure handling

| Situation | Outcome |
|---|---|
| Missing or malformed `rawConfig` / `credentialRef` | Task fails terminally — no retry, since a retry cannot fix it |
| Credential not found or incomplete in the secret store | Falls through to the next auth mode, ending at the platform's default credential chain |
| Platform returns `401` / `403` | Cached credential discarded, task retries; a rotated secret is picked up on the next poll |
| Platform unreachable or `5xx` | Counted as a transient poll failure, up to `maxPollFailures` |
| Run exceeds `maxDurationSeconds` | Cancellation attempted on the platform, task fails terminally |
| Agent run fails on the platform | Task fails, carrying the platform's error in `reasonForIncompletion` |
| A tool task exhausts its retries (`autoRunTools`) | Remaining tools are stopped, the agent run is cancelled, and the task fails with that tool's reason |
