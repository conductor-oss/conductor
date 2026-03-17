---
description: "Build Conductor workers in JavaScript/TypeScript with workflow management and task polling."
---

# JavaScript SDK

!!! info "Source"
    GitHub: [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk) | Report issues and contribute on GitHub.

## Start Conductor server

If you don't already have a Conductor server running, pick one:

**Docker (recommended, includes UI):**

```shell
docker run -p 8080:8080 conductoross/conductor:latest
```

The UI will be available at `http://localhost:8080` and the API at `http://localhost:8080/api`.

**MacOS / Linux (one-liner):**

```shell
curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh
```

**Conductor CLI:**

```shell
npm install -g @conductor-oss/conductor-cli
conductor server start
```

## Install the SDK

```shell
npm install @io-orkes/conductor-javascript
```

## 60-Second Quickstart

**Step 1: Create a workflow**

Workflows are definitions that reference task types. We'll build a workflow called `greetings` that runs one worker task and returns its output.

```typescript
import { ConductorWorkflow, simpleTask } from "@io-orkes/conductor-javascript";

const workflow = new ConductorWorkflow(executor, "greetings")
  .add(simpleTask("greet_ref", "greet", { name: "${workflow.input.name}" }))
  .outputParameters({ result: "${greet_ref.output.result}" });

await workflow.register();
```

**Step 2: Write a worker**

Workers are TypeScript functions decorated with `@worker` that poll Conductor for tasks and execute them.

```typescript
import { worker } from "@io-orkes/conductor-javascript";

@worker({ taskDefName: "greet" })
async function greet(task: Task) {
  return {
    status: "COMPLETED",
    outputData: { result: `Hello ${task.inputData.name}` },
  };
}
```

**Step 3: Run your first workflow app**

Create a `quickstart.ts` with the following:

```typescript
import {
  OrkesClients,
  ConductorWorkflow,
  TaskHandler,
  worker,
  simpleTask,
} from "@io-orkes/conductor-javascript";
import type { Task } from "@io-orkes/conductor-javascript";

// A worker is any TypeScript function.
@worker({ taskDefName: "greet" })
async function greet(task: Task) {
  return {
    status: "COMPLETED" as const,
    outputData: { result: `Hello ${task.inputData.name}` },
  };
}

async function main() {
  // Configure the SDK (reads CONDUCTOR_SERVER_URL / CONDUCTOR_AUTH_* from env).
  const clients = await OrkesClients.from();
  const executor = clients.getWorkflowClient();

  // Build a workflow with the fluent builder.
  const workflow = new ConductorWorkflow(executor, "greetings")
    .add(simpleTask("greet_ref", "greet", { name: "${workflow.input.name}" }))
    .outputParameters({ result: "${greet_ref.output.result}" });

  await workflow.register();

  // Start polling for tasks (auto-discovers @worker decorated functions).
  const handler = new TaskHandler({
    client: clients.getClient(),
    scanForDecorated: true,
  });
  await handler.startWorkers();

  // Run the workflow and get the result.
  const run = await workflow.execute({ name: "Conductor" });
  console.log(`result: ${run.output?.result}`);

  await handler.stopWorkers();
}

main();
```

Run it:

```shell
export CONDUCTOR_SERVER_URL=http://localhost:8080
npx ts-node quickstart.ts
```

> ### Using Orkes Conductor / Remote Server?
> Export your authentication credentials:
>
> ```shell
> export CONDUCTOR_SERVER_URL="https://your-cluster.orkesconductor.io/api"
> export CONDUCTOR_AUTH_KEY="your-key"
> export CONDUCTOR_AUTH_SECRET="your-secret"
> ```

That's it — you defined a worker, built a workflow, and executed it. Open the Conductor UI (default: [http://localhost:8080](http://localhost:8080)) to see the execution.

## What You Can Build

The SDK provides typed builders for common orchestration patterns. Here's a taste of what you can wire together:

**HTTP calls from workflows** — call any API without writing a worker ([kitchensink.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/kitchensink.ts)):

```typescript
httpTask("call_api", {
  uri: "https://api.example.com/orders/${workflow.input.orderId}",
  method: "POST",
  body: { items: "${workflow.input.items}" },
  headers: { "Authorization": "Bearer ${workflow.input.token}" },
})
```

**Wait between tasks** — pause a workflow for a duration or until a timestamp ([kitchensink.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/kitchensink.ts)):

