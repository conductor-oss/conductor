---
description: "Frequently asked questions about Conductor: durable workflows, adaptive agents, AI orchestration, self-hosting, operations, and runtime control."
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

## What does Conductor provide?

Conductor combines durable workflow execution with built-in system tasks, JSON-native workflow definitions, polyglot workers, and native AI and MCP capabilities. Use it to coordinate distributed services, framework-authored agents, and adaptive runtime paths while retaining an inspectable execution record.

### Isn't JSON too limited for complex workflows?

No. A JSON definition expresses orchestration data: task order, inputs, outputs, operators, and policy. Put side effects in built-in tasks or workers, where they can be observed and retried. The graph remains machine-readable and versioned, while the worker remains ordinary code.

For runtime-selected paths, use [DYNAMIC tasks](../documentation/configuration/workflowdef/operators/dynamic-task.md), [FORK_JOIN_DYNAMIC](../documentation/configuration/workflowdef/operators/dynamic-fork-task.md), and [sub-workflows](../documentation/configuration/workflowdef/operators/sub-workflow-task.md). A generated definition is data that must be validated before it is started; see [Durable Adaptive Graphs](ai/dynamic-workflows.md).

### Can I use Conductor for workflow automation?

Yes. Conductor is a developer-first workflow automation platform — not a low-code drag-and-drop tool, but a code-first workflow engine where you define workflows as code or JSON and implement task workers in any language. It is well suited for automating business processes, data pipelines, and multi-service workflows that need durable execution and full observability.

## Can Conductor orchestrate AI agents?

Yes. Conductor provides LLM tasks, MCP tool discovery and calls, human approval, vector workflows, and adaptive control flow. An agent can select approved paths at runtime while Conductor retains state, task outcomes, and operator controls around the execution.

## Does Conductor support MCP (Model Context Protocol)?

Yes. LIST_MCP_TOOLS discovers available tools from any MCP server, and CALL_MCP_TOOL executes them. Workflows can also be exposed as MCP tools via the MCP Gateway.

## What LLM providers does Conductor support?

See [LLM orchestration](ai/llm-orchestration.md) for the source-backed provider matrix and the capability-specific task reference. Providers, models, and supported features evolve independently, so the matrix is the canonical documentation.

## Does Conductor support vector databases and RAG?

Yes. Built-in support for Pinecone, pgvector, and MongoDB Atlas Vector Search. System tasks handle embedding generation, storage, indexing, and semantic search — enabling RAG pipelines as standard workflows.

## Is Conductor a durable execution engine?

Yes. Conductor persists workflow and task state, supports configurable retry and timeout policy, and provides recovery paths for worker and infrastructure failure. At-least-once task delivery means side-effecting tools must be idempotent. See [Durable Execution](../architecture/durable-execution.md).

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

Use Conductor's built-in scheduler to bind a Spring cron expression to a workflow start request. You can create, pause, resume, preview, and inspect schedules through the [scheduling workflows guide](how-tos/Workflows/scheduling-workflows.md) or the [Scheduler API](../documentation/api/scheduler.md). For message-driven starts instead of time-based starts, use [event orchestration](how-tos/event-bus.md).

## Can I use Conductor with Ruby / Go / Python / JavaScript / C# / Rust?

Yes. Workers can be written in any language as long as they can poll and update the task results via HTTP endpoints. Conductor provides official and community SDKs for many languages:

- **Java** — [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk)
- **Python** — [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk)
- **Go** — [conductor-oss/go-sdk](https://github.com/conductor-oss/go-sdk)
- **JavaScript** — [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk)
- **C#** — [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk)
- **Ruby** — [conductor-oss/ruby-sdk](https://github.com/conductor-oss/ruby-sdk)
- **Rust** — [conductor-oss/rust-sdk](https://github.com/conductor-oss/rust-sdk)

## The same task is scheduled twice, both showing "attempt 0". What causes this?

This is almost always caused by running multiple Conductor server instances without distributed locking enabled. When locking is off, two server instances can each pick up the same workflow and independently schedule the same task — producing two identical entries, both at attempt 0, with neither aware of the other.

**To fix it**, enable distributed locking so only one server processes a given workflow at a time:

```properties
conductor.app.workflowExecutionLockEnabled=true
conductor.workflow-execution-lock.type=redis   # or zookeeper
```

See [Locking](running/deploy.md#locking) for the full configuration, including Redis and Zookeeper options.

If you are running a single server instance, the cause is more likely the sweeper and an event or callback both triggering a `decide` on the same workflow simultaneously. The locking setting above resolves this case as well.

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
