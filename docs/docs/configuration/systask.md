# System Tasks

System Tasks (Workers) are built-in tasks that are general purpose and re-usable. They run on the Conductor servers.
Such tasks allow you to get started without having to write custom workers.

## Available System Tasks

Conductor has the following set of system tasks available.

| Task                  | Description                                            | Use Case                                                                           |
|-----------------------|--------------------------------------------------------|------------------------------------------------------------------------------------|
| Event Publishing      | [Event Task](/reference-docs/event-task.html)          | External eventing system integration. e.g. amqp, sqs, nats                         |
| HTTP                  | [HTTP Task](/reference-docs/http-task.html)            | Invoke any HTTP(S) endpoints                                                       |
| Inline Code Execution | [Inline Task](/reference-docs/inline-task.html)        | Execute arbitrary lightweight javascript code                                      |
| JQ Transform          | [JQ Task](/reference-docs/json-jq-transform-task.html) | Use <a href="https://github.com/stedolan/jq">JQ</a> to transform task input/output |
| Kafka Publish         | [Kafka Task](/reference-docs/kafka-publish-task.html)  | Publish messages to Kafka                                                          |

| Name                     | Description                                                                                                                               |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| joinOn                   | List of task reference names, which the EXCLUSIVE_JOIN will lookout for to capture output. From above example, this could be ["T2", "T3"] |
| defaultExclusiveJoinTask | Task reference name, whose output should be used incase the decision case is undefined. From above example, this could be ["T1"]          |


**Example**

``` json
{
  "name": "exclusive_join",
  "taskReferenceName": "exclusiveJoin",
  "type": "EXCLUSIVE_JOIN",
  "joinOn": [
    "task2",
    "task3"
  ],
  "defaultExclusiveJoinTask": [
    "task1"
  ]
}
```


## Wait
A wait task is implemented as a gate that remains in ```IN_PROGRESS``` state unless marked as ```COMPLETED``` or ```FAILED``` by an external trigger.
To use a wait task, set the task type as ```WAIT```

**Parameters:**
None required.

**External Triggers for Wait Task**

Task Resource endpoint can be used to update the status of a task to a terminate state. 

Contrib module provides SQS integration where an external system can place a message in a pre-configured queue that the server listens on.  As the messages arrive, they are marked as ```COMPLETED``` or ```FAILED```.  

**SQS Queues**

* SQS queues used by the server to update the task status can be retrieve using the following API:
```
GET /queue
```
* When updating the status of the task, the message needs to conform to the following spec:
	* 	Message has to be a valid JSON string.
	*  The message JSON should contain a key named ```externalId``` with the value being a JSONified string that contains the following keys:
		*  ```workflowId```: Id of the workflow
		*  ```taskRefName```: Task reference name that should be updated.
	*  Each queue represents a specific task status and tasks are marked accordingly.  e.g. message coming to a ```COMPLETED``` queue marks the task status as ```COMPLETED```.
	*  Tasks' output is updated with the message.

**Example SQS Payload:**

```json
{
  "some_key": "valuex",
  "externalId": "{\"taskRefName\":\"TASK_REFERENCE_NAME\",\"workflowId\":\"WORKFLOW_ID\"}"
}
```


## Dynamic Task

Dynamic Task allows to execute one of the registered Tasks dynamically at run-time. It accepts the task name to execute in inputParameters.

**Parameters:**

|name|description|
|---|---|
| dynamicTaskNameParam|Name of the parameter from the task input whose value is used to schedule the task.  e.g. if the value of the parameter is ABC, the next task scheduled is of type 'ABC'.|

**Example**
``` json
{
  "name": "user_task",
  "taskReferenceName": "t1",
  "inputParameters": {
    "files": "${workflow.input.files}",
    "taskToExecute": "${workflow.input.user_supplied_task}"
  },
  "type": "DYNAMIC",
  "dynamicTaskNameParam": "taskToExecute"
}
```
If the workflow is started with input parameter user_supplied_task's value as __user_task_2__, Conductor will schedule __user_task_2__ when scheduling this dynamic task.

## Inline Task

Inline Task helps execute ad-hoc logic at Workflow run-time, using any evaluator engine. Supported evaluators 
are `value-param` evaluator which simply translates the input parameter to output and `javascript` evaluator that 
evaluates Javascript expression.

This is particularly helpful in running simple evaluations in Conductor server, over creating Workers.

**Parameters:**

|name|type|description|notes|
|---|---|---|---|
|evaluatorType|String|Type of the evaluator. Supported evaluators: `value-param`, `javascript` which evaluates javascript expression.|
|expression|String|Expression associated with the type of evaluator. For `javascript` evaluator, Javascript evaluation engine is used to evaluate expression defined as a string. Must return a value.|Must be non-empty.|

Besides `expression`, any value is accessible as `$.value` for the `expression` to evaluate.

