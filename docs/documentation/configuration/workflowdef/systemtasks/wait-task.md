# Wait Task
The WAIT task is a no-op task that will remain `IN_PROGRESS` until after a certain duration or timestamp, at which point it will be marked as `COMPLETED`.

```json
"type" : "WAIT"
```

## Configuration
The `WAIT` task is configured using **either** `duration` **or** `until` in `inputParameters`.

### inputParameters
| name     | type   | description             |
| -------- | ------ | ----------------------- |
| duration | String | Duration to wait for    |
| until    | String | Timestamp to wait until |

### Wait For time duration

Format duration as ```XhYmZs```, using the `duration` key.

```json
{
	"type": "WAIT",
	"inputParameters": {
		"duration": "10m20s"
	}
}
```

### Wait until specific date/time

Specify the timestamp using one of the formats, using the `until` key.

1. ```yyyy-MM-dd HH:mm```
2. ```yyyy-MM-dd HH:mm z```
3. ```yyyy-MM-dd```

```json
{
	"type": "WAIT",
	"inputParameters": {
		"until": "2022-12-31 11:59"
	}
}
```
## External Triggers

The task endpoint `POST {{ api_prefix }}/tasks` can be used to update the status of a task to COMPLETED prior to the configured timeout. This is
same technique as prescribed for the [HUMAN](human-task.md#completing) task.

For cases where no timeout is necessary it is recommended that you use the [HUMAN](human-task.md) task directly.

