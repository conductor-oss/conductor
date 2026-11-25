---
description: Publish Conductor workflow lifecycle events to Kafka, Conductor queues, or an outbound HTTP webhook.
---

# Workflow status events

The `workflow-event-listener` module publishes lifecycle notifications for workflows that opt in with `workflowStatusListenerEnabled: true` in their definition. The standard server includes this module. Configure one listener with `conductor.workflow-status-listener.type`, or use the composite listener to publish to more than one destination.

```json
{
  "name": "order_processing",
  "version": 1,
  "workflowStatusListenerEnabled": true,
  "tasks": []
}
```

Workflow status events are outbound notifications. They do not register inbound webhooks or create event handlers; use [Event orchestration](event-bus.md) to receive and route broker events.

## Choose a publisher

| Type | Destination | Events |
|---|---|---|
| `kafka` | Kafka topic | `STARTED`, `RERAN`, `RETRIED`, `PAUSED`, `RESUMED`, `RESTARTED`, `COMPLETED`, `TERMINATED`, `FINALIZED` |
| `queue_publisher` | Conductor queue | Completion, termination, and finalization summaries |
| `workflow_publisher` | Outbound HTTP webhook | Configured lifecycle statuses; defaults to `COMPLETED` and `TERMINATED` |
| `composite` | Multiple publishers | The combined events from the selected publishers |

Each publisher serializes workflow summary data. The Kafka publisher wraps that summary in an object with `workflowName`, `eventType`, and `payload`; it uses the workflow ID as the Kafka record key.

## Publish to Kafka

Set the listener type to `kafka`. Kafka producer settings are supplied beneath `conductor.workflow-status-listener.kafka.producer`; the listener uses `workflow-status-events` when no default topic is configured.

```properties
conductor.workflow-status-listener.type=kafka
conductor.workflow-status-listener.kafka.producer[bootstrap.servers]=kafka:29092
conductor.workflow-status-listener.kafka.default-topic=workflow-status-events
conductor.workflow-status-listener.kafka.event-topics.completed=workflow-completed-events
```

`event-topics` overrides the default topic per event name. The configured producer map is limited to supported Kafka producer properties; configure serializers, retries, acknowledgements, and TLS there when required.

## Publish to a Conductor queue

Set the listener type to `queue_publisher`. Completion, termination, and finalization send a serialized `WorkflowSummary` to the respective queue.

```properties
conductor.workflow-status-listener.type=queue_publisher
conductor.workflow-status-listener.queue-publisher.successQueue=_callbackSuccessQueue
conductor.workflow-status-listener.queue-publisher.failureQueue=_callbackFailureQueue
conductor.workflow-status-listener.queue-publisher.finalizeQueue=_callbackFinalizeQueue
```

At least one success or failure queue must be configured. These are Conductor task queues, not the event-handler provider queues documented in [Event orchestration](event-bus.md).

## Publish to an HTTP webhook

Set the listener type to `workflow_publisher` and configure the notification URL. The publisher sends the workflow status notification to that URL asynchronously.

```properties
conductor.workflow-status-listener.type=workflow_publisher
conductor.status-notifier.notification.url=https://example.internal/workflow-events
conductor.status-notifier.notification.subscribed-workflow-statuses=RUNNING,COMPLETED,TERMINATED
```

When `subscribed-workflow-statuses` is omitted, the webhook publisher subscribes to `COMPLETED` and `TERMINATED`. It can also subscribe to `RUNNING`, `PAUSED`, `RESUMED`, `RESTARTED`, `RETRIED`, `RERAN`, and `FINALIZED`.

## Publish to multiple destinations

Use `composite` with a comma-separated list of `kafka`, `queue_publisher`, `workflow_publisher`, and `archive`. Each selected publisher keeps its own configuration namespace.

```properties
conductor.workflow-status-listener.type=composite
conductor.workflow-status-listener.composite.types=kafka,workflow_publisher,queue_publisher

conductor.workflow-status-listener.kafka.producer[bootstrap.servers]=kafka:29092
conductor.workflow-status-listener.kafka.default-topic=workflow-events
conductor.status-notifier.notification.url=https://example.internal/workflow-events
conductor.workflow-status-listener.queue-publisher.successQueue=_callbackSuccessQueue
conductor.workflow-status-listener.queue-publisher.failureQueue=_callbackFailureQueue
```

The composite listener creates each configured publisher independently. A configuration error in one selected publisher prevents it from being created, so validate every selected publisher's required properties before deployment.

## Related references

- [Workflow definition](../../documentation/configuration/workflowdef/index.md#workflow-status-listener) documents the workflow-level opt-in flag.
- [Event orchestration](event-bus.md) documents inbound broker events, event handlers, and provider configuration.
