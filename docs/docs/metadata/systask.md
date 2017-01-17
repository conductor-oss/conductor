# Dynamic Task

### Parameters:
|name|description|
|---|---|
| dynamicTaskNameParam|Name of the parameter from the task input whose value is used to schedule the task.  e.g. if the value of the parameter is ABC, the next task scheduled is of type 'ABC'.|

### Example
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

# Decision
A decision task is similar to ```case...switch``` statement in a programming langugage.
The task takes 3 parameters:

### Parameters:
|name|description|
|---|---|
|caseValueParam |Name of the parameter in task input whose value will be used as a switch.|
|decisionCases|Map where key is possible values of ```caseValueParam``` with value being list of tasks to be executed.|
|defaultCase|List of tasks to be executed when no matching value if found in decision case (default condition)|

### Example

``` json
{
  "name": "decide_task",
  "taskReferenceName": "decide1",
  "inputParameters": {
    "case_value_param": "${workflow.input.movieType}"
  },
  "type": "DECISION",
  "caseValueParam": "case_value_param",
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

# Fork

Fork is used to schedule parallel set of tasks.

### Parameters:
|name|description|
|---|---|
| forkTasks |A list of list of tasks.  Each sublist is scheduled to be executed in parallel.  However, tasks within the sublists are scheduled in a serial fashion.|

### Example

``` json
{
  "forkTasks": [
    [
      {
        "name": "task11",
        "taskReferenceName": "t11"
      },
      {
        "name": "task12",
        "taskReferenceName": "t12"
      }
    ],
    [
      {
        "name": "task21",
        "taskReferenceName": "t21"
      },
      {
        "name": "task22",
        "taskReferenceName": "t22"
      }
    ]
  ]
}
```
When executed, _task11_ and _task21_ are scheduled to be executed at the same time.

# Dynamic Fork
A dynamic fork is same as FORK_JOIN task.  Except that the list of tasks to be forked is provided at runtime using task's input.  Useful when number of tasks to be forked is not fixed and varies based on the input.

|name|description|
|---|---|
| dynamicForkTasksParam |Name of the parameter that contains list of workflow task configuration to be executed in parallel|
|dynamicForkTasksInputParamName|Name of the parameter whose value should be a map with key as forked task's referece name and value as input the forked task|

###Example

```json
{
  "dynamicTasks": "${taskA.output.dynamicTasksJSON}",
  "dynamicTasksInput": "${taskA.output.dynamicTasksInputJSON}",
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
    "forkedTask12": {
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

!!!warning "Dyanmic Fork and Join"
	**A Join task MUST follow FORK_JOIN_DYNAMIC**
	
	Workflow definition MUST include a Join task definition followed by FORK_JOIN_DYNAMIC task.  However, given the dynamic nature of the task, no joinOn parameters are required for this Join.  The join will wait for ALL the forked branches to complete before completing.
	
	Unlike FORK, which can execute parallel flows with each fork executing a series of tasks in  sequence, FORK_JOIN_DYNAMIC is limited to only one task per fork.  However, forked task can be a Sub Workflow, allowing for more complex execution flows.
	 
# Join
Join task is used to wait for completion of one or more tasks spawned by fork tasks.

### Parameters
|name|description|
|---|---|
| joinOn |List of task reference name, for which the JOIN will wait for completion.|


### Example

``` json
{
	"joinOn": ["taskRef1", "taskRef3"]
}
```

### Join Task Output
Fork task's output will be a JSON object with key being the task reference name and value as the output of the fork task.

# Sub Workflow
Sub Workflow task allows for nesting a workflow within another workflow.

### Parameters
|name|description|
|---|---|
| subWorkflowParam |List of task reference name, for which the JOIN will wait for completion.|

###Example

```json
{
  "name": "sub_workflow_task",
  "taskReferenceName": "sub1",
  "inputParameters": {
    "requestId": "${workflow.input.requestId}",
    "file": "${encode.output.location}"
  },
  "type": "SUB_WORKFLOW",
  "subWorkflowParam": {
    "name": "deployment_workflow",
    "version": 1
  }
}
```
When executed, a ```deployment_workflow``` is executed with two input parameters requestId and _file_.  The task is marked as completed upon the completion of the spawned workflow.  If the sub-workflow is terminated or fails the task is marked as failure and retried if configured. 

# Wait
A wait task is implemented as a gate that remains in ```IN_PROGRESS``` state unless marked as ```COMPLETED``` or ```FAILED``` by an external trigger.
To use a wait task, set the task type as ```WAIT```

### Parameters
None required.

### Exernal Triggers for Wait Task

Task Resource endpoint can be used to update the status of a task to a terminate state. 

Contrib module provides SQS integration where an external system can place a message in a pre-configured queue that the server listens on.  As the messages arrive, they are marked as ```COMPLETED``` or ```FAILED```.  

#### SQS Queues
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

#### Example SQS Payload:

```
{
  "some_key": "valuex",
  "externalId": "{\"taskRefName\":\"TASK_REFERENCE_NAME\",\"workflowId\":\"WORKFLOW_ID\"}"
}
```

# HTTP
An HTTP task is used to make calls to another microservice over HTTP.

### Parameters
The task expects an input parameter named ```http_request``` as part of the task's input with the following details:

|name|description|
|---|---|
| uri |URI for the service.  Can be a partial when using vipAddress or includes the server address.|
|method|HTTP method.  One of the GET, PUT, POST, DELETE, OPTIONS, HEAD|
|accept|Accpet header as required by server.|
|contentType|Content Type - supported types are text/plain, text/html and, application/json|
|headers|A map of additional http headers to be sent along with the request.|
|body|Request body|
|vipAddress|When using discovery based service URLs.|

### HTTP Task Output
|name|description|
|---|---|
|response|JSON body containing the response if one is present|
|headers|Response Headers|
|statusCode|Integer status code|

### Example

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