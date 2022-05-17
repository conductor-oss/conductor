---
sidebar_position: 1
---

# Task Configurations

Refer to [Task Definitions](/configuration/taskdef.html) for details on how to configure task definitions

### Example

Here is a task template payload with commonly used fields:

```json
{
  "createdBy": "user",
  "name": "sample_task_name_1",
  "description": "This is a sample task for demo",
  "responseTimeoutSeconds": 10,
  "timeoutSeconds": 30,
  "inputKeys": [],
  "outputKeys": [],
  "timeoutPolicy": "TIME_OUT_WF",
  "retryCount": 3,
  "retryLogic": "FIXED",
  "retryDelaySeconds": 5,
  "inputTemplate": {},
  "rateLimitPerFrequency": 0,
  "rateLimitFrequencyInSeconds": 1
}
```

### Best Practices

1. Refer to [Task Timeouts](/how-tos/Tasks/task-timeouts.html) for additional information on how the various timeout settings work
2. Refer to [Monitoring Task Queues](/how-tos/Tasks/monitoring-task-queues.html) on how to monitor task queues
