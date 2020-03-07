## Workflow Definition
Workflows are defined using a JSON based DSL.

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
  "failureWorkflow": "cleanup_encode_resources",
  "restartable": true,
  "workflowStatusListenerEnabled": true,
  "schemaVersion": 2
}
```

|field|description|Notes|
|:-----|:---|:---|
|name|Name of the workflow||
|description|Description of the workflow|optional|
|version|Numeric field used to identify the version of the schema.  Use incrementing numbers|When starting a workflow execution, if not specified, the definition with highest version is used|
|tasks|An array of task definitions as described below.||
|inputParameters|List of input parameters. Used for documenting the required inputs to workflow|optional|
|outputParameters|JSON template used to generate the output of the workflow|If not specified, the output is defined as the output of the _last_ executed task|
|failureWorkflow|String; Workflow to be run on current Workflow failure. Useful for cleanup or post actions on failure.|optional|
|schemaVersion|Current Conductor Schema version. schemaVersion 1 is discontinued.|Must be 2|
|restartable|Boolean flag to allow Workflow restarts|defaults to true|
|workflowStatusListenerEnabled|If true, every workflow that gets terminated or completed will send a notification. See [below](#workflow-notifications)|optional (false by default)|

### Tasks within Workflow
```tasks``` property in a workflow execution defines an array of tasks to be executed in that order.

|field|description|Notes|
|:-----|:---|:---|
|name|Name of the task. MUST be registered as a task with Conductor before starting the workflow||
|taskReferenceName|Alias used to refer the task within the workflow.  MUST be unique within workflow.||
|type|Type of task. SIMPLE for tasks executed by remote workers, or one of the system task types||
|description|Description of the task|optional|
|optional|true  or false.  When set to true - workflow continues even if the task fails.  The status of the task is reflected as `COMPLETED_WITH_ERRORS`|Defaults to `false`|
|inputParameters|JSON template that defines the input given to the task|See [Wiring Inputs and Outputs](#wiring-inputs-and-outputs) for details|
|domain|See [Task Domains](/conductor/configuration/taskdomains) for more information.|optional|

In addition to these parameters, System Tasks have their own parameters. Checkout [System Tasks](/conductor/configuration/systask/) for more information.

### Wiring Inputs and Outputs

Workflows are supplied inputs by client when a new execution is triggered. 
Workflow input is a JSON payload that is available via ```${workflow.input...}``` expressions.

Each task in the workflow is given input based on the ```inputParameters``` template configured in workflow definition.  ```inputParameters``` is a JSON fragment with value containing parameters for mapping values from input or output of a workflow or another task during the execution.

Syntax for mapping the values follows the pattern as: 

__${SOURCE.input/output.JSONPath}__

|field|description|
|------|---|
|SOURCE|can be either "workflow" or any of the task reference name |
|input/output|refers to either the input or output of the source|
|JSONPath|JSON path expression to extract JSON fragment from source's input/output|


!!! note "JSON Path Support"
	Conductor supports [JSONPath](http://goessner.net/articles/JsonPath/) specification and uses Java implementation from [here](https://github.com/jayway/JsonPath).

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
      "headers": {
        "Accept": "application/json",
        "Content-Type": "application/json"
      }
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

When scheduling the task, Conductor will merge the values from workflow input and loc_task's output and create the input to the task as follows:

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
    "headers": {
    	"Accept": "application/json",
    	"Content-Type": "application/json"
    }
  }
}
```

### Workflow notifications

Conductor can be configured to publish notifications to external systems upon completion/termination of workflows. See [extending conductor](../../extend/#workflow-status-listener) for details.
