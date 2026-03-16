---
description: How Conductor guarantees durable code execution for distributed workflows — what persists at every step, at-least-once task delivery, saga pattern compensation, failure matrix, task state transitions, retry logic with exponential backoff, and distributed consistency. The open source distributed workflow engine built for reliability.
---

# Durable Execution Semantics

Conductor is a durable execution engine for distributed workflows and durable agents. Every workflow execution is persisted at every step, survives infrastructure failures, and guarantees at-least-once task delivery. This durable execution model means your workflows and agents never lose progress. This page defines exactly what that means.

## What persists

When a workflow executes, Conductor persists:

- The **workflow definition snapshot** used for this execution (immutable after start).
- The **workflow state**: status, input, output, correlation ID, and variables.
- Every **task execution**: status, input, output, timestamps, retry count, and worker ID.
- The **task queue state**: which tasks are scheduled, in progress, or completed.

All state is written to the configured persistence store (Redis, PostgreSQL, MySQL, or Cassandra) before the next step proceeds. If the server restarts, execution resumes from the last persisted state.


## Task delivery guarantees

Conductor provides **at-least-once delivery** for all tasks:

- When a task is scheduled, it is placed in a persistent task queue.
- A worker polls for the task and receives it. The task moves to `IN_PROGRESS`.
- If the worker completes the task, it reports `COMPLETED` and Conductor advances the workflow.
- If the worker fails or crashes, the task is **redelivered** based on the retry and timeout configuration.

A task is never silently lost. If a worker polls a task but never responds, the response timeout triggers redelivery.


## Failure matrix

Here is exactly what happens in each failure scenario:

| Scenario | What Conductor does | Outcome |
|---|---|---|
| **Worker crashes after poll, before any work** | Response timeout fires. Task returns to `SCHEDULED`. New worker picks it up. | Task is retried automatically. No data loss. |
| **Worker crashes after side effect, before completion update** | Response timeout fires. Task is redelivered to another worker. | Task executes again. Workers must be idempotent for side effects, or use the task's `updateTime` to detect redelivery. |
| **Worker reports FAILED** | Conductor creates a new task execution based on retry configuration (`retryCount`, `retryDelaySeconds`, `retryLogic`). | Retried up to the configured limit. After exhaustion, task moves to `FAILED` and the workflow's failure handling kicks in. |
| **Worker reports FAILED_WITH_TERMINAL_ERROR** | No retry. Task is terminal. | Workflow fails or executes the configured `failureWorkflow`. |
| **Server restarts during workflow execution** | On restart, the sweeper service picks up in-progress workflows from persistent storage and re-evaluates them. | Execution resumes from the last persisted state. No manual intervention needed. |
| **Long wait across deploys** | WAIT and HUMAN tasks remain `IN_PROGRESS` in persistent storage. The timer or signal resolution is durable. | When the duration elapses or signal arrives (even days later, after multiple deploys), the task completes and the workflow advances. |
| **Signal/webhook arrives for a paused workflow** | The Task Update API or event handler sets the WAIT/HUMAN task to `COMPLETED` with the provided output. | Workflow resumes immediately with the signal payload available as task output. |
| **Workflow definition updated while executions are running** | Running executions continue using the **snapshot** of the definition taken at start time. New executions use the updated definition. | No running execution is affected by definition changes. Zero-downtime upgrades. |
| **Workflow version deleted while executions are running** | Running executions are decoupled from the metadata store. They continue using their embedded definition snapshot. | Existing executions complete normally. Only new starts are affected. |
| **Network partition between worker and server** | Worker's updates don't reach the server. Response timeout fires, task is requeued. | After partition heals, a new worker (or the same one) picks up the task. |


## Task state transitions

Every task follows this state machine:

```
SCHEDULED ──→ IN_PROGRESS ──→ COMPLETED
     │              │
     │              ├──→ FAILED ──→ SCHEDULED (retry)
     │              │
     │              ├──→ FAILED_WITH_TERMINAL_ERROR
     │              │
     │              └──→ TIMED_OUT ──→ SCHEDULED (retry)
     │
     └──→ CANCELED (workflow terminated)
```

**Terminal states**: `COMPLETED`, `FAILED` (after retries exhausted), `FAILED_WITH_TERMINAL_ERROR`, `CANCELED`, `COMPLETED_WITH_ERRORS` (optional tasks).

Each transition is persisted before any subsequent action is taken.


## Timeout and retry configuration

Durability is configurable per task via the [task definition](../devguide/configuration/taskdef.md):

| Parameter | What it controls |
|---|---|
| `timeoutSeconds` | Maximum wall-clock time for the task to reach a terminal state. |
| `responseTimeoutSeconds` | Maximum time to wait for a worker status update before requeuing. |
| `pollTimeoutSeconds` | Maximum time a scheduled task waits to be polled before timeout. |
| `retryCount` | Number of retry attempts on failure or timeout. |
| `retryLogic` | `FIXED`, `EXPONENTIAL_BACKOFF`, or `LINEAR_BACKOFF`. |
| `retryDelaySeconds` | Base delay between retries. |
| `timeoutPolicy` | `RETRY`, `TIME_OUT_WF`, or `ALERT_ONLY`. |


## Workflow-level durability

Beyond individual tasks, Conductor provides workflow-level durability:

- **Compensation flows**: Configure a `failureWorkflow` that runs automatically when the main workflow fails, with full context (reason, failed task ID, workflow execution data).
- **Pause and resume**: Any running workflow can be paused via API and resumed later. State is fully preserved.
- **Restart, rerun, and retry**: See [Replay and recovery](#replay-and-recovery) below for full details on re-executing workflows.
- **Versioning**: Multiple workflow versions can run concurrently. Running executions are immutable against definition changes. Restarts can optionally use the latest definition.


## Replay and recovery

Every workflow execution is fully replayable. Conductor preserves the complete execution graph — inputs, outputs, and state for every task — so you can re-execute workflows at any time.

| Operation | What it does | When to use |
|-----------|-------------|-------------|
| **Restart** | Re-executes the entire workflow from the beginning | Definition changed, need a clean run |
| **Rerun** | Re-executes from a specific task, reusing outputs of prior tasks | Fix a task in the middle without re-running everything |
| **Retry** | Retries the last failed task and continues from that point | Transient failure, external dependency was down |

All three operations work on workflows in any terminal state (COMPLETED, FAILED, TIMED_OUT, TERMINATED) and are available indefinitely — Conductor preserves the full execution graph. Restart can optionally use the latest workflow definition, so you can fix a bug in the definition and replay immediately.


## Distributed consistency

In multi-node deployments, Conductor ensures consistency through:

- **Distributed locking**: Only one `decide` evaluation runs per workflow at a time across the cluster (pluggable: Zookeeper, Redis).
- **Fencing tokens**: Prevent stale updates from nodes with expired locks.
- **Persistent queues**: Task queues survive node failures. Configurable sharding strategies (round-robin or local-only) trade off distribution vs. consistency.

See the [deployment guide](../devguide/running/deploy.md#locking) for distributed lock configuration.


## What this means for your code

1. **Workers should be idempotent.** Because of at-least-once delivery, a task may execute more than once. Design workers to handle redelivery safely.
2. **You don't need to build retry logic.** Conductor handles retries, timeouts, and requeuing. Your worker just reports success or failure.
3. **Long-running processes are safe.** Use WAIT and HUMAN tasks for pauses that span minutes to days. State is durable across deploys.
4. **Definition changes are safe.** Update workflow definitions without affecting running executions. Roll out new versions gradually with zero downtime.
