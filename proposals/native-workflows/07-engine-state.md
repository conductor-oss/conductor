# 07. Continuations on the tasks: no CODE, no RECORD, no engine-only tasks

Status: implemented, as protocol 2. [01](01-execution-model.md), [02](02-protocol.md) and
[03](03-server-changes.md) now describe this protocol; [06](06-implementation.md) records what was
validated. Nothing in the Java SDK's public API changed for protocol 2 itself; `switchOn` was
added afterwards, replacing `decision` (sections 3 and 9). Where the implementation drifted from
this plan, the text below says so, and section 9 lists the drift in one place.

Later change, not reflected below: the poll no longer leases under the execution lock. A decide
offers each continuation (the lease fields and the slot are set there, under the lock decide
already takes), the poll hands out what was offered without a lock or a write, and a completion
or a pushed message is applied inside the decide it triggers. [03](03-server-changes.md) describes
it.

## The rule

**An execution of a native workflow contains exactly the tasks the code asked for.** A workflow
method that calls two tasks produces an execution with two tasks. The state the engine needs to
drive the code lives on those tasks, because the code only ever pauses on a task.

Under protocol 1 the hello world (`greet`, then `shout`) produced six tasks: three `CODE`
continuations, the two tasks, and a `TERMINATE`. The kitchensink added `RECORD` tasks for every
`once`, `now` and `random`, a FORK + DO_WHILE + PULL_WORKFLOW_MESSAGES + handler `CODE` + JOIN for
the handler lane, and one more `CODE` per pause. After this change the hello world has two tasks,
and the kitchensink has the same tasks as its JSON twin: the forks and joins of `async`, the
DO_WHILE of `repeatUntil` and `forEach`, the WAITs of `sleep`, `sleepUntil` and `awaitCondition`,
HUMAN, HTTP, SUB_WORKFLOW, EVENT, LLM, MCP, agent. Nothing else.

What went away, and where it went:

| Protocol 1 | Protocol 2 |
|---|---|
| `CODE` task per pause: queued, leased, timed out, retried, owner-routed as a worker task | a **continuation marker on the task the code paused on**, and one queue message per wake-up; the queue's lease is the continuation lease. Polled and completed with the regular task poll and update |
| `CODE` output: `fragment`, `applied`, `step`, `resumedWith`, `variables`, `owner` | the marker's **record** once applied: `step`, recorded values, `resumedWith`, the lane's result or error |
| `RECORD` task per `once`, `now`, `random` | `recorded` in the record of the continuation that produced them |
| `TERMINATE` with the result or the failure | the engine completes or fails the workflow from the main lane's final record |
| FORK `lanes` + DO_WHILE `handlers` + PULL_WORKFLOW_MESSAGES + handler `CODE` + JOIN `lanes_join` | a handler marker on the workflow (handlers schedule no tasks); a message push wakes it; the poll claims the messages |
| `__replies`, `__seen` in workflow variables | `replies`, `seen` on the workflow's native section; variables hold only `@Variable` fields |
| `taskToDomain[<codeType>]` owner chain | as planned, `owner` on the native section; since removed: the shared queue, or the start's `taskToDomain` (section 9) |
| hidden-task fold in the UI | nothing to fold; the task panel shows a task's continuation |

What stays a task because it is the user's structure, exactly as a JSON author would write it:
FORK_JOIN and JOIN for `async` lanes and `fanOut`, DO_WHILE for `repeatUntil` and `forEach`, WAIT
for `sleep`, `sleepUntil` and `awaitCondition`, and every call to a worker or system task.

## 1. The state

### 1.1 On the task: the continuation marker

`TaskModel` and `Task` gain a `continuation` field (`TaskContinuation`, serialized with the task as
JSON and carried over gRPC as `Task.continuation`). A task carries one when the code
paused on it: the code's lane will run again once this task is terminal. The lease is flat fields
on the marker, not a nested object as this plan first drew it:

```json
"continuation": {
  "lane": "main", "n": 3, "state": "LEASED",
  "epoch": 2, "worker": "w2", "queue": "w2:kitchensink.v1.code",
  "queuedAt": 1794128769900, "leasedAt": 1794128770000, "attempts": 2, "deferred": null
}
```

Once the continuation has run and its completion was applied, the marker is the record replay
reads. The plan's `delta` became `resumedWith`, the variables the continuation started from (the
server applies only what differs from it; a replay restores the lane's fields to it):

