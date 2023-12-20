# Set Variable

```json
"type" : "SET_VARIABLE"
```

The `SET_VARIABLE` task allows users to create global workflow variables, and update them with new values.

Variables can be initialized in the workflow definition as well as during the workflow run. Once a variable is initialized
it can be read using the *expression* `${workflow.variables.NAME}` by any other task.

It can be overwritten by a subsequent `SET_VARIABLE` task.

!!!warning
	There is a hard barrier for variables payload size in KB defined in the JVM system properties (`conductor.max.workflow.variables.payload.threshold.kb`) the default value is `256`. Passing this barrier will fail the task and the workflow.

## Use Cases
For example, you might want to track shared state at the workflow level, and have the state be accessible by any task executed as part of the workflow.

## Configuration
Global variables can be set in `inputParameters` using the desired variable names and their respective values.

## Example
Suppose in a workflow, we have to store a value in a variable and then later in
workflow reuse the value stored in the variable just as we do in programming, in such
scenarios `Set Variable` task can be used.

Following is the workflow definition with `SET_VARIABLE` task.

```json
{
  "name": "Set_Variable_Workflow",
  "description": "Set a value to a variable and then reuse it later in the workflow",
  "version": 1,
  "tasks": [
    {
      "name": "Set_Name",
      "taskReferenceName": "Set_Name",
      "type": "SET_VARIABLE",
      "inputParameters": {
        "name": "Foo"
      }
    },
    {
      "name": "Read_Name",
      "taskReferenceName": "Read_Name",
      "inputParameters": {
        "saved_name" : "${workflow.variables.name}"
      },
      "type": "SIMPLE"
    }
  ],
  "restartable": true,
  "ownerEmail":"abc@example.com",
  "workflowStatusListenerEnabled": true,
  "schemaVersion": 2
}
```

In the above example, it can be seen that the task `Set_Name` is a Set Variable Task and
the variable `name` is set to `Foo` and later in the workflow it is referenced by
`"${workflow.variables.name}"` in another task.
