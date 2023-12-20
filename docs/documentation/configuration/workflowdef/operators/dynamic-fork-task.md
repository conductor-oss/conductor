# Dynamic Fork
```json
"type" : "FORK_JOIN_DYNAMIC"
```

The `DYNAMIC_FORK` operation in Conductor lets you execute a list of tasks or sub-workflows in parallel. This list will
be determined at run-time and be of variable length.

A `DYNAMIC_FORK` is typically followed by `JOIN` task that collects outputs from each of the forked tasks or sub workflows.

There are three things that are needed to configure a `FORK_JOIN_DYNAMIC` task.

1. A list of tasks or sub-workflows that needs to be forked and run in parallel.
2. A list of inputs to each of these forked tasks or sub-workflows
3. A task prior to the `FORK_JOIN_DYNAMIC` tasks outputs 1 and 2 above that can be wired in as in input to
   the `FORK_JOIN_DYNAMIC` tasks

## Use Cases
A `FORK_JOIN_DYNAMIC` is useful, when a set of tasks or sub-workflows needs to be executed and the number of tasks or
sub-workflows are determined at run time. E.g. Let's say we have a task that resizes an image, and we need to create a
workflow that will resize an image into multiple sizes. In this case, a task can be created prior to
the `FORK_JOIN_DYNAMIC` task that will prepare the input that needs to be passed into the `FORK_JOIN_DYNAMIC` task. The
single image resize task does one job. The `FORK_JOIN_DYNAMIC` and the following `JOIN` will manage the multiple
invokes of the single image resize task. Here, the responsibilities are clearly broken out, where the single image resize
task does the core job and `FORK_JOIN_DYNAMIC` manages the orchestration and fault tolerance aspects.

## Configuration
To use the `DYNAMIC_FORK` task, you need to provide the following attributes at the top level of the task configuration, **as well as** corresponding values inside `inputParameters`.

| Attribute                      | Description                                                                                             |
| ------------------------------ | ------------------------------------------------------------------------------------------------------- |
| dynamicForkTasksParam          | Name of attribute in `inputParameters` to read array of task or subworkflow names from.                 |
| dynamicForkTasksInputParamName | Name of attribute in `inputParameters` to read map of inputs to use for spawned tasks or subworkflows. |

### inputParameters
| Attribute                       | Description                                                                                                                                                                                  |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| *dynamicForkTasksParam          | This is a JSON array of tasks or sub-workflow objects that needs to be forked and run in parallel (Note: This has a different format for ```SUB_WORKFLOW``` compared to ```SIMPLE``` tasks.) |
| *dynamicForkTasksInputParamName | A JSON map, where the keys are task or sub-workflow names, and the values are the `inputParameters` to be passed into the corresponding spawned tasks or sub-workflows.                      |

Note: * means the de-referenced name.


## Examples
### Example 1
Here is an example of a `FORK_JOIN_DYNAMIC` task followed by a `JOIN` task

```json
[
  {
    "inputParameters": {
      "dynamicTasks": "${fooBarTask.output.dynamicTasksJSON}",
      "dynamicTasksInput": "${fooBarTask.output.dynamicTasksInputJSON}"
    },
    "type": "FORK_JOIN_DYNAMIC",
    "dynamicForkTasksParam": "dynamicTasks",
    "dynamicForkTasksInputParamName": "dynamicTasksInput"
  },
  {
    "name": "image_multiple_convert_resize_join",
    "taskReferenceName": "image_multiple_convert_resize_join_ref",
    "type": "JOIN"
  }
]
```

Dissecting into this example above, let's look at the three things that are needed to configured for
the `FORK_JOIN_DYNAMIC` task

`dynamicForkTasksParam` This is a JSON array of task or sub-workflow objects that specifies the list of tasks or
sub-workflows that needs to be forked and run in parallel `dynamicForkTasksInputParamName` This is a JSON map of task or
sub-workflow objects that specifies the list of tasks or sub-workflows that needs to be forked and run in parallel
fooBarTask This is a task that is defined prior to the FORK_JOIN_DYNAMIC in the workflow definition. This task will need
to output (outputParameters) 1 and 2 above so that it can be wired into inputParameters of the FORK_JOIN_DYNAMIC
tasks. (dynamicTasks and dynamicTasksInput)

### Example 2
Let's say we have a task that resizes an image, and we need to create a workflow that will resize an image into multiple sizes. In this case, a task can be created prior to
the `FORK_JOIN_DYNAMIC` task that will prepare the input that needs to be passed into the `FORK_JOIN_DYNAMIC` task. These will be:

* ```dynamicForkTasksParam``` the JSON array of tasks/subworkflows to be run in parallel. Each JSON object will have: 
  * A unique ```taskReferenceName```.
  * The name of the Task/Subworkflow to be called (note - the location of this key:value is different for a subworkflow).
  * The type of the task (This is optional for SIMPLE tasks).
* ```dynamicForkTasksInputParamName``` a JSON map of input parameters for each task. The keys will be the unique ```taskReferenceName``` defined in the first JSON array, and the values will be the specific input parameters for the task/subworkflow.

The ```image_resize``` task works to resize just one image. The `FORK_JOIN_DYNAMIC` and the following `JOIN` will manage the multiple invocations of the single ```image_resize``` task. The responsibilities are clearly broken out, where the individual  ```image_resize```
tasks do the core job and `FORK_JOIN_DYNAMIC` manages the orchestration and fault tolerance aspects of handling multiple invocations of the task.

