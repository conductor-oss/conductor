---
description: "Configure Wait tasks in Conductor to pause workflow execution for a set duration or until a specific timestamp. Supports durable code execution patterns."
---

# Wait Task
```json
"type" : "WAIT"
```

The Wait task (`WAIT`) is used to pause the workflow until a certain duration or timestamp. It is a a no-op task that will remain IN_PROGRESS until the configured time has passed, at which point it will be marked as COMPLETED.


## Task parameters

Use these parameters inside `inputParameters` in the Wait task configuration. You can configure the Wait task using either `duration` or `until` in `inputParameters`.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| duration | String | The wait duration in the format `x days y hours z minutes aa seconds`. The accepted units in this field are: <ul><li>**days**, or **d** for days</li> <li>**hours**, **hrs**, or **h** for hours</li> <li>**minutes**, **mins**, or **m** for minutes</li> <li>**seconds**, **secs**, or **s** for seconds</li></ul>   | Required for duration wait type. |
| until    | String | The datetime and timezone to wait until, in one of the following formats: <ul><li>yyyy-MM-dd HH:mm z</li> <li>yyyy-MM-dd HH:mm</li> <li>yyyy-MM-dd</li></ul> <br/> For example, 2024-04-30 15:20 GMT+04:00. | Required for until wait type. |

## JSON configuration

Here is the task configuration for a Wait task.

### Using `duration`

```json
{
	"name": "wait",
    "taskReferenceName": "wait_ref",
	"inputParameters": {
		"duration": "10m20s"
	},
	"type": "WAIT"
}
```

### Using `until`

```json
{
	"name": "wait",
    "taskReferenceName": "wait_ref",
	"inputParameters": {
		"until": "2022-12-31 11:59"
	},
	"type": "WAIT"
}
```

## Examples

### Wait for a fixed duration

Wait for 30 seconds before proceeding:

```json
{
  "name": "wait_30s",
  "taskReferenceName": "wait_30s_ref",
  "type": "WAIT",
  "inputParameters": {
    "duration": "30 seconds"
  }
}
```

Wait for 2 hours and 30 minutes:

```json
{
  "name": "wait_2h30m",
  "taskReferenceName": "wait_2h30m_ref",
  "type": "WAIT",
  "inputParameters": {
    "duration": "2 hours 30 minutes"
  }
}
```

### Wait until a specific date/time

Wait until a specific timestamp:

```json
{
  "name": "wait_until_deadline",
  "taskReferenceName": "wait_deadline_ref",
  "type": "WAIT",
  "inputParameters": {
    "until": "2025-06-15 09:00 GMT+00:00"
  }
}
```

Wait until a date/time provided as workflow input:

```json
{
  "name": "wait_until_input_time",
  "taskReferenceName": "wait_input_ref",
  "type": "WAIT",
  "inputParameters": {
    "until": "${workflow.input.scheduledTime}"
  }
}
```

### Wait for an external signal (no duration)

When no `duration` or `until` is specified, the Wait task pauses indefinitely until it is completed externally via the Task Update API or an event handler:

```json
{
  "name": "wait_for_signal",
  "taskReferenceName": "signal_ref",
  "type": "WAIT"
}
```

Complete the task externally:

```shell
curl -X POST 'http://localhost:8080/api/tasks/{workflowId}/signal_ref/COMPLETED/sync' \
  -H 'Content-Type: application/json' \
  -d '{"approvedBy": "admin"}'
```

## Overriding the Wait task

The Task Update API (`POST api/tasks`) can be used to set the status of the Wait task to COMPLETED prior to the configured wait duration or timestamp.

If the workflow does not require a specific wait duration or timestamp, it is recommended to directly use the [Human](human-task.md) task instead, which waits for an external trigger.