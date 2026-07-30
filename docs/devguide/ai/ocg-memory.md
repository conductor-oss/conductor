---
description: "Configure Orkes Context Graph memory capture and MCP recall for AgentSpan agents."
---

# OCG memory for AgentSpan

AgentSpan sends completed runs to Orkes Context Graph (OCG) as raw events. OCG owns session
folding, summarization, structural fallback, versioning, indexing, TTL, pinning, feedback, and
ranking. AgentSpan does not create a local last-turn summary and does not call `cg.set_memory` when
a run finishes.

## Configuration

Enable the AI integration and store the OCG API key in Conductor's credential store:

```properties
conductor.integrations.ai.enabled=true
conductor.ai.outbound.allowed-origins=https://ocg.example.com
```

The exact OCG origin must be allowed for MCP discovery. Private-network OCG deployments also need
the development-only `conductor.ai.outbound.allow-private-networks=true` setting where appropriate.

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

Use the same `agent` and optional `user` identity for capture and recall. The configured user takes
precedence; runtime input `user` is used when the configuration omits it. Runtime values are
normalized to the `user:<id>` form. If user identity is not available, omit it so OCG uses the
private owner for the configured agent.

Supply these stable inputs when starting a run:

- `session_id`: the conversation/session identifier shared by its turns.
- `prompt`: the original user input.
- `user`, `repo`, `branch`, and `cwd`: optional capture metadata.

The Conductor workflow execution id is the stable `turn_id`. Retrying export for the same execution
therefore sends the same `session_id` and `turn_id`.

## Capture and recall

At root workflow completion or termination, the workflow listener reads the persisted task history,
maps tool calls and sub-workflows (including outputs and errors), and asynchronously posts it to
`{ocgUrl}/api/v1/memories/agent-run` with `X-API-Key`. OCG timeouts and errors are contained and do
not change the agent result. If the request approaches OCG's 10 MiB limit, only event detail and
output are truncated; the original prompt and final result are preserved.

The compiler also registers `{ocgUrl}/mcp/` as a best-effort MCP server with the same credential.
Its discovered `cg.search_memories` tool lets the model recall prior work. `cg.get_memory`,
`cg.list_memories`, and `cg.set_memory` remain available for deliberate explicit memory operations;
none is used as the run-completion trigger. MCP discovery failure does not fail the run.
