# Tasks

A task is the basic building block of a Conductor workflow. They are reusable and modular, representing steps in your application like processing data files, calling an AI model, or executing some logic.

In Conductor, tasks can be defined, configured, and then executed. Learn more about the distinct but related concepts, **task definition**, **task configuration**, and **task execution** below.


## Types of tasks

Tasks are categorized into three types, enabling you to flexibly build workflows using pre-built tasks, custom logic, or a combination of both:

### System tasks

[System tasks](../../documentation/configuration/workflowdef/systemtasks/index.md) are built-in, general-purpose tasks designed for common uses like calling an HTTP endpoint or publishing events to an external system.

System tasks are managed by Conductor and executed within its server's JVM, allowing you to get started without having to write custom workers.

### Worker tasks
Worker tasks (`SIMPLE`) can be used to implement custom logic outside the scope of Conductor’s system tasks. Also known as Simple tasks, Worker tasks are implemented by your task workers that run in a separate environment from Conductor.

### Operators
[Operators](../../documentation/configuration/workflowdef/operators/index.md) are built-in control flow primitives similar to programming language constructs like loops, switch cases, or fork/joins. Like system tasks, operators are also managed by Conductor.


## Task definition
[Task definitions](../../documentation/configuration/taskdef.md) are used to define a task's default parameters, like inputs and output keys, timeouts, and retries. This provides reusability across workflows, as the registered task definition will be referenced when a task is configured in a workflow definition.

When using Worker tasks (`SIMPLE`), its task definition must be registered to the Conductor server before it can execute in a workflow. Because system tasks are managed by Conductor, tt is not necessary to add a task definition for system tasks unless you wish to customize its default parameters.


## Task configuration

Stored in the `tasks` array of a [workflow definition](workflows.md#workflow-definition), task configurations make up the workflow-specific blueprint that describes:

- The order and control flow of tasks.
- How data is passed from one task to another through task inputs and outputs.
- Other workflow-specific behavior, like optionality, caching, and schema enforcement.

The specific configuration for each task differs depending on the task type. For system tasks and operators, the task configuration will contain important parameters that control the behavior of the task. For example, the task configuration of an HTTP task will specify an endpoint URL and its templatized payload that will be used when the task executes.

For Worker tasks (`SIMPLE`), the configuration will simply contain its inputs/outputs and a reference to its task definition name, because the logic of its behavior will already be specified in the worker code of your application.

There must be at least one task configured in each workflow definition.

## Task execution

A task execution object is created during runtime when an input is passed into a configured task. This object has a unique ID and represents the result of the task operation, including the task status, start time, and inputs/outputs.

