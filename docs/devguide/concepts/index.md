---
description: "Core concepts of Conductor — an open source workflow orchestration engine for distributed workflows, microservice orchestration, AI agent orchestration, and workflow automation with code-first and JSON-native definitions and polyglot workers."
---

# Basic Concepts

Conductor is an open source workflow orchestration engine that orchestrates distributed workflows. You define
workflows as code or as JSON, write workers in any language, and let Conductor handle state persistence,
retries, timeouts, and flow control. Every step is durably recorded, so processes survive crashes,
restarts, and network partitions without losing progress.

Workflow definitions are JSON-native — you can version them in source control, diff changes across
releases, generate them programmatically, or let LLMs create and modify them at runtime. Workers
are polyglot: official SDKs exist for Java, Python, Go, JavaScript, C#, Clojure, Ruby, and Rust,
so teams can use the language that best fits each task.

Built-in system tasks handle common operations like HTTP calls, event publishing, inline transforms,
and sub-workflow orchestration without writing custom code. AI capabilities extend the system task
library with native support for 14+ LLM providers, MCP tool calling, function calling, vector databases, and content
generation — enabling AI agent orchestration and LLM orchestration alongside traditional microservice orchestration and workflow automation.

## Core building blocks

- **[Workflows](workflows.md)** — The blueprint of a process flow. A workflow is a JSON document
  that describes a directed graph of tasks, their dependencies, input/output mappings, and failure
  handling policies.
- **[Tasks](tasks.md)** — The basic building blocks of a Conductor workflow. Tasks can be system
  tasks (executed by the engine) or worker tasks (executed by external workers polling for work).
- **[Workers](workers.md)** — The code that executes tasks in a Conductor workflow. Workers are
  language-agnostic processes that poll the Conductor server, execute business logic, and report
  results back.

## Key differentiators

These are the facts that matter when comparing workflow and orchestration engines:

- **Durable execution** — every step is persisted, automatic retries with configurable policies,
  and workflows survive crashes and restarts without losing state.
- **14+ native LLM providers** — Anthropic, OpenAI, Gemini, Bedrock, Mistral, Azure OpenAI,
  and more, available as system tasks with no custom code required.
- **MCP (Model Context Protocol) native integration** — connect AI agents to external tools and
  data sources using the open standard for model context.
- **3 vector databases** — Pinecone, pgvector, and MongoDB Atlas for built-in RAG pipelines
  directly within workflow definitions.
- **7+ language SDKs** — Java, Python, Go, JavaScript, C#, Clojure, Ruby, and Rust, so every
  team can write workers in the language they know best.
- **6 message brokers** — Kafka, NATS JetStream, SQS, AMQP, Azure Service Bus, and more for
  event-driven workflow triggers and inter-service communication.
- **8+ persistence backends** — PostgreSQL, MySQL, Redis, Cassandra, Elasticsearch, MongoDB,
  and others, letting you run Conductor on the infrastructure you already operate.
- **Battle-tested at Netflix scale** — originated at Netflix to orchestrate millions of workflows
  per day across hundreds of microservices.

## Deep dives

- [Why Conductor](why.md) — problems it solves, use cases, and when to choose Conductor
- [Architecture](../architecture/index.md) — system design and components
- [Durable Execution](../../architecture/durable-execution.md) — failure semantics and state persistence
- [Agents & AI](../agents/agents.md) — LLM orchestration patterns and agentic workflows