```json
"continuation": {
  "lane": "main", "n": 3, "state": "APPLIED", "step": 7,
  "recorded": {"now": {"value": "2026-11-08T…", "type": "java.time.Instant"}},
  "resumedWith": {"stage": "new"},
  "done": false, "result": null, "error": null,
  "queuedAt": 1794128769900, "leasedAt": 1794128769940, "appliedAt": 1794128770010, "attempts": 1
}
```

| State | Meaning | Leaves it when |
|---|---|---|
| `WAITING` | the task is not terminal yet | the decider evaluates the task |
| `RUNNABLE` | the code must run; one queue message with this task's id is outstanding | a poll leases it |
| `LEASED` | a worker holds it under `lease.epoch` until the continuation timeout | a completion with that epoch, or the lease expires and a poll re-leases it with `epoch + 1` |
| `APPLIED` | the continuation ran and its fragment was appended after this task | never; a rerun resets it to `WAITING` |

The marker is placed by the SDK: each lane's last blocking entry in a fragment carries
`metadata.continuation = {"lane": …, "n": …}`, and `TaskMapperContext.createTaskModel` copies it
onto the `TaskModel` as `WAITING` when the entry is scheduled. A JOIN, a DO_WHILE and a WAIT carry
it like a worker task does, since the code pauses on them too. A retried attempt copies the marker
reset to `WAITING`, so the continuation runs after the attempt that finishes.

`resumedWith` holds the `@Variable` values the continuation read when it ran; a replaying worker
restores the lane's fields to it before replaying that segment (`Execution.Marker.resumedWith`).
What a continuation appended is not stored: the definition's per-lane entry order says it (the
entries after this task up to the next marker of the lane). A marker with nothing recorded adds
well under 100 bytes to its task.

### 1.2 On the workflow: what has no task to live on, all O(1)

`WorkflowModel` and `Workflow` gain a `nativeState` field (`NativeState`; the plan called it
`native`; JSON only, no `@ProtoField`). It never grows with the length of the execution.

```json
"nativeState": {
  "protocol": 2,
  "codeType": "kitchensink.v1.code",
  "continuationTimeoutSeconds": 30,
  "step": 7,
  "bootstrap": {"lane": "main", "n": 1, "state": "APPLIED", "step": 1,
                "recorded": {"runId": {"value": "ks-…", "type": "java.lang.String"}}, "resumedWith": {}, "done": false},
  "handlers": {"lane": "handlers", "n": 8, "state": "WAITING", "step": 6},
  "replies": {"<message id>": {"value": 1, "at": 1794128766000}},
  "seen": ["<message id>", "…"]
}
```

- `bootstrap` is the main lane's first continuation, which runs before any task exists. It is the
  only continuation without a task, so it is the only record kept here.
- `handlers` is the handler lane's marker. Handlers schedule no tasks and their runs are not
  replayed, so only the live state is kept: whether a run is due or leased, and its number, which
  is the ordinal the message-queue claim fence uses. After a run is applied the server installs a
  fresh `WAITING` marker for `n + 1`.
- `step` is the counter that orders applied continuations across lanes for replay, as protocol 1's
  `step` output did.
- `replies` and `seen` keep protocol 1's bounds (10 minutes and 500; last 500).

The definition in the workflow row still grows by one entry per call, as it did under protocol 1, which is
why the entry cap and the `continueAsNew` gap remain. A workflow with 10,000 task calls has
10,000 task rows and a workflow row that has not grown from continuations, which is what
Conductor stores for any 10,000-task workflow.

## 2. Driving the code

### 2.1 Wake-up: the `executed` flag does the work

The decider already evaluates every terminal task exactly once, when `executed` is false, and
schedules what follows it. Native wake-ups hang on that evaluation:

1. When `DeciderService.decide` evaluates a terminal task `T` (where it sets `executed = true`;
   the protocol 1 `CODE` apply hook sat there), it checks `T.continuation`. If the marker is
   `WAITING`, it becomes `RUNNABLE` and the outcome records a wake-up for `T`.
2. `WorkflowExecutorOps.decide` saves `T` with the other updated tasks, then pushes the wake-up
   message, so a `RUNNABLE` marker in storage always has a message or a sweeper repair coming.
3. The workflow start does the same for `bootstrap`: `RUNNABLE`, message `<workflowId>|main`.

No task row is written for the wake-up. `isLazyEvaluateWorkflow` loses its `CODE` exemption; the
general rule applies to native tasks as to any other.

### 2.2 Poll: the regular batch poll, the queue message is the lease

No new endpoint. Workers poll continuations with the task batch poll they use for every task:

`GET /api/tasks/poll/batch/{codeType}?workerid=&domain=&count=&timeout=`

`codeType` (`<workflow>.v<version>.code`) names the continuation queue, as it named the CODE
task queue under protocol 1; the queue is `codeType` plus the domain, like any task queue. The SDK keeps
polling no domain and, when `buildId` is set, that domain, with `TaskClient.batchPollTasksInDomain`.

`ExecutionService.poll` already pops ids from that queue and records poll data for
`(codeType, domain, workerId)`, which is what `ExecutorUtils.activeDomain` reads when the start named domains.
It gains one branch, taken before its "terminal task: drop the message" check:

- The message id is the awaited task's id (its task carries a continuation marker), or
  `<workflowId>|main` for the bootstrap and `<workflowId>|handlers` for the handler lane. Wake-ups
  are pushed with `pushIfNotExists`, so a marker has at most one outstanding message however many
  signals arrive.
- If the marker is `RUNNABLE`, or `LEASED` with an expired lease: under the execution lock, set
  `LEASED` with `epoch + 1`, the worker id and the time, save it (the task row, or the workflow
  row for the two synthetic ids), release the lock, and keep the message unacknowledged with
  `setUnackTimeout(continuationTimeoutSeconds)`, so the queue redelivers it if the worker dies.
  The awaited task's own status, worker id and poll count are left alone: the task is finished;
  only its marker changes.
- Otherwise the message is stale: acknowledge it and skip, as the poll drops a finished task's
  message today.
- The returned `Task` is the awaited task itself, with its `continuation` field now `LEASED`. For
  the two synthetic ids the poll builds a `Task` that is not persisted: `taskType = codeType`,
  `referenceTaskName = "main"` or `"handlers"`, `taskId` the synthetic id, `workflowInstanceId`,
  the marker, and in `inputData` the workflow input (bootstrap) or the claimed messages
  (handlers). The handler poll claims them from the workflow message queue under claimant
  `handlers#<n>` with ordinal `n`; the claim fence and the idempotent re-claim work exactly as
  they do for `PULL_WORKFLOW_MESSAGES`.

The branch takes the lock because a poll re-leasing an expired marker can race the late
completion of the previous lease; without it the lease write could overwrite the applied record.

This one mechanism replaces, for continuations, the owner refusal, the stale-write guard, the
response timeout with retry, the poll timeout with retry, and `isExecuteIdempotent`. Because it
is the task poll, continuations carry task logs and can extend their lease with
`TaskResult.extendLease`, which a long replay may need. A gRPC worker would use the same poll and
update path, but none has been run.

### 2.3 Complete: the regular task update, one validated, locked write

No new endpoint. A continuation finishes with the task update every worker uses:

`POST /api/tasks/update-v2` with a `TaskResult`

```json
{
  "workflowInstanceId": "…", "taskId": "<awaited task id | wf|main | wf|handlers>",
  "workerId": "w2", "status": "COMPLETED",
  "outputData": {
    "protocol": 2, "n": 3, "epoch": 2,
    "fragment": [ {"name": "shout", "taskReferenceName": "shout", "type": "SIMPLE", "optional": true,
                   "metadata": {"lane": "main", "site": "Hello.hello:44", "continuation": {"lane": "main", "n": 4}}} ],
    "recorded": {"token": {"value": "…", "type": "java.lang.String"}},
    "variables": {"stage": "classic"}, "resumedWith": {"stage": "new"},
    "done": false, "result": null, "error": null,
    "releases": [ {"ref": "condition", "output": {"satisfied": true}} ],
    "replies": {"…": "…"}, "seen": ["…"]
  }
}
```

The lane is not in the output; the server reads it from the marker the completion names.

`WorkflowExecutorOps.updateTask` already applies native task updates under the execution lock.
It gains one rule, decided after loading the target: a result for one of the two synthetic ids,
or for a task whose marker is `LEASED` while the result's output carries `protocol`, is a
continuation completion and goes to `completeContinuation`; any other result is an ordinary task
result, as when the worker that ran `greet` reports it. The ordinary path never runs for a
completion, so the awaited task's status stays what the worker that executed it set. The
update-v2 variant is used because it returns a task: the server answers with a stand-in of the
code type in the completion's domain, so the worker's next continuation comes back in the same
request. (The plan had the SDK read the marker's new state from the response; it does not need
to.)

`NativeContinuationService.complete`, under the execution lock:

