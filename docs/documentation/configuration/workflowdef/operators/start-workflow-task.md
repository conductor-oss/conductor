# Start Workflow
```json
"type" : "START_WORKFLOW"
```

The `START_WORKFLOW` task starts another workflow. Unlike `SUB_WORKFLOW`, `START_WORKFLOW` does
not create a relationship between starter and the started workflow. It also does not wait for the started workflow to complete. A `START_WORKFLOW` is 
considered successful once the requested workflow is started successfully. In other words, `START_WORKFLOW` is marked as `COMPLETED` once the started 
workflow is in `RUNNING` state.

There is no ability to access the `output` of the started workflow.

## Use Cases
When another workflow needs to be started from the current workflow, `START_WORKFLOW` can be used. 

## Configuration
The workflow invocation payload is passed into `startWorkflow` under `inputParameters`.

### inputParameters
| name          | type             | description                                                                                                         |
|---------------|------------------|---------------------------------------------------------------------------------------------------------------------|
| startWorkflow | Map[String, Any] | The value of this parameter is [Start Workflow Request](../../../api/startworkflow.md#start-workflow-request). |

## Output
| name       | type   | description                    |
|------------|--------|--------------------------------|
| workflowId | String | The id of the started workflow |

Note: `START_WORKFLOW` will neither wait for the completion of, nor pass back the `output` of the spawned workflow.
