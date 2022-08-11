---
sidebar_position: 1
---

# Wait
```json
"name": "waiting_task",
"taskReferenceName":"waiting_task_ref",
"type" : "WAIT"
```
## Introduction

WAIT is used when the workflow needs to be paused for an external signal to continue.

## Use Cases
WAIT is used when the workflow needs to wait and pause for an external signal such as a human intervention 
(like manual approval) or an event coming from external source such as Kafka, SQS or Conductor's internal queueing mechanism.

Some use cases where WAIT task is used:

1. Wait for a certain amount of time (e.g. 2 minutes) or until a certain date time (e.g. 12/25/2022 00:00)
2. To wait for and external signal coming from an event queue mechanism supported by Conductor

## Configuration
* taskType: WAIT
* Wait for a specific amount of time
format: short: **D**d**H**h**M**m or full:  **D**days**H**hours**M**minutes 
* The following are the accepted units: *days*, *d*, *hrs*, *hours*, *h*, *minutes*, *mins*, *m*, *seconds*, *secs*, *s*
```json
{
  "taskType": "WAIT",
  "inputParameters": {
    "duration": "2 days 3 hours"  
  }
}
```
* Wait until specific date/time
* e.g. the following Wait task remains blocked until Dec 25, 2022 9am PST
* The date/time can be supplied in one of the following formats: 
**yyyy-MM-dd HH:mm**, **yyyy-MM-dd HH:mm**, **yyyy-MM-dd**
```json
{
  "name":"wait_until_date",
  "taskReferenceName":"wait_until_date_ref",
  "taskType": "WAIT",
  "inputParameters": {
    "until": "2022-12-25 09:00 PST"
  }
}
```

## Ending a WAIT when there is no time duration specified

To conclude a WAIT task, there are three endpoints that can be used. 
You'll need the  ```workflowId```, ```taskRefName``` or ```taskId``` and the task status (generally ```COMPLETED``` or ```FAILED```).

1. POST ```/api/tasks```
2. POST ```api/queue/update/{workflowId}/{taskRefName}/{status}``` 
3. POST ```api/queue/update/{workflowId}/task/{taskId}/{status}``` 

Any parameter that is sent in the body of the POST message will be repeated as the output of the task.  For example, if we send a COMPLETED message as follows:

```bash
curl -X "POST" "https://play.orkes.io/api/queue/update/{workflowId}/waiting_around_ref/COMPLETED" -H 'Content-Type: application/json' -d '{"data_key":"somedatatoWait1","data_key2":"somedatatoWAit2"}'
```

The output of the task will be:

```json
{
  "data_key":"somedatatoWait1",
  "data_key2":"somedatatoWAit2"
}
```