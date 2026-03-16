---
description: "The exact failure contract for AI agents on Conductor — what happens when LLM calls fail, tools timeout, humans don't respond, callbacks arrive twice, branches partially complete, versions change mid-flight, and workers deploy during active executions."
---

# Failure semantics for AI agents

This page defines exactly what happens when things go wrong in an agent workflow. Not "Conductor is durable" — but the precise behavior under every failure scenario an agent can encounter.


## LLM task failure

**Scenario:** The `LLM_CHAT_COMPLETE` task calls an LLM provider and the call fails (rate limit, timeout, provider outage, malformed response).

**What happens:**

1. The task moves to `FAILED`.
2. Conductor checks the task's retry configuration (`retryCount`, `retryLogic`, `retryDelaySeconds`).
3. A new task execution is created with an incremented retry count.
4. The task is requeued after the configured delay.
5. If all retries are exhausted, the task moves to `FAILED` terminal state.
6. The workflow's failure handling kicks in: `failureWorkflow` runs if configured, or the workflow moves to `FAILED`.

**What is preserved:** The prompt, the error response, the retry count, and the timing of each attempt. You can inspect every failed attempt in the UI.

**What is NOT re-executed:** Nothing upstream. Only the failed LLM call retries. All previously completed tasks retain their outputs.

**Configuration:**

```json
{
  "name": "plan_action",
  "retryCount": 3,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 5,
  "responseTimeoutSeconds": 60
}
```

This retries the LLM call up to 3 times with exponential backoff (5s, 10s, 20s). If the LLM doesn't respond within 60 seconds, the task times out and retries.


## LLM returns malformed output

**Scenario:** The LLM responds, but the output is not valid JSON or doesn't match the expected schema (e.g., missing `action` field).

**What happens:**

The `LLM_CHAT_COMPLETE` task completes successfully — the LLM did respond. The malformed output propagates to the next task. What happens next depends on the downstream task:

- If the next task references `${plan.output.result.action}` and `action` doesn't exist, the task fails with an input resolution error.
- The task retries according to its retry policy.
- The LLM is **not** re-called (it already completed).

**How to handle it:** Add a `SWITCH` or `INLINE` task after the LLM call to validate the output before acting on it:

```json
{
  "name": "validate_plan",
  "taskReferenceName": "validate",
  "type": "INLINE",
  "inputParameters": {
    "plan": "${plan.output.result}",
    "evaluatorType": "graaljs",
    "expression": "(function() { var p = $.plan; if (!p || !p.action) { return {valid: false, error: 'Missing action field'}; } return {valid: true, plan: p}; })()"
  }
}
```

If validation fails, use a `SWITCH` to re-run the LLM with a corrective prompt, or fail the workflow.


## Tool call timeout

**Scenario:** A `CALL_MCP_TOOL` or `HTTP` task calls an external tool and the tool doesn't respond within the configured timeout.

**What happens:**

1. `responseTimeoutSeconds` fires. The task moves to `TIMED_OUT`.
2. If retries are configured, the task is retried. A new request is sent to the tool.
3. The original (timed-out) request may still be in flight. The tool may eventually process it.

**Critical implication:** The tool call may execute more than once. **Tool workers and MCP tools should be idempotent.** Use the task's `taskId` or a correlation ID as an idempotency key.

**What is preserved:** The timed-out attempt is recorded with its input, the timeout event, and the timing. Every retry attempt is separately recorded.


## Tool call fails after side effects

**Scenario:** A tool call sends an email, then the worker crashes before reporting completion. The task is retried, and the email is sent again.

**What happens:**

1. The worker polls the task, begins execution, and sends the email.
2. The worker crashes before calling `POST /api/tasks` to report completion.
3. `responseTimeoutSeconds` fires. The task moves to `TIMED_OUT`, then `SCHEDULED` (retry).
4. A new worker picks up the task and sends the email again.

**This is at-least-once delivery.** Conductor guarantees the task will execute at least once, but it may execute more than once if the worker fails after performing side effects.

**How to handle it:**

- Make side-effecting operations idempotent. Use an idempotency key (the `taskId` is unique per attempt).
- Use the task's `updateTime` to detect redelivery — if the task was already processed, skip the side effect.
- For irreversible side effects, configure a `failureWorkflow` with compensation tasks.


## Human never responds

**Scenario:** A `HUMAN` task is waiting for approval, and nobody responds. Hours pass. Days pass.

**What happens:**

The `HUMAN` task remains `IN_PROGRESS` in durable storage indefinitely. It does not timeout unless you explicitly configure `timeoutSeconds` on the task definition.

- The workflow consumes no compute resources while waiting. No polling, no timers, no threads.
- The task survives server restarts, deploys, and infrastructure changes.
- The task is visible in the UI and queryable via API.

**If you want a timeout:** Set `timeoutSeconds` and `timeoutPolicy` on the task definition:

```json
{
  "name": "human_approval",
  "timeoutSeconds": 86400,
  "timeoutPolicy": "TIME_OUT_WF"
}
```

This times out after 24 hours and fails the workflow. Alternatively, use `timeoutPolicy: "ALERT_ONLY"` to log a timeout without failing.

**If you want escalation:** Use a parallel `WAIT` + `HUMAN` pattern:

```json
{
  "type": "FORK",
  "forkTasks": [
    [{"type": "HUMAN", "taskReferenceName": "approval"}],
    [{"type": "WAIT", "inputParameters": {"duration": "4 hours"}},
     {"type": "LLM_CHAT_COMPLETE", "taskReferenceName": "escalation_notify"}]
  ]
}
```


## Callback delivered twice

**Scenario:** An external system calls the Task Update API to complete a `HUMAN` task, but the network is flaky and the call is retried. Conductor receives the completion signal twice.

**What happens:**

The first call moves the task from `IN_PROGRESS` to `COMPLETED` and advances the workflow. The second call arrives for a task that is already in a terminal state.

- Conductor rejects the update. The task is already `COMPLETED`.
- No duplicate execution occurs. The workflow does not advance twice.
- The second call returns an error indicating the task is already in a terminal state.

**This is safe by default.** Conductor's task state machine enforces that a task can only transition to a terminal state once. Duplicate callbacks are harmless.


## Branch partially completes in a FORK/JOIN

**Scenario:** A `FORK/JOIN` runs three parallel branches. Branch 1 completes. Branch 2 fails. Branch 3 is still running.

**What happens:**

1. Branch 2 fails. Its task moves to `FAILED` and retries according to its retry policy.
2. Branch 3 continues executing independently.
3. The `JOIN` task waits for all branches to reach a terminal state.
4. If branch 2 exhausts its retries and moves to terminal `FAILED`, the `JOIN` task fails.
5. Branch 3 may still be running — it is not automatically canceled (unless the workflow is terminated).
6. The workflow's failure handling kicks in.

**What is preserved:** Each branch's completed tasks retain their outputs. If you retry the workflow from the failed task, only the failed branch re-executes. Successful branches are not re-run.


## Workflow definition changes mid-flight

**Scenario:** You update the workflow definition (add a task, change a parameter) while executions are running.

**What happens:**

Running executions are **not affected**. Each execution uses an immutable snapshot of the definition taken at start time. The snapshot is embedded in the execution record.

- New executions use the updated definition.
- Running executions continue with their original definition.
- You can have multiple versions running concurrently.

**If you want to apply the new definition:** Use [restart with latest definitions](../../architecture/durable-execution.md#replay--recovery). This re-executes the workflow from the beginning using the updated definition.


## Worker deploy during active executions

**Scenario:** You deploy a new version of your worker code. Old worker instances are shut down, new instances start up. Tasks are in-flight.

**What happens:**

1. Old workers are shut down. Tasks they were processing are abandoned.
2. `responseTimeoutSeconds` fires for abandoned tasks. Tasks move to `TIMED_OUT`, then `SCHEDULED` (retry).
3. New worker instances poll for tasks and pick up the requeued tasks.
4. Execution continues.

**Window of vulnerability:** The time between old worker shutdown and `responseTimeoutSeconds` firing. During this window, the task appears `IN_PROGRESS` but no worker is processing it.

**How to minimize impact:**

- Keep `responseTimeoutSeconds` short (10-60 seconds for most tasks).
- Use graceful shutdown in your workers — complete in-progress tasks before stopping.
- For the Conductor server itself: the sweeper service re-evaluates in-progress workflows on startup and requeues stalled tasks.

**What is never lost:** Completed task outputs. The workflow state. The execution history. Only the in-progress task is affected, and it is automatically retried.


## Dynamic task type no longer exists

**Scenario:** A `DYNAMIC` task resolves to a task type based on LLM output. The LLM returns a task name that doesn't exist (not registered, was deleted, or is misspelled).

**What happens:**

The `DYNAMIC` task fails with a resolution error — the specified task type cannot be found. The task moves to `FAILED` and retries according to its retry policy.

**How to handle it:** Validate the LLM output before the `DYNAMIC` task. Use an `INLINE` or `SWITCH` task to check that the resolved task name is in a known allowlist.


## Network partition between worker and server

**Scenario:** A worker is executing a task (e.g., an LLM call). A network partition occurs. The worker completes the task but cannot report the result to the Conductor server.

**What happens:**

1. The worker completes the LLM call and receives the response.
2. The worker attempts to report `COMPLETED` to the server. The request fails due to the network partition.
3. The worker retries the status update (SDK-level retry).
4. If the partition persists longer than `responseTimeoutSeconds`, the server marks the task as `TIMED_OUT` and requeues it.
5. When the partition heals, a worker (possibly the same one) picks up the task and re-executes the LLM call.

**Tokens are consumed twice in this scenario.** The original LLM call succeeded but the result was lost. This is the cost of at-least-once delivery. For long-running or expensive LLM calls, consider implementing client-side caching in your worker to avoid re-execution.


## Long-running agent loops over hours/days/weeks

**Scenario:** An autonomous agent loop runs for hours or days, with `WAIT` pauses, `HUMAN` approvals, and periodic LLM calls.

**What happens:**

This is a normal operating mode for Conductor. The workflow stays `RUNNING` with individual tasks in `IN_PROGRESS` (for active work) or `COMPLETED` (for finished steps).

- `WAIT` tasks consume no resources. The durable timer fires when the duration elapses, even across deploys.
- `HUMAN` tasks consume no resources. They persist until the signal arrives.
- The `DO_WHILE` loop counter and all intermediate state survive indefinitely.
- Server restarts, worker deploys, and infrastructure changes do not affect the execution.

**Practical limits:**

- Execution data grows linearly with the number of completed tasks. For very long loops (thousands of iterations), consider offloading large payloads to external storage and storing only pointers in task output. See [external payload storage](../advanced/externalpayloadstorage.md).
- Workflow-level `timeoutSeconds` applies to the total execution. Set it high enough for your expected duration, or omit it for unlimited execution time.


## Summary: the failure contract

| Failure | What Conductor does | What you should do |
|---------|--------------------|--------------------|
| LLM call fails | Retries with configured backoff | Set retry policy on task definition |
| LLM returns bad output | Downstream task fails on input resolution | Add a validation step after LLM calls |
| Tool call times out | Retries after `responseTimeoutSeconds` | Make tools idempotent |
| Tool call has side effects, then crashes | Retries — side effect may execute twice | Use idempotency keys |
| Human never responds | Task stays `IN_PROGRESS` forever | Set `timeoutSeconds` or build escalation |
| Duplicate callback | Second call rejected, no duplicate execution | Safe by default |
| FORK branch fails | JOIN waits for all branches; workflow fails if branch exhausts retries | Configure retry policies per branch |
| Definition changes while running | Running executions unaffected (snapshot) | Use restart to apply new definitions |
| Worker deploy | In-flight tasks requeued after response timeout | Keep response timeouts short; use graceful shutdown |
| Dynamic task doesn't exist | Task fails, retries | Validate LLM output before DYNAMIC resolution |
| Network partition | Task requeued after timeout, may re-execute | Make workers idempotent; consider client-side caching |
| Multi-day execution | Normal operation, fully durable | Offload large payloads; set appropriate timeouts |


## Next steps

- **[Production Agent Architecture](production-agent-architecture.md)** — The canonical end-to-end agent pattern.
- **[Durable Execution Semantics](../../architecture/durable-execution.md)** — The full persistence model, task state machine, and retry configuration.
- **[Why Conductor for Agents](why-conductor.md)** — What Conductor gives you out of the box for agentic workflows.
- **[Token Efficiency](token-efficiency.md)** — How durable execution saves tokens across all these failure scenarios.
