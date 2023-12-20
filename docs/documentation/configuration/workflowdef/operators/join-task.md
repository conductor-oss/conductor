
# Join
```json
"type" : "JOIN"
```

A `JOIN` task is used in conjunction with a `FORK_JOIN` or `FORK_JOIN_DYNAMIC` task. Each of the aggregated task outputs is given a corresponding key in the `JOIN` task output.

* When used with a `FORK_JOIN` task, it waits for a **user provided** list of zero or more of the forked tasks to be completed. 
* When used with a `FORK_JOIN_DYNAMIC` task, it implicitly waits for all of the dynamically forked tasks to complete.

`JOIN` used in this context is loosely analogous to the *Map* phase of the *Map-Reduce* programming pattern. In Conductor, the *reduce* step could 
be implemented as a subsequent task that references the output of the `JOIN` task.

## Use Cases

[FORK_JOIN](fork-task.md) and [FORK_JOIN_DYNAMIC](dynamic-fork-task.md) task are used to execute a collection of other tasks or sub workflows in parallel. In
such cases, there is a need to collect the output from the forked tasks before moving to the next stage in the workflow. 

## Configuration
When used with `FORK_JOIN` (Static Fork), The `joinOn` attribute is provided at the top level of the task configuration. 

| Attribute | Description                                                                                               |
| --------- | --------------------------------------------------------------------------------------------------------- |
| joinOn    | A list of task reference names that this `JOIN` task will wait for completion. Omitted for `DYNAMIC_FORK` |

The `JOIN` task does not utilize `inputParameters`. 

## Output

| Attribute       | Description                                                                         |
| --------------- | ----------------------------------------------------------------------------------- |
| task_ref_name_1 | A task reference name that was being `joinOn`. The value is the output of that task |
| task_ref_name_2 | A task reference name that was being `joinOn`. The value is the output of that task |
| ...             | ...                                                                                 |
| task_ref_name_N | A task reference name that was being `joinOn`. The value is the output of that task |



## Examples

### Simple Example
Here is an example of a _`JOIN`_ task. This task will wait for the completion of tasks `my_task_ref_1`
and `my_task_ref_2` as specified by the `joinOn` attribute.

```json
{
  "name": "join_task",
  "taskReferenceName": "my_join_task_ref",
  "type": "JOIN",
  "joinOn": [
    "my_task_ref_1",
    "my_task_ref_2"
  ]
}
```


### Example - Ignoring one fork
Here is an example of a `JOIN` task used in conjunction with a `FORK_JOIN` task. The 'FORK_JOIN' spawns 3 tasks.
An `email_notification` task, a `sms_notification` task and a  `http_notification` task. Email and SMS are usually best
effort delivery systems. However, in case of a http based notification you get a return code and you can retry until it
succeeds or eventually give up. When you setup a notification workflow, you may decide to continue ,if you kicked off an
email and sms notification. Im that case, you can decide to `joinOn` those specific tasks. However,
the `http_notification` task will still continue to execute, but it will not block the rest of the workflow from
proceeding.

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

Here is how the output of notification_join will look like. The output is a map, where the keys are the names of task
references that were being `joinOn`. The corresponding values are the outputs of those tasks.

```json

{
  "email_notification_ref": {
    "email_sent_at": "2021-11-06T07:37:17+0000",
    "email_sent_to": "test@example.com"
  },
  "sms_notification_ref": {
    "smm_sent_at": "2021-11-06T07:37:17+0129",
    "sms_sen": "+1-425-555-0189"
  }
}

```

