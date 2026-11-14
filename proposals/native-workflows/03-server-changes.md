# 03. Server changes

Each change: what existed on `main` with the code that does it, what changed, the algorithm, edge
cases, configuration, metrics, size. B changes are needed for the flat graph; S changes are
standing items the SDK reaches later. How the work was staged is in [05-plan.md](05-plan.md).

## Where `DeciderService` and `WorkflowExecutorOps` change

| Method | Change | Where below |
|---|---|---|
| `DeciderService.startWorkflow` | a native definition with no tasks schedules nothing, instead of terminating with "No tasks found to be executed"; the bootstrap continuation appends the first entries | B2 |
| `DeciderService.getNextTask`, `WorkflowDef.getNextTask`, `WorkflowTask.next` | none; verified to walk appended entries, including inside fork branches | B2 |
| `DeciderService.checkForWorkflowCompletion` | returns false for a native workflow: the execution ends only through the continuation that carries the result | B2 |
| `DeciderService.retry` | retries as for any task; lane tasks are `optional`, so exhausted retries do not terminate the workflow, the failed task becomes COMPLETED_WITH_ERRORS as for any optional task, and for native workflows its output records how it failed under `conductor.failedStatus`; a retried attempt copies the marker reset to `WAITING` | B2 |
| `MetadataMapperService.populateTaskDefinitions` | an overload takes the timeout policy for a task with no registered definition; a native fragment passes `RETRY`, so a worker that dies holding a native task is retried. Standard workflows keep the global default, `TIME_OUT_WF` | B2 |
| `TaskMapperContext.createTaskModel` | copies the entry's `metadata.continuation` onto the `TaskModel` as a `WAITING` `TaskContinuation` | B1 |
| `WorkflowExecutorOps.startWorkflow`, `restart` | `NativeContinuationService.initialize(workflow)` sets up `nativeState` | B1, B7 |
| `WorkflowExecutorOps.updateTask` | a continuation's completion (a synthetic id, or a task with a marker and `protocol` in the result's output) goes to `completeContinuation` under the execution lock, and the decide it triggers runs in that same acquisition; a stale one is refused with 409 and never applied to the task. A task update takes no lock and is saved only if the task's stored status is still the one read | B2 |
| `WorkflowExecutorOps.decide(WorkflowModel)` | after saving updated tasks, `repairAndWake` makes a `WAITING` marker on an evaluated terminal task `RUNNABLE`, repairs markers and offers the execution's slot to the next continuation (`offer`); touched tasks and the workflow row are saved, then the offers are pushed; the decider-queue postpone is capped at `workflowOffsetTimeout` and always written while a marker is `RUNNABLE` or `LEASED` (`NativeContinuationService.needsPromptRevisit`) | B2 |
| `SwitchTaskMapper`, `Switch`, `NativeCodeEvaluator` | a new evaluator, `native-code`, reads the case the code took (as `value-param` reads a parameter); every `SWITCH` a native workflow draws carries it, with `switchCaseValue` already fixed to the taken case, and fragment validation refuses any other evaluator on one. The mapper records `outputData.selectedCase` and `evaluationResult` as for any switch | B2 |
| `WorkflowDef` | none but the task-list validation (below); `NativeContinuationService.insertAfter`, `previousTask` and `removeAll` edit an execution's own definition at any depth (`forkTasks`, `decisionCases`, `defaultCase`, `loopOver`) | B2 |
| `ExecutionService.poll` | a synthetic id, or a message naming a task with a marker of another type than the one polled, goes to `NativeContinuationService.lease` instead of the task path; `lease` hands out what a decide offered, with no lock and no write | B2 |
| `WorkflowExecutor.decide(workflowId, change)` | applies a change to the workflow, read with its tasks, and decides it in one acquisition of the execution lock; the message endpoint pushes with it | S2 |
| `NativeContinuationService.queueName`, `ExecutorUtils.activeDomain` | the shared queue of the code type, unless the workflow's `taskToDomain` names the code type or `*`; `activeDomain` is the list resolution `WorkflowExecutorOps.getActiveDomain` also uses | B2 |
| `WorkflowExecutorOps.rerunWF` | for a native execution, `NativeContinuationService.planRerun` decides which tasks go instead of the later `seq` rule, then `NativeContinuationService.adjustRerun`, whose reopened JOINs are queued only after the rerun task is saved; with no task, `startNativeOver` (the reset restart uses); a rerun from a native `SWITCH`, `FORK` or `JOIN` task is refused with a 400 | B7 |
| `WorkflowExecutorOps.reopenLoopHolding` | a rerun from a task inside a loop reopens the innermost `DO_WHILE` holding it at the task's iteration; standard workflows too (`DECIDER_BUGS.md` item 5) | B7 |
| `NativeContinuationService.complete` | stamps each appended entry with `metadata.appendedBy` (`lane#n`), which a rerun uses to undo a continuation's entries | B7 |
| `WorkflowExecutorOps.retry(WorkflowModel)` | `retryNative`: rerun from `failedTaskId`, or run the main lane's last continuation again (`NativeContinuationService.undoThrow`, `rearmLastMain`) | B7 |
| `WorkflowExecutorOps.skipTaskFromWorkflow`, `pauseWorkflow`, `resumeWorkflow`, `terminateWorkflow`, `cancelNonTerminalTasks` | none | B7 |
| `Join.execute` | live targets for a native JOIN with empty `joinOn`; `lanesSettled` on the markers | B3 |
| `WorkflowSweeper` | skips the "RUNNING with no running task" reset for a native workflow: the running work may be a queued or leased continuation | B2 |
| `WorkflowMessageQueueResource.pushMessage` | pushes inside `WorkflowExecutor.decide(workflowId, change)`, for every workflow; that decide wakes a waiting `PULL_WORKFLOW_MESSAGES` task or a native workflow's handler lane (`repairAndWake`) | S2 |
| `MetadataServiceImpl.checkNativeWorkflow`, `WorkflowDef` task-list validation | an empty task list is allowed when `metadata.native` is present (the SDK now registers the graph it discovered from the code, with inline task definitions, so the list is normally not empty); native definitions refused when `conductor.app.nativeWorkflow.enabled=false` | B1 |
| `NativeContinuationService.initialize` | clears the execution's own copy of the definition's `tasks` when the registered definition carries discovered entries, so the execution starts empty and holds only what the code calls | B1 |
| `WorkflowExecutor` interface | one new method, `decide(workflowId, change)` (above) | |