```typescript
.add(simpleTask("step1_ref", "process_order", {...}))
.add(waitTaskDuration("cool_down", "10s"))        // wait 10 seconds
.add(simpleTask("step2_ref", "send_confirmation", {...}))
```

**Parallel execution (fork/join)** — fan out to multiple branches and join ([fork-join.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/fork-join.ts)):

```typescript
workflow.fork([
  [simpleTask("email_ref", "send_email", {})],
  [simpleTask("sms_ref", "send_sms", {})],
  [simpleTask("push_ref", "send_push", {})],
])
```

**Conditional branching** — route based on input values ([kitchensink.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/kitchensink.ts)):

```typescript
switchTask("route_ref", "${workflow.input.tier}", {
  premium: [simpleTask("fast_ref", "fast_track", {})],
  standard: [simpleTask("normal_ref", "standard_process", {})],
})
```

**Sub-workflows** — compose workflows from smaller workflows ([sub-workflows.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/sub-workflows.ts)):

```typescript
const child = new ConductorWorkflow(executor, "payment_flow").add(...);
const parent = new ConductorWorkflow(executor, "order_flow")
  .add(child.toSubWorkflowTask("pay_ref"));
```

All of these are type-safe, composable, and registered to the server as JSON — workers can be in any language.

## Workers

Workers are TypeScript functions that execute Conductor tasks. Decorate any function with `@worker` to register it as a worker (auto-discovered by `TaskHandler`) and use it as a workflow task.

```typescript
import { worker, TaskHandler } from "@io-orkes/conductor-javascript";

@worker({ taskDefName: "greet", concurrency: 5, pollInterval: 100 })
async function greet(task: Task) {
  return {
    status: "COMPLETED",
    outputData: { result: `Hello ${task.inputData.name}` },
  };
}

@worker({ taskDefName: "process_payment", domain: "payments" })
async function processPayment(task: Task) {
  const result = await paymentGateway.charge(task.inputData.customerId, task.inputData.amount);
  return { status: "COMPLETED", outputData: { transactionId: result.id } };
}

// Auto-discover and start all decorated workers
const handler = new TaskHandler({ client, scanForDecorated: true });
await handler.startWorkers();

// Graceful shutdown
process.on("SIGTERM", async () => {
  await handler.stopWorkers();
  process.exit(0);
});
```

**Worker configuration:**

```typescript
@worker({
  taskDefName: "my_task",    // Required: task name
  concurrency: 5,             // Max concurrent tasks (default: 1)
  pollInterval: 100,          // Polling interval in ms (default: 100)
  domain: "production",       // Task domain for multi-tenancy
  workerId: "worker-123",     // Unique worker identifier
})
```

**Environment variable overrides** (no code changes needed):

```shell
# Global (all workers)
export CONDUCTOR_WORKER_ALL_POLL_INTERVAL=500
export CONDUCTOR_WORKER_ALL_CONCURRENCY=10

# Per-worker override
export CONDUCTOR_WORKER_SEND_EMAIL_CONCURRENCY=20
export CONDUCTOR_WORKER_PROCESS_PAYMENT_DOMAIN=payments
```

**NonRetryableException** — mark failures as terminal to prevent retries:

```typescript
import { NonRetryableException } from "@io-orkes/conductor-javascript";

@worker({ taskDefName: "validate_order" })
async function validateOrder(task: Task) {
  const order = await getOrder(task.inputData.orderId);
  if (!order) {
    throw new NonRetryableException("Order not found"); // FAILED_WITH_TERMINAL_ERROR
  }
  return { status: "COMPLETED", outputData: { validated: true } };
}
```

- `throw new Error()` → Task status: `FAILED` (will retry)
- `throw new NonRetryableException()` → Task status: `FAILED_WITH_TERMINAL_ERROR` (no retry)

**Long-running tasks with TaskContext** — return `IN_PROGRESS` to keep a task alive while an external process completes. Conductor will call back after the specified interval ([task-context.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/task-context.ts)):

```typescript
import { worker, getTaskContext } from "@io-orkes/conductor-javascript";

@worker({ taskDefName: "process_video" })
async function processVideo(task: Task) {
  const ctx = getTaskContext();
  ctx?.addLog("Starting video processing...");

  if (!isComplete(task.inputData)) {
    ctx?.setCallbackAfter(30); // check again in 30 seconds
    return { status: "IN_PROGRESS", callbackAfterSeconds: 30 };
  }

  return { status: "COMPLETED", outputData: { url: "..." } };
}
```

