# Server Metrics

!!! Info "Feature Update"
    Since [v3.21.16](https://github.com/conductor-oss/conductor/releases/tag/v3.21.16), Conductor has switched to [Micrometer](https://micrometer.io/) for metrics collection.


Conductor uses [Micrometer](https://micrometer.io/) for metrics collection and export. 

The following metrics are published by the Conductor server. You can export these metrics to set up alerts for your workflows and tasks.

| Metric Name          | Description       | Tags  |
| ------------- |:----------------- | ----- |
| workflow_server_error | The rate at which server-side errors are occurring.  | methodName|
| workflow_failure | The number of failed workflows.                           |workflowName, status|
| workflow_start_error | The number of workflows that fail to start.           |workflowName|
| workflow_running | The number of running workflows.                          | workflowName, version|
| workflow_execution | The time taken for workflow completion.                 | workflowName, ownerApp |
| task_queue_wait | The amount of time spent by a task in queue.               | taskType |
| task_execution | The time taken to execute a task.                           | taskType, includeRetries, status |
| task_poll | The time taken to poll for a task.                               | taskType|
| task_poll_count | The number of times the task is being polled.              | taskType, domain |
| task_queue_depth | The queue depth for pending tasks.                        | taskType, ownerApp |
| task_rate_limited | The current number of tasks that are being rate limited. | taskType |
| task_concurrent_execution_limited | The current number of tasks that are being limited by its concurrent execution limit. | taskType |
| task_timeout | The number of timed-out tasks. | taskType |
| task_response_timeout | The number of tasks that timed out due to `responseTimeout`. | taskType |
| task_update_conflict | The number of task update conflicts. <br/><br/> For example, a worker updates the task status even though the workflow is already in a terminal state. | workflowName, taskType, taskStatus, workflowStatus |
| event_queue_messages_processed | The number of messages fetched from an event queue. | queueType, queueName |
| observable_queue_error | The number of errors encountered when fetching messages from an event queue. | queueType |
| event_queue_messages_handled | The number of messages executed from an event queue. | queueType, queueName |
| external_payload_storage_usage | The number of times an external payload storage was used. | name, operation, payloadType |


## Supported monitoring systems

Conductor supports the following Micrometer publishers:

- [Atlas](https://docs.micrometer.io/micrometer/reference/implementations/atlas.html)
- [Prometheus](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Datadog](https://docs.micrometer.io/micrometer/reference/implementations/datadog.html)
- [JMX](https://docs.micrometer.io/micrometer/reference/implementations/jmx.html)
- [OpenTelemetry Protocol (OTPL)](https://docs.micrometer.io/micrometer/reference/implementations/otlp.html)
- [Dynatrace](https://docs.micrometer.io/micrometer/reference/implementations/dynatrace.html)
- [Elasticsearch](https://docs.micrometer.io/micrometer/reference/implementations/elastic.html)
- [New Relic](https://docs.micrometer.io/micrometer/reference/implementations/new-relic.html)
- [StackDriver](https://docs.micrometer.io/micrometer/reference/implementations/stackdriver.html)
- [StatsD](https://docs.micrometer.io/micrometer/reference/implementations/statsD.html)
- [CloudWatch](https://docs.micrometer.io/micrometer/reference/implementations/cloudwatch.html)
- [Azure Monitor](https://docs.micrometer.io/micrometer/reference/implementations/azure-monitor.html)
- [Influx](https://docs.micrometer.io/micrometer/reference/implementations/influx.html)

### Enabling metrics collection

To enable metrics collection to a particular monitoring system, refer to the [Micrometer documentation](https://docs.micrometer.io/micrometer/reference/implementations.html) complete the implementation. You will also need to enable the particular monitoring system in the Conductor's [`application.properties` file](https://github.com/conductor-oss/conductor/blob/6147d61d1babf47f5a0a328d114f1eb5d3d5ecb1/server/src/main/resources/application.properties#L163).