## B1. Engine state: `TaskContinuation`, `NativeState`, the skeleton

### Before

Task types are the `TaskType` enum; a definition task becomes task models through the mapper for
its type, and `TaskMapperContext.createTaskModel` copies the entry (`WorkflowTask`, with its
`metadata`) onto the model. A definition needs at least one task. Nothing on a task or a workflow
describes code waiting on it.

### Change

1. `TaskContinuation` in `common/.../metadata/tasks/`, and a `continuation` field on `Task` and
   `TaskModel` (`TaskModel.copy` carries it). Fields: `lane`, `n`, `state` (`WAITING`, `RUNNABLE`,
   `LEASED`, `APPLIED`), `epoch`, `worker`, `queue`, `queuedAt`, `leasedAt`, `appliedAt`,
   `attempts`, `deferred`, `step`, `recorded`, `resumedWith`, `done`, `result`, `error`.
   `reset()` returns a fresh `WAITING` marker for the same lane and `n`, for a retried attempt.
2. `NativeState` in `common/.../run/`, and a `nativeState` field on `Workflow` and `WorkflowModel`.
   Fields: `protocol`, `codeType`, `continuationTimeoutSeconds`, `step`, `activeContinuation`, `activeUntil`,
   `bootstrap` and `handlers` (both `TaskContinuation`), `replies`, `seen`. Null for other
   workflows.
3. `TaskMapperContext.createTaskModel`: when the entry's `metadata.continuation` is a map with
   `lane` and a numeric `n`, set `taskModel.continuation = new TaskContinuation(lane, n)`.
4. `NativeContinuationService.initialize(workflow)` (called by `startWorkflow` when
   `NativeContinuationService.isNative(workflowDefinition)`, and by `restart`): first, if the
   execution's copy of the definition has tasks (the graph the SDK discovered from the code at
   registration, 02 §2), replace them with an empty list, so the execution starts empty and its
   copy holds only what the code calls; then protocol, code type (default `<name>.v<version>.code`)
   and timeout (default 30) from `metadata.native`; `bootstrap` = `main#1` `RUNNABLE` with
   `queuedAt` now; `handlers` = `handlers#1` `WAITING` when `metadata.native.handlers` is true.
5. `WorkflowDef`: the "at least one task" rule is relaxed when `metadata` has `native`.
   `MetadataServiceImpl.checkNativeWorkflow` refuses a native definition when
   `conductor.app.nativeWorkflow.enabled` is false, and logs when it declares handlers while
   `conductor.workflow-message-queue.enabled` is false. A discovered task list is validated like
   any definition's: its `SIMPLE` entries carry inline task definitions for that reason.
6. `TaskType` has no `CODE` or `RECORD`; no engine-only task types exist.

Size: 1 day.

## B2. Driving the code: wake, lease, complete

### Before

`DeciderService.decide` walks pending tasks; when a task is terminal and not yet executed it marks
it executed and asks `getNextTask(workflow, task)`, which calls `WorkflowDef.getNextTask` and, for
container tasks, `WorkflowTask.next`, reading the live lists. `ExecutionService.poll` pops ids from
`QueueUtils.getQueueName(taskType, domain)`, drops ids of terminal tasks, sets the task
`IN_PROGRESS` and hands it out. `WorkflowExecutorOps.updateTask` applies a `TaskResult` to a task
and decides.

### Change: wake-up

1. `DeciderService.decide` evaluates the terminal task the code paused on (`executed` true) as
   for any task; it does not touch the marker.
2. `WorkflowExecutorOps.decide(WorkflowModel)`, after `updateTasks`, for native workflows:
   `repairAndWake(workflow)` returns the tasks it touched, whether the workflow row changed, and
   the message ids to push: a `WAITING` marker on a terminal, executed task becomes `RUNNABLE`
   with `queuedAt` now (in the decide that evaluated the task, or a later one after a crash
   between the evaluation and the save), and a `WAITING` handler marker becomes `RUNNABLE` when
   the message queue holds messages (a crash between the message push and the wake). Touched tasks are saved, the workflow row is saved if
   dirty, then `push` sends each id with `queueDAO.pushIfNotExists` to `queueName(workflow)`.
   Saved before pushed, pushed idempotently, so a `RUNNABLE` marker in storage always has a message
   or a repair coming, and a marker has at most one outstanding message however many decides run.
3. `queueName(workflow)`: `QueueUtils.getQueueName(codeType, domain, null, null)`. With no
   `taskToDomain` entry for the code type or `*`, `domain` is null: the shared queue, so any worker
   can run any continuation. With an entry, `ExecutorUtils.activeDomain` picks the first domain
   whose poll data for the code type (`getTaskPollDataByDomain`) is fresher than
   `activeWorkerLastPollTimeout` (10 s), else the last entry, `NO_DOMAIN` meaning the shared queue.
   `WorkflowExecutorOps.getActiveDomain` calls the same method for tasks. Domains are resolved at
   push time, so a message pushed just after every worker of a domain died waits for the next
   decider pass (up to about 30 s), when `repairAndWake` re-pushes it to the queue now active.
4. `WorkflowSweeper`: the "RUNNING with no running task, reset the last task" repair is skipped
   when `nativeState` is set; the sweeper's `decide` runs `repairAndWake` instead.
5. `WorkflowExecutorOps.decide`, where it postpones the workflow's decider-queue entry:
   `ExecutorUtils.computePostpone` derives the postpone from the in-progress tasks (remaining
   response timeout, wait deadline, poll timeout), capped at `maxPostponeDurationSeconds`
   (3600 s). While `NativeContinuationService.needsPromptRevisit(workflow)` is true (a
   `RUNNABLE` or `LEASED` marker on a task, on the bootstrap or on the handler lane, or a `SCHEDULED` task whose queue message may have been lost to a storage failure), the revisit is set to at most `workflowOffsetTimeout` (30 s) with `setUnackTimeoutIfShorter`, which replaces a longer postpone but never extends one, so that a busy workflow's constant decides cannot keep the sweeper away,
   so the next decide, and `repairAndWake` with it, comes soon enough to re-push a wake-up that
   sits in the queue of a domain whose workers are gone, or to re-offer a lease whose holder died.
   Found by the chaos run under the earlier per-worker routing: a worker was killed right after its
   continuation was queued to its own domain queue, the sweeper's decide had postponed the workflow
   by an hour, and the message sat in the dead worker's queue until then.

### Change: offer (in a decide) and hand-out (the poll)

Only a decide gives out a continuation, under the lock it already takes.
`NativeContinuationService.repairAndWake`, on every decide:

1. Repairs: a `WAITING` marker on an evaluated task becomes `RUNNABLE`; the handler marker becomes
   `RUNNABLE` when `workflowMessageQueueDAO.size(workflowId) > 0` (this is how a pushed message
   wakes the handler lane).
