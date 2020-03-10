## Start Workflow Request

When starting a Workflow execution with a registered definition, Workflow accepts following parameters:

|field|description|Notes|
|:-----|:---|:---|
| name | Name of the Workflow. MUST be registered with Conductor before starting workflow | |
| version | Workflow version | defaults to latest available version |
| input | JSON object with key value params, that can be used by downstream tasks | See [Wiring Inputs and Outputs](../../configuration/workflowdef/#wiring-inputs-and-outputs) for details |
| correlationId | Unique Id that correlates multiple Workflow executions | optional |
| taskToDomain | See [Task Domains](../../configuration/taskdomains/#task-domains) for more information. | optional |
| workflowDef | Provide adhoc Workflow definition to run, without registering. See Dynamic Workflows below. | optional |
| externalInputPayloadStoragePath | This is taken care of by Java client. See [External Payload Storage](../../externalpayloadstorage/) for more info. | optional |
| priority | Priority level for the tasks within this workflow execution. Possible values are between 0 - 99. | optional |

**Example:**

Send a `POST` request to `/workflow` with payload like:
```json
{
    "name": "encode_and_deploy",
    "version": 1,
    "correlationId": "my_unique_correlation_id",
    "input": {
        "param1": "value1",
        "param2": "value2"
    }
}
```

## Dynamic Workflows

If the need arises to run a one-time workflow, and it doesn't make sense to register Task and Workflow definitions in Conductor Server, as it could change dynamically for each execution, dynamic workflow executions can be used.

This enables you to provide a workflow definition embedded with the required task definitions to the Start Workflow Request in the `workflowDef` parameter, avoiding the need to register the blueprints before execution.

**Example:**

Send a `POST` request to `/workflow` with payload like:
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
