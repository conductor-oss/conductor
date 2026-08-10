---
description: "Learn about tasks in Conductor — the reusable building blocks of workflows, including system tasks, worker tasks, operators, LLM tasks with 14+ AI providers, and MCP tool calling."
---

# Tasks

<section class="concept-hero concept-hero--tasks">
  <svg class="concept-hero__graphic" viewBox="0 20 440 150" role="img" aria-label="A workflow routes work to a system task or worker task and receives a recorded output">
    <defs><marker id="task-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="currentColor" /></marker></defs>
    <rect x="14" y="68" width="105" height="54" rx="10" class="concept-hero__node" />
    <text x="66" y="91" text-anchor="middle" class="concept-hero__label">Workflow</text>
    <text x="66" y="108" text-anchor="middle" class="concept-hero__detail">input</text>
    <path d="M119 95 H163 V54 H191" class="concept-hero__line" marker-end="url(#task-arrow)" />
    <path d="M163 95 V136 H191" class="concept-hero__line" marker-end="url(#task-arrow)" />
    <rect x="199" y="28" width="122" height="52" rx="10" class="concept-hero__node concept-hero__node--accent" />
    <text x="260" y="51" text-anchor="middle" class="concept-hero__label">System task</text>
    <text x="260" y="68" text-anchor="middle" class="concept-hero__detail">HTTP · WAIT · LLM</text>
    <rect x="199" y="110" width="122" height="52" rx="10" class="concept-hero__node" />
    <text x="260" y="133" text-anchor="middle" class="concept-hero__label">Worker task</text>
    <text x="260" y="150" text-anchor="middle" class="concept-hero__detail">your code</text>
    <path d="M321 54 H350 V95 H360" class="concept-hero__line" marker-end="url(#task-arrow)" />
    <path d="M321 136 H350 V95" class="concept-hero__line" />
    <rect x="366" y="68" width="66" height="54" rx="10" class="concept-hero__outcome-box" />
    <text x="399" y="100" text-anchor="middle" class="concept-hero__label">Output</text>
  </svg>
</section>

A task is the basic building block of a Conductor workflow. They are reusable and modular, representing steps in your application like processing data files, calling an AI model, or executing some logic.

In Conductor, tasks can be defined, configured, and then executed. Learn more about the distinct but related concepts, **task definition**, **task configuration**, and **task execution** below.


## Types of tasks

Tasks are categorized into three types, enabling you to flexibly build workflows using pre-built tasks, custom logic, or a combination of both:

### System tasks

Conductor ships with 20+ [system tasks](../../documentation/configuration/workflowdef/systemtasks/index.md) — built-in, general-purpose tasks designed for common uses like calling an HTTP endpoint, publishing events, or running AI inference.

System tasks are managed by Conductor and executed within its server's JVM, allowing you to get started without having to write custom workers.

| Category | Tasks |
|---|---|
| **Core** | HTTP, Inline (script), Event, Wait, Human, Kafka Publish, JSON JQ Transform, No Op |
| **Flow Control** | Fork/Join, Dynamic Fork, Join, Switch, Do While, Sub Workflow, Start Workflow, Set Variable, Terminate, Dynamic |
| **AI / LLM** | Chat Completion, Text Completion, Embeddings, Vector Search, Content Generation, MCP Tool Calling |

## Commonly used system tasks

| Task | Type | Use it for |
|---|---|---|
| [HTTP](../../documentation/configuration/workflowdef/systemtasks/http-task.md) | `HTTP` | Calling HTTP or REST endpoints. |
| [Event](../../documentation/configuration/workflowdef/systemtasks/event-task.md) | `EVENT` | Publishing to an event sink or messaging system. |
| Chat Completion | `LLM_CHAT_COMPLETE` | Conversational AI and optional model tool calling. |
| [Wait](../../documentation/configuration/workflowdef/systemtasks/wait-task.md) | `WAIT` | Pausing until a time, duration, or external signal. |
| [JSON JQ Transform](../../documentation/configuration/workflowdef/systemtasks/json-jq-transform-task.md) | `JSON_JQ_TRANSFORM` | Reshaping, filtering, or aggregating JSON data. |
| [Inline](../../documentation/configuration/workflowdef/systemtasks/inline-task.md) | `INLINE` | Small server-side GraalJS expressions for validation or simple logic. |

See the [complete System Tasks reference](../../documentation/configuration/workflowdef/systemtasks/index.md) for every built-in task and its configuration.

### Worker tasks

Worker tasks (`SIMPLE`) can be used to implement custom logic outside the scope of Conductor's system tasks. Also known as Simple tasks, Worker tasks are implemented by your task workers that run in a separate environment from Conductor.

A minimal worker task configuration and its corresponding Python worker:

```json
{
  "name": "process_payment",
  "taskReferenceName": "process_payment_ref",
  "type": "SIMPLE",
  "inputParameters": {
    "orderId": "${workflow.input.orderId}",
    "amount": "${workflow.input.amount}"
  }
}
```

```python
@worker_task(task_definition_name="process_payment")
def process_payment(orderId: str, amount: float) -> dict:
    result = payment_gateway.charge(orderId, amount)
    return {"transactionId": result.id, "status": result.status}
```

### Operators
[Operators](../../documentation/configuration/workflowdef/operators/index.md) are built-in control flow primitives similar to programming language constructs like loops, switch cases, or fork/joins. Like system tasks, operators are also managed by Conductor.

| Operator | Purpose |
|---|---|
| [Do While](../../documentation/configuration/workflowdef/operators/do-while-task.md) | Do-while loops / For loops |
| [Dynamic](../../documentation/configuration/workflowdef/operators/dynamic-task.md) | Function pointer |
| [Dynamic Fork](../../documentation/configuration/workflowdef/operators/dynamic-fork-task.md) | Dynamic parallel execution |
| [Fork](../../documentation/configuration/workflowdef/operators/fork-task.md) | Static parallel execution |
| [Join](../../documentation/configuration/workflowdef/operators/join-task.md) | Map |
| [Set Variable](../../documentation/configuration/workflowdef/operators/set-variable-task.md) | Workflow variable declaration |
| [Start Workflow](../../documentation/configuration/workflowdef/operators/start-workflow-task.md) | Entry point |
| [Sub Workflow](../../documentation/configuration/workflowdef/operators/sub-workflow-task.md) | Subroutine |
| [Switch](../../documentation/configuration/workflowdef/operators/switch-task.md) | Switch / If..then...else selection |
| [Terminate](../../documentation/configuration/workflowdef/operators/terminate-task.md) | Exit |

For full configuration and examples, see the [Operators reference](../../documentation/configuration/workflowdef/operators/index.md).


## Task definition

[Task definitions](../../documentation/configuration/taskdef.md) are used to define a task's default parameters, like inputs and output keys, timeouts, and retries. This provides reusability across workflows, as the registered task definition will be referenced when a task is configured in a workflow definition.

```json
{
  "name": "process_payment",
  "retryCount": 3,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 5,
  "maxRetryDelaySeconds": 60,
  "backoffJitterMs": 2000,
  "totalTimeoutSeconds": 300,
  "timeoutSeconds": 120,
  "responseTimeoutSeconds": 60,
  "pollTimeoutSeconds": 30
}
```

- **retryCount / retryLogic / retryDelaySeconds** — How many times to retry a failed task, the backoff strategy, and the initial delay between retries.
- **maxRetryDelaySeconds** — Caps the computed backoff delay. Prevents exponential growth from becoming arbitrarily large.
- **backoffJitterMs** — Adds random milliseconds to each retry delay to spread concurrent retries over time (thundering herd prevention).
- **totalTimeoutSeconds** — Hard wall-clock budget across all retry attempts combined. Once exceeded, no further retries are attempted regardless of `retryCount`.
- **timeoutSeconds** — Maximum wall-clock time per individual attempt before the task is marked `TIMED_OUT`.
- **responseTimeoutSeconds** — Maximum time to wait for a worker to respond after picking up a task. Useful for detecting unresponsive workers.
- **pollTimeoutSeconds** — Maximum time a worker can hold a long-poll connection before the server releases it.

When using Worker tasks (`SIMPLE`), its task definition must be registered to the Conductor server before it can execute in a workflow. Because system tasks are managed by Conductor, it is not necessary to add a task definition for system tasks unless you wish to customize its default parameters.


## Task configuration

Stored in the `tasks` array of a [workflow definition](workflows.md#workflow-definition), task configurations make up the workflow-specific blueprint that describes:

- The order and control flow of tasks.
- How data is passed from one task to another through task inputs and outputs.
- Other workflow-specific behavior, like optionality, caching, and schema enforcement.

The specific configuration for each task differs depending on the task type. For system tasks and operators, the task configuration will contain important parameters that control the behavior of the task. For example, the task configuration of an HTTP task will specify an endpoint URL and its templatized payload that will be used when the task executes.

Data is passed between tasks using `${...}` expression syntax. This allows a task to reference outputs from a previous task, workflow inputs, or other context variables:

```json
{
  "name": "send_notification",
  "taskReferenceName": "send_notification_ref",
  "type": "SIMPLE",
  "inputParameters": {
    "recipient": "${workflow.input.email}",
    "paymentId": "${process_payment_ref.output.transactionId}",
    "status": "${process_payment_ref.output.status}"
  }
}
```

For Worker tasks (`SIMPLE`), the configuration will simply contain its inputs/outputs and a reference to its task definition name, because the logic of its behavior will already be specified in the worker code of your application.

There must be at least one task configured in each workflow definition.

## Task execution

A task execution object is created during runtime when an input is passed into a configured task. This object has a unique ID and represents the result of the task operation, including the task status, start time, and inputs/outputs.


## AI and LLM tasks

Conductor includes first-class support for building AI-powered workflows through its AI/LLM [system tasks](../../documentation/configuration/workflowdef/systemtasks/index.md).

### Supported LLM providers

Conductor integrates with **14+ LLM providers** out of the box:

Anthropic, OpenAI, Azure OpenAI, Google Gemini, AWS Bedrock, Mistral, Cohere, HuggingFace, Ollama, Perplexity, Grok, StabilityAI, and more.

Each provider is configured once at the server level; workflows reference them by name, making it straightforward to swap models without changing workflow logic.

### MCP tool calling

The **LIST_MCP_TOOLS** and **CALL_MCP_TOOL** system tasks let your workflows discover and invoke tools exposed by any MCP-compatible server. This enables LLM agents to interact with external APIs, databases, and services through a standardized protocol.

### Vector databases and RAG

For retrieval-augmented generation (RAG), Conductor supports vector stores including **Pinecone**, **pgvector**, and **MongoDB Atlas**. The Embeddings and Vector Search system tasks handle the embedding generation and similarity search steps so that RAG pipelines can be expressed as standard workflows.

### Content generation

Beyond text, Conductor's AI tasks support generating images, audio, video, and PDFs — useful for workflows that produce rich media from LLM outputs.

For end-to-end AI agent patterns that combine LLM reasoning with tool use, see the [agents documentation](../ai/index.md).
