# Start Workflow
```json
"type" : "START_WORKFLOW"
```

The Start Workflow task (`START_WORKFLOW`) starts another workflow from the current workflow. Unlike the [Sub Workflow](sub-workflow-task.md) task, the workflow triggered by the Start Workflow task will execute asynchronously. That means the current workflow proceeds to its next task without waiting for the started workflow to complete.

A Start Workflow task is marked as COMPLETED when the requested workflow enters the RUNNING state, regardless of its final state.

## Task parameters

Use these parameters inside `inputParameters` in the Start Workflow task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.startWorkflow | Map[String, Any] | A map that includes the requested workflow’s configuration, such as the name and version. Refer to [Start Workflow Request](../../../api/startworkflow.md#start-workflow-request) for what to include in this parameter. | Required. |

## Task configuration
Here is the task configuration for a Start Workflow task.​

```json
{
  "name": "start_workflow",
  "taskReferenceName": "start_workflow_ref",
  "inputParameters": {
    "startWorkflow": {
      "name": "someName",
      "input": {
        "someParameter": "someValue",
        "anotherParameter": "anotherValue"
      },
      "version": 1,
      "correlationId": ""
    }
  },
  "type": "START_WORKFLOW"
}
```

## Output


The Start Workflow task will return the following parameters.

| Name             | Type         | Description                                                   |
| ---------------- | ------------ | ------------------------------------------------------------- |
| workflowId | String | The workflow execution ID of the started workflow. |


## Limitations

Because the Start Workflow task will neither wait for the completion of the started workflow nor pass back its output, it is not possible to access the output of the started workflow from the current workflow. If required, you can use the [Sub Workflow](sub-workflow-task.md) task instead.
