# Extending System Tasks

[System tasks](../../../documentation/configuration/workflowdef/systemtasks/index.md) allow Conductor to run basic tasks on the server without needing to build and deploy custom workers. Since system tasks run on the Conductor server, there is no need to create task definitions before using them.

However, a task definition may be useful for adding additional parameters to a system task, so as to gain the desired behavior.

## Example

This example workflow consists of a single [HTTP Task](../../../documentation/configuration/workflowdef/systemtasks/http-task.md). When triggered, the HTTP task will return the weather in Beverly Hills, CA (90210).

```json
{
  "name": "get_weather_90210",
  "version": 1,
  "tasks": [
    {
      "name": "get_weather_90210",
      "taskReferenceName": "get_weather_90210",
      "inputParameters": {
        "http_request": {
          "uri": "https://weatherdbi.herokuapp.com/data/weather/90210",
          "method": "GET",
          "connectionTimeOut": 1300,
          "readTimeOut": 1300
        }
      },
      "type": "HTTP"
    }
  ],
  "inputParameters": [],
  "outputParameters": {
    "data": "${get_weather_ref.output.response.body.currentConditions.comment}"
  },
  "schemaVersion": 2,
  "ownerEmail": "conductor@example.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 0
}
```

To get the weather, the HTTP task makes a call to a third-party weather API. However, an API call can sometimes fail due to an issue on the remote server. Retrying the task can help result in a successful response. 

To configure automatic retries for the HTTP task, you can create a task definition with the same name:

```json
{

  "createdBy": "user@example.com",
  "name": "get_weather_90210",
  "description": "extends HTTP task",
  "retryCount": 3,
  "timeoutSeconds": 5,
  "inputKeys": [],
  "outputKeys": [],
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 5,
  "responseTimeoutSeconds": 5,
  "inputTemplate": {},
  "rateLimitPerFrequency": 0,
  "rateLimitFrequencyInSeconds": 1
}
```

The task definition configures the `get_weather_90210` task with the following retry policy: `retryCount: 3`, `retryDelaySeconds: 5`, and `retryLogic: FIXED`.

When running the workflow again, the HTTP task will no run up to four times (fail once, then retry thrice), with a fixed 5 second delay between attempts.
