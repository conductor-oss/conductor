---
description: "MCP (Model Context Protocol) integration with Conductor — connect AI agents to external tools, discover tools at runtime, execute with durable retry, and expose workflows as MCP tools."
---

# MCP Integration

<section class="integration-hero integration-hero--mcp" aria-label="MCP integration">
  <p>The <strong>Model Context Protocol (MCP)</strong> is the open standard agents use to discover and call tools. In Conductor, MCP calls run as workflow tasks: <code>LIST_MCP_TOOLS</code> asks a server what it offers, and <code>CALL_MCP_TOOL</code> invokes one tool. Because each call is a task, it gets the same retries, observability, and history as every other step.</p>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="#list_mcp_tools-discover-available-tools">
      <span class="integration-action-card__title">Discover tools</span>
      <span>Inspect an MCP server’s available capabilities at runtime.</span>
    </a>
    <a class="integration-action-card" href="#call_mcp_tool-execute-a-tool">
      <span class="integration-action-card__title">Call tools</span>
      <span>Run a selected tool as a native Conductor task.</span>
    </a>
    <a class="integration-action-card" href="#exposing-workflows-as-mcp-tools">
      <span class="integration-action-card__title">Expose workflows</span>
      <span>Publish durable workflow logic as an MCP tool.</span>
    </a>
  </div>
</section>


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

#### Selecting the MCP protocol version: `mcpOptions`

`CALL_MCP_TOOL` accepts an optional input object, `mcpOptions`. Its one field, `protocolVersion`, selects the MCP protocol version for the call.

<!-- TODO: verify against live server -->
```json
{
  "name": "generate_report",
  "taskReferenceName": "generate_report",
  "type": "CALL_MCP_TOOL",
  "inputParameters": {
    "mcpServer": "${workflow.input.mcpServerUrl}",
    "method": "generate_report",
    "arguments": { "region": "us-west1" },
    "mcpOptions": { "protocolVersion": "2026-07-28" }
  }
}
```

| `mcpOptions` | Behavior |
|---|---|
| absent, `{}`, or `protocolVersion` absent or `null` | The legacy request: the same request and the same output as before `mcpOptions` existed. This never changes in a later Conductor release. |
| `"protocolVersion": "2026-07-28"` | The call uses MCP protocol revision `2026-07-28`, including the MCP Tasks extension (see below). |
| any other `protocolVersion` value, including `"auto"` | The task fails with a terminal error (`FAILED_WITH_TERMINAL_ERROR`, not retried) before any request is sent. The reason names the value and lists the supported versions. |
| an unknown key (for example `"tasks"`), a value that is not an object, or a `protocolVersion` that is not a string | The task fails with a terminal error (not retried) before any request is sent, with a reason that names the problem. |

- **Where it is read:** only from the top level of the task input. A key named `mcpOptions` inside `arguments` is an ordinary tool argument and is sent to the tool.
- **Scope:** `CALL_MCP_TOOL` only. `LIST_MCP_TOOLS` always sends the legacy request.
- **Validation time:** `mcpOptions` is checked when the task runs, after expressions such as `${workflow.input.mcpOptions}` are resolved.
- **Upgrading:** before `mcpOptions` existed, a top-level input key with that name was ignored. It is now read and validated. If an existing `CALL_MCP_TOOL` task passes an unrelated top-level `mcpOptions` key (for example, through wiring that spreads tool arguments at the top level of the input), the task now fails validation, or selects a protocol version. Keep tool arguments inside `arguments`.

**Long-running tools: MCP Tasks is decided by the server.** With `protocolVersion: "2026-07-28"`, Conductor tells the server that it supports the MCP Tasks extension. There is no separate switch for this. The server then decides, for each call, how to answer:

- **An immediate result:** the task completes in the same run, with the same output as a legacy call.
- **A task handle:** the server runs the tool in the background, and Conductor checks its status until it finishes.

Selecting the protocol version does not make every call poll, and you do not need to know in advance how long a tool runs. It does allow any call to become long-running when the server chooses to create a task.

**Output of a polled call.** While Conductor polls a task, the task is `IN_PROGRESS`, and its output holds only the polling state:

