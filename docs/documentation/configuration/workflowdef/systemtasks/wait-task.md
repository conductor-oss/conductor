# Wait Task
```json
"type" : "WAIT"
```

The Wait task (`WAIT`) is used to pause the workflow until a certain duration or timestamp. It is a a no-op task that will remain IN_PROGRESS until the configured time has passed, at which point it will be marked as COMPLETED.


## Task parameters

Use these parameters inside `inputParameters` in the Wait task configuration. You can configure the Wait task using either `duration` or `until` in `inputParameters`.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.duration | String | The wait duration in the format `x days y hours z minutes aa seconds`. The accepted units in this field are: <ul><li>**days**, or **d** for days</li> <li>**hours**, **hrs**, or **h** for hours</li> <li>**minutes**, **mins**, or **m** for minutes</li> <li>**seconds**, **secs**, or **s** for seconds</li></ul>   | Required for duration wait type. |
| inputParameters.until    | String | The datetime and timezone to wait until, in one of the following formats: <ul><li>yyyy-MM-dd HH:mm z</li> <li>yyyy-MM-dd HH:mm</li> <li>yyyy-MM-dd</li></ul> <br/> For example, 2024-04-30 15:20 GMT+04:00. | Required for until wait type. |

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

## Overriding the Wait task

In edge cases, the Task Update API (`POST api/tasks`) can be used to set the status of the Wait task to COMPLETED prior to the configured wait duration or timestamp.

However, if the workflow does not require a specific wait duration or timestamp, it is recommended to directly use the [Human](human-task.md) task instead, which waits for an external trigger.