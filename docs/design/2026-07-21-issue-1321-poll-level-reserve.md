# #1321 — Duplicate execution of async system tasks (queue-message reservation)

Issue: https://github.com/conductor-oss/conductor/issues/1321

## Summary

An async system task whose synchronous execution outlasts the queue's redelivery
window is executed a **second time in parallel** by another `system-task-worker`.
For `LLM_CHAT_COMPLETE` this means duplicate paid provider calls (observed: all 4
agent-loop turns billed twice). The fix keeps a task's queue message **present but
invisible** while it runs, so the scheduler's own repair logic no longer re-queues
a task that is legitimately in flight.

## Background: how a system task's message flows

`SystemTaskWorker.pollAndExecute` (per task-type queue), then `AsyncSystemTaskExecutor`:

1. `queueDAO.pop(queue)` → the message moves to the queue's *unacked* set (invisible).
2. `executionService.ackTaskReceived(taskId)` → `queueDAO.ack` → **the message is removed.**
3. `AsyncSystemTaskExecutor.execute` runs on a worker thread; the task's
   `start()`/`execute()` runs the work (for annotated `@WorkerTask` adapters, the
   provider call runs **synchronously** here).
4. Only in `AsyncSystemTaskExecutor`'s `finally` — *after* the work returns — is the
   message removed (terminal) or re-posted (`postpone`, non-terminal).

Between step 2 and step 4 a **running** task has **no message in the queue**.

## Root cause: a violated invariant + the sweeper's repair

`WorkflowSweeper` (`org.conductoross.conductor.core.execution.WorkflowSweeper`, on
by default) enforces on every sweep (~30s): *"every running task MUST be in the
queue."* Its repair re-pushes any repairable task whose message is missing:

```java
if (isTaskRepairable.test(task) && !queueDAO.containsMessage(queue, taskId)) {
    queueDAO.push(queue, taskId, task.getCallbackAfterSeconds());   // re-queue
}
```

For async system tasks `isTaskRepairable` is true in `SCHEDULED` **and**
`IN_PROGRESS`. So while the task runs (message removed at step 2, task still
non-terminal), a sweep sees "running task, no message" → **re-pushes it** → a
second worker pops it and runs the same task again → **duplicate**.

Remote worker tasks never hit this: their poll does **not** remove the message,
and their repair predicate requires `SCHEDULED`. Async system tasks have neither
guard. This is generic to **every** async system task — the message is briefly
absent for all of them; it is only *reliably* hit by long-running (LLM/A2A) tasks
whose window (minutes) is wider than the 30s sweep.

## Fix (what): don't remove the message at poll; reserve it for the run

Two small changes, both on the shared execution path (no per-task-type gating):

1. **`SystemTaskWorker` stops removing the message at poll.** The
   `executionService.ackTaskReceived(taskId)` call is dropped. The popped message
   stays in the queue (invisible while unacked), so a task that is about to run
   still *has* a message — `containsMessage` is true — and the sweeper's repair
   leaves it alone. (This removed the only use of `ExecutionService` in
   `SystemTaskWorker`, so that dependency is gone.)

2. **`AsyncSystemTaskExecutor` reserves the message for the run.** Before invoking
   `start()`/`execute()`, it extends the message's visibility to the task's
   `responseTimeoutSeconds`:

   ```java
   // before the SCHEDULED/IN_PROGRESS invocation:
   queueDAO.setUnackTimeout(queueName, taskId, responseTimeoutSeconds * 1000);
   ```

   The executor already loads the `TaskModel`, so `responseTimeoutSeconds` is in
   hand — no new config property and no extra read. It falls back to the default
   response timeout (`TaskDef.ONE_HOUR`, 3600s) when the task has none (e.g. an
   annotated task with no registered task def).

Together: the message is present from pop (repair can't re-push it — **no
ack→reserve race**), and invisible for `responseTimeout` (the unack sweep won't
redeliver it mid-run). The executor's `finally` still owns the outcome — it
`remove`s (terminal) or `postpone`s (non-terminal / worker-requested callback),
overriding the reservation — so normal completion, the async-complete flow, and
long-running `IN_PROGRESS + callbackAfterSeconds` re-invocation are all unchanged.

3. **A blocking overrun is timed out, not re-executed.** The reservation only lasts
   `responseTimeout`; if the actual run outlives it, the message *does* reappear.
   To avoid re-running it in parallel, the executor persists `startTime` before the
   first invocation (the status is left unchanged so a system task whose `start()`
   branches on `SCHEDULED` — e.g. `SUB_WORKFLOW` — still works), and on redelivery of a
   still-`SCHEDULED` task checks whether it started and has not responded within
   `responseTimeout`:

   ```java
   if (scheduled                                             // SCHEDULED only — see below
           && task.getStartTime() > 0
           && task.getUpdateTime() > 0                       // skip just-scheduled tasks
           && now - task.getUpdateTime() >= responseTimeoutMs) {
       task.setStatus(TIMED_OUT);   // don't invoke again — let retry/timeout policy decide
   }
   ```

   **Only `SCHEDULED` is handled here.** A blocking `start()` runs synchronously and
   never moves the task to `IN_PROGRESS`, so it stays `SCHEDULED` for its whole run —
   that is the only overrun the normal response-timeout path can't already see.
   `IN_PROGRESS` response-timeouts are owned by `DeciderService.isResponseTimedOut`
   (invoked from `decide()`), which budgets `responseTimeout + callbackAfterSeconds`.
   Applying this executor check to `IN_PROGRESS` tasks would fire *earlier* than
   `DeciderService` (it omits `callbackAfterSeconds`) and wrongly time out a task that
   is merely waiting for its next scheduled callback whenever
   `callbackAfterSeconds >= responseTimeout`.

   The `updateTime > 0` guard is also required: a task whose mapper sets `startTime` at
   scheduling (e.g. `JOIN`) has `updateTime == 0` until first persisted, so without it
   `now - 0` always exceeds the timeout and the task is wrongly timed out on its first
   poll.

   So a blocking run that exceeds `responseTimeout` is marked `TIMED_OUT` (retriable)
   and the retry/timeout policy creates a *new* attempt or fails the workflow — it is
   never re-run in parallel under the same taskId.

## Why `responseTimeout` (not a new property)

`responseTimeout` is exactly "how long the task is allowed to run", which is what
the reservation should cover, and it already exists per task. Adding a dedicated
lease property would duplicate it. It only needs a fallback (the platform default,
`TaskDef.ONE_HOUR`) for tasks with no configured timeout.

## Why not gate to one task type

The race is generic and the reservation is behavior-preserving: the executor's
`finally` always removes/re-posts the message once the task returns, so for a fast
task the reservation is immediately superseded. The one trade-off, uniform across
all tasks: a crash **mid-execute** leaves the message reserved and recovered after
`responseTimeout` instead of by the ~30s repair. That is bounded and acceptable,
and is the price of not letting repair fight in-flight tasks.

## Alternatives considered

- **Adapter-level reserve (PR #1367):** reserve inside `AnnotatedWorkflowSystemTask`.
  Covers only annotated tasks, and reserves *after* the poller's ack — leaving a
  small ack→reserve race (a sweep in that gap still re-queues). This approach
  removes the ack entirely, so there is no such window, and it covers all async
  system tasks.
- **Non-blocking poll model (#1359):** run the method off the worker thread and poll
  a future — eliminates the in-flight window, but a much larger change.

## Known limitations

- **Crash mid-execute** → recovery after `responseTimeout` (bounded), slower than
  repair's ~30s.
- **A blocking run that overruns `responseTimeout` is timed out and retried** per the
  task's policy — so a task whose `responseTimeout` is set shorter than its real work
  will time out repeatedly and eventually fail. Set a realistic `responseTimeout` for
  long tasks (e.g. LLM); absent one, the `TaskDef.ONE_HOUR` default applies. This is a
  de-facto 1-hour execution cap **only for blocking (`SCHEDULED`-throughout) tasks**;
  `IN_PROGRESS` waiters (HUMAN, WAIT) are unaffected — their timeout stays governed by
  `DeciderService.isResponseTimedOut`.
- **`pop` → reserve window.** The message is left unacked at the queue's default unack
  timeout between `pop` (poller thread) and `reserveInflightMessage` (executor thread).
  This is safe while a queue's semaphore permits equal its pool threads
  (`ExecutionConfig`): a popped id always has a free thread, so it reserves within
  milliseconds — far inside the default unack timeout. If a future change decouples
  in-flight permits from thread count (e.g. a per-type `permitCount`, PR #1204), a
  popped id could queue behind busy threads and the pre-reserve gap could exceed the
  unack timeout → redelivery → the very duplicate this fixes. In that case, reserve on
  the poller thread immediately after `pop` instead of in the executor.
- **Extra persistence on the schedule path.** Persisting `startTime` before `start()`
  adds one `executionDAOFacade.updateTask` (and its index write) per async system task,
  **once per task** (only on the first, `SCHEDULED` invocation — not per callback).
  Required so `startTime` is durable before a blocking `start()`.
- **Zombie late-write (#1322, not fixed here):** the original invocation of a
  timed-out task keeps running and, on completion, its `finally` still writes its
  result under the original taskId, which can overwrite the `TIMED_OUT`/retry state.
  Rejecting that late write is a separate follow-on (#1322).

## Testing

- `AsyncSystemTaskExecutorTest`:
  - a SCHEDULED task is reserved via
    `queueDAO.setUnackTimeout(queue, taskId, responseTimeout*1000)` before `start()`;
    a task with no `responseTimeout` falls back to `TaskDef.ONE_HOUR` (3600s).
  - a redelivered **SCHEDULED** task whose blocking `start()` outlived `responseTimeout`
    is marked `TIMED_OUT` and **not** re-invoked.
  - a **just-scheduled** task with `startTime` set but `updateTime == 0` (e.g. `JOIN`)
    is reserved and started, **not** timed out (the `updateTime > 0` guard).
  - an **`IN_PROGRESS`** task whose callback interval exceeds `responseTimeout` is
    reserved and `execute()`d, **not** timed out (the `SCHEDULED`-only gate — the
    response-timeout is left to `DeciderService.isResponseTimedOut`).
- `WorkflowSweeperTest`: an in-flight async system task (`isAsync`, not
  `asyncComplete`) whose queue message is **present** (`containsMessage == true`) is
  **not** re-pushed by repair — the regression guard for #202 / #630.
- `TestSystemTaskWorker`: the poll hands the task to the executor and does **not**
  `ack`/`remove` the message.
- End-to-end: the full `conductor-test-harness` suite passes, including the fork/join
  integration tests (`ForkJoinSyncModeIntegrationTest` et al.) that exercise `JOIN`
  through the async executor and would fail if a freshly-scheduled `JOIN` were timed
  out.