```json
{ "mcpTask": { "taskId": "<remote-task-id>", "pollFailures": 0, "retryCount": 0, "pollIntervalMs": 5000 } }
```

- `taskId` is the server's task ID. `pollFailures` counts consecutive failed status checks, and returns to `0` after a successful one. `retryCount` is the task's retry count when the remote task was created. `pollIntervalMs` is the last interval the server gave; it is left out when the server gave none.
- When the task completes, the output is the same as for an immediate result (`content`, `isError`), plus `mcpTask`. `mcpTask` stays in the final output, so it is also visible in the UI and in LLM conversation history, as the polling state of the `AGENT` task is.
- A call that completes immediately has no `mcpTask` key. One exception: when the engine re-schedules a cancelled task in place (for example, next to a retried sub-workflow), the task keeps its earlier output, so an `mcpTask` from the earlier run can remain.

**Polling limits.**

| Setting | Value |
|---|---|
| Interval between status checks | the server's `pollIntervalMs`, rounded up to whole seconds. When a response gives none, the last interval the server gave; 5 seconds when the server never gave one. |
| Maximum duration, from the task's start | 24 hours. Then Conductor sends `tasks/cancel` and the task fails with a terminal error (`FAILED_WITH_TERMINAL_ERROR`), so a retry does not start the tool again. |
| Consecutive failed status checks (network error, timeout, JSON-RPC `-32603` internal error, or HTTP 408, 429 or 5xx, as described below) | 30. Then Conductor sends `tasks/cancel` and the task fails. After each failed check, the wait doubles, from the interval above up to 5 minutes (or the server's interval, if longer). |
| Time limit of one status check / one cancel request | 30 seconds / 5 seconds |

A remote task that fails, that the server cancels, or that asks for input (`input_required`, which is not supported) fails the Conductor task. Its retry settings apply as for any failed `CALL_MCP_TOOL` task; a retry sends a new `tools/call`.

With `protocolVersion: "2026-07-28"`, Conductor sends the requests for that call in the `2026-07-28` form:

- **Request metadata:** each request has `params._meta` with `io.modelcontextprotocol/protocolVersion` (`2026-07-28`), `io.modelcontextprotocol/clientInfo` (`{"name": "conductor", "version": ...}`), and `io.modelcontextprotocol/clientCapabilities`. The tool call declares the Tasks extension there.
- **Protocol headers:** `MCP-Protocol-Version`, `Mcp-Method`, and, where the protocol requires it, `Mcp-Name`. These replace any header with the same name in `headers`. Other headers, such as `Authorization`, are forwarded as before, without encoding. In the `Mcp-Method`, `Mcp-Name` and `Mcp-Param-*` headers, a value that is not plain printable ASCII is sent Base64-encoded as `=?base64?...?=`.
- **Tool schema lookup:** before the call, Conductor reads the tool's definition with `tools/list`, following `nextCursor` across pages. For each input property annotated with `x-mcp-header`, Conductor sends the argument's value in an `Mcp-Param-<name>` header. The task fails before the tool is called if the server does not list the tool, if an `x-mcp-header` annotation is invalid (for example, on a property whose type is not `string`, `integer` or `boolean`), or if an annotated argument is not a string, a boolean, or an integer.

The MCP server must support protocol revision `2026-07-28`. Conductor does not fall back to another protocol version or to the legacy request. If the server does not accept the selected version, the task fails with a reason that says so; remove `protocolVersion` to use the legacy request, or update the server.


#### Cancellation and known limitations

**Cancellation.** Conductor sends a best-effort `tasks/cancel` for the remote task when:

- the workflow ends while the call is polling: it is terminated, times out, or fails because another task failed
- the 24-hour maximum duration or the 30-failure limit is reached
- the remote task asks for input (`input_required`)
- a status check gets a response that cannot be used (over 10 MiB, nested more than 32 levels, or without a task status), or a status that the Tasks extension does not define
- the server answers a status check with a JSON-RPC error code that JSON-RPC or MCP defines, other than "unknown task" (`-32602`) and "internal error" (`-32603`), whatever the HTTP status. Such a code is the server's own answer, so it decides before the HTTP status. The defined codes are `-32700` and `-32600` to `-32603` (JSON-RPC), and `-32002`, `-32020` to `-32022` and `-32042` (MCP).
- a status check gets an HTTP status other than 401, 403, 408, 429 or 5xx (including 200), and either no JSON-RPC error or a code with no defined meaning (any other code, such as `-32000` or `-1`). Servers use such codes both for server failures and for rejected requests, so the HTTP status decides.

Cancellation is best effort. A failed or slow `tasks/cancel` is logged and does not hold up the workflow; each cancel request is limited to 5 seconds, for each cancelled task. The engine cancels a workflow's tasks one after another while it holds the workflow lock (60 seconds by default, `conductor.app.lockLeaseTime`), so a workflow with many polling calls to an unreachable server can take longer than the lock to terminate. The MCP server decides whether and when to stop the work.

**Known limitations.**

- **Task-level timeouts do not cancel the remote task.** When `timeoutSeconds` or `responseTimeoutSeconds` of the task definition times out a `CALL_MCP_TOOL` task, Conductor does not send `tasks/cancel`. The remote task keeps running until it finishes, expires, or the server stops it.
- **Secrets are not resolved in termination-time cancels.** When a workflow is terminated, the cancel request uses the task's literal input. A header written as `${workflow.secrets.<name>}` is sent unresolved, so the server may reject the cancel. Cancels sent because of a limit or `input_required` are not affected.
- **Oversized or unreadable response to the first request.** If `tools/call` creates a remote task but the response is larger than 10 MiB, nested more than 32 levels deep, or has an invalid task ID or status, the call fails before Conductor stores the task ID, so the remote task is not cancelled. A later status check with such a response cancels the remote task, then fails.
- **Termination during the first request.** If a workflow is terminated while the call's first request is still running, the remote task that the server creates can be left running without a cancel.
- **Retries and time limits without a task definition.** Without a task definition, or with a task-definition `retryCount` below 1, `CALL_MCP_TOOL` is retried up to 3 times by default, with a linear backoff of 2, 4 and 6 seconds. Each retry sends a new `tools/call`. No task-level timeout applies, so the 24-hour maximum duration and the 30-failure limit are the only time bounds. Both apply per attempt; a task that reaches the 24-hour limit is not retried. A task definition with a `retryCount` of 1 or more sets the number of retries, and `timeoutSeconds` sets a time limit. A task-definition `retryCount` of 0 cannot turn off retries, because it is raised to 3. To turn off retries, set `retryCount: 0` on the workflow task.
- **Stall after a server crash.** If the Conductor server that runs a status check crashes during the check, the next check runs only after the task's `responseTimeoutSeconds` (one hour when unset). This is the existing behavior of all asynchronous system tasks.
- **Duplicate execution.** MCP tools are not assumed to be idempotent. A retried `CALL_MCP_TOOL` task sends a new `tools/call`, so the tool can run more than once, as with any retried call. In addition, the shared AI HTTP client resends a `tools/call` after a network error, HTTP 429 or a 5xx response other than 501 (up to `conductor.ai.http.max-retries` times, 3 by default), for calls with and without `mcpOptions`. Status checks and cancel requests are not resent by the HTTP client. Conductor does not add deduplication.

**Rolling deployment and rollback.**

- **Rolling deployment:** a Conductor server without this feature ignores `mcpOptions`. If it runs the next check of a call that is polling, it sends a new `tools/call`, so the tool can run twice. Finish upgrading all servers before workflows set `mcpOptions.protocolVersion`.
- **Rollback:** persisted tasks still load; `mcpTask` is an ordinary output key. Before rolling back, stop starting workflows that set `mcpOptions.protocolVersion`, and check that no such `CALL_MCP_TOOL` task is `IN_PROGRESS`, or accept the duplicate-execution risk above.

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

- **[Production Agent Architecture](production-agent-architecture.md)** — govern and operate a tool-using agent after its first result.
- **[Build Your First Agentic Workflow Graph](first-ai-agent.md)** — Compose an SDK-authored agent with durable workflow tasks.
- **[Dynamic Workflows](dynamic-workflows.md)** — Agents that generate their own execution plans.
- **[Human-in-the-Loop](human-in-the-loop.md)** — Approval patterns for MCP tool calls.
- **[LLM Orchestration](llm-orchestration.md)** — 12 native LLM providers, vector databases, content generation.
