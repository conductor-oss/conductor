# Task API

## Manage Tasks
| Endpoint                                              | Description                                           |
|-------------------------------------------------------|-------------------------------------------------------|
| `GET {{ api_prefix }}/tasks/{taskId}`                                 | Get task details.                                     |
| `GET {{ api_prefix }}/tasks/queue/all`                                | List the pending task sizes.                          |
| `GET {{ api_prefix }}/tasks/queue/all/verbose`                        | Same as above, includes the size per shard            |
| `GET {{ api_prefix }}/tasks/queue/sizes?taskType=&taskType=&taskType` | Return the size of pending tasks for given task types |
|||

## Polling, Ack and Update Task
These endpoints are used by the worker to poll for task, send ack (after polling) and finally updating the task result. They typically should not be invoked manually.

| Endpoint                                                            | Description                                                                                                                                                                                                                                                                                                        |
|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET {{ api_prefix }}/tasks/poll/{taskType}?workerid=&domain=`                      | Poll for a task. `workerid` identifies the worker that polled for the job and `domain` allows the poller to poll for a task in a specific domain                                                                                                                                                                   |
| `GET {{ api_prefix }}/tasks/poll/batch/{taskType}?count=&timeout=&workerid=&domain` | Poll for a task in a batch specified by `count`.  This is a long poll and the connection will wait until `timeout` or if there is at-least 1 item available, whichever comes first.`workerid` identifies the worker that polled for the job and `domain` allows the poller to poll for a task in a specific domain |
| `POST {{ api_prefix }}/tasks`                                                       | Update the result of task execution.  See the schema below.                                                                                                                                                                                                                                                        |


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
