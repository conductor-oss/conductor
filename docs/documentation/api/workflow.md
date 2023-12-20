# Workflow API

## Retrieve Workflows
| Endpoint                                                                    | Description                                   |
|-----------------------------------------------------------------------------|-----------------------------------------------|
| `GET {{ api_prefix }}/workflow/{workflowId}?includeTasks=true                               | false`                                        |Get Workflow State by workflow Id.  If includeTasks is set, then also includes all the tasks executed and scheduled.|
| `GET {{ api_prefix }}/workflow/running/{name}`                                              | Get all the running workflows of a given type |
| `GET {{ api_prefix }}/workflow/running/{name}/correlated/{correlationId}?includeClosed=true | false&includeTasks=true                       |false`|Get all the running workflows filtered by correlation Id.  If includeClosed is set, also includes workflows that have completed running.|
| `GET {{ api_prefix }}/workflow/search`                                                      | Search for workflows.  See Below.             |


## Workflow Search
Conductor uses Elasticsearch for indexing workflow execution and is used by search APIs.

`GET {{ api_prefix }}/workflow/search?start=&size=&sort=&freeText=&query=`

| Parameter | Description                                                                                                      |
|-----------|------------------------------------------------------------------------------------------------------------------|
| start     | Page number.  Defaults to 0                                                                                      |
| size      | Number of results to return                                                                                      |
| sort      | Sorting.  Format is: `ASC:<fieldname>` or `DESC:<fieldname>` to sort in ascending or descending order by a field |
| freeText  | Elasticsearch supported query. e.g. workflowType:"name_of_workflow"                                              |
| query     | SQL like where clause.  e.g. workflowType = 'name_of_workflow'.  Optional if freeText is provided.               |

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
| Endpoint                                                  | Description                                                                                        |
|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `PUT {{ api_prefix }}/workflow/{workflowId}/pause`                        | Pause.  No further tasks will be scheduled until resumed.  Currently running tasks are not paused. |
| `PUT {{ api_prefix }}/workflow/{workflowId}/resume`                       | Resume normal operations after a pause.                                                            |
| `POST {{ api_prefix }}/workflow/{workflowId}/rerun`                       | See Below.                                                                                         |
| `POST {{ api_prefix }}/workflow/{workflowId}/restart`                     | Restart workflow execution from the start.  Current execution history is wiped out.                |
| `POST {{ api_prefix }}/workflow/{workflowId}/retry`                       | Retry the last failed task.                                                                        |
| `PUT {{ api_prefix }}/workflow/{workflowId}/skiptask/{taskReferenceName}` | See below.                                                                                         |
| `DELETE {{ api_prefix }}/workflow/{workflowId}`                           | Terminates the running workflow.                                                                   |
| `DELETE {{ api_prefix }}/workflow/{workflowId}/remove`                    | Deletes the workflow from system.  Use with caution.                                               |

### Rerun
Re-runs a completed workflow from a specific task. 

`POST {{ api_prefix }}/workflow/{workflowId}/rerun`

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
`PUT {{ api_prefix }}/workflow/{workflowId}/skiptask/{taskReferenceName}?workflowId=&taskReferenceName=`
```json
{
  "taskInput": {},
  "taskOutput": {}
}
```
