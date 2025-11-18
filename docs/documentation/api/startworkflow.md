# Start Workflow API

## API Parameters
When starting a Workflow execution with a registered definition, `{{ api_prefix }}/workflow` accepts following parameters in the `POST` payload:

| Field                           | Description                                                                                                                               | Notes                                                                                                   |
|:--------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------|
| name                            | Name of the Workflow. MUST be registered with Conductor before starting workflow                                                          |                                                                                                         |
| version                         | Workflow version                                                                                                                          | defaults to latest available version                                                                    |
| input                           | JSON object with key value params, that can be used by downstream tasks                                                                   | See [Wiring Inputs and Outputs](../configuration/workflowdef/index.md#wiring-inputs-and-outputs) for details |
| correlationId                   | Unique Id that correlates multiple Workflow executions                                                                                    | optional                                                                                                |
| taskToDomain                    | See [Task Domains](taskdomains.md) for more information.                                                   | optional                                                                                                |
| workflowDef                     | An adhoc [Workflow Definition](../configuration/workflowdef/index.md) to run, without registering. See [Dynamic Workflows](#dynamic-workflows). | optional                                                                                                |
| externalInputPayloadStoragePath | This is taken care of by Java client. See [External Payload Storage](../advanced/externalpayloadstorage.md) for more info.                        | optional                                                                                                |
| priority                        | Priority level for the tasks within this workflow execution. Possible values are between 0 - 99.                                          | optional                                                                                                |

## Output
On success, this API returns the ID of the workflow.

## Synchronous Execution

!!! note "Enterprise Feature"
    Synchronous workflow execution (using `waitForCompletion` and `waitForSeconds` parameters) is available in Orkes Conductor (Enterprise) but is not currently implemented in Conductor OSS.

    This feature is planned for implementation in Conductor OSS in 2026. Track progress at [#659](https://github.com/conductor-oss/conductor/issues/659).

Conductor OSS currently executes all workflows **asynchronously**. The start workflow API returns a workflow ID immediately, and you must poll the workflow status separately to check for completion.


## Basic Example

`POST {{ server_host }}{{ api_prefix }}/workflow` with payload body:

```js
{
  "name": "myWorkflow", // Name of the workflow
  "version": 1, // Version
  "correlationId": "corr1", // Correlation Id
  "priority": 1, // Priority
    "input": { // Input Value Map
      "param1": "value1",
      "param2": "value2"
    },
  "taskToDomain": {
	// Task to domain map
  }
}
```

## Dynamic Workflows

If the need arises to run a one-time workflow, and it doesn't make sense to register Task and Workflow definitions in Conductor Server, as it could change dynamically for each execution, dynamic workflow executions can be used.

This enables you to provide a workflow definition embedded with the required task definitions to the Start Workflow Request in the `workflowDef` parameter, avoiding the need to register the blueprints before execution.

**Example:**

Send a `POST` request to `{{ api_prefix }}/workflow` with payload like:
```json
{
  "name": "my_adhoc_unregistered_workflow",
  "workflowDef": {
    "ownerApp": "my_owner_app",
    "ownerEmail": "my_owner_email@test.com",
    "createdBy": "my_username",
    "name": "my_adhoc_unregistered_workflow",
    "description": "Test Workflow setup",
    "version": 1,
    "tasks": [
    	{
	        "name": "fetch_data",
	        "type": "HTTP",
	        "taskReferenceName": "fetch_data",
	        "inputParameters": {
	          "http_request": {
	            "connectionTimeOut": "3600",
	            "readTimeOut": "3600",
	            "uri": "${workflow.input.uri}",
	            "method": "GET",
	            "accept": "application/json",
	            "content-Type": "application/json",
	            "headers": {
	            }
	          }
	        },
	        "taskDefinition": {
	            "name": "fetch_data",
			    "retryCount": 0,
			    "timeoutSeconds": 3600,
			    "timeoutPolicy": "TIME_OUT_WF",
			    "retryLogic": "FIXED",
			    "retryDelaySeconds": 0,
			    "responseTimeoutSeconds": 3000
	        }
	    }
    ],
    "outputParameters": {
    }
  },
  "input": {
    "uri": "http://www.google.com"
  }
}
```

!!! Note
    If the `taskDefinition` is defined with Metadata API, it doesn't have to be added in above dynamic workflow definition.
