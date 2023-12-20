# Tasks
Tasks are the building blocks of Conductor Workflows. There must be at least one task configured in each Workflow Definition. A typical Conductor workflow defines a lists of tasks that are executed until the completion or termination of the workflow.

Tasks can be categorized into three types: 

## Types of Tasks
### System Tasks
[**System Tasks**](../../documentation/configuration/workflowdef/systemtasks/index.md) are built-in tasks that are general purpose and re-usable. They are executed within the JVM of the Conductor server and managed by Conductor for execution and scalability. Such tasks allow you to get started without having to write custom workers. 

### Simple Tasks
[**Simple Tasks**](workers.md) or Worker Tasks are implemented by your application and run in a separate environment from Conductor. These tasks talk to the Conductor server via REST/gRPC to poll for tasks and update its status after execution.

### Operators
[**Operators**](../../documentation/configuration/workflowdef/operators/index.md) are built-in primitives in Conductor that allow you control the flow of tasks in your workflow. Operators are similar to programming constructs such as `for` loops, `switch` blocks, etc.

## Task Configuration
Task Configurations appear within the `tasks` array property of the Workflow Definition. This array is the blueprint that describes how a workflow will process an input payload by passing it through successive tasks.

* For all tasks, the configuration will specifiy what **input parameters** the task takes. 
* For SIMPLE (worker based) tasks, the configuration will contain a reference to a registered worker `taskName`. 
* For System Tasks and Operators, the task configuration will contain important parameters that control the behavior of the task. For example, the task configuration of an HTTP task will specify an endpoint URL and the templatized payload that it will be called with when the task executes.

## Task Definition
Not to be confused with Task Configurations, [Task Definitions](../../documentation/configuration/taskdef.md) help define default task level parameters like inputs and outputs, timeouts, retries etc. for SIMPLE (i.e. worker implemented) tasks.

* All simple tasks need to be registered before they can be used by active workflows.
* Task definitions can be registered via the UI, or through the API.
* A registered task definition can be referenced from within different workflows.

## Task Execution
Each time a workload is passed into a configured task, a Task Execution object is created. This object has a unique ID and represents the result of the operation. This includes the status (i.e. whether the task was completed successfully), and any input, output and variables associated with the task. 

