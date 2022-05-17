---
sidebar_position: 1
---
# Start Workflow
```json
"type" : "START_WORKFLOW"
```
### Introduction

Start Workflow starts another workflow. Unlike `SUB_WORKFLOW`, `START_WORKFLOW` does
not create a relationship between starter and the started workflow. It also does not wait for the started workflow to complete. A `START_WORKFLOW` is 
considered successful once the requested workflow is started successfully. In other words, `START_WORKFLOW` is marked as COMPLETED, once the started 
workflow is in RUNNING state.

### Use Cases

When another workflow needs to be started from a workflow, `START_WORKFLOW` can be used. 

### Configuration

Start Workflow task is defined directly inside the workflow with type `START_WORKFLOW`.

#### Input

**Parameters:**

| name          | type             | description                                                                                                         |
|---------------|------------------|---------------------------------------------------------------------------------------------------------------------|
| startWorkflow | Map[String, Any] | The value of this parameter is [Start Workflow Request](/gettingstarted/startworkflow.html#start-workflow-request). |

#### Output

| name       | type   | description                    |
|------------|--------|--------------------------------|
| workflowId | String | The id of the started workflow |
