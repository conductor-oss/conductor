## Workflow Definition
Workflows are defined using a JSON based DSL and includes a set of tasks that are executed as part of the workflows.  The tasks are either control tasks (fork, conditional etc) or application tasks (e.g. encode a file) that are executed on a remote machine.
[more details](/metadata)

## Task Definition
* All tasks need to be registered before they can be used by active workflows.
* A task can be re-used within multiple workflows.
[more details](/metadata/#task-definition)
