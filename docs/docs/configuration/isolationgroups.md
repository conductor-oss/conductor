#### Isolation Group Id

Consider an HTTP task where the latency of an API is high, task queue piles up effecting execution of other HTTP tasks which have low latency.

We can isolate the execution of such tasks to have predictable performance using `isolationgroupId`, a property of task def.

When we set isolationGroupId,  the executor(SystemTaskWorkerCoordinator) will allocate an isolated queue and an isolated thread pool for execution of those tasks.

If no isolationgroupId is specified in taskdef, then fallback is default behaviour where the executor executes the task in shared threadpool for all tasks. 

Example taskdef  

```json
{
  "name": "encode_task",
  "retryCount": 3,

  "timeoutSeconds": 1200,
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
  "responseTimeoutSeconds": 3600,
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "rateLimitPerFrequency": 50,
  "isolationgroupId": "myIsolationGroupId"
}
```
Example Workflow task 
```json
{
  "name": "encode_and_deploy",
  "description": "Encodes a file and deploys to CDN",
  "version": 1,
  "tasks": [
    {
      "name": "encode",
      "taskReferenceName": "encode",
      "type": "HTTP", 
      "inputParameters": {
        "http_request": {
          "uri": "http://localhost:9200/conductor/_search?size=10",
          "method": "GET"
        }
      }
    }
  ],
  "outputParameters": {
    "cdn_url": "${d1.output.location}"
  },
  "failureWorkflow": "cleanup_encode_resources",
  "restartable": true,
  "workflowStatusListenerEnabled": true,
  "schemaVersion": 2
}
```


- puts `encode` in `HTTP-myIsolationGroupId` queue, and allocates a new thread pool for this for execution.

<b>Note: </b>  To enable this feature, the `workflow.isolated.system.task.enable` property needs to be made `true`,its default value is `false`

The property `workflow.isolated.system.task.worker.thread.count`  sets the thread pool size for isolated tasks; default is `1`.

isolationGroupId is currently supported only in HTTP and kafka Task. 

#### Execution Name Space

`executionNameSpace` A property of taskdef can be used to provide JVM isolation to task execution and scale executor deployments horizontally.

Limitation of using isolationGroupId is that we need to scale executors vertically as the executor allocates a new thread pool per `isolationGroupId`.  Also, since the executor runs the tasks in the same JVM, task execution is not isolated completely. 

To support JVM isolation, and also allow the executors to scale horizontally, we can use `executionNameSpace` property in taskdef.

Executor consumes tasks whose executionNameSpace matches with the configuration property `workflow.system.task.worker.executionNameSpace`

If the property is not set, the executor executes tasks without any executionNameSpace set. 


```json
{
  "name": "encode_task",
  "retryCount": 3,

  "timeoutSeconds": 1200,
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
  "responseTimeoutSeconds": 3600,
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "rateLimitPerFrequency": 50,
  "executionNameSpace": "myExecutionNameSpace"
}
```




Example Workflow task

```json
{ 
  "name": "encode_and_deploy",
  "description": "Encodes a file and deploys to CDN",
  "version": 1,
  "tasks": [
    { 
      "name": "encode",
      "taskReferenceName": "encode",
      "type": "HTTP", 
      "inputParameters": {
        "http_request": {
          "uri": "http://localhost:9200/conductor/_search?size=10",
          "method": "GET"
        }
      }
    }
  ],
  "outputParameters": {
    "cdn_url": "${d1.output.location}"
  },
  "failureWorkflow": "cleanup_encode_resources",
  "restartable": true,
  "workflowStatusListenerEnabled": true,
  "schemaVersion": 2
}
``` 
 
- `encode` task is executed by the executor deployment whose `workflow.system.task.worker.executionNameSpace` property is `myExecutionNameSpace` 

`executionNameSpace` can be used along with `isolationGroupId`

If the above task contains a isolationGroupId `myIsolationGroupId`, the tasks will be scheduled in a queue HTTP@myExecutionNameSpace-myIsolationGroupId, and have a new threadpool for execution in the deployment group with myExecutionNameSpace



