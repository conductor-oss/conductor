# Sub Workflow
```json
"type" : "SUB_WORKFLOW"
```

The Sub Workflow task executes another workflow within the current workflow. This allows you to nest and reuse common workflows across multiple workflows. 


Unlike the [Start Workflow](start-workflow-task.md) task, the Sub Workflow task provides synchronous execution and the executed sub-workflow will contain a reference to its parent workflow.

The Sub Workflow task can also be used to overcome the limitations of other tasks:

- Use it in a [Do While](do-while-task.md) task to achieve nested Do While loops.
- Use it in a [Dynamic Fork](dynamic-fork-task.md) task to execute more than one task in each fork.


## Task parameters

Use these parameters inside `subWorkflowParam` in the Sub Workflow task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| subWorkflowParam.name               | String              | Name of the workflow to be executed. This workflow should have a pre-existing definition in Conductor.                                                                      | Required. |
| subWorkflowParam.version            | Integer              | The version of the workflow to be executed. If unspecified, the latest version will be used.                                     | Required. |
| subWorkflowParam.taskToDomain       | Map[String, String]               | Allows scheduling the sub-workflow's tasks to specific domain mappings. <br/> Refer to [Task Domains](../../../api/taskdomains.md) for how to configure `taskToDomain`. | Optional. |
| inputParameters | Map[String, Any] | Contains the sub-workflow's input parameters, if any. | Optional. |

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

The Sub Workflow task will return the following parameters.

| Name             | Type         | Description                                                   |
| ---------------- | ------------ | ------------------------------------------------------------- |
| subWorkflowId | String | The workflow execution ID of the sub-workflow. |

In addition, the task output will also contain the sub-workflow's outputs.


## Execution

During execution, the Sub Workflow task will be marked as COMPLETED only upon the completion of the spawned workflow. If the sub-workflow fails or terminates, the Sub Workflow task will be marked as FAILED and retried if configured. 

If the Sub Workflow task is defined as optional in the parent workflow definition, the Sub Workflow task will not be retried if sub-workflow fails or terminates. In addition, even if the sub-workflow is retried/rerun/restarted after reaching to a terminal status, the parent workflow task status will remain as it is.


## Examples

In this example workflow, a Fork task containing two tasks is used to simultaneously create two images from one image:

![workflow with fork](workflow_fork.png)

The left fork will create a JPG file, and the right fork a WEBP file. Maintaining this workflow might be cumbersome, as changes made to one of the fork tasks do not automatically propagate the other.  Rather than using two tasks, we can define a single, reuseable `image_convert_resize` workflow that can be called as a sub-workflow in both forks:


```json

{
	"name": "image_convert_resize_subworkflow1",
	"description": "Image Processing Workflow",
	"version": 1,
	"tasks": [{
			"name": "image_convert_resize_multipleformat_fork",
			"taskReferenceName": "image_convert_resize_multipleformat_ref",
			"inputParameters": {},
			"type": "FORK_JOIN",
			"decisionCases": {},
			"defaultCase": [],
			"forkTasks": [
				[{
					"name": "image_convert_resize_sub",
					"taskReferenceName": "subworkflow_jpg_ref",
					"inputParameters": {
						"fileLocation": "${workflow.input.fileLocation}",
						"recipeParameters": {
							"outputSize": {
								"width": "${workflow.input.recipeParameters.outputSize.width}",
								"height": "${workflow.input.recipeParameters.outputSize.height}"
							},
							"outputFormat": "jpg"
						}
					},
					"type": "SUB_WORKFLOW",
					"subWorkflowParam": {
						"name": "image_convert_resize",
						"version": 1
					}
				}],
				[{
						"name": "image_convert_resize_sub",
						"taskReferenceName": "subworkflow_webp_ref",
						"inputParameters": {
							"fileLocation": "${workflow.input.fileLocation}",
							"recipeParameters": {
								"outputSize": {
									"width": "${workflow.input.recipeParameters.outputSize.width}",
									"height": "${workflow.input.recipeParameters.outputSize.height}"
								},
								"outputFormat": "webp"
							}
						},
						"type": "SUB_WORKFLOW",
						"subWorkflowParam": {
							"name": "image_convert_resize",
							"version": 1
						}
					}

				]
			]
		},
		{
			"name": "image_convert_resize_multipleformat_join",
			"taskReferenceName": "image_convert_resize_multipleformat_join_ref",
			"inputParameters": {},
			"type": "JOIN",
			"decisionCases": {},
			"defaultCase": [],
			"forkTasks": [],
			"startDelay": 0,
			"joinOn": [
				"subworkflow_jpg_ref",
				"upload_toS3_webp_ref"
			],
			"optional": false,
			"defaultExclusiveJoinTask": [],
			"asyncComplete": false,
			"loopOver": []
		}
	],
	"inputParameters": [],
	"outputParameters": {
		"fileLocationJpg": "${subworkflow_jpg_ref.output.fileLocation}",
		"fileLocationWebp": "${subworkflow_webp_ref.output.fileLocation}"
	},
	"schemaVersion": 2,
	"restartable": true,
	"workflowStatusListenerEnabled": true,
	"ownerEmail": "conductor@example.com",
	"timeoutPolicy": "ALERT_ONLY",
	"timeoutSeconds": 0,
	"variables": {},
	"inputTemplate": {}
}
```

Here is the corresponding workflow diagram:

![workflow with 2 subworkflows](subworkflow_diagram.png)


Now that the tasks are abstracted into a sub-workflow, any changes to the sub-workflow will automatically apply to both forks.