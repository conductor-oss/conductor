# Reusing Tasks

Since task workers typically perform a unit of work as part of a larger workflow, Conductor’s infrastructure is built to enable task reusability out of the box. Once a task is defined in Conductor, it can be reused numerous times:

- In the same workflow, using different task reference names.
- Across various workflows.

When reusing tasks, it's important to think of situations that a multi-tenant system faces. By default, all the work assigned to this worker goes into the same task queue. This could result in your worker not being polled quickly if there is a noisy neighbor in the ecosystem. You can tackle this situation by scaling up the number of workers to handle the task load, or even using [task-to-domain](../../../devguide/how-tos/Tasks/taskdomains.md) to route the task load into separate queues.