## Task Definition
Conductor maintains a registry of worker tasks.  A task MUST be registered before being used in a workflow.

**Example**
``` json
{
  "name": "encode_task",
  "retryCount": 3,
  
  "timeoutSeconds": 1200,
  "pollTimeoutSeconds": 3600,
  "inputKeys": [
    "sourceRequestId",
    "qcElementType"
  ],
  "outputKeys": [
    "state",
    "skipped",
    "result"
  ],
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 600,
  "responseTimeoutSeconds": 1200,
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "rateLimitPerFrequency": 50,
  "ownerEmail": "foo@bar.com",
  "description": "Sample Encoding task"
}
```

|field|description|Notes|
|---|---|---|
|name|Task Type. Unique name of the Task that resonates with it's function.|Unique|
|description|Description of the task|optional|
|retryCount|No. of retries to attempt when a Task is marked as failure|defaults to 3|
|retryLogic|Mechanism for the retries|see possible values below|
|retryDelaySeconds|Time to wait before retries|defaults to 60 seconds|
|timeoutPolicy|Task's timeout policy|see possible values below|
|timeoutSeconds|Time in seconds, after which the task is marked as `TIMED_OUT` if not completed after transitioning to `IN_PROGRESS` status for the first time|No timeouts if set to 0|
|pollTimeoutSeconds|Time in seconds, after which the task is marked as `TIMED_OUT` if not polled by a worker|No timeouts if set to 0|
|responseTimeoutSeconds|Must be greater than 0 and less than timeoutSeconds. The task is rescheduled if not updated with a status after this time (heartbeat mechanism). Useful when the worker polls for the task but fails to complete due to errors/network failure.|defaults to 3600|
|backoffScaleFactor|Must be greater than 0. Scale factor for linearity of the backoff|defaults to 1|
|inputKeys|Array of keys of task's expected input.  Used for documenting task's input. See [Using inputKeys and outputKeys](#using-inputkeys-and-outputkeys). |optional|
|outputKeys|Array of keys of task's expected output.  Used for documenting task's output|optional|
|inputTemplate|See [Using inputTemplate](#using-inputtemplate) below.|optional|
|concurrentExecLimit|Number of tasks that can be executed at any given time.|optional|
|rateLimitFrequencyInSeconds, rateLimitPerFrequency|See [Task Rate limits](#task-rate-limits) below.|optional|


### Retry Logic

* FIXED : Reschedule the task after the ```retryDelaySeconds```
* EXPONENTIAL_BACKOFF : Reschedule after ```retryDelaySeconds  * attemptNumber```
* LINEAR_BACKOFF : Reschedule after ```retryDelaySeconds * backoffRate * attemptNumber```
 
### Timeout Policy

* RETRY : Retries the task again
* TIME_OUT_WF : Workflow is marked as TIMED_OUT and terminated
* ALERT_ONLY : Registers a counter (task_timeout)

### Task Concurrent Execution Limits

* `concurrentExecLimit` limits the number of simultaneous Task executions at any point.  
**Example:**  
If you have 1000 task executions waiting in the queue, and 1000 workers polling this queue for tasks, but if you have set `concurrentExecLimit` to 10, only 10 tasks would be given to workers (which would lead to starvation). If any of the workers finishes execution, a new task(s) will be removed from the queue, while still keeping the current execution count to 10.

### Task Rate limits

* `rateLimitFrequencyInSeconds` and `rateLimitPerFrequency` should be used together.
* `rateLimitFrequencyInSeconds` sets the "frequency window", i.e the `duration` to be used in `events per duration`. Eg: 1s, 5s, 60s, 300s etc.
* `rateLimitPerFrequency`defines the number of Tasks that can be given to Workers per given "frequency window".  
**Example:**  
Let's set `rateLimitFrequencyInSeconds = 5`, and `rateLimitPerFrequency = 12`. This means, our frequency window is of 5 seconds duration, and for each frequency window, Conductor would only give 12 tasks to workers. So, in a given minute, Conductor would only give 12*(60/5) = 144 tasks to workers irrespective of the number of workers that are polling for the task.  
Note that unlike `concurrentExecLimit`, rate limiting doesn't take into account tasks already in progress/completed. Even if all the previous tasks are executed within 1 sec, or would take a few days, the new tasks are still given to workers at configured frequency, 144 tasks per minute in above example.   
Note: Rate limiting is only supported for the Redis-persistence module and is not available with other persistence layers.

### Using inputKeys and outputKeys

* `inputKeys` and `outputKeys` can be considered as parameters and return values for the Task. 
* Consider the task Definition as being represented by an interface: ```(value1, value2 .. valueN) someTaskDefinition(key1, key2 .. keyN);```
* However, these parameters are not strictly enforced at the moment. Both `inputKeys` and `outputKeys` act as a documentation for task re-use. The tasks in workflow need not define all of the keys in the task definition.
* In the future, this can be extended to be a strict template that all task implementations must adhere to, just like interfaces in programming languages.

### Using inputTemplate

* `inputTemplate` allows to define default values, which can be overridden by values provided in Workflow.
* Eg: In your Task Definition, you can define your inputTemplate as:

```json
"inputTemplate": {
    "url": "https://some_url:7004"
}
```

* Now, in your workflow Definition, when using above task, you can use the default `url` or override with something else in the task's `inputParameters`.

```json
"inputParameters": {
    "url": "${workflow.input.some_new_url}"
}
```
