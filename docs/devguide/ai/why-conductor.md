---
description: "Why Conductor for AI agents — native LLM tasks, MCP tool calling, deterministic JSON definitions, durable human-in-the-loop, and dynamic runtime execution. Show-don't-tell with code examples."
---

# Why Conductor for agents

Other engines give you generic primitives and say "build your agent infrastructure yourself." Conductor gives you the agent infrastructure. Here's what that looks like in practice.


## Call an LLM — zero boilerplate

Other engines treat LLM calls as generic function calls. You build the abstraction: prompt construction, provider switching, response parsing, token tracking, retry logic. On Conductor, an LLM call is a system task:

```json
{
  "name": "plan_action",
  "type": "LLM_CHAT_COMPLETE",
  "inputParameters": {
    "llmProvider": "anthropic",
    "model": "claude-sonnet-4-20250514",
    "messages": [
      {"role": "system", "message": "You are a planning agent. Tools: ${tools.output}"},
      {"role": "user", "message": "${workflow.input.goal}"}
    ],
    "temperature": 0.1,
    "maxTokens": 1000
  }
}
```

That's it. No SDK wrapper, no worker code, no retry logic. Conductor executes it, persists the prompt, response, token usage, model, and latency. Switch providers by changing `llmProvider` — from `anthropic` to `openai` to `bedrock` — with zero code changes. 14+ providers supported natively.

On other engines, this same task requires:

- A worker/activity function that constructs the HTTP request
- Provider-specific SDK initialization and auth
- Response parsing and error handling
- Custom logging for prompt/response/token tracking
- Retry configuration in your code, not the orchestrator

Every team builds this differently. Every implementation has different bugs.


## Discover and call tools — native MCP

MCP (Model Context Protocol) is the open standard for agent tool use. On Conductor, tool discovery and execution are system tasks:

```json
[
  {
    "name": "discover",
    "type": "LIST_MCP_TOOLS",
    "inputParameters": {
      "mcpServer": "http://localhost:3001/mcp"
    }
  },
  {
    "name": "execute",
    "type": "CALL_MCP_TOOL",
    "inputParameters": {
      "mcpServer": "http://localhost:3001/mcp",
      "method": "${plan.output.result.method}",
      "arguments": "${plan.output.result.arguments}"
    }
  }
]
```

The agent discovers tools at runtime, the LLM picks the right one, and Conductor executes it with automatic retry, timeout, and full audit trail. Connect to any MCP server — GitHub, Slack, databases, custom APIs — with no wrapper code.

On other engines, you write a "Durable MCP" wrapper: a custom activity/worker that connects to the MCP server, marshals requests, handles errors, and logs results. For every MCP server. For every tool type.


## Human-in-the-loop — one line, durable forever

An agent needs human approval before a risky action. On Conductor:

```json
{
  "name": "approval_gate",
  "type": "HUMAN",
  "inputParameters": {
    "action": "${plan.output.result.action}",
    "reasoning": "${plan.output.result.reasoning}"
  }
}
```

The workflow pauses. The pause survives server restarts, deploys, infrastructure changes — indefinitely. When someone approves via the API or UI, the workflow resumes with the approval payload. No polling, no timer hacks, no external state.

On other engines, you implement `wait_condition()` with signal handlers, write the signal routing code, and build the approval UI integration yourself. The pause mechanism is in your workflow code, not in the platform.


## Agent loops — checkpointed per iteration

An autonomous agent loops: plan, act, observe, repeat. On Conductor, each iteration is a durable checkpoint:

```json
{
  "name": "agent_loop",
  "type": "DO_WHILE",
  "loopCondition": "if ($.loop['think'].output.result.done == true) { false; } else if ($.loop['think'].output.iteration >= 20) { false; } else { true; }",
  "loopOver": [
    {
      "name": "think",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {"role": "system", "message": "Goal: ${workflow.input.goal}. Previous results: ${workflow.variables.context}. Respond with {action, arguments, done}."}
        ]
      }
    },
    {
      "name": "act",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}",
        "method": "${think.output.result.action}",
        "arguments": "${think.output.result.arguments}"
      }
    },
    {
      "name": "remember",
      "type": "SET_VARIABLE",
      "inputParameters": {
        "context": "${workflow.variables.context.concat([{action: think.output.result.action, result: act.output.content}])}"
      }
    }
  ]
}
```

If the agent crashes at iteration 18 of 20, it resumes from iteration 18. Not from scratch. The 17 completed LLM calls and tool executions are already persisted — zero tokens wasted, zero duplicate side effects. The loop condition enforces an iteration cap so the agent can't run forever.

On other engines, you build the loop in your workflow code. If the process crashes, you either restart from the beginning (burning all tokens again) or build your own checkpointing mechanism.


## Dynamic workflows — LLMs generate execution plans

This is the capability no other engine can match. An LLM generates a complete workflow definition as JSON, and Conductor executes it immediately:

```json
{
  "name": "execute_agent_plan",
  "type": "START_WORKFLOW",
  "inputParameters": {
    "startWorkflow": {
      "workflowDefinition": "${planner_llm.output.result}",
      "input": "${workflow.input.taskInput}"
    }
  }
}
```