**Outputs:**

|name|type|description|
|---|---|---|
|result|Map|Contains the output returned by the evaluator based on the `expression`|

The task output can then be referenced in downstream tasks like:
```"${inline_test.output.result.testvalue}"```

**Example**
``` json
{
  "name": "INLINE_TASK",
  "taskReferenceName": "inline_test",
  "type": "INLINE",
  "inputParameters": {
      "inlineValue": "${workflow.input.inlineValue}",
      "evaluatorType": "javascript",
      "expression": "function scriptFun(){if ($.inlineValue == 1){ return {testvalue: true} } else { return 
      {testvalue: false} }} scriptFun();"
  }
}
```

## Terminate Task

Task that can terminate a workflow with a given status and modify the workflow's output with a given parameter. It can act as a "return" statement for conditions where you simply want to terminate your workflow.

For example, if you have a decision where the first condition is met, you want to execute some tasks, otherwise you want to finish your workflow.

**Parameters:**

|name|type| description                                       | notes                   |
|---|---|---------------------------------------------------|-------------------------|
|terminationStatus|String| can only accept "COMPLETED" or "FAILED"           | task cannot be optional |
 |terminationReason|String| reason for incompletion to be set in the workflow | optional                |
|workflowOutput|Any| Expected workflow output | optional |

**Outputs:**

|name|type|description|
|---|---|---|
|output|Map|The content of `workflowOutput` from the inputParameters. An empty object if `workflowOutput` is not set.|

```json
{
  "name": "terminate",
  "taskReferenceName": "terminate0",
  "inputParameters": {
      "terminationStatus": "COMPLETED",
	  "terminationReason": "",
      "workflowOutput": "${task0.output}"
  },
  "type": "TERMINATE",
  "startDelay": 0,
  "optional": false
}
```


## Kafka Publish Task

A kafka Publish task is used to push messages to another microservice via kafka

**Parameters:**

The task expects an input parameter named ```kafka_request``` as part of the task's input with the following details:

|name|description|
|---|---|
| bootStrapServers |bootStrapServers for connecting to given kafka.|
|key|Key to be published|
|keySerializer | Serializer used for serializing the key published to kafka.  One of the following can be set : <br/> 1. org.apache.kafka.common.serialization.IntegerSerializer<br/>2. org.apache.kafka.common.serialization.LongSerializer<br/>3. org.apache.kafka.common.serialization.StringSerializer. <br/>Default is String serializer  |
|value| Value published to kafka|
|requestTimeoutMs| Request timeout while publishing to kafka. If this value is not given the value is read from the property `kafka.publish.request.timeout.ms`. If the property is not set the value defaults to 100 ms |
|maxBlockMs| maxBlockMs while publishing to kafka. If this value is not given the value is read from the property `kafka.publish.max.block.ms`. If the property is not set the value defaults to 500 ms |
|headers|A map of additional kafka headers to be sent along with the request.|
|topic|Topic to publish|

The producer created in the kafka task is cached. By default the cache size is 10 and expiry time is 120000 ms. To change the defaults following can be modified kafka.publish.producer.cache.size,kafka.publish.producer.cache.time.ms respectively.  

**Kafka Task Output**

Task status transitions to COMPLETED

**Example**

Task sample

```json
{
  "name": "call_kafka",
  "taskReferenceName": "call_kafka",
  "inputParameters": {
    "kafka_request": {
      "topic": "userTopic",
      "value": "Message to publish",
      "bootStrapServers": "localhost:9092",
      "headers": {
  	"x-Auth":"Auth-key"    
      },
      "key": "123",
      "keySerializer": "org.apache.kafka.common.serialization.IntegerSerializer"
    }
  },
  "type": "KAFKA_PUBLISH"
}
```

The task is marked as ```FAILED``` if the message could not be published to the Kafka queue. 


## Do While Task

Sequentially execute a list of task as long as a condition is true. The list of tasks is executed first, before the condition is
checked (even for the first iteration).

When scheduled, each task of this loop will see its `taskReferenceName` concatenated with `__i`, with `i` being the
iteration number, starting at 1. Warning: `taskReferenceName` containing arithmetic operators must not be used.

Each task output is stored as part of the `DO_WHILE` task, indexed by the iteration value (see example below), allowing
the condition to reference the output of a task for a specific iteration (eg. ```$.LoopTask['iteration]['first_task']```)

The `DO_WHILE` task is set to `FAILED` as soon as one of the loopTask fails. In such case retry, iteration starts from 1.

Limitations:
 - Domain or isolation group execution is unsupported;
 - Nested `DO_WHILE` is unsupported;
 - `SUB_WORKFLOW` is unsupported;
 - Since loopover tasks will be executed in loop inside scope of parent do while task, crossing branching outside of DO_WHILE
   task is not respected. Branching inside loopover task is supported.

**Parameters:**

|name|type|description|

