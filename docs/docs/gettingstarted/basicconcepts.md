## Definitions (aka Metadata or Blueprints)
Conductor definitions are like class definitions in OOP paradigm, or templates. You define this once, and use for each workflow execution. Definitions to Executions have 1:N relationship.

## Tasks
Tasks are the building blocks of Workflow. There must be at least one task in a Workflow.  
Tasks can be categorized into two types: 

 * [Systems tasks](../../configuration/systask) - executed by Conductor server.
 * Worker tasks - executed by your own workers.

## Workflow
A Workflow is the container of your process flow. It could include several different types of Tasks, Sub-Workflows, inputs and outputs connected to each other, to effectively achieve the desired result.

## Workflow Definition
Workflows are defined using a JSON based DSL and includes a set of tasks that are executed as part of the workflows.  The tasks are either control tasks (fork, conditional etc) or application tasks (e.g. encode a file) that are executed on a remote machine. [Detailed description](../../configuration/workflowdef)

## Task Definition
Task definitions help define Task level parameters like inputs and outputs, timeouts, retries etc.

* All tasks need to be registered before they can be used by active workflows.
* A task can be re-used within multiple workflows.

[Detailed description](../../configuration/taskdef)

## System Tasks
System tasks are executed within the JVM of the Conductor server and managed by Conductor for its execution and scalability.

See [Systems tasks](../../configuration/systask) for list of available Task types, and instructions for using them.

!!! Note
	Conductor provides an API to create user defined tasks that are executed in the same JVM as the engine.	See [WorkflowSystemTask](https://github.com/Netflix/conductor/blob/dev/core/src/main/java/com/netflix/conductor/core/execution/tasks/WorkflowSystemTask.java) interface for details.

## Worker Tasks
Worker tasks are implemented by your application(s) and run in a separate environment from Conductor. The worker tasks can be implemented in any language.  These tasks talk to Conductor server via REST/gRPC to poll for tasks and update its status after execution.

Worker tasks are identified by task type __SIMPLE__ in the blueprint.
