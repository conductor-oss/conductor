---
description: "Azure AI Foundry test agents used for A2A integration testing and documentation examples."
---

# Azure A2A Test Agents

Three agents deployed on Azure OpenAI (`ai-orkes-tests`) for testing the A2A client integration
(PR [#1358](https://github.com/conductor-oss/conductor/pull/1358)) and for use in documentation
examples.

**Account:** `ai-orkes-tests.openai.azure.com`
**Resource group:** `dl-testing`
**Model:** `gpt-4o-mini-viren` (deployment on `ai-orkes-tests` — all three assistants use this)

---

## Azure Setup

### 1. Azure OpenAI Resource

The `ai-orkes-tests` Azure OpenAI resource was already provisioned in the `dl-testing` resource
group. The assistants (agents) were created via **Azure AI Foundry portal** (`ai.azure.com`):

1. Navigate to the resource → **AI Foundry portal**
2. Go to **Assistants** playground → **New assistant**
3. Set a name, system prompt, and select the model deployment (`gpt-4o-mini-viren`)
4. Save — this gives you an `asst_...` ID to use in Conductor

The 3 assistants created:

| Name | Assistant ID | System prompt |
|---|---|---|
| conductor-a2a-greeter | `asst_lEkmApixhANgnTj0ky8LUygc` | Friendly greeter |
| conductor-a2a-summarizer | `asst_GHSfevnpNTfpiWUGOZ3BXTzq` | Text summarizer (bullet points) |
| conductor-a2a-analyst | `asst_RpopyW7pURDhSbfX60q4HmOz` | Data analyst |

### 2. Service Principal (OAuth credentials)

Conductor authenticates to Azure OpenAI using OAuth 2.0 client credentials flow (Entra ID).
A service principal was created and granted access to the `ai-orkes-tests` resource:

```bash
# Create service principal
az ad sp create-for-rbac --name conductor-a2a-test --skip-assignment

# Assign Cognitive Services roles on the Azure OpenAI resource
az role assignment create \
  --assignee <appId> \
  --role "Cognitive Services OpenAI User" \
  --scope /subscriptions/<sub>/resourceGroups/dl-testing/providers/Microsoft.CognitiveServices/accounts/ai-orkes-tests

az role assignment create \
  --assignee <appId> \
  --role "Cognitive Services OpenAI Contributor" \
  --scope /subscriptions/<sub>/resourceGroups/dl-testing/providers/Microsoft.CognitiveServices/accounts/ai-orkes-tests
```

The `client_id`, `client_secret`, and `tenant_id` from the service principal are stored as a
JSON blob in the Conductor secret store (see [Credentials](#credentials) below).

---

## Agents

### 1. conductor-a2a-greeter
**ID:** `asst_lEkmApixhANgnTj0ky8LUygc`
**Expected latency:** ~2–4 seconds

Simple one-shot agent. Responds with a single warm greeting sentence. No tools.
Use this to test basic A2A connectivity — agent card discovery, `message/send`, and
verifying the task reaches `completed` state in one round-trip.

**Good for testing:**
- Agent card discovery (`GET /.well-known/agent-card.json`)
- Basic `message/send` → `completed` flow
- Idempotency key behavior (send the same message twice, expect one execution)

---

### 2. conductor-a2a-summarizer
**ID:** `asst_GHSfevnpNTfpiWUGOZ3BXTzq`
**Expected latency:** ~4–8 seconds

Medium-complexity agent. Takes any text or topic and returns a structured summary
(one-line title + 3–5 bullet points). No tools, single response.
Longer than the greeter but still completes in one turn — good for testing artifact
content in the response.

**Good for testing:**
- Verifying artifact content in the A2A task response
- Passing larger input text via message parts
- `tasks/get` polling (task will be `working` briefly before `completed`)

---

### 3. conductor-a2a-analyst
**ID:** `asst_RpopyW7pURDhSbfX60q4HmOz`
**Expected latency:** ~8–15 seconds

Longer-running agent with `code_interpreter` tool enabled. Given a question or data,
it writes and executes Python code to answer it, then explains the results.
The code execution step is what makes it slower — realistic simulation of a
tool-using agent that takes a few polling cycles to complete.

**Good for testing:**
- Multi-poll `tasks/get` cycle (task stays `working` for several seconds)
- `message/stream` / SSE — you'll see intermediate status updates
- `tasks/cancel` — cancel mid-execution to test cancellation flow
- Verifying code output appears in artifacts

---

## Credentials

The service principal credentials are stored as a single JSON blob in Conductor's secret store.
Set the following env var before starting the server:

```bash
export CONDUCTOR_SECRET_AZURE_ORK_TESTS='{
  "client_id": "<appId>",
  "client_secret": "<secret>",
  "tenant_id": "<tenantId>"
}'
```

Conductor's `CredentialResolutionService` resolves dotted-path sub-keys automatically — e.g.
`credentialRef: "AZURE_ORK_TESTS"` causes the client to look up `AZURE_ORK_TESTS.client_id`,
`AZURE_ORK_TESTS.client_secret`, and `AZURE_ORK_TESTS.tenant_id` from that JSON blob.

The OAuth scope used is `https://cognitiveservices.azure.com/.default`.

---

## End-to-End Testing

All 3 agents were tested end-to-end using Conductor's **AGENT task** type on a local
Conductor OSS server (port 8091). Each agent was wired into a workflow definition and
executed via the Conductor API. All 3 completed successfully (2026-07-29).

### How the AGENT task works

```
Workflow triggers AGENT task
  → AzureFoundryAgentClient authenticates via Entra ID OAuth
    → Creates an OpenAI thread
    → Posts user message to thread
    → Starts a run against the assistant
    → Polls run status until completed
      → Reads last assistant message
        → Returns result as task output
```

### Workflow definition (AGENT task config)

```json
{
  "name": "call_greeter",
  "taskReferenceName": "call_greeter_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "azure-foundry",
    "rawConfig": {
      "endpoint": "https://ai-orkes-tests.openai.azure.com/openai",
      "assistantId": "asst_lEkmApixhANgnTj0ky8LUygc"
    },
    "credentialRef": "AZURE_ORK_TESTS",
    "prompt": "${workflow.input.userMessage}"
  }
}
```

Replace `assistantId` with `asst_GHSfevnpNTfpiWUGOZ3BXTzq` (summarizer) or
`asst_RpopyW7pURDhSbfX60q4HmOz` (analyst) to test the other agents.

### Test results

| Agent | Input | Output |
|---|---|---|
| greeter | `"Hello! My name is Shailesh."` | `"Hello Shailesh! It's wonderful to meet you!"` |
| summarizer | Paragraph about Conductor OSS | 5-bullet structured summary |
| analyst | API latency degradation question | Root cause analysis with investigation areas |

**Key config notes:**
- `agentType` must be `"azure-foundry"` (hyphen, not underscore)
- `endpoint` must include the `/openai` path suffix
- Two bugs were found and fixed in `AzureFoundryAgentClient` during this test (PR [#1421](https://github.com/conductor-oss/conductor/pull/1421)):
  - `DEFAULT_SCOPE` was `management.azure.com` → fixed to `cognitiveservices.azure.com`
  - `API_VERSION` was hardcoded `2025-05-01` → changed to configurable default `2025-01-01-preview`
