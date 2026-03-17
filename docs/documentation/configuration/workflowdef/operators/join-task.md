
# Join
```json
"type" : "JOIN"
```

A Join task is used in conjunction with a [Fork](fork-task.md) or [Dynamic Fork](dynamic-fork-task.md) task to wait on and join the forks. The Join task also aggregates the forked tasks' outputs for subsequent use.

The Join task's behavior varies based on the preceding fork type:

* When used with a Static Fork task, the Join task waits for a provided list of the forked tasks to be completed before proceeding with the next task. 
* When used with a Dynamic Fork task, it implicitly waits for all the forked tasks to complete.


## Task parameters

When used with a Static Fork, use these parameters in top level of the Join task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| joinOn    | List[String] | (For Static Forks only) A list of task reference names that the Join task will wait for completion before proceeding with the next task. If not specified, the Join will move on to the next task without waiting for any forked tasks to complete. | Optional. |

## JSON configuration

Here is the task configuration for a Join task.

### With a static fork

```json
{
  "name": "join",
  "taskReferenceName": "join_ref",
  "inputParameters": {},
  "type": "JOIN",
  "joinOn": [
    // List of task reference names that the join should wait for
  ]
}
```

### With a dynamic fork

```json
{
  "name": "join",
  "taskReferenceName": "join_ref",
  "inputParameters": {},
  "type": "JOIN"
}
```

## Output

The Join task will return a map of all completed forked task outputs (in other words, the output from all `joinOn` tasks.) The keys are task reference names of the tasks being joined and the values are the corresponding task outputs.

**Example:**

```json
{
  "taskReferenceName": {
    "outputKey": "outputValue"
  },
  "anotherTaskReferenceName": {
    "outputKey": "outputValue"
  },
  "someTaskReferenceName": {
    "outputKey": "outputValue"
  }
}
```


## Examples

Here are some examples for using the Join task.

### Joining on all forks

In this example task configuration, the Join task will wait for the completion of tasks `my_task_ref_1` and `my_task_ref_2` as specified in `joinOn`.

```json
[
  {
    "name": "fork_join",
    "taskReferenceName": "my_fork_join_ref",
    "type": "FORK_JOIN",
    "forkTasks": [
      [
        {
          "name": "my_task",
          "taskReferenceName": "my_task_ref_1",
          "type": "SIMPLE"
        }
      ],
      [
        {
          "name": "my_task",
          "taskReferenceName": "my_task_ref_2",
          "type": "SIMPLE"
        }
      ]
    ]
  },
  {
    "name": "join_task",
    "taskReferenceName": "my_join_task_ref",
    "type": "JOIN",
    "joinOn": [
      "my_task_ref_1",
      "my_task_ref_2"
    ]
  }
]
```


### Ignoring one fork

In this example task configuration, the [Fork](fork-task.md) task spawns three tasks: an `email_notification` task, a `sms_notification` task, and a `http_notification` task. 

Email and SMS are usually best-effort delivery systems, while a HTTP-based notification can be retried until it succeeds or eventually gives up. Therefore, when you set up a notification workflow, you may decide to continue the workflow after you have kicked off an email and SMS notification, but let the `http_notification` task continue to execute without blocking the rest of the workflow.

In that case, you can specify the `joinOn` tasks as follows: 

```json
[
  {
    "name": "fork_join",
    "taskReferenceName": "my_fork_join_ref",
    "type": "FORK_JOIN",
    "forkTasks": [
      [
        {
          "name": "email_notification",
          "taskReferenceName": "email_notification_ref",
          "type": "SIMPLE"
        }
      ],
      [
        {
          "name": "sms_notification",
          "taskReferenceName": "sms_notification_ref",
          "type": "SIMPLE"
        }
      ],
      [
        {
          "name": "http_notification",
          "taskReferenceName": "http_notification_ref",
          "type": "SIMPLE"
        }
      ]
    ]
  },
  {
    "name": "notification_join",
    "taskReferenceName": "notification_join_ref",
    "type": "JOIN",
    "joinOn": [
      "email_notification_ref",
      "sms_notification_ref"
    ]
  }
]
```

Here is the output of `notification_join`. The output is a map, where the keys are the task reference names of the `joinOn` tasks, and the corresponding values are the outputs of those tasks.

```json
{
  "email_notification_ref": {
    "email_sent_at": "2021-11-06T07:37:17+0000",
    "email_sent_to": "test@example.com"
  },
  "sms_notification_ref": {
    "sms_sent_at": "2021-11-06T07:37:17+0129",
    "sms_sent_to": "+1-425-555-0189"
  }
}
```
