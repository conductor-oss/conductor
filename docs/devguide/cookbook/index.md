---
description: "Conductor cookbook — copy-paste workflow orchestration recipes for microservice orchestration, dynamic parallelism, event-driven patterns, AI agent orchestration, LLM orchestration, workflow automation, and RAG pipelines."
---

# Cookbook

Production-ready workflow recipes. Each recipe includes the complete JSON workflow definition and commands to register and run it.

<div class="grid cards" markdown>

-   **[Microservice orchestration](microservice-orchestration.md)**

    HTTP service chains, conditional branching, parallel HTTP calls with Fork/Join.

-   **[Dynamic parallelism](dynamic-parallelism.md)**

    Dynamic forks — different tasks per branch, fan-out with same task, parallel sub-workflows.

-   **[Wait and timer patterns](wait-and-timers.md)**

    Fixed delays, scheduled execution, external signals, and human-in-the-loop approvals.

-   **[Task timeouts and retries](task-timeouts-and-retries.md)**

    Exponential backoff with cap and jitter, lease extension for long-running workers, hard SLA with totalTimeoutSeconds, and thundering herd prevention.

-   **[Event-driven recipes](event-driven.md)**

    Publish to Kafka/NATS/RabbitMQ/SQS, event handlers to trigger workflows, complete tasks from events.

-   **[AI & LLM orchestration recipes](ai-llm.md)**

    Chat completion, RAG pipelines, MCP agents with function calling, image generation, LLM-to-PDF, and provider configuration.

-   **[Dynamic workflows as code](dynamic-workflows.md)**

    Workflow as code in Python — sequential chains, conditional branching, parallel execution, loops, sub-workflows, and runtime-generated definitions.

</div>
