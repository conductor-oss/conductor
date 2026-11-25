---
description: Event handler model, expression scope, supported actions, and OSS runtime semantics.
---

# Consume and route events with event handlers

An event handler consumes one provider event, evaluates an optional condition, and dispatches one or more actions. Register it with the [Event Handlers API](../api/eventhandlers.md); only active handlers are subscribed for processing.

```json
--8<-- "docs/devguide/cookbook/examples/events/start-workflow-handler.json"
```

## Event identifier

The format is `provider:<provider-specific queue URI>`. Runtime parsing splits at the first colon. Valid registered provider keys are `conductor`, `kafka`, `sqs`, `nats`, `jsm`, `nats_stream`, `amqp_queue`, and `amqp_exchange` when their modules are enabled.

## Conditions and payload expressions

- `active` defaults to `false`.
- An absent condition is treated as true.
- Conditions evaluate against the payload root, for example `$.status == 'READY'`. If `evaluatorType` identifies a registered evaluator, Conductor uses it; otherwise it evaluates the condition with the default script evaluator.
- Action placeholders also resolve from the payload root, for example `${orderId}`.
- `expandInlineJSON: true` expands stringified JSON fields before expressions resolve.

## Action capability matrix

| Action | OSS Conductor | Orkes | Behavior |
|---|:---:|:---:|---|
| `start_workflow` | Yes | Yes | Starts the named workflow and adds Conductor event metadata to its input |
| `complete_task` | Yes | Yes | Completes an identified task |
| `fail_task` | Yes | Yes | Fails an identified task; can set `reasonForIncompletion` |
| `terminate_workflow` | No | Yes | Terminates the targeted workflow |
| `update_workflow_variables` | No | Yes | Updates variables on the targeted workflow |

For `complete_task` and `fail_task`, specify either `taskId`, or both `workflowId` and `taskRefName`. Those are exact task-targeting mechanisms; an OSS handler does not resolve a business correlation key to a waiting task. `terminate_workflow` and `update_workflow_variables` exist in the shared model but are not implemented by the OSS action processor.

## Concurrency and deduplication

Actions run concurrently and are not atomic. Each action is recorded separately using the broker message ID plus its action index. A stable broker message ID enables persisted duplicate detection after the event-execution record is stored, but downstream workflow starts, task updates, and external side effects still require idempotency.

For a condition that evaluates to false, Conductor records a skipped event execution and runs no actions. For a practical first-use walkthrough, see [Consume and route events](../../devguide/how-tos/consume-route-events.md); use this page as the action and expression reference.