#### Workflow Definition - Task Configuration

Here is an example of a `FORK_JOIN_DYNAMIC` task followed by a `JOIN` task.  The fork is named and given a taskReferenceName, but all of the input parameters are JSON variables that we will discuss next:

```json
[
  {      
    "name": "image_multiple_convert_resize_fork",
    "taskReferenceName": "image_multiple_convert_resize_fork_ref",
    "inputParameters": {
      "dynamicTasks": "${fooBarTask.output.dynamicTasksJSON}",
      "dynamicTasksInput": "${fooBarTask.output.dynamicTasksInputJSON}"
    },
    "type": "FORK_JOIN_DYNAMIC",
    "dynamicForkTasksParam": "dynamicTasks",
    "dynamicForkTasksInputParamName": "dynamicTasksInput"
  },
  {
    "name": "image_multiple_convert_resize_join",
    "taskReferenceName": "image_multiple_convert_resize_join_ref",
    "type": "JOIN"
  }
]
```

This appears in the UI as follows:

![diagram of dynamic fork](dynamic-task-diagram.png)

Let's assume this data is sent to the workflow:

```
{
	"fileLocation": "https://pbs.twimg.com/media/FJY7ud0XEAYVCS8?format=png&name=900x900",
	"outputFormats": ["png","jpg"],
	
	"outputSizes": [
		{"width":300,
		"height":300},
		{"width":200,
		"height":200}
	],
	"maintainAspectRatio": "true"
}
```

With 2 file formats and 2 sizes in the input, we'll be creating 4 images total.  The first task will generate the tasks and the parameters for these tasks:

* `dynamicForkTasksParam` This is a JSON array of task or sub-workflow objects that specifies the list of tasks or sub-workflows that needs to be forked and run in parallel. This JSON varies depeding oon the type of task.  


#### ```dynamicForkTasksParam``` Simple task 
In this case, our fork is running a SIMPLE task: ```image_convert_resize```:

```
{ "dynamicTasks": [
  {
    "name": :"image_convert_resize",
    "taskReferenceName": "image_convert_resize_png_300x300_0",
    ...
  },
  {
    "name": :"image_convert_resize",
    "taskReferenceName": "image_convert_resize_png_200x200_1",
    ...
  },
  {
    "name": :"image_convert_resize",
    "taskReferenceName": "image_convert_resize_jpg_300x300_2",
    ...
  },
  {
    "name": :"image_convert_resize",
    "taskReferenceName": "image_convert_resize_jpg_200x200_3",
    ...
  }
]}
```
#### ```dynamicForkTasksParam``` SubWorkflow task
In this case, our Dynamic fork is running a SUB_WORKFLOW task: ```image_convert_resize_subworkflow```

```
{ "dynamicTasks": [
  {
    "subWorkflowParam" : {
      "name": :"image_convert_resize_subworkflow",
      "version": "1"
    },
    "type" : "SUB_WORKFLOW",
    "taskReferenceName": "image_convert_resize_subworkflow_png_300x300_0",
    ...
  },
  {
    "subWorkflowParam" : {
      "name": :"image_convert_resize_subworkflow",
      "version": "1"
    },
    "type" : "SUB_WORKFLOW",
    "taskReferenceName": "image_convert_resize_subworkflow_png_200x200_1",
    ...
  },
  {
    "subWorkflowParam" : {
      "name": :"image_convert_resize_subworkflow",
      "version": "1"
    },
    "type" : "SUB_WORKFLOW",
    "taskReferenceName": "image_convert_resize_subworkflow_jpg_300x300_2",
    ...
  },
  {
    "subWorkflowParam" : {
      "name": :"image_convert_resize_subworkflow",
      "version": "1"
    },
    "type" : "SUB_WORKFLOW",
    "taskReferenceName": "image_convert_resize_subworkflow_jpg_200x200_3",
    ...
  }
]}
```



* `dynamicForkTasksInputParamName` This is a JSON map of task or
sub-workflow objects and all the input parameters that these tasks will need to run.

```
"dynamicTasksInput":{
"image_convert_resize_jpg_300x300_2":{
"outputWidth":300
"outputHeight":300
"fileLocation":"https://pbs.twimg.com/media/FJY7ud0XEAYVCS8?format=png&name=900x900"
"outputFormat":"jpg"
"maintainAspectRatio":true
}
"image_convert_resize_jpg_200x200_3":{
"outputWidth":200
"outputHeight":200
"fileLocation":"https://pbs.twimg.com/media/FJY7ud0XEAYVCS8?format=png&name=900x900"
"outputFormat":"jpg"
"maintainAspectRatio":true
}
"image_convert_resize_png_200x200_1":{
"outputWidth":200
"outputHeight":200
"fileLocation":"https://pbs.twimg.com/media/FJY7ud0XEAYVCS8?format=png&name=900x900"
"outputFormat":"png"
"maintainAspectRatio":true
}
"image_convert_resize_png_300x300_0":{
"outputWidth":300
"outputHeight":300
"fileLocation":"https://pbs.twimg.com/media/FJY7ud0XEAYVCS8?format=png&name=900x900"
"outputFormat":"png"
"maintainAspectRatio":true
}
```

#### Join Task

The [JOIN](join-task.md) task will run after all of the dynamic tasks, collecting the output for all of the tasks.