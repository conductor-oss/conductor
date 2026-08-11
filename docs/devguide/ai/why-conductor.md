---
description: "Why Conductor for AI agents — native LLM and MCP tasks, durable human approval, governed runtime paths, and operational recovery."
---

# Why Conductor for agents

Agents fail in production for ordinary reasons. A process crashes mid-loop, a tool call fails once and the whole run is lost, and afterwards nobody can see which decision led to which action. Conductor addresses this by running every step of an agent, each model call and each tool call, as a durable workflow task. A failed step is retried, an interrupted run resumes from its last completed step, and the full history of the run is recorded.

This page shows what that looks like in practice using the native tasks. The same properties apply when you bring a framework-authored agent instead; see [Conductor Agents](conductor-agents.md).


## Call an LLM as a workflow task

An LLM call is a system task. The provider, model, and messages are ordinary task input:

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

Conductor records the task input, result, token usage when returned by the provider, and task outcome alongside the workflow execution. Select a provider and model per task; see [LLM orchestration](llm-orchestration.md) for the maintained capability matrix.


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

The agent discovers tools at runtime, the LLM picks an approved method, and Conductor records the call, task outcome, and result. Combine MCP with [guardrails](agent-guardrails.md) to constrain capability selection and require approval before consequential actions.


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

The workflow pauses until the approval is completed or rejected. The approval payload becomes durable task output, and the execution can be inspected or managed while it waits.


## Agent loops — checkpointed per iteration

An autonomous agent loops: plan, act, observe, repeat. On Conductor, each iteration is a durable checkpoint:

```json
{
  "name": "agent_loop",
  "taskReferenceName": "loop",
  "type": "DO_WHILE",
  "loopCondition": "if ($.think['result']['route'] == 'done' || $.loop['iteration'] >= 20) { false; } else { true; }",
  "loopOver": [
    {
      "name": "think",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {"role": "system", "message": "Goal: ${workflow.input.goal}. Respond with JSON only: {\"route\": \"call_tool\", \"action\": \"tool_name\", \"arguments\": {}} or {\"route\": \"done\", \"answer\": \"final answer\"}."}
        ],
        "jsonOutput": true
      }
    },
    {
      "name": "act_or_finish",
      "taskReferenceName": "act_or_finish",
      "type": "SWITCH",
      "evaluatorType": "value-param",
      "expression": "route",
      "inputParameters": {
        "route": "${think.output.result.route}"
      },
      "decisionCases": {
        "call_tool": [
          {
            "name": "act",
            "taskReferenceName": "act",
            "type": "CALL_MCP_TOOL",
            "inputParameters": {
              "mcpServer": "${workflow.input.mcpServerUrl}",
              "method": "${think.output.result.action}",
              "arguments": "${think.output.result.arguments}"
            }
          }
        ],
        "done": []
      },
      "defaultCase": []
    }
  ]
}
```

Completed task outputs remain in the execution record if a later task fails. The loop condition enforces an iteration cap; tools remain responsible for idempotency because task delivery is at least once.


## Dynamic workflows — LLMs generate execution plans

An LLM or service can generate a complete workflow definition as JSON and submit it as a runtime plan:

```json
{
  "name": "execute_agent_plan",
  "type": "START_WORKFLOW",
  "inputParameters": {
    "startWorkflow": {
          "workflowDef": "${planner_llm.output.result}",
      "input": "${workflow.input.taskInput}"
    }
  }
}
```

The LLM's output is data, not an unrestricted mutation of a running execution. Validate the definition and its allowed capabilities before starting it. The resulting workflow uses the same persisted state, retry policy, and execution controls as a registered definition.

Combined with `DYNAMIC` tasks (resolve an approved task at runtime) and `FORK_JOIN_DYNAMIC` (create validated, bounded parallel branches at runtime), Conductor makes runtime plans inspectable and governable as data.

Use this pattern when a runtime plan needs its own execution boundary, audit trail, version, and lifecycle.


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

Pinecone, pgvector, and MongoDB Atlas are supported through the vector workflow tasks. The same pattern can compose with an existing agent framework when retrieval is only one part of the graph.


## Multi-agent delegation — sub-workflows with lifecycle

A parent agent delegates to specialist agents. Each specialist is a sub-workflow with full lifecycle management:

```json
{
  "name": "parallel_research",
  "type": "FORK_JOIN_DYNAMIC",
  "inputParameters": {
    "dynamicTasks": "${planner.output.result.research_tasks}",
    "dynamicTasksInput": "${planner.output.result.task_inputs}"
  },
  "dynamicForkTasksParam": "dynamicTasks",
  "dynamicForkTasksInputParamName": "dynamicTasksInput"
}
```

The LLM decides how many research agents to spawn and what each one investigates. Conductor creates the branches at runtime, runs them in parallel, and joins the results. If one branch fails, it retries independently without affecting the others. The parent agent sees the full execution tree — drill from parent to child to sub-child in the UI.


## Long-running workflows — evolve without breaking

An agent workflow can run for days. Keep definition changes explicit and versioned so the execution behavior remains understandable while the system evolves.

```json
{
  "name": "agent_workflow",
  "version": 2,
  "tasks": [
    {"name": "plan", "type": "LLM_CHAT_COMPLETE", "...": "..."},
    {"name": "validate", "type": "INLINE", "...": "..."},
    {"name": "execute", "type": "CALL_MCP_TOOL", "...": "..."}
  ]
}
```

Running executions retain the definition version they started with; new executions can be directed to a new version. If a new definition must apply to work already started, [restart the execution](../../architecture/durable-execution.md#replay-and-recovery) deliberately and evaluate its side effects.


## Failure is an explicit part of the graph

Conductor records task state and exposes retry, timeout, failure-workflow, pause, resume, and termination controls. Build the failure policy into the graph instead of treating it as an afterthought.

The guarantees:

- **At-least-once task delivery** — Every task is persisted to durable storage before execution. If a worker crashes, the task is automatically requeued and delivered to another worker. Tasks do not disappear.
- **Sweeper recovery** — A background sweeper service continuously scans for stalled tasks. If a task is `IN_PROGRESS` but its worker has gone silent (no heartbeat, past `responseTimeoutSeconds`), the sweeper requeues it. If the Conductor server itself restarts, the sweeper recovers all in-flight work on startup.
- **Configurable retry policies** — Every task has retry count, delay, and backoff strategy. Retries are managed by the engine, not your code. Exponential backoff, fixed delay, and linear backoff are built in.
- **Failure workflows** — When a workflow fails after exhausting retries, a `failureWorkflow` runs automatically. This is where you put compensation logic: undo API calls, release resources, send alerts. The failure workflow has the full context of what failed and why.
- **Terminal handling** — Use terminal states, workflow timeouts, and alerts to make the outcome actionable for operators.

```json
{
  "name": "critical_agent",
  "failureWorkflow": "agent_failure_handler",
  "tasks": [
    {
      "name": "risky_action",
      "type": "CALL_MCP_TOOL",
      "retryCount": 5,
      "retryLogic": "EXPONENTIAL_BACKOFF",
      "retryDelaySeconds": 10,
      "responseTimeoutSeconds": 30,
      "timeoutPolicy": "RETRY"
    }
  ]
}
```

Configure retry and compensation with the idempotency behavior of each external system in mind. The workflow records the outcome and failure path for operators to inspect.


## Explicit orchestration, ordinary workers

The JSON definition makes graph structure, task inputs, and workflow policy visible. Put business logic and side effects in built-in tasks or workers, then design retry and compensation according to the external system's idempotency contract. This separation makes the execution path easier to inspect, version, and generate as validated data.


## Observability — automatic, not opt-in

Every `LLM_CHAT_COMPLETE` task automatically records:

- The full prompt (every message in the conversation)
- The complete response
- Token usage (prompt tokens, completion tokens, total)
- Model and provider
- Latency
- Retry history (if any)

Every `CALL_MCP_TOOL` task records the method, arguments, response, and timing. Every `HUMAN` task records who approved, when, and with what payload. All of this is queryable via API and visible in the UI.

Use the execution view and APIs to inspect these task-level records alongside the graph path and retry history.


## The agent use case matrix

Every agentic pattern maps to a specific Conductor primitive:

| Use case | Conductor pattern |
|---|---|
| **Tool-calling agent** | `LLM_CHAT_COMPLETE` + `CALL_MCP_TOOL` |
| **Approval-gated actions** | `HUMAN` task + `SWITCH` for timeout |
| **Planner/executor loop** | `DO_WHILE` + `SET_VARIABLE` |
| **Multi-agent delegation** | `SUB_WORKFLOW` or `FORK_JOIN_DYNAMIC` |
| **Long wait for external system** | `HUMAN` or `WAIT` task |
| **High fan-out research** | `FORK_JOIN_DYNAMIC` + `JOIN` |
| **RAG pipeline** | `LLM_SEARCH_INDEX` + `LLM_CHAT_COMPLETE` |
| **Content generation** | `GENERATE_IMAGE` / `GENERATE_AUDIO` / `GENERATE_VIDEO` / `GENERATE_PDF` |
| **Agent that builds its own plan** | `LLM_CHAT_COMPLETE` + `START_WORKFLOW` with inline definition |
| **Deterministic post-processing** | `INLINE` (JavaScript) or `JSON_JQ_TRANSFORM` |


## Next steps

- **[Conductor Agents](conductor-agents.md)** — Author Conductor Agents or bring existing framework agents into durable Conductor graphs.
- **[Framework Agent Recipes](agent-framework-recipes.md)** — Supported SDK paths for OpenAI Agents, Google ADK, LangChain, LangGraph, Vercel AI SDK, and Conductor Agents.
- **[Production Agent Architecture](production-agent-architecture.md)** — The canonical end-to-end agent pattern, fully wired.
- **[Failure Semantics for AI Agents](failure-semantics.md)** — The exact failure contract under every scenario.
- **[Build Your First Agentic Workflow Graph](first-ai-agent.md)** — Compose an SDK-authored agent with durable workflow tasks.
- **[Token Efficiency](token-efficiency.md)** — How durable execution saves tokens and reduces LLM costs.
