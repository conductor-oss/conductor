---
sidebar_position: 1
---
# Start Workflow
```json
"type" : "START_WORKFLOW"
```
### Introduction

Start Workflow starts another workflow.

### Use Cases

When another workflow needs to be started from a workflow, `START_WORKFLOW` can be used.

### Configuration

Start Workflow task is defined directly inside the workflow with type `START_WORKFLOW`.

#### Input

**Parameters:**

| name          |type| description                                                                                                            |
|---------------|---|------------------------------------------------------------------------------------------------------------------------|
| startWorkflow | Map[String, Any] | The value of this parameter is [Start Workflow Request](../../gettingstarted/startworkflow.md#start-workflow-request). |

#### Output

| name       |type| description                    |
|------------|---|--------------------------------|
| workflowId | String | The id of the started workflow |
