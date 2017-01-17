## Workflow Definition
Workflows are defined using a JSON based DSL and includes a set of tasks that are executed as part of the workflows.  The tasks are either control tasks (fork, conditional etc) or application tasks (e.g. encode a file) that are executed on a remote machine.
[more details](/metadata)

## Task Definition
* All tasks need to be registered before they can be used by active workflows.
* A task can be re-used within multiple workflows.
Worker tasks fall into two categories:
	* System Task
	* Worker Task

## System Tasks
System tasks are executed within the JVM of the Conductor server and managed by Conductor for its execution and scalability.

| Name        | Purpose           |
| ------------- |:-------------|
| [DYNAMIC](/metadata/systask/#dynamic-task) | A worker task which is derived based on the input expression to the task, rather than being statically defined as part of the blueprint |
| [DECIDE](/metadata/systask/#decision) | Decision tasks - implements case...switch style fork|
| [FORK](/metadata/systask/#fork) | Forks a parallel set of tasks.  Each set is scheduled to be executed in parallel |
| [FORK_JOIN_DYNAMIC](/metadata/systask/#dynamic-fork) | Similar to FORK, but rather than the set of tasks defined in the blueprint for parallel execution, FORK_JOIN_DYNAMIC spawns the parallel tasks based on the input expression to this task |
| [JOIN](/metadata/systask/#join) | Complements FORK and FORK_JOIN_DYNAMIC.  Used to merge one of more parallel branches* 
| [SUB_WORKFLOW](/metadata/systask/#sub-workflow) | Nest another workflow as a sub workflow task.  Upon execution it instantiates the sub workflow and awaits it completion| 

Conductor provides an API to create user defined tasks that are excuted in the same JVM as the engine.  see [WorkflowSystemTask](https://github.com/Netflix/conductor/blob/dev/core/src/main/java/com/netflix/conductor/core/execution/tasks/WorkflowSystemTask.java) interface for details.

## Worker Taks
Worker tasks are implemented by application(s) and runs in a separate environment from Conductor.  The worker tasks can be implemented in any langugage.  These tasks talk to Conductor server via REST API endpionts to poll for tasks and update its status after execution.

Worker tasks are identified by task type __SIMPLE__ in the blueprint.


[more details](/metadata/#task-definition)