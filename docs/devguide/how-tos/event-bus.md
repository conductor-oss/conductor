---
description: "Orchestrate event-driven workflows with Conductor using Kafka, NATS, AMQP (RabbitMQ), and SQS as event buses. Configure event handlers to trigger workflows, complete tasks, or fail tasks on incoming events."
---

# Event Bus Orchestration

Conductor integrates with external messaging systems to enable event-driven workflow orchestration. You can publish events from workflows and react to external events — starting workflows, completing tasks, or failing tasks based on incoming messages.

## Supported event buses

| System | Sink prefix | Module | Use case |
| :--- | :--- | :--- | :--- |
| **Kafka** | `kafka` | `kafka` | High-throughput, durable event streaming |
| **NATS** | `nats` | `nats` | Lightweight, low-latency messaging |
| **NATS Streaming** | `nats-stream` | `nats-streaming` | Durable NATS with replay (legacy) |
| **NATS JetStream** | `nats` | `nats` | Modern durable NATS streaming |
| **AMQP (RabbitMQ)** | `amqp`, `amqp_queue`, `amqp_exchange` | `amqp` | Traditional message queuing with routing |
| **SQS** | `sqs` | `sqs` | AWS-native message queuing |
| **Conductor** | `conductor` | built-in | Internal event routing between workflows |


## How it works

Event bus orchestration has two sides:

1. **Publishing** — Use the [Event task](../../documentation/configuration/workflowdef/systemtasks/event-task.md) or [Kafka Publish task](../../documentation/configuration/workflowdef/systemtasks/kafka-publish-task.md) to send messages from a workflow.
2. **Consuming** — Register [event handlers](../../documentation/configuration/eventhandlers.md) that listen for messages and trigger actions.

```
┌──────────────┐     Event Task      ┌──────────────┐    Event Handler    ┌──────────────┐
│  Workflow A   │ ──────────────────► │  Event Bus   │ ──────────────────► │  Workflow B   │
│              │   (publish)         │ (Kafka/NATS/ │   (start_workflow)  │  (triggered)  │
│              │                     │  AMQP/SQS)   │                     │              │
└──────────────┘                     └──────────────┘                     └──────────────┘
```


## Publishing events

### Event task

The [Event task](../../documentation/configuration/workflowdef/systemtasks/event-task.md) publishes a message to any supported event bus. The `sink` parameter determines the target:

```json
{
  "name": "notify_downstream",
  "taskReferenceName": "notify_ref",
  "type": "EVENT",
  "sink": "kafka:order-events",
  "inputParameters": {
    "orderId": "${workflow.input.orderId}",
    "status": "PROCESSED"
  }
}
```

### Kafka Publish task

For Kafka-specific features (custom headers, key, serializers), use the dedicated [Kafka Publish task](../../documentation/configuration/workflowdef/systemtasks/kafka-publish-task.md):

```json
{
  "name": "publish_to_kafka",
  "taskReferenceName": "kafka_ref",
  "type": "KAFKA_PUBLISH",
  "inputParameters": {
    "kafka_request": {
      "topic": "order-events",
      "value": "${workflow.input.orderData}",
      "bootStrapServers": "kafka:9092",
      "headers": {
        "X-Correlation-Id": "${workflow.correlationId}"
      }
    }
  }
}
```

### Sink format

The `sink` parameter follows the format `prefix:queue_name`:

| Example | System |
| :--- | :--- |
| `kafka:order-events` | Kafka topic `order-events` |
| `nats:notifications` | NATS subject `notifications` |
| `amqp:task-queue` | AMQP queue `task-queue` |
| `amqp_exchange:events` | AMQP exchange `events` |
| `sqs:my-queue` | SQS queue `my-queue` |
| `conductor` | Conductor internal queue |
| `conductor:workflow_name:queue_name` | Conductor internal, specific queue |


## Consuming events

### Event handlers

Event handlers listen for messages on an event bus and execute actions when a matching event arrives. Register them via the `/api/event` API.

```json
{
  "name": "order_event_handler",
  "event": "kafka:order-events",
  "condition": "$.status == 'PROCESSED'",
  "actions": [
    {
      "action": "start_workflow",
      "start_workflow": {
        "name": "fulfillment_workflow",
        "input": {
          "orderId": "${orderId}"
        }
      }
    }
  ]
}
```

### Supported actions

| Action | Description |
| :--- | :--- |
| `start_workflow` | Start a new workflow execution with the event payload as input. |
| `complete_task` | Complete a waiting task (e.g., a `WAIT` or `HUMAN` task) in a running workflow. |
| `fail_task` | Fail a task in a running workflow. |

### Conditions

The `condition` field supports JavaScript-like expressions evaluated against the event payload:

| Expression | Result |
| :--- | :--- |
| `$.version > 1` | true if `version` field > 1 |
| `$.metadata.codec == 'aac'` | true if nested field matches |
| `$.status == 'COMPLETED'` | true if status is COMPLETED |

Actions execute only when the condition evaluates to `true`. If no condition is specified, actions execute for every event.


## Patterns

### Event-driven workflow chaining

Decouple workflows using events instead of sub-workflows:

```json
{
  "name": "order_pipeline",
  "tasks": [
    {
      "name": "process_order",
      "taskReferenceName": "process_ref",
      "type": "SIMPLE"
    },
    {
      "name": "notify_fulfillment",
      "taskReferenceName": "notify_ref",
      "type": "EVENT",
      "sink": "kafka:fulfillment-requests",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}",
        "items": "${process_ref.output.items}"
      }
    }
  ]
}
```

A separate event handler starts the fulfillment workflow when the event arrives.

### Wait for external event

Combine a `WAIT` task with an event handler to pause a workflow until an external system signals completion:

```json
{
  "name": "wait_for_approval",
  "taskReferenceName": "approval_ref",
  "type": "WAIT"
}
```

Register an event handler that completes the task when an approval event arrives:

```json
{
  "name": "approval_handler",
  "event": "kafka:approval-events",
  "condition": "$.approved == true",
  "actions": [
    {
      "action": "complete_task",
      "complete_task": {
        "workflowId": "${workflowId}",
        "taskRefName": "approval_ref",
        "output": {
          "approvedBy": "${approvedBy}"
        }
      }
    }
  ]
}
```


## Configuration

Each event bus module requires its own configuration. Enable the modules you need in your Conductor server configuration:

### Kafka

```properties
conductor.event-queues.kafka.enabled=true
conductor.event-queues.kafka.bootstrap-servers=kafka:9092
```

### NATS

```properties
conductor.event-queues.nats.enabled=true
conductor.event-queues.nats.url=nats://localhost:4222
```

### AMQP (RabbitMQ)

```properties
conductor.event-queues.amqp.enabled=true
conductor.event-queues.amqp.hosts=rabbitmq
conductor.event-queues.amqp.port=5672
conductor.event-queues.amqp.username=guest
conductor.event-queues.amqp.password=guest
```

Refer to the module source code for the full set of configuration properties.
