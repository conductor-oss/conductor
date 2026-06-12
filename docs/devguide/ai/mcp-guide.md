---
description: "MCP (Model Context Protocol) integration with Conductor — connect AI agents to external tools, discover tools at runtime, execute with durable retry, and expose workflows as MCP tools."
---

# MCP integration

MCP (Model Context Protocol) is the open standard for connecting AI agents to tools and data sources. Conductor provides native MCP integration — discover tools, call them with full durability, and expose your own workflows as MCP tools.


## What is MCP

MCP defines a protocol for how AI agents discover and use tools. Instead of hardcoding API integrations, your agent asks an MCP server "what tools do you have?" and gets back a structured list. The agent (or the LLM) picks the right tool, and the MCP server executes it.

**Without MCP:** Every tool integration is custom code — different auth, different schemas, different error handling.

**With MCP:** Tools are standardized. Connect once, use any MCP-compatible tool server.

Conductor supports MCP as a first-class integration with two native system tasks.


## Native MCP system tasks

### LIST_MCP_TOOLS — discover available tools

Queries an MCP server and returns the list of tools it offers, including names, descriptions, and parameter schemas.

```json
{
  "name": "discover_tools",
  "taskReferenceName": "discover",
  "type": "LIST_MCP_TOOLS",
  "inputParameters": {
    "mcpServer": "${workflow.input.mcpServerUrl}"
  }
}
```

**Output:** A structured list of tools with their schemas. Pass this directly to an LLM so it can decide which tool to call.

**Why this matters:** Tool discovery happens at runtime. Your agent doesn't need to know which tools exist at design time — it discovers them dynamically. Add a new tool to the MCP server, and every agent using it gains that capability immediately.


### CALL_MCP_TOOL — execute a tool

Calls a specific tool on an MCP server with the given arguments.

```json
{
  "name": "execute_tool",
  "taskReferenceName": "execute",
  "type": "CALL_MCP_TOOL",
  "inputParameters": {
    "mcpServer": "${workflow.input.mcpServerUrl}",
    "method": "${plan.output.result.method}",
    "arguments": "${plan.output.result.arguments}"
  }
}
```

**What Conductor adds on top of raw MCP:**

- **Durable execution** — if the tool call fails, Conductor retries according to the task's retry policy. The retry is automatic and configurable (fixed delay, exponential backoff, linear backoff).
- **Full audit trail** — every tool call is persisted: the method, arguments, response, timing, and retry history. You can inspect exactly what your agent did.
- **Crash recovery** — if the server crashes between tool calls, the workflow resumes from the last completed step. The tool call is never silently lost.
- **Timeout handling** — configure `responseTimeoutSeconds` to prevent stuck tool calls from blocking your agent.


## Connecting to MCP servers

Conductor connects to any MCP server via HTTP. Pass the server URL as a workflow input or hardcode it in the task definition.

```json
{
  "mcpServer": "http://localhost:3001/mcp"
}
```

### Using multiple MCP servers

An agent can connect to multiple MCP servers in the same workflow. Discover tools from each server, combine the tool lists, and let the LLM choose across all of them:

```json
{
  "name": "multi_tool_agent",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "discover_github_tools",
      "taskReferenceName": "github_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3001/mcp"
      }
    },
    {
      "name": "discover_db_tools",
      "taskReferenceName": "db_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3002/mcp"
      }
    },
    {
      "name": "plan_with_all_tools",
      "taskReferenceName": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "message": "Available tools: GitHub: ${github_tools.output.tools}, Database: ${db_tools.output.tools}. User task: ${workflow.input.task}. Pick the best tool. Respond with JSON: {\"server\": \"github\" or \"db\", \"method\": \"tool_name\", \"arguments\": {}}"
          }
        ],
        "temperature": 0.1
      }
    }
  ]
}
```


## Exposing workflows as MCP tools

Any Conductor workflow can be exposed as an MCP tool via the MCP Gateway. This means other agents and LLMs can discover and invoke your workflows using the MCP protocol.

```
Agent → LIST_MCP_TOOLS → discovers your workflow
Agent → CALL_MCP_TOOL → starts your workflow
Conductor → executes with full durability
Agent → receives structured output
```

Your workflow's `inputParameters` become the tool's input schema, and `outputParameters` become the tool's output. The workflow runs with full durable execution guarantees — retries, persistence, compensation — while appearing to the calling agent as a simple tool call.

This creates a composable architecture: workflows call MCP tools, and workflows *are* MCP tools. Agents can invoke other agents' workflows without knowing they're workflows.


## MCP vs HTTP vs custom workers

| Approach | When to use |
|----------|-------------|
| **MCP** (`LIST_MCP_TOOLS` + `CALL_MCP_TOOL`) | Tools exposed via MCP servers. Dynamic tool discovery. Agent decides which tool to call at runtime. |
| **HTTP** (`HTTP` system task) | Direct API calls with known endpoints. No tool discovery needed. |
| **Custom workers** (`SIMPLE` task) | Complex business logic that needs custom code. Multi-step processing. |

MCP is the best choice when your agent needs to **discover tools dynamically** or when you want to **standardize tool access** across multiple agents. Use HTTP for simple, known API calls. Use custom workers for logic that doesn't fit into a single API call.


## Complete example: MCP agent with approval

A production-ready agent that discovers tools, plans, gets human approval, executes, and summarizes:

```json
{
  "name": "mcp_agent_with_approval",
  "description": "Discover tools, plan, execute with approval, summarize",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["task", "mcpServerUrl"],
  "tasks": [
    {
      "name": "list_available_tools",
      "taskReferenceName": "discover_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}"
      }
    },
    {
      "name": "decide_which_tools_to_use",
      "taskReferenceName": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {
            "role": "system",
            "message": "You are an AI agent. Available tools: ${discover_tools.output.tools}. User wants to: ${workflow.input.task}"
          },
          {
            "role": "user",
            "message": "Which tool should I use and what parameters? Respond with JSON: {\"method\": \"string\", \"arguments\": {}}"
          }
        ],
        "temperature": 0.1,
        "maxTokens": 500
      }
    },
    {
      "name": "human_review",
      "taskReferenceName": "approval",
      "type": "HUMAN",
      "inputParameters": {
        "plannedAction": "${plan.output.result}"
      }
    },
    {
      "name": "execute_tool",
      "taskReferenceName": "execute",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}",
        "method": "${plan.output.result.method}",
        "arguments": "${plan.output.result.arguments}"
      }
    },
    {
      "name": "summarize_result",
      "taskReferenceName": "summarize",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {
            "role": "user",
            "message": "The user asked: ${workflow.input.task}\n\nTool result: ${execute.output.content}\n\nSummarize this result for the user."
          }
        ],
        "maxTokens": 500
      }
    }
  ],
  "outputParameters": {
    "plan": "${plan.output.result}",
    "toolResult": "${execute.output.content}",
    "summary": "${summarize.output.result}",
    "approvedBy": "${approval.output.reviewer}"
  }
}
```

Every task type here — `LIST_MCP_TOOLS`, `LLM_CHAT_COMPLETE`, `CALL_MCP_TOOL`, `HUMAN` — is a native Conductor system task. No custom code needed.


## Next steps

- **[Build Your First AI Agent](first-ai-agent.md)** — Step-by-step tutorial using MCP.
- **[Dynamic Workflows](dynamic-workflows.md)** — Agents that generate their own execution plans.
- **[Human-in-the-Loop](human-in-the-loop.md)** — Approval patterns for MCP tool calls.
- **[LLM Orchestration](llm-orchestration.md)** — 12 native LLM providers, vector databases, content generation.
