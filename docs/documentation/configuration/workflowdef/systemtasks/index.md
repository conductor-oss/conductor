---
description: "Overview of built-in system tasks in Conductor — HTTP, Event, Human, Wait, Inline, Kafka Publish, JSON JQ Transform, LLM orchestration, MCP function calling, and more for durable workflow orchestration."
---

# System Tasks

System tasks are built-in tasks that run on the Conductor server. They execute without external workers, allowing you to build workflows using common operations out of the box.

## Available system tasks

| System Task | Type | Description |
| :--- | :--- | :--- |
| [HTTP](http-task.md) | `HTTP` | Call any HTTP/REST endpoint. Supports GET, POST, PUT, DELETE with headers, body, and connection/read timeouts. |
| [Inline](inline-task.md) | `INLINE` | Execute lightweight JavaScript or Python expressions server-side using GraalJS. Useful for data transformation, validation, and simple logic. |
| [Event](event-task.md) | `EVENT` | Publish events to external systems — Kafka, NATS, NATS Streaming, AMQP (RabbitMQ), SQS, or Conductor's internal queue. |
| [Wait](wait-task.md) | `WAIT` | Pause workflow execution until a specified time, duration, or external signal. |
| [Human](human-task.md) | `HUMAN` | Wait for an external signal, typically a human approval or manual action. The task stays `IN_PROGRESS` until completed via API. |
| [Kafka Publish](kafka-publish-task.md) | `KAFKA_PUBLISH` | Publish messages directly to a Kafka topic with configurable serializers and headers. |
| [JSON JQ Transform](json-jq-transform-task.md) | `JSON_JQ_TRANSFORM` | Transform JSON data using [jq](https://jqlang.org/) expressions. Powerful for reshaping, filtering, and aggregating data. |
| [No Op](noop-task.md) | `NOOP` | Do nothing. Useful as a placeholder or to merge branches in fork/join patterns. |
| [JDBC](jdbc-task.md) | `JDBC` | Execute SQL queries and updates against relational databases (MySQL, PostgreSQL, Oracle, etc.) with connection pooling and transaction management. |

## Operators (flow control)

These are also system tasks but control workflow execution flow rather than performing work:

| Operator | Type | Description |
| :--- | :--- | :--- |
| [Fork/Join](../operators/fork-task.md) | `FORK_JOIN` | Execute tasks in parallel branches, then join. |
| [Dynamic Fork](../operators/dynamic-fork-task.md) | `FORK_JOIN_DYNAMIC` | Dynamically create parallel branches at runtime. |
| [Join](../operators/join-task.md) | `JOIN` | Wait for parallel branches to complete. |
| [Switch](../operators/switch-task.md) | `SWITCH` | Conditional branching based on expressions or values. |
| [Do While](../operators/do-while-task.md) | `DO_WHILE` | Loop over tasks until a condition is met. |
| [Sub Workflow](../operators/sub-workflow-task.md) | `SUB_WORKFLOW` | Execute another workflow as a task. |
| [Start Workflow](../operators/start-workflow-task.md) | `START_WORKFLOW` | Start another workflow asynchronously (fire-and-forget). |
| [Set Variable](../operators/set-variable-task.md) | `SET_VARIABLE` | Set or update workflow-level variables. |
| [Terminate](../operators/terminate-task.md) | `TERMINATE` | Terminate the workflow with a specified status. |
| [Dynamic](../operators/dynamic-task.md) | `DYNAMIC` | Determine the task type to execute at runtime. |

## AI & LLM tasks

Conductor is the only open-source workflow engine with native AI system tasks. These tasks require the `ai` module to be enabled and provide direct integration with 14+ LLM providers, 3 vector databases, and MCP servers — no external frameworks or custom workers needed.

### LLM

| Task | Type | Description |
| :--- | :--- | :--- |
| Chat Completion | `LLM_CHAT_COMPLETE` | Multi-turn conversational AI with optional tool calling. Supports all major LLM providers. |
| Text Completion | `LLM_TEXT_COMPLETE` | Single prompt completion. |

**Supported providers:** Anthropic (Claude), OpenAI (GPT), Azure OpenAI, Google Gemini, AWS Bedrock, Mistral, Cohere, HuggingFace, Ollama, Perplexity, Grok (xAI), StabilityAI, and more. Switch providers by changing a configuration parameter — no code changes required.

### Embeddings & Vector Search

| Task | Type | Description |
| :--- | :--- | :--- |
| Generate Embeddings | `LLM_GENERATE_EMBEDDINGS` | Convert text to vector embeddings. |
| Store Embeddings | `LLM_STORE_EMBEDDINGS` | Store pre-computed embeddings in a vector database. |
| Index Text | `LLM_INDEX_TEXT` | Store text with auto-generated embeddings in a vector database. |
| Search Index | `LLM_SEARCH_INDEX` | Semantic search using a text query. |
| Search Embeddings | `LLM_SEARCH_EMBEDDINGS` | Search using embedding vectors directly. |

**Supported vector databases:** Pinecone, pgvector (PostgreSQL), and MongoDB Atlas Vector Search. These enable RAG (retrieval-augmented generation) pipelines as standard Conductor workflows.

### Content Generation

| Task | Type | Description |
| :--- | :--- | :--- |
| Generate Image | `GENERATE_IMAGE` | Generate images from text prompts. |
| Generate Audio | `GENERATE_AUDIO` | Text-to-speech synthesis. |
| Generate Video | `GENERATE_VIDEO` | Generate videos from text or image prompts (async). |
| Generate PDF | `GENERATE_PDF` | Convert markdown to PDF documents. |

### MCP (Model Context Protocol)

| Task | Type | Description |
| :--- | :--- | :--- |
| List MCP Tools | `LIST_MCP_TOOLS` | List available tools from an MCP server. |
| Call MCP Tool | `CALL_MCP_TOOL` | Execute a tool on an MCP server. |

MCP integration enables Conductor workflows to discover and use tools from any MCP-compatible server, and to expose Conductor workflows as MCP tools for use by LLMs and AI agents.

## Deprecated

| Task | Replacement |
| :--- | :--- |
| Lambda | Use [Inline](inline-task.md) instead. |
| Decision | Use [Switch](../operators/switch-task.md) instead. |
