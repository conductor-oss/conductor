---
description: "Azure AI Foundry and AWS Bedrock test agents for A2A integration testing and documentation examples."
---

# A2A Test Agents

Test agents deployed on **Azure AI Foundry** and **AWS Bedrock** for validating the external agent
client integrations (PR [#1358](https://github.com/conductor-oss/conductor/pull/1358)) and for use
in documentation examples.

---

## Azure AI Foundry Agents

Three assistants deployed on Azure OpenAI (`ai-orkes-tests`).

**Account:** `ai-orkes-tests.openai.azure.com`
**Resource group:** `dl-testing`
**Model:** `gpt-4o-mini-viren` (deployment on `ai-orkes-tests` — all three assistants use this)

### Azure Setup

#### 1. Azure OpenAI Resource

The `ai-orkes-tests` resource was already provisioned. Assistants were created via
**Azure AI Foundry portal** (`ai.azure.com`):

1. Navigate to the resource → **AI Foundry portal**
2. Go to **Assistants** playground → **New assistant**
3. Set a name, system prompt, and select the model deployment (`gpt-4o-mini-viren`)
4. Save — this gives you an `asst_...` ID to use in Conductor

| Name | Assistant ID | System prompt |
|---|---|---|
| conductor-a2a-greeter | `asst_lEkmApixhANgnTj0ky8LUygc` | Friendly greeter |
| conductor-a2a-summarizer | `asst_GHSfevnpNTfpiWUGOZ3BXTzq` | Text summarizer (bullet points) |
| conductor-a2a-analyst | `asst_RpopyW7pURDhSbfX60q4HmOz` | Data analyst |

#### 2. Service Principal (OAuth credentials)

Conductor authenticates via OAuth 2.0 client credentials flow (Entra ID). A service principal
was created and granted access to the `ai-orkes-tests` resource:

```bash
# Create service principal
az ad sp create-for-rbac --name conductor-a2a-test --skip-assignment

# Assign roles on the Azure OpenAI resource
az role assignment create \
  --assignee <appId> \
  --role "Cognitive Services OpenAI User" \
  --scope /subscriptions/<sub>/resourceGroups/dl-testing/providers/Microsoft.CognitiveServices/accounts/ai-orkes-tests

az role assignment create \
  --assignee <appId> \
  --role "Cognitive Services OpenAI Contributor" \
  --scope /subscriptions/<sub>/resourceGroups/dl-testing/providers/Microsoft.CognitiveServices/accounts/ai-orkes-tests
```

### Azure Agents

#### conductor-a2a-greeter
**ID:** `asst_lEkmApixhANgnTj0ky8LUygc` | **Latency:** ~2–4 seconds

Simple one-shot agent. Responds with a warm greeting. No tools. Use this to verify basic
A2A connectivity — agent card discovery, `message/send`, and `completed` state in one round-trip.

#### conductor-a2a-summarizer
**ID:** `asst_GHSfevnpNTfpiWUGOZ3BXTzq` | **Latency:** ~4–8 seconds

Returns a structured summary (one-line title + 3–5 bullet points) for any input text. Good for
testing artifact content in the A2A task response.

#### conductor-a2a-analyst
**ID:** `asst_RpopyW7pURDhSbfX60q4HmOz` | **Latency:** ~8–15 seconds

Data analyst agent. Given a question or dataset, provides root cause analysis and recommended
next steps. Good for testing multi-poll `tasks/get` cycles.

### Azure Credentials

Store the service principal as a JSON blob in Conductor's secret store:

```bash
export CONDUCTOR_SECRET_AZURE_ORK_TESTS='{
  "client_id": "<appId>",
  "client_secret": "<secret>",
  "tenant_id": "<tenantId>"
}'
```

`CredentialResolutionService` resolves dotted-path sub-keys automatically — `credentialRef: "AZURE_ORK_TESTS"`
reads `AZURE_ORK_TESTS.client_id`, `.client_secret`, `.tenant_id` from the JSON blob.
OAuth scope: `https://cognitiveservices.azure.com/.default`.

### Azure AGENT Task Config

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

**Notes:**
- `agentType` must be `"azure-foundry"` (hyphen, not underscore)
- `endpoint` must include the `/openai` path suffix
- Two bugs fixed in `AzureFoundryAgentClient` during testing (PR [#1421](https://github.com/conductor-oss/conductor/pull/1421)):
  - `DEFAULT_SCOPE` was `management.azure.com` → fixed to `cognitiveservices.azure.com`
  - `API_VERSION` was hardcoded `2025-05-01` → changed to configurable default `2025-01-01-preview`

### Azure Test Results (2026-07-29)

| Agent | Input | Output |
|---|---|---|
| greeter | `"Hello! My name is Shailesh."` | `"Hello Shailesh! It's wonderful to meet you!"` |
| summarizer | Paragraph about Conductor OSS | 5-bullet structured summary |
| analyst | API latency degradation question | Root cause analysis with investigation areas |

---

## AWS Bedrock Agents

Three agents deployed on AWS Bedrock in `us-east-1`.

**Account:** `255364981640`
**Region:** `us-east-1`
**Model:** `amazon.nova-micro-v1:0` (cheapest Bedrock model, sufficient for testing)

### Bedrock Setup

#### 1. Create Agents in AWS Console

1. Go to **AWS Console → Amazon Bedrock → Agents → Create agent**
2. Select **Amazon Nova Micro** (`amazon.nova-micro-v1:0`) as the foundation model
3. Set the agent name and **Instructions** (system prompt)
4. Save and click **Prepare** — status must reach `PREPARED` before invocation
5. Note the **Agent ID** and **Agent Alias ID** (`TSTALIASID` is the default test alias)

Agents can also be created and prepared via CLI:

```bash
# Create agent
aws bedrock-agent create-agent \
  --agent-name "my-agent" \
  --foundation-model "amazon.nova-micro-v1:0" \
  --agent-resource-role-arn "<roleArn>" \
  --instruction "You are a ..." \
  --region us-east-1

# Prepare agent (required before invocation)
aws bedrock-agent prepare-agent --agent-id <agentId> --region us-east-1
```

| Name | Agent ID | Alias ID | Instructions |
|---|---|---|---|
| shailesh-test-agent (greeter) | `KZGTZ8AKK2` | `TSTALIASID` | Friendly greeter with humor |
| conductor-bedrock-summarizer | `VBC3AZ8YUD` | `TSTALIASID` | Text summarizer (title + bullet points) |
| conductor-bedrock-analyst | `R2DEMCLLNR` | `TSTALIASID` | Data analyst (insights + next steps) |

#### 2. IAM Role

Each agent needs an IAM execution role (`AmazonBedrockExecutionRoleForAgents_*`). When creating
via the console, AWS auto-creates this role. When creating via CLI, pass `--agent-resource-role-arn`.
The role used for all 3 agents:
`arn:aws:iam::255364981640:role/service-role/AmazonBedrockExecutionRoleForAgents_8OUF84S8LH3`

### Bedrock Credentials

The Bedrock client falls back to the **default AWS credential chain** when no `credentialRef`
is set — this supports temporary credentials (SSO session tokens):

```bash
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_SESSION_TOKEN="..."   # required for temporary/SSO credentials
```

Set these env vars before starting the Conductor server. No `credentialRef` needed in the task.

> **Note:** The `credentialRef` path in `BedrockAgentClient` uses `AwsBasicCredentials` which
> does not support session tokens. Use env vars for SSO/temporary credentials.

### Bedrock AGENT Task Config

```json
{
  "name": "call_bedrock_greeter",
  "taskReferenceName": "bedrock_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "bedrock",
    "rawConfig": {
      "agentId": "KZGTZ8AKK2",
      "agentAliasId": "TSTALIASID",
      "region": "us-east-1"
    },
    "prompt": "${workflow.input.userMessage}"
  }
}
```

Replace `agentId` with `VBC3AZ8YUD` (summarizer) or `R2DEMCLLNR` (analyst) for the other agents.

### Bedrock Test Results (2026-07-29)

| Agent | Input | Output |
|---|---|---|
| greeter | `"Hello! My name is Shailesh."` | `"Hello Shailesh! It's always a pleasure to meet someone with a name that sounds like it could be a superhero's sidekick..."` |
| summarizer | Paragraph about Conductor OSS | 5-bullet structured summary |
| analyst | API latency degradation question | Asked for more context (expected — Nova Micro is minimal) |

---

## How the AGENT Task Works

Both Azure Foundry and Bedrock clients follow the same Conductor lifecycle:

```
Workflow triggers AGENT task
  → ConductorAgentClient.startAgent()   — authenticate + submit prompt
    → poll getAgentStatus()             — until COMPLETED / FAILED
      → return output to workflow
```

The difference is in the underlying API:

| | Azure Foundry | AWS Bedrock |
|---|---|---|
| Auth | Entra ID OAuth (client credentials) | AWS SigV4 / credential chain |
| API style | REST (Assistants API) — create thread → run → poll | Streaming invoke — buffers response in memory |
| Agent config | `assistantId` + `endpoint` | `agentId` + `agentAliasId` + `region` |
| `credentialRef` | Required (JSON blob with `client_id`, `client_secret`, `tenant_id`) | Optional (falls back to env vars) |
