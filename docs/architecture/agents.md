---
description: How Conductor's durable code execution primitives power agentic workflow orchestration — LLM tasks, tool calls, human-in-the-loop approval, dynamic workflows, saga pattern compensation, and MCP tool exposure. The agentic workflow engine for AI agents.
---

# Why Conductor for Agents

Conductor is not an AI framework. It is a durable execution engine that solves the hard infrastructure problems that AI agents create: long-running processes, unreliable external calls, human-in-the-loop approval, structured output, and the need to survive failures across any of these steps.

This page explains the mechanics&mdash;what persists, what gets retried, and how the primitives compose into agent workflows.


## The problem agents create

An AI agent is a long-running process that:

1. **Calls an LLM** to decide what to do next.
2. **Calls tools** (APIs, databases, other services) to take action.
3. **Waits** for external events, human approval, or time-based delays.
4. **Loops** through plan/act/observe cycles until a goal is reached.
5. **Returns structured output** to the caller or another system.

Each of these steps can fail, take minutes to hours, or require intervention. Running this in a single process means any crash loses all progress. Running it in a queue means building your own state machine, retry logic, and observability. Conductor provides all of this out of the box.


## How Conductor's primitives map to agent patterns

| Agent pattern | Conductor primitive | What happens mechanically |
|---|---|---|
| **LLM call** | `SIMPLE` task with an LLM worker, or `HTTP` system task calling an LLM API | Worker polls, calls the model, returns structured output. Retried on failure. Timeout if the model is slow. |
| **Tool call** | `SIMPLE` task (custom worker) or `HTTP` task | Each tool is a task. Conductor schedules it, tracks state, retries on failure. |
| **Wait for human approval** | `HUMAN` task | Workflow pauses. Remains `IN_PROGRESS` in persistent storage. Resumes when the Task Update API is called with approval/rejection. Survives deploys. |
| **Wait for external event** | `WAIT` task (time-based) or `HUMAN` task with event handler | Durable pause. Timer or signal resolution survives server restarts. |
| **Wait for webhook** | `HUMAN` task + webhook endpoint | External system calls the Task Update API with payload. Workflow resumes with that payload as task output. |
| **Plan/act/observe loop** | `DO_WHILE` operator | Loop until a condition is met. Each iteration is a persisted step. The loop counter and state survive failures. |
| **Dynamic tool selection** | `DYNAMIC` task or `DYNAMIC_FORK` | The LLM output determines which task(s) to run next. Conductor resolves the task type at runtime. |
| **Sub-agent delegation** | `SUB_WORKFLOW` task | Spawn a child workflow. Parent waits for completion. Full observability into both. |
| **Structured output** | Workflow `outputParameters` | Map task outputs to a structured JSON response using Conductor's expression syntax. |
| **Expose as API** | Conductor REST API: `POST /api/workflow/{name}` | Any workflow is callable via HTTP. Start synchronously or asynchronously. Get structured output back. |
| **Expose as MCP tool** | MCP Gateway integration | Register a workflow as an MCP tool. LLMs and agents can invoke it directly and receive structured output. |


## What persists during an agent run

Every step of an agent workflow is durably persisted:

- The **workflow definition snapshot** (immutable for this execution).
- Each **LLM call**: input prompt, model response, token usage, latency.
- Each **tool call**: input, output, status, retry count.
- Each **wait state**: when it started, what it's waiting for, the resume payload when it arrives.
- Each **human decision**: who approved, when, with what data.
- The **loop state**: iteration count, intermediate results, exit condition evaluation.

If the server restarts mid-run, the agent resumes from the last completed step. No LLM calls are repeated unless a task explicitly failed and needs retry.


## JSON is the runtime, not a compromise

Conductor stores workflow definitions as JSON. For agents, this is an operational advantage:

**Inspectability.** Every agent run is a JSON document you can query, diff, and audit. You can see exactly what the LLM decided, what tools were called, and what the human approved.

**Runtime generation.** An LLM or planner can emit a workflow definition as JSON, and Conductor can execute it immediately via the [StartWorkflowRequest API](../documentation/api/startworkflow.md) without pre-registration. This enables dynamic agent planning where the workflow itself is generated at runtime.

**Versioning.** Workflow definitions are versioned. You can run multiple agent versions concurrently, A/B test different tool configurations, and roll back without affecting running executions.

