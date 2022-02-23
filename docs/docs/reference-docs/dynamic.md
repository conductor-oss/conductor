## Dynamic Task

Dynamic Tasks allow you to execute a registered task dynamically at run-time. It accepts the task name to execute in inputParameters.

**Parameters:**

|name|description|
|---|---|
| dynamicTaskNameParam|Name of the parameter from the task input whose value is used to schedule the task.  e.g. if the value of the parameter is ABC, the next task scheduled is of type 'ABC'.|

**Example**
``` json
{
  "name": "user_task",
  "taskReferenceName": "t1",
  "inputParameters": {
    "files": "${workflow.input.files}",
    "taskToExecute": "${workflow.input.user_supplied_task}"
  },
  "type": "DYNAMIC",
  "dynamicTaskNameParam": "taskToExecute"
}
```
If the workflow is started with input parameter user_supplied_task's value as __user_task_2__, Conductor will schedule __user_task_2__ when scheduling this dynamic task.
