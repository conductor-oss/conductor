# Human Task
```json
"type" : "HUMAN"
```

The Human task (`HUMAN`) is used to pause the workflow and wait for an external signal. It acts as a gate that remains in IN_PROGRESS until marked as COMPLETED or FAILED by an external trigger.

The Human task can be used when the workflow needs to pause and wait for human intervention, such as manual approval. It can also be used with an event coming from external source such as Kafka, SQS, or Conductor's internal queueing mechanism.

## Task parameters

No parameters are required to configure the Human task.

## JSON configuration

Here is the task configuration for a Human task.

```json
{
	"name": "human",
  "taskReferenceName": "human_ref",
	"inputParameters": {},
	"type": "HUMAN"
}
```

## Completing the Human task

There are several ways to complete the Human task:

- Using the Task Update API
- Using an event handler


### Task Update API
Use the Task Update API (`POST api/tasks`) to complete a Human task. Provide the `taskId`, the task status, and the desired task output.

### Event handler
If SQS integration is enabled, the Human task can also be resolved using the Update Queue APIs:

1. `POST api/queue/update/{workflowId}/{taskRefName}/{status}` 
2. `POST api/queue/update/{workflowId}/task/{taskId}/{status}` 

Any parameter that is sent in the body of the POST message will be repeated as the output of the task.  For example, if we send a COMPLETED message as follows:

```bash
curl -X "POST" "{{ server_host }}{{ api_prefix }}/queue/update/{workflowId}/waiting_around_ref/COMPLETED" -H 'Content-Type: application/json' -d '{"data_key":"somedatatoWait1","data_key2":"somedatatoWAit2"}'
```

The output of the Human task will be:

```json
{
  "data_key":"somedatatoWait1",
  "data_key2":"somedatatoWAit2"
}
```


Alternatively, an [event handler](../../eventhandlers.md) using the `complete_task` action can also be configured.