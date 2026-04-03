# Terminate
```json
"type" : "TERMINATE"
```

The Terminate task (`TERMINATE`) terminates the current workflow with a termination status and reason, and sets the workflow output with any supplied values. 

Often used in [Switch](switch-task.md) tasks, the Terminate task can act as a return statement for cases where you want the workflow to be terminated without continuing to the subsequent tasks.

## Task parameters

Use these parameters inside `inputParameters` in the Terminate task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.terminationStatus | String (enum) | The termination status. Supported types: <ul><li>COMPLETED</li><li>FAILED</li><li>TERMINATED</li></ul>                                   | Required. |
| inputParameters.terminationReason | String | The reason for terminating the current workflow, which will provide the context of the termination. <br/><br/> For FAILED workflows, this reason is passed to any configured `failureWorkflow`. | Optional.         |
| inputParameters.workflowOutput    | Any     | The expected workflow output upon termination.                                                              | Optional.         |


## Configuration JSON
Here is the task configuration for a Terminate task.

```json
{
  "name": "terminate",
  "taskReferenceName": "terminate_ref",
  "inputParameters": {
    "terminationStatus": "TERMINATED",
    "terminationReason": "",
    "workflowOutput": "${someTask.output}"
  },
  "type": "TERMINATE"
}
```


## Output

The Terminate task will return the following parameters.

| Name   | Type | Description                                                                                               |
| ------ | ---- | --------------------------------------------------------------------------------------------------------- |
| output | Map[String, Any]  | A map of the workflow output on termination, as defined in `inputParameters.workflowOutput`. If `workflowOutput` is not set in the Terminate task configuration, the output will be an empty object. |

## Examples

Here are some examples for using the Terminate task.

### Using the Terminate task in a switch case

In this example workflow, a decision is made to ship with a specific shipping provider based on the provided workflow input. If the provided input does not match the available shipping providers, then the workflow will terminate with a FAILED status. Here is a snippet that shows the default switch case terminating the workflow:


```json
{
  "name": "switch_task",
  "taskReferenceName": "switch_task",
  "type": "SWITCH",
  "defaultCase": [
      {
      "name": "terminate",
      "taskReferenceName": "terminate_ref",
      "type": "TERMINATE",
      "inputParameters": {
          "terminationStatus": "FAILED",
          "terminationReason":"Shipping provider not found."
      }      
    }
   ]
}
```

The full workflow with the Terminate task looks like this:

![Conductor UI - Workflow Diagram](Terminate_Task.png)


## Best practices

Here are some best practices for handling workflow termination:

* Include a termination reason when terminating the workflow with FAILED status, so that it is easy to understand the cause.
2. Include any additional details in the workflow output (e.g., output of the tasks, the selected switch case), to add context to the path taken to termination.
