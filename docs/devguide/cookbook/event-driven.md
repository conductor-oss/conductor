---
description: Copy-and-paste EVENT and event-handler recipes using canonical fixtures.
---

# Event-driven recipes

Read [Event orchestration](../how-tos/event-bus.md) first for action support, provider configuration, delivery, and idempotency semantics.

## Publish an internal event

```json
--8<-- "docs/devguide/cookbook/examples/events/publish-internal-event-workflow.json"
```

Register and run the workflow. Its `conductor:order-status` sink expands to `conductor:publish_order_event:order-status`.

## Start a workflow from the event

Register the target workflow first:

```json
--8<-- "docs/devguide/cookbook/examples/events/fulfill-order-workflow.json"
```

```json
--8<-- "docs/devguide/cookbook/examples/events/start-workflow-handler.json"
```

```bash
curl -sS -X POST 'http://localhost:8080/api/event' \
  -H 'Content-Type: application/json' \
  --data-binary @docs/devguide/cookbook/examples/events/start-workflow-handler.json
```

The payload expression is rooted directly at the Event task's published JSON.

## Wait for an external approval

Workflow:

```json
--8<-- "docs/devguide/cookbook/examples/events/wait-for-approval-workflow.json"
```

Handler:

```json
--8<-- "docs/devguide/cookbook/examples/events/complete-wait-handler.json"
```

Representative broker payload:

```json
--8<-- "docs/devguide/cookbook/examples/events/approval-event.json"
```

Replace the representative `workflowId` with the ID returned when the waiting workflow starts. A correlation ID alone cannot target the WAIT task.

## Use an external provider

Change `event`/`sink` to a registered provider identifier and its provider-specific URI, for example `kafka:order-approvals`, `sqs:https://sqs.us-east-1.amazonaws.com/123/order-events`, `nats:orders.ready`, `jsm:orders.ready`, `nats_stream:orders.ready`, `amqp_queue:orders`, or `amqp_exchange:orders`. Enable the matching module and properties described in the guide.
