# Running First Worker

In this article we will explore how you can get your first worker task running.

We are hosting the code used in this article in the following location. You can clone and use it as a reference
locally.

#### https://github.com/orkes-io/orkesworkers

In the previous article, you used an `HTTP` task run your first simple workflow. Now it's time to explore how to run a
custom worker that you will implement yourself.

After completing the steps in this article, you will:

1. Learn about a SIMPLE worker type which runs your custom code
2. Learn about how a custom worker task runs from your environment and connects to Conductor

Worker tasks are implemented by your application(s) and runs in a separate environment from Conductor. The worker tasks
can be implemented in any language. These tasks talk to Conductor server via REST/gRPC to poll for tasks and update its
status after execution. In our example we will be implementing a Java based worker by leveraging the official Java
Client SDK.

Worker tasks are identified by task type `SIMPLE` in the workflow JSON definition.

### Step 1 - Register the Worker Task

First let's create task definition for "simple_worker". Send a POST request to `/metadata/taskdefs` API endpoint on your
conductor server to register these tasks.

```json
[
  {
    "name": "simple_worker",
    "retryCount": 3,
    "retryLogic": "FIXED",
    "retryDelaySeconds": 10,
    "timeoutSeconds": 300,
    "timeoutPolicy": "TIME_OUT_WF",
    "responseTimeoutSeconds": 180,
    "ownerEmail": "example@gmail.com"
  }
]
```

Here is the curl command to do that

```shell
curl 'http://localhost:8080/api/metadata/taskdefs' \
  -H 'accept: */*' \
  -H 'Referer: ' \
  -H 'Content-Type: application/json' \
  --data-raw '[{"name":"simple_worker","retryCount":3,"retryLogic":"FIXED","retryDelaySeconds":10,"timeoutSeconds":300,"timeoutPolicy":"TIME_OUT_WF","responseTimeoutSeconds":180,"ownerEmail":"example@gmail.com"}]'
```

You can also use the Conductor Swagger API UI to make this call.

Here is an overview of the task fields that we just created

1. `"name"` : Name of your worker. This should be unique.
2. `"retryCount"` : The number of times Conductor should retry your worker task in the event of an unexpected failure
3. `"retryLogic"` : `FIXED` - The retry logic - options are `FIXED` and `EXPONENTIAL_BACKOFF`
4. `"retryDelaySeconds"` : Time to wait before retries
5. `"timeoutSeconds"` : Time in seconds, after which the task is marked as `TIMED_OUT` if not completed after
   transitioning to `IN_PROGRESS` status for the first time
