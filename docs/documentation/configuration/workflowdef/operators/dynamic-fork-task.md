# Dynamic Fork
```json
"type" : "FORK_JOIN_DYNAMIC"
```

The Dynamic Fork task (`FORK_JOIN_DYNAMIC`) is used to run tasks in parallel, with the forking behavior (such as the task type and the number of forks) determined at runtime. This contrasts with the [Fork](fork-task.md) task, where the forking behavior is defined at workflow creation. 

Like the Fork task, the Dynamic Fork task must be followed by a [Join](join-task.md) that waits on the forked tasks to finish before moving to the next task. This Join task collects the outputs from each forked tasks.

Unlike the Fork/Join task, a Dynamic Fork task can only run one task per fork. A sub-workflow can be utilized if there is a need for multiple tasks per fork.

There are two ways to run the Dynamic Fork task:

- **Each fork runs a different task**—Use `dynamicForkTasksParam` and `dynamicForkTasksInputParamName`.
- **All forks run the same task**—Use `forkTaskType` and `forkTaskInputs` for any task type, or `forkTaskWorkflow` and `forkTaskInputs` for Sub Workflow tasks.


## Task parameters

Use these parameters in top level of the Dynamic Fork task configuration. The input payload for the forked tasks should correspond with its expected input. For example, if the forked tasks are HTTP tasks, its input should include `http_request`.

### For different tasks in each fork

To configure the Dynamic Fork task, provide a `dynamicForkTasksParam` and `dynamicForkTasksInputParamName` at the top level of the task configuration, as well as the matching parameters in `inputParameters` based on the `dynamicForkTasksParam` and `dynamicForkTasksInputParamName`.


| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| dynamicForkTasksParam          | String | The parameter name for `inputParameters` whose value is used to schedule the task. For example, "dynamicTasks".               | Required. |
| inputParameters.dynamicTasks | List[Task] | The list of task configurations that will be executed across forks (one task per fork) | Required. |
| dynamicForkTasksInputParamName | String | The parameter name for `inputParameters` whose value is used to pass the required input parameters for each forked task.  For example, "dynamicTasksInput".     | Required. |
| inputParameters.dynamicTasksInput | Map[String, Map[String, Any]] | The inputs for each forked task. The keys are the task reference names for each fork and the values are the input parameters that will be passed into its corresponding task.  | Required. |

The [Join](join-task.md) task must run after the forked tasks. Add the Join task to complete the fork-join operations.

### For the same task (any task type)

Use these parameters inside `inputParameters` in the Dynamic Fork task configuration to execute any task type (except Sub Workflow tasks) for all forks.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.forkTaskType  | String (enum) | The type of task that will be executed in each fork. For example, "HTTP", or "SIMPLE".                                                                      | Required. |
| inputParameters.forkTaskName	 | String | The name of the Worker task (`SIMPLE`) that will be executed in each fork.                                                                                                                        | Required only if `forkTaskType` is "SIMPLE". |
| inputParameters.forkTaskInputs  | List[Map[String, Any]] | The inputs for each forked task. The number of list items corresponds with the number of branches in the dynamic fork at execution.        | Required. |

The [Join](join-task.md) task must run after the forked tasks. Configure the Join task as well to complete the fork-join operations.

### For the same subworkflow

Use these parameters inside `inputParameters` in the Dynamic Fork task configuration to execute a [Sub Workflow](sub-workflow-task.md) task for all forks.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.forkTaskWorkflow  | String | The name of the workflow that will be executed in each fork.            | Required. |
| inputParameters.forkTaskWorkflowVersion	 | Integer | The version of the workflow to be executed. If unspecified, the latest version will be used.                                | Optional. |
| inputParameters.forkTaskInputs  | List[Map[String, Any]] | The inputs for each forked task. The number of list items corresponds with the number of branches in the dynamic fork at execution.        | Required. |

The [Join](join-task.md) task must run after the forked tasks. Configure the Join task as well to complete the fork-join operations.


## JSON configuration

This is the task configuration for a Dynamic Fork task.

### For different tasks in each fork

```json
{
  "name": "fork_join_dynamic",
  "taskReferenceName": "fork_join_dynamic_ref",
  "inputParameters": {
    "dynamicTasks": [ // name of the tasks to execute
      {
        "name": "http",
        "taskReferenceName": "http_ref",
        "type": "HTTP",
        "inputParameters": {}
      },
      { 
        // another task configuration 
      }

    ],
    "dynamicTasksInput": { // inputs for the tasks
      "taskReferenceName" : {
        "key": "value",
        "key": "value"
      },
      "anotherTaskReferenceName" : {
        "key": "value",
        "key": "value"
      }
    }
  },
  "type": "FORK_JOIN_DYNAMIC",
  "dynamicForkTasksParam": "dynamicTasks", // input parameter key that will hold the task names to execute
  "dynamicForkTasksInputParamName": "dynamicTasksInput" // input parameter key that will hold the input parameters for each task
}
```

### For the same task (any task type)

```json
{
  "name": "fork_join_dynamic",
  "taskReferenceName": "fork_join_dynamic_ref",
  "inputParameters": {
    "forkTaskType": "HTTP",
    "forkTaskInputs": [
      {
        // inputs for the first branch
      },
      {
        // inputs for the second branch
      },
      ...
    ]
  },
  "type": "FORK_JOIN_DYNAMIC"
}
```

### For the same subworkflow

```json
{
  "name": "fork_join_dynamic",
  "taskReferenceName": "fork_join_dynamic_ref",
  "inputParameters": {
    "forkTaskWorkflow": "someWorkflow",
    "forkTaskWorkflowVersion": 1,
    "forkTaskInputs": [
      {
        // inputs for the first branch
      },
      {
        // inputs for the second branch
      },
      ...
    ]
  },
  "type": "FORK_JOIN_DYNAMIC"
}
```


## Examples

Here are some examples for using the Dynamic Fork task.

### Running different tasks

To run a different task per fork, you must use `dynamicForkTasksParam` and `dynamicForkTasksInputParamName`.

In this example workflow, the Dynamic Fork task spawns three forks, each running a different task (`HTTP`, `SIMPLE`, and `INLINE`). For true dynamism, you can add another task to prepare the list of tasks and inputs for the Dynamic Fork task.

```json
{
  "name": "DynamicForkExample",
  "description": "This workflow runs different tasks in a dynamic fork.",
  "version": 1,
  "tasks": [
    {
      "name": "fork_join_dynamic",
      "taskReferenceName": "fork_join_dynamic_ref",
      "inputParameters": {
        "dynamicTasks": [
          {
            "name": "inline",
            "taskReferenceName": "task1",
            "type": "INLINE",
            "inputParameters": {
              "expression": "(function () {\n  return $.input;\n})();",
              "evaluatorType": "javascript"
            }
          },
          {
            "name": "http",
            "taskReferenceName": "task2",
            "type": "HTTP",
            "inputParameters": {}
          },
          {
            "name": "task_38",
            "taskReferenceName": "simple_ref",
            "type": "SIMPLE"
          }
        ],
        "dynamicTasksInput": {
          "task1": {
            "input": "one"
          },
          "task2": {
            "http_request": {
              "method": "GET",
              "uri": "https://randomuser.me/api/",
              "connectionTimeOut": 3000,
              "readTimeOut": "3000",
              "accept": "application/json",
              "contentType": "application/json",
              "encode": true
            }
          },
          "task3": {
            "input": {
              "someKey": "someValue"
            }
          }
        }
      },
      "type": "FORK_JOIN_DYNAMIC",
      "dynamicForkTasksParam": "dynamicTasks",
      "dynamicForkTasksInputParamName": "dynamicTasksInput"
    },
    {
      "name": "join",
      "taskReferenceName": "join_ref",
      "inputParameters": {},
      "type": "JOIN",
      "joinOn": []
    }
  ],
  "inputParameters": [],
  "outputParameters": {},
  "schemaVersion": 2,
  "ownerEmail": "example@email.com"
}
```

Refer to the [Join](join-task.md) task for more details on the Join aspect of the Fork.

### Running the same task — Worker task

In this example workflow, a Dynamic Fork task is used to run Worker tasks (`SIMPLE`) that will resize uploaded images and store the resized images into a specified `location`.

```json
{
  "name": "image_multiple_convert_resize_fork",
  "description": "Image multiple convert resize example",
  "version": 1,
  "tasks": [
    {
      "name": "image_multiple_convert_resize_dynamic_task",
      "taskReferenceName": "image_multiple_convert_resize_dynamic_task_ref",
      "inputParameters": {
        "forkTaskName": "fork_task",
        "forkTaskType": "SIMPLE",
        "forkTaskInputs": [
           {
            "image" : "url1",
            "location" : "location_url",
            "width" : 100,
            "height" : 200
           },
           {
            "image" : "url2",
            "location" : "location_url",
            "width" : 300,
            "height" : 400
           }
       ]
      },
      "type": "FORK_JOIN_DYNAMIC",
      "dynamicForkTasksParam": "dynamicTasks",
      "dynamicForkTasksInputParamName": "dynamicTasksInput"
    },
    {
      "name": "image_multiple_convert_resize_join",
      "taskReferenceName": "image_multiple_convert_resize_join_ref",
      "inputParameters": {},
      "type": "JOIN"
    }
  ],
  "inputParameters": [],
  "outputParameters": {
    "output": "${join_task_ref.output}"
  },
  "schemaVersion": 2,
  "ownerEmail": "example@email.com"
}
```

Refer to the [Join](join-task.md) task for more details on the Join aspect of the Fork.


### Running the same task — HTTP task

In this example workflow, the Dynamic Fork task runs HTTP tasks in parallel. The provided input in `forkTaskInputs` contains the typical payload expected in a HTTP task.

```json
{
  "name": "dynamic_workflow_array_http",
  "description": "Dynamic workflow array - run HTTP tasks",
  "version": 1,
  "tasks": [
    {
      "name": "dynamic_workflow_array_http",
      "taskReferenceName": "dynamic_workflow_array_http_ref",
      "inputParameters": {
        "forkTaskType": "HTTP",
        "forkTaskInputs": [
          {
            "http_request": {
              "method": "GET",
              "uri": "https://randomuser.me/api/"
            }
          },
          {
            "http_request": {
              "method": "GET",
              "uri": "https://randomuser.me/api/"
            }
          }
        ]
      },
      "type": "FORK_JOIN_DYNAMIC",
      "dynamicForkTasksParam": "dynamicTasks",
      "dynamicForkTasksInputParamName": "dynamicTasksInput"
    },
    {
      "name": "dynamic_workflow_array_http_join",
      "taskReferenceName": "dynamic_workflow_array_http_join_ref",
      "inputParameters": {},
      "type": "JOIN",
      "joinOn": []
    }
  ],
  "inputParameters": [],
  "outputParameters": {},
  "schemaVersion": 2,
  "ownerEmail": "example@email.com"
}
```

Refer to the [Join](join-task.md) task for more details on the Join aspect of the Fork.


### Running the same task — Sub Workflow task


In this example workflow, the dynamic fork runs Sub Workflow tasks in parallel. Each sub-workflow will resize the image and store the resized image into a specified `location`.

```json
{
  "name": "image_multiple_convert_resize_fork_subwf",
  "description": "Image multiple convert resize example",
  "version": 1,
  "tasks": [
    {
      "name": "image_multiple_convert_resize_dynamic_task_subworkflow",
      "taskReferenceName": "image_multiple_convert_resize_dynamic_task_subworkflow_ref",
      "inputParameters": {
        "forkTaskWorkflow": "image_resize_subworkflow",
        "forkTaskInputs": [
          {
            "image": "url1",
            "location": "location url",
            "width": 100,
            "height": 200
          },
          {
            "image": "url2",
            "location": "locationurl",
            "width": 300,
            "height": 400
          }
        ]
      },
      "type": "FORK_JOIN_DYNAMIC",
      "dynamicForkTasksParam": "dynamicTasks",
      "dynamicForkTasksInputParamName": "dynamicTasksInput"
    },
    {
      "name": "dynamic_workflow_array_http_subworkflow",
      "taskReferenceName": "dynamic_workflow_array_http_subworkflow_ref",
      "inputParameters": {},
      "type": "JOIN",
      "joinOn": []
    }
  ],
  "inputParameters": [],
  "outputParameters": {},
  "schemaVersion": 2,
  "ownerEmail": "example@email.com"
}
```

Refer to the [Join](join-task.md) task for more details on the Join aspect of the Fork.