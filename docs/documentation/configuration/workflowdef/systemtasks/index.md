---
description: "Overview of built-in system tasks in Conductor — HTTP, Event, Human, Wait, Inline, Kafka Publish, JSON JQ Transform, LLM orchestration, MCP function calling, and more for durable workflow orchestration."
---

# System Tasks

System tasks are built-in tasks that run on the Conductor server. They execute without external workers, allowing you to build workflows using common operations out of the box.

## Available system tasks

| System Task | Type | Description |
| :--- | :--- | :--- |
| [HTTP](http-task.md) | `HTTP` | Call any HTTP/REST endpoint. Supports GET, POST, PUT, DELETE with headers, body, and connection/read timeouts. |
| [Inline](inline-task.md) | `INLINE` | Execute lightweight JavaScript or GraalVM Python expressions server-side. Useful for data transformation, validation, and simple logic. |
| [Event](event-task.md) | `EVENT` | Publish events to external systems — Kafka, NATS, NATS Streaming, AMQP (RabbitMQ), SQS, or Conductor's internal queue. |
| [Wait](wait-task.md) | `WAIT` | Pause workflow execution until a specified time, duration, or external signal. |
| [Human](human-task.md) | `HUMAN` | Wait for an external signal, typically a human approval or manual action. The task stays `IN_PROGRESS` until completed via API. |
| [Kafka Publish](kafka-publish-task.md) | `KAFKA_PUBLISH` | Publish messages directly to a Kafka topic with configurable serializers and headers. |
| [JSON JQ Transform](json-jq-transform-task.md) | `JSON_JQ_TRANSFORM` | Transform JSON data using [jq](https://jqlang.org/) expressions. Powerful for reshaping, filtering, and aggregating data. |
| [No Op](noop-task.md) | `NOOP` | Do nothing. Useful as a placeholder or to merge branches in fork/join patterns. |
| [JDBC](jdbc-task.md) | `JDBC` | Execute SQL queries and updates against relational databases (MySQL, PostgreSQL, Oracle, etc.) with connection pooling and transaction management. |
| [Pull Workflow Messages](pull-workflow-messages-task.md) | `PULL_WORKFLOW_MESSAGES` | Pull a batch from workflow-message queues; requires `conductor.workflow-message-queue.enabled=true`. |

## Operators (flow control)

These are also system tasks but control workflow execution flow rather than performing work:

| Operator | Type | Description |
| :--- | :--- | :--- |
| [Fork/Join](../operators/fork-task.md) | `FORK_JOIN` | Execute tasks in parallel branches, then join. |
| [Dynamic Fork](../operators/dynamic-fork-task.md) | `FORK_JOIN_DYNAMIC` | Dynamically create parallel branches at runtime. |
| [Join](../operators/join-task.md) | `JOIN` | Wait for parallel branches to complete. |
| [Exclusive Join](../operators/exclusive-join-task.md) | `EXCLUSIVE_JOIN` | Continue when the first selected branch completes. |
| [Switch](../operators/switch-task.md) | `SWITCH` | Conditional branching based on expressions or values. |
| [Do While](../operators/do-while-task.md) | `DO_WHILE` | Loop over tasks until a condition is met. |
| [Sub Workflow](../operators/sub-workflow-task.md) | `SUB_WORKFLOW` | Execute another workflow as a task. |
| [Start Workflow](../operators/start-workflow-task.md) | `START_WORKFLOW` | Start another workflow asynchronously (fire-and-forget). |
| [Set Variable](../operators/set-variable-task.md) | `SET_VARIABLE` | Set or update workflow-level variables. |
| [Terminate](../operators/terminate-task.md) | `TERMINATE` | Terminate the workflow with a specified status. |
| [Dynamic](../operators/dynamic-task.md) | `DYNAMIC` | Determine the task type to execute at runtime. |

## AI tasks

[AI Tasks](ai-tasks.md) is the complete catalog for LLM, vector/embedding, media/PDF, MCP, and A2A task families. They require `conductor.integrations.ai.enabled=true` and any provider-specific setup.

## Deprecated

| Task | Replacement |
| :--- | :--- |
| Lambda | Use [Inline](inline-task.md) instead. |
| Decision | Use [Switch](../operators/switch-task.md) instead. |