2. The slot holder (`nativeState.activeContinuation`): if its marker is `LEASED`, its offer is
   pushed again (`pushIfNotExists`, which repairs a lost message), unless it was offered in a queue
   other than the one resolved now and `activeUntil` has passed (its domain's workers went away):
   then the message is removed from the old queue and it is offered again. A holder whose marker is no longer `LEASED` frees the slot.
3. With the slot free: the `RUNNABLE` marker (or a `LEASED` one that does not hold the slot, left by
   a crash) with the oldest `queuedAt` is offered, skipping a marker deferred `waiting for handlers`
   while the handler lane is `RUNNABLE` or `LEASED`.

`offer(workflow, marker, messageId, delaySeconds)`: `activeContinuation` the message id,
`activeUntil` delivery plus `continuationTimeoutSeconds`; marker `LEASED`, `epoch + 1`, `worker`
null, `leasedAt` the delivery time, `queue` (`queueName(workflow)`), `attempts + 1`. `deferred` is
kept until the continuation applies. The decide saves the task and the workflow row, then pushes
each `Offer(messageId, queue, delaySeconds)` with `pushIfNotExists`.

`ExecutionService.poll`, in the loop over popped ids, before the terminal-task check: if the id is
synthetic (`contains("|")`), or names a task with a marker of another type than the type being
polled (the task the code paused on), `lease(queueName, id, task, workerId)` with the task it read,
and add the returned task if any; `continue`. `lease` takes no lock and writes nothing:

1. Resolve the workflow id: from the synthetic id, or from the task.
2. Load the workflow. No `nativeState`, or terminal: remove the message, return null.
3. Find the marker: the task's `continuation`, or `nativeState.bootstrap` / `nativeState.handlers`.
   Unless it is `LEASED`, the message is `activeContinuation` and the queue is the marker's
   `queue`, the message is stale (an old wake-up, or a duplicate in another domain's queue):
   remove it, return null.
4. `setUnackTimeout(queue, id, continuationTimeoutSeconds)`.
5. Return the task's `Task`, or for a synthetic id `syntheticTask(...)`: `taskType` and
   `taskDefName` the code type, `referenceTaskName` the lane, `workflowInstanceId`,
   `workflowType`, `correlationId`, `workflowPriority`, `status IN_PROGRESS`, `scheduledTime`
   `queuedAt`, `startTime` `leasedAt`, `workerId` the poller, `domain` (the queue's prefix), the
   marker, and for `handlers` `inputData.messages`, up to 10 claimed with
   `workflowMessageQueueDAO.claim(workflowId, 10, "handlers#" + n, n)`.

The awaited task's status, worker id, poll count and start time are not touched. Concurrency and
rate limits, which apply to the awaited task's definition, are not consulted for a continuation. A
worker that dies leaves the message to the queue's redelivery after the continuation timeout; the
next worker runs the same offer (same epoch), and the first completion applies while the other is
refused because the marker is no longer `LEASED`.

### Change: complete (the update)

`WorkflowExecutorOps.updateTask`: a synthetic id goes to `completeContinuation(result, null)`, and
a task with a marker whose result's `outputData` has `protocol` to `completeContinuation(result,
task)`, both through `completeContinuationLocked`: the completion changes the workflow row, which
only a decide writes, so it takes the execution lock (busy: `TransientException`, the worker
retries), applies the completion, decides, and releases the lock, so the completion and its decide
share one acquisition. A `ConflictException` (a stale completion, such as one that arrives after a
rerun reset the marker) is rethrown after the decide; it never reaches the task. Everything else is
an ordinary task update, so a worker's report of the awaited task itself never collides with the
continuation's completion. A task update takes no lock: it is saved only if the task's stored
status is still the one it read (`ExecutionDAO.updateTaskIfStatus`), so a late completion cannot
overwrite a timeout saved meanwhile.

`completeContinuation` loads the workflow with its tasks, ignores the completion if the workflow is
terminal, calls `NativeContinuationService.complete(workflow, awaited, result)`, then: saves the
workflow row, then the touched tasks (the `workflow-saved-last` mutant reverses the order for
the bench); acknowledges the queue message (`queueDAO.remove(ackQueue, ackId)`); on `deferred`
offers the same continuation again, saved with the rest and pushed with the backoff as the delay,
unless `Completion.waitsForHandlers` is set (pushed after the acknowledgement, which would otherwise
delete it: the offer reuses the message id); on `finished` (the code raised, or its fragment was
rejected) calls `terminate(workflow, new TerminateWorkflowException(reason, FAILED, causeTask))`.
A code that returned is completed by the decider (`checkForWorkflowCompletion`). It returns a
stand-in `TaskModel` of the code type, `COMPLETED`, with the ack queue's domain, so `update-v2`
polls the continuation queue for this worker's next continuation.

`NativeContinuationService.complete`, in memory, under the lock the caller holds (the completing
worker is recorded as the marker's `worker`):

1. **Validate the lease.** The marker must be `LEASED` with the output's `n` and `epoch`
   (the `ignore-epoch` mutant skips the epoch check). Otherwise `native_stale_completion` and
   `ConflictException` (HTTP 409).
2. **Status.** `FAILED_WITH_TERMINAL_ERROR` (replay divergence) defers with `deferred`
   "diverged: <reason>"; the workflow keeps running. Any other non-`COMPLETED` status defers with
   "worker reported <status>". Both set the marker `RUNNABLE`, `queuedAt` now, `worker` null,
   `native_completion_deferred`, and are offered again after 1, 2, 4, ... seconds by `attempts`,
   at most 60.
3. **Protocol.** `protocol` must be the number 2 (`RejectedFragmentException`, reason `protocol`).
4. **Variable conflict.** `variableConflict`: for each variable the completion changed (differs
   from `resumedWith`), if the current value differs from `resumedWith`, defer with "variable 'x'
   was changed by another completion". the `no-variable-fencing` mutant disables it.
5. **Completion guard.** `done` without `error` on the main lane: `pendingMessages` defers while
   the handler marker is `LEASED` or `RUNNABLE`, or `workflowMessageQueueDAO.size(workflowId) > 0`.
   The reason is prefixed `waiting for handlers: ` and `Completion.waitsForHandlers` is set, so the
   marker is not re-queued: competing for the slot, it could win every time and the signal would
   never run. `repairAndWake` skips such a marker while the handler marker is `RUNNABLE` or `LEASED`
   and pushes it once that lane has settled. the `no-completion-deferral` mutant disables it.
6. **Fragment.** Everything is checked before anything is written, so a rejected completion
   changes neither the definition nor the variables. `readFragment` (must deserialize to
   `List<WorkflowTask>`), split into runs by `metadata.after` (`runs`), `validateFragment` (02 §8:
   the size counts nested entries, and each run's anchor must be in the definition or drawn by an
   earlier run of the same fragment; one it does not find is a `RejectedFragmentException` with
   reason `fragment`), and the variables' size against
   `maxWorkflowVariablesPayloadSizeThreshold`. Then `populateTaskDefinitions` and each run goes in:
   at the end of the list for the bootstrap, else `insertAfter(definition, anchor, entries)`. The
   anchor is the run's first entry's `metadata.after` when set (code that left a `switchOn` case
   appends after the switch, not inside the case where it paused), else the awaited task's
   reference; `insertAfter` finds it at any depth (`forkTasks`, `decisionCases`, `defaultCase`,
   `loopOver`). Preview entries inside a `SWITCH`'s untaken cases are validated and counted like
   every other nested entry; nothing marks them special on the server.
7. **Apply the rest.** The variables the completion changed (those that differ from
   `resumedWith`), `replies` and `seen` replace `nativeState`'s, and
   `release` completes each `releases[].ref` task that is not terminal with `COMPLETED`, `endTime`,
   `executed false` and the given `output`.
8. **Record.** Marker `APPLIED`, `step = ++nativeState.step`, `recorded`, `resumedWith`, `done`,
   `result`, `error`, `appliedAt`, `deferred` null. For the handler lane, `nativeState.handlers`
   becomes a new `WAITING` marker for `n + 1` carrying the same `step`.
9. **Re-evaluate.** The awaited task gets `executed = false` and is returned among the touched
   tasks; the next decide finds the appended entries after it. For the bootstrap, the start path
   schedules the first top-level entry.
10. **Finish.** `done` with `error` on the main lane: `finished`, `FAILED` with `error.message` as
    the reason and the task named by `error.ref` as the cause. `done` without `error` finishes
    nothing here: the decider completes the workflow with `result` as the output once everything
    the fragment appended has run.

A `RejectedFragmentException` at any step records the marker `APPLIED` with `done` and
`error.message`, counts `native_fragment_rejected` with the reason, and finishes the workflow
`FAILED` with the message.

### Edge cases

- `checkForWorkflowCompletion` for a native workflow requires the main lane's last marker to be
  applied, `done` and without an error, every task to be terminal, and no task's latest attempt to
  have a marker not yet applied (`hasUnappliedLane`), so a lane nothing joined finishes first. Code that returned without
  calling anything has no tasks to wait for and is complete; a definition with every task terminal
  while a marker is runnable or leased is not.
- `isLazyEvaluateWorkflow` needs no exemption: a task inside a fork whose JOIN has `joinOn: []`
  is not listed by any join, so its completion decides at once.
- A crash between saving the workflow row and the task row leaves a `LEASED` marker whose fragment
  is already in the definition. The lease expires, the continuation runs again, and the SDK finds
  its entries already in the definition (a call whose reference name exists pauses on it or reads
  its result). `<!-- TODO: verify against live server -->`
- Two continuations of one execution: a decide offers one at a time (`activeContinuation`);
  `complete` frees the slot on every outcome and the decide that follows offers the next.
- Two workers with the same continuation: the queue hands a message to one poller at a time and
  redelivers it after the continuation timeout. Both run the same offer; the first completion
  applies, the second finds the marker `APPLIED` (or deferred and offered again at a new epoch) and
  is refused with 409. A completion from an earlier offer fails the epoch check.
- No native path takes the execution lock outside a decide: the poll takes none and writes
  nothing; a completion and a pushed message are applied inside the decide they trigger.
- The definition in the workflow row grows by one entry per call; every append rewrites the row.
  `maxDefinitionTasks` fails cleanly with a reason that names the limit. Raising the limit is the
  large-loops round. Markers add well under 100 bytes to a task; `nativeState` never grows with
  the execution.
- Lock lease: `complete` runs inside a decide, under the execution lock (`lockLeaseTime`, 60
  seconds). Validation
  of a 500-task fragment is milliseconds.
- Incoming fragments count against `maxTaskOutputPayloadSizeThreshold`, and external payload
  storage applies as usual to the result, not to the marker.

### Configuration

`ConductorProperties.NativeWorkflow`, prefix `conductor.app.nativeWorkflow`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | true | refuse native definitions when false |
| `maxFragmentBytes` | 1 MB | serialized fragment |
| `maxDefinitionTasks` | 5000 | nested count of the execution's definition after append |

### Metrics

Every metric below is tagged with `workflowName`. Prometheus names add the `_total` suffix to
counters.

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `native_completion_deferred` | counter | `reason` | a continuation will run again: `diverged`, `worker_failed`, `variable_conflict`, `waiting_for_handlers` |
| `native_fragment_rejected` | counter | `reason` | a fragment was refused, or the code diverged (`diverged`) |
| `native_stale_completion` | counter | | a completion arrived for a lease that had ended |
| `workflow_messages_discarded` | counter | | an execution ended with undelivered messages, which were dropped |

Alerting on held executions, in PromQL:

- Something is held right now: `sum(rate(native_completion_deferred_total{reason="diverged"}[5m])) > 0`.
  A held execution retries at least once a minute while a worker polls its code type, so this
  stays above zero until the hold ends and needs no cluster-wide state.

There is no gauge of the number of held executions. Each server only sees the holds it processes,
and counting them across the cluster would mean scanning running executions. With no worker
polling the code type, a held execution stops retrying and these signals go quiet; then
`task_queue_depth{taskType="<name>.v<version>.code"}` stays above zero where the code type has a
task definition; it counts the shared queue, not domain queues. The UI shows how long a held
execution has waited (the marker's `queuedAt`, kept from the start of the hold).

Size: 5 days including unit tests; integration specs are in B3's and 04's estimates.

## B3. `JOIN` with live targets

### Before

`JoinTaskMapper` copies `workflowTask.getJoinOn()` into the join task's input at scheduling.
`ForkJoinTaskMapper.getMappedTasks` schedules the `JOIN` together with the fork and requires the
next definition task to be a `JOIN`. `Join.execute` reads `joinOn` from the input, treats the join
as complete when every listed task is terminal, and applies `optional` and `permissive` per listed
task. `Join` is asynchronous, re-evaluated by the system task worker.

### Change

In `Join.execute`, when the input's `joinOn` is empty, the join is not a loop-over task and the
workflow is native: `liveJoinTargets` computes the references of the last entry of each
`forkTasks` list of the preceding fork in the current definition; when that entry is a finished
`SWITCH`, the last entry of the case it took instead, at any depth. If the fork is not in the
definition the JOIN is reading (a copy loaded before the decide that appended it was saved),
return false and wait for the next evaluation. Return false while
`NativeContinuationService.lanesSettled(workflow, joinOn)` is false: every target must be
terminal and carry no marker or an `APPLIED` marker with `done`. the `join-ignores-lanes` mutant
skips the check. Then proceed unchanged.

Two more forms exist for lanes the code does not wait for at once. A JOIN with
`metadata.detached: true` completes immediately. A JOIN with `metadata.waitFor` targets the named
lanes instead of the preceding fork, and can sit anywhere after it; it waits for the next
evaluation when a lane is not in the definition it is reading. All targets include the branch ends
of any fork a branch started. Native JOINs do not use `joinMode: SYNC`: a JOIN re-evaluated without
pause takes the workflow lock continuously and refuses task updates. They are checked continuously
for the first `systemTaskPostponeThreshold` evaluations and then every second, instead of backing
off to the system task callback duration.

### Edge cases

- A branch whose last task carries a `WAITING`, `RUNNABLE` or `LEASED` marker: the lane has more
  to say; not settled.
- A branch whose last task carries an `APPLIED` marker without `done`: the branch's real last
  entry was appended after it and this copy of the definition does not show it yet; not settled.
- A lane task that fails: it is optional; its continuation runs and ends the lane with `done` and
  `error`, so the branch settles and the join completes; the lane that calls `get()` throws.
- Dynamic forks keep fixed targets (`ForkJoinDynamicTaskMapper` sets `joinOn`).
- `joinOn: []` after a `FORK_JOIN` in a non-native definition keeps its old meaning (completes at
  once); the live-target rule is gated on `metadata.native`.
- Loop-over joins keep the iteration-suffix mapping.

Size: 2 days.

## B4. Recorded values

`once`, `now` and `random` are not tasks. The continuation that computes them reports them in the
completion's `recorded` map, and `complete` stores the map on the marker (`recorded` on the task's
`continuation`, or on `nativeState.bootstrap`). A replay reads them from the applied markers. No
server task type, mapper or system task is involved. Output size is covered by the task row and
the payload thresholds.

Size: none beyond B2.

## B5. UI: continuation on the task panel, engine state on the summary

### Before

`ui-next` builds the execution graph from `execution.workflowDefinition` and renders every task.
The task panel (`ui-next/src/pages/execution/TaskSummary.tsx`) shows the task's fields; the
execution summary (`ExecutionSummary.tsx`) shows the workflow's.

### Change

1. Nothing to fold: an execution holds only the tasks the code called, so the diagram is the
   definition's tasks, and lanes come from `metadata.lane` on each entry.
2. `TaskSummary.tsx`: when `taskResult.continuation` is set, a "Continuation" row with
   `lane #n — state` and `(deferred: …)` when set, a "Continuation worker" row and an applied-at
   row. `metadata.site` on the entry gives the source line.
3. `ExecutionSummary.tsx`: when `execution.nativeState` is set, a row with the code type and
   `step` as "continuation(s) applied"; the bootstrap state while it is not
   applied; and the handler lane's run number and state.
4. A `SWITCH` emitted by `switchOn` needs nothing new: the existing switch mapper
   (`ui-next/src/components/features/flow/nodes/mapper/switch.ts`) draws one branch per key of
   `decisionCases`, an empty list as an empty branch, `defaultCase` last, and marks the branch
   equal to `outputData.selectedCase` as the one taken (the default branch when `selectedCase`
   names no declared case). The preview entries the SDK puts in untaken cases
   (`metadata.preview: true`, names `<base>_if_<key>`) are definition entries with no task row, so
   they render as the untaken branch of a JSON switch does: the nodes the branch would have run,
   without a status. Neither the server nor `ui-next` reads `metadata.preview`. The decision with
   all its cases, and what each would have done, is visible as it is for a JSON switch.
5. A JOIN with `metadata.detached` is not drawn: the path goes on from its fork, and the branches
   end there (`core.ts`). `join.ts` draws a JOIN with `metadata.waitFor` or `metadata.waitForFork`
   with a dashed line from the last node of each lane or branch.
6. Consecutive steps with the same `metadata.loops` entry are drawn as one loop (`codeLoops.ts`). The
   server stores them as ordinary steps; only the diagram groups them.
7. A held execution draws a pseudo step, type `HELD_STEP`, after the task the code paused on
   (`executionMapper.ts`, `HeldStep.jsx`): a dashed box reading "Waiting for compatible code".
   Clicking it opens `HeldStepPanel.tsx` in the right panel, which shows what the execution did
   against what the code calls now, why it is waiting, what that means, the current state and what
   to do. The step exists only in the UI and goes away when the hold ends.

The deprecated `ui/` is not changed.

Size: 0.5 day.

## B6. Per-execution time scale for `WAIT` (removed)

Dropped: nothing outside development used it. Timers run in real time; `WaitTaskMapper` is
unchanged.

## B7. Operator operations on a growing definition

### Before

- `restart(workflowId, useLatestDefinitions)` keeps `workflow.getWorkflowDefinition()` unless the
  latest definition is requested, clears the tasks and recreates the workflow. For a native
  execution that definition is the grown one.
- `rerunWF` sets the chosen task back to `SCHEDULED`, removes the tasks after it, and lets the
  decider continue. For a native execution the definition entries after that point would stay,
  and markers that already appended would not run again.
- `retry(WorkflowModel)` reschedules every `FAILED`, `FAILED_WITH_TERMINAL_ERROR` and `TIMED_OUT`
  task. For a native execution that would re-run a failure the code handled.

### Change

1. **Restart.** When the execution is native: load the registered definition for the execution's
   name and version (or the latest when requested), `populateTaskDefinitions`, set it on the
   model, clear `variables`, and `initialize(workflow)`: a fresh `nativeState` with the bootstrap
   `RUNNABLE`, the handler marker `WAITING` when the class has handlers. Then proceed as
   today; the start path pushes `<workflowId>|main`. A restart onto a latest definition that is not
   native clears `nativeState`.

2. **Rerun from task `X`.** After the generic rerun removed the tasks scheduled after `X`,
   `NativeContinuationService.adjustRerun`: remove from the definition every entry whose task is
   gone, except entries inside `DO_WHILE` bodies (`removeAll`);
   reset `X`'s marker to `WAITING` if it has one; reset to `WAITING` every `APPLIED` marker that
   did not end its lane and whose task is now the last of its list, so its continuation runs again
   when the decider evaluates the task; if the definition is empty again, re-arm the bootstrap
   `RUNNABLE`. Completed JOINs of forks that contain `X` are reopened; the executor saves them and
   queues them only after the rerun task is saved, so a JOIN evaluated at once sees `X` pending. The
   continuation that runs again replays against a definition that ends at `X`, and other lanes
   whose later tasks were removed re-emit them with the same reference names, which are free
   again.

3. **Retry.** `retryNative`: if `failedTaskId` names a task that still exists (the code did not
   catch a `TaskFailure`; `completeContinuation` set it from `error.ref`), rerun from it (step 2);
   the cause task is rescheduled, the lane's code runs again from that call. When the code threw,
   the `TERMINATE` mark goes with everything else the continuation that threw drew (`undoThrow`:
   the entries whose `metadata.appendedBy` is that marker, with what they hold): the workflow
   failed in that same completion, so none of it has a task, and the code draws it again. (Should
   one have a task, only the mark and its `previewAfter` previews go.) Then (the code threw, or
   diverged from its recording), `rearmLastMain`: the last main-lane marker (the highest `n` on a
   task, or the bootstrap) becomes `RUNNABLE` with `deferred: "retry"`, `done` false, `error`
   null; the workflow is `RUNNING` again with `reasonForIncompletion` and `failedTaskId` cleared;
   the wake-up is pushed. Failed tasks other than the cause are never
   rescheduled: the code consumed their failure already.

4. **Skip.** No code change. For native workflows the operator completes a pending task by
   reference instead.

5. **Pause, resume, terminate.** No change. On terminate, `cancelNonTerminalTasks` cancels pending
   tasks; a leased continuation's completion finds the workflow terminal and is ignored. Pause
   leaves parked lanes parked.

### Edge cases

- Rerun from a task inside a fork branch removes later tasks of sibling branches too, because
  removal is by sequence, which is the same time-ordered semantics `rerun` has for JSON workflows.
  The sibling lanes re-emit their work on replay.
- Rerun from a task whose lane has already ended: the reset marker's continuation completes again
  with `done` and the lane's result, as the original did.
- A retried attempt copies the marker reset (`DeciderService.retry`); the lease must be on the
  latest attempt, and a completion names the task id it was leased on.

Size: 3 days.

## S1. Idempotency key on the public start

### Before

`StartWorkflowRequest` carries `idempotencyKey` and `idempotencyStrategy` and `StartWorkflowInput`
copies them; `startWorkflowIdempotent` existed for the agent start path only.

### Change

In `WorkflowExecutorOps.startWorkflow(StartWorkflowInput)`: if `idempotencyKey` is non-blank,
derive `workflowId` from `(name, idempotencyKey)` when the request did not set one, call
`startWorkflowIdempotent`, and apply `idempotencyStrategy`: `RETURN_EXISTING` returns the id;
`FAIL` throws `ConflictException` when the execution existed. Default strategy when the key is set
but the strategy is not: `RETURN_EXISTING`.

Size: 1 day.

## S2. Signals: the message endpoint wakes the handler lane

- `WorkflowMessageQueueResource.pushMessage` (`POST /api/workflow/{workflowId}/messages`) pushes
  inside `WorkflowExecutor.decide(workflowId, change)`: the change refuses a non-RUNNING workflow
  with 409 and pushes the message; the decide that follows, in the same acquisition of the
  execution lock, wakes the handler lane (`repairAndWake`: `WAITING` → `RUNNABLE` while messages
  are queued) and offers it. A busy workflow gets 503; the client retries. Requires `conductor.workflow-message-queue.enabled=true`; the server logs at
  registration when a native skeleton declares handlers and the queue is disabled.
- The handler poll claims messages with `claim(workflowId, batch, "handlers#<n>", n)`; the claim
  fence (the `no-claim-fence` mutant disables it in `JdbcWorkflowMessageQueueDAO`) keeps a stale
  run from taking newer messages. `PullWorkflowMessages` keeps the same claim mechanism for JSON
  workflows (the `destructive-pull` mutant).
- Targeted completion of a `WAIT` by reference already resolves loop-iteration names
  (`ExecutionService.getPendingTaskForWorkflow`); the SDK uses it for `awaitExternal` and human
  tasks. `awaitCondition` waits are completed by the handler completion's `releases` instead.

Size: 0.5 day.

## S3. `JOIN` any-of (removed)

Dropped: no SDK used it.

## Cross-cutting

### Compatibility

- No database schema change. `continuation` on tasks and `nativeState` on workflows are new JSON
  fields, null for every other workflow. `TaskType` has no new constants (and none for `CODE` or
  `RECORD`).
- `joinOn: []` after `FORK_JOIN` means live targets only when the definition carries
  `metadata.native`; every other JOIN behaves as before.
- A protocol 1 execution is not readable by this server; the test databases were disposable.

### Security

A fragment is a definition authored by the workflow's own worker, arriving over the task update
endpoint that the worker is already trusted to call. Validation (02 §8) keeps it to registered
types, bounded size, and the execution's own lanes. A fragment can still name any `SIMPLE` task
type, exactly as a definition author can today; that is the existing trust model for definition
authors and workers. The limits prevent accidental runaway growth, not malice.

### Observability

- Metrics named above, all tagged with workflow name.
- Task search shows exactly the code's tasks; `nativeState.codeType` makes native executions
  easy to identify.
- Log lines at `complete`: workflow id, marker (lane, `n`, state, epoch), entries appended; at
  `defer`: the reason; at rejection: the rule.