`TaskContext` is also available for one-shot workers — use `ctx?.addLog()` to stream logs visible in the Conductor UI.

**Event listeners** for observability:

```typescript
const handler = new TaskHandler({
  client,
  scanForDecorated: true,
  eventListeners: [{
    onTaskExecutionCompleted(event) {
      metrics.histogram("task_duration_ms", event.durationMs, { task_type: event.taskType });
    },
    onTaskUpdateFailure(event) {
      alertOps({ severity: "CRITICAL", message: `Task update failed`, taskId: event.taskId });
    },
  }],
});
```

**Organize workers across files** with module imports:

```typescript
const handler = await TaskHandler.create({
  client,
  importModules: ["./workers/orderWorkers", "./workers/paymentWorkers"],
});
await handler.startWorkers();
```

**Legacy TaskManager API** continues to work with full backward compatibility. New projects should use `@worker` + `TaskHandler` above.

## Monitoring Workers

Enable Prometheus metrics with the built-in `MetricsCollector`:

```typescript
import { MetricsCollector, MetricsServer, TaskHandler } from "@io-orkes/conductor-javascript";

const metrics = new MetricsCollector();
const server = new MetricsServer(metrics, 9090);
await server.start();

const handler = new TaskHandler({
  client,
  eventListeners: [metrics],
  scanForDecorated: true,
});
await handler.startWorkers();
// GET http://localhost:9090/metrics — Prometheus text format
// GET http://localhost:9090/health  — {"status":"UP"}
```