1. **Validate the lease.** The marker must be `LEASED` at this `n` with this `epoch`. Otherwise
   `ConflictException` (`409`): a late, duplicate or superseded completion. The SDK stops; nothing
   is lost, because the marker either already advanced or was re-leased to another worker.
2. **Validate the fragment**: unique reference names, types with a mapper, no TERMINATE, every
   entry with `metadata.lane`, inline definitions populated through `populateTaskDefinitions`.
   At most one entry per lane in the fragment carries `metadata.continuation`, the lane's last
   blocking call; none when the lane finished (`done`) or made no blocking call.
3. **Check conflicts.** The variable conflict check (`resumedWith` against the current
   variables) and the completion guard (`done` with success while the handler marker is `LEASED`
   or `RUNNABLE`, or messages are queued) both defer: the marker goes back to `RUNNABLE` with a
   `deferred` reason, a wake-up is pushed, and the continuation runs again on current state. Same
   behaviour as protocol 1's deferral, without a CANCELED task.
4. **Append the fragment** right after the awaited task's entry (`WorkflowDef.insertAfter`), or
   after the entry its first element names in `metadata.after` (a lane that resumed inside a
   `switchOn` case and left it; `NativeContinuationService.anchorOf`), or at the top level for
   the bootstrap. An `async` group arrives as a FORK_JOIN whose branches hold
   the lanes' entries, plus its JOIN (the plan's `metadata.lanes` on the fork entry was not
   needed: each entry's `metadata.lane` names its lane). A lane that finished inside the
   continuation contributes its entries before the fork and has no branch. Lanes the code does not
   wait for yet arrive with a `metadata.detached` JOIN, and a later wait is a JOIN with
   `metadata.waitFor`.
5. **Apply the rest**: variables (only those changed since `resumedWith`), `replies`
   (pruned) and `seen` (bounded), and `releases`, which complete the named WAIT tasks with the
   given output in the same locked write.
6. **Write the record**: the marker becomes `APPLIED` with `step = ++nativeState.step`,
   `recorded`, `resumedWith`, `done`, `result` or `error`, and `appliedAt`.
7. **Make the decider schedule the fragment**: mark the awaited task `executed = false`. The
   decider re-evaluates it and `getNextTask` finds the appended entries after it. For the
   bootstrap, the definition had no tasks, and the decider's start path schedules the first entry.
   This is the same reuse of `executed` that `adjustNativeRerun` and the sweeper's repair make.
8. **Save the workflow row first** (definition, step, bootstrap or handlers), **then the
   task**, acknowledge the queue message, release the lock, then `decide`. A crash between the
   two writes leaves a `LEASED` marker whose fragment is already in the definition; the re-leased
   continuation's completion finds its entries present and the apply is idempotent.
9. **If the main lane is done**: with a result, the engine completes the workflow with that
   output; with an error, it fails it through `terminate(workflow, TerminateWorkflowException(
   error.message, FAILED, causeTask))`, so `reasonForIncompletion`, `failedTaskId` (the task named
   by `error.ref`), the failure workflow and the status listeners behave as for any failed
   workflow. Unjoined async lanes are rejected by the SDK.
10. **A failed continuation run** (a `TaskResult` with `FAILED_WITH_TERMINAL_ERROR`, which the SDK
    sends on a replay divergence) fails the workflow with that reason; a `FAILED` result (an
    unexpected exception in the worker) defers the marker back to `RUNNABLE` so the continuation
    runs again.

`checkForWorkflowCompletion` never completes a native workflow on its own: a native workflow is
complete only when the main lane's last marker is applied and `done` with no error and every
task is terminal. Code that returned without calling anything has no tasks to wait for and is
complete; every task being terminal while a marker is runnable or leased is not.

### 2.4 Signals, updates and queries

- The message endpoint pushes the message under the execution lock, as now, then marks the
  handler marker `RUNNABLE` and pushes its wake-up (`pushIfNotExists`). No decide, no task.
- The handler continuation receives the claimed messages in the poll response, applies them to an
  instance restored from the variables, and completes with the new variables, `replies`, `seen`,
  and `releases` for every `awaitCondition` that now holds. Under protocol 1 the worker completed those
  WAITs with separate task updates after a full extra replay; the completion carries them instead.
