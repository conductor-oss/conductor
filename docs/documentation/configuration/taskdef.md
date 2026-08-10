---
description: "Task definition schema in Conductor — configure retry logic, exponential backoff, timeouts, rate limiting, and concurrency for durable workflow execution."
---

# Task Definition

Task Definitions are used to register SIMPLE tasks (workers). Conductor maintains a registry of user task types. A task type MUST be registered before being used in a workflow.

This should not be confused with [*Task Configurations*](workflowdef/index.md#task-configurations) which are part of the Workflow Definition, and are iterated in the `tasks` property in the definition.


## Schema

| Field                       | Type               | Description                                                                                                                                                                                                   | Notes                                                                            |
| :-------------------------- | :----------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :------------------------------------------------------------------------------- |
| name                        | string             | Task Name. Unique name of the Task that resonates with its function.                                                                                                                                          | Must be unique                                                                   |
| description                 | string             | Description of the task.                                                                                                                                                                                       | Optional                                                                         |
| retryCount                  | number             | Number of retries to attempt when a Task is marked as failure.                                                                                                                                                 | Defaults to 3 with maximum allowed capped at 10                                  |
| retryLogic                  | string (enum)      | Mechanism for the retries.                                                                                                                                                                                     | See [Retry Logic](#retry-logic)                                                  |
| retryDelaySeconds           | number             | Base delay before the first retry. The meaning varies by `retryLogic`.                                                                                                                                        | Defaults to 60 seconds                                                           |
| maxRetryDelaySeconds        | number             | Maximum delay between retries, in seconds. Caps the computed delay for `EXPONENTIAL_BACKOFF` and `LINEAR_BACKOFF` so delays never grow beyond this value. `0` disables the cap.                              | Defaults to 0 (no cap). See [Retry Logic](#retry-logic)                          |
| backoffJitterMs             | number             | Adds a random jitter of up to this many milliseconds to each retry delay. Spreads simultaneous retries across time to prevent thundering herd. `0` disables jitter.                                          | Defaults to 0 (no jitter). See [Retry Logic](#retry-logic)                       |
| totalTimeoutSeconds         | number             | Maximum wall-clock time (in seconds) across all retry attempts combined. Once exceeded, the task fails immediately with no further retries, regardless of `retryCount`. `0` disables this limit.             | Defaults to 0 (no limit). See [Timeout scenarios](../../../devguide/architecture/tasklifecycle.md#total-timeout)  |
| timeoutPolicy               | string (enum)      | Task's timeout policy.                                                                                                                                                                                         | Defaults to `TIME_OUT_WF`; See [Timeout Policy](#timeout-policy)                 |
| timeoutSeconds              | number             | Time in seconds, after which the task is marked as `TIMED_OUT` if it has not reached a terminal state after transitioning to `IN_PROGRESS` status for the first time.                                                                | No timeouts if set to 0                                                          |
| responseTimeoutSeconds      | number             | If greater than 0, the task is rescheduled if not updated with a status after this time (heartbeat mechanism). Useful when the worker polls for the task but fails to complete due to errors/network failure. | Defaults to 600                                                                 |
| pollTimeoutSeconds          | number             | Time in seconds, after which the task is marked as `TIMED_OUT` if not polled by a worker.                                                                                                                     | No timeouts if set to 0                                                          |
| inputKeys                   | array of string(s) | Array of keys of task's expected input. Used for documenting task's input.                                                                                                                                    | Optional. See [Using inputKeys and outputKeys](#using-inputkeys-and-outputkeys). |
| outputKeys                  | array of string(s) | Array of keys of task's expected output. Used for documenting task's output.                                                                                                                                  | Optional. See [Using inputKeys and outputKeys](#using-inputkeys-and-outputkeys). |
| inputTemplate               | object             | Define default input values.                                                                                                                                                                                  | Optional. See [Using inputTemplate](#using-inputtemplate)                        |
| concurrentExecLimit         | number             | Number of tasks that can be executed at any given time.                                                                                                                                                        | Optional                                                                         |
| rateLimitFrequencyInSeconds | number             | Sets the rate limit frequency window.                                                                                                                                                                         | Optional. See [Task Rate limits](#task-rate-limits)                              |
| rateLimitPerFrequency       | number             | Sets the max number of tasks that can be given to workers within window.                                                                                                                                      | Optional. See [Task Rate limits](#task-rate-limits) below                        |
| ownerEmail                  | string             | Email address of the team that owns the task.                                                                                                                                                                  | Required                                                                         |

### Retry Logic

The `retryLogic` field controls how the delay between retries is computed. The final delay applied is:

```
delay = clamp(computedDelay, 0, maxRetryDelaySeconds)  +  random(0, backoffJitterMs) ms
```

where `clamp` only applies when `maxRetryDelaySeconds > 0`.

| Value | Delay formula | Notes |
| :--- | :--- | :--- |
| `FIXED` | `retryDelaySeconds` | Constant delay every retry. |
| `EXPONENTIAL_BACKOFF` | `retryDelaySeconds × 2^attemptNumber` | Doubles each attempt. Cap with `maxRetryDelaySeconds` to avoid runaway delays. |
| `LINEAR_BACKOFF` | `retryDelaySeconds × backoffScaleFactor × attemptNumber` | Grows linearly. `backoffScaleFactor` defaults to 1. |

**`maxRetryDelaySeconds`** — caps the computed delay so it never exceeds this value. Example with `EXPONENTIAL_BACKOFF`, `retryDelaySeconds=1`, `maxRetryDelaySeconds=3`:

| Attempt | Raw delay | After cap |
| :--- | :--- | :--- |
| 0 | 1s | 1s |
| 1 | 2s | 2s |
| 2 | 4s | 3s |
| 3+ | 8s+ | 3s |

**`backoffJitterMs`** — adds a uniform random value in `[0, backoffJitterMs]` milliseconds to the final delay. This spreads retries from multiple failing workers across time (thundering herd prevention). Example: `retryDelaySeconds=2`, `backoffJitterMs=1000` → each retry fires between 2 000 ms and 3 000 ms after failure.

### Timeout Policy

* `RETRY`: Retries the task again
* `TIME_OUT_WF`: Workflow is marked as TIMED_OUT and terminated. This is the default value.
* `ALERT_ONLY`: Registers a counter (task_timeout)

### Task Concurrent Execution Limits

`concurrentExecLimit` limits the number of simultaneous Task executions at any point.

**Example**
You have 1000 task executions waiting in the queue, and 1000 workers polling this queue for tasks, but if you have set `concurrentExecLimit` to 10, only 10 tasks would be given to workers (which would lead to starvation). If any of the workers finishes execution, a new task(s) will be removed from the queue, while still keeping the current execution count to 10.

### Task Rate Limits

!!! note "Rate Limiting"
    Rate limiting is only supported for the Redis-persistence module and is not available with other persistence layers.

* `rateLimitFrequencyInSeconds` and `rateLimitPerFrequency` should be used together.
* `rateLimitFrequencyInSeconds` sets the "frequency window", i.e the `duration` to be used in `events per duration`. Eg: 1s, 5s, 60s, 300s etc.
* `rateLimitPerFrequency`defines the number of Tasks that can be given to Workers per given "frequency window". No rate limit if set to 0.

**Example**
Let's set `rateLimitFrequencyInSeconds = 5`, and `rateLimitPerFrequency = 12`. This means our frequency window is of 5 seconds duration, and for each frequency window, Conductor would only give 12 tasks to workers. So, in a given minute, Conductor would only give 12*(60/5) = 144 tasks to workers irrespective of the number of workers that are polling for the task.

Note that unlike `concurrentExecLimit`, rate limiting doesn't take into account tasks already in progress or a terminal state. Even if all the previous tasks are executed within 1 sec, or would take a few days, the new tasks are still given to workers at configured frequency, 144 tasks per minute in above example.


### Using `inputKeys` and `outputKeys`

* `inputKeys` and `outputKeys` can be considered as parameters and return values for the Task.
* Consider the task Definition as being represented by an interface: ```(value1, value2 .. valueN) someTaskDefinition(key1, key2 .. keyN);```.
* However, these parameters are not strictly enforced at the moment. Both `inputKeys` and `outputKeys` act as a documentation for task re-use. The tasks in workflow need not define all of the keys in the task definition.
* In the future, this can be extended to be a strict template that all task implementations must adhere to, just like interfaces in programming languages.

### Using `inputTemplate`

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

## Retry configuration examples

### Retrying a flaky external API call

```json
{
  "name": "call_payment_api",
  "retryCount": 5,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 2,
  "maxRetryDelaySeconds": 60,
  "backoffJitterMs": 2000,
  "responseTimeoutSeconds": 30,
  "timeoutSeconds": 300,
  "timeoutPolicy": "RETRY",
  "ownerEmail": "payments@example.com"
}
```

Retries up to 5 times with delays 2s, 4s, 8s, 16s, 32s — capped at 60s — plus up to 2 seconds of random jitter on each attempt. Prevents hammering a degraded payment provider.

### Bounded retry budget with `totalTimeoutSeconds`

```json
{
  "name": "process_order",
  "retryCount": 10,
  "retryLogic": "FIXED",
  "retryDelaySeconds": 5,
  "totalTimeoutSeconds": 120,
  "timeoutPolicy": "TIME_OUT_WF",
  "ownerEmail": "orders@example.com"
}
```

Retries every 5 seconds, but the entire sequence — all attempts combined — must finish within 2 minutes. Even if `retryCount` isn't exhausted, the task fails once the 2-minute budget is consumed.

### High-throughput worker with jitter

```json
{
  "name": "send_notification",
  "retryCount": 3,
  "retryLogic": "FIXED",
  "retryDelaySeconds": 1,
  "backoffJitterMs": 3000,
  "concurrentExecLimit": 500,
  "ownerEmail": "notifications@example.com"
}
```

When thousands of notifications fail simultaneously (e.g., downstream outage), jitter spreads the retries across a 3-second window instead of all hammering the service at once.

## Complete Example

``` json
{
  "name": "encode_task",
  "retryCount": 3,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 10,
  "maxRetryDelaySeconds": 120,
  "backoffJitterMs": 5000,
  "totalTimeoutSeconds": 600,
  "timeoutSeconds": 1200,
  "timeoutPolicy": "TIME_OUT_WF",
  "responseTimeoutSeconds": 3600,
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
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "rateLimitPerFrequency": 50,
  "ownerEmail": "foo@bar.com",
  "description": "Sample Encoding task"
}
```