6. `"timeoutPolicy"` : `TIME_OUT_WF` - Task's timeout policy. Options can be
   found [here](https://netflix.github.io/conductor/configuration/taskdef/#timeout-policy)
7. `"responseTimeoutSeconds"` : Must be greater than 0 and less than timeoutSeconds. The task is rescheduled if not
   updated with a status after this time (heartbeat mechanism). Useful when the worker polls for the task but fails to
   complete due to errors/network failure. Defaults to 3600
8. `"ownerEmail"` : **Mandatory** metadata to manage who created or owns this worker definition in a shared conductor
   environment.

More details on the fields used and the remaining fields in the task definition can be
found [here](https://netflix.github.io/conductor/configuration/taskdef/#task-definition).

### Step 2 - Create a Workflow definition

Creating Workflow definition is similar to creating the task definition. In our workflow, we will use the task we
defined earlier. Note that same Task definitions can be used in multiple workflows, or for multiple times in same
Workflow (that's where taskReferenceName is useful).

```json
{
  "createTime": 1634021619147,
  "updateTime": 1630694890267,
  "name": "first_sample_workflow_with_worker",
  "description": "First Sample Workflow With Worker",
  "version": 1,
  "tasks": [
    {
      "name": "simple_worker",
      "taskReferenceName": "simple_worker_ref_1",
      "inputParameters": {},
      "type": "SIMPLE"
    }
  ],
  "inputParameters": [],
  "outputParameters": {
    "currentTimeOnServer": "${simple_worker_ref_1.output.currentTimeOnServer}",
    "message": "${simple_worker_ref_1.output.message}"
  },
  "schemaVersion": 2,
  "restartable": true,
  "ownerEmail": "example@email.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 0
}
```

Notice that in the workflow definition, we are using a single worker task using the task worker definition we created
earlier. The task is of type `SIMPLE`.

To create this workflow in your Conductor server using CURL, use the following:

```shell
curl 'http://localhost:8080/api/metadata/workflow' \
-H 'accept: */*' \
-H 'Referer: ' \
-H 'Content-Type: application/json' \
--data-raw '{"createTime":1634021619147,"updateTime":1630694890267,"name":"first_sample_workflow_with_worker","description":"First Sample Workflow With Worker","version":1,"tasks":[{"name":"simple_worker","taskReferenceName":"simple_worker_ref_1","inputParameters":{},"type":"SIMPLE"}],"inputParameters":[],"outputParameters":{"currentTimeOnServer":"${simple_worker_ref_1.output.currentTimeOnServer}","message":"${simple_worker_ref_1.output.message}"},"schemaVersion":2,"restartable":true,"ownerEmail":"example@email.com","timeoutPolicy":"ALERT_ONLY","timeoutSeconds":0}'
```

### Step 3 - Starting the Workflow

This workflow doesn't need any inputs. So we can issue a curl command to start it.

```shell
curl 'http://localhost:8080/api/workflow/first_sample_workflow_with_worker' \
  -H 'accept: text/plain' \
  -H 'Referer: ' \
  -H 'Content-Type: application/json' \
  --data-raw '{}' 
```

The API path contains the workflow name `first_sample_workflow_with_worker` and in our workflow since we don't need any
inputs we will specify `{}`

Successful POST request should return a workflow id, which you can use to find the execution in the UI by navigating to `http://localhost:5000/execution/<workflowId>`.

*Note: You can also run this using the Swagger UI instead of curl.*

### Step 4 - Poll for Worker Task

To get your Worker taskId, you first naviaget to `http://localhost:5000/execution/<workflowId>`. Next, click on the `simple_worker_ref_1` in the UI. This will open a summary pane with the `Task Execution ID`

If you look up the worker using the following URL `http://localhost:8080/api/tasks/{taskId}`, you will notice that the worker is in `SCHEDULED` state. This is
because we haven't implemented the worker yet. Let's walk through the steps to implement the worker.

#### Prerequisite

1. Setup a Java project on your local. You can also use an existing Java project if you already have one

#### Adding worker implementation

In your project, add the following dependencies. We are showing here how you will do this in gradle:

```javascript
implementation("com.netflix.conductor:conductor-client:${versions.conductor}") {
        exclude group: 'com.github.vmg.protogen', module: 'protogen-annotations'
    }

implementation("com.netflix.conductor:conductor-common:${versions.conductor}") {
    exclude group: 'com.github.vmg.protogen', module: 'protogen-annotations'
}
```

[See full example on GitHub](https://github.com/orkes-io/orkesworkers/blob/main/build.gradle)

You can do this for Maven as well, just need to use the appropriate syntax. We will need the following two libraries
available in maven repo and you can use the latest version if required.

1. `com.netflix.conductor:conductor-client:3.0.6`
2. `com.netflix.conductor:conductor-common:3.0.6`

Now "simple_worker" task is in `SCHEDULED` state, it is worker's turn to fetch the task, execute it and update Conductor
with final status of the task.

A class needs to be created which implements Worker and defines its methods.

**Note**: Make sure the method `getTaskDefName` returns string same as the task name we defined in step
1 (`simple_worker`).

```js reference
https://github.com/orkes-io/orkesworkers/blob/main/src/main/java/io/orkes/samples/workers/SimpleWorker.java#L11-L30
```

Awesome, you have implemented your first worker in code! Amazing.

#### Connecting, Polling and Executing your Worker

In your main method or where ever your application starts, you will need to configure a class
called `TaskRunnerConfigurer` and initialize it. This is the step that makes your code connect to a Conductor server.

Here we have used a SpringBoot based Java application and we are showing you how to create a Bean for this class.

```js reference
https://github.com/orkes-io/orkesworkers/blob/main/src/main/java/io/orkes/samples/OrkesWorkersApplication.java#L18-L45
```

This is the place where you define your conductor server URL:

```javascript
env.getProperty("conductor.server.url")
```

We have defined this in a property file as shown below. You can also hard code this.

```javascript reference
https://github.com/orkes-io/orkesworkers/blob/main/src/main/resources/application.properties#L1-L1
```

That's it. You are all set. Run your Java application to start running your worker.

After running your application, it will be able to poll and run your worker. Let's go back and load up the previously
created workflow in your UI.

![Conductor UI - Workflow Run](/img/tutorial/successfulWorkerExecution.png)

In your worker you had this implementation:

```js
     result.addOutputData("currentTimeOnServer", currentTimeOnServer);
result.addOutputData("message", "Hello World!");
```

As you can see in the screenshot above the worker has added these outputs to the workflow run. Play around with this,
change the outputs and re-run and see how it works.

## Summary

In this blog post — we learned how to run a sample workflow in our Conductor installation with a custom worker.
Concepts we touched on:

1. Adding Task (worker) definition
2. Adding Workflow definition with a custom `SIMPLE` task
3. Running Conductor client using the Java SDK

Thank you for reading, and we hope you found this helpful. Please feel free to reach out to us for any questions and we
are happy to help in any way we can.




