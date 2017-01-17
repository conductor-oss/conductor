Conductor uses [spectator][1] to collect the metrics.

| Name        | Purpose           | Tags  |
| ------------- |:-------------| -----|
| workflow_server_error      | Rate at which server side error is happening | methodName|
| workflow_failure | Counter for failing workflows|workflowName, status|
| workflow_start_error | Counter for failing to start a workflow|workflowName|
| workflow_running | Counter for no. of running workflows | workflowName, version|
| task_queue_wait | Time spent by a task in queue | taskType|
| task_execution | Time taken to execute a task | taskType, includeRetries, status |
| task_poll | Time taken to poll for a task | taskType|
| task_queue_depth | Pending tasks queue depth | taskType |
| task_timeout | Counter for timed out tasks | taskType |


[1]: https://github.com/Netflix/spectator