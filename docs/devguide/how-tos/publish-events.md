---
description: Publish resolved workflow data to an enabled event sink with EVENT, or use KAFKA_PUBLISH only for Kafka-specific controls.
---

# Publish events

<section class="concept-hero concept-hero--event-bus" aria-labelledby="publish-events-title">
  <div class="concept-hero__content">
    <p class="concept-hero__eyebrow">Outbound workflow events</p>
    <h2 id="publish-events-title">Publish resolved data to a configured sink</h2>
    <p><code>EVENT</code> enriches resolved workflow input with durable metadata and publishes it through an enabled provider. Use <code>KAFKA_PUBLISH</code> only when the contract needs Kafka-specific producer controls.</p>
    <p><a href="../../documentation/configuration/workflowdef/systemtasks/event-task.html">EVENT task reference</a> · <a href="consume-route-events.html">Consume and route events</a></p>
  </div>
  <svg class="concept-hero__graphic event-hero__graphic" viewBox="0 0 440 190" role="img" aria-labelledby="publish-svg-title publish-svg-desc" xmlns="http://www.w3.org/2000/svg">
    <title id="publish-svg-title">Workflow event publication flow</title>
    <desc id="publish-svg-desc">A workflow sends resolved input to the Event task, which publishes to a configured sink for a broker and consumer. Kafka Publish is a separate Kafka-specific option.</desc>
    <defs><marker id="publish-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="currentColor"/></marker></defs>
    <rect x="14" y="59" width="92" height="54" rx="10" class="concept-hero__node"/><text x="60" y="82" text-anchor="middle" class="concept-hero__label">Workflow</text><text x="60" y="99" text-anchor="middle" class="concept-hero__detail">resolved input</text>
    <path d="M106 86 H145" class="concept-hero__line" marker-end="url(#publish-arrow)"/>
    <rect x="153" y="59" width="88" height="54" rx="10" class="concept-hero__node concept-hero__node--accent"/><text x="197" y="82" text-anchor="middle" class="concept-hero__label">EVENT</text><text x="197" y="99" text-anchor="middle" class="concept-hero__detail">metadata</text>
    <path d="M241 86 H279" class="concept-hero__line" marker-end="url(#publish-arrow)"/>
    <rect x="287" y="59" width="139" height="54" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="356" y="82" text-anchor="middle" class="concept-hero__label">Configured sink</text><text x="356" y="99" text-anchor="middle" class="concept-hero__detail">broker or consumer</text>
    <path d="M197 113 V143 H317" class="concept-hero__line event-hero__line--dashed" marker-end="url(#publish-arrow)"/>
    <text x="194" y="164" text-anchor="middle" class="concept-hero__detail">KAFKA_PUBLISH: Kafka-specific branch</text>
  </svg>
</section>

## Choose the task

| Use | When it fits | What it gives you |
|---|---|---|
| `EVENT` | You want a provider-neutral message to an enabled event-queue provider. | A common sink model, workflow metadata, a stable message identity, and event-handler compatibility. |
| `KAFKA_PUBLISH` | Your contract needs Kafka-specific keys, headers, serializers, or producer controls. | Direct Kafka topic publishing with Kafka-specific configuration. |

Do not use `KAFKA_PUBLISH` just because the destination happens to be Kafka. Prefer `EVENT` unless those Kafka-specific controls are required.

## Name the destination

An `EVENT` sink is `provider:<provider-specific destination>`. In OSS, enabled provider keys include `conductor`, `kafka`, `sqs`, `nats`, `jsm`, `nats_stream`, `amqp_queue`, and `amqp_exchange`.

The `conductor` provider expands a short sink so it is namespaced by the workflow:

| Sink in the definition | Expanded sink for workflow `order_workflow` |
|---|---|
| `conductor` | `conductor:order_workflow:<taskReferenceName>` |
| `conductor:order-status` | `conductor:order_workflow:order-status` |

An event handler must subscribe to the expanded name. Kafka topics, SQS queue URLs, NATS subjects, and AMQP destinations retain the grammar required by their provider.

On Orkes, select the managed broker integration configured for the tenant and use its integration-qualified sink naming. That configuration is distinct from the OSS provider keys above: do not copy an OSS provider prefix into an Orkes integration name, or assume an Orkes integration name is portable to OSS.

## What is published

Conductor resolves `inputParameters`, then adds these fields to the published JSON:

| Field | Value |
|---|---|
| `workflowInstanceId` | Parent workflow execution ID |
| `workflowType` / `workflowVersion` | Parent workflow name and version |
| `correlationId` | Parent correlation ID |
| `taskToDomain` | Parent task-domain map |

The task output also includes `event_produced`, the expanded sink, but that field is not sent as part of the broker message. The Event task ID is the broker message identity; consumers can use it as a durable duplicate-detection key.

## Publish an order-status event

```json
{
  "name": "publish_order_status",
  "taskReferenceName": "publish_order_status",
  "type": "EVENT",
  "sink": "conductor:order-status",
  "inputParameters": {
    "orderId": "${workflow.input.orderId}",
    "status": "READY"
  },
  "asyncComplete": false
}
```

With `asyncComplete: false`, a successful broker publish completes the task. With `asyncComplete: true`, publication succeeds but the task remains `IN_PROGRESS` until an external task update, or an event-handler `complete_task` or `fail_task` action, resolves it.

## Production guidance

- **Delivery:** Treat broker delivery as at-least-once. Consumer actions and any side effects must be idempotent.
- **Observability:** Monitor `event_queue_depth`, the `event_queue_messages_*` counters, then inspect the downstream workflow or task result.
- **Identity:** Preserve the broker message ID and use the Event task ID for duplicate detection; do not invent a new random key for retries.

## Next steps

<div class="event-next-steps">
  <a href="consume-route-events.html">Route the published event →</a>
  <a href="incoming-webhooks.html">Receive HTTP callbacks instead →</a>
  <a href="../../documentation/configuration/workflowdef/systemtasks/event-task.html">EVENT task reference →</a>
  <a href="../../documentation/configuration/workflowdef/systemtasks/kafka-publish-task.html">KAFKA_PUBLISH reference →</a>
</div>
