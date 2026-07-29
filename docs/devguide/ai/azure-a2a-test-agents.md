---
description: "Azure AI Foundry test agents used for A2A integration testing and documentation examples."
---

# Azure A2A Test Agents

Three agents deployed on Azure OpenAI (`ai-orkes-tests`) for testing the A2A client integration
(PR [#1358](https://github.com/conductor-oss/conductor/pull/1358)) and for use in documentation
examples.

**Account:** `ai-orkes-tests.openai.azure.com`
**Resource group:** `dl-testing`
**Model:** `gpt-4o-mini` (all three — cheapest available, no LLM cost concerns for testing)

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

Stored as environment variables — resolve via Conductor's secret store using
`AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`, and
`AZURE_FOUNDRY_ENDPOINT`.

For direct API testing using the API key:

```bash
# List all test agents
curl -H "api-key: $AZURE_OPENAI_API_KEY" \
  "https://ai-orkes-tests.openai.azure.com/openai/assistants?api-version=2025-01-01-preview"
```

---

## Example: calling the greeter via Conductor

Configure an `AGENT` task in a workflow:

```json
{
  "name": "call_greeter",
  "taskReferenceName": "call_greeter",
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

**Notes from local testing (2026-07-29):**
- `agentType` is `"azure-foundry"` (hyphen, not underscore)
- `endpoint` must include the `/openai` path suffix
- The model deployment on `ai-orkes-tests` is named `gpt-4o-mini-viren` — the 3 assistants
  above were originally created with model `gpt-4o-mini` (which doesn't exist as a deployment)
  and have been updated to `gpt-4o-mini-viren`
- Two bugs were found and fixed in `AzureFoundryAgentClient` during this test:
  - `DEFAULT_SCOPE` was `management.azure.com` → fixed to `cognitiveservices.azure.com`
  - `API_VERSION` was `2025-05-01` (unsupported on this resource) → changed to `2025-01-01-preview`
    as the default, overridable via `rawConfig.apiVersion`

Replace `assistantId` with `asst_GHSfevnpNTfpiWUGOZ3BXTzq` (summarizer) or
`asst_RpopyW7pURDhSbfX60q4HmOz` (analyst) to test the other agents.
