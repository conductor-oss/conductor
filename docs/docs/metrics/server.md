## Publishing metrics

Conductor uses [spectator](https://github.com/Netflix/spectator) to collect the metrics.

- To enable conductor serve to publish metrics, add this [dependency](http://netflix.github.io/spectator/en/latest/registry/metrics3/) to your build.gradle.
- Conductor Server enables you to load additional modules dynamically, this feature can be controlled using this [configuration](https://github.com/Netflix/conductor/blob/master/server/README.md#additional-modules-optional).
- Create your own AbstractModule that overides configure function and registers the Spectator metrics registry.
- Initialize the Registry and add it to the global registry via ```((CompositeRegistry)Spectator.globalRegistry()).add(...)```.

The following metrics are published by the server. You can use these metrics to configure alerts for your workflows and tasks.

| Name        | Purpose           | Tags  |
| ------------- |:-------------| -----|
| workflow_server_error | Rate at which server side error is happening | methodName|
| workflow_failure | Counter for failing workflows|workflowName, status|
| workflow_start_error | Counter for failing to start a workflow|workflowName|
| workflow_running | Counter for no. of running workflows | workflowName, version|
| workflow_execution | Timer for Workflow completion | workflowName, ownerApp |
| task_queue_wait | Time spent by a task in queue | taskType|
| task_execution | Time taken to execute a task | taskType, includeRetries, status |
| task_poll | Time taken to poll for a task | taskType|
| task_poll_count | Counter for number of times the task is being polled | taskType, domain |
| task_queue_depth | Pending tasks queue depth | taskType, ownerApp |
| task_rate_limited | Current number of tasks being rate limited | taskType |
| task_concurrent_execution_limited | Current number of tasks being limited by concurrent execution limit | taskType |
| task_timeout | Counter for timed out tasks | taskType |
| task_response_timeout | Counter for tasks timedout due to responseTimeout | taskType |
| task_update_conflict | Counter for task update conflicts. Eg: when the workflow is in terminal state | workflowName, taskType, taskStatus, workflowStatus |
| event_queue_messages_processed | Counter for number of messages fetched from an event queue | queueType, queueName |
| observable_queue_error | Counter for number of errors encountered when fetching messages from an event queue | queueType |
| event_queue_messages_handled | Counter for number of messages executed from an event queue | queueType, queueName |
| external_payload_storage_usage | Counter for number of times external payload storage was used | name, operation, payloadType |

[1]: https://github.com/Netflix/spectator
