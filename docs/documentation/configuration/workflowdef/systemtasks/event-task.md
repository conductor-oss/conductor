---
description: EVENT system task inputs, payload, sink expansion, and asynchronous completion behavior.
---

# Publish events with the Event task

`EVENT` publishes a JSON message through a registered event-queue provider. It is the generic publishing task: use [`KAFKA_PUBLISH`](kafka-publish-task.md) when the message contract needs Kafka-specific keys, headers, serializers, or producer controls.

## Task parameters

| Parameter | Required | Behavior |
|---|---|---|
| `sink` | Yes | `provider:<provider-specific destination>`; expressions resolve at runtime |
| `inputParameters` | No | User payload fields |
| `asyncComplete` | No | Defaults to `false`; when true the task remains `IN_PROGRESS` after publish |

In OSS, registered provider identifiers are `conductor`, `kafka`, `sqs`, `nats`, `jsm`, `nats_stream`, `amqp_queue`, and `amqp_exchange`, subject to the corresponding server module being enabled. The provider owns the destination grammar after the first colon; for example, it might be a Kafka topic, an SQS queue URL, a NATS subject, or an AMQP queue/exchange.

## Conductor sink expansion

- `conductor` becomes `conductor:<workflowName>:<taskReferenceName>`.
- `conductor:<suffix>` becomes `conductor:<workflowName>:<suffix>`.

The event handler must listen on the expanded name.

## Published payload and output

The task begins with its resolved input parameters and adds workflow metadata:

| Field | Value |
|---|---|
| `workflowInstanceId` | Parent workflow execution ID |
| `workflowType` | Parent workflow name |
| `workflowVersion` | Parent version |
| `correlationId` | Parent correlation ID |
| `taskToDomain` | Parent domain map |

The task output also contains `event_produced`, the expanded sink. The published message is the task output without `event_produced`. The Event task uses its task ID as the broker message identity, so consumers can use that stable value for duplicate detection.

## Completion behavior

With `asyncComplete: false`, a successful publish completes the task. With `asyncComplete: true`, publishing succeeds but the task remains `IN_PROGRESS`; an external task update or an event-handler `complete_task`/`fail_task` action must resolve it.

## Example

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

For a practical first-use walkthrough, see [Publish events](../../../../devguide/how-tos/publish-events.md). Use [Event-Driven Orchestration](../../../../devguide/how-tos/event-bus.md) for the provider matrix, routing, webhooks, signals, and delivery observability.

<a id="configuration-json"></a>
<a id="conductor-sink-configuration"></a>
<a id="output"></a>
<a id="examples"></a>