Collects 18 metric types: poll counts, execution durations, error rates, output sizes, and more — with p50/p75/p90/p95/p99 quantiles. See [METRICS.md](https://github.com/conductor-oss/javascript-sdk/blob/main/METRICS.md) for the full reference.

## Managing Workflow Executions

Once a workflow is registered (see [What You Can Build](#what-you-can-build)), you can run and manage it through the full lifecycle:

```typescript
const executor = clients.getWorkflowClient();

// Start (async — returns immediately)
const workflowId = await executor.startWorkflow({
  name: "order_flow",
  input: { orderId: "ORDER-123" },
});

// Execute (sync — waits for completion)
const result = await workflow.execute({ orderId: "123" });

// Lifecycle management
await executor.pause(workflowId);
await executor.resume(workflowId);
await executor.terminate(workflowId, "cancelled by user");
await executor.restart(workflowId);
await executor.retry(workflowId);

// Signal a running WAIT task
await executor.signal(workflowId, TaskResultStatusEnum.COMPLETED, { approved: true });

// Search workflows
const results = await executor.search("workflowType = 'order_flow' AND status = 'RUNNING'");
```

See [workflow-ops.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/workflow-ops.ts) for a runnable example covering all lifecycle operations.

## Troubleshooting

- **Worker stops polling or crashes:** `TaskHandler` monitors and restarts worker polling loops by default. Expose a health check using `handler.running` and `handler.runningWorkerCount`. If you enable metrics, alert on `worker_restart_total`.
- **HTTP/2 connection errors:** The SDK uses Undici for HTTP/2 when available. If your environment has unstable long-lived connections, the SDK falls back to HTTP/1.1 automatically. You can also provide a custom fetch function: `orkesConductorClient(config, myFetch)`.
- **Task stuck in SCHEDULED:** Ensure your worker is polling for the correct `taskDefName`. Workers must be started before the workflow is executed.

## Examples

See the [Examples Guide](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/README.md) for the full catalog. Key examples:

| Example | Description | Run |
|---------|-------------|-----|
| [workers-e2e.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/workers-e2e.ts) | End-to-end: 3 chained workers with verification | `npx ts-node examples/workers-e2e.ts` |
| [quickstart.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/quickstart.ts) | 60-second intro: @worker + workflow + execute | `npx ts-node examples/quickstart.ts` |
| [kitchensink.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/kitchensink.ts) | All major task types in one workflow | `npx ts-node examples/kitchensink.ts` |
| [workflow-ops.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/workflow-ops.ts) | Lifecycle: pause, resume, terminate, retry, search | `npx ts-node examples/workflow-ops.ts` |
| [test-workflows.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/test-workflows.ts) | Unit testing with mock outputs (no workers) | `npx ts-node examples/test-workflows.ts` |
| [metrics.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/metrics.ts) | Prometheus metrics + HTTP server on :9090 | `npx ts-node examples/metrics.ts` |
| [express-worker-service.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/express-worker-service.ts) | Express.js + workers in one process | `npx ts-node examples/express-worker-service.ts` |
| [function-calling.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/function-calling.ts) | LLM dynamically picks which worker to call | `npx ts-node examples/agentic-workflows/function-calling.ts` |
| [fork-join.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/fork-join.ts) | Parallel branches with join synchronization | `npx ts-node examples/advanced/fork-join.ts` |
| [sub-workflows.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/sub-workflows.ts) | Workflow composition with sub-workflows | `npx ts-node examples/advanced/sub-workflows.ts` |
| [human-tasks.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/human-tasks.ts) | Human-in-the-loop: claim, update, complete | `npx ts-node examples/advanced/human-tasks.ts` |

## API Journey Examples

End-to-end examples covering all APIs for each domain:

| Example | APIs | Run |
|---------|------|-----|
| [authorization.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/authorization.ts) | Authorization APIs (17 calls) | `npx ts-node examples/api-journeys/authorization.ts` |
| [metadata.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/metadata.ts) | Metadata APIs (21 calls) | `npx ts-node examples/api-journeys/metadata.ts` |
| [prompts.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/prompts.ts) | Prompt APIs (9 calls) | `npx ts-node examples/api-journeys/prompts.ts` |
| [schedules.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/schedules.ts) | Schedule APIs (13 calls) | `npx ts-node examples/api-journeys/schedules.ts` |
| [secrets.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/secrets.ts) | Secret APIs (12 calls) | `npx ts-node examples/api-journeys/secrets.ts` |
| [integrations.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/integrations.ts) | Integration APIs (22 calls) | `npx ts-node examples/api-journeys/integrations.ts` |
| [schemas.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/schemas.ts) | Schema APIs (10 calls) | `npx ts-node examples/api-journeys/schemas.ts` |
| [applications.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/applications.ts) | Application APIs (20 calls) | `npx ts-node examples/api-journeys/applications.ts` |
| [event-handlers.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/api-journeys/event-handlers.ts) | Event Handler APIs (18 calls) | `npx ts-node examples/api-journeys/event-handlers.ts` |

## AI & LLM Workflows

Conductor supports AI-native workflows including agentic tool calling, RAG pipelines, and multi-agent orchestration. The SDK provides typed builders for all LLM task types:

| Builder | Description |
|---------|-------------|
| `llmChatCompleteTask` | LLM chat completion (OpenAI, Anthropic, etc.) |
| `llmTextCompleteTask` | Text completion |
| `llmGenerateEmbeddingsTask` | Generate vector embeddings |
| `llmIndexDocumentTask` | Index a document into a vector store |
| `llmIndexTextTask` | Index text into a vector store |
| `llmSearchIndexTask` | Search a vector index |
| `llmSearchEmbeddingsTask` | Search by embedding similarity |
| `llmStoreEmbeddingsTask` | Store pre-computed embeddings |
| `llmQueryEmbeddingsTask` | Query embeddings |
| `generateImageTask` | Generate images |
| `generateAudioTask` | Generate audio |
| `callMcpToolTask` | Call an MCP tool |
| `listMcpToolsTask` | List available MCP tools |

**Example: LLM chat workflow**

```typescript
import { ConductorWorkflow, llmChatCompleteTask, Role } from "@io-orkes/conductor-javascript";

const workflow = new ConductorWorkflow(executor, "ai_chat")
  .add(llmChatCompleteTask("chat_ref", "openai", "gpt-4o", {
    messages: [{ role: Role.USER, message: "${workflow.input.question}" }],
    temperature: 0.7,
    maxTokens: 500,
  }))
  .outputParameters({ answer: "${chat_ref.output.result}" });

await workflow.register();
const run = await workflow.execute({ question: "What is Conductor?" });
console.log(run.output?.answer);
```

**Agentic Workflows**

Build AI agents where LLMs dynamically select and call TypeScript workers as tools.
See [examples/agentic-workflows/](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/) for all examples.

| Example | Description |
|---------|-------------|
| [llm-chat.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/llm-chat.ts) | Automated multi-turn conversation between two LLMs |
| [llm-chat-human-in-loop.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/llm-chat-human-in-loop.ts) | Interactive chat with WAIT tasks for human input |
| [function-calling.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/function-calling.ts) | LLM dynamically picks which worker function to call |
| [mcp-weather-agent.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/mcp-weather-agent.ts) | MCP tool discovery and invocation for real-time data |
| [multiagent-chat.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agentic-workflows/multiagent-chat.ts) | Multi-agent debate: optimist vs skeptic with moderator |

**RAG and Vector DB Workflows**

| Example | Description |
|---------|-------------|
| [rag-workflow.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/rag-workflow.ts) | End-to-end RAG: document indexing → semantic search → LLM answer |
| [vector-db.ts](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/advanced/vector-db.ts) | Vector DB operations: embedding generation, storage, search |

## Documentation

| Document | Description |
|----------|-------------|
| [SDK Development Guide](https://github.com/conductor-oss/javascript-sdk/blob/main/SDK_DEVELOPMENT.md) | Architecture, patterns, pitfalls, testing |
| [Metrics Reference](https://github.com/conductor-oss/javascript-sdk/blob/main/METRICS.md) | All 18 Prometheus metrics with descriptions |
| [Breaking Changes](https://github.com/conductor-oss/javascript-sdk/blob/main/BREAKING_CHANGES.md) | v3.x migration guide |
| [Workflow Management](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/workflow-executor.md) | Start, pause, resume, terminate, retry, search, signal |
| [Task Management](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/task-client.md) | Task operations, logs, queue management |
| [Metadata](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/metadata-client.md) | Task & workflow definitions, tags, rate limits |
| [Scheduling](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/scheduler-client.md) | Workflow scheduling with CRON expressions |
| [Authorization](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/authorization-client.md) | Users, groups, permissions |
| [Applications](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/application-client.md) | Application management, access keys, roles |
| [Events](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/event-client.md) | Event handlers, event-driven workflows |
| [Human Tasks](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/human-executor.md) | Human-in-the-loop workflows, form templates |
| [Service Registry](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/service-registry-client.md) | Service discovery, circuit breakers |
| [Secrets](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/secret-client.md) | Secret storage and management |
| [Prompts](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/prompt-client.md) | AI/LLM prompt templates |
| [Integrations](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/integration-client.md) | AI/LLM provider integrations |
| [Schemas](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/api-reference/schema-client.md) | JSON/Avro/Protobuf schema management |

## Support

- [Open an issue (SDK)](https://github.com/conductor-oss/javascript-sdk/issues) for SDK bugs, questions, and feature requests
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

**What Node.js versions are supported?**

Node.js 18 and above.

**Should I use `@worker` decorator or the legacy `TaskManager`?**

Use `@worker` + `TaskHandler` for all new projects. It provides auto-discovery, cleaner code, and better TypeScript integration. The legacy `TaskManager` API is maintained for backward compatibility.

**Can I mix workers written in different languages?**

Yes. A single workflow can have workers written in TypeScript, Python, Java, Go, or any other supported language. Workers communicate through the Conductor server, not directly with each other.

**How do I run workers in production?**

Workers are standard Node.js processes. Deploy them as you would any Node.js application — in containers, VMs, or serverless. Workers poll the Conductor server for tasks, so no inbound ports need to be opened.

**How do I test workflows without running a full Conductor server?**

The SDK provides `testWorkflow()` on `WorkflowExecutor` that uses Conductor's `POST /api/workflow/test` endpoint to evaluate workflows with mock task outputs.

**Does the SDK support HTTP/2?**

Yes. When the optional `undici` package is installed (`npm install undici`), the SDK automatically uses HTTP/2 with connection pooling for better performance.

## License

Apache 2.0


## Examples

Browse all examples on GitHub: [conductor-oss/javascript-sdk/examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples)

| Example | Type |
|---|---|
| [Readme](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/README.md) | file |
| [Advanced](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/advanced) | directory |
| [Agentic Workflows](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agentic-workflows) | directory |
| [Api Journeys](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/api-journeys) | directory |
| [Dynamic Workflow](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/dynamic-workflow.ts) | file |
| [Event Listeners](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/event-listeners.ts) | file |
| [Express Worker Service](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/express-worker-service.ts) | file |
| [Helloworld](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/helloworld.ts) | file |
| [Kitchensink](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/kitchensink.ts) | file |
| [Metrics](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/metrics.ts) | file |
| [Perf Test](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/perf-test.ts) | file |
| [Quickstart](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/quickstart.ts) | file |
| [Task Configure](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/task-configure.ts) | file |
| [Task Context](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/task-context.ts) | file |
| [Test Workflows](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/test-workflows.ts) | file |
| [Worker Configuration](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/worker-configuration.ts) | file |
| [Workers E2E](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/workers-e2e.ts) | file |
| [Workflow Ops](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/workflow-ops.ts) | file |
