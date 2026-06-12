---
description: "Frequently asked questions about Conductor — open source workflow engine, self-hosted deployment, AI agent orchestration, LLM orchestration, workflow automation, durable execution, microservice orchestration, saga pattern, scaling, and how Conductor compares to Temporal, Airflow, and Step Functions."
---

# Frequently Asked Questions

## General

### Is Conductor open source?

Yes. Conductor is a fully open source workflow engine, released under the Apache 2.0 license. You can self-host it on your own infrastructure — there is no vendor lock-in, no proprietary runtime, and no cloud dependency. The self-hosted workflow engine supports 5 persistence backends, 6 message brokers, and runs anywhere Docker or a JVM runs.

### Is this the same as Netflix Conductor?

Yes. Conductor OSS is the continuation of the original Netflix Conductor repository after Netflix contributed the project to the open-source foundation.

### Is Netflix Conductor abandoned?

No. The original Netflix repository has transitioned to Conductor OSS, which is the new home for the project. Active development and maintenance continues here.

### Is this project actively maintained?

Yes. Orkes is the primary maintainer of this repository and offers an enterprise SaaS platform for Conductor across all major cloud providers.

### Is Orkes Conductor compatible with Conductor OSS?

100% compatible. Orkes Conductor is built on top of Conductor OSS, ensuring full compatibility between the open-source version and the enterprise offering.

### Are workflows always asynchronous?

No. While Conductor excels at asynchronous orchestration, it also supports synchronous workflow execution when immediate results are required.

### Do I need to use a Conductor-specific framework?

Not at all. Conductor is language and framework agnostic. Use your preferred language and framework — SDKs provide native integration for Java, Python, JavaScript, Go, C#, and more.

### Is Conductor a low-code/no-code platform?

No. Conductor is designed for developers who write code. While workflows can be defined in JSON, the power comes from building workers and tasks in your preferred programming language.

### Can Conductor handle complex workflows?

Yes. Conductor supports advanced patterns including nested loops, dynamic branching, sub-workflows, and workflows with thousands of tasks.

## How does Conductor compare to other workflow engines?

Conductor combines durable execution, 14+ native LLM providers, JSON-native workflow definitions, 7+ language SDKs, and battle-tested scale (Netflix, Tesla, LinkedIn, JP Morgan). It's the only open source workflow engine with native AI/LLM task types, MCP integration, and built-in vector database support.

### Isn't JSON too limited for complex workflows?

No — JSON makes workflows *more* capable, not less. A JSON workflow definition is pure orchestration: it describes what runs, in what order, with what inputs. It cannot open connections, mutate state, or produce side effects. This means every execution is deterministic by construction — given the same inputs, the same task graph executes every time. That is why replay, restart, and retry work unconditionally.

Code-based workflow engines embed orchestration logic alongside business logic, which means your workflow code can introduce non-determinism (system clocks, random values, uncontrolled I/O). These engines must impose restrictions on what your code is allowed to do — and bugs from violating those restrictions are subtle and hard to debug.

Conductor's dynamic primitives — [DYNAMIC tasks](../documentation/configuration/workflowdef/operators/dynamic-task.md), [DYNAMIC_FORK](../documentation/configuration/workflowdef/operators/dynamic-fork-task.md), and [dynamic sub-workflows](../documentation/configuration/workflowdef/operators/sub-workflow-task.md) — provide more runtime flexibility than code-based definitions. An LLM can generate a complete workflow definition as JSON and Conductor executes it immediately, with full durability and observability. No code generation, no compilation, no deployment. See [JSON + Code Native](../architecture/json-native.md) for the full picture.

### How is Conductor different from Temporal?

Both are durable execution engines, but with fundamentally different approaches. Conductor's JSON-native definitions separate orchestration from implementation, making workflows deterministic by construction — no side-effect restrictions to remember, no non-determinism bugs to debug. Temporal embeds orchestration in code, which requires developers to avoid non-deterministic operations (system clocks, random values, uncontrolled I/O) or risk subtle replay failures.

Conductor is fully open source (Apache 2.0) with no proprietary server components. It provides native LLM orchestration for 14+ providers, MCP tool calling, and vector database support out of the box — capabilities Temporal does not offer. Conductor's JSON definitions can be generated and modified at runtime by LLMs or APIs without a compile/deploy cycle.

### How is Conductor different from AWS Step Functions?

Step Functions is a proprietary, cloud-locked service. Conductor is an open source, self-hosted workflow engine you can run on any infrastructure. Conductor supports 7+ language SDKs, 5 persistence backends, and provides native AI agent orchestration — none of which Step Functions offers. If you need an open source Step Functions alternative with no cloud lock-in, Conductor is a strong fit.

### How is Conductor different from Airflow?

Airflow is a DAG-based batch scheduler designed for data pipelines. Conductor is a real-time workflow orchestration engine designed for microservice orchestration, event-driven workflows, and AI agent orchestration. Conductor provides durable execution with sub-second task scheduling, while Airflow is optimized for scheduled batch jobs. If you need a real-time workflow engine rather than a job scheduler, Conductor is the better choice.

### Can I use Conductor for workflow automation?

Yes. Conductor is a developer-first workflow automation platform — not a low-code drag-and-drop tool, but a code-first workflow engine where you define workflows as code or JSON and implement task workers in any language. It is well suited for automating business processes, data pipelines, and multi-service workflows that need durable execution and full observability.

## Can Conductor orchestrate AI agents?

Yes. Conductor provides native AI agent orchestration with LLM tasks (chat completion, text completion), MCP tool calling and function calling (LIST_MCP_TOOLS, CALL_MCP_TOOL), human-in-the-loop approval (HUMAN task), and dynamic workflows that agents can generate at runtime. Every agent built on Conductor is a durable agent — LLM orchestration runs with the same durable execution guarantees as any other workflow, so agents survive crashes, retries, and infrastructure failures without losing progress.

## Does Conductor support MCP (Model Context Protocol)?

Yes. LIST_MCP_TOOLS discovers available tools from any MCP server, and CALL_MCP_TOOL executes them. Workflows can also be exposed as MCP tools via the MCP Gateway.

## What LLM providers does Conductor support?

14+ providers natively: Anthropic (Claude), OpenAI (GPT), Azure OpenAI, Google Gemini, AWS Bedrock, Mistral, Cohere, HuggingFace, Ollama, Perplexity, Grok, StabilityAI, and more. All accessible as workflow system tasks with built-in function calling and tool use via MCP integration.

## Does Conductor support vector databases and RAG?

Yes. Built-in support for Pinecone, pgvector, and MongoDB Atlas Vector Search. System tasks handle embedding generation, storage, indexing, and semantic search — enabling RAG pipelines as standard workflows.

## Is Conductor a durable execution engine?

Yes. Every workflow execution is persisted at each step. If a task fails, it's retried with configurable backoff. If a worker crashes, the task is rescheduled. If the server restarts, execution resumes exactly where it left off. See [Durable Execution](../architecture/durable-execution.md).

## Can Conductor handle millions of workflows?

Yes. Originally built at Netflix to handle massive scale, Conductor scales horizontally across multiple server instances. Workers scale independently, and the server supports millions of concurrent workflow executions across multiple persistence backends. This horizontal scaling architecture makes Conductor suitable for production workflow deployments at any scale.

## Does Conductor support the saga pattern?

Yes. Configure a `failureWorkflow` that runs compensation logic when the main workflow fails. Combined with task-level retries and timeout policies, Conductor provides full saga pattern support for distributed transactions. See [Handling Errors](how-tos/Workflows/handling-errors.md).

## Can I create workflows at runtime?

Yes. Workflow definitions are JSON and can be created, modified, and started dynamically via the API or SDKs. LLMs can generate workflow definitions that Conductor executes immediately without pre-registration.

## Does Conductor support human-in-the-loop?

Yes. The HUMAN task type pauses workflow execution until an external signal (approval, rejection, or data input) is received via API. The pause survives server restarts and deploys.

## What persistence backends are supported?

Redis, PostgreSQL, MySQL, Cassandra, and SQLite. Choose based on your scale and operational requirements.

## What message brokers are supported?

Kafka, NATS, NATS Streaming, AMQP (RabbitMQ), SQS, and Conductor's internal queue. Use them for event-driven workflows and external system integration.

## How do you schedule a task to be put in the queue after some time (e.g. 1 hour, 1 day etc.)

After polling for the task update the status of the task to `IN_PROGRESS` and set the `callbackAfterSeconds` value to the desired time.  The task will remain in the queue until the specified second before worker polling for it will receive it again.

If there is a timeout set for the task, and the `callbackAfterSeconds` exceeds the timeout value, it will result in task being TIMED_OUT.

## How long can a workflow be in running state?  Can I have a workflow that keeps running for days or months?

Yes.  As long as the timeouts on the tasks are set to handle long running workflows, it will stay in running state.

## My workflow fails to start with missing task error

Ensure all the tasks are registered via `/metadata/taskdefs` APIs.  Add any missing task definition (as reported in the error) and try again.

## Where does my worker run?  How does conductor run my tasks?

Conductor does not run the workers.  When a task is scheduled, it is put into the queue maintained by Conductor.  Workers are required to poll for tasks using `/tasks/poll` API at periodic interval, execute the business logic for the task and report back the results using `POST {{ api_prefix }}/tasks` API call.
Conductor, however will run [system tasks](../documentation/configuration/workflowdef/systemtasks/index.md) on the Conductor server.

## How can I schedule workflows to run at a specific time?

Conductor itself does not provide any scheduling mechanism.  But there is a community project [_Schedule Conductor Workflows_](https://github.com/jas34/scheduledwf) which provides workflow scheduling capability as a pluggable module as well as workflow server.
Other way is you can use any of the available scheduling systems to make REST calls to Conductor to start a workflow.  Alternatively, publish a message to a supported eventing system like SQS to trigger a workflow.
More details about [eventing](../documentation/configuration/eventhandlers.md).

## Can I use Conductor with Ruby / Go / Python / JavaScript / C# / Rust?

Yes. Workers can be written in any language as long as they can poll and update the task results via HTTP endpoints. Conductor provides official and community SDKs for many languages:

- **Java** — [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk)
- **Python** — [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk)
- **Go** — [conductor-oss/go-sdk](https://github.com/conductor-oss/go-sdk)
- **JavaScript** — [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk)
- **C#** — [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk)
- **Ruby** — [conductor-oss/ruby-sdk](https://github.com/conductor-oss/ruby-sdk)
- **Rust** — [conductor-oss/rust-sdk](https://github.com/conductor-oss/rust-sdk)

## My workflow is running and the task is SCHEDULED but it is not being processed.

Make sure that the worker is actively polling for this task. Navigate to the `Task Queues` tab on the Conductor UI and select your task name in the search box. Ensure that `Last Poll Time` for this task is current.

In Conductor 3.x, ```conductor.redis.availabilityZone``` defaults to ```us-east-1c```.  Ensure that this matches where your workers are, and that it also matches```conductor.redis.hosts```.

## How do I configure a notification when my workflow completes or fails?

When a workflow fails, you can configure a "failure workflow" to run using the```failureWorkflow``` parameter. By default, three parameters are passed:

* reason
* workflowId: use this to pull the details of the failed workflow.
* failureStatus

You can also use the Workflow Status Listener:

* Set the workflowStatusListenerEnabled field in your workflow definition to true which enables [notifications](../documentation/configuration/workflowdef/index.md#workflow-status-listener).
* Add a custom implementation of the Workflow Status Listener. Refer to the [Workflow Status Listener extension guide](../documentation/advanced/extend.md#workflow-status-listener).
* This notification can be implemented in such a way as to either send a notification to an external system or to send an event on the conductor queue to complete/fail another task in another workflow as described in the [event handlers documentation](../documentation/configuration/eventhandlers.md).

Refer to this [documentation](../documentation/configuration/workflowdef/index.md#workflow-status-listener) to extend conductor to send out events/notifications upon workflow completion/failure.

## I want my worker to stop polling and executing tasks when the process is being terminated. (Java client)

In a `PreDestroy` block within your application, call the `shutdown()` method on the `TaskRunnerConfigurer` instance that you have created to facilitate a graceful shutdown of your worker in case the process is being terminated.

## Can I exit early from a task without executing the configured automatic retries in the task definition?

Set the status to `FAILED_WITH_TERMINAL_ERROR` in the TaskResult object within your worker. This would mark the task as FAILED and fail the workflow without retrying the task as a fail-fast mechanism.
