# MCP tool agent

**Derived from:** `ai/examples/10-mcp-ai-agent.json` (cookbook name and prose only).

**Outcome:** collect read-only incident evidence from an MCP server and produce a concise, auditable report.

```mermaid
flowchart LR
  I[Incident ID] --> D[LIST_MCP_TOOLS]
  D --> R[CALL_MCP_TOOL read]
  R --> B[LLM brief]
```

## Prerequisites and contract

Expose only read tools such as `get_incident` on the MCP server and store its credential as `INCIDENT_MCP_TOKEN` in the platform secrets system. Input is `mcpServerUrl`, `method`, and `arguments`; output is `report` plus immutable `evidence`. Do not put the token in workflow input.

For a local deterministic MCP server, install and run [mcp-testkit](https://pypi.org/project/mcp-testkit/):

```bash
python -m pip install mcp-testkit
mcp-testkit --transport http
```

It listens at `http://localhost:3001/mcp`. Use `LIST_MCP_TOOLS` to inspect its exact tool names. For the runnable testkit smoke test below, use its deterministic read-only `get_weather` tool.

## Runnable definition

Save this as `mcp-incident-investigator.json`:

```json
--8<-- "docs/devguide/ai/cookbook/assets/mcp-incident-investigator.json"
```

## Register and run

```bash
conductor workflow create mcp-incident-investigator.json
conductor workflow start -w mcp_incident_investigator --sync -i '{"mcpServerUrl":"http://localhost:3001/mcp","method":"get_weather","arguments":{"city":"San Francisco"}}'
```

Open **[Executions](http://localhost:8080/executions)** in the Conductor UI and select the new execution to review the task graph, and each task's inputs and outputs.

## Production notes

Tool discovery and reads have bounded retry, timeout, and concurrency. The prompt explicitly forbids writes; a remediation workflow should require a separate approval. Preserve the MCP result and request correlation ID for audit, truncate or reference oversized logs, and reconcile with the incident system before retriggering. Replace MCP URL, secret name, method schema, and retention policy for your environment.
