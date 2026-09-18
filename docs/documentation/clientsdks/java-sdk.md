---
description: "Build Conductor workers in Java with automated polling, thread management, and Spring Boot integration."
source_repo: "https://github.com/conductor-oss/java-sdk"
sdk_page: java
---

# Java SDK

## Install the SDK

The SDK requires Java 21+. Add the following dependency to your project:

**For Gradle:**

```gradle
dependencies {
    implementation 'org.conductoross:conductor-client:VERSION'

    // Optionally, you can also add spring module for auto configuration
    // implementation 'org.conductoross:conductor-client-spring:VERSION'
}
```

**For Maven:**

```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client</artifactId>
    <version>VERSION</version>
</dependency>
```
*Optionally, you can also add spring module for auto configuration*
```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client-spring</artifactId>
    <version>VERSION</version>
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

That's it -- you just defined a worker, built a workflow, and executed it. Open the UI for the Conductor server you configured to inspect the execution.

## Comprehensive worker example

See [examples/basics/hello-world/](https://github.com/conductor-oss/java-sdk/tree/main/examples/basics/hello-world) for a complete working example with:
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
- [Worker SDK Guide](https://github.com/conductor-oss/java-sdk/blob/main/docs/workers.md) — Complete worker framework documentation
- [Worker Examples](https://github.com/conductor-oss/java-sdk/blob/main/examples/) — Sample worker implementations

## Monitoring Workers

Enable metrics collection for monitoring workers:

```java
// Using conductor-client-metrics module
dependencies {
    implementation 'org.conductoross:conductor-client-metrics:VERSION'
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
- [Workflow SDK Guide](https://github.com/conductor-oss/java-sdk/blob/main/docs/workflows.md) — Workflow-as-code documentation
- [Workflow Testing](https://github.com/conductor-oss/java-sdk/blob/main/docs/workflow-testing.md) — Unit testing workflows

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

## File handling

Binary workflow values are opaque `conductor://file/<id>` strings. Workers inject `org.conductoross.conductor.client.FileClient`, receive handle strings as task inputs, and publish handle strings as outputs. Upload and download are explicit; the task runner does not scan worker objects for files.

Every operation requires the workflow ID:

```java
public String upload(String workflowId, Path source);

public String upload(
        String workflowId,
        Path source,
        FileUploadOptions options);

public String upload(
        String workflowId,
        InputStream source,
        FileUploadOptions options);

public Path download(
        String workflowId,
        String fileHandleId,
        Path destination);

public FileMetadata getMetadata(
        String workflowId,
        String fileHandleId);
```

`FileUploadOptions` supports `fileName`, `contentType`, and optional producing `taskId`. Multipart is deliberately absent from the options: `FileClient` selects it automatically from the source size, configured threshold, and provider capability.

### Upload a path with inferred filename

```java
Path report = Path.of("/work/monthly-report.pdf");
String handle = fileClient.upload(workflowId, report);
```

The source must be a readable regular file. Its final path segment becomes the filename.

### Upload a path with metadata

```java
String handle = fileClient.upload(
        task.getWorkflowInstanceId(),
        report,
        new FileUploadOptions()
                .setFileName("customer-report.pdf")
                .setContentType("application/pdf")
                .setTaskId(task.getTaskId()));
```

### Upload a stream

```java
FileUploadOptions options = new FileUploadOptions()
        .setFileName("events.ndjson")
        .setContentType("application/x-ndjson")
        .setTaskId(task.getTaskId());

try (InputStream source = eventStore.openExport()) {
    String handle = fileClient.upload(task.getWorkflowInstanceId(), source, options);
    // FileClient does not close source; this try-with-resources block owns it.
}
```

A stream upload requires a safe filename. `FileClient` buffers the stream into a repeatable temporary path before creating the server record, removes the temporary file afterward, and never closes the caller-owned stream.

### Read metadata

```java
FileMetadata metadata = fileClient.getMetadata(workflowId, handle);

System.out.printf(
        "%s: %s, %d bytes, status=%s%n",
        metadata.getFileName(),
        metadata.getContentType(),
        metadata.getFileSize(),
        metadata.getUploadStatus());
```

### Download to a path

```java
Path destination = Path.of("/work/input.pdf");
Path downloaded = fileClient.download(workflowId, handle, destination);
```

The destination may be new or existing. The client downloads to a unique sibling temporary file and atomically replaces the destination only after the transfer succeeds. A failed download removes the temporary file and leaves an existing destination unchanged.

### Pass a file between workers

```java
public final class RenderWorker implements Worker {
    private final FileClient fileClient;

    public RenderWorker(FileClient fileClient) {
        this.fileClient = fileClient;
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        Path source = null;
        Path rendered = null;
        try {
            String workflowId = task.getWorkflowInstanceId();
            String sourceHandle = (String) task.getInputData().get("source");
            source = Files.createTempFile("source-", ".bin");
            rendered = Files.createTempFile("rendered-", ".pdf");
            fileClient.download(workflowId, sourceHandle, source);
            render(source, rendered);

            String renderedHandle = fileClient.upload(
                    workflowId,
                    rendered,
                    new FileUploadOptions()
                            .setContentType("application/pdf")
                            .setTaskId(task.getTaskId()));

            result.setStatus(TaskResult.Status.COMPLETED);
            result.addOutputData("rendered", renderedHandle);
        } catch (Exception e) {
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(e.getMessage());
        } finally {
            deleteQuietly(source);
            deleteQuietly(rendered);
        }
        return result;
    }
}
```

The workflow maps `${render.output.rendered}` into the next task as a plain string. A parent or sub-workflow in the same workflow family can read metadata and download the handle. Only the exact owning workflow can refresh or complete its upload.

### Automatic multipart and retries

Spring auto-configuration creates `FileClient` and reads these settings:

```properties
conductor.file-client.retry-count=3
conductor.file-client.multipart-threshold=104857600
conductor.file-client.multipart-part-size=10485760
```

Files larger than the threshold use multipart for S3 and Azure Blob. GCS, local storage, and generic HTTP(S) signed URLs stay single-request. Before each retry the client obtains a fresh signed URL; it retries only transient I/O failures, throttling, expired signatures, and server errors, and stops when the thread is interrupted.

See the runnable [Media Transcoder example](https://github.com/conductor-oss/java-sdk/tree/main/examples/file-storage/media-transcoder), [File Storage](../advanced/file-storage.md) for server configuration, and [File API](../api/files.md) for the REST contract.

---

## AI & LLM Workflows

Conductor supports AI-native workflows including agentic tool calling, RAG pipelines, and multi-agent orchestration.

**Agentic Workflows**

Build AI agents where LLMs dynamically select and call Java workers as tools. All agentic examples live in [`AgenticExamplesRunner.java`](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/agentic/AgenticExamplesRunner.java) — a single unified runner.

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
| [RagWorkflowExample.java](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/agentic/RagWorkflowExample.java) | End-to-end RAG: document indexing, semantic search, answer generation |
| [VectorDbExample.java](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/agentic/VectorDbExample.java) | Vector database operations: text indexing, embedding generation, and semantic search |

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
| [Hello World](https://github.com/conductor-oss/java-sdk/tree/main/examples/basics/hello-world) | Minimal workflow with worker | `./gradlew :examples:run -PmainClass=com.netflix.conductor.sdk.examples.helloworld.Main` |
| [Workflow Operations](https://github.com/conductor-oss/java-sdk/tree/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/workflowops) | Pause, resume, terminate workflows | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.workflowops.Main` |
| [Shipment Workflow](https://github.com/conductor-oss/java-sdk/tree/main/examples/old/src/main/java/com/netflix/conductor/sdk/examples/shipment) | Real-world order processing | `./gradlew :examples:run -PmainClass=com.netflix.conductor.sdk.examples.shipment.Main` |
| [Events](https://github.com/conductor-oss/java-sdk/tree/main/examples/old/src/main/java/com/netflix/conductor/sdk/examples/events) | Event-driven workflows | `./gradlew :examples:run -PmainClass=com.netflix.conductor.sdk.examples.events.EventHandlerExample` |
| [All AI examples](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/agentic/AgenticExamplesRunner.java) | All agentic/LLM workflows | `./gradlew :examples:run --args="--all"` |
| [RAG Workflow](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/agentic/RagWorkflowExample.java) | RAG pipeline (index → search → answer) | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.agentic.RagWorkflowExample` |
| [Media Transcoder](https://github.com/conductor-oss/java-sdk/tree/main/examples/file-storage/media-transcoder) | File-handling pipeline: upload video → transcode → thumbnail → manifest | `mvn -f examples/file-storage/media-transcoder/pom.xml exec:java` |

## API Journey Examples

End-to-end examples covering all APIs for each domain:

| Example | APIs | Run |
|---------|------|-----|
| [Metadata Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/MetadataManagement.java) | Task & workflow definitions | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.MetadataManagement` |
| [Workflow Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/WorkflowManagement.java) | Start, monitor, control workflows | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.WorkflowManagement` |
| [Authorization Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/AuthorizationManagement.java) | Users, groups, permissions | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.AuthorizationManagement` |
| [Scheduler Management](https://github.com/conductor-oss/java-sdk/blob/main/examples/old/src/main/java/io/orkes/conductor/sdk/examples/SchedulerManagement.java) | Workflow scheduling | `./gradlew :examples:run -PmainClass=io.orkes.conductor.sdk.examples.SchedulerManagement` |

## Documentation

| Document | Description |
|----------|-------------|
| [Worker SDK](https://github.com/conductor-oss/java-sdk/blob/main/docs/workers.md) | Complete worker framework guide |
| [Workflow SDK](https://github.com/conductor-oss/java-sdk/blob/main/docs/workflows.md) | Workflow-as-code documentation |
| [Testing Framework](https://github.com/conductor-oss/java-sdk/blob/main/docs/workflow-testing.md) | Unit testing workflows and workers |
| [Conductor Client](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client/README.md) | HTTP client library documentation |
| [Client Metrics](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client-metrics/README.md) | Prometheus metrics collection |
| [Spring Integration](https://github.com/conductor-oss/java-sdk/blob/main/conductor-client-spring/README.md) | Spring Boot auto-configuration |
| [Examples](https://github.com/conductor-oss/java-sdk/blob/main/examples/README.md) | Complete examples catalog |

## Support

- [Open an issue (SDK)](https://github.com/conductor-oss/conductor-java-sdk/issues) for SDK bugs, questions, and feature requests
- [Open an issue (Conductor server)](https://github.com/conductor-oss/conductor/issues) for Conductor OSS server issues
- [Join the Conductor Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA) for community discussion and help
- [Orkes Community Forum](https://community.orkes.io/) for Q&A

## License

Apache 2.0


## Examples

Browse all examples on GitHub: [conductor-oss/java-sdk/examples](https://github.com/conductor-oss/java-sdk/tree/main/examples)

| Example | Type |
|---|---|
| [Readme](https://github.com/conductor-oss/java-sdk/blob/main/examples/README.md) | file |
| [Examples](https://github.com/conductor-oss/java-sdk/tree/main/examples) | directory |
