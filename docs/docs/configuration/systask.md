## Switch
A switch task is similar to ```case...switch``` statement in a programming language. 
The `switch` expression, however, is simply an input parameter (`value-param` evaluator) or a complex javascript 
expression (`javascript` evaluator). Only two evaluators are supported by default in conductor. 

**For Conductor Developers**: Custom evaluators can be implemented without having to change the way `SWITCH` task works.
To implement and use the custom evaluators we can use the params `evaluatorType` and `expression`. 

**Parameters:**

|name|type|description|
|---|---|---|
|evaluatorType|String|Type of the evaluator used. Supported types: `value-param`, `javascript`.|
|expression|String|Expression that depends on the evaluator type. For `value-param` evaluator, expression is input parameter, for `javascript` evaluator, it is the javascript expression.|
|decisionCases|Map[String, List[task]]|Map where key is possible values that can result from `expression` being evaluated by `evaluatorType` with value being list of tasks to be executed.|
|defaultCase|List[task]|List of tasks to be executed when no matching value if found in decision case (default condition)|


**Outputs:**

|name|type|description|
|---|---|---|
|evaluationResult|List[String]|A List of string representing the list of cases that matched.|

**Example**

``` json
{
  "name": "switch_task",
  "taskReferenceName": "switch",
  "inputParameters": {
    "case_value_param": "${workflow.input.movieType}"
  },
  "type": "SWITCH",
  "evaluatorType": "value-param",
  "expression": "case_value_param",
  "decisionCases": {
    "Show": [
      {
        "name": "setup_episodes",
        "taskReferenceName": "se1",
        "inputParameters": {
          "movieId": "${workflow.input.movieId}"
        },
        "type": "SIMPLE"
      },
      {
        "name": "generate_episode_artwork",
        "taskReferenceName": "ga",
        "inputParameters": {
          "movieId": "${workflow.input.movieId}"
        },
        "type": "SIMPLE"
      }
    ],
    "Movie": [
      {
        "name": "setup_movie",
        "taskReferenceName": "sm",
        "inputParameters": {
          "movieId": "${workflow.input.movieId}"
        },
        "type": "SIMPLE"
      },
      {
        "name": "generate_movie_artwork",
        "taskReferenceName": "gma",
        "inputParameters": {
          "movieId": "${workflow.input.movieId}"
        },
        "type": "SIMPLE"
      }
    ]
  }
}
```

### Decision (Deprecated)

`DECISION` task type has been **deprecated** and replaced with the `SWITCH` task type. Switch task type is identical to how Decision tasks works except for the following differences:

`DECISION` task type used to take two parameters 
1. `caseExpression` : If present, this takes precedence and will be evaluated as a Javascript expression
2. `caseValueParam` : If `caseExpression` param is null or empty, case value param will be used to determine the decision branch

`SWITCH` works with the `evaluatorType` and `expression` params as a replacement to the above. For details refer to the `SWITCH` task documentation

## Event
Event task provides ability to publish an event (message) to either Conductor or an external eventing system like SQS.  Event tasks are useful for creating event based dependencies for workflows and tasks.

**Parameters:**

|name|type|description|
|---|---|---|
| sink | String | Qualified name of the event that is produced.  e.g. conductor or sqs:sqs_queue_name|
| asyncComplete | Boolean | ```false``` to mark status COMPLETED upon execution ; ```true``` to keep it IN_PROGRESS, wait for an external event (via Conductor or SQS or EventHandler) to complete it. |

**Outputs:**

|name|type|description|
|---|---|---|
| workflowInstanceId | String | Workflow id |
| workflowType | String | Workflow Name | 
| workflowVersion | Integer | Workflow Version |
| correlationId | String | Workflow CorrelationId |
| sink | String | Copy of the input data "sink" |
| asyncComplete | Boolean | Copy of the input data "asyncComplete |
| event_produced | String | Name of the event produced |

The published event's payload is identical to the output of the task (except "event_produced").

**Example**

``` json
{
	"sink": "sqs:example_sqs_queue_name",
	"asyncComplete": false
}
```

When producing an event with Conductor as sink, the event name follows the structure:
```conductor:<workflow_name>:<task_reference_name>```

For SQS, use the **name** of the queue and NOT the URI.  Conductor looks up the URI based on the name.

!!!warning
	When using SQS add the [ContribsModule](https://github.com/Netflix/conductor/blob/master/contribs/src/main/java/com/netflix/conductor/contribs/ContribsModule.java) to the deployment.  The module needs to be configured with AWSCredentialsProvider for Conductor to be able to use AWS APIs.

!!warning
	When using Conductor as sink, you have two options: defining the sink as `conductor` in which case the queue name will default to the taskReferenceName of the Event Task, or specifying the queue name in the sink, as `conductor:<queue_name>`. The queue name is in the `event` value of the event Handler, as `conductor:<workflow_name>:<queue_name>`.

**Supported Sinks**

* Conductor
* SQS


## HTTP
An HTTP task is used to make calls to another microservice over HTTP.

**Parameters:**

|name|type|description|
|---|---|---|
| http_request | HttpRequest | JSON object (see below) |

```HttpRequest``` JSON object:

|name|type|description|
|---|---|---|
| uri | String | URI for the service.  Can be a partial when using vipAddress or includes the server address.|
| method | String | HTTP method.  One of the GET, PUT, POST, DELETE, OPTIONS, HEAD|
| accept | String | Accept header as required by server. Defaults to ```application/json``` |
| contentType | String | Content Type - supported types are ```text/plain```, ```text/html```, and ```application/json``` (Default)|
| headers| Map[String, Any] | A map of additional http headers to be sent along with the request.|
| body| Map[] | Request body |
| vipAddress | String | When using discovery based service URLs.|
| asyncComplete | Boolean | ```false``` to mark status COMPLETED upon execution ; ```true``` to keep it IN_PROGRESS, wait for an external event (via Conductor or SQS or EventHandler) to complete it.
| oauthConsumerKey | String | [OAuth](https://oauth.net/core/1.0/) client consumer key  |
| oauthConsumerSecret | String | [OAuth](https://oauth.net/core/1.0/) client consumer secret |
| connectionTimeOut | Integer | Connection Time Out in milliseconds. If set to 0, equivalent to infinity. Default: 100. |
| readTimeOut | Integer | Read Time Out in milliseconds. If set to 0, equivalent to infinity. Default: 150. |

**Output:**

|name|type|description|
|---|---|---|
| response | Map |  JSON body containing the response if one is present |
| headers | Map[String, Any] | Response Headers |
| statusCode | Integer | [Http Status Code](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes) |
| reasonPhrase | String | Http Status Code's reason phrase |

**Example**

Task Input payload using vipAddress

```json
{
  "http_request": {
    "vipAddress": "examplevip-prod",
    "uri": "/",
    "method": "GET",
    "accept": "text/plain"
  }
}
```
Task Input using an absolute URL

```json
{
  "http_request": {
    "uri": "http://example.com/",
    "method": "GET",
    "accept": "text/plain"
  }
}
```

The task is marked as ```FAILED``` if the request cannot be completed or the remote server returns non successful status code. 

!!!note
	HTTP task currently only supports Content-Type as application/json and is able to parse the text as well as JSON response.  XML input/output is currently not supported.  However, if the response cannot be parsed as JSON or Text, a string representation is stored as a text value.


## Sub Workflow
Sub Workflow task allows for nesting a workflow within another workflow.

**Parameters:**

|name|type|description|
|---|---|---|
| subWorkflowParam | Map[String, Any] | See below |

**subWorkflowParam**

|name|type|description|
|---|---|---|
| name | String | Name of the workflow to execute |
| version | Integer | Version of the workflow to execute |
| taskToDomain | Map[String, String] | Allows scheduling the sub workflow's tasks per given mappings. See [Task Domains](conductor/configuration/taskdomains/) for instructions to configure taskDomains. |
| workflowDefinition | [WorkflowDefinition](conductor/configuration/workflowdef/) | Allows starting a subworkflow with a dynamic workflow definition. |

**Outputs:**

|name|type|description|
|---|---|---|
| subWorkflowId | String | Subworkflow execution Id generated when running the subworkflow |

**Example**

```json
{
	"name": "sub_workflow_task",
	"taskReferenceName": "sub1",
	"type": "SUB_WORKFLOW",
	"inputParameters": {
		"subWorkflowParam": {
			"name": "deployment_workflow",
			"version": 1,
			"taskToDomain": {
				"*": "mydomain"
			},
			"workflowDefinition": {
				"name": "deployment_workflow",
				"description": "Deploys to CDN",
				"version": 1,
				"tasks": [{
					"name": "deploy",
					"taskReferenceName": "d1",
					"type": "SIMPLE",
					"inputParameters": {
						"fileLocation": "${workflow.input.encodeLocation}"
					}
				}],
				"outputParameters": {
					"cdn_url": "${d1.output.location}"
				},
				"failureWorkflow": "cleanup_encode_resources",
				"restartable": true,
				"workflowStatusListenerEnabled": true,
				"schemaVersion": 2
			}
		},
		"anythingelse": "value"
	}
}
```

When executed, a ```deployment_workflow``` is executed with its inputs parameters set
to the inputParameters of the ```sub_workflow_task``` and the workflow definition specified.  
The task is marked as completed upon the completion of the spawned workflow. 
If the sub-workflow is terminated or fails the task is marked as failure and retried if configured. 


## Fork

Fork is used to schedule parallel set of tasks, specified by ```"type":"FORK_JOIN"```.

**Parameters:**

|name|description|
|---|---|
| forkTasks |A list of list of tasks.  Each sublist is scheduled to be executed in parallel.  However, tasks within the sublists are scheduled in a serial fashion.|

**Example**

```json
[
    {
        "name": "fork_join",
        "taskReferenceName": "forkx",
        "type": "FORK_JOIN",
        "forkTasks": [
          [
            {
              "name": "task_10",
              "taskReferenceName": "task_A",
              "type": "SIMPLE"
            },
            {
              "name": "task_11",
              "taskReferenceName": "task_B",
              "type": "SIMPLE"
            }
          ],
          [
            {
              "name": "task_21",
              "taskReferenceName": "task_Y",
              "type": "SIMPLE"
            },
            {
              "name": "task_22",
              "taskReferenceName": "task_Z",
              "type": "SIMPLE"
            }
          ]
        ]
    },
    {
        "name": "join",
        "taskReferenceName": "join2",
        "type": "JOIN",
        "joinOn": [
          "task_B",
          "task_Z"
        ]
    }
]

```

When executed, _task_A_ and _task_Y_ are scheduled to be executed at the same time.

!!! Note "Fork and Join"
	**A Join task MUST follow FORK_JOIN**
	
	Workflow definition MUST include a Join task definition followed by FORK_JOIN task. Forked task can be a Sub Workflow, allowing for more complex execution flows.


## Dynamic Fork
A dynamic fork is same as FORK_JOIN task.  Except that the list of tasks to be forked is provided at runtime using task's input.  Useful when number of tasks to be forked is not fixed and varies based on the input.

|name|description|
|---|---|
| dynamicForkTasksParam |Name of the parameter that contains list of workflow task configuration to be executed in parallel|
|dynamicForkTasksInputParamName|Name of the parameter whose value should be a map with key as forked task's reference name and value as input the forked task|

**Example**

```json
{
  "inputParameters": {
     "dynamicTasks": "${taskA.output.dynamicTasksJSON}",
     "dynamicTasksInput": "${taskA.output.dynamicTasksInputJSON}"
  },
  "type": "FORK_JOIN_DYNAMIC",
  "dynamicForkTasksParam": "dynamicTasks",
  "dynamicForkTasksInputParamName": "dynamicTasksInput"
}
```
Consider **taskA**'s output as:

```json
{
  "dynamicTasksInputJSON": {
    "forkedTask1": {
      "width": 100,
      "height": 100,
      "params": {
        "recipe": "jpg"
      }
    },
    "forkedTask2": {
      "width": 200,
      "height": 200,
      "params": {
        "recipe": "jpg"
      }
    }
  },
  "dynamicTasksJSON": [
    {
      "name": "encode_task",
      "taskReferenceName": "forkedTask1",
      "type": "SIMPLE"
    },
    {
      "name": "encode_task",
      "taskReferenceName": "forkedTask2",
      "type": "SIMPLE"
    }
  ]
}
```
When executed, the dynamic fork task will schedule two parallel task of type "encode_task" with reference names "forkedTask1" and "forkedTask2" and inputs as specified by _ dynamicTasksInputJSON_

!!! Note "Dynamic Fork and Join"
	**A Join task MUST follow FORK_JOIN_DYNAMIC**
	
	Workflow definition MUST include a Join task definition followed by FORK_JOIN_DYNAMIC task.  However, given the dynamic nature of the task, no joinOn parameters are required for this Join.  The join will wait for ALL the forked branches to complete before completing.
	
	Unlike FORK, which can execute parallel flows with each fork executing a series of tasks in  sequence, FORK_JOIN_DYNAMIC is limited to only one task per fork.  However, forked task can be a Sub Workflow, allowing for more complex execution flows.


## Join
Join task is used to wait for completion of one or more tasks spawned by fork tasks.

**Parameters:**

|name|description|
|---|---|
| joinOn |List of task reference name, for which the JOIN will wait for completion.|


**Example**

``` json
{
	"joinOn": ["taskRef1", "taskRef3"]
}
```

**Join Task Output**
Fork task's output will be a JSON object with key being the task reference name and value as the output of the fork task.


## Exclusive Join
Exclusive Join task helps capture Task output from Decision Task's flow.

For example, If we have a Workflow with T1 -> [Decision: T2/T3] -> EJ, then based on the decision, Exclusive Join (EJ) will produce the output from T2 or T3. I.e What ever is the output of one of T2/T3 will be available to downstream tasks through Exclusive Join task.

If Decision Task takes True/False as cases, then:

- True: T1 -> T2 -> EJ; EJ will have T2's output.
- False: T1 -> T3 -> EJ; EJ will have T3's output.
- Undefined: T1 -> EJ; EJ will have T1's output.



**Parameters:**

|name|description|
|---|---|
| joinOn |List of task reference names, which the EXCLUSIVE_JOIN will lookout for to capture output. From above example, this could be ["T2", "T3"]|
|defaultExclusiveJoinTask|Task reference name, whose output should be used incase the decision case is undefined. From above example, this could be ["T1"]|


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
|---|---|---|
|loopCondition|String|Condition to be evaluated after every iteration. This is a Javascript expression, evaluated using the Nashorn engine. If an exception occurs during evaluation, the DO_WHILE task is set to FAILED_WITH_TERMINAL_ERROR.|
|loopOver|List[Task]|List of tasks that needs to be executed as long as the condition is true.|

**Outputs:**

|name|type|description|
|---|---|---|
|iteration|Integer|Iteration number: the current one while executing; the final one once the loop is finished|
|`i`|Map[String, Any]|Iteration number as a string, mapped to the task references names and their output.|
|*|Any|Any state can be stored here if the `loopCondition` does so. For example `storage` will exist if `loopCondition` is `if ($.LoopTask['iteration'] <= 10) {$.LoopTask.storage = 3; true } else {false}`|

**Example**

The following definition:
```json
{
    "name": "Loop Task",
    "taskReferenceName": "LoopTask",
    "type": "DO_WHILE",
    "inputParameters": {
      "value": "${workflow.input.value}"
    },
    "loopCondition": "if ( ($.LoopTask['iteration'] < $.value ) || ( $.first_task['response']['body'] > 10)) { false; } else { true; }",
    "loopOver": [
        {
            "name": "first task",
            "taskReferenceName": "first_task",
            "inputParameters": {
                "http_request": {
                    "uri": "http://localhost:8082",
                    "method": "POST"
                }
            },
            "type": "HTTP"
        },{
            "name": "second task",
            "taskReferenceName": "second_task",
            "inputParameters": {
                "http_request": {
                    "uri": "http://localhost:8082",
                    "method": "POST"
                }
            },
            "type": "HTTP"
        }
    ],
    "startDelay": 0,
    "optional": false
}
```

will produce the following execution, assuming 3 executions occurred (alongside `first_task__1`, `first_task__2`, `first_task__3`,
`second_task__1`, `second_task__2` and `second_task__3`):

```json
{
    "taskType": "DO_WHILE",
    "outputData": {
        "iteration": 3,
        "1": {
            "first_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            },
            "second_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            }
        },
        "2": {
            "first_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            },
            "second_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            }
        },
        "3": {
            "first_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            },
            "second_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            }
        }
    }
}
```

## JSON JQ Transform Task

JSON JQ Transform task allows transforming a JSON input to another JSON structure using a query expression.

Check the [Jq manual](https://stedolan.github.io/jq/manual/v1.5/), and the [Jq playground](https://jqplay.org/)
for more information.

Limitations:
 - The java implementation support most, but not all jq functions. See [the lib](https://github.com/eiiches/jackson-jq) for details.

**Parameters:**

|name|type|description|
|---|---|---|
|queryExpression|String|JQ query expression. Input is the entire `inputParameters` object.|

**Outputs:**

|name|type|description|
|---|---|---|
|result|Any|First result returned by the jq expression|
|resultList|List[Any]|List of all results returned by the jq expression|
|error|String|Error, if the query throws an error.|

**Example**

The following definition:
```json
{
    "name": "jq_1",
    "taskReferenceName": "jq_1",
    "type": "JSON_JQ_TRANSFORM",
    "inputParameters": {
    "in1": {
      "arr": [ "a", "b" ]
    },
    "in2": {
      "arr": [ "c", "d" ]
    },
    "queryExpression": "{ out: (.in1.arr + .in2.arr) }"
    }
}
```

will produce the following execution:

```json
{
    "name": "jq_1",
    "type": "task-execution",
    "taskReferenceName": "jq_1",
    "taskType": "JSON_JQ_TRANSFORM",
    "inputData": {
        "in1": {
          "arr": [ "a", "b" ]
        },
        "in2": {
          "arr": [ "c", "d" ]
        },
        "queryExpression": "{ out: (.in1.arr + .in2.arr) }"
    },
    "outputData": {
        "result": {
            "out": ["a","b","c","d"]
        },
        "resultList": [
            {
                "out": ["a","b","c","d"]
            }
        ]
    }
}
```


## Set Variable Task

This task allows to set workflow variables by creating or updating them with new values.
Variables can be initialized in the workflow definition as well as during the workflow run.
Once a variable was initialized it can be read or overwritten with a new value by any other task.

!!!warning
	There is a hard barrier for variables payload size in KB defined in the JVM system properties (`conductor.max.workflow.variables.payload.threshold.kb`) the default value is `256`. Passing this barrier will fail the task and the workflow.

**Parameters:**

The parameters for this task are the variable names with their respective values.

**Example**
```json
{
  "type": "SET_VARIABLE",
  "name": "set_stage_start",
  "taskReferenceName": "set_stage_start",
  "inputParameters": {
    "stage": "START"
  }
}
```

Later in that workflow, the variable can be referenced by `"${workflow.variables.stage}"`
