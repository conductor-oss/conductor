---
sidebar_position: 1
---

# Human
```json
"type" : "HUMAN"
```
### Introduction

HUMAN is used when the workflow needs to be paused for an external signal by a human to continue.

### Use Cases
HUMAN is used when the workflow needs to wait and pause for  human intervention 
(like manual approval) or an event coming from external source such as Kafka, SQS or Conductor's internal queueing mechanism.

Some use cases where HUMAN task is used:
1. To add a human approval task.  When the task is approved/rejected by HUMAN task is updated using `POST /tasks` API to completion.


### Configuration
* taskType: HUMAN
* There are no other configurations required


