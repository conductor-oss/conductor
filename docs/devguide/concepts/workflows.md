---
description: "Understand workflows in Conductor — JSON workflow definition, dynamic workflows, distributed workflow execution, and long-running async workflows that power durable code execution across distributed services."
---

# Workflows

<section class="concept-hero concept-hero--workflows">
  <svg class="concept-hero__graphic" viewBox="0 0 440 190" role="img" aria-label="A workflow definition runs tasks through durable checkpoints to a completed outcome">
    <defs><marker id="workflow-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="currentColor" /></marker></defs>
    <rect x="18" y="65" width="112" height="54" rx="10" class="concept-hero__node" />
    <text x="74" y="89" text-anchor="middle" class="concept-hero__label">Definition</text>
    <text x="74" y="107" text-anchor="middle" class="concept-hero__detail">JSON or code</text>
    <path d="M130 92 H176" class="concept-hero__line" marker-end="url(#workflow-arrow)" />
    <rect x="184" y="45" width="116" height="94" rx="10" class="concept-hero__node concept-hero__node--accent" />
    <text x="242" y="74" text-anchor="middle" class="concept-hero__label">Tasks</text>
    <path d="M209 91 H275 M209 112 H275" class="concept-hero__line concept-hero__line--inside" />
    <text x="242" y="128" text-anchor="middle" class="concept-hero__detail">durable state</text>
    <path d="M300 92 H346" class="concept-hero__line" marker-end="url(#workflow-arrow)" />
    <circle cx="386" cy="92" r="32" class="concept-hero__outcome" />
    <path d="M371 93 l10 10 20 -22" class="concept-hero__check" />
    <text x="386" y="145" text-anchor="middle" class="concept-hero__detail">Outcome</text>
  </svg>
</section>

A workflow is a sequence of tasks with a defined order and execution. Each workflow encapsulates a specific process, such as:

- Classifying documents
- Ordering from a self-checkout service
- Upgrading cloud infrastructure
- Transcoding videos
- Approving expenses

In Conductor, workflows can be defined and then executed. Learn more about the two distinct but related concepts, **workflow definition** and **workflow execution**, below.


## What makes Conductor workflows different

Conductor workflows stand apart from traditional orchestration approaches in several key ways:

- **Durable execution** — Workflows survive process failures, restarts, and infrastructure outages. Conductor persists state at every step, so a long-running workflow or async workflow picks up exactly where it left off — even after days or weeks.
- **JSON-native definitions** — Every workflow is a JSON workflow definition you can store in version control, diff across releases, and generate programmatically. No compiled DSL or proprietary format required.
- **Dynamic workflows** — Workflows can be created and modified at runtime as code-first or JSON definitions, enabling use cases where the task graph is not known ahead of time (for example, when the number of parallel branches depends on an API response).
- **Versioned** — Each workflow definition carries an explicit version number so you can roll out changes incrementally and run multiple versions side by side.
- **Language-agnostic** — Workers that execute tasks can be written in any language — Java, Python, Go, JavaScript, C#, or Clojure — and deployed anywhere. The workflow definition itself is decoupled from implementation.


## Workflow definition

The workflow definition describes the flow and behavior of your business logic. Think of it as a blueprint specifying how it should execute at runtime until it reaches a terminal state. The workflow definition includes:

