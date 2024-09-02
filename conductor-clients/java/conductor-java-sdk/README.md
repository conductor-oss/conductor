# Conductor Java SDK

SDK for developing Java applications that create, manage and execute workflows, and run workers.

[Conductor](https://www.conductor-oss.org/) is the leading open-source orchestration platform allowing developers to build highly scalable distributed applications.

To learn more about Conductor checkout our [developer's guide](https://docs.conductor-oss.org/devguide/concepts/index.html) and give it a ⭐ to make it famous!

[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor.svg?style=social&label=Star&maxAge=)](https://GitHub.com/conductor-oss/conductor/)

## Content

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Set Up Conductor Java SDK](#set-up-conductor-java-sdk)
    - [Gradle](#gradle)
    - [Maven](#maven)
- [Hello World Application Using Conductor](#hello-world-application-using-conductor)
    - [Step 1: Create Workflow](#step-1-create-workflow)
        - [Creating Workflows by Code](#creating-workflows-by-code)
        - [(Alternatively) Creating Workflows in JSON](#alternatively-creating-workflows-in-json)
    - [Step 2: Write Worker](#step-2-write-worker)
    - [Step 3: Write *Hello World* Application](#step-3-write-hello-world-application)
- [Running Workflows on Conductor Standalone (Installed Locally)](#running-workflows-on-conductor-standalone-installed-locally)
    - [Conductor Server Settings](#conductor-server-settings)
    - [Start Conductor Server](#start-conductor-server)
    - [Execute Hello World Application](#execute-hello-world-application)
- [Running Workflows on Orkes Conductor](#running-workflows-on-orkes-conductor)
- [Learn More about Conductor Java SDK](#learn-more-about-conductor-java-sdk)
- [Create and Run Conductor Workers](#create-and-run-conductor-workers)
    - [Writing Workers](#writing-workers)
    - [Implementing Workers](#implementing-workers)
    - [Managing Workers in Application](#managing-workers-in-application)
    - [Design Principles for Workers](#design-principles-for-workers)
    - [System Task Workers](#system-task-workers)
        - [Wait Task](#wait-task)
            - [Using Code to Create Wait Task](#using-code-to-create-wait-task)
            - [JSON Configuration](#json-configuration)
        - [HTTP Task](#http-task)
            - [Using Code to Create HTTP Task](#using-code-to-create-http-task)
            - [JSON Configuration](#json-configuration-1)
        - [Javascript Executor Task](#javascript-executor-task)
            - [Using Code to Create Inline Task](#using-code-to-create-inline-task)
            - [JSON Configuration](#json-configuration-2)
        - [JSON Processing using JQ](#json-processing-using-jq)
            - [Using Code to Create JSON JQ Transform Task](#using-code-to-create-json-jq-transform-task)
            - [JSON Configuration](#json-configuration-3)
    - [Worker vs. Microservice/HTTP Endpoints](#worker-vs-microservicehttp-endpoints)
    - [Deploying Workers in Production](#deploying-workers-in-production)
- [Create Conductor Workflows](#create-conductor-workflows)
    - [Creating Workflows](#creating-workflows)
    - [Execute Dynamic Workflows Using Code](#execute-dynamic-workflows-using-code)
        - [Kitchen-Sink Workflow](#kitchen-sink-workflow)
    - [Executing Workflows](#executing-workflows)
        - [Execute Workflow Asynchronously](#execute-workflow-asynchronously)
        - [Execute Workflow Synchronously](#execute-workflow-synchronously)
    - [Managing Workflow Executions](#managing-workflow-executions)
    - [Get Execution Status](#get-execution-status)
    - [Update Workflow State Variables](#update-workflow-state-variables)
    - [Terminate Running Workflows](#terminate-running-workflows)
    - [Retry Failed Workflows](#retry-failed-workflows)
    - [Restart Workflows](#restart-workflows)
    - [Rerun Workflow from a Specific Task](#rerun-workflow-from-a-specific-task)
    - [Pause Running Workflow](#pause-running-workflow)
    - [Resume Paused Workflow](#resume-paused-workflow)
    - [Searching for Workflows](#searching-for-workflows)
    - [Handling Failures, Retries and Rate Limits](#handling-failures-retries-and-rate-limits)
        - [Retries](#retries)
        - [Rate Limits](#rate-limits)
        - [Task Registration](#task-registration)
        - [Update Task Definition:](#update-task-definition)
- [Using Conductor in Your Application](#using-conductor-in-your-application)
    - [Adding Conductor SDK to Your Application](#adding-conductor-sdk-to-your-application)
        - [Gradle](#gradle-1)
        - [Maven](#maven-1)
    - [Testing Workflows](#testing-workflows)
        - [Gradle](#gradle-2)
        - [Maven](#maven-2)
        - [Example Unit Testing Application](#example-unit-testing-application)
    - [Workflow Deployments Using CI/CD](#workflow-deployments-using-cicd)
    - [Versioning Workflows](#versioning-workflows)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Set Up Conductor Java SDK

Add `orkes-conductor-client` dependency to your project.

### Gradle

For Gradle-based projects, modify the `build.gradle` file in the project directory by adding the following line to the dependencies block in that file:

```
implementation 'io.orkes.conductor:client:3.0.0'
implementation 'io.orkes.conductor:sdk:3.0.0'
implementation 'io.orkes.conductor:spring:3.0.0'
```

### Maven

For Maven-based projects, modify the `pom.xml` file in the project directory by adding the following XML snippet within the `dependencies` section:

```
<dependency>
	<groupId>io.orkes.conductor</groupId>
	<artifactId>client</artifactId>
	<version>3.0.0</version>
</dependency>

<dependency>
	<groupId>io.orkes.conductor</groupId>
	<artifactId>sdk</artifactId>
	<version>3.0.0</version>
</dependency>

<dependency>
	<groupId>io.orkes.conductor</groupId>
	<artifactId>spring</artifactId>
	<version>3.0.0</version>
</dependency>
```

## Hello World Application Using Conductor

In this section, we will create a simple "Hello World" application that executes a "greetings" workflow managed by Conductor.

### Step 1: Create Workflow

#### Creating Workflows by Code

Create `workflow/GreetingsWorkflow.java` with the following:

```java
package io.orkes.conductor.sdk.examples.HelloWorld.workflow;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

public class GreetingsWorkflow {
    private final WorkflowExecutor executor;
    public GreetingsWorkflow(WorkflowExecutor executor) {
        this.executor = executor;
    }
    public ConductorWorkflow<WorkflowInput> GreetingsWorkflow() {
        ConductorWorkflow<WorkflowInput> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("greetings");
        workflow.setVersion(1);
        SimpleTask greetingsTask = new SimpleTask("greet", "greet_ref");
        greetingsTask.input("name", "${workflow.input.name}");
        workflow.add(greetingsTask);
        return workflow;
    }
}
```
Create `workflow/WorkflowInput.java` with the following:

```java
package io.orkes.conductor.sdk.examples.HelloWorld.workflow;

public class WorkflowInput {
    private String name;
    public WorkflowInput(String name) {
        this.name = name;
    }
    public String getName() {
            return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
```

#### (Alternatively) Creating Workflows in JSON

Create `workflow.json` with the following:

```json
{
  "name": "greetings",
  "description": "Sample greetings workflow",
  "version": 1,
  "tasks": [
    {
      "name": "greet",
      "taskReferenceName": "greet_ref",
      "type": "SIMPLE",
      "inputParameters": {
        "name": "${workflow.input.name}"
      }
    }
  ],
  "timeoutPolicy": "TIME_OUT_WF",
  "timeoutSeconds": 60
}
```

Workflows must be registered to the Conductor server. Use the API to register the greetings workflow from the JSON file above:


```shell
curl -X POST -H "Content-Type:application/json" \
http://localhost:8080/api/metadata/workflow -d @workflow.json
```

> [!note]
> To use the Conductor API, the Conductor server must be up and running (see [Running over Conductor standalone (installed locally)](#running-over-conductor-standalone-installed-locally))

### Step 2: Write Worker

Create `workers/ConductorWorkers.java` with a simple worker and workflow function.

> [!note]
> A single workflow can have task workers written in different languages and deployed anywhere, making your workflow polyglot and distributed!

```java
package io.orkes.conductor.sdk.examples.HelloWorld.worker;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
  public class ConductorWorkers {
    @WorkerTask("greet")
    public String greet(@InputParam("name") String name) {
      return "Hello " + name;
    }
  }
```

Now, we are ready to write our main application, which will execute our workflow.

### Step 3: Write *Hello World* Application

Let's add `Main.java` with a `main` method:

```java
package io.orkes.conductor.sdk.examples.HelloWorld;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Preconditions;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import com.netflix.conductor.client.http.ApiClient;
import io.orkes.conductor.client.MetadataClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.TaskClient;
import io.orkes.conductor.client.WorkflowClient;
import io.orkes.conductor.client.automator.TaskRunnerConfigurer;
import io.orkes.conductor.sdk.examples.HelloWorld.workflow.GreetingsWorkflow;
import io.orkes.conductor.sdk.examples.HelloWorld.workflow.WorkflowInput;

public class Main {

    private static final String ENV_ROOT_URI = "CONDUCTOR_SERVER_URL";
    private static final String ENV_KEY_ID = "KEY";
    private static final String ENV_SECRET = "SECRET";

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        //Initialise Conductor Client
        OrkesClients orkesClients = getApiClientWithCredentials();
        TaskClient taskClient = orkesClients.getTaskClient();
        WorkflowClient workflowClient = orkesClients.getWorkflowClient();
        MetadataClient metadataClient = orkesClients.getMetadataClient();

        //Initialise WorkflowExecutor and Conductor Workers
        WorkflowExecutor workflowExecutor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 10);
        workflowExecutor.initWorkers("io.orkes.conductor.sdk.examples.HelloWorld.workers");

        //Create the workflow with input
        GreetingsWorkflow workflowCreator = new GreetingsWorkflow(workflowExecutor);
        ConductorWorkflow<WorkflowInput> simpleWorkflow = workflowCreator.createWorkflow();
        WorkflowInput input = new WorkflowInput("Orkes");
        CompletableFuture<Workflow> workflowExecution = simpleWorkflow.executeDynamic(input);
        Workflow workflowRun = workflowExecution.get(10, TimeUnit.SECONDS);

        //Shutdown workflowClient and taskrunner
        workflowClient.shutdown();
        System.exit(0);
    }

    private static TaskRunnerConfigurer initWorkers(List<Worker> workers, TaskClient taskClient) {
        TaskRunnerConfigurer.Builder builder = new TaskRunnerConfigurer.Builder(taskClient, workers);
        TaskRunnerConfigurer taskRunner = builder.withThreadCount(1).withTaskPollTimeout(5).build();
        // Start Polling for tasks and execute them
        taskRunner.init();
        return taskRunner;
    }

    public static OrkesClients getApiClientWithCredentials() {
        ApiClient apiClient = new ApiClient(ENV_ROOT_URI,ENV_KEY_ID,ENV_SECRET);
        apiClient.setWriteTimeout(30_000);
        apiClient.setReadTimeout(30_000);
        apiClient.setConnectTimeout(30_000);
        return new OrkesClients(apiClient);
    }
}
```
Add the [ApiUtil.java](examples/java/io/orkes/conductor/sdk/ApiUtil.java) file to set the environment variables.
## Running Workflows on Conductor Standalone (Installed Locally)

### Conductor Server Settings

Everything related to server settings should be done within the `ApiClient` class by setting the required parameters when initializing an object, like this:

```java
ApiClient apiClient = new ApiClient("CONDUCTOR_SERVER_URL");
```

If you are using Spring Framework, you can initialize the above class as a bean that can be used across the project.

### Start Conductor Server

To start the Conductor server in a standalone mode from a Docker image, type the command below:

```
docker run --init -p 8080:8080 -p 5000:5000 conductoross/conductor-standalone:3.15.0
```

To ensure the server has started successfully, open Conductor UI on http://localhost:5000.

### Execute Hello World Application

Run the Java application now.

Now, the workflow is executed, and its execution status can be viewed from Conductor UI (http://localhost:5000).

Navigate to the **Executions** tab to view the workflow execution.

<img width="1431" alt="Screenshot 2024-04-01 at 15 53 02" src="https://github.com/RizaFarheen/orkes-conductor-client/assets/163816773/e413c828-5f47-4cdc-b113-bed3fefa63a4">

## Running Workflows on Orkes Conductor

For running the workflow in Orkes Conductor,

- Update the Conductor server URL to your cluster name.

```java
 export CONDUCTOR_SERVER_URL="https://[your-cluster-name].orkesconductor.io/api"
```

- If you want to run the workflow on the Orkes Conductor Playground, set the Conductor Server variable as follows:

```java
export CONDUCTOR_SERVER_URL=https://play.orkes.io/api
```

- Orkes Conductor requires authentication. [Obtain the key and secret from the Conductor server](https://orkes.io/content/how-to-videos/access-key-and-secret) and set the following environment variables.

```
export KEY=your_key
export SECRET=your_secret
```
Run the application and view the execution status from Conductor's UI Console.

> [!NOTE]
> That's it - you just created and executed your first distributed Java app!
>

## Learn More about Conductor Java SDK

There are three main ways you can use Conductor when building durable, resilient, distributed applications.

1. Write service workers that implement business logic to accomplish a specific goal - such as initiating payment transfer, getting user information from the database, etc.
2. Create Conductor workflows that implement application state - A typical workflow implements the saga pattern.
3. Use Conductor SDK and APIs to manage workflows from your application.

## Create and Run Conductor Workers

### Writing Workers

A Workflow task represents a unit of business logic that achieves a specific goal, such as checking inventory, initiating payment transfer, etc. A worker implements a task in the workflow.

### Implementing Workers

The workers can be implemented by writing a simple Java function and annotating the function with the `@worker_task`. Conductor workers are services (similar to microservices) that follow the [Single Responsibility Principle](https://en.wikipedia.org/wiki/Single_responsibility_principle).

Workers can be hosted along with the workflow or run in a distributed environment where a single workflow uses workers deployed and running in different machines/VMs/containers. Whether to keep all the workers in the same application or run them as a distributed application is a design and architectural choice. Conductor is well suited for both kinds of scenarios.

You can create or convert any existing Java function to a distributed worker by adding `@WorkerTask` annotation to it. Here is a simple worker that takes name as input and returns greetings:

```java
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

public class ConductorWorkers {
    @WorkerTask("greetings")
    public void greeting(@InputParam("name") String name) {
        System.out.println("Hello my friend " + name);
    }
}
```

### Managing Workers in Application

Workers use a polling mechanism (with a long poll) to check for any available tasks from the server periodically. The startup and shutdown of workers are handled by the  `conductor.client.automator.TaskRunnerConfigurer` class.

```java
WorkflowExecutor executor = new WorkflowExecutor("http://server/api/");
/*List of packages  (comma separated) to scan for annotated workers.  
  Please note the worker method MUST be public, and the class in which they are defined
  MUST have a no-args constructor*/       
executor.initWorkers("com.company.package1,com.company.package2");
```

### Design Principles for Workers

Each worker embodies the design pattern and follows certain basic principles:

1. Workers are stateless and do not implement a workflow-specific logic.
2. Each worker executes a particular task and produces well-defined output given specific inputs.
3. Workers are meant to be idempotent (Should handle cases where the partially executed task, due to timeouts, etc, gets rescheduled).
4. Workers do not implement the logic to handle retries, etc., that is taken care of by the Conductor server.

### System Task Workers

A system task worker is a pre-built, general-purpose worker in your Conductor server distribution.

System tasks automate repeated tasks such as calling an HTTP endpoint, executing lightweight ECMA-compliant javascript code, publishing to an event broker, etc.

#### Wait Task

> [!tip]
> Wait is a powerful way to have your system wait for a specific trigger, such as an external event, a particular date/time, or duration, such as 2 hours, without having to manage threads, background processes, or jobs.

##### Using Code to Create Wait Task

```java
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
/* Wait for a specific duration */
Wait waitTask = new Wait("wait_for_2_sec",Duration.ofMillis(1000));
/* Wait using Datetime */
ZonedDateTime zone = ZonedDateTime.parse("2020-10-05T08:20:10+05:30[Asia/Kolkata]");
Wait waitTask = new Wait("wait_till_2days",zone);
workflow.add(waitTask);//workflow is an object of ConductorWorkflow<WorkflowInput>
```

##### JSON Configuration

```json
{
  "name": "wait",
  "taskReferenceName": "wait_till_jan_end",
  "type": "WAIT",
  "inputParameters": {
    "until": "2024-01-31 00:00 UTC"
  }
}
```

#### HTTP Task

Make a request to an HTTP(S) endpoint. The task allows for GET, PUT, POST, DELETE, HEAD, and PATCH requests.

##### Using Code to Create HTTP Task

```java
import com.netflix.conductor.sdk.workflow.def.tasks.Http;
Http httptask = new Http("mytask");
httptask.url("http://worldtimeapi.org/api/timezone/Asia/Kolkata");
workflow.add(httptask);//workflow is an object of ConductorWorkflow<WorkflowInput>
```

##### JSON Configuration

```json
{
  "name": "http_task",
  "taskReferenceName": "http_task_ref",
  "type" : "HTTP",
  "uri": "https://orkes-api-tester.orkesconductor.com/api",
  "method": "GET"
}
```

#### Javascript Executor Task

Execute ECMA-compliant Javascript code. It is useful when writing a script for data mapping, calculations, etc.

##### Using Code to Create Inline Task

```java
import com.netflix.conductor.sdk.workflow.def.tasks.Javascript;
Javascript jstask = new Javascript("hello_script",
                  """function greetings(name) {
                     return {
                        "text": "hello " + name
                            }
                      }
                    greetings("Orkes");""");
workflow.add(jstask);
```

##### JSON Configuration

```json
{
  "name": "inline_task",
  "taskReferenceName": "inline_task_ref",
  "type": "INLINE",
  "inputParameters": {
    "expression": " function greetings() {\n return {\n     \"text\": \"hello \" + $.name\n        }\n    }\n    greetings();",
    "evaluatorType": "graaljs",
    "name": "${workflow.input.name}"
  }
}
```

#### JSON Processing using JQ

[Jq](https://jqlang.github.io/jq/) is like sed for JSON data - you can slice, filter, map, and transform structured data with the same ease that sed, awk, grep, and friends let you play with text.

##### Using Code to Create JSON JQ Transform Task

```java
import com.netflix.conductor.sdk.workflow.def.tasks.JQ;

JQ jqtask = new JQ("jq_task", "{ key3: (.key1.value1 + .key2.value2) }");
workflow.add(jqtask);
```

##### JSON Configuration

```json
{
  "name": "json_transform_task",
  "taskReferenceName": "json_transform_task_ref",
  "type": "JSON_JQ_TRANSFORM",
  "inputParameters": {
    "key1": "k1",        
    "key2": "k2",
    "queryExpression": "{ key3: (.key1.value1 + .key2.value2) }",
  }
}
```

### Worker vs. Microservice/HTTP Endpoints

> [!tip]
> Workers are a lightweight alternative to exposing an HTTP endpoint and orchestrating using `HTTP` tasks. Using workers is a recommended approach if you do not need to expose the service over HTTP or gRPC endpoints.

There are several advantages to this approach:

1. **No need for an API management layer**: Given there are no exposed endpoints and workers are self-load-balancing.
2. **Reduced infrastructure footprint**: No need for an API gateway/load balancer.
3. All the communication is initiated by workers using polling - avoiding the need to open up any incoming TCP ports.
4. Workers **self-regulate** when busy; they only poll as much as they can handle. Backpressure handling is done out of the box.
5. Workers can be scaled up / down quickly based on the demand by increasing the number of processes.

### Deploying Workers in Production

Conductor workers can run in the cloud-native environment or on-prem and can easily be deployed like any other Java application. Workers can run a containerized environment, VMs, or bare metal like you would deploy your other Java applications.


## Create Conductor Workflows

Workflow can be defined as the collection of tasks and operators that specify the order and execution of the defined tasks. This orchestration occurs in a hybrid ecosystem that encircles serverless functions, microservices, and monolithic applications.

This section will dive deeper into creating and executing Conductor workflows using Java SDK.

### Creating Workflows

Conductor lets you create the workflows using either Java or JSON as the configuration.

Using Java as code to define and execute workflows lets you build extremely powerful, dynamic workflows and run them on Conductor.

When the workflows are relatively static, they can be designed using the Orkes UI (available when using Orkes Conductor) and APIs or SDKs to register and run the workflows.

Both the code and configuration approaches are equally powerful and similar in nature to how you treat Infrastructure as Code.

### Execute Dynamic Workflows Using Code

For cases where the workflows cannot be created statically ahead of time, Conductor is a powerful dynamic workflow execution platform that lets you create very complex workflows in code and execute them. It is useful when the workflow is unique for each execution.

`CreateWorkflow.java`

```java
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

public class CreateWorkflow {

    private final WorkflowExecutor executor;

    public WorkflowCreator(WorkflowExecutor executor) {
        this.executor = executor;
    }

    public ConductorWorkflow<WorkflowInput> createSimpleWorkflow() {
        ConductorWorkflow<WorkflowInput> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("email_send_workflow");
        workflow.setVersion(1);

        SimpleTask getUserDetails = new SimpleTask("get_user_info", "get_user_info");
        getUserDetails.input("userId", "${workflow.input.userId}");

        // send email
        SimpleTask sendEmail = new SimpleTask("send_email", "send_email");
        // get user details user info, which contains the email field
        sendEmail.input("email", "${get_user_info.output.email}");

        workflow.add(getUserDetails);
        workflow.add(sendEmail);

        return workflow;
    }
    
}
```

`ConductorWorkers.java`

```java
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

public class ConductorWorkers {

    @WorkerTask("get_user_info")
    public UserInfo getUserInfo(@InputParam("userId") String userId) {
        UserInfo userInfo =  new UserInfo("User X", userId);
        userInfo.setEmail(userId + "@example.com");
        userInfo.setPhoneNumber("555-555-5555");
        return userInfo;
    }

    @WorkerTask("send_email")
    public void sendEmail(@InputParam("email") String email) {
        System.out.println("Sending email to " + email);
    }
}
```

See [DynamicWorkflow](../../examples/java/io/orkes/conductor/sdk/DynamicWorkflow) for a fully functional example.

#### Kitchen-Sink Workflow

For a more complex workflow example with all the supported features, see [KitchenSink.java](../../examples/java/io/orkes/conductor/sdk/KitchenSink.java)

### Executing Workflows

The [WorkflowClient](/src/main/java/io/orkes/conductor/client/WorkflowClient.java) interface provides all the APIs required to work with workflow executions.

```java
import com.netflix.conductor.client.http.WorkflowClient;
WorkflowClient wfClient = utils.getWorkflowClient();
String workflowId = wfClient.startWorkflow(startWorkflowReq);
```

#### Execute Workflow Asynchronously

Useful when workflows are long-running.

```java
import com.netflix.conductor.client.http.WorkflowClient;
WorkflowClient wfClient = utils.getWorkflowClient();
String workflowId = wfClient.startWorkflow(startWorkflowReq);
```

#### Execute Workflow Synchronously

Applicable when workflows complete very quickly - usually under 20-30 seconds.

```java
Workflow workflowRun = workflowExecution.get(10, TimeUnit.SECONDS);
```

### Managing Workflow Executions

> [!note]
> See [WorkflowOps.java](../../examples/java/io/orkes/conductor/sdk/WorkflowOps.java) for a fully working application that demonstrates working with the workflow executions and sending signals to the workflow to manage its state.

Workflows represent the application state. With Conductor, you can query the workflow execution state anytime during its lifecycle. You can also send signals to the workflow that determines the outcome of the workflow state.

`WorkflowClient` is the client interface used to manage workflow executions.

```java
import io.orkes.conductor.client.OrkesClients;
import com.netflix.conductor.client.http.ApiClient;
OrkesClients orkesClients = OrkesClients(getApiClientWithCredentials());
WorkflowClient workflowClient = orkesClients.getWorkflowClient();
```

### Get Execution Status

The following method lets you query the status of the workflow execution given the id. When the include_tasks is set, the response also includes all the completed and in-progress tasks.

```java
getWorkflowStatusSummary(String workflowId, Boolean includeOutput, Boolean includeVariables)
```

### Update Workflow State Variables

Variables inside a workflow are the equivalent of global variables in a program.

```java
setVariables(Map<String, Object> variables)
```

### Terminate Running Workflows

Used to terminate a running workflow. Any pending tasks are canceled, and no further work is scheduled for this workflow upon termination.

```java
terminateWorkflow(List<String> workflowIds, String reason)
```

### Retry Failed Workflows

If the workflow has failed due to one of the task failures after exhausting the retries for the task, the workflow can still be resumed by calling the retry.

```java
retryWorkflow(List<String> workflowIds)
```

When a sub-workflow inside a workflow has failed, there are two options:

1. Re-trigger the sub-workflow from the start (Default behavior).
2. Resume the `sub-workflow` from the failed task (set `resume_subworkflow_tasks` to True).

### Restart Workflows

A workflow in the terminal state (COMPLETED, TERMINATED, FAILED) can be restarted from the beginning. Useful when retrying from the last failed task is insufficient, and the whole workflow must be started again.

```java
restartWorkflow(List<String> workflowIds, Boolean useLatestDefinitions)
```

### Rerun Workflow from a Specific Task

In the cases where a workflow needs to be restarted from a specific task rather than from the beginning, rerun provides that option. When issuing the rerun command to the workflow, you can specify the task ID from where the workflow should be restarted (as opposed to from the beginning), and optionally, the workflow's input can also be changed.

```java
setReRunFromTaskId(String reRunFromTaskId)
```

> [!tip]
> Rerun is one of the most powerful features Conductor has, giving you unparalleled control over the workflow restart.

### Pause Running Workflow

A running workflow can be put to a **PAUSED** status. A paused workflow lets the currently running tasks complete but does not schedule any new tasks until resumed.

```java
pauseWorkflow(List<String> workflowIds)
```

### Resume Paused Workflow

Resume operation resumes the currently paused workflow, immediately evaluating its state and scheduling the next set of tasks.

```java
resumeWorkflow(List<String> workflowIds)
```

### Searching for Workflows

Workflow executions are retained until removed from the Conductor. This gives complete visibility into all the executions an application has - regardless of the number of executions. Conductor has a powerful search API that allows you to search for workflow executions.

```java
searchWorkflows(queryId, start, size, sort, freeText, query, skipCache);
```

- **free_text:** Free text search to look for specific words in the workflow and task input/output
- **query** -  SQL-like query to search against specific fields in the workflow.

Here are the supported fields for the query:

| Field |  Description |
| ----- | ------------ |
| status | The status of the workflow. |
| correlationId | The ID to correlate the workflow execution to other executions. | 
| workflowType | The name of the workflow. | 
| version | The version of the workflow. | 
| startTime	| The start time of the workflow is in milliseconds. |

### Handling Failures, Retries and Rate Limits

Conductor lets you embrace failures rather than worry about the complexities introduced in the system to handle failures.

All the aspects of handling failures, retries, rate limits, etc., are driven by the configuration that can be updated in real time without re-deploying your application.

#### Retries

Each task in the Conductor workflow can be configured to handle failures with retries, along with the retry policy (linear, fixed, exponential backoff) and maximum number of retry attempts allowed.

See [Error Handling](https://orkes.io/content/error-handling) for more details.

#### Rate Limits

What happens when a task is operating on a critical resource that can only handle a few requests at a time? Tasks can be configured to have a fixed concurrency (X request at a time) or a rate (Y tasks/time window).

#### Task Registration

```java
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.MetadataClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.TaskClient;
import io.orkes.conductor.client.WorkflowClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


public class TaskDefinitionTest {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        OrkesClients orkesClients = OrkesClients(getApiClientWithCredentials());
        TaskClient taskClient = orkesClients.getTaskClient();
        WorkflowClient workflowClient = orkesClients.getWorkflowClient();
        MetadataClient metadataClient = orkesClients.getMetadataClient();
        //Get an instance of WorkflowExecutor
        WorkflowExecutor workflowExecutor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 10);
        TaskDef taskDef = new TaskDef();
        taskDef.setName("task_with_retries");
        taskDef.setRetryCount(3);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);
        //only allow 3 tasks at a time to be in the IN_PROGRESS status
        taskDef.setConcurrentExecLimit(3);
        //timeout the task if not polled within 60 seconds of scheduling
        taskDef.setPollTimeoutSeconds(60);
        //timeout the task if the task does not COMPLETE in 2 minutes
        taskDef.setTimeoutSeconds(120);
        //for the long running tasks, timeout if the task does not get updated in COMPLETED or IN_PROGRESS status in 60 seconds after the last update
        taskDef.setResponseTimeoutSeconds(60);
        //only allow 100 executions in a 10-second window! -- Note, this is complementary to concurrent_exec_limit
        taskDef.setRateLimitPerFrequency(100);
        taskDef.setRateLimitFrequencyInSeconds(10);
        List<TaskDef> taskDefs = new ArrayList<TaskDef>();
        taskDefs.add(taskDef);
        metadataClient.registerTaskDefs(taskDefs);

    }
}
```

```json
{
  "name": "task_with_retries",
  
  "retryCount": 3,
  "retryLogic": "LINEAR_BACKOFF",
  "retryDelaySeconds": 1,
  "backoffScaleFactor": 1,
  
  "timeoutSeconds": 120,
  "responseTimeoutSeconds": 60,
  "pollTimeoutSeconds": 60,
  "timeoutPolicy": "TIME_OUT_WF",
  
  "concurrentExecLimit": 3,
  
  "rateLimitPerFrequency": 0,
  "rateLimitFrequencyInSeconds": 1
}
```

#### Update Task Definition:

```shell
POST /api/metadata/taskdef -d @task_def.json
```

See [TaskConfigure.java](../../examples/java/io/orkes/conductor/sdk/TaskConfigure.java) for a detailed working app.

## Using Conductor in Your Application

Conductor SDKs are lightweight and can easily be added to your existing or new Java app. This section will dive deeper into integrating Conductor in your application.


### Adding Conductor SDK to Your Application

Add `orkes-conductor-client` dependency to your project.

#### Gradle

For Gradle-based projects, modify the `build.gradle` file in the project directory by adding the following line to the dependencies block in that file:

```
implementation 'io.orkes.conductor:orkes-conductor-client:2.0.1'
```

#### Maven

For Maven-based projects, modify the `pom.xml` file in the project directory by adding the following XML snippet within the `dependencies` section:

```
<dependency>
  <groupId>io.orkes.conductor</groupId>
  <artifactId>orkes-conductor-client</artifactId>
  <version>1.1.14</version>
</dependency>
```

### Testing Workflows

Conductor SDK for Java provides a complete feature testing framework for your workflow-based applications. The framework works well with any testing framework you prefer without imposing any specific framework.

The Conductor server provides a test endpoint `POST /api/workflow/test` that allows you to post a workflow along with the test execution data to evaluate the workflow.

The goal of the test framework is as follows:

1. Ability to test the various branches of the workflow.
2. Confirm the workflow execution and tasks given a fixed set of inputs and outputs.
3. Validate that the workflow completes or fails given specific inputs.

Here are example assertions from the test:

```java
import static org.junit.Assert.*;

Workflow workflowRun = workflowExecution.get(10, TimeUnit.SECONDS);
String status = String.valueOf(workflowRun.getStatus());
assertEquals(status,"COMPLETED");
```
You can add the JUnit dependency by adding the following to your project:

#### Gradle

For Gradle-based projects, modify the `build.gradle` file in the project directory by adding the following line to the dependencies block in that file:

```
testImplementation "org.junit.jupiter:junit-jupiter-api:{{VERSION}}"
testRuntimeOnly "org.junit.jupiter:junit-jupiter-engine:{{VERSION}}"
```

#### Maven

For Maven-based projects, modify the `pom.xml` file in the project directory by adding the following XML snippet within the `dependencies` section:

```
<dependency>
  <groupId>junit</groupId>
  <artifactId>junit</artifactId>
  <version>{{VERSION}}</version>
  <scope>test</scope>
</dependency>
```

>[!note]
>Workflow workers are your regular Java functions and can be tested with any available testing framework.

#### Example Unit Testing Application

See [WorkflowTest.java](../examples/java/io/orkes/conductor/sdk/WorkflowTest.java) for a fully functional example of how to test a moderately complex workflow with branches.

### Workflow Deployments Using CI/CD

>[!tip]
>Treat your workflow definitions just like your code. Suppose you are defining the workflows using UI. In that case, we recommend checking the JSON configuration into the version control and using your development workflow for CI/CD to promote the workflow definitions across various environments such as Dev, Test, and Prod.

Here is a recommended approach when defining workflows using JSON:

- Treat your workflow metadata as code.
- Check in the workflow and task definitions along with the application code.
- Use `POST /api/metadata/* endpoints` or MetadataClient(com.conductor.client.MetadataClient) to register/update workflows as part of the deployment process.
- Version your workflows. If there is a significant change, change the version field of the workflow. See versioning workflows below for more details.

### Versioning Workflows

A powerful feature of Conductor is the ability to version workflows. You should increment the version of the workflow when there is a significant change to the definition. You can run multiple versions of the workflow at the same time. When starting a new workflow execution, use the `version` field to specify which version to use. When omitted, the latest (highest-numbered) version is used.

- Versioning allows safely testing changes by doing canary testing in production or A/B testing across multiple versions before rolling out.
- A version can also be deleted, effectively allowing for "rollback" if required.
