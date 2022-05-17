# Dynamic
```json
"type" : "DYNAMIC"
```

### Introduction
Dynamic Task allows to execute one of the registered Tasks dynamically at run-time.
It accepts the task name to execute as `taskToExecute` in `inputParameters`.

### Use Cases 

Consider a scenario, when we have to make decision of executing a task dynamically i.e. while the workflow is still
running. In such cases, Dynamic Task would be useful.

### Configuration

Dynamic task is defined directly inside the workflow with type `DYNAMIC`.

#### Inputs

Following are the input parameters :

| name                 | description                                                                                                                                                               |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| dynamicTaskNameParam | Name of the parameter from the task input whose value is used to schedule the task.  e.g. if the value of the parameter is ABC, the next task scheduled is of type 'ABC'. |

#### Output

TODO: Talk about output of the task, what to expect


### Examples

Suppose in a workflow, we have to take decision to ship the courier with the shipping
service providers on the basis of Post Code.

Following task `shipping_info` generates an output on the basis of which decision would be
taken to run the next task.

```json
{
  "name": "shipping_info",
  "retryCount": 3,
  "timeoutSeconds": 600,
  "pollTimeoutSeconds": 1200,
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 300,
  "responseTimeoutSeconds": 300,
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "ownerEmail":"abc@example.com",
  "rateLimitPerFrequency": 1
}
```

Following are the two worker tasks, one among them would execute on the basis of output generated
by the `shipping_info` task :

```json
{
  "name": "ship_via_fedex",
  "retryCount": 3,
  "timeoutSeconds": 600,
  "pollTimeoutSeconds": 1200,
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 300,
  "responseTimeoutSeconds": 300,
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "ownerEmail":"abc@example.com",
  "rateLimitPerFrequency": 2
},
{
  "name": "ship_via_ups",
  "retryCount": 3,
  "timeoutSeconds": 600,
  "pollTimeoutSeconds": 1200,
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 300,
  "responseTimeoutSeconds": 300,
  "concurrentExecLimit": 100,
  "rateLimitFrequencyInSeconds": 60,
  "ownerEmail":"abc@example.com",
  "rateLimitPerFrequency": 2
}
```


We will create the Workflow with the following definition :

```json
{
  "name": "Shipping_Flow",
  "description": "Ships smartly on the basis of Shipping info",
  "version": 1,
  "tasks": [
    {
      "name": "shipping_info",
      "taskReferenceName": "shipping_info",
      "inputParameters": {
      },
      "type": "SIMPLE"
    },
    {
      "name": "shipping_task",
      "taskReferenceName": "shipping_task",
      "inputParameters": {
        "taskToExecute": "${shipping_info.output.shipping_service}"
      },
      "type": "DYNAMIC",
      "dynamicTaskNameParam": "taskToExecute"
    }

  ],
  "restartable": true,
  "ownerEmail":"abc@example.com",
  "workflowStatusListenerEnabled": true,
  "schemaVersion": 2
}
```

Workflow is the created as shown in the below diagram.


![Conductor UI - Workflow Diagram](/img/tutorial/ShippingWorkflow.png)


Note : `shipping_task` is a `DYNAMIC` task and the `taskToExecute` parameter can be set
with input value provided while running the workflow or with the output of previous tasks.
Here, it is set to the output provided by the previous task i.e.
`${shipping_info.output.shipping_service}`.

If the input value is provided while running the workflow it can be accessed by
`${workflow.input.shipping_service}`.

```json
{
  "shipping_service": "ship_via_fedex"
}
```

We can see in the below example that on the basis of Post Code the shipping service is being
decided.

Based on given set of inputs i.e. Post Code starts with '9' hence, `ship_via_fedex` is executed -

![Conductor UI - Workflow Run](/img/tutorial/ShippingWorkflowRunning.png)

If the Post Code started with anything other than 9 `ship_via_ups` is executed -

![Conductor UI - Workflow Run](/img/tutorial/ShippingWorkflowUPS.png)

If the incorrect task name or the task that doesn't exist is provided then the workflow fails and
we get the error `"Invalid task specified. Cannot find task by name in the task definitions."`

If the null reference is provided in the task name then also the workflow fails and we get the
error `"Cannot map a dynamic task based on the parameter and input. Parameter= taskToExecute, input= {taskToExecute=null}"`
