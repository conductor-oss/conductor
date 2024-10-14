# Conductor Client

This module provides the core client library to interact with Conductor through its HTTP API, enabling the creation, execution, and management of workflows, while also providing a framework for building workers.

## Getting Started

### Prerequisites
- Java 11 or higher
- A Gradle or Maven project properly set up
- A running Conductor server (local or remote)

### Using Conductor Client

1. **Add `conductor-client` dependency to your project**

For Gradle:
```groovy
implementation 'org.conductoross:conductor-client:4.0.0'
```

For Maven:
```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client</artifactId>
    <version>4.0.0</version>
</dependency>
```

2. **Create a `ConductorClient` instance**

Assuming your Conductor server is running at `localhost:8080`:

```java
import com.netflix.conductor.client.http.ConductorClient;

// … other code
var client = new ConductorClient("http://localhost:8080/api");
```

> **Note:** Use the Builder to configure options.

3. **Start a Workflow**

Use the [WorkflowClient](src/main/java/com/netflix/conductor/client/http/WorkflowClient.java) to start a workflow:

```java
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

// … other code
var client = new ConductorClient("http://localhost:8080/api");
var workflowClient = new WorkflowClient(client);
var workflowId = workflowClient.startWorkflow(new StartWorkflowRequest()
        .withName("hello_workflow")
        .withVersion(1));

System.out.println("Started workflow " + workflowId);
```

4. **Run a Worker**

```java
public class HelloWorker implements Worker {

    @Override
    public TaskResult execute(Task task) {
        var taskResult = new TaskResult(task);
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.getOutputData().put("message", "Hello World!");
        return taskResult;
    }

    @Override
    public String getTaskDefName() {
        return "hello_task";
    }

    public static void main(String[] args) {
        var client = new ConductorClient("http://localhost:8080/api");
        var taskClient = new TaskClient(client);
        var runnerConfigurer = new TaskRunnerConfigurer
                .Builder(taskClient, List.of(new HelloWorker()))
                .withThreadCount(10)
                .build();
        runnerConfigurer.init();
    }
}
```

> **Note:** The full code for the above examples can be found [here](../examples/src/main/java/com/netflix/conductor/gettingstarted).