**SDK/UI/API parity.** The same workflow can be defined via JSON file, SDK code, API call, or the Conductor UI. All paths produce the same stored JSON definition.


## Dynamic workflows: agents that build their own plans

Conductor supports [dynamic workflow execution](../devguide/architecture/technicaldetails.md#dynamic-workflow-executions) where the complete workflow definition is provided at start time, without pre-registration. This means:

1. An LLM generates a plan as a JSON workflow definition.
2. Your code passes that definition directly to the `StartWorkflowRequest`.
3. Conductor validates, persists, and executes it immediately.
4. Every step is durable, observable, and retryable.

Combined with `DYNAMIC` tasks (where the task type is resolved at runtime based on input) and `DYNAMIC_FORK` (where the number and type of parallel tasks is determined at runtime), Conductor supports agents that create and modify their own execution plans.


## End-to-end example: a long-running agent

Here is a workflow that demonstrates the key agent patterns:

```json
{
  "name": "research_agent",
  "description": "Research a topic, gather sources, get human approval, publish",
  "version": 1,
  "tasks": [
    {
      "name": "plan_research",
      "taskReferenceName": "plan",
      "type": "SIMPLE",
      "inputParameters": {
        "topic": "${workflow.input.topic}"
      }
    },
    {
      "name": "gather_sources",
      "taskReferenceName": "gather",
      "type": "DYNAMIC_FORK",
      "inputParameters": {
        "dynamicTasks": "${plan.output.searchTasks}",
        "dynamicTasksInput": "${plan.output.searchInputs}"
      },
      "dynamicForkTasksParam": "dynamicTasks",
      "dynamicForkTasksInputParamName": "dynamicTasksInput"
    },
    {
      "name": "join_sources",
      "taskReferenceName": "join",
      "type": "JOIN"
    },
    {
      "name": "synthesize",
      "taskReferenceName": "synthesize",
      "type": "SIMPLE",
      "inputParameters": {
        "sources": "${join.output}"
      }
    },
    {
      "name": "human_review",
      "taskReferenceName": "approval",
      "type": "HUMAN"
    },
    {
      "name": "publish",
      "taskReferenceName": "publish",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "${workflow.input.publishUrl}",
          "method": "POST",
          "body": {
            "content": "${synthesize.output.article}",
            "approved_by": "${approval.output.reviewer}"
          }
        }
      }
    }
  ],
  "outputParameters": {
    "article": "${synthesize.output.article}",
    "sources": "${gather.output}",
    "reviewer": "${approval.output.reviewer}"
  },
  "failureWorkflow": "research_agent_failure_handler",
  "schemaVersion": 2,
  "ownerEmail": "dev@example.com"
}
```

**What happens:**

1. `plan_research` &mdash; An LLM worker decides which sources to search and returns a list of dynamic tasks.
2. `gather_sources` &mdash; Conductor forks into parallel search tasks (the number and type determined at runtime by the plan).
3. `join_sources` &mdash; Waits for all searches to complete. All results are collected.
4. `synthesize` &mdash; An LLM worker produces the final article from the gathered sources.
5. `human_review` &mdash; Workflow pauses. A reviewer approves or rejects via the Task Update API. This pause survives server restarts and deploys.
6. `publish` &mdash; On approval, the article is published via HTTP.

Every step is persisted, retryable, and visible in the Conductor UI. If the LLM call in step 4 fails, it retries. If the reviewer takes 3 days, the workflow waits. If the server restarts during the wait, execution resumes exactly where it left off.


## Next steps

- **[Durable Execution Semantics](durable-execution.md)** &mdash; Failure matrix, state transitions, and exactly what persists.
- **[Quickstart](../quickstart/index.md)** &mdash; Get Conductor running and execute your first workflow.
- **[WAIT task](../documentation/configuration/workflowdef/systemtasks/wait-task.md)** &mdash; Time-based and signal-based pauses.
- **[HUMAN task](../documentation/configuration/workflowdef/systemtasks/human-task.md)** &mdash; Human-in-the-loop approval.
- **[Dynamic Fork](../documentation/configuration/workflowdef/operators/dynamic-fork-task.md)** &mdash; Runtime-determined parallel execution.
- **[Client SDKs](../documentation/clientsdks/index.md)** &mdash; Java, Python, Go, C#, JavaScript, Clojure.
