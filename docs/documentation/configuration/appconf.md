# App Configuration

The Conductor application server offers extensive customization options to optimize its operation for specific
environments.

These configuration parameters allow fine-tuning of various aspects of the server's behavior, performance, and
integration capabilities.
All of these parameters are grouped under the `conductor.app` namespace.

### Configuration

| Field                                       | Type     | Description                                                                                                                                                                     | Notes                                                   |
|:--------------------------------------------|:---------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------|
| stack                                       | String   | Name of the stack within which the app is running. e.g. `devint`, `testintg`, `staging`, `prod` etc.                                                                            | Default is "test"                                       |
| appId                                       | String   | The ID with which the app has been registered. e.g. `conductor`, `myApp`                                                                                                        | Default is "conductor"                                  |
| executorServiceMaxThreadCount               | int      | The maximum number of threads to be allocated to the executor service threadpool. e.g. `50`                                                                                     | Default is 50                                           |
| workflowOffsetTimeout                       | Duration | The timeout duration to set when a workflow is pushed to the decider queue. Example: `30s` or `1m`                                                                              | Default is 30 seconds                                   |
| maxPostponeDurationSeconds                  | Duration | The maximum timeout duration to set when a workflow with running task is pushed to the decider queue. Example: `30m` or `1h`                                                    | Default is 3600 seconds                                 |
| sweeperThreadCount                          | int      | The number of threads to use for background sweeping on active workflows. Example: `8` if there are 4 processors (2x4)                                                          | Default is 2 times the number of available processors   |
| sweeperWorkflowPollTimeout                  | Duration | The timeout for polling workflows to be swept. Example: `2000ms` or `2s`                                                                                                        | Default is 2000 milliseconds                            |
| eventProcessorThreadCount                   | int      | The number of threads to configure the threadpool in the event processor. Example: `4`                                                                                          | Default is 2                                            |
| eventMessageIndexingEnabled                 | boolean  | Whether to enable indexing of messages within event payloads. Example: `true` or `false`                                                                                        | Default is true                                         |
| eventExecutionIndexingEnabled               | boolean  | Whether to enable indexing of event execution results. Example: `true` or `false`                                                                                               | Default is true                                         |
| workflowExecutionLockEnabled                | boolean  | Whether to enable the workflow execution lock. Example: `true` or `false`                                                                                                       | Default is false                                        |
| lockLeaseTime                               | Duration | The time for which the lock is leased. Example: `60000ms` or `1m`                                                                                                               | Default is 60000 milliseconds                           |
| lockTimeToTry                               | Duration | The time for which the thread will block in an attempt to acquire the lock. Example: `500ms` or `1s`                                                                            | Default is 500 milliseconds                             |
| activeWorkerLastPollTimeout                 | Duration | The time to consider if a worker is actively polling for a task. Example: `10s`                                                                                                 | Default is 10 seconds                                   |
| taskExecutionPostponeDuration               | Duration | The time for which a task execution will be postponed if rate-limited or concurrent execution limited. Example: `60s`                                                           | Default is 60 seconds                                   |
| taskIndexingEnabled                         | boolean  | Whether to enable indexing of tasks. Example: `true` or `false`                                                                                                                 | Default is true                                         |
| taskExecLogIndexingEnabled                  | boolean  | Whether to enable indexing of task execution logs. Example: `true` or `false`                                                                                                   | Default is true                                         |
| asyncIndexingEnabled                        | boolean  | Whether to enable asynchronous indexing to Elasticsearch. Example: `true` or `false`                                                                                            | Default is false                                        |
| systemTaskWorkerThreadCount                 | int      | The number of threads in the threadpool for system task workers. Example: `8` if there are 4 processors (2x4)                                                                   | Default is 2 times the number of available processors   |
| systemTaskMaxPollCount                      | int      | The maximum number of threads to be polled within the threadpool for system task workers. Example: `8`                                                                          | Default is equal to systemTaskWorkerThreadCount         |
| systemTaskWorkerCallbackDuration            | Duration | The interval after which a system task will be checked by the system task worker for completion. Example: `30s`                                                                 | Default is 30 seconds                                   |
| systemTaskWorkerPollInterval                | Duration | The interval at which system task queues will be polled by system task workers. Example: `50ms`                                                                                 | Default is 50 milliseconds                              |
| systemTaskWorkerExecutionNamespace          | String   | The namespace for the system task workers to provide instance-level isolation. Example: `namespace1`, `namespace2`                                                              | Default is an empty string                              |
| isolatedSystemTaskWorkerThreadCount         | int      | The number of threads to be used within the threadpool for system task workers in each isolation group. Example: `4`                                                            | Default is 1                                            |
| asyncUpdateShortRunningWorkflowDuration     | Duration | The duration of workflow execution qualifying as short-running when async indexing to Elasticsearch is enabled. Example: `30s`                                                  | Default is 30 seconds                                   |
| asyncUpdateDelay                            | Duration | The delay with which short-running workflows will be updated in Elasticsearch when async indexing is enabled. Example: `60s`                                                    | Default is 60 seconds                                   |
| ownerEmailMandatory                         | boolean  | Whether to validate the owner email field as mandatory within workflow and task definitions. Example: `true` or `false`                                                         | Default is true                                         |
| eventQueueSchedulerPollThreadCount          | int      | The number of threads used in the Scheduler for polling events from multiple event queues. Example: `8` if there are 4 processors (2x4)                                         | Default is equal to the number of available processors  |
| eventQueuePollInterval                      | Duration | The time interval at which the default event queues will be polled. Example: `100ms`                                                                                            | Default is 100 milliseconds                             |
| eventQueuePollCount                         | int      | The number of messages to be polled from a default event queue in a single operation. Example: `10`                                                                             | Default is 10                                           |
| eventQueueLongPollTimeout                   | Duration | The timeout for the poll operation on the default event queue. Example: `1000ms`                                                                                                | Default is 1000 milliseconds                            |
| workflowInputPayloadSizeThreshold           | DataSize | The threshold of the workflow input payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: `5120KB`                                           | Default is 5120 kilobytes                               |
| maxWorkflowInputPayloadSizeThreshold        | DataSize | The maximum threshold of the workflow input payload size beyond which input will be rejected and the workflow marked as FAILED. Example: `10240KB`                              | Default is 10240 kilobytes                              |
| workflowOutputPayloadSizeThreshold          | DataSize | The threshold of the workflow output payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: `5120KB`                                          | Default is 5120 kilobytes                               |
| maxWorkflowOutputPayloadSizeThreshold       | DataSize | The maximum threshold of the workflow output payload size beyond which output will be rejected and the workflow marked as FAILED. Example: `10240KB`                            | Default is 10240 kilobytes                              |
| taskInputPayloadSizeThreshold               | DataSize | The threshold of the task input payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: `3072KB`                                               | Default is 3072 kilobytes                               |
| maxTaskInputPayloadSizeThreshold            | DataSize | The maximum threshold of the task input payload size beyond which the task input will be rejected and the task marked as FAILED_WITH_TERMINAL_ERROR. Example: `10240KB`         | Default is 10240 kilobytes                              |
| taskOutputPayloadSizeThreshold              | DataSize | The threshold of the task output payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: `3072KB`                                              | Default is 3072 kilobytes                               |
| maxTaskOutputPayloadSizeThreshold           | DataSize | The maximum threshold of the task output payload size beyond which the task output will be rejected and the task marked as FAILED_WITH_TERMINAL_ERROR. Example: `10240KB`       | Default is 10240 kilobytes                              |
| maxWorkflowVariablesPayloadSizeThreshold    | DataSize | The maximum threshold of the workflow variables payload size beyond which the task changes will be rejected and the task marked as FAILED_WITH_TERMINAL_ERROR. Example: `256KB` | Default is 256 kilobytes                                |
| taskExecLogSizeLimit                        | int      | The maximum size of task execution logs. Example: `10000`                                                                                                                       | Default is 10                                           |

