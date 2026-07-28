---
description: Signal the first blocked WAIT in a workflow or running sub-workflow; use task-update APIs for exact task targeting.
---

# Sending signals to workflows

<section class="concept-hero concept-hero--event-bus" aria-labelledby="signals-title">
  <div class="concept-hero__content">
    <p class="concept-hero__eyebrow">Advance existing work</p>
    <h2 id="signals-title">Signal the workflow that is already waiting</h2>
    <p>Signals resolve the first non-terminal <code>WAIT</code> in a workflow or running sub-workflow. They do not start a new execution, target arbitrary task references, or resolve <code>HUMAN</code> tasks.</p>
    <p><a href="../../documentation/api/task.html">Task API reference</a> · <a href="../how-tos/consume-route-events.html">Route a broker event</a></p>
  </div>
  <svg class="concept-hero__graphic event-hero__graphic" viewBox="0 0 440 190" role="img" aria-labelledby="signal-svg-title signal-svg-desc" xmlns="http://www.w3.org/2000/svg">
    <title id="signal-svg-title">Workflow signal flow</title>
    <desc id="signal-svg-desc">A caller sends an output payload to a signal endpoint. It finds the first blocked Wait, including in a running sub-workflow, then the workflow continues.</desc>
    <defs><marker id="signal-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="currentColor"/></marker></defs>
    <rect x="14" y="68" width="96" height="54" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="62" y="91" text-anchor="middle" class="concept-hero__label">Caller</text><text x="62" y="108" text-anchor="middle" class="concept-hero__detail">decision output</text>
    <path d="M110 95 H145" class="concept-hero__line" marker-end="url(#signal-arrow)"/>
    <rect x="153" y="68" width="100" height="54" rx="10" class="concept-hero__node concept-hero__node--accent"/><text x="203" y="91" text-anchor="middle" class="concept-hero__label">Signal API</text><text x="203" y="108" text-anchor="middle" class="concept-hero__detail">find first WAIT</text>
    <path d="M253 95 H288" class="concept-hero__line" marker-end="url(#signal-arrow)"/>
    <rect x="296" y="51" width="130" height="88" rx="10" class="concept-hero__node event-hero__node--action"/><text x="361" y="77" text-anchor="middle" class="concept-hero__label">Blocked WAIT</text><text x="361" y="94" text-anchor="middle" class="concept-hero__detail">workflow or running</text><text x="361" y="111" text-anchor="middle" class="concept-hero__detail">sub-workflow</text><text x="361" y="128" text-anchor="middle" class="concept-hero__detail">then continue</text>
  </svg>
</section>

## Define a workflow that waits for a signal

This workflow records an approval request, then waits until another system supplies the decision.

```json
{
  "name": "order_approval",
  "description": "Wait for an external order approval signal",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["orderId"],
  "tasks": [
    {
      "name": "wait_for_approval",
      "taskReferenceName": "approval",
      "type": "WAIT"
    }
  ],
  "outputParameters": {
    "orderId": "${workflow.input.orderId}",
    "approval": "${approval.output}"
  }
}
```

Register it with the workflow metadata API:

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @order_approval.json
```

## Start and wait for the blocking task

The synchronous execution endpoint starts the workflow and waits for a terminal state or a blocked `WAIT` task. `waitForSeconds` defaults to `10`; use `waitUntilTaskRef` when a terminal task reference should also end the wait.

```shell
curl -X POST 'http://localhost:8080/api/workflow/execute/order_approval/1?requestId=approval-demo-42&waitForSeconds=30&returnStrategy=BLOCKING_TASK_INPUT' \
  -H 'Content-Type: application/json' \
  -d '{"input":{"orderId":"order-42"}}'
```

`returnStrategy` controls the shape of the response:

| Value | Returns |
|---|---|
| `TARGET_WORKFLOW` | The workflow requested by ID. This is the default. |
| `BLOCKING_WORKFLOW` | The workflow that contains the current blocker; it can be a sub-workflow. |
| `BLOCKING_TASK` | The current blocking task. |
| `BLOCKING_TASK_INPUT` | The input of the current blocking task. |

## Signal the wait asynchronously

Use the asynchronous signal endpoint when the caller only needs to submit the decision. It completes the currently blocked `WAIT` task and returns immediately.

```shell
curl -X POST 'http://localhost:8080/api/tasks/<workflow-id>/COMPLETED/signal' \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"approvedBy":"manager@example.com","reason":"Within policy"}'
```

The signal target is the first non-terminal `WAIT` task in the workflow, including a currently running sub-workflow. It does not target `HUMAN` tasks or an arbitrary task reference. A signal does not name a task reference; use this endpoint only when that current blocking-wait behavior is what you want. When exact task targeting is required, use the task-update endpoint (`POST /api/tasks/{workflowId}/{taskRefName}/{status}`) instead.

## Signal and wait for the next workflow state

Use the synchronous variant when the caller needs the resulting workflow state in the same response. It accepts the same `returnStrategy` values and waits up to `timeoutMillis` (default: `5000`).

```shell
curl -X POST 'http://localhost:8080/api/tasks/<workflow-id>/COMPLETED/signal/sync?returnStrategy=TARGET_WORKFLOW&timeoutMillis=5000' \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"approvedBy":"manager@example.com"}'
```

If the workflow reaches another `WAIT` task, the response represents that next blocking state. If it completes first, the response represents the completed workflow. A synchronous signal returns `404` when there is no blocked task to signal; the asynchronous route returns after submitting the signal and does not provide that state in its response.

## Reject or fail the wait

Choose the task status from the URL to record a different decision. For example, signal `FAILED` when an approval is rejected and you want the workflow's failure path to run:

```shell
curl -X POST 'http://localhost:8080/api/tasks/<workflow-id>/FAILED/signal' \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Order exceeds the approval limit"}'
```

The payload you send is stored as the `WAIT` task's output. Downstream tasks can reference it with expressions such as `${approval.output.approved}` or `${approval.output.reason}`.

## Next steps

<div class="event-next-steps">
  <a href="../how-tos/consume-route-events.html">Route a broker event to a task →</a>
  <a href="../how-tos/incoming-webhooks.html">Receive a verified webhook →</a>
  <a href="../how-tos/event-bus.html">Event-driven overview →</a>
  <a href="wait-and-timers.html">Wait and timer patterns →</a>
</div>
