---
description: "Why use Conductor? Durable execution, polyglot workers, built-in retries, AI agent orchestration, and event-driven workflows — all open source."
---

# Why Conductor

Conductor is an open-source durable execution engine and agentic workflow engine. It orchestrates distributed workflows across services, languages, and infrastructure — tracking every state transition, retrying failures automatically, and giving you full visibility into what happened and why.

## The problem

Distributed systems fail. Services crash, networks drop, deployments roll mid-flight. Without orchestration, you end up writing retry logic, state tracking, timeout handling, and compensation flows into every service. That logic is scattered, inconsistent, and invisible.

**Choreography** (peer-to-peer events) makes this worse at scale:

- Business processes are implicit — embedded across dozens of services with no single view of the flow.
- Tight coupling through assumed message contracts makes changes risky.
- "How far along is order #12345?" requires querying every service in the chain.
- Debugging a failure means correlating logs across services, queues, and time.

**Orchestration** centralizes the flow definition while keeping execution distributed. Conductor is the orchestrator — your workers stay stateless and independent.

## What Conductor gives you

### Durable execution
Every workflow execution is persisted. If a task fails, Conductor retries it with configurable backoff. If a worker crashes, the task is rescheduled. If the server restarts, execution resumes exactly where it left off. Your code doesn't need to handle any of this.

### Language-agnostic workers
Write workers in Python, Java, Go, JavaScript, C#, or Clojure. Each task in a workflow can use a different language — pick the best tool for each job. Workers communicate with Conductor via REST or gRPC and can run anywhere: containers, VMs, serverless, or your laptop.

### Built-in system tasks
HTTP calls, inline JavaScript execution, JSON transforms, event publishing, wait timers, and human approval gates — all available without writing a single worker. See [System Tasks](../../documentation/configuration/workflowdef/systemtasks/index.md).

### Flow control operators
Fork/join for parallelism, switch for conditional branching, do-while for loops, sub-workflows for composition, and dynamic tasks resolved at runtime. See [Operators](../../documentation/configuration/workflowdef/operators/index.md).

### AI and agent orchestration
Conductor provides LLM orchestration as native system tasks — no external frameworks required. Supported providers include Anthropic (Claude), OpenAI (GPT), Azure OpenAI, Google Gemini, AWS Bedrock, Mistral, Cohere, HuggingFace, Ollama, Perplexity, Grok, and StabilityAI — 14+ providers available out of the box for chat completion, text completion, and embedding generation.

MCP (Model Context Protocol) integration is built in: use `LIST_MCP_TOOLS` to discover available tools and `CALL_MCP_TOOL` to invoke them, all within a workflow with full retry and state tracking.

For RAG pipelines, Conductor supports three vector databases natively — Pinecone, pgvector, and MongoDB Atlas — so you can index embeddings, run similarity search, and feed results to an LLM in a single workflow definition.

Content generation tasks cover image, audio, video, and PDF creation using AI models. Every AI task runs with the same durability guarantees as any other Conductor task: automatic retries, timeout handling, and a complete audit trail.

### Event-driven workflows
Publish to and consume from Kafka, NATS, AMQP (RabbitMQ), and SQS. Trigger workflows from external events or emit events from within workflows. See [Event Bus Orchestration](../how-tos/event-bus.md).

### Full operational control
Pause, resume, restart, retry, and terminate any workflow execution. Search and filter executions by status, time, correlation ID, or custom tags. Every task has a complete audit trail — inputs, outputs, timestamps, retry history, and worker identity.

### Horizontal scalability
Conductor scales to millions of concurrent workflow executions. Workers scale independently — add more instances and Conductor distributes tasks automatically. Rate limits and concurrency caps prevent overload.

## When to use Conductor

| Use case | Example |
| :--- | :--- |
| **Microservice orchestration** | Order processing: payment → inventory → shipping → notification |
| **Data pipelines** | ETL flows with conditional branching and parallel processing |
| **AI agent workflows** | Multi-step LLM chains with tool calling, RAG, and human-in-the-loop |
| **Long-running processes** | Insurance claims, loan approvals, onboarding flows spanning days or weeks |
| **Event-driven automation** | React to Kafka events, trigger workflows, publish results back |
| **Batch processing** | Fan-out work across thousands of parallel workers with dynamic fork |
| **Saga pattern** | Distributed transactions with compensation on failure |
| **RAG applications** | Build retrieval-augmented generation pipelines with vector search, embedding generation, and LLM completion as workflow tasks |
| **Content generation pipelines** | Generate images, audio, video, and PDFs using AI models orchestrated as durable workflows |

## What sets Conductor apart

No other durable execution engine matches this combination:

- **14+ native LLM providers as system tasks** — Anthropic, OpenAI, Azure OpenAI, Gemini, Bedrock, Mistral, Cohere, HuggingFace, Ollama, Perplexity, Grok, StabilityAI, and more. No wrappers, no plugins — first-class support.
- **MCP (Model Context Protocol) native integration** — discover and call tools directly from workflow definitions.
- **3 vector databases for built-in RAG** — Pinecone, pgvector, MongoDB Atlas. Embed, index, search, and generate in one workflow.
- **Content generation tasks** — image, audio, video, and PDF generation as system tasks.
- **6 message brokers** — Kafka, NATS, NATS Streaming, SQS, AMQP (RabbitMQ), and internal queuing.
- **8+ persistence backends** — Redis, PostgreSQL, MySQL, Cassandra, SQLite, Elasticsearch, OpenSearch, and more.
- **7+ language SDKs** — Java, Python, Go, JavaScript, C#, Clojure, Ruby, and Rust.
- **Battle-tested at scale** — proven in production at Netflix, Tesla, LinkedIn, and JP Morgan.
- **JSON-native workflow definitions** — workflows are data, not code. LLMs can generate, modify, and reason about them directly.
- **Human-in-the-loop as a first-class task type** — pause execution for approvals, reviews, or manual intervention with built-in timeout and escalation.

## How it works

```mermaid
graph TD
    subgraph Workers
        A["Worker A<br/>(Python)"]
        B["Worker B<br/>(Java)"]
        C["Worker C<br/>(Go)"]
        D["Worker D<br/>(C#)"]
    end

    subgraph Server["Conductor Server"]
        S["Scheduling · State · Retries<br/>Persistence · Queuing"]
    end

    subgraph Storage["Persistence"]
        DB["Redis / PostgreSQL / MySQL / Cassandra"]
    end

    A -- "poll / complete" --> S
    B -- "poll / complete" --> S
    C -- "poll / complete" --> S
    D -- "poll / complete" --> S
    S --> DB
```

Workers poll for tasks, execute business logic, and report results. Conductor handles everything else — scheduling, retries, timeouts, state persistence, and flow control. See [Architecture](../architecture/index.md) for details.

## Next steps

- [Quickstart](../../quickstart/index.md) — run your first workflow in 2 minutes
- [Workflows](workflows.md) — how workflow definitions work
- [Tasks](tasks.md) — task types and configuration
- [Workers](workers.md) — building workers in any language