### Example usage

In your configuration file add the configuration as you need

```properties
# Conductor App Configuration

# Name of the stack within which the app is running. e.g. devint, testintg, staging, prod etc.
conductor.app.stack=test

# The ID with which the app has been registered. e.g. conductor, myApp
conductor.app.appId=conductor

# The maximum number of threads to be allocated to the executor service threadpool. e.g. 50
conductor.app.executorServiceMaxThreadCount=50

# The timeout duration to set when a workflow is pushed to the decider queue. Example: 30s or 1m
conductor.app.workflowOffsetTimeout=30s

# The number of threads to use for background sweeping on active workflows. Example: 8 if there are 4 processors (2x4)
conductor.app.sweeperThreadCount=8

# The timeout for polling workflows to be swept. Example: 2000ms or 2s
conductor.app.sweeperWorkflowPollTimeout=2000ms

# The number of threads to configure the threadpool in the event processor. Example: 4
conductor.app.eventProcessorThreadCount=4

# Whether to enable indexing of messages within event payloads. Example: true or false
conductor.app.eventMessageIndexingEnabled=true

# Whether to enable indexing of event execution results. Example: true or false
conductor.app.eventExecutionIndexingEnabled=true

# Whether to enable the workflow execution lock. Example: true or false
conductor.app.workflowExecutionLockEnabled=false

# The time for which the lock is leased. Example: 60000ms or 1m
conductor.app.lockLeaseTime=60000ms

# The time for which the thread will block in an attempt to acquire the lock. Example: 500ms or 1s
conductor.app.lockTimeToTry=500ms

# The time to consider if a worker is actively polling for a task. Example: 10s
conductor.app.activeWorkerLastPollTimeout=10s

# The time for which a task execution will be postponed if rate-limited or concurrent execution limited. Example: 60s
conductor.app.taskExecutionPostponeDuration=60s

# Whether to enable indexing of tasks. Example: true or false
conductor.app.taskIndexingEnabled=true

# Whether to enable indexing of task execution logs. Example: true or false
conductor.app.taskExecLogIndexingEnabled=true

# Whether to enable asynchronous indexing to Elasticsearch. Example: true or false
conductor.app.asyncIndexingEnabled=false

# The number of threads in the threadpool for system task workers. Example: 8 if there are 4 processors (2x4)
conductor.app.systemTaskWorkerThreadCount=8

# The maximum number of threads to be polled within the threadpool for system task workers. Example: 8
conductor.app.systemTaskMaxPollCount=8

# The interval after which a system task will be checked by the system task worker for completion. Example: 30s
conductor.app.systemTaskWorkerCallbackDuration=30s

# The interval at which system task queues will be polled by system task workers. Example: 50ms
conductor.app.systemTaskWorkerPollInterval=50ms

# The namespace for the system task workers to provide instance-level isolation. Example: namespace1, namespace2
conductor.app.systemTaskWorkerExecutionNamespace=

# The number of threads to be used within the threadpool for system task workers in each isolation group. Example: 4
conductor.app.isolatedSystemTaskWorkerThreadCount=4

# The duration of workflow execution qualifying as short-running when async indexing to Elasticsearch is enabled. Example: 30s
conductor.app.asyncUpdateShortRunningWorkflowDuration=30s

# The delay with which short-running workflows will be updated in Elasticsearch when async indexing is enabled. Example: 60s
conductor.app.asyncUpdateDelay=60s

# Whether to validate the owner email field as mandatory within workflow and task definitions. Example: true or false
conductor.app.ownerEmailMandatory=true

# The number of threads used in the Scheduler for polling events from multiple event queues. Example: 8 if there are 4 processors (2x4)
conductor.app.eventQueueSchedulerPollThreadCount=8

# The time interval at which the default event queues will be polled. Example: 100ms
conductor.app.eventQueuePollInterval=100ms

# The number of messages to be polled from a default event queue in a single operation. Example: 10
conductor.app.eventQueuePollCount=10

# The timeout for the poll operation on the default event queue. Example: 1000ms
conductor.app.eventQueueLongPollTimeout=1000ms

# The threshold of the workflow input payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: 5120KB
conductor.app.workflowInputPayloadSizeThreshold=5120KB

# The maximum threshold of the workflow input payload size beyond which input will be rejected and the workflow marked as FAILED. Example: 10240KB
conductor.app.maxWorkflowInputPayloadSizeThreshold=10240KB

# The threshold of the workflow output payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: 5120KB
conductor.app.workflowOutputPayloadSizeThreshold=5120KB

# The maximum threshold of the workflow output payload size beyond which output will be rejected and the workflow marked as FAILED. Example: 10240KB
conductor.app.maxWorkflowOutputPayloadSizeThreshold=10240KB

# The threshold of the task input payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: 3072KB
conductor.app.taskInputPayloadSizeThreshold=3072KB

# The maximum threshold of the task input payload size beyond which the task input will be rejected and the task marked as FAILED_WITH_TERMINAL_ERROR. Example: 10240KB
conductor.app.maxTaskInputPayloadSizeThreshold=10240KB

# The threshold of the task output payload size beyond which the payload will be stored in ExternalPayloadStorage. Example: 3072KB
conductor.app.taskOutputPayloadSizeThreshold=3072KB

# The maximum threshold of the task output payload size beyond which the task output will be rejected and the task marked as FAILED_WITH_TERMINAL_ERROR. Example: 10240KB
conductor.app.maxTaskOutputPayloadSizeThreshold=10240KB

# The maximum threshold of the workflow variables payload size beyond which the task changes will be rejected and the task marked as FAILED_WITH_TERMINAL_ERROR. Example: 256KB
conductor.app.maxWorkflowVariablesPayloadSizeThreshold=256KB

# The maximum size of task execution logs. Example: 10000
conductor.app.taskExecLogSizeLimit=10000
```
