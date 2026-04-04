---
description: "Build Conductor workers in Java with automated polling, thread management, and Spring Boot integration."
---

# Java SDK

!!! info "Source"
    GitHub: [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk) | Report issues and contribute on GitHub.

## Start Conductor server

If you don't already have a Conductor server running, pick one:

**Docker (recommended, includes UI):**

```shell
docker run -p 8080:8080 conductoross/conductor:latest
```
The UI will be available at `http://localhost:8080` and the API at `http://localhost:8080/api`

**MacOS / Linux (one-liner):** (If you don't want to use docker, you can install and run the binary directly)
```shell
curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh
```

**Conductor CLI**
```shell
# Installs conductor cli
npm install -g @conductor-oss/conductor-cli

# Start the open source conductor server
conductor server start
# see conductor server --help for all the available commands
```

## Install the SDK

The SDK requires Java 17+. Add the following dependency to your project:

**For Gradle:**

```gradle
dependencies {
    implementation 'org.conductoross:conductor-client:5.0.1'

    // Optionally, you can also add spring module for auto configuration
    // implementation 'org.conductoross:conductor-client-spring:5.0.1'
}
```

**For Maven:**

```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client</artifactId>
    <version>5.0.1</version>
</dependency>
```
*Optionally, you can also add spring module for auto configuration*
```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client-spring</artifactId>
    <version>5.0.1</version>
</dependency>
```


## 60-Second Quickstart

**Step 1: Write a worker**

Workers are Java classes that implement the `Worker` interface and poll Conductor for tasks to execute.

```java
public class GreetWorker implements Worker {
    
    @Override
    public String getTaskDefName() {
        return "greet";
    }

    @Override
    public TaskResult execute(Task task) {
        String name = (String) task.getInputData().get("name");
        TaskResult result = new TaskResult(task);
        result.setStatus(TaskResult.Status.COMPLETED);
        result.addOutputData("greeting", "Hello, " + name + "!");
        return result;
    }
}
```

**Step 2: Run your first workflow app**

Create a `Main.java` with the following:

```java
import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.OrkesClients;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // Configure the SDK via ApiClient (enterprise-compatible path)
        ApiClient apiClient = ApiClient.builder().build();
        OrkesClients clients = new OrkesClients(apiClient);

        // Create workflow executor
        WorkflowExecutor executor = new WorkflowExecutor(apiClient, 100);

        // Build and register the workflow
        ConductorWorkflow<Map> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("greetings");
        workflow.setVersion(1);

        SimpleTask greetTask = new SimpleTask("greet", "greet_ref");
        greetTask.input("name", "${workflow.input.name}");
        workflow.add(greetTask);
        workflow.registerWorkflow(true, true);

        // Start polling for tasks using OrkesTaskClient
        TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(
                clients.getTaskClient(),
                List.of(new GreetWorker())
        ).withThreadCount(10).build();
        configurer.init();

        // Run the workflow using OrkesWorkflowClient
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("greetings");
        request.setVersion(1);
        request.setInput(Map.of("name", "Conductor"));
        String workflowId = clients.getWorkflowClient().startWorkflow(request);

        System.out.println("Started workflow: " + workflowId);
        System.out.println("View execution at: " + apiClient.getBasePath().replace("/api", "") + "/execution/" + workflowId);
    }
}
```

Run it:

```shell
./gradlew run
```

> ### Using Orkes Conductor / Remote Server?
> Export your authentication credentials as well:
>
> ```shell
> export CONDUCTOR_SERVER_URL="https://your-cluster.orkesconductor.io/api"
>
> # If using Orkes Conductor that requires auth key/secret
> export CONDUCTOR_AUTH_KEY="your-key"
> export CONDUCTOR_AUTH_SECRET="your-secret"
> ```

That's it -- you just defined a worker, built a workflow, and executed it. Open the Conductor UI (default:
[http://localhost:8080](http://localhost:8080)) to see the execution.

## Comprehensive worker example

See [examples/src/main/java/com/netflix/conductor/sdk/examples/helloworld/](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/com/netflix/conductor/sdk/examples/helloworld/) for a complete working example with:
- Workflow definition using the SDK
- Worker implementation with annotations
- Workflow execution and monitoring

---

## Workers

Workers are Java classes that execute Conductor tasks. Implement the `Worker` interface or use the `@WorkerTask` annotation:

**Using Worker interface:**

```java
public class MyWorker implements Worker {
    
    @Override
    public String getTaskDefName() {
        return "my_task";
    }

    @Override
    public TaskResult execute(Task task) {
        // Your business logic here
        TaskResult result = new TaskResult(task);
        result.setStatus(TaskResult.Status.COMPLETED);
        result.addOutputData("result", "Task completed successfully");
        return result;
    }
}
```

**Using @WorkerTask annotation:**

```java
public class Workers {
    
    @WorkerTask("greet")
    public String greet(@InputParam("name") String name) {
        return "Hello, " + name + "!";
    }
    
    @WorkerTask("process_data")
    public Map<String, Object> processData(@InputParam("data") Map<String, Object> data) {
        // Process and return data
        return Map.of("processed", true, "result", data);
    }
}
```

**Start workers** with `TaskRunnerConfigurer` or `WorkflowExecutor`:

```java
// Option 1: Using TaskRunnerConfigurer
ApiClient apiClient = ApiClient.builder().build();
OrkesClients clients = new OrkesClients(apiClient);

TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(
    clients.getTaskClient(),
    List.of(new MyWorker(), new AnotherWorker())
)
.withThreadCount(10)
.build();
configurer.init();

// Option 2: Using WorkflowExecutor (auto-discovers @WorkerTask annotations)
WorkflowExecutor executor = new WorkflowExecutor(apiClient, 10);
executor.initWorkers("com.mycompany.workers");  // Package to scan for @WorkerTask
```

**Worker Design Principles:**

- Workers should be stateless and idempotent
- Handle failure scenarios gracefully
- Report status back to Conductor
- Complete execution quickly (or use polling for long-running tasks)

**Worker vs. HTTP Endpoints:**

| Feature | Worker | HTTP Endpoint |
|---------|--------|---------------|
| Deployment | Embedded in application | Separate service |
| Scalability | Horizontal (add more instances) | Horizontal (add more instances) |
| Latency | Lower (direct polling) | Higher (network overhead) |
| Complexity | Simple | Complex (service mesh, load balancer) |

**Learn more:**
- [Worker SDK Guide](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/worker_sdk.md) — Complete worker framework documentation
- [Worker Examples](https://github.com/conductor-oss/java-sdk/blob/main/examples/) — Sample worker implementations

## Monitoring Workers

Enable metrics collection for monitoring workers:

```java
// Using conductor-client-metrics module
dependencies {
    implementation 'org.conductoross:conductor-client-metrics:5.0.1'
}
```

```java
// Configure metrics with Prometheus
TaskRunnerConfigurer configurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
    .withThreadCount(10)
    .withMetricsCollector(new PrometheusMetricsCollector())
    .build();
```

See [conductor-client-metrics/README.md](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client-metrics/README.md) for full metrics documentation.

## Workflows

Define workflows in Java using the `ConductorWorkflow` builder:

```java
ConductorWorkflow<MyInput> workflow = new ConductorWorkflow<>(executor);
workflow.setName("my_workflow");
workflow.setVersion(1);
workflow.setOwnerEmail("team@example.com");

// Add tasks
SimpleTask task1 = new SimpleTask("task1", "task1_ref");
SimpleTask task2 = new SimpleTask("task2", "task2_ref");
workflow.add(task1);
workflow.add(task2);

// Register the workflow
workflow.registerWorkflow(true, true);
```

**Execute workflows:**

```java
ApiClient apiClient = ApiClient.builder().build();
OrkesClients clients = new OrkesClients(apiClient);
WorkflowClient workflowClient = clients.getWorkflowClient();

// Synchronous (start and poll for completion)
CompletableFuture<Workflow> future = workflow.execute(input);
Workflow result = future.get(30, TimeUnit.SECONDS);
System.out.println("Output: " + result.getOutput());

// Asynchronous (returns workflow ID immediately)
StartWorkflowRequest request = new StartWorkflowRequest();
request.setName("my_workflow");
request.setVersion(1);
request.setInput(Map.of("key", "value"));
String workflowId = workflowClient.startWorkflow(request);

// Dynamic execution (sends workflow definition with request)
CompletableFuture<Workflow> dynamicRun = workflow.executeDynamic(input);
```

**Manage running workflows:**

```java
// Get workflow status
Workflow wf = workflowClient.getWorkflow(workflowId, true);
System.out.println("Status: " + wf.getStatus());

// Pause, resume, terminate
workflowClient.pauseWorkflow(workflowId);
workflowClient.resumeWorkflow(workflowId);
workflowClient.terminateWorkflow(workflowId, "No longer needed");

// Retry and restart failed workflows
workflowClient.retryWorkflow(workflowId);
workflowClient.restartWorkflow(workflowId, false);
```

**Learn more:**
- [Workflow SDK Guide](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/workflow_sdk.md) — Workflow-as-code documentation
- [Workflow Testing](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/testing_framework.md) — Unit testing workflows

## Troubleshooting

**Worker stops polling or crashes:**
- Check network connectivity to Conductor server
- Verify `CONDUCTOR_SERVER_URL` is set correctly
- Ensure sufficient thread pool size for your workload
- Monitor JVM memory and GC pauses

**Connection refused errors:**
- Verify Conductor server is running: `curl http://localhost:8080/health`
- Check firewall rules if connecting to remote server
- For Orkes Conductor, verify auth credentials are correct

**Tasks stuck in SCHEDULED state:**
- Ensure workers are polling for the correct task type
- Check that `getTaskDefName()` matches the task name in workflow
- Verify worker thread count is sufficient

**Workflow execution timeout:**
- Increase workflow timeout in definition
- Check if tasks are completing within expected time
- Monitor Conductor server logs for errors

**Authentication errors with Orkes Conductor:**
- Verify `CONDUCTOR_AUTH_KEY` and `CONDUCTOR_AUTH_SECRET` are set
- Ensure the application has required permissions
- Check that credentials haven't expired

---

## AI & LLM Workflows

Conductor supports AI-native workflows including agentic tool calling, RAG pipelines, and multi-agent orchestration.

**Agentic Workflows**

Build AI agents where LLMs dynamically select and call Java workers as tools. All agentic examples live in [`AgenticExamplesRunner.java`](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/agentic/AgenticExamplesRunner.java) — a single unified runner.

| Workflow | Description |
|----------|-------------|
| `llm_chat_workflow` | Automated multi-turn Q&A using `LLM_CHAT_COMPLETE` system task |
| `llm_chat_human_in_loop` | Interactive chat with WAIT task pauses for user input |
| `multiagent_chat_demo` | Multi-agent debate with moderator routing between two LLM panelists |
| `function_calling_workflow` | LLM picks which Java worker to call, returns JSON, dispatch worker executes it |
| `mcp_ai_agent` | AI agent using MCP tools (ListMcpTools → LLM plans → CallMcpTool → summarize) |

**LLM and RAG Workflows**

| Example | Description |
|---------|-------------|
| [RagWorkflowExample.java](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/agentic/RagWorkflowExample.java) | End-to-end RAG: document indexing, semantic search, answer generation |
| [VectorDbExample.java](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/agentic/VectorDbExample.java) | Vector database operations: text indexing, embedding generation, and semantic search |

**Using LLM Tasks in Workflows:**

```java
// Chat completion task (LLM_CHAT_COMPLETE system task)
LlmChatComplete chatTask = new LlmChatComplete("chat_assistant", "chat_ref")
    .llmProvider("openai")
    .model("gpt-4o-mini")
    .messages(List.of(
        Map.of("role", "system", "message", "You are a helpful assistant."),
        Map.of("role", "user", "message", "${workflow.input.question}")
    ))
    .temperature(0.7)
    .maxTokens(500);

// Text completion task (LLM_TEXT_COMPLETE system task)
LlmTextComplete textTask = new LlmTextComplete("generate_text", "text_ref")
    .llmProvider("openai")
    .model("gpt-4o-mini")
    .promptName("my-prompt-template")
    .temperature(0.7);

// Document indexing for RAG (LLM_INDEX_DOCUMENT system task)
LlmIndexDocument indexTask = new LlmIndexDocument("index_doc", "index_ref")
    .vectorDb("pinecone")
    .namespace("my-docs")
    .index("knowledge-base")
    .embeddingModel("text-embedding-ada-002")
    .text("${workflow.input.document}");

// Semantic search (LLM_SEARCH_INDEX system task)
LlmSearchIndex searchTask = new LlmSearchIndex("search_docs", "search_ref")
    .vectorDb("pinecone")
    .namespace("my-docs")
    .index("knowledge-base")
    .query("${workflow.input.question}")
    .topK(5);

// MCP tool discovery (MCP_LIST_TOOLS system task — Orkes Conductor)
ListMcpTools listTools = new ListMcpTools("discover_tools", "tools_ref")
    .mcpServer("http://localhost:3001/mcp");

// MCP tool execution (MCP_CALL_TOOL system task — Orkes Conductor)
CallMcpTool callTool = new CallMcpTool("execute_tool", "tool_ref")
    .mcpServer("http://localhost:3001/mcp")
    .method("${tools_ref.output.result.method}")
    .arguments("${tools_ref.output.result.arguments}");

workflow.add(chatTask);
workflow.add(textTask);
workflow.add(indexTask);
```

Run all agentic examples:

```shell
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export OPENAI_API_KEY=your-key   # or ANTHROPIC_API_KEY

# Run all examples end-to-end
./gradlew :examples:run --args="--all"

# Run specific workflow
./gradlew :examples:run --args="--menu"
```

## Examples

See the [Examples Guide](https://github.com/conductor-oss/java-sdk/blob/main/examples/README.md) for the full catalog. Key examples:

| Example | Description | Run |
|---------|-------------|-----|
| [Hello World](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/com/netflix/conductor/sdk/examples/helloworld/) | Minimal workflow with worker | `./gradlew :examples:run -PmainClass=com.netflix.conductor.sdk.examples.helloworld.Main` |
| [Workflow Operations](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/workflowops/) | Pause, resume, terminate workflows | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.workflowops.Main` |
| [Shipment Workflow](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/com/netflix/conductor/sdk/examples/shipment/) | Real-world order processing | `./gradlew :examples:run -PmainClass=com.netflix.conductor.sdk.examples.shipment.Main` |
| [Events](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/com/netflix/conductor/sdk/examples/events/) | Event-driven workflows | `./gradlew :examples:run -PmainClass=com.netflix.conductor.sdk.examples.events.EventHandlerExample` |
| [All AI examples](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/agentic/AgenticExamplesRunner.java) | All agentic/LLM workflows | `./gradlew :examples:run --args="--all"` |
| [RAG Workflow](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/agentic/RagWorkflowExample.java) | RAG pipeline (index → search → answer) | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.agentic.RagWorkflowExample` |

## API Journey Examples

End-to-end examples covering all APIs for each domain:

| Example | APIs | Run |
|---------|------|-----|
| [Metadata Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/MetadataManagement.java) | Task & workflow definitions | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.MetadataManagement` |
| [Workflow Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/WorkflowManagement.java) | Start, monitor, control workflows | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.WorkflowManagement` |
| [Authorization Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/AuthorizationManagement.java) | Users, groups, permissions | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.AuthorizationManagement` |
| [Scheduler Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/src/main/java/io/orkes/conductor/sdk/examples/SchedulerManagement.java) | Workflow scheduling | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.SchedulerManagement` |

## Documentation

| Document | Description |
|----------|-------------|
| [Worker SDK](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/worker_sdk.md) | Complete worker framework guide |
| [Workflow SDK](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/workflow_sdk.md) | Workflow-as-code documentation |
| [Testing Framework](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/testing_framework.md) | Unit testing workflows and workers |
| [Conductor Client](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client/README.md) | HTTP client library documentation |
| [Client Metrics](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client-metrics/README.md) | Prometheus metrics collection |
| [Spring Integration](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client-spring/README.md) | Spring Boot auto-configuration |
| [Examples](https://github.com/conductor-oss/java-sdk/blob/main/examples/README.md) | Complete examples catalog |

## Support

- [Open an issue (SDK)](https://github.com/conductor-oss/conductor-java-sdk/issues) for SDK bugs, questions, and feature requests
- [Open an issue (Conductor server)](https://github.com/conductor-oss/conductor/issues) for Conductor OSS server issues
- [Join the Conductor Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA) for community discussion and help
- [Orkes Community Forum](https://community.orkes.io/) for Q&A

## Frequently Asked Questions

**Is this the same as Netflix Conductor?**

Yes. Conductor OSS is the continuation of the original [Netflix Conductor](https://github.com/Netflix/conductor) repository after Netflix contributed the project to the open-source foundation.

**Is this project actively maintained?**

Yes. [Orkes](https://orkes.io) is the primary maintainer and offers an enterprise SaaS platform for Conductor across all major cloud providers.

**Can Conductor scale to handle my workload?**

Conductor was built at Netflix to handle massive scale and has been battle-tested in production environments processing millions of workflows. It scales horizontally to meet virtually any demand.

**Does Conductor support durable code execution?**

Yes. Conductor ensures workflows complete reliably even in the face of infrastructure failures, process crashes, or network issues.

**Are workflows always asynchronous?**

No. While Conductor excels at asynchronous orchestration, it also supports synchronous workflow execution when immediate results are required.

**Do I need to use a Conductor-specific framework?**

No. Conductor is language and framework agnostic. Use your preferred language and framework -- the [SDKs](https://github.com/conductor-oss/conductor#conductor-sdks) provide native integration for Python, Java, JavaScript, Go, C#, and more.

**Can I mix workers written in different languages?**

Yes. A single workflow can have workers written in Python, Java, Go, or any other supported language. Workers communicate through the Conductor server, not directly with each other.

**What Java versions are supported?**

Java 17 and above.

**Should I use Worker interface or @WorkerTask annotation?**

Use `@WorkerTask` annotation for simpler, cleaner code -- input parameters are automatically mapped and return values become task output. Use the `Worker` interface when you need full control over task execution, access to task metadata, or custom error handling.

**How do I run workers in production?**

Workers are standard Java applications. Deploy them as you would any Java application -- in containers, VMs, or bare metal. Workers poll the Conductor server for tasks, so no inbound ports need to be opened.

**How do I test workflows without running a full Conductor server?**

The SDK provides a test framework that uses Conductor's `POST /api/workflow/test` endpoint to evaluate workflows with mock task outputs. See [Testing Framework](https://github.com/conductor-oss/java-sdk/blob/main/java-sdk/testing_framework.md) for details.

## License

Apache 2.0


## Examples

Browse all examples on GitHub: [conductor-oss/java-sdk/examples](https://github.com/conductor-oss/java-sdk/tree/main/examples)

| Example | Type |
|---|---|
| [Readme](https://github.com/conductor-oss/java-sdk/blob/main/examples/README.md) | file |
| [Src](https://github.com/conductor-oss/java-sdk/tree/main/examples/src) | directory |