- The workflow's input/output keys.
- A collection of [task configurations](tasks.md#task-configuration) that specify the task conditions, sequence, and data flow until the workflow is completed.
- The workflow's runtime behavior, such as the timeout policy and compensation flow.


### Example JSON workflow definition

Below is a realistic three-task workflow that fetches data from an API, transforms it with an inline script, and then delegates the result to a worker task for further processing.

```json
{
  "name": "process_order",
  "description": "Fetch order details, enrich them, and hand off to fulfillment",
  "version": 1,
  "schemaVersion": 2,
  "ownerEmail": "team-platform@example.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 3600,
  "restartable": true,
  "failureWorkflow": "handle_order_failure",
  "inputParameters": ["orderId"],
  "outputParameters": {
    "enrichedOrder": "${enrich_order.output.result}",
    "fulfillmentStatus": "${fulfill_order.output.status}"
  },
  "tasks": [
    {
      "name": "fetch_order",
      "taskReferenceName": "fetch_order",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://api.example.com/orders/${workflow.input.orderId}",
          "method": "GET",
          "connectionTimeOut": 5000,
          "readTimeOut": 5000
        }
      }
    },
    {
      "name": "enrich_order",
      "taskReferenceName": "enrich_order",
      "type": "INLINE",
      "inputParameters": {
        "order": "${fetch_order.output.response.body}",
        "evaluatorType": "graaljs",
        "expression": "(function() { var o = $.order; o.region = o.country === 'US' ? 'domestic' : 'international'; return o; })()"
      }
    },
    {
      "name": "fulfill_order",
      "taskReferenceName": "fulfill_order",
      "type": "SIMPLE",
      "inputParameters": {
        "enrichedOrder": "${enrich_order.output.result}"
      }
    }
  ]
}
```


### Workflow definition parameters

| Parameter | Type | Description |
|---|---|---|
| **name** | `string` | A unique name identifying the workflow. Used when starting executions. |
| **version** | `integer` | The version of the workflow definition. Allows multiple versions to coexist. |
| **tasks** | `array[object]` | An ordered list of [task configurations](tasks.md#task-configuration) that define the workflow's execution graph. |
| **inputParameters** | `array[string]` | List of input keys the workflow expects when triggered. |
| **outputParameters** | `object` | Mapping of output keys to expressions that extract values from task outputs. |
| **failureWorkflow** | `string` | Name of a workflow to trigger when this workflow transitions to FAILED. Useful for compensation or alerting. |
| **timeoutPolicy** | `string` | Policy to apply when the workflow exceeds `timeoutSeconds`. Supported values: `TIME_OUT_WF` (fail the workflow) or `ALERT_ONLY` (mark timed out but keep running). |
| **timeoutSeconds** | `integer` | Maximum time (in seconds) the workflow is allowed to run before the timeout policy is applied. Set to `0` for no timeout. |
| **restartable** | `boolean` | Whether the workflow can be restarted after completion or failure. Defaults to `true`. |
| **ownerEmail** | `string` | Email address of the workflow owner. Used for notifications and audit tracking. |
| **schemaVersion** | `integer` | Schema version of the workflow definition format. Current version is `2`. |


## Workflow execution

A workflow execution is the execution instance of a workflow definition.

Whenever a workflow definition is invoked with a given input, a new workflow execution with a unique ID is created. The workflow is governed by a defined state (like RUNNING or COMPLETED), which makes it intuitive to track the workflow.


### Workflow execution states

Each workflow execution transitions through a set of well-defined states:

| State | Description |
|---|---|
| **RUNNING** | The workflow is actively executing tasks. |
| **COMPLETED** | All tasks finished successfully and the workflow reached its terminal state. |
| **FAILED** | One or more tasks failed and the workflow could not recover. If a `failureWorkflow` is configured, it will be triggered. |
| **TIMED_OUT** | The workflow exceeded its configured `timeoutSeconds` and the `timeoutPolicy` was set to `TIME_OUT_WF`. |
| **TERMINATED** | The workflow was explicitly stopped by an API call or system action. |
| **PAUSED** | The workflow has been paused and will not schedule new tasks until resumed. |

The following diagram illustrates how a workflow transitions between states:

```mermaid
stateDiagram-v2
    [*] --> RUNNING
    RUNNING --> COMPLETED : all tasks succeed
    RUNNING --> FAILED : task failure (unrecoverable)
    RUNNING --> TIMED_OUT : timeout exceeded
    RUNNING --> TERMINATED : API termination
    RUNNING --> PAUSED : pause requested
    PAUSED --> RUNNING : resume requested
    PAUSED --> TERMINATED : API termination
    FAILED --> RUNNING : retry
    TIMED_OUT --> RUNNING : retry
    TERMINATED --> RUNNING : restart (if restartable)
    COMPLETED --> [*]
    FAILED --> [*]
    TIMED_OUT --> [*]
    TERMINATED --> [*]
```


## Next steps

- [Tasks](tasks.md) — Learn about the building blocks that make up a workflow, including system tasks, worker tasks, and operators.
- [Workers](workers.md) — Understand how to implement task workers in any programming language.
- [Handling errors](../how-tos/Workflows/handling-errors.md) — Configure retries, failure workflows, and compensation strategies.
