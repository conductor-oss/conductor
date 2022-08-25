# Workflow Definition

## What are Workflows?

At a high level, a workflow is the Conductor primitive that encompasses the definition and flow of your business logic.
A workflow is a collection (graph) of tasks and sub-workflows. A workflow definition specifies the order of execution of
these [Tasks](taskdef.md). It also specifies how data/state is passed from one task to the other (using the
input/output parameters). These are then combined to give you the final result. This orchestration of Tasks can
happen in a hybrid ecosystem that includes microservices, serverless functions, and monolithic applications. They can
also span across any public cloud and on-premise data center footprints. In addition, the orchestration of tasks can be
across any programming language since Conductor is also language agnostic.

One key benefit of this approach is that you can build a complex application using simple and granular tasks that do not
need to be aware of or keep track of the state of your application's execution flow. Conductor keeps track of the state,
calls tasks in the right order (sequentially or in parallel, as defined by you), retry calls if needed, handle failure
scenarios gracefully, and outputs the final result.

Leveraging workflows in Conductor enables developers to truly focus on their core mission - building their application
code in the languages of their choice. Conductor does the heavy lifting associated with ensuring high
reliability, transactional consistency, and long durability of their workflows. Simply put, wherever your application's
component lives and whichever languages they were written in, you can build a workflow in Conductor to orchestrate their
execution in a reliable & scalable manner.

## What does a Workflow look like?

Let's start with a basic workflow and understand what are the different aspects of it. In particular, we will talk about
two stages of a workflow, *defining* a workflow and *executing* a workflow

### Simple Workflow Example

Assume your business logic is to simply to get some shipping information and then do the shipping. You start by
logically partitioning them into two tasks:

* **shipping_info**
* **shipping_task**

First we would build these two task definitions. Let's assume that ```shipping info``` takes an account number, and returns a name and address.

**Example**
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
    "trackingNumber": "${shipping_task_ref.output.trackinNumber}"
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

The mail_a_box workflow has 2 tasks:
 1. The first task takes the provided account number, and outputs an address.  
 2. The 2nd task takes the address info and generates a shipping label.
 
 Upon completion of the 2 tasks, the workflow outputs the tracking number generated in the 2nd task.  If the workflow fails, a second workflow named ```shipping_issues``` is run.

## Fields in a Workflow

| Field                         | Description                                                                                                                              | Notes                                                                                             |
|:------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------|
| name                          | Name of the workflow                                                                                                                     ||
| description                   | Description of the workflow                                                                                                              | optional                                                                                          |
| version                       | Numeric field used to identify the version of the schema.  Use incrementing numbers                                                      | When starting a workflow execution, if not specified, the definition with highest version is used |
| tasks                         | An array of task definitions.                                                                                                            | [Task properties](#tasks-within-workflow)                                                         |
| inputParameters               | List of input parameters. Used for documenting the required inputs to workflow                                                           | optional                                                                                          |
| inputTemplate                 | Default input values. See [Using inputTemplate](#using-inputtemplate)                                                                    | optional                                                                                          |
| outputParameters              | JSON template used to generate the output of the workflow                                                                                | If not specified, the output is defined as the output of the _last_ executed task                 |
| failureWorkflow               | String; Workflow to be run on current Workflow failure. Useful for cleanup or post actions on failure.                                   | optional                                                                                          |
| schemaVersion                 | Current Conductor Schema version. schemaVersion 1 is discontinued.                                                                       | Must be 2                                                                                         |
| restartable                   | Boolean flag to allow Workflow restarts                                                                                                  | defaults to true                                                                                  |
| workflowStatusListenerEnabled | If true, every workflow that gets terminated or completed will send a notification. See [workflow notifictions](#workflow-notifications) | optional (false by default)                                                                       |

## Tasks within Workflow
```tasks``` property in a workflow execution defines an array of tasks to be executed in that order.

| Field             | Description                                                                                                                                    | Notes                                                                   |
|:------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------|
| name              | Name of the task. MUST be registered as a task with Conductor before starting the workflow                                                     ||
| taskReferenceName | Alias used to refer the task within the workflow.  MUST be unique within workflow.                                                             ||
| type              | Type of task. SIMPLE for tasks executed by remote workers, or one of the system task types                                                     ||
| description       | Description of the task                                                                                                                        | optional                                                                |
| optional          | true or false.  When set to true - workflow continues even if the task fails.  The status of the task is reflected as `COMPLETED_WITH_ERRORS` | Defaults to `false`                                                     |
| inputParameters   | JSON template that defines the input given to the task                                                                                         | See [Wiring Inputs and Outputs](#wiring-inputs-and-outputs) for details |
| domain            | See [Task Domains](/configuration/taskdomains.html) for more information.                                                                 | optional                                                                |

In addition to these parameters, System Tasks have their own parameters. Checkout [System Tasks](/configuration/systask.html) for more information.

## Wiring Inputs and Outputs

Workflows are supplied inputs by client when a new execution is triggered. 
Workflow input is a JSON payload that is available via ```${workflow.input...}``` expressions. 

Each task in the workflow is given input based on the ```inputParameters``` template configured in workflow definition.  ```inputParameters``` is a JSON fragment with value containing parameters for mapping values from input or output of a workflow or another task during the execution.

Syntax for mapping the values follows the pattern as: 

__${SOURCE.input/output.JSONPath}__

| field        | description                                                              |
|--------------|--------------------------------------------------------------------------|
| SOURCE       | can be either "workflow" or any of the task reference name               |
| input/output | refers to either the input or output of the source                       |
| JSONPath     | JSON path expression to extract JSON fragment from source's input/output |


!!! note "JSON Path Support"
	Conductor supports [JSONPath](http://goessner.net/articles/JsonPath/) specification and uses Java implementation from [here](https://github.com/jayway/JsonPath).

!!! note "Escaping expressions"
	To escape an expression, prefix it with an extra _$_ character (ex.: ```$${workflow.input...}```).

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

### Using inputTemplate

* `inputTemplate` allows to define default values, which can be overridden by values provided in Workflow.
* Eg: In your Workflow Definition, you can define your inputTemplate as:

```json
"inputTemplate": {
    "url": "https://some_url:7004"
}
```

And `url` would be `https://some_url:7004` if no `url` was provided as input to your workflow.

## Workflow notifications

Conductor can be configured to publish notifications to external systems upon completion/termination of workflows. See [extending conductor](/extend.html) for details.
