---
description: Conductor stores workflow definitions as JSON — the canonical runtime format for this durable execution workflow engine. Create dynamic workflows at runtime, version and diff definitions, and expose any workflow as an API or MCP tool.
---

# JSON + Code Native Workflow Orchestration

Conductor stores workflow definitions as JSON. This is not a UI convenience or a simplified mode&mdash;JSON is the canonical runtime representation. Every workflow, whether created via SDK, API, UI, or file, is stored, versioned, and executed as a JSON document.

For agent orchestration and dynamic workloads, this is a structural advantage.


## What "JSON + code native" means mechanically

1. **Storage.** The workflow definition is a JSON document persisted in the data store. The execution engine reads this document to schedule tasks.
2. **Versioning.** Each version is a distinct JSON document. Multiple versions can run concurrently. Running executions use a snapshot taken at start time and are immutable against later changes.
3. **API parity.** The JSON you write in a file is the same JSON you send to the API, see in the UI, and get back from the SDK. There is no compiled intermediate form.
4. **Dynamic creation.** You can construct a workflow definition as a JSON object at runtime and pass it directly to the `StartWorkflowRequest` API. Conductor executes it immediately without pre-registration.


## Why this matters for agents

### Agents produce structured output&mdash;JSON is native

LLMs already communicate in structured formats: function calls, tool-use schemas, JSON mode responses. Conductor's JSON workflow definitions are in the same format that agents already produce. An LLM can generate a workflow definition directly, and Conductor can execute it.

### Runtime generation without compile/deploy

Traditional workflow engines require you to define workflows in code, compile, and deploy before they can run. Conductor's JSON + code native approach means:

- A planner agent can generate a new workflow definition as JSON.
- Your code sends that JSON to `POST /api/workflow` with the definition inline.
- Conductor validates, persists, and executes it immediately.
- The workflow is fully durable, observable, and retryable&mdash;identical to any pre-registered workflow.

This enables patterns like:

- **LLM-generated plans** where the agent decides the steps at runtime.
- **Template instantiation** where a base workflow is modified per-request.
- **A/B testing** where different workflow versions are created and run dynamically.

### Inspectability and auditability

Every workflow execution is a JSON document that records:

- The definition that was used (immutable snapshot).
- Every task's input, output, status, timestamps, and retry history.
- The workflow's input, output, variables, and state transitions.

You can query, diff, export, and replay any execution. For AI agent workflows, this means you can audit exactly what the agent planned, what tools it called, what the LLM returned, and what the human approved.

### Diffable versioning

Because definitions are JSON, you can:

- Store them in Git and review changes in pull requests.
- Diff two versions to see exactly what changed.
- Roll back by re-registering a previous version.
- Run canary deployments by routing traffic between versions.

Running executions are never affected by definition changes&mdash;they use the snapshot taken at start time.


## Dynamic workflows in detail

Conductor supports three levels of dynamism:

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

The `DYNAMIC` task type resolves which task to execute at runtime based on input:

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

The value of `taskToExecute` is determined by the output of a previous task (e.g., an LLM deciding which tool to call). Conductor resolves and schedules the appropriate task type at runtime.

### 3. Dynamic fork/join

The `DYNAMIC_FORK` operator creates parallel branches at runtime:

```json
{
  "name": "parallel_tool_calls",
  "taskReferenceName": "fork",
  "type": "DYNAMIC_FORK",
  "inputParameters": {
    "dynamicTasks": "${plan.output.parallelTasks}",
    "dynamicTasksInput": "${plan.output.taskInputs}"
  },
  "dynamicForkTasksParam": "dynamicTasks",
  "dynamicForkTasksInputParamName": "dynamicTasksInput"
}
```

The number of branches, their task types, and their inputs are all determined at runtime. This enables an agent to decide how many tools to call in parallel based on its plan.


## Deterministic by construction

JSON workflow definitions are pure orchestration — they describe *what* runs and in *what order*, but contain no executable code. This separation is not a limitation; it is a structural guarantee.

**No side effects in the workflow definition.** A JSON definition cannot open a database connection, write to a file, or call an API outside of a declared task. Every side effect lives in a worker or system task — isolated, testable, and independently deployable. The workflow definition itself is inert data.

**Every run is deterministic.** Given the same inputs, a Conductor workflow will schedule the same tasks in the same order, every time. There is no ambient state, no thread-local context, no hidden mutation. This is why [replay](durable-execution.md#replay-and-recovery) works unconditionally — restart a workflow from three months ago and it re-executes the same graph. Code-based workflow engines that embed orchestration logic alongside business logic cannot make this guarantee without imposing significant constraints on what your code is allowed to do (no random numbers, no system clocks, no uncontrolled I/O).

**Clean separation of concerns.** Orchestration logic (sequencing, branching, retries, timeouts) is defined declaratively in JSON. Implementation logic (calling APIs, transforming data, running ML models) lives in workers written in any language. Each can be tested, deployed, and versioned independently. Change a worker without touching the workflow. Change the workflow without redeploying workers.

### JSON is more dynamic than code

The common assumption is that code-based workflows are more flexible. The opposite is true. Code-based definitions are static at deploy time — to change the workflow, you redeploy.

Conductor's JSON definitions can be:

- **Generated at runtime** — an LLM or planner service produces a workflow definition as JSON and Conductor executes it immediately, no compilation or deployment step.
- **Modified per-execution** — pass a complete `workflowDef` in the start request to customize any execution on the fly.
- **Dynamically branched** — [DYNAMIC tasks](../devguide/configuration/workflowdef/operators/dynamic-task.md) resolve which task to execute based on runtime output. [DYNAMIC_FORK](../devguide/configuration/workflowdef/operators/dynamic-fork-task.md) creates an arbitrary number of parallel branches determined by a previous task's output. [Sub-workflows](../devguide/configuration/workflowdef/operators/sub-workflow-task.md) can be selected and parameterized dynamically.

Combined, these primitives make Conductor the most dynamic workflow engine available — not despite using JSON, but because of it. A JSON definition is data, and data is easy to generate, transform, and compose programmatically. Code is not.

### AI-native by design

LLMs produce structured output. JSON *is* structured output. There is no impedance mismatch — an agent can generate a Conductor workflow definition directly, and Conductor executes it with full durability, observability, and replayability. No code generation, no compilation, no deployment pipeline. The workflow evolves as fast as the agent can think.

Code-based workflow engines require generated code to be compiled, tested, and deployed before it runs — a friction that fundamentally limits how dynamically an AI system can operate.


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

Workflows return structured JSON output defined by `outputParameters` in the definition. This makes them directly consumable by other agents, services, or MCP-compatible tools.

For MCP integration, a Conductor workflow can be registered as an MCP tool, allowing LLMs and agent frameworks to discover and invoke it directly with structured input/output.


## Next steps

- **[Durable Execution Semantics](durable-execution.md)** &mdash; What persists, what gets retried, failure matrix.
- **[Why Conductor for Agents](../devguide/ai/index.md)** &mdash; How Conductor's primitives map to agent patterns.
- **[Quickstart](../quickstart/index.md)** &mdash; Get running in 5 minutes.
- **[Workflow Definition Reference](../devguide/configuration/workflowdef/index.md)** &mdash; Full JSON schema for workflow definitions.
- **[Dynamic Fork](../devguide/configuration/workflowdef/operators/dynamic-fork-task.md)** &mdash; Runtime-determined parallel execution.
