Workflow Tasks are of two types:

* System tasks (e.g. DECISION, FORK, JOIN etc.)
* Remote worker tasks.

System tasks are executed within the JVM of the engine's server.  User defined tasks are remote tasks that executes on remote JVM and talks to the conductor server using REST endpoints.

* **SIMPLE** Task that is executed by a worker running on a remote machine.  Orchestration engine schedules the task and awaits completion.
* **DECIDE** Decision task used to create a branch.  Only one branch is executed based on the condition.
* **FORK** Forks a parallel set of tasks.  Each set is scheduled to be executed in parallel.
* **JOIN** Waits for one or more tasks to be completed before proceeding.  Used in conjunction with _FORK_ or _ FORK_JOIN_DYNAMIC_
* **SUB_WORKFLOW** Allows nesting another workflow as a task.  When executed, system spawns the workflow and task waits its completion.  If the newly spawned workflow fails, the task is marked as FAILEd.
* **DYNAMIC** A dynamic task.  Similar to _SIMPLE_.  The difference being the name of the task to be scheduled is determined at runtime using parameters from workflow input or other task input/output.
* **FORK_JOIN_DYNAMIC**

Conductor provides an API to create user defined tasks that are excuted in the same JVM as the engine.  see ```om.netflix.workflow.cpe.core.execution.tasks.WorkflowSystemTask ``` interface for details.
