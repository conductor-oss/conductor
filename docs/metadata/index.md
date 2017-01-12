# Task Definition
Conductor maintains a registry of worker task types.  A task type MUST be registered before using in a workflow.

**Example**
``` json
{
  "name": "encode_task",
  "retryCount": 3,
  "timeoutSeconds": 1200,
  "inputKeys": [
    "sourceRequestId",
    "qcElementType"
  ],
  "outputKeys": [
    "state",
    "skipped",
    "result"
  ],
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 600,
  "responseTimeoutSeconds": 3600
}
```

|field|description|Notes|
|---|---|---|
|name|Task Type|Unique|
|retryCount|No. of retries to attempt when a task is marked as failure||
|retryLogic|Mechanism for the retries|see possible values below|
|timeoutSeconds|Time in milliseconds, after which the task is marked as TIMED_OUT if not completed after transiting to ```IN_PROGRESS``` status|No timeouts if set to 0|
|timeoutPolicy|Task's timeout policy|see possible values below|
|responseTimeoutSeconds|if greater than 0, the task is rescheduled if not updated with a status after this time.  Useful when the worker polls for the task but fails to complete due to errors/network failure.
||
|outputKeys|Set of keys of task's output.  Used for documenting task's output||

**Retry Logic**

* FIXED : Reschedule the task afer the ```retryDelaySeconds```
* EXPONENTIAL_BACKOFF : reschedule after ```retryDelaySeconds  * attempNo```
 
**Timeout Policy**

* RETRY : Retries the task again
* TIME_OUT_WF : Workflow is marked as TIMED_OUT and terminated
* ALERT_ONLY : Registers a counter (task_timeout)

# Workflow Definition
Workflows are define using a JSON based DSL.

**Example**
```json
{
  "name": "encode_and_deploy",
  "description": "Encodes a file and deploys to CDN",
  "version": 1,
  "tasks": [
    {
      "name": "encode",
      "taskReferenceName": "encode",
      "type": "SIMPLE",
      "inputParameters": {
        "fileLocation": "${workflow.input.fileLocation}"
      }
    },
    {
      "name": "deploy",
      "taskReferenceName": "d1",
      "type": "SIMPLE",
      "inputParameters": {
        "fileLocation": "${encode.output.encodeLocation}"
      }

    }
  ],
  "outputParameters": {
    "cdn_url": "${d1.output.location}"
  },
  "schemaVersion": 2
}
```

|field|description|Notes|
|:-----|:---|:---|
|name|Name of the workflow||
|description|Descriptive name of the workflow||
|version|Numeric field used to identify the version of the schema.  Use incrementing numbers|When starting a workflow execution, if not specified, the definition with highest version is used|
|tasks|An array of task defintions as described below.||
|outputParameters|JSON template used to generate the output of the workflow|If not specified, the output is defined as the output of the _last_ executed task|
|inputParameters|List of input parameters.  Used for documenting the required inputs to workflow|optional|

## Tasks within Workflow
```tasks``` property in a workflow defines an array of tasks to be executed in that order.
Below are the mandatory minimum parameters required for each task:

|field|description|Notes|
|:-----|:---|:---|
|name|Name of the task.  MUST be registered as a task type with Conductor before starting workflow||
|taskReferenceName|Alias used to refer the task within the workflow.  MUST be unique.||
|type|Type of task. SIMPLE for tasks executed by remote workers, or one of the system task types||
|inputParameters|JSON template that defines the input given to the task|See "wiring inputs and outputs" for details|

In addition to these paramters, additional parameters speciific to the task type are required as documented [here](/metadata/systask/)

# Wiring Inputs and Outputs

Workflows are supplied inputs by client when a new execution is triggered. 
Workflow input is a JSON payload that is available via ```${workflow.input...}``` expressions.

Each task in the workflow is given input based on the ```inputParameters``` template configured in workflow definition.  ```inputParameters``` is a JSON fragment with value containing parameters for mapping values from input or output of a workflow or another task during the execution.

Syntax for mapping the values follows the pattern as: 

__${SOURCE.input/output.JSONPath}__

|-|-|
|------|---|
|SOURCE|can be either "workflow" or reference name of any of the task|
|input/output|refers to either the input or output of the source|
|JSONPath|JSON path expression to extract JSON fragment from source's input/output|


!!! note "JSON Path Support"
	Conductor supports [JSONPath](http://goessner.net/articles/JsonPath/) specification and uses Java implementaion from [here](https://github.com/jayway/JsonPath).

**Example**

Consider a task with input configured to use input/output parameters from workflow and a task named __loc_task__.

```json
{
  "inputParameters": {
    "movieId": "${workflow.input.movieId}",
    "url": "${workflow.input.fileLocation}",
    "lang": "${loc_task.output.languages[0]}",
    "http_request": {
      "method": "POST",
      "url": "http://example.com/${loc_task.output.fileId}/encode",
      "body": {
        "recipe": "${workflow.input.recipe}",
        "params": {
          "width": 100,
          "height": 100
        }
      },
      "headers": [
        {
          "Accept": "application/json"
        },
        {
          "Content-Type": "application/json"
        }
      ]
    }
  }
}
```

Consider the following as the _workflow input_

```json
{
  "movieId": "movie_123",
  "fileLocation":"s3://moviebucket/file123",
  "recipe":"png"
}
```
And the output of the _loc_task_ as the following;

```json
{
  "fileId": "file_xxx_yyy_zzz",
  "languages": ["en","ja","es"]
}
```

When scheduling the task, Conductor will merge the values from workflow input and loc_tak's output and create the input to the task as follows:

```json
{
  "movieId": "movie_123",
  "url": "s3://moviebucket/file123",
  "lang": "en",
  "http_request": {
    "method": "POST",
    "url": "http://example.com/file_xxx_yyy_zzz/encode",
    "body": {
      "recipe": "png",
      "params": {
        "width": 100,
        "height": 100
      }
    },
    "headers": [
      {
        "Accept": "application/json"
      },
      {
        "Content-Type": "application/json"
      }
    ]
  }
}
```



