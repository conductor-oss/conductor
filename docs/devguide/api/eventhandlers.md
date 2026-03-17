---
description: "Conductor Event Handlers API — create, update, delete, and list event handlers for event-driven workflow orchestration."
---

# Event Handlers API

The Event Handlers API manages event handler definitions — rules that start workflows or complete tasks in response to events from message brokers (Kafka, NATS, SQS, AMQP). All endpoints use the base path `/api/event`.

For details on configuring event handlers, see [Event Handler Configuration](../../documentation/configuration/eventhandlers.md). For configuring message broker connections, see the [Event Bus Orchestration](../how-tos/event-bus.md) guide.

## Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/event` | `POST` | Create a new event handler |
| `/event` | `PUT` | Update an existing event handler |
| `/event` | `GET` | Get all event handlers |
| `/event/{name}` | `DELETE` | Delete an event handler |
| `/event/{event}` | `GET` | Get event handlers for a specific event |

### Create an Event Handler

```
POST /api/event
```

```shell
curl -X POST 'http://localhost:8080/api/event' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "order_event_handler",
    "event": "kafka:orders_topic:new_order",
    "active": true,
    "actions": [
      {
        "action": "start_workflow",
        "start_workflow": {
          "name": "order_processing",
          "version": 1,
          "input": {
            "orderId": "${eventPayload.orderId}",
            "customerId": "${eventPayload.customerId}",
            "payload": "${eventPayload}"
          }
        }
      }
    ]
  }'
```

**Response** `200 OK` — no response body.

#### Event Handler Fields

| Field | Description | Required |
|---|---|---|
| `name` | Unique name for the event handler | Yes |
| `event` | Event identifier in format `type:queue:subject` (e.g., `kafka:my_topic:my_event`) | Yes |
| `active` | Whether the handler is active | Yes |
| `actions` | List of actions to execute when the event is received | Yes |
| `condition` | Optional JavaScript expression to filter events | No |
| `evaluatorType` | Expression evaluator type (`javascript` or `graaljs`) | No |

#### Action Types

| Action | Description |
|---|---|
| `start_workflow` | Start a new workflow execution |
| `complete_task` | Complete a pending task (e.g., a WAIT task) |
| `fail_task` | Fail a pending task |

#### Complete Task Action Example

```json
{
  "name": "approval_handler",
  "event": "kafka:approvals_topic:approved",
  "active": true,
  "actions": [
    {
      "action": "complete_task",
      "complete_task": {
        "workflowId": "${eventPayload.workflowId}",
        "taskRefName": "wait_for_approval",
        "output": {
          "approved": true,
          "approvedBy": "${eventPayload.approver}"
        }
      }
    }
  ]
}
```

### Update an Event Handler

```
PUT /api/event
```

Updates an existing event handler. The request body is the full event handler definition (same format as create).

```shell
curl -X PUT 'http://localhost:8080/api/event' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "order_event_handler",
    "event": "kafka:orders_topic:new_order",
    "active": false,
    "actions": [
      {
        "action": "start_workflow",
        "start_workflow": {
          "name": "order_processing",
          "version": 2,
          "input": {
            "payload": "${eventPayload}"
          }
        }
      }
    ]
  }'
```

**Response** `200 OK` — no response body.

### Get All Event Handlers

```
GET /api/event
```

Returns a list of all registered event handlers.

```shell
curl 'http://localhost:8080/api/event'
```

**Response** `200 OK`

```json
[
  {
    "name": "order_event_handler",
    "event": "kafka:orders_topic:new_order",
    "active": true,
    "actions": [
      {
        "action": "start_workflow",
        "start_workflow": {
          "name": "order_processing",
          "version": 1,
          "input": {
            "payload": "${eventPayload}"
          }
        }
      }
    ]
  }
]
```

### Delete an Event Handler

```
DELETE /api/event/{name}
```

Removes an event handler by name.

```shell
curl -X DELETE 'http://localhost:8080/api/event/order_event_handler'
```

**Response** `200 OK` — no response body.

### Get Event Handlers for an Event

```
GET /api/event/{event}?activeOnly=true
```

Returns event handlers configured for a specific event.

| Parameter | Description | Default |
|---|---|---|
| `event` | Event identifier (e.g., `kafka:orders_topic:new_order`) | — |
| `activeOnly` | Only return active handlers | `true` |

```shell
curl 'http://localhost:8080/api/event/kafka:orders_topic:new_order?activeOnly=true'
```

**Response** `200 OK` — returns a list of matching event handler definitions.

---

## Event Identifier Format

Event identifiers follow the pattern:

```
{type}:{queue/topic}:{subject}
```

| Type | Example | Description |
|---|---|---|
| `kafka` | `kafka:my_topic:my_event` | Apache Kafka topic |
| `nats` | `nats:my_subject:my_event` | NATS subject |
| `sqs` | `sqs:my_queue:my_event` | Amazon SQS queue |
| `amqp_exchange` | `amqp_exchange:my_exchange:my_event` | RabbitMQ exchange |
| `conductor` | `conductor:my_event:my_event` | Conductor internal event queue |
