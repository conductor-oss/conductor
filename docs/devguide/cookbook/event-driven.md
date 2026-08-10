---
description: "Conductor cookbook — event-driven workflow recipes for publishing to Kafka, NATS, RabbitMQ, SQS, triggering workflows from events, and completing tasks from external events."
---

# Event-driven recipes

### Publish events to Kafka, NATS, and RabbitMQ

Use the `EVENT` task type to publish messages. The `sink` field determines the destination.

**Kafka:**

```json
{
  "name": "publish_to_kafka",
  "taskReferenceName": "kafka_event",
  "type": "EVENT",
  "sink": "kafka:order-events",
  "inputParameters": {
    "orderId": "${workflow.input.orderId}",
    "status": "PROCESSED"
  }
}
```

**NATS:**

```json
{
  "name": "publish_to_nats",
  "taskReferenceName": "nats_event",
  "type": "EVENT",
  "sink": "nats:order-events",
  "inputParameters": {
    "orderId": "${workflow.input.orderId}",
    "status": "PROCESSED"
  }
}
```

**RabbitMQ (AMQP):**

```json
{
  "name": "publish_to_rabbitmq",
  "taskReferenceName": "amqp_event",
  "type": "EVENT",
  "sink": "amqp_exchange:order-events",
  "inputParameters": {
    "orderId": "${workflow.input.orderId}",
    "status": "PROCESSED"
  }
}
```

**Sink format reference:**

| Sink | Format |
|---|---|
| Kafka | `kafka:topic-name` |
| NATS | `nats:subject-name` |
| RabbitMQ queue | `amqp:queue-name` |
| RabbitMQ exchange | `amqp_exchange:exchange-name` |
| SQS | `sqs:queue-name` |
| Conductor internal | `conductor` |

---

### Listen for events to trigger workflows

Register event handlers to start workflows automatically when messages arrive on a queue or topic.

**Kafka event handler:**

```json
{
  "name": "kafka_order_handler",
  "event": "kafka:order-events",
  "condition": "$.status == 'NEW'",
  "actions": [
    {
      "action": "start_workflow",
      "start_workflow": {
        "name": "process_order",
        "input": {
          "orderId": "${orderId}",
          "payload": "${$}"
        }
      }
    }
  ],
  "active": true
}
```

**NATS event handler:**

```json
{
  "name": "nats_notification_handler",
  "event": "nats:notifications",
  "actions": [
    {
      "action": "start_workflow",
      "start_workflow": {
        "name": "handle_notification",
        "input": { "data": "${$}" }
      }
    }
  ],
  "active": true
}
```

**AMQP event handler:**

```json
{
  "name": "amqp_task_handler",
  "event": "amqp:task-queue",
  "actions": [
    {
      "action": "start_workflow",
      "start_workflow": {
        "name": "process_task",
        "input": { "taskData": "${$}" }
      }
    }
  ],
  "active": true
}
```

**Register an event handler:**

```shell
curl -X POST 'http://localhost:8080/api/event' \
  -H 'Content-Type: application/json' \
  -d @handler.json
```

---

### Complete a task from an external event

Use a WAIT task to pause a workflow until an external system sends an event. An event handler listens for that event and completes the task, resuming the workflow.

**Workflow with WAIT task:**

```json
{
  "name": "order_with_approval",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "process_order",
      "taskReferenceName": "process",
      "type": "SIMPLE"
    },
    {
      "name": "wait_for_approval",
      "taskReferenceName": "approval_wait",
      "type": "WAIT"
    },
    {
      "name": "ship_order",
      "taskReferenceName": "ship",
      "type": "SIMPLE"
    }
  ]
}
```

**Event handler to complete the WAIT task:**

```json
{
  "name": "approval_event_handler",
  "event": "kafka:approval-events",
  "condition": "$.approved == true",
  "actions": [
    {
      "action": "complete_task",
      "complete_task": {
        "workflowId": "${workflowId}",
        "taskRefName": "approval_wait",
        "output": {
          "approvedBy": "${approvedBy}",
          "approvedAt": "${timestamp}"
        }
      }
    }
  ],
  "active": true
}
```

When a message with `approved: true` arrives on the `approval-events` Kafka topic, the handler completes the WAIT task and the workflow continues to `ship_order`.

**Register both:**

```shell
# Register the workflow
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @order_with_approval.json

# Register the event handler
curl -X POST 'http://localhost:8080/api/event' \
  -H 'Content-Type: application/json' \
  -d @approval_event_handler.json
```

---

### Server configuration for event buses

Add the relevant properties to your `application.properties` to enable each event bus.

**Kafka:**

```properties
conductor.event-queues.kafka.enabled=true
conductor.event-queues.kafka.bootstrap-servers=kafka:9092
```

**NATS:**

```properties
conductor.event-queues.nats.enabled=true
conductor.event-queues.nats.url=nats://localhost:4222
```

**AMQP (RabbitMQ):**

```properties
conductor.event-queues.amqp.enabled=true
conductor.event-queues.amqp.hosts=rabbitmq
conductor.event-queues.amqp.port=5672
conductor.event-queues.amqp.username=guest
conductor.event-queues.amqp.password=guest
```

**SQS:**

```properties
conductor.event-queues.sqs.enabled=true
# Uses AWS default credential chain (env vars, IAM role, etc.)
```
