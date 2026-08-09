---
description: Route broker messages through active event handlers to start workflows or exactly complete or fail identified tasks.
---

# Consume and route events

<section class="concept-hero concept-hero--event-bus" aria-labelledby="consume-events-title">
  <div class="concept-hero__content">
    <p class="concept-hero__eyebrow">Inbound broker events</p>
    <h2 id="consume-events-title">Evaluate a message, then take a durable action</h2>
    <p>In OSS, register an active handler with the Event Handlers API. It evaluates the delivered payload and can start a workflow or exactly complete or fail a task. Orkes can separately use configured broker integrations.</p>
    <p><a href="../../documentation/configuration/eventhandlers.html">Event handler reference</a> · <a href="publish-events.html">Publish events</a></p>
  </div>
  <svg class="concept-hero__graphic event-hero__graphic" viewBox="0 0 440 190" role="img" aria-labelledby="consume-svg-title consume-svg-desc" xmlns="http://www.w3.org/2000/svg">
    <title id="consume-svg-title">Event handler routing flow</title>
    <desc id="consume-svg-desc">A broker message reaches an event handler. Its condition and evaluator lead to either a workflow start or an exact task completion or failure.</desc>
    <defs><marker id="consume-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="currentColor"/></marker></defs>
    <rect x="14" y="68" width="101" height="54" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="64" y="91" text-anchor="middle" class="concept-hero__label">Broker event</text><text x="64" y="108" text-anchor="middle" class="concept-hero__detail">message + ID</text>
    <path d="M115 95 H153" class="concept-hero__line" marker-end="url(#consume-arrow)"/>
    <rect x="161" y="58" width="119" height="74" rx="10" class="concept-hero__node concept-hero__node--accent"/><text x="220" y="83" text-anchor="middle" class="concept-hero__label">Event handler</text><text x="220" y="100" text-anchor="middle" class="concept-hero__detail">condition + evaluator</text><text x="220" y="117" text-anchor="middle" class="concept-hero__detail">matched actions</text>
    <path d="M280 81 H317" class="concept-hero__line" marker-end="url(#consume-arrow)"/>
    <path d="M280 109 H300 V146 H317" class="concept-hero__line" marker-end="url(#consume-arrow)"/>
    <rect x="325" y="55" width="101" height="44" rx="10" class="concept-hero__node event-hero__node--action"/><text x="375" y="82" text-anchor="middle" class="concept-hero__label">Start workflow</text>
    <rect x="325" y="124" width="101" height="44" rx="10" class="concept-hero__node event-hero__node--action"/><text x="375" y="145" text-anchor="middle" class="concept-hero__label">Exact task</text><text x="375" y="160" text-anchor="middle" class="concept-hero__detail">complete or fail</text>
  </svg>
</section>

## Register a handler

Create and activate the handler with the [Event Handlers API](../../documentation/api/eventhandlers.md). Its `event` is `provider:<provider-specific queue URI>`; runtime parsing splits at the first colon. The provider must be enabled on the server.

On Orkes, first configure the managed broker integration, then use that configured integration in the event-handler flow. The OSS API example below uses an OSS provider key and enabled server module; it is not an integration-setup example.

```json
{
  "name": "start_fulfillment_on_order_ready",
  "event": "conductor:publish_order_event:order-status",
  "condition": "$.status == 'READY'",
  "actions": [
    {
      "action": "start_workflow",
      "start_workflow": {
        "name": "fulfill_order",
        "version": 1,
        "correlationId": "${orderId}",
        "input": {
          "orderId": "${orderId}",
          "sourceEventId": "${workflowInstanceId}"
        }
      }
    }
  ],
  "active": true
}
```

## Match the payload, not a wrapper

Conditions and placeholders are rooted directly at the delivered payload. For example, use `$.status == 'READY'` in a condition and `${orderId}` in an action. A missing condition is true; `active` defaults to `false`.

If `evaluatorType` names a registered evaluator, Conductor uses it. Otherwise it uses the default script evaluator. Set `expandInlineJSON: true` on an action only when fields inside the event are intentionally JSON strings that must be expanded before expressions resolve.

## Choose an action

| Action | OSS Conductor | Orkes | Behavior |
|---|:---:|:---:|---|
| `start_workflow` | Yes | Yes | Starts a named workflow and includes Conductor event metadata in its input. |
| `complete_task` | Yes | Yes | Completes one identified task. |
| `fail_task` | Yes | Yes | Fails one identified task and can set `reasonForIncompletion`. |
| `terminate_workflow` | No | Yes | Terminates the targeted workflow. |
| `update_workflow_variables` | No | Yes | Updates variables on the targeted workflow. |

Task actions need an exact target: provide `taskId`, or both `workflowId` and `taskRefName`. A business correlation key alone cannot resolve an OSS handler action to a waiting task.

## Complete or fail a targeted task

Use a task action when the event itself supplies the task identity. The handler resolves placeholders from the broker payload.

Complete the task when the approval event arrives:

```json
{
  "name": "complete_payment_wait",
  "event": "kafka:payment-events",
  "condition": "$.status == 'APPROVED'",
  "actions": [
    {
      "action": "complete_task",
      "complete_task": {
        "workflowId": "${workflowId}",
        "taskRefName": "wait_for_payment",
        "output": {
          "paymentId": "${paymentId}",
          "approved": true
        }
      }
    }
  ],
  "active": true
}
```

Register a separate handler for a rejected event when it should fail a task:

```json
{
  "name": "fail_payment_wait",
  "event": "kafka:payment-events",
  "condition": "$.status == 'REJECTED'",
  "actions": [
    {
      "action": "fail_task",
      "fail_task": {
        "taskId": "${rejectionTaskId}",
        "reasonForIncompletion": "${reason}",
        "output": {
          "providerStatus": "${status}"
        }
      }
    }
  ],
  "active": true
}
```

## Delivery and idempotency

Actions execute concurrently and are not atomic. Conductor records each action with the broker message ID and action index; a stable message ID enables persisted duplicate detection after the event execution is stored. Still make workflow starts, task updates, and any external side effects idempotent. When a condition is false, Conductor records a skipped event execution and runs no actions.

## Next steps

<div class="event-next-steps">
  <a href="publish-events.html">Publish an event →</a>
  <a href="incoming-webhooks.html">Receive an HTTP callback →</a>
  <a href="../../documentation/configuration/eventhandlers.html">Event handler reference →</a>
  <a href="../cookbook/sending-signals.html">Signal a known waiting workflow →</a>
</div>
