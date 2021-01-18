## Task & Workflow Metadata
| Endpoint        | Description  | Input|
| ------------- |:-------------|---|
| `GET /metadata/taskdefs` | Get all the task definitions| n/a|
| `GET /metadata/taskdefs/{taskType}` | Retrieve task definition| Task Name|
| `POST /metadata/taskdefs` | Register new task definitions| List of [Task Definitions](../configuration/taskdef)|
| `PUT /metadata/taskdefs` | Update a task definition| A [Task Definition](../configuration/taskdef)|
| `DELETE /metadata/taskdefs/{taskType}` | Delete a task definition| Task Name|
|||
| `GET /metadata/workflow` | Get all the workflow definitions| n/a|
| `POST /metadata/workflow` | Register new workflow| [Workflow Definition](../configuration/workflowdef)|
| `PUT /metadata/workflow` | Register/Update new workflows| List of [Workflow Definition](../configuration/workflowdef)|
| `GET /metadata/workflow/{name}?version=` | Get the workflow definitions| workflow name, version (optional)|
|||
 
## Start A Workflow
### With Input only
See [Start Workflow Request](../gettingstarted/startworkflow/#start-workflow-request).

#### Output
Id of the workflow (GUID)

### With Input and Task Domains
```json
POST /workflow
{
   //JSON payload for Start workflow request
}
```
#### Start workflow request
JSON for start workflow request
```json
{
  "name": "myWorkflow", // Name of the workflow
  "version": 1, // Version
  "correlationId": "corr1", // correlation Id
  "priority": 1, // Priority
  "input": {
	// Input map. 
  },
  "taskToDomain": {
	// Task to domain map
  }
}
```

#### Output
Id of the workflow (GUID)


## Retrieve Workflows
|Endpoint|Description|
|---|---|
|`GET /workflow/{workflowId}?includeTasks=true|false`|Get Workflow State by workflow Id.  If includeTasks is set, then also includes all the tasks executed and scheduled.|
|`GET /workflow/running/{name}`|Get all the running workflows of a given type|
|`GET /workflow/running/{name}/correlated/{correlationId}?includeClosed=true|false&includeTasks=true|false`|Get all the running workflows filtered by correlation Id.  If includeClosed is set, also includes workflows that have completed running.|
|`GET /workflow/search`|Search for workflows.  See Below.|


## Search for Workflows
Conductor uses Elasticsearch for indexing workflow execution and is used by search APIs.

`GET /workflow/search?start=&size=&sort=&freeText=&query=`

|Parameter|Description|
|---|---|
|start|Page number.  Defaults to 0|
|size|Number of results to return|
|sort|Sorting.  Format is: `<fieldname>:ASC` or `<fieldname>:DESC` to sort in ascending or descending order by a field|
|freeText|Elasticsearch supported query. e.g. workflowType:"name_of_workflow"|
|query|SQL like where clause.  e.g. workflowType = 'name_of_workflow'.  Optional if freeText is provided.|

### Output
Search result as described below:
```json
{
  "totalHits": 0,
  "results": [
    {
      "workflowType": "string",
      "version": 0,
      "workflowId": "string",
      "correlationId": "string",
      "startTime": "string",
      "updateTime": "string",
      "endTime": "string",
      "status": "RUNNING",
      "input": "string",
      "output": "string",
      "reasonForIncompletion": "string",
      "executionTime": 0,
      "event": "string"
    }
  ]
}
```

## Manage Workflows
|Endpoint|Description|
|---|---|
|`PUT /workflow/{workflowId}/pause`|Pause.  No further tasks will be scheduled until resumed.  Currently running tasks are not paused.|
|`PUT /workflow/{workflowId}/resume`|Resume normal operations after a pause.|
|`POST /workflow/{workflowId}/rerun`|See Below.|
|`POST /workflow/{workflowId}/restart`|Restart workflow execution from the start.  Current execution history is wiped out.|
|`POST /workflow/{workflowId}/retry`|Retry the last failed task.|
|`PUT /workflow/{workflowId}/skiptask/{taskReferenceName}`|See below.|
|`DELETE /workflow/{workflowId}`|Terminates the running workflow.|
|`DELETE /workflow/{workflowId}/remove`|Deletes the workflow from system.  Use with caution.|

### Rerun
Re-runs a completed workflow from a specific task. 

`POST /workflow/{workflowId}/rerun`

```json
{
  "reRunFromWorkflowId": "string",
  "workflowInput": {},
  "reRunFromTaskId": "string",
  "taskInput": {}
}
```

###Skip Task

Skips a task execution (specified as `taskReferenceName` parameter) in a running workflow and continues forward.
Optionally updating task's input and output as specified in the payload.
`PUT /workflow/{workflowId}/skiptask/{taskReferenceName}?workflowId=&taskReferenceName=`
```json
{
  "taskInput": {},
  "taskOutput": {}
}
```

## Manage Tasks
|Endpoint|Description|
|---|---|
|`GET /tasks/{taskId}`|Get task details.|
|`GET /tasks/queue/all`|List the pending task sizes.|
|`GET /tasks/queue/all/verbose`|Same as above, includes the size per shard|
|`GET /tasks/queue/sizes?taskType=&taskType=&taskType`|Return the size of pending tasks for given task types|
|||

## Polling, Ack and Update Task
These are critical endpoints used to poll for task, send ack (after polling) and finally updating the task result by worker.

|Endpoint|Description|
|---|---|
|`GET /tasks/poll/{taskType}?workerid=&domain=`| Poll for a task. `workerid` identifies the worker that polled for the job and `domain` allows the poller to poll for a task in a specific domain|
|`GET /tasks/poll/batch/{taskType}?count=&timeout=&workerid=&domain`| Poll for a task in a batch specified by `count`.  This is a long poll and the connection will wait until `timeout` or if there is at-least 1 item available, whichever comes first.`workerid` identifies the worker that polled for the job and `domain` allows the poller to poll for a task in a specific domain|
|`POST /tasks`| Update the result of task execution.  See the schema below.|
|`POST /tasks/{taskId}/ack`| Acknowledges the task received AFTER poll by worker.|

### Schema for updating Task Result
```json
{
    "workflowInstanceId": "Workflow Instance Id",
    "taskId": "ID of the task to be updated",
    "reasonForIncompletion" : "If failed, reason for failure",
    "callbackAfterSeconds": 0,
    "status": "IN_PROGRESS|FAILED|COMPLETED",
    "outputData": {
 		//JSON document representing Task execution output     
    }
    
}
```
!!!Info "Acknowledging tasks after poll"
	If the worker fails to ack the task after polling, the task is re-queued and put back in queue and is made available during subsequent poll.
