---
description: Choose direct starts, schedules, events, workflow composition, or signals for Conductor workflows.
---

# Choose a workflow trigger

Choose the mechanism whose owner can make the start or resume decision reliably.

| Need | Use | Result |
|---|---|---|
| A request should create work now | [Direct start](starting-workflows.md) | A new workflow execution |
| Time or cadence should create work | [Schedule](scheduling-workflows.md) | A new execution at each cron slot |
| A broker message should create work | [Event handler](../../../documentation/configuration/eventhandlers.md) | A new execution for a matching event |
| A parent workflow owns the dependency | `SUB_WORKFLOW` or `START_WORKFLOW` | A child execution, waited for or fire-and-forget |
| An external result should resume existing work | Task signal or event-handler `complete_task`/`fail_task` | The identified task changes state |

## Decision procedure

1. Decide whether the action creates a new execution or resumes one that already exists.
2. If it creates work, identify the owner: application request, clock, message, or parent workflow.
3. If it resumes work, retain the task ID or workflow ID and task reference name when the task begins waiting.
4. Define an idempotency key or stable message ID before enabling retries or broker redelivery.
5. Verify the observable result: a returned workflow ID for a start, or the expected task status and downstream transition for a resume.

## Limitations

- Schedules have no native overlap policy; executions can overlap.
- Event actions are concurrent and not atomic; one can succeed while another fails.
- An OSS event handler cannot resolve a business correlation key to a waiting task.
- A signal changes existing work; it does not create a new workflow.

Next, implement the selected route with [Start workflows](starting-workflows.md), [Schedule workflows](scheduling-workflows.md), or [Event orchestration](../event-bus.md).