- A class without handlers has no handler marker. The endpoint still queues the message and
  pushes the wake-up; the poll finds no marker and drops it (the plan's `409` was not built).
- Queries stay in the client, on the applied variables, unchanged.

### 2.5 Joins

A live-target JOIN's targets are the last entries of its fork's branches, looking inside the
taken case when a branch ends in a finished `SWITCH`. A branch is settled when
its last task is terminal and carries no `WAITING`, `RUNNABLE` or `LEASED` marker, so the lane has
nothing more to say. `lanesSettled` reads markers instead of CODE tasks; the "wait while the JOIN
is missing from a stale definition" rule and the "wait on a failed or superseded continuation"
rule both become this one check. A joined lane's uncaught failure reaches the joining code from
the `error` on the lane's last record, as it reached it from the lane's last CODE output under protocol 1.

### 2.6 Operator actions

- **Restart**: definition tasks `[]`, `bootstrap` `RUNNABLE` at 1, handler marker `WAITING`
  when the class has handlers, wake-up pushed.
- **Rerun from task `T`**: the generic rerun removes the tasks scheduled after `T` and their
  markers go with them. Native adjustment: `T`'s marker, if any, resets to `WAITING`; a task
  whose `APPLIED` marker appended entries that no longer have tasks resets to `WAITING` too, so
  its continuation runs again when the decider evaluates it; definition entries without tasks are
  removed. This is `adjustNativeRerun` with markers instead of appended CODE
  tasks.
- **Retry** of a failed execution: rerun from `causeRef` when the failure came from a task; when
  the code itself threw, the main lane's last record (its task, or `bootstrap`) resets to
  `RUNNABLE`.

### 2.7 Sweeper

The repairs live in `NativeContinuationService.repairAndWake`, which `WorkflowExecutorOps.decide`
runs on every decide of a native workflow (the sweeper's decide included), rather than in the
sweeper's task-repair loop as planned:

- `RUNNABLE`: push the wake-up again (`pushIfNotExists`, so a crash between saving and pushing is
  repaired and a duplicate is never queued).
- `WAITING` on a terminal, executed task: make it `RUNNABLE` and push (a crash between the
  evaluation and the save).
- `LEASED` past its lease: push (the queue normally redelivers; this covers a lost message).
- `handlers` `WAITING` while the message queue holds messages: make it `RUNNABLE` and push (a
  crash between the message push and the wake).

The same checks apply to `bootstrap` and `handlers` on the workflow. The sweeper's "RUNNING with
no running task, so reset the last task" repair skips workflows with `nativeState`, where a leased
or queued continuation is the running work.

### 2.8 Failure handling that stays

- Native task updates are applied under the execution lock, as now; the late-completion race is
  about worker tasks too.
- Transient storage failures while scheduling abort the decide for a retry, as now.
- Message de-duplication by client id, the claim fence, reply pruning, variable fencing and
  completion deferral all keep their semantics; only where the state lives changes.

## 3. The SDK: internals change, the API does not

| Public API | Change |
|---|---|
| `Conductor.task/once/now/random/async/join/fanOut/child/startWorkflow/sleep/sleepUntil/repeatUntil/forEach/awaitCondition/awaitExternal/human/http/event/llm/mcp/agent` | none |
| `Conductor.decision(name, value)` | removed after protocol 2, replaced by `switchOn(name, value).when(key, body)…otherwise(body).get()`/`.run()`, which emits a real `SWITCH` with every declared case (02 §9); `Engine.switchOn`, implemented by `Replayer.Lane.switchOn` and `DirectEngine.switchOn` |
| `@Workflow`, `@Variable`, `@Signal`, `@Update`, `@Query`, `@Schedule`, `@WorkerTask` | none |
| `Conductor.connect/start/workflow(...).idempotencyKey/correlationId/priority/execute/attach` | none |
| `WorkflowHandle.signal/update/query/result/status/variables/completeTask` | `variables()` returns only `@Variable` values; replies move out of it |
| `Workers.workerId/buildId/devMode/timeScale/threads/continuationTimeout/workflows/tasks/start` | none |
| `DurableTestEnvironment`, `TestRun`, `DirectEngine` | none |
| `DurableReplay.assertCompatible` | none; it reads the new recording shape |
| `TaskFailure`, `WorkflowFailedException`, `NonDeterminismException`, `Backoff`, `Promise` | none |

Internals:

- `Registration.skeleton`: `metadata.native` with `sdk`, `protocol: 2`, `class`, `method`,
  `codeType`, `continuationTimeoutSeconds`, `handlers` (a boolean) and, added later, `signals`,
  `updates`, `queries`, `discovered` (and `discoveryError`); `inputParameters`; and `tasks` holding
  the graph `Replayer.discover` found (was `tasks: []` when this plan landed; 02 §2). No task
  definition for the code type. `WorkflowDef` accepts an empty task list for a native definition,
  and `NativeContinuationService.initialize` empties the execution's copy either way.
- `Workers.poll`: unchanged, `batchPollTasksInDomain(codeType, domain, workerId, 10, 500)` on
  the shared queue and, when set, the build's domain. `process(Task)`: the lane, number and epoch come from
  `task.continuation` (the synthetic tasks carry the marker too); the handler messages from
  `task.inputData.messages`. Build the `Execution` from the workflow JSON with its tasks;
  segments are the tasks whose marker is `APPLIED` (latest attempt per reference name) plus
  `bootstrap`, ordered by `step`; each segment's `resumedWith` and `recorded` are read from its
  marker; lane results and errors from the lane's last record. Complete with
  `updateTaskV2(TaskResult)`: a `4xx` (`409` means stale) is final, stop; a `5xx` or a lost
  connection is retried four times, 250 ms apart and growing. The SDK's vendored `Task` and
  `Workflow` classes gain `continuation` and `nativeState`.
- `Replayer`: no `codeEntry`, `terminate` or RECORD entries. A pause puts
  `metadata.continuation` on the entry it pauses on (`Entries.continuation`); `once`, `now` and
  `random` report `recorded`; the lane's end reports `done` with `result` or `error`.
  `processHandlers` reports `releases` instead of posting task updates, and sends `replies` and
  `seen` in the completion.
- `Entries.metadata` keeps `lane` and `site`; pause entries gain `continuation`. The fork entry
  carries nothing extra.
- `Execution.TaskView.isCode/lane/continuation/step` are replaced by `TaskView.marker`
  (`Execution.Marker`), read from `continuation` on the task, and `Execution.bootstrap()`,
  `replies()`, `seen()` read from `nativeState`.

## 4. What an operator sees

- **Diagram and task list**: exactly the definition's tasks, so no fold, no `?showHidden`. Lanes
  come from `metadata.lane` on each task, as the diagram reads them now.
- **The task panel** (`ui-next/src/pages/execution/TaskSummary.tsx`) shows a task's continuation
  when it has one: lane, number, state, the deferred reason, the worker and the applied time. A
  stalled or diverged continuation shows on the task the code paused on, which is where an
  operator would look.
- **The execution summary** (`ExecutionSummary.tsx`) shows the native section: code type,
  continuations applied, the bootstrap state while pending, and the handler lane's run.
- **Source lines** stay on each task's panel (`metadata.site`).
- **Operator actions**: retry, rerun from a task, restart, pause, resume and terminate, unchanged.
- **Metrics**: continuation latency (`queuedAt` to `appliedAt`), attempts, deferrals and stale
  completions, from the markers.

## 5. Durability guarantees, re-derived

| Guarantee | Mechanism after the change |
|---|---|
| A worker crash loses nothing | the lease expires; the queue redelivers; the next poll re-leases with a new epoch |
| A server crash loses nothing | the marker is saved before the queue push; the sweeper repairs a `RUNNABLE` marker without a message and a `WAITING` marker on an evaluated task |
| A continuation is applied exactly once | the `(n, epoch)` check under the execution lock |
| Two workers never both apply the same continuation | one lease holder per marker; the loser gets `409` |
| Stale variables never erase a handler's write | variable fencing on `resumedWith`, unchanged |
| An acknowledged signal is never lost | push under the lock; completion deferred while messages are queued or the handler marker is active; claims fenced by `n` |
| Recorded values never change | `recorded` is written with the continuation that produced them, in one write |
| Replay reads only applied state | only `APPLIED` markers are segments, so the "unapplied completion" replay bug cannot recur |
| Failover time | lease (30 s default) plus queue redelivery granularity (up to 60 s on Postgres); a wake-up queued for a domain whose workers died is re-pushed by the next decide, which is at most `workflowOffsetTimeout` (30 s) plus the sweeper's pass away while a marker is `RUNNABLE` or `LEASED`, because `WorkflowExecutorOps.decide` caps the postpone then |

## 6. Performance

- A pause costs the task row write the task needs anyway, plus one queue message; under protocol 1 it cost
  a CODE task row, its index row, its queue message and the workflow-row write.
- A signal costs one message-queue row, one queue message and one workflow-row write; under protocol 1 it
  cost those plus a PULL task, a handler CODE task, their index rows and a DO_WHILE iteration.
- A recorded value costs nothing beyond its bytes on a task; under protocol 1 it was a task row.
- The decide loop loads the workflow and its tasks, as it does for every workflow; markers add
  under 100 bytes to a task. The workflow row never grows with continuations.
- The definition in the workflow row still grows by one entry per call, as before. That, not the
  markers, is the size limit of long executions, and `continueAsNew` is its answer.

## 7. Work plan

All seven items landed on this branch; [06](06-implementation.md) has the validation status. The
recording `kitchensink-odd.json` in the SDK's `native-examples` test resources was re-recorded on
protocol 2 on 2026-11-08.

| # | Scope | Contents |
|---|---|---|
| 1 | common, core | `Continuation` on `TaskModel` and `Task` (`@ProtoField`), copied from entry metadata in `TaskMapperContext.createTaskModel`; `native` on `WorkflowModel` and `Workflow` (`@ProtoField(id = 27)`); empty task list allowed for native definitions; remove `CODE` and `RECORD` from `TaskType`, `CodeTaskMapper`, `RecordTaskMapper`, `Record` |
| 2 | core | `NativeContinuationService` v2: `onEvaluated`, `lease`, `complete`, `wakeHandlers`, rerun and restart on markers; decider hook; completion guard in `checkForWorkflowCompletion`; `Join.lanesSettled` on markers; sweeper repair; `isLazyEvaluateWorkflow` cleanup; metrics |
| 3 | core, rest | continuation branch in `ExecutionService.poll` (lease a marker, build the two synthetic tasks); continuation rule in `WorkflowExecutorOps.updateTask`; the message endpoint wakes the handler marker instead of deciding. No new endpoints |
| 4 | SDK | `Registration`, `Workers` (poll, process, complete), `Replayer`, `Execution`, `Entries`, `WorkflowHandle.awaitReply`, `DurableReplay`; protocol 2 |
| 5 | ui-next | remove the fold; continuation on the task panel; native section on the summary |
| 6 | bench, docs | checker reads markers for retried and stalled continuations; bench summaries carry markers and the native section; mutants re-mapped (`ignore-epoch` added for the lease check; the SDK's unapplied-replay mutant dropped, having no equivalent; `no-stale-write-guard` kept for the async executor); re-record `kitchensink-odd.json` (done 2026-11-08); rewrite 01, 02, 03 and 06, update DURABILITY, COMPARISON and the SDK README |
| 7 | validation | server unit and harness suites; SDK suites; kitchensink, operator checks, chaos; cluster bench with every fault profile and overlapping nemeses; bug-injection rounds; Elle and Porcupine |

Order followed: 1, 2 and 3 together (a completion with any protocol other than 2 is rejected);
4 against a local server; 5 and 6 in parallel; 7 last.

## 8. Risks and open points, as resolved

- **Workflow-definition validation** rejected an empty task list; `WorkflowDef` now allows it
  when `metadata` has `native`, and `MetadataServiceImpl.checkNativeWorkflow` keeps the
  enabled-flag and message-queue checks.
- **Completion of an empty workflow**: `DeciderService.checkForWorkflowCompletion` returns false
  for native workflows, and the start path schedules nothing for an empty native definition
  (`TestDeciderOutcomes.nativeWorkflowWithNoTasksYetIsNotCompleteAndSchedulesNothing`). The
  bench's `incomplete` and `wrong-output` checks cover it end to end.
- **Markers on retried attempts**: `DeciderService.retry` copies the marker reset to `WAITING`
  onto the new attempt; `latestAttempts` is what `repairAndWake`, `adjustRerun` and
  `lastMainMarker` read; a completion names the task id it was leased on and the epoch check
  catches an older one.
- **The poll's other checks**: the continuation branch in `ExecutionService.poll` runs before the
  concurrency and rate-limit checks and skips them.
- **Metrics by task type**: poll and update metrics attribute continuations to the code type;
  `native_continuation_leased` and `native_stale_completion` were added.
- **Queue redelivery granularity**: a dead worker's continuation resumes within the lease plus the
  queue's redelivery of the unacknowledged message; `repairAndWake` closes the gap for lost
  messages, not for slow redelivery. `<!-- TODO: verify against live server -->` the redelivery
  interval of each queue implementation has not been measured on protocol 2.
- **Rerun**: `NativeContinuationServiceTest.rerunResetsTheMarkersWhoseContinuationsMustRunAgain`
  covers the marker cases in the unit test; the harness has no rerun scenario yet.
- **`metadata.lane` is the only link between a task and its lane.** The server refuses a fragment
  entry without it (`RejectedFragmentException`, reason `lane`).
- **Protocol 1 executions** became unreadable; the bench databases were disposable.

## 9. Drift from this plan

| Plan | Implementation |
|---|---|
| `native` field on the workflow, `@ProtoField(id = 27)` | `nativeState`; JSON only, no `@ProtoField` on it or on `Task.continuation` |
| nested `lease: {epoch, worker, since}` on the marker | flat `epoch`, `worker`, `queue`, `leasedAt`, `attempts`, `deferred` |
| `delta` against the lane's previous record | `resumedWith`, the variables the continuation started from |
| `lane` in the completion output | not sent; the server reads the lane from the marker |
| `metadata.lanes` on the fork entry | not needed; each entry carries `metadata.lane` |
| the SDK reads the marker's new state from the `update-v2` response | the response is a stand-in task of the code type; `update-v2` then polls the continuation queue |
| a message to a class without handlers is refused with 409 | queued and pushed; the poll drops the wake-up because there is no marker |
| marker repairs in the sweeper's task-repair loop | `repairAndWake` in every decide of a native workflow |
| the handler marker keeps its `n` between runs | a fresh `WAITING` marker for `n + 1` after each applied run |
| `protocolVersions` property consulted | `NativeContinuationService.PROTOCOL = 2` is checked directly; the property is declared but not read |
| re-record `kitchensink-odd.json` | done 2026-11-08; the replay test passes against it; re-recorded again the same day with `oddEvenDecision` as a `SWITCH` |
| `decision(name, value)`: nothing emitted, a label for the diamond | removed; `switchOn` emits a real `SWITCH` (`value-param`, `switchCaseValue`, every declared case, `defaultCase`, `metadata.lane`), the taken body's entries go inside its case, and the first entry after the body carries `metadata.after` when the lane resumed inside the case |
| an untaken switch case is an empty list | the SDK reads each untaken case (and `otherwise` when a declared case was taken) from its compiled body: `Replayer.previewOf` → `CodeReader.read`, which runs nothing; its entries (`metadata.preview: true`, `<base>_if_<key>`, no marker, no `optional`) fill the case. The server validates them as ordinary entries and never schedules them |
| a fragment always goes after the awaited task | the first entry may name another anchor in `metadata.after` (`NativeContinuationService.anchorOf`, `WorkflowDef.insertAfter` at any depth); an anchor that names no entry is a rejected fragment |
| the sweeper's decide may postpone a native workflow like any other, up to `maxPostponeDurationSeconds` | capped at `workflowOffsetTimeout` while a marker is `RUNNABLE` or `LEASED` (`needsPromptRevisit`), and always written to replace a longer earlier postpone, so `repairAndWake` re-routes a wake-up stuck in a dead domain's queue; found by the chaos run under the earlier per-worker routing |
| the registered skeleton has `tasks: []`; the definition view comes later from an observed graph | the registered definition carries the graph discovered from the code (`Registration.skeleton` → `Replayer.discover`: the compiled method read by `CodeReader`, plain reference names, no `metadata.preview`, inline task definitions), plus `inputParameters` and `metadata.native.signals/updates/queries/discovered`; `NativeContinuationService.initialize` clears those tasks from the execution's copy at start and restart, so the rule above still holds: an execution contains exactly the tasks the code asked for. New public API: `Conductor.register(Class<?>...)` and `Conductor.definitions(Class<?>)` |
| continuations of one execution may run concurrently, one lease per marker | one per execution at a time across hosts (`nativeState.activeContinuation`, `activeUntil`) |
| `owner` on the native section and in every completion; workers poll their worker id, their build id and no domain; a host runs an execution's continuations one at a time | all removed: `NativeState.owner`, the `owner` completion output (`OUTPUT_OWNER`), the worker-id poller and the SDK's per-execution `ReentrantLock`. `queueName` uses the workflow's `taskToDomain` for the code type, else `*`, else the shared queue, resolved by `ExecutorUtils.activeDomain`; `Workers` polls the shared queue plus `buildId` as a domain when set; the server's `activeContinuation` slot is what serializes an execution |
| a deferred completion is re-queued at once | a main lane that returned while the handler lane has work is deferred with `waiting for handlers: ` and not re-queued (`Completion.waitsForHandlers`); `repairAndWake` skips it while the handler marker is `RUNNABLE` or `LEASED` and wakes it afterwards |
| a failed continuation's result needs no `protocol` | every continuation result carries `protocol`, or it lands on the task the code paused on; found as a bench failure |
| a diverged continuation fails the workflow | the execution is held: the marker is deferred with `diverged: ...` and retried with backoff up to 60 s |
