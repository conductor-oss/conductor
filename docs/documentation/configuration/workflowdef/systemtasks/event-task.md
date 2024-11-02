# Event Task

```json
"type" : "EVENT"
```

The `EVENT` task type in Conductor is used to publish events to supported eventing systems. It enables event-based dependencies within workflows and tasks, making it possible to trigger external systems like SQS, NATS, or AMQP as part of the workflow execution.

## Use Cases 
An EVENT task can be configured to send an event to an external system at any specified point in a workflow, enabling integration with event-based dependencies.

## Supported Queuing Systems
Conductor supports the following queuing systems for EVENT tasks:

1. Conductor internal events (prefix: `conductor`)
2. SQS (prefix: `sqs`)
3. NATS (prefix: `nats`)
4. AMQP (prefix: `amqp_queue or amqp_exchange`)


## Configuration

| Attribute     | Description                                                                                                                                                                 |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| sink          | The sink specifies the target event queue in the format `prefix:location`, where the prefix denotes the queuing system (e.g., `conductor`, `sqs`, `nats`, or `amqp`/`amqp_exchange`), and the location represents the specific queue name (e.g., `send_email_queue`). |
| asyncComplete | Setting `false` marks the status as COMPLETED upon execution, while setting `true` keeps the status IN_PROGRESS, awaiting completion from an external event.  |

### Conductor Sink
When producing an event with Conductor as a sink, the event name follows the structure:
```conductor:<workflow_name>:<task_reference_name>```

When using Conductor as sink, you have two options: defining the sink as `conductor`, in which case the queue name will default to the taskReferenceName of the Event Task, or specifying the queue name in the sink as `conductor:<queue_name>`. The queue name is in the `event` value of the event Handler, as `conductor:<workflow_name>:<queue_name>`.

### SQS/NATS/AMQP Sink
Use the **queue's name**, NOT the URI. Conductor looks up the URI based on the name.

## Output
Upon execution, the task’s output is sent to the external event queue. The payload contains:


| name               | type    | description                           |
| ------------------ | ------- | ------------------------------------- |
| workflowInstanceId | String  | Workflow ID                           |
| workflowType       | String  | Workflow Name                         |
| workflowVersion    | Integer | Workflow Version                      |
| correlationId      | String  | Workflow Correlation ID                |
| sink               | String  | Copy of input data for "sink"        |
| asyncComplete      | Boolean | Copy of input data for "asyncComplete” |
| event_produced     | String  | Name of the event produced            |

The published event's payload is identical to the task output (except "event_produced").

## Examples

**Conductor Event:**
```json
{
    "type": "EVENT",
    "sink": "conductor:internal_event_name",
    "asyncComplete": false
}
```

**SQS Event:**
```json
{
    "type": "EVENT",
    "sink": "sqs:sqs_queue_name",
    "asyncComplete": false
}
```

**NATS Event:**
```json
{

   "type": "EVENT",

   "sink": "nats:nats_queue_name",

   "asyncComplete": false

}
```

**AMQP Event:**
```json
{

   "type": "EVENT",

   "sink": "amqp:amqp_queue_name",

   "asyncComplete": false

}
```

## Event Queue Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact              | Description                         |
| ------------------------------- | ----------------------------------- |
| conductor-amqp | Support for integration with AMQP |
| conductor-nats | Support for integration with NATS | 

### Modules

#### AMQP

Provides the capability to publish and consume messages from AMQP-compatible brokers.

Configuration (default values shown below):

```java
conductor.event-queues.amqp.enabled=true
conductor.event-queues.amqp.hosts=localhost
conductor.event-queues.amqp.port=5672
conductor.event-queues.amqp.username=guest
conductor.event-queues.amqp.password=guest
conductor.event-queues.amqp.virtualhost=/
conductor.event-queues.amqp.useSslProtocol=false
#milliseconds
conductor.event-queues.amqp.connectionTimeout=60000
conductor.event-queues.amqp.useExchange=true
conductor.event-queues.amqp.listenerQueuePrefix=
```

#### NATS

Provides the capability to publish and consume messages from NATS queues.

Configuration (default values shown below):

```java
conductor.event-queues.nats.enabled=true
conductor.event-queues.nats-stream.clusterId=test-cluster
conductor.event-queues.nats-stream.durableName=
conductor.event-queues.nats-stream.url=nats://localhost:4222
conductor.event-queues.nats-stream.listenerQueuePrefix=
```