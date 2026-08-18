---
description: "Configure Orkes Context Graph memory capture and MCP recall for AgentSpan agents."
---

# OCG memory for AgentSpan

AgentSpan sends completed runs to Orkes Context Graph (OCG) as raw events. OCG owns session
folding, summarization, structural fallback, versioning, indexing, TTL, pinning, feedback, and
ranking. AgentSpan does not create a local last-turn summary and does not call `cg_set_memory` when
a run finishes.

## Configuration

Enable the AI integration and store the OCG API key in Conductor's credential store:

```properties
conductor.integrations.ai.enabled=true
```

Set `longTermMemory` on the agent definition. `credential` is the credential name, never the API
key value:

```json
{
  "name": "support_agent",
  "model": "openai/gpt-4o",
  "instructions": "Help the user.",
  "longTermMemory": {
    "ocgUrl": "https://ocg.example.com",
    "credential": "OCG_API_KEY",
    "agent": "agentspan",
    "user": "user:alice"
  }
}
```

### Security boundary

`ocgUrl` is trusted agent-definition configuration, not a value supplied when an execution starts.
Creating or changing it requires the same authority as deploying an agent that uses credentialed MCP
tools. Such an agent author can already direct a server-resolved credential to the configured MCP
server, so using that credential for terminal OCG capture does not add a new outbound-request or
credential-access capability. Under Conductor's authorization model, this is not a vulnerability or
a separate security boundary.

Deployments that allow untrusted principals to author agent definitions must restrict that authority
or enforce their own network egress policy for all credentialed agent integrations, including MCP
and OCG. Applying a capture-only URL allowlist would not secure the existing MCP path.

Use the same `agent` and optional `user` identity for capture and recall. The configured user takes
precedence; runtime input `user` is used when the configuration omits it. Runtime values are
normalized to the `user:<id>` form. If user identity is not available, omit it so OCG uses the
private owner for the configured agent.

Supply these stable inputs when starting a run:

- `session_id`: the conversation/session identifier shared by its turns.
- `prompt`: the original user input.
- `user`, `repo`, `branch`, and `cwd`: optional capture metadata.

The Conductor workflow execution id is the stable `execution_id`. Retrying export for the same
execution therefore sends the same `session_id` and `execution_id`.

## Recall, capture, and feedback

For a root agent workflow, the compiler calls `cg_search_memories` directly through
`{ocgUrl}/mcp/` before the first model or sub-agent task. The query is the original `prompt`, the
owner is the configured `agent`, shared memories are included, and the initial result limit is five.
Conductor normalizes MCP text blocks, caps the recalled text to the agent context value limit, and
injects it as explicitly untrusted supporting context. Search and normalization are optional, so an
OCG outage does not prevent the agent from running.

This compiler-owned recall does not expose OCG tools to the model and does not run MCP discovery.
An OCG MCP declaration with `config.tool_names` is compiled from Conductor's bundled schemas without
`LIST_MCP_TOOLS`. The model-callable lookup catalog contains `cg_query`, `cg_get_neighbors`,
`cg_traverse`, `cg_shortest_path`, `cg_has_path`, and `cg_find_all_paths`; lifecycle memory write,
delete, sharing, history, and cleanup tools are not exposed. Compiled sub-agents do not receive
their own automatic search or terminal listener, but the root recall is forwarded to their initial
context.

At root workflow completion or termination, the workflow listener reads the persisted task history,
maps tool calls and sub-workflows (including outputs and errors), and asynchronously posts it to
`{ocgUrl}/api/v1/memories/agent-run` with `X-API-Key`. OCG timeouts and errors are contained and do
not change the agent result. If the request approaches OCG's 10 MiB limit, only event detail and
output are truncated; the original prompt and final result are preserved.

Completed-execution feedback is exposed through Conductor at
`GET/POST /api/agent/executions/{executionId}/feedback`; the browser never receives the OCG key.
For an eligible completed root execution, Conductor uses its server-resolved OCG credential to read
and upsert the canonical rating at `{ocgUrl}/api/v1/memories/agent-run/feedback`. OCG derives the
memory partition from that API key's owner; neither the browser nor workflow input selects a user
partition. The feedback dialog reads the OCG-generated execution memory from
`{ocgUrl}/api/v1/agent-runs/{executionId}/memory` and never receives the OCG key.
