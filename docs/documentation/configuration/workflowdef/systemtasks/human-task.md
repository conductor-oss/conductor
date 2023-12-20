# Human Task
```json
"type" : "HUMAN"
```

The `HUMAN` task is used when the workflow needs to be paused for an external signal to continue. It acts as a gate that 
remains in the `IN_PROGRESS` state until marked as ```COMPLETED``` or ```FAILED``` by an external trigger.

## Use Cases
The HUMAN is can be used when the workflow needs to pause and wait for human intervention, such as manual approval.
It can also be used with an event coming from external source such as Kafka, SQS or Conductor's internal queueing mechanism.

## Configuration
No parameters are required

## Completing
### Task Update API
To conclude a `HUMAN` task, the `POST {{ api_prefix }}/tasks` [API](../../../api/task.md) can be used.

You'll need to provide the`taskId`, the task status (generally `COMPLETED` or `FAILED`), and the desired task output.

### Event Handler
If SQS integration is enabled, the `HUMAN` task can also be resolved using the `{{ api_prefix }}/queue` API.

You'll need the  `workflowId` and `taskRefName` or `taskId`.

2. POST `{{ api_prefix }}/queue/update/{workflowId}/{taskRefName}/{status}` 
3. POST `{{ api_prefix }}/queue/update/{workflowId}/task/{taskId}/{status}` 

An [event handler](../../eventhandlers.md) using the `complete_task` action can also be configured.

Any parameter that is sent in the body of the POST message will be repeated as the output of the task.  For example, if we send a COMPLETED message as follows:

```bash
curl -X "POST" "{{ server_host }}{{ api_prefix }}/queue/update/{workflowId}/waiting_around_ref/COMPLETED" -H 'Content-Type: application/json' -d '{"data_key":"somedatatoWait1","data_key2":"somedatatoWAit2"}'
```

The output of the task will be:

```json
{
  "data_key":"somedatatoWait1",
  "data_key2":"somedatatoWAit2"
}
```



