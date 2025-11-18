# Workflow Definition

The Workflow Definition contains all the information necessary to define the behavior of a workflow. The most important part of this definition is the `tasks` property, which is an array of [**Task Configurations**](#task-configurations). 

## Workflow Properties
| Field                         | Type                             | Description                                                                                                                     | Notes                                                                                             |
| :---------------------------- | :------------------------------- | :------------------------------------------------------------------------------------------------------------------------------ | :------------------------------------------------------------------------------------------------ |
| name                          | string                           | Name of the workflow                                                                                                            |                                                                                                   |
| description                   | string                           | Description of the workflow                                                                                                     | Optional                                                                                          |
| version                       | number                           | Numeric field used to identify the version of the schema. Use incrementing numbers.                                             | When starting a workflow execution, if not specified, the definition with highest version is used |
| tasks                         | array of object(s)               | An array of task configurations. [Details](#task-configurations)                                                                |                                                                                                   |
| inputParameters               | array of string(s)               | List of input parameters. Used for documenting the required inputs to workflow                                                  | Optional.                                                                                         |
| outputParameters              | object                           | JSON template used to generate the output of the workflow                                                                       | If not specified, the output is defined as the output of the _last_ executed task                 |
| inputTemplate                 | object                           | Default input values. See [Using inputTemplate](#default-input-with-inputtemplate)                                              | Optional.                                                                                         |
| failureWorkflow               | string                           | Workflow to be run on current Workflow failure. Useful for cleanup or post actions on failure. [Explanation](#failure-workflow) | Optional.                                                                                         |
| schemaVersion                 | number                           | Current Conductor Schema version. schemaVersion 1 is discontinued.                                                              | Must be 2                                                                                         |
| restartable                   | boolean                          | Flag to allow Workflow restarts                                                                                                 | Defaults to true                                                                                  |
| workflowStatusListenerEnabled | boolean                          | Enable status callback. [Explanation](#workflow-status-listener)                                                                | Defaults to false                                                                                 |
| ownerEmail                    | string                           | Email address of the team that owns the workflow                                                                                | Required                                                                                          |
| timeoutSeconds                | number                           | The timeout in seconds after which the workflow will be marked as `TIMED_OUT` if it hasn't been moved to a terminal state       | No timeouts if set to 0                                                                           |
| timeoutPolicy                 | string ([enum](#timeout-policy)) | Workflow's timeout policy                                                                                                       | Defaults to `TIME_OUT_WF`                                                                         |

### Failure Workflow

The failure workflow gets the _original failed workflow’s input_ along with 3 additional items,

* `workflowId` - The id of the failed workflow which triggered the failure workflow.
* `reason` - A string containing the reason for workflow failure.
* `failureStatus` - A string status representation of the failed workflow.
* `failureTaskId` - The id of the failed task of the workflow that triggered the failure workflow.

### Timeout Policy

* TIME_OUT_WF: Workflow is marked as TIMED_OUT and terminated
* ALERT_ONLY: Registers a counter (workflow_failure with status tag set to `TIMED_OUT`)

### Workflow Status Listener
Setting the `workflowStatusListenerEnabled` field in your Workflow Definition to `true` enables notifications.

To add a custom implementation of the Workflow Status Listener. Refer to [this](../../advanced/extend.md#workflow-status-listener) .

The listener can be implemented in such a way as to either send a notification to an external system or to send an event on the conductor queue to complete/fail another task in another workflow as described [here](../../configuration/eventhandlers.md).

### Default Input with `inputTemplate`

* `inputTemplate` allows you to define default input values, which can optionally be overridden at runtime (when the workflow is invoked).
* Eg: In your Workflow Definition, you can define your inputTemplate as:

```json
"inputTemplate": {
    "url": "https://some_url:7004"
}
```

And `url` would be `https://some_url:7004` if no `url` was provided as input to your workflow.




## Task Configurations

The `tasks` property in a Workflow Definition defines an array of *Task Configurations*. This is the blueprint for the workflow. Task Configurations can reference different types of Tasks.

* Simple Tasks
* System Tasks
* Operators

Note: Task Configuration should not be confused with **Task Definitions**, which are used to register SIMPLE (worker based) tasks.

| Field             | Type    | Description                                                                                                                                    | Notes                                                                 |
| :---------------- | :------ | :--------------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------- |
| name              | string  | Name of the task. MUST be registered as a Task Type with Conductor before starting workflow                                                    |                                                                       |
| taskReferenceName | string  | Alias used to refer the task within the workflow.  MUST be unique within workflow.                                                             |                                                                       |
| type              | string  | Type of task. SIMPLE for tasks executed by remote workers, or one of the system task types                                                     |                                                                       |
| description       | string  | Description of the task                                                                                                                        | optional                                                              |
| optional          | boolean | true  or false.  When set to true - workflow continues even if the task fails.  The status of the task is reflected as `COMPLETED_WITH_ERRORS` | Defaults to `false`                                                   |
| inputParameters   | object  | JSON template that defines the input given to the task. Only one of `inputParameters` or `inputExpression` can be used in a task.              | See [Using Expressions](#using-expressions) for details |
| inputExpression   | object  | JSONPath expression that defines the input given to the task. Only one of `inputParameters` or `inputExpression` can be used in a task.        | See [Using Expressions](#using-expressions) for details |
| asyncComplete     | boolean | `false` to mark status COMPLETED upon execution; `true` to keep the task IN_PROGRESS and wait for an external event to complete it.            | Defaults to `false`                                                   |
| startDelay        | number  | Time in seconds to wait before making the task available to be polled by a worker.                                                             | Defaults to 0.                                                        |


In addition to these parameters, System Tasks have their own parameters. Check out [System Tasks](systemtasks/index.md) for more information.

### Using Expressions
Each executed task is given an input based on the `inputParameters` template or the `inputExpression` configured in the task configuration. Only one of `inputParameters` or `inputExpression` can be used in a task.

#### inputParameters
`inputParameters` can use JSONPath **expressions** to extract values out of the workflow input and other tasks in the workflow.

For example, workflows are supplied an `input` by the client/caller when a new execution is triggered. The workflow `input` is available via an *expression* of the form `${workflow.input...}`. Likewise, the `input` and `output` data of a previously executed task can also be extracted using an *expression* for use in the `inputParameters` of a subsequent task.

Generally, `inputParameters` can use *expressions* of the following syntax:

> `${SOURCE.input/output.JSONPath}`

| Field        | Description                                                              |
| ------------ | ------------------------------------------------------------------------ |
| SOURCE       | Can be either `"workflow"` or the reference name of any task             |
| input/output | Refers to either the input or output of the source                       |
| JSONPath     | JSON path expression to extract JSON fragment from source's input/output |


!!! note "JSON Path Support"
    Conductor supports [JSONPath](http://goessner.net/articles/JsonPath/) specification and uses Java implementation from [here](https://github.com/jayway/JsonPath).

!!! note "Escaping expressions"
    To escape an expression, prefix it with an extra _$_ character (ex.: ```$${workflow.input...}```).

#### inputExpression

`inputExpression` can be used to select an entire object from the workflow input, or the output of another task. The field supports all [definite](https://github.com/json-path/JsonPath#what-is-returned-when) JSONPath expressions.

The syntax for mapping values in `inputExpression` follows the pattern,

> `SOURCE.input/output.JSONPath`

**NOTE:** The ```inputExpression``` field does not require the expression to be wrapped in `${}`.

See [example](#example-3-inputexpression) below.

## Examples

### Example 1 - A Basic Workflow Definition 

Assume your business logic is to simply to get some shipping information and then do the shipping. You start by
logically partitioning them into two tasks:

 1. *shipping_info* - The first task takes the provided account number, and outputs an address.  
 2. *shipping_task* - The 2nd task takes the address info and generates a shipping label.

We can configure these two tasks in the `tasks` array of our Workflow Definition. Let's assume that ```shipping info``` takes an account number, and returns a name and address.

```json
{
  "name": "mail_a_box",
  "description": "shipping Workflow",
  "version": 1,
  "tasks": [
    {
      "name": "shipping_info",
      "taskReferenceName": "shipping_info_ref",
      "inputParameters": {
        "account": "${workflow.input.accountNumber}"
      },
      "type": "SIMPLE"
    },
    {
      "name": "shipping_task",
      "taskReferenceName": "shipping_task_ref",
      "inputParameters": {
        "name": "${shipping_info_ref.output.name}",
		"streetAddress": "${shipping_info_ref.output.streetAddress}",
		"city": "${shipping_info_ref.output.city}",
		"state": "${shipping_info_ref.output.state}",
		"zipcode": "${shipping_info_ref.output.zipcode}",
      },
      "type": "SIMPLE"
    }
  ],
  "outputParameters": {
    "trackingNumber": "${shipping_task_ref.output.trackingNumber}"
  },
  "failureWorkflow": "shipping_issues",
  "restartable": true,
  "workflowStatusListenerEnabled": true,
  "ownerEmail": "conductor@example.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 0,
  "variables": {},
  "inputTemplate": {}
}
```

Upon completion of the 2 tasks, the workflow outputs the tracking number generated in the 2nd task.  If the workflow fails, a second workflow named ```shipping_issues``` is run.


### Example 2 - Task Configuration
Consider a task `http_task` with input configured to use input/output parameters from workflow and a task named `loc_task`.

```json
{
  "name": "encode_workflow",
  "description": "Encode movie.",
  "version": 1,
  "inputParameters": [
    "movieId", "fileLocation", "recipe"
  ],
  "tasks": [
    {
      "name": "loc_task",
      "taskReferenceName": "loc_task_ref",
      "taskType": "SIMPLE",
      ...      
    },    
    {
      "name": "http_task",
      "taskReferenceName": "http_task_ref",
      "taskType": "HTTP",
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
  ],
  "ownerEmail": "conductor@example.com",
  "variables": {},
  "inputTemplate": {}
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

When scheduling the task, Conductor will merge the values from workflow input and `loc_task`'s output and create the input to the `http_task` as follows:

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

### Example 3 - inputExpression
Given the following task configuration:
```json
{
  "name": "loc_task",
  "taskReferenceName": "loc_task_ref",
  "taskType": "SIMPLE",
  "inputExpression": {
    "expression": "workflow.input",
    "type": "JSON_PATH"
  }  
}
```

When the workflow is invoked with the following _workflow input_
```json
{
  "movieId": "movie_123",
  "fileLocation":"s3://moviebucket/file123",
  "recipe":"png"
}
```

When the task `loc_task` is scheduled, the entire workflow input object will be passed in as the task input:
```json
{
  "movieId": "movie_123",
  "fileLocation":"s3://moviebucket/file123",
  "recipe":"png"
}
```
