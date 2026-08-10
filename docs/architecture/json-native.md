---
description: Conductor stores workflow definitions as JSON — the canonical runtime format for this durable execution workflow engine. Create dynamic workflows at runtime, version and diff definitions, and expose any workflow as an API or MCP tool.
---

# JSON + Code Native Workflow Orchestration

Conductor stores workflow definitions as JSON. This is not a UI convenience or a simplified mode. JSON is the canonical runtime representation. Every workflow, whether created via SDK, API, UI, or file, is stored, versioned, and executed as a JSON document.


## What "JSON + code native" means mechanically

You can write a [workflow definition](../documentation/configuration/workflowdef/index.md) in JSON directly, or in code using an [SDK](../documentation/clientsdks/index.md). Both produce the same thing: a JSON document. When you define a workflow in code, the SDK converts it to that JSON and registers it with the server. The server only ever stores, versions, and executes the JSON. Everything below applies no matter which way the workflow was written.

1. **Storage.** The workflow definition is a JSON document [persisted in the data store](durable-execution.md#what-persists). The execution engine reads this document to schedule tasks.
2. **Versioning.** Each [version](../devguide/how-tos/Workflows/versioning-workflows.md) is a distinct JSON document. Multiple versions can run concurrently. Running executions use a snapshot taken at start time and are immutable against later changes.
3. **API parity.** The JSON you write in a file is the same JSON you send to the [API](../documentation/api/metadata.md), see in the UI, and get back from the SDK. There is no compiled intermediate form.
4. **Dynamic creation.** You can [construct a workflow definition as a JSON object at runtime](../devguide/cookbook/dynamic-workflows.md) and pass it directly to the [`StartWorkflowRequest` API](../documentation/api/startworkflow.md). Conductor executes it immediately without pre-registration.


## Why this matters for agents

### Agents produce structured output, and JSON is native

LLMs already produce structured output in the form of function calls and JSON responses. A Conductor workflow definition is the same kind of object. An LLM can therefore generate a workflow definition directly. Your application validates the plan and applies its [policy boundaries](../devguide/ai/agent-guardrails.md), and Conductor executes it.

### Runtime generation without compile/deploy

Most engines require code changes, a compile, and a deploy before a new workflow can run. Conductor does not. A planner agent generates a definition as JSON, your code sends it to [`POST /api/workflow`](../documentation/api/startworkflow.md) with the definition inline, and Conductor validates, persists, and executes it immediately. The result is as durable, observable, and retryable as any pre-registered workflow.

### Inspectability and auditability

Every execution records the definition snapshot it used, every task's input, output, status, and retry history, and the workflow's own input, output, and state transitions. You can query, diff, export, and [replay](durable-execution.md#replay-and-recovery) any execution. For agent workflows, that record shows what the agent planned, which tools it called, what the model returned, and [what a person approved](../devguide/ai/human-in-the-loop.md).

### Diffable versioning

Because definitions are JSON, they belong in source control. You can review changes in pull requests, diff two versions to see exactly what changed, and [roll back by re-registering an earlier version](../devguide/how-tos/Workflows/versioning-workflows.md). Multiple versions can run side by side, which makes canary rollouts straightforward. Running executions are never affected by any of this, because each keeps the snapshot taken at start.


## Dynamic workflows in detail

Conductor supports three levels of runtime flexibility.

### 1. Dynamic workflow definitions

Pass the complete workflow definition in the `StartWorkflowRequest`:

```json
{
  "name": "dynamic_agent_plan",
  "workflowDef": {
    "name": "dynamic_agent_plan",
    "tasks": [
      {
        "name": "search_web",
        "taskReferenceName": "search",
        "type": "HTTP",
        "inputParameters": {
          "http_request": {
            "uri": "https://api.search.com/query",
            "method": "POST",
            "body": { "q": "${workflow.input.query}" }
          }
        }
      },
      {
        "name": "summarize",
        "taskReferenceName": "summarize",
        "type": "SIMPLE"
      }
    ]
  },
  "input": {
    "query": "conductor workflow engine"
  }
}
```

No pre-registration needed. The definition is embedded in the execution and persisted.

### 2. Dynamic tasks

The [`DYNAMIC`](../documentation/configuration/workflowdef/operators/dynamic-task.md) task type resolves which task to execute at runtime:

```json
{
  "name": "run_tool",
  "taskReferenceName": "tool_call",
  "type": "DYNAMIC",
  "inputParameters": {
    "taskToExecute": "${plan.output.nextTool}"
  },
  "dynamicTaskNameParam": "taskToExecute"
}
```

The value of `taskToExecute` comes from the output of a previous task, such as an LLM choosing a tool. Conductor resolves and schedules that task at runtime.

### 3. Dynamic fork/join

The [`FORK_JOIN_DYNAMIC`](../documentation/configuration/workflowdef/operators/dynamic-fork-task.md) operator creates parallel branches at runtime:

```json
{
  "name": "parallel_tool_calls",
  "taskReferenceName": "fork",
  "type": "FORK_JOIN_DYNAMIC",
  "inputParameters": {
    "dynamicTasks": "${plan.output.parallelTasks}",
    "dynamicTasksInput": "${plan.output.taskInputs}"
  },
  "dynamicForkTasksParam": "dynamicTasks",
  "dynamicForkTasksInputParamName": "dynamicTasksInput"
}
```

The number of branches, their task types, and their inputs are all decided at runtime. Follow the fork with a [`JOIN`](../documentation/configuration/workflowdef/operators/join-task.md). If an agent produced the branch list, validate it and enforce a branch limit before executing the plan.

A [sub-workflow](../documentation/configuration/workflowdef/operators/sub-workflow-task.md) can be selected and parameterized at runtime in the same way. For a governed implementation of runtime-generated plans, with capability allowlists, bounded fan-out, and approval, see [Durable Adaptive Graphs](../devguide/ai/dynamic-workflows.md).


## Deterministic by construction

A JSON definition describes what runs and in what order. It contains no executable code, so it cannot open a database connection, write a file, or call an API on its own. Every side effect happens inside a [worker](../devguide/concepts/workers.md) or [system task](../documentation/configuration/workflowdef/systemtasks/index.md), where it is isolated, testable, and independently deployable. The definition itself is inert data.

Because the definition is inert, execution is deterministic. Given the same inputs, Conductor schedules the same tasks in the same order every time. There is no ambient state and no hidden mutation. That is why [replay](durable-execution.md#replay-and-recovery) works unconditionally: restart a workflow from months ago and it re-executes the same graph. Engines that embed orchestration in application code can only promise this by restricting what your code is allowed to do.

The same split keeps orchestration and implementation separate. Sequencing, branching, retries, and timeouts live in the definition. Implementation logic lives in workers, in any language. You can change a worker without touching the workflow, and change the workflow without redeploying workers.


## Exposing workflows as APIs and MCP tools

Any Conductor workflow is already an API endpoint:

```bash
# Start a workflow (async, returns execution ID)
conductor workflow start -w my_agent -i '{"query": "summarize this document"}'

# Get the result
conductor workflow status {executionId}
```

??? note "Using cURL"
    ```bash
    curl -X POST http://localhost:8080/api/workflow/my_agent \
      -H 'Content-Type: application/json' \
      -d '{"query": "summarize this document"}'

    curl http://localhost:8080/api/workflow/{executionId}
    ```

A workflow returns the structured output declared by its `outputParameters`, so services and agents can call it like any other API. A workflow can also be [registered as an MCP tool](../devguide/ai/mcp-guide.md), which lets LLMs and agent frameworks discover and invoke it with structured input and output.


## Next steps

- **[Durable Execution Semantics](durable-execution.md)** &mdash; What persists, what gets retried, failure matrix.
- **[Agents & AI](../devguide/ai/index.md)** &mdash; What agents are and how they run on Conductor.
- **[Run a Workflow from JSON](../quickstart/first-workflow.md)** &mdash; Register and run a JSON workflow with the CLI.
- **[Workflow Definition Reference](../documentation/configuration/workflowdef/index.md)** &mdash; Full JSON schema for workflow definitions.
- **[Dynamic Fork](../documentation/configuration/workflowdef/operators/dynamic-fork-task.md)** &mdash; Runtime-determined parallel execution.
