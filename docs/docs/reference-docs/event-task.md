---
sidebar_position: 4
---

# Event Task

```json
"type" : "EVENT"
```

### Introduction
EVENT is a task used to publish an event into one of the supported eventing systems in Conductor.
Conductor supports the following eventing models:

1. Conductor internal events (type: conductor)
2. SQS (type: sqs)

### Use Cases 
Consider a use case where at some point in the execution, an event is published to an external eventing system such as SQS.
Event tasks are useful for creating event based dependencies for workflows and tasks.

Consider an example where we want to publish an event into SQS to notify an external system. 

```json
{
    "type": "EVENT",
    "sink": "sqs:sqs_queue_name",
    "asyncComplete": false
}
```

An example where we want to publish a message to conductor's internal queuing system.
```json
{
    "type": "EVENT",
    "sink": "conductor:internal_event_name",
    "asyncComplete": false
}
```


### Configuration

#### Input Configuration

| Attribute         | Description                                                                                                                                                                 |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name              | Task Name. A unique name that is descriptive of the task function                                                                                                           |
| taskReferenceName | Task Reference Name. A unique reference to this task. There can be multiple references of a task within the same workflow definition                                        |
| type              | Task Type. In this case, `EVENT`                                                                                                                                            |
| sink              | External event queue in the format of `prefix:location`.  Prefix is either `sqs` or `conductor` and `location` specifies the actual queue name. e.g. "sqs:send_email_queue" |
| asyncComplete     | Boolean                                                                                                                                                                     |

#### asyncComplete
* ```false``` to mark status COMPLETED upon execution 
* ```true``` to keep it IN_PROGRESS, wait for an external event (via Conductor or SQS or EventHandler) to complete it. 

#### Output Configuration
Tasks's output are sent as a payload to the external event. In case of SQS the task's output is sent to the SQS message a payload.


| name               | type    | description                           |
|--------------------|---------|---------------------------------------|
| workflowInstanceId | String  | Workflow id                           |
| workflowType       | String  | Workflow Name                         | 
| workflowVersion    | Integer | Workflow Version                      |
| correlationId      | String  | Workflow CorrelationId                |
| sink               | String  | Copy of the input data "sink"         |
| asyncComplete      | Boolean | Copy of the input data "asyncComplete |
| event_produced     | String  | Name of the event produced            |

The published event's payload is identical to the output of the task (except "event_produced").


When producing an event with Conductor as sink, the event name follows the structure:
```conductor:<workflow_name>:<task_reference_name>```

For SQS, use the **name** of the queue and NOT the URI.  Conductor looks up the URI based on the name.

!!!warning
	When using SQS add the [ContribsModule](https://github.com/Netflix/conductor/blob/master/contribs/src/main/java/com/netflix/conductor/contribs/ContribsModule.java) to the deployment.  The module needs to be configured with AWSCredentialsProvider for Conductor to be able to use AWS APIs.


!!!warning
    When using Conductor as sink, you have two options: defining the sink as `conductor` in which case the queue name will default to the taskReferenceName of the Event Task, or specifying the queue name in the sink, as `conductor:<queue_name>`. The queue name is in the `event` value of the event Handler, as `conductor:<workflow_name>:<queue_name>`.


### Supported Queuing Systems
Conductor has support for the following external event queueing systems as part of the OSS build

1. SQS (prefix: sqs)
2. [NATS](https://github.com/Netflix/conductor/tree/main/contribs/src/main/java/com/netflix/conductor/contribs/queue/nats) (prefix: nats)
3. [AMQP](https://github.com/Netflix/conductor/tree/main/contribs/src/main/java/com/netflix/conductor/contribs/queue/amqp) (prefix: amqp_queue or amqp_exchange)
4. Internal Conductor (prefix: conductor) 
To add support for other 
