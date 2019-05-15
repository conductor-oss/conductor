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
    "name": "my_adhoc_workflow_notregistered",
    "workflowDef": {
        "ownerApp": "my_owner_app",
        "createdBy": "my_username",
        "name": "my_adhoc_http_test_notregistered",
        "description": "Test Http Task Workflow",
        "version": 1,
        "tasks": [
            {
            "name": "test_http_task",
            "taskReferenceName": "my_test_http_task_registered",
            "inputParameters": {
                "http_request": "${workflow.input.http_request}"
            },
            "type": "HTTP",
            "startDelay": 0,
            "optional": false
            }
        ],
        "schemaVersion": 2,
        "restartable": true,
        "workflowStatusListenerEnabled": false
    },
    "input": {
        "http_request": {
            "uri": "/health",
            "vipAddress": "cpeworkflow-devint:7004",
            "method": "GET",
            "contentType": "application/json",
            "appName": "cpeworkflow"
        }
    }
}
```
