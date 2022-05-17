# Extending System Tasks

[System tasks](/configuration/systask.html) allow Conductor to run simple tasks on the server - removing the need to build (and deploy) workers for basic tasks.  This allows for automating more mundane tasks without building specific microservices for them.

However, sometimes it might be necessary to add additional parameters to a System Task to gain the behavior that is desired.

## Example HTTP Task

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
      "type": "HTTP",
      "decisionCases": {},
      "defaultCase": [],
      "forkTasks": [],
      "startDelay": 0,
      "joinOn": [],
      "optional": false,
      "defaultExclusiveJoinTask": [],
      "asyncComplete": false,
      "loopOver": []
    }
  ],
  "inputParameters": [],
  "outputParameters": {
    "data": "${get_weather_ref.output.response.body.currentConditions.comment}"
  },
  "schemaVersion": 2,
  "restartable": true,
  "workflowStatusListenerEnabled": false,
  "ownerEmail": "conductor@example.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 0,
  "variables": {},
  "inputTemplate": {}
}

```

This very simple workflow has a single HTTP Task inside.  No parameters need to be passed, and when run, the HTTP task will return the weather in Beverly Hills, CA (Zip code = 90210).

> This API has a very slow response time. In the HTTP task, the connection is set to time out after 1300ms, which is *too short* for this API, resulting in a timeout.  This API *will* work if we allowed for a longer timeout, but in order to demonstrate adding retries to the HTTP Task, we will artificially force the API call to fail.

When this workflow is run - it fails, as expected.

Now, sometimes an API call might fail due to an issue on the remote server, and retrying the call will result in a response.  With many Conductor tasks,  ```retryCount```, ```retryDelaySeconds``` and ```retryLogic``` fields can be applied to retry the worker (with the desired parameters).

By default, the [HTTP Task](/reference-docs/http-task.html) does not have ```retryCount```, ```retryDelaySeconds``` or ```retryLogic``` built in.  Attempting to add these parameters to a HTTP Task results in an error.

## The Solution

We can create a task with the same name with the desired parameters.  Defining the following task (note that the ```name``` is identical to the one in the workflow):

```json
{

  "createdBy": "",
  "name": "get_weather_90210",
  "description": "editing HTTP task",
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

We've added the three parameters: ```retryCount: 3, retryDelaySeconds: 5, retryLogic: FIXED```

The ```get_weather_90210``` task will now run 4 times (it will fail once, and then retry 3 times), with a ```FIXED``` 5 second delay between attempts.

Re-running the task (and looking at the timeline view) shows that this is what occurs.  There are 4 attempts, with a 5 second delay between them.

If we change the ```retryLogic``` to EXPONENTIAL_BACKOFF, the delay between attempts grows exponentially:

1. 5*2^0 = 5 seconds
2. 5*2^1 = 10 seconds
3. 5*2^2 = 20 seconds
