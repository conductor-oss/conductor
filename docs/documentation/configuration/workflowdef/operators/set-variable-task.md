# Set Variable

```json
"type" : "SET_VARIABLE"
```

The Set Variable task (`SET_VARIABLE`) allows you to construct shared variables at the workflow level across tasks. 

These variables can be initialized, accessed, or overwritten at any point in the workflow:

* Once initialized, the variable can be referenced in any subsequent task using "${workflow.variables._someName_}" (replacing _someName_ with the actual variable name).
* Initialized values can be overwritten by a subsequent Set Variable task.

## Task parameters

To configure the Set Variable task, set your desired variable names and their respective values in `inputParameters`. The values can be set in two ways:

* Hard-coded in the workflow definition, or
* A dynamic reference.

## JSON configuration

This is the task configuration for a Set Variable task.

```json
{
  "name": "set_variable",
  "taskReferenceName": "set_variable_ref",
  "type": "SET_VARIABLE",
  "inputParameters": {
    "variableName": "value",
    "variableName2": "${workflow.input.someKey}"
    "variableName3": 5,
  }
}
```

## Examples

In this example workflow, a username is stored as a variable so that it can be reused in other tasks that require the username.

```json
{
  "name": "Welcome_User_Workflow",
  "description": "Designate a user to be welcomed",
  "tasks": [
    {
      "name": "set_name",
      "taskReferenceName": "set_name_ref",
      "type": "SET_VARIABLE",
      "inputParameters": {
        "name": "${workflow.input.userName}"
      }
    },
    {
      "name": "greet_user",
      "taskReferenceName": "greet_user_ref",
      "inputParameters": {
        "var_name": "${workflow.variables.name}"
      },
      "type": "SIMPLE"
    },
    {
      "name": "send_reminder_email",
      "taskReferenceName": "send_reminder_email_ref",
      "inputParameters": {
        "var_name": "${workflow.variables.name}"
      },
      "type": "SIMPLE"
    }
  ]
}
```

In the example above, `set_name` is a Set Variable task that initializes a variable `name` using a workflow input reference. In subsequent tasks, the variable is later referenced using "${workflow.variables.name}".


## Limitations

Here are some limitation when using the Set Variable task:

* **Payload limit**—By default, there is a hard limit for the payload size of variables defined in the JVM system properties (`conductor.max.workflow.variables.payload.threshold.kb`) of 256KB. Exceeding this limit will cause the Set Variable task to fail.
* **Variable scope**—The scope of the Set Variable task is limited to its workflow. An initialized variable in one workflow will not carry over to another workflow or sub-workflow and will have to be re-initialized using another Set Variable task. 