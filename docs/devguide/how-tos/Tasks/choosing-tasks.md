# Choosing Tasks

Tasks are the building blocks of Conductor workflows. In this guide, familiarise yourself with the tasks available in Conductor OSS and the differences between each of them.

## Built-in tasks

Built-in tasks allow you to easily run common tasks on the Conductor server without needing to build and deploy your own task workers. Here is an introduction of the built-in tasks available in Conductor:

* **[System tasks](../../../documentation/configuration/workflowdef/systemtasks/index.md)** common tasks that allow you to get started quickly without needing custom workers. 
* **[Operators](../../../documentation/configuration/workflowdef/operators/index.md)** enable you to declaratively design the workflow's control flow and logic with minimal code required.

### System tasks

Here are the system tasks available in Conductor OSS for common use: 

| System Task                  | Description                          |
| :-------------------- | :----------------------------------- |
| [Event](../../../documentation/configuration/workflowdef/systemtasks/event-task.md)       | Publish events to an external eventing system (AMQP, SQS, Kafka, and so on).              |
| [HTTP](../../../documentation/configuration/workflowdef/systemtasks/http-task.md)         | Call an API or HTTP endpoint.                                 |
| [Human](../../../documentation/configuration/workflowdef/systemtasks/human-task.md)       | Wait for an external signal.                                  |
| [Inline](../../../documentation/configuration/workflowdef/systemtasks/inline-task.md)     | Execute lightweight JavaScript code inline.                   |
| [No Op](../../../documentation/configuration/workflowdef/systemtasks/noop-task.md)        | Do nothing.                                                   |
| [JSON JQ Transform](../../../documentation/configuration/workflowdef/systemtasks/json-jq-transform-task.md) | Clean or transform JSON data using jq.      |
| [Kafka Publish](../../../documentation/configuration/workflowdef/systemtasks/kafka-publish-task.md)  | Publish messages to Kafka.                         |
| [Wait](../../../documentation/configuration/workflowdef/systemtasks/wait-task.md)         | Wait until a set time or duration has passed.                 |


### Operators

Here are the operators available in Conductor OSS for managing the flow of execution:

| Operator                        | Description         |
| -------------------------- | ----------------------------------------- |
| [Do While](../../../documentation/configuration/workflowdef/operators/do-while-task.md)         | Execute tasks repeatedly, like a _do…while…_ statement.     | 
| [Dynamic](../../../documentation/configuration/workflowdef/operators/dynamic-task.md)           | Execute a task dynamically, like a function pointer.           | 
| [Dynamic Fork](../../../documentation/configuration/workflowdef/operators/dynamic-fork-task.md) | Execute a dynamic number of tasks in parallel. |
| [Fork](../../../documentation/configuration/workflowdef/operators/fork-task.md)                 | Execute a static number of tasks in parallel.  | 
| [Join](../../../documentation/configuration/workflowdef/operators/join-task.md)                 | Join the forks after a Fork or Dynamic Fork before proceeding to the next task.                        |
| [Set Variable](../../../documentation/configuration/workflowdef/operators/set-variable-task.md)     | Create or update workflow variables.        |
| [Start Workflow](../../../documentation/configuration/workflowdef/operators/start-workflow-task.md) | Asynchronously start another workflow, like an entry point.   | 
| [Sub Workflow](../../../documentation/configuration/workflowdef/operators/sub-workflow-task.md) | Synchronously start another workflow, like a subroutine.  | 
| [Switch](../../../documentation/configuration/workflowdef/operators/switch-task.md)             | Execute tasks conditionally, like an _if…else…_ statement.     | 
| [Terminate](../../../documentation/configuration/workflowdef/operators/terminate-task.md)       | Terminate the current workflow, like a _return_ statement.                       |

## Custom tasks

If you need to implement custom logic beyond the scope of Conductor's system tasks, you can use Worker (`SIMPLE`) tasks instead. Unlike a built-in task, a Worker task requires setting up a worker outside the Conductor environment that polls for and executes the task.

## Task comparison

To help you decide on which tasks to use, here is a detailed comparison of similar tasks available in Conductor.

### Inline vs Worker tasks

The [Inline task](../../../documentation/configuration/workflowdef/systemtasks/inline-task.md) is used to execute custom JavaScript code directly within the workflow. It’s ideal for lightweight operations like **simple data transformations, conditional checks, or small calculations**. Because the code executes within the Conductor JVM, Inline tasks benefit from low latency, no network overhead, and easier debugging. However, it also has limitations on using other languages, custom libraries, frameworks, or stacks. 


The Worker task is handled by external task workers that execute a custom function or service
is an external custom function or service that performs a specific task in a workflow. Written in any language of choice (Python, Java, etc), it can execute **complex business logic, custom algorithms, or long-running operations**. Worker tasks run outside the Conductor server, meaning they require additional infrastructure set-up and logging mechanisms.

### Event vs Kafka Publish tasks

If you only need to publish messages to a Kafka topic for external services to use, the [Kafka Publish](../../../documentation/configuration/workflowdef/systemtasks/kafka-publish-task.md) task is simpler to set up.

In contrast, the [Event](../../../documentation/configuration/workflowdef/systemtasks/event-task.md) task supports more involved set-ups, such as using events to start a Conductor workflow, or having Conductor consume messages. It also supports a wider range of event brokers across AMQP, NATS, SQS, Kafka, and Conductor's own internal queue.


### Wait vs Human tasks

The [Wait](../../../documentation/configuration/workflowdef/systemtasks/wait-task.md) task and [Human](../../../documentation/configuration/workflowdef/systemtasks/human-task.md) task both support waiting  until a specific condition is met. Use the Wait task for cases when the workflow needs to wait for specific wait duration or timestamp, and use the Human task when the workflow needs to wait for an external trigger.

### Start Workflow vs Sub Workflow tasks

Both [Start Workflow](../../../documentation/configuration/workflowdef/operators/start-workflow-task.md) and [Sub Workflow](../../../documentation/configuration/workflowdef/operators/sub-workflow-task.md) tasks are useful for starting another workflow within a workflow. However, the Start Workflow task starts another workflow and proceeds to the next task without waiting for the started workflow to complete, while the Sub Workflow task will wait for the subworkflow to reach terminal state before proceeding to the next task.

The Sub Workflow task provides a tighter coupling between the parent workflow and the subworkflow. This is useful for cases when you need to associate workflow progress and states, or if you need to pass the output of the subworkflow back into the parent workflow.


### Fork vs Dynamic Fork tasks

Both [Fork](../../../documentation/configuration/workflowdef/operators/fork-task.md) and [Dynamic Fork](../../../documentation/configuration/workflowdef/operators/dynamic-fork-task.md) facilitate parallel execution of tasks. The Fork task executes a predetermined number of forks, while the Dynamic Fork executes a variable number of forks at runtime. 

If each fork must run a different set of tasks, it is best to use the Fork task, because Dynamic Forks can only run the same task for all its forks.


### Dynamic vs Switch tasks

Both the [Switch](../../../documentation/configuration/workflowdef/operators/switch-task.md) task and the [Dynamic](../../../documentation/configuration/workflowdef/operators/dynamic-task.md) task are useful in situations when the specific task to run is determined only at runtime. Using the Switch task allows you to easily predefine and set the specific conditions for each switch case, while using the Dynamic task allows to to mark a dynamic point in the workflow without having to pre-set all the case options into the workflow definition beforehand.

In the workflow diagram, the Dynamic task will produce a more simplified view, as it will only display the selected task. Meanwhile, the Switch task will produce a more comprehensive view that shows all possible paths that the workflow could have taken.

Here are some scenarios for deciding between a Dynamic task and a Switch task:


| Scenario                        | Task to Use         |
| -------------------------- | ----------------------------------------- |
| You have a huge number of case options or the specific case options are not yet determined.         | Dynamic    | 
| You need a default case option.       | Switch    |
| Each case option involves multiple tasks.       | Switch    |
| The conditions for each switch case is relatively straightforward.         | Switch    | 
| The conditions for each switch case is constantly changing, or requires more complicated logic.      | Dynamic    | 

If you opt for the Dynamic task, you must set up the control flow for how the task to run will be determined at runtime. For example, using a preceding task that must pass the task name into the Dynamic task.

