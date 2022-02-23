---
sidebar_position: 1
---

# Wait
```json
"type" : "WAIT"
```
## Introduction

WAIT is used when the workflow needs to be paused for an external signal to continue.

## Use Cases
WAIT is used when the workflow needs to wait and pause for an external signal such as a human intervention 
(like manual approval) or an event coming from external source such as Kafka, SQS or Conductor's internal queueing mechanism.

Some use cases where WAIT task is used:
1. To add a human approval task.  When the task is approved/rejected by human WAIT task is updated using `POST /tasks` API to completion.
2. To wait for and external signal coming from an event queue mechanism supported by Conductor

## Configuration
* taskType: WAIT
* There are no other configurations required

## Ending a WAIT

To conclude a WAIT task, there are three endpoints that can be used. You'll need the  ```workflowId```, ```taskRefName``` or ```taskId``` and the task status (generally ```COMPLETED``` or ```FAILED```).

1. POST ```/api/tasks```
2. POST ```api/queue/update/{workflowId}/{taskRefName}/{status}``` 
3. POST ```api/queue/update/{workflowId}/task/{taskId}/{status}``` 