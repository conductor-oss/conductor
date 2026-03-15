---
description: "Frequently asked questions about Conductor — AI agent orchestration, durable execution, LLM providers, saga pattern, scaling, persistence backends, and worker configuration."
---

# Frequently Asked Questions

## General

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

Conductor combines durable execution, 14+ native LLM providers, JSON-native workflow definitions, 7+ language SDKs, and battle-tested scale (Netflix, Tesla, LinkedIn, JP Morgan). It's the only workflow engine with native AI/LLM task types, MCP integration, and built-in vector database support.

## Can Conductor orchestrate AI agents?

Yes. Conductor provides native LLM tasks (chat completion, text completion), MCP tool calling (LIST_MCP_TOOLS, CALL_MCP_TOOL), human-in-the-loop approval (HUMAN task), and dynamic workflows that agents can generate at runtime. All with the same durability guarantees as any other workflow.

## Does Conductor support MCP (Model Context Protocol)?

Yes. LIST_MCP_TOOLS discovers available tools from any MCP server, and CALL_MCP_TOOL executes them. Workflows can also be exposed as MCP tools via the MCP Gateway.

## What LLM providers does Conductor support?

14+ providers natively: Anthropic (Claude), OpenAI (GPT), Azure OpenAI, Google Gemini, AWS Bedrock, Mistral, Cohere, HuggingFace, Ollama, Perplexity, Grok, StabilityAI, and more. All accessible as workflow system tasks.

## Does Conductor support vector databases and RAG?

Yes. Built-in support for Pinecone, pgvector, and MongoDB Atlas Vector Search. System tasks handle embedding generation, storage, indexing, and semantic search — enabling RAG pipelines as standard workflows.

## Is Conductor a durable execution engine?

Yes. Every workflow execution is persisted at each step. If a task fails, it's retried with configurable backoff. If a worker crashes, the task is rescheduled. If the server restarts, execution resumes exactly where it left off. See [Durable Execution](../architecture/durable-execution.md).

## Can Conductor handle millions of workflows?

Yes. Originally built at Netflix to handle massive scale, Conductor scales horizontally. Workers scale independently, and the server supports millions of concurrent workflow executions across multiple persistence backends.

## Does Conductor support the saga pattern?

Yes. Configure a `failureWorkflow` that runs compensation logic when the main workflow fails. Combined with task-level retries and timeout policies, Conductor provides full saga pattern support for distributed transactions. See [Handling Errors](how-tos/Workflows/handling-errors.md).

## Can I create workflows at runtime?

Yes. Workflow definitions are JSON and can be created, modified, and started dynamically via the API or SDKs. LLMs can generate workflow definitions that Conductor executes immediately without pre-registration.

## Does Conductor support human-in-the-loop?

Yes. The HUMAN task type pauses workflow execution until an external signal (approval, rejection, or data input) is received via API. The pause survives server restarts and deploys.

## What persistence backends are supported?

Redis+Dynomite, PostgreSQL, MySQL, Cassandra, SQLite, Elasticsearch, and OpenSearch. Choose based on your scale and operational requirements.

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

- **Java** — [conductor-community/conductor](https://github.com/conductoross/conductor)
- **Python** — [conductor-community/conductor-python](https://github.com/conductor-sdk/conductor-python)
- **Go** — [conductor-community/conductor-go](https://github.com/conductor-sdk/conductor-go)
- **JavaScript** — [conductor-community/conductor-javascript](https://github.com/conductor-sdk/conductor-javascript)
- **C#** — [conductor-community/conductor-csharp](https://github.com/conductor-sdk/conductor-csharp)
- **Clojure** — [conductor-community/conductor-clojure](https://github.com/conductor-sdk/conductor-clojure)
- **Ruby** — [nicklaros/conductor_ruby](https://github.com/nicklaros/conductor_ruby)
- **Rust** — [nicklaros/conductor_rust](https://github.com/nicklaros/conductor_rust)

## My workflow is running and the task is SCHEDULED but it is not being processed.

Make sure that the worker is actively polling for this task. Navigate to the `Task Queues` tab on the Conductor UI and select your task name in the search box. Ensure that `Last Poll Time` for this task is current.

In Conductor 3.x, ```conductor.redis.availabilityZone``` defaults to ```us-east-1c```.  Ensure that this matches where your workers are, and that it also matches```conductor.redis.hosts```.

## How do I configure a notification when my workflow completes or fails?

When a workflow fails, you can configure a "failure workflow" to run using the```failureWorkflow``` parameter. By default, three parameters are passed:

* reason
* workflowId: use this to pull the details of the failed workflow.
* failureStatus

You can also use the Workflow Status Listener:

* Set the workflowStatusListenerEnabled field in your workflow definition to true which enables [notifications](../documentation/configuration/workflowdef/index.md#workflow-notifications).
* Add a custom implementation of the Workflow Status Listener. Refer [this](../documentation/advanced/extend.md#workflow-status-listener).
* This notification can be implemented in such a way as to either send a notification to an external system or to send an event on the conductor queue to complete/fail another task in another workflow as described [here](../documentation/configuration/eventhandlers.md).

Refer to this [documentation](../documentation/configuration/workflowdef/index.md#workflow-notifications) to extend conductor to send out events/notifications upon workflow completion/failure.

## I want my worker to stop polling and executing tasks when the process is being terminated. (Java client)

In a `PreDestroy` block within your application, call the `shutdown()` method on the `TaskRunnerConfigurer` instance that you have created to facilitate a graceful shutdown of your worker in case the process is being terminated.

## Can I exit early from a task without executing the configured automatic retries in the task definition?

Set the status to `FAILED_WITH_TERMINAL_ERROR` in the TaskResult object within your worker. This would mark the task as FAILED and fail the workflow without retrying the task as a fail-fast mechanism.