The LLM's output is a Conductor workflow definition. No code generation. No compilation. No deployment pipeline. The generated workflow runs with the same durable execution guarantees as any hand-written workflow — persistence, retries, observability, replay.

Combined with `DYNAMIC` tasks (resolve which task to run at runtime) and `DYNAMIC_FORK` (create N parallel branches at runtime), Conductor is more dynamic than code-based engines. Not despite using JSON — because of it. Data is easier to generate, transform, and compose than code.

On code-based engines, dynamic workflows require generating source code, compiling it, deploying it, and then executing it. That friction fundamentally limits how dynamically an AI system can operate.


## RAG pipelines — native vector database support

Retrieval-augmented generation as two system tasks, no external framework:

```json
[
  {
    "name": "search",
    "type": "LLM_SEARCH_INDEX",
    "inputParameters": {
      "vectorDB": "postgres-prod",
      "namespace": "kb",
      "index": "articles",
      "embeddingModelProvider": "openai",
      "embeddingModel": "text-embedding-3-small",
      "query": "${workflow.input.question}"
    }
  },
  {
    "name": "answer",
    "type": "LLM_CHAT_COMPLETE",
    "inputParameters": {
      "llmProvider": "anthropic",
      "model": "claude-sonnet-4-20250514",
      "messages": [
        {"role": "system", "message": "Answer based on: ${search.output.result}"},
        {"role": "user", "message": "${workflow.input.question}"}
      ]
    }
  }
]
```

Pinecone, pgvector, and MongoDB Atlas are supported natively. No LangChain, no custom retrieval workers, no framework dependencies.


## Multi-agent delegation — sub-workflows with lifecycle

A parent agent delegates to specialist agents. Each specialist is a sub-workflow with full lifecycle management:

```json
{
  "name": "parallel_research",
  "type": "DYNAMIC_FORK",
  "inputParameters": {
    "dynamicTasks": "${planner.output.result.research_tasks}",
    "dynamicTasksInput": "${planner.output.result.task_inputs}"
  },
  "dynamicForkTasksParam": "dynamicTasks",
  "dynamicForkTasksInputParamName": "dynamicTasksInput"
}
```

The LLM decides how many research agents to spawn and what each one investigates. Conductor creates the branches at runtime, runs them in parallel, and joins the results. If one branch fails, it retries independently without affecting the others. The parent agent sees the full execution tree — drill from parent to child to sub-child in the UI.


## Deterministic by construction

JSON workflow definitions cannot have side effects. There is no ambient state, no thread-local context, no hidden mutation. Given the same inputs, a Conductor workflow schedules the same tasks in the same order, every time. This is why [replay](../../architecture/durable-execution.md#replay--recovery) works unconditionally — restart a workflow from three months ago and it re-executes the same graph.

Code-based engines require developers to keep workflow functions deterministic: no system clocks, no random numbers, no uncontrolled I/O. Violating these constraints causes subtle replay bugs that are hard to detect and harder to debug. Conductor eliminates this entire class of bugs by construction.


## Observability — automatic, not opt-in

Every `LLM_CHAT_COMPLETE` task automatically records:

- The full prompt (every message in the conversation)
- The complete response
- Token usage (prompt tokens, completion tokens, total)
- Model and provider
- Latency
- Retry history (if any)

Every `CALL_MCP_TOOL` task records the method, arguments, response, and timing. Every `HUMAN` task records who approved, when, and with what payload. All of this is queryable via API and visible in the UI.

On other engines, you build this logging yourself. Every team does it differently, with different coverage and different gaps.


## The agent use case matrix

Every agentic pattern maps to a specific Conductor primitive:

| Use case | Conductor pattern |
|---|---|
| **Tool-calling agent** | `LLM_CHAT_COMPLETE` + `CALL_MCP_TOOL` |
| **Approval-gated actions** | `HUMAN` task + `SWITCH` for timeout |
| **Planner/executor loop** | `DO_WHILE` + `SET_VARIABLE` |
| **Multi-agent delegation** | `SUB_WORKFLOW` or `DYNAMIC_FORK` |
| **Long wait for external system** | `HUMAN` or `WAIT` task |
| **High fan-out research** | `DYNAMIC_FORK` + `JOIN` |
| **RAG pipeline** | `LLM_SEARCH_INDEX` + `LLM_CHAT_COMPLETE` |
| **Content generation** | `GENERATE_IMAGE` / `GENERATE_AUDIO` / `GENERATE_VIDEO` / `GENERATE_PDF` |
| **Agent that builds its own plan** | `LLM_CHAT_COMPLETE` + `START_WORKFLOW` with inline definition |
| **Deterministic post-processing** | `INLINE` (JavaScript) or `JSON_JQ_TRANSFORM` |


## Next steps

- **[Production Agent Architecture](production-agent-architecture.md)** — The canonical end-to-end agent pattern, fully wired.
- **[Failure Semantics for AI Agents](failure-semantics.md)** — The exact failure contract under every scenario.
- **[Build Your First AI Agent](first-ai-agent.md)** — From zero to a running agent in 5 minutes.
- **[Token Efficiency](token-efficiency.md)** — How durable execution saves tokens and reduces LLM costs.
