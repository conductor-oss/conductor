# Event Task

```json
"type" : "EVENT"
```

The `EVENT` task is a task used to publish an event into one of the supported eventing systems in Conductor.

## Use Cases 
Consider a use case where at some point in the execution, an event is published to an external eventing system such as SQS.
Event tasks are useful for creating event based dependencies for workflows and tasks.

## Supported Queuing Systems
Conductor supports the the following eventing models:

1. Conductor internal events (prefix: `conductor`)
2. SQS (prefix: `sqs`)
3. NATS (prefix: `nats`)
4. AMQP (prefix: `amqp_queue or amqp_exchange`)


## Configuration
The following parameters are specified at the top level of the task configuration.

| Attribute     | Description                                                                                                                                                                 |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| sink          | External event queue in the format of `prefix:location`.  Prefix is either `sqs` or `conductor` and `location` specifies the actual queue name. e.g. `sqs:send_email_queue` |
| asyncComplete | Boolean. [See below](#asynccomplete)                                                                                                                                        |

### asyncComplete
* ```false``` to mark status COMPLETED upon execution 
* ```true``` to keep it IN_PROGRESS, wait for an external event (via Conductor or SQS or EventHandler) to complete it. 

### Conductor sink
When producing an event with Conductor as sink, the event name follows the structure:
```conductor:<workflow_name>:<task_reference_name>```

When using Conductor as sink, you have two options: defining the sink as `conductor` in which case the queue name will default to the taskReferenceName of the Event Task, or specifying the queue name in the sink, as `conductor:<queue_name>`. The queue name is in the `event` value of the event Handler, as `conductor:<workflow_name>:<queue_name>`.

### SQS sink
For SQS, use the **name** of the queue and NOT the URI.  Conductor looks up the URI based on the name.

## Output
Tasks's output are sent as a payload to the external event. In case of SQS the task's output is sent to the SQS message a a payload.


| name               | type    | description                           |
| ------------------ | ------- | ------------------------------------- |
| workflowInstanceId | String  | Workflow id                           |
| workflowType       | String  | Workflow Name                         |
| workflowVersion    | Integer | Workflow Version                      |
| correlationId      | String  | Workflow CorrelationId                |
| sink               | String  | Copy of the input data "sink"         |
| asyncComplete      | Boolean | Copy of the input data "asyncComplete |
| event_produced     | String  | Name of the event produced            |

The published event's payload is identical to the output of the task (except "event_produced").


## Examples

Consider an example where we want to publish an event into SQS to notify an external system. 

```json
{
    "type": "EVENT",
    "sink": "sqs:sqs_queue_name",
    "asyncComplete": false
}
```

An example where we want to publish a messase to conductor's internal queuing system.
```json
{
    "type": "EVENT",
    "sink": "conductor:internal_event_name",
    "asyncComplete": false
}
```