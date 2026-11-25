# 07. Continuations on the tasks: no CODE, no RECORD, no engine-only tasks

Status: implemented, as protocol 2. [01](01-execution-model.md), [02](02-protocol.md) and
[03](03-server-changes.md) now describe this protocol; [06](06-implementation.md) records what was
validated. Nothing in the Java SDK's public API changed for protocol 2 itself; `switchOn` was
added afterwards, replacing `decision` (sections 3 and 9). Where the implementation drifted from
this plan, the text below says so, and section 9 lists the drift in one place.

Later changes, not reflected in the step-by-step plan of sections 2 to 4
([01](01-execution-model.md), [02](02-protocol.md) and [03](03-server-changes.md) describe the
code as it is):

- The poll no longer leases under the execution lock. A decide offers each continuation (the lease
  fields and the slot are set there, under the lock decide already takes), and the poll hands out
  what was offered without a lock or a write. A completion is applied under the execution lock,
  and the decide it triggers runs in the same acquisition.
- Markers are kept on the workflow row, in `nativeState.markers` by task id, not on the task rows:
  a task row is also saved by a worker's result or a system task's run, without the lock. The API
  shows each marker on its task (`Task.continuation`). The decide that schedules a task gives it
  its marker; a retried attempt gets the one before's, reset.
- The variables a completion sends are applied as sent: one continuation runs at a time, so there
  is no fence on `resumedWith`, which is kept for replay.
- A diverged or failed attempt is deferred and offered again with backoff; it does not fail the
  workflow. The main lane's code throwing may carry one `TERMINATE` entry, named by `error.ref`,
  that marks where it threw and becomes the failed task.
- Each appended entry carries `metadata.appendedBy` (`lane#n`), and a rerun drops the entries of
  the continuations applied at or after the rerun task ended (`NativeRerunPlanner`).
- The message endpoint pushes without the lock, then runs a decide; that decide offers the handler
  lane. An execution without handlers has no handler marker and no wake-up.
- An execution's code can be held in memory by one worker, which the server pins (section 9).
- Native JOINs stay asynchronous; the decide after a lane ends puts the JOIN on its queue with no
  delay (`NativeDriver.wakeJoins`).
- `NativeContinuationService` was split: `NativeWorkflows`, `NativeDefinitionEdits`,
  `NativeFragmentValidator`, `NativeRerunPlanner`, and `NativeDriver` for what differs from a
  standard workflow in the decider, the executor and the sweeper.

A second later change renamed the marker's fields after a task's (section 9): `epoch` became
`retryCount`, which counts the attempts that ended without applying rather than the offers;
`queuedAt` became `scheduledTime`, `appliedAt` `endTime` (on a task's marker only) and `deferred`
`reasonForIncompletion`; `leasedAt` was dropped.

A third later change kept on the marker only what decides state (section 9): `leased`,
`retryCount`, `endTime`, `done`, `recorded`, `resumedWith`, and `n` on the handler lane's marker
only. Which continuation a task's marker is comes from the task's entry (`metadata.lane`,
`metadata.continuation`); the poll gives the bootstrap's and the handler lane's synthetic tasks an
entry that names it the same way. `state` is derived: applied is `endTime > 0` (the bootstrap gets
an `endTime` too), leased is `leased`, runnable is a terminal, evaluated task whose marker is
neither, so the `WAITING` to `RUNNABLE` repairs below are gone. `scheduledTime` went: a decide
offers the handler lane while messages are queued, else the bootstrap until it applied, else the
continuation whose task ended first. The marker's `reasonForIncompletion` went to the workflow's.
Sections 1 to 5 still name the four states; read them as derived. The examples below use the
current fields.

## The rule

**An execution of a native workflow contains exactly the tasks the code asked for.** A workflow
method that calls two tasks produces an execution with two tasks. The state the engine needs to
drive the code is one marker per task the code paused on, kept with the workflow, because the
code only ever pauses on a task.

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
| `CODE` task per pause: queued, leased, timed out, retried, owner-routed as a worker task | a **continuation marker for the task the code paused on**, kept on the workflow row, and one queue message per wake-up; the queue's lease is the continuation lease. Polled and completed with the regular task poll and update |
| `CODE` output: `fragment`, `applied`, `step`, `resumedWith`, `variables`, `owner` | the marker's **record** once applied: `endTime` (its order, the bootstrap's included), recorded values, `resumedWith`, `done`; the main lane's result is the run's output and its error the run's reason |
| `RECORD` task per `once`, `now`, `random` | `recorded` in the record of the continuation that produced them |
| `TERMINATE` with the result or the failure | the engine completes or fails the workflow from the main lane's final record |
| FORK `lanes` + DO_WHILE `handlers` + PULL_WORKFLOW_MESSAGES + handler `CODE` + JOIN `lanes_join` | a handler marker on the workflow (handlers schedule no tasks); a message push wakes it; the poll claims the messages |
| `__replies`, `__seen` in workflow variables | `replies` on the workflow's native section (every handled message); variables hold only `@Variable` fields |
| `taskToDomain[<codeType>]` owner chain | as planned, `owner` on the native section; since removed: the shared queue of the code type, whatever the start's `taskToDomain`, or first the queue of the worker that holds the execution in memory (`pinnedTo`, section 9) |
| hidden-task fold in the UI | nothing to fold; the task panel shows a task's continuation |

What stays a task because it is the user's structure, exactly as a JSON author would write it:
FORK_JOIN and JOIN for `async` lanes and `fanOut`, DO_WHILE for `repeatUntil` and `forEach`, WAIT
for `sleep`, `sleepUntil` and `awaitCondition`, and every call to a worker or system task.

## 1. The state

### 1.1 For the task: the continuation marker

A task the code paused on has a marker (`TaskContinuation`): the code's lane will run again once
this task is terminal. Markers are kept on the workflow row, in `nativeState.markers` by task id,
not on the task's row. Only code under the workflow lock saves the workflow row; a task's row is
also saved by a worker's result, an async system task's run (a JOIN evaluated twice, out of
order) and a child finishing its parent's task, without the lock and from a copy that may be
older than the marker. A marker on the task's row was put back by such a late save after its
continuation had applied (4 of 200 executions of fork { child | child | task } then join, held as
diverged). The API shows each marker on its task (`Task.continuation`, also over gRPC), and
`nativeState` without them. The lease is flat fields on the marker, not a nested object as this
plan first drew it:

```json
"continuation": { "leased": true, "retryCount": 1 }
```

Which continuation it is (here `main` #3) is on the task's entry: `metadata.lane` and
`metadata.continuation`.

Once the continuation has run and its completion was applied, the marker is the record replay
reads. The plan's `delta` became `resumedWith`, the variables the continuation started from (a replay
restores the lane's fields to it; the server applies the variables the completion wrote, since one
continuation runs at a time):

```json
"continuation": {
  "endTime": 1794128770010,
  "recorded": {"now": {"value": "2026-11-08T…", "type": "java.time.Instant"}},
  "resumedWith": {"stage": "new"}
}
```

| State | Derived from | Meaning | Leaves it when |
|---|---|---|---|
| `WAITING` | not applied or leased; the task has not ended | the task is not terminal yet | the decider evaluates the task |
| `RUNNABLE` | not applied or leased; the task has ended and been evaluated | the code must run; it waits for the execution's slot | a decide offers it (`repairAndWake`) |
| `LEASED` | `leased` | offered at its `retryCount`; a worker holds its message until the continuation timeout, then the queue redelivers the same offer | a completion with that `n` and `retryCount`: it applies, or the attempt is deferred (`retryCount + 1`, offered again) |
| `APPLIED` | `endTime > 0` | the continuation ran and its fragment was appended after this task | never; a rerun resets the marker |

The marker is placed by the SDK: each lane's last blocking entry in a fragment carries
`metadata.continuation = n` (with the entry's `metadata.lane`), and the decide that schedules the
entry's task gives it a new marker, stored as `{}` (`NativeContinuationService.repairAndWake`). A JOIN, a DO_WHILE and a WAIT carry
it like a worker task does, since the code pauses on them too. A retried attempt copies the marker
reset (`retryCount + 1`), so the continuation runs after the attempt that finishes.

`resumedWith` holds the `@Variable` values the continuation read when it ran; a replaying worker
restores the lane's fields to it before replaying that segment (`Execution.Marker.resumedWith`).
What a continuation appended is marked on the entries: each carries `metadata.appendedBy`
(`lane#n`), which a rerun reads. A marker with nothing recorded adds well under 100 bytes to the
workflow row.

### 1.2 On the workflow: the lanes with no task, and every marker

`WorkflowModel` and `Workflow` gain a `nativeState` field (`NativeState`; the plan called it
`native`; proto field 27, carrying `bootstrap`, `handlers` and `replies`; `markers`, `pinnedTo`
and `stamp` are HTTP only). As the API shows it, with each marker on its task, it does not grow with
the length of the execution; the stored row holds one marker per task the code paused on.

```json
"nativeState": {
  "bootstrap": {"endTime": 1794128766010,
                "recorded": {"runId": {"value": "ks-…", "type": "java.lang.String"}}},
  "handlers": {"n": 8},
  "replies": {"<message id>": {"value": 1, "at": 1794128766000}}
}
```

- `bootstrap` is the main lane's first continuation, which runs before any task exists. It is the
  only continuation without a task; the records of the others are their markers in `markers`. It
  is always `main` #1, so
  neither is on the marker.
- `handlers` is the handler lane's marker. Handlers schedule no tasks and their runs are not
  replayed, so only the live state is kept: whether a run is leased, and its number `n`, which
  is the ordinal the message-queue claim fence uses (a run is due while messages are queued).
  After a run is applied the server installs a fresh marker for `n + 1`. Its lane is always
  `handlers`.
- Applied markers' `endTime` orders them across lanes for replay, as protocol 1's `step`
  output did: the server keeps it increasing within a run, the bootstrap's included, which is the
  earliest.
- `replies` holds every handled message (10 minutes, at most 500), which also de-duplicates a
  retried send; protocol 1 kept the ids apart, in `seen`.

The definition in the workflow row still grows by one entry per call, as it did under protocol 1, which is
why the entry cap and the `continueAsNew` gap remain. A workflow with 10,000 task calls has
10,000 task rows, and its workflow row holds their entries and their markers, with the values they
recorded.

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
task queue under protocol 1. Task domains do not apply: every host of the code polls the shared
queue, with the SDK's `TaskRunner`, as any worker polls its task type.

`ExecutionService.poll` already pops ids from that queue and records poll data for
`(codeType, workerId)`.
It gains one branch, taken before its "terminal task: drop the message" check:

- The message id is the awaited task's id (its task carries a continuation marker), or
  `<workflowId>|main` for the bootstrap and `<workflowId>|handlers` for the handler lane. Wake-ups
  are pushed with `pushIfNotExists`, so a marker has at most one outstanding message however many
  signals arrive.
- If the marker is `RUNNABLE`, or `LEASED` with an expired lease: under the execution lock, set
  `LEASED` with a new epoch (since replaced by `retryCount`, section 9), the worker id and the time, save it (the task row, or the workflow
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
  `handlers` with ordinal `n`; the claim fence and the idempotent re-claim work exactly as
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
    "protocol": 2, "n": 3, "retryCount": 1,
    "fragment": [ {"name": "shout", "taskReferenceName": "shout", "type": "SIMPLE", "optional": true,
                   "metadata": {"lane": "main", "site": "Hello.hello:44", "continuation": 4}} ],
    "recorded": {"token": {"value": "…", "type": "java.lang.String"}},
    "variables": {"stage": "classic"}, "resumedWith": {"stage": "new"},
    "done": false, "result": null, "error": null,
    "releases": [ {"ref": "condition", "output": {"satisfied": true}} ],
    "replies": {"…": "…"}
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
code type, so the worker's next continuation comes back in the same
request. (The plan had the SDK read the marker's new state from the response; it does not need
to.)

`NativeContinuationService.complete`, under the execution lock:

1. **Validate the lease.** The marker must be `LEASED` at this `n` with this `retryCount`. Otherwise
   `ConflictException` (`409`): a late, duplicate or superseded completion. The SDK stops; nothing
   is lost, because the marker either already advanced or was re-leased to another worker.
2. **Validate the fragment**: unique reference names, types with a mapper, no TERMINATE, every
   entry with `metadata.lane`, inline definitions populated through `populateTaskDefinitions`.
   At most one entry per lane in the fragment carries `metadata.continuation`, the lane's last
   blocking call; none when the lane finished (`done`) or made no blocking call.
3. **Check the guard.** The completion guard (`done` with success while the handler marker is
   `LEASED` or messages are queued) defers: the marker's lease is released with `retryCount + 1`, the
   reason goes in the workflow's `reasonForIncompletion`, and the continuation runs again on
   current state. Same
   behaviour as protocol 1's deferral, without a CANCELED task.
4. **Append the fragment** right after the awaited task's entry (`NativeDefinitionEdits.insertAfter`), or
   after the entry its first element names in `metadata.after` (a lane that resumed inside a
   `switchOn` case and left it; `NativeFragmentValidator.anchorOf`), or at the top level for
   the bootstrap. An `async` group arrives as a FORK_JOIN whose branches hold
   the lanes' entries, plus its JOIN (the plan's `metadata.lanes` on the fork entry was not
   needed: each entry's `metadata.lane` names its lane). A lane that finished inside the
   continuation contributes its entries before the fork and has no branch. Lanes the code does not
   wait for yet arrive with a `metadata.detached` JOIN, and a later wait is a JOIN with
   `metadata.waitFor`.
5. **Apply the rest**: variables (only those changed since `resumedWith`), `replies`
   (pruned), and `releases`, which complete the named WAIT tasks with the
   given output in the same locked write.
6. **Write the record**: the marker becomes `APPLIED` with `endTime` (increasing within the run,
   the bootstrap's included), `recorded`, `resumedWith` and `done`, and the workflow's
   `reasonForIncompletion` is cleared; what the main lane returned is the run's
   output, and an exception the run's `reasonForIncompletion`.
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

- The message endpoint pushes the message under the execution lock, as now; with messages
  queued the handler lane is `RUNNABLE`, and its wake-up is pushed (`pushIfNotExists`). No task.
- The handler continuation receives the claimed messages in the poll response, applies them to an
  instance restored from the variables, and completes with the new variables, `replies`
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

The repair lives in `NativeContinuationService.repairAndWake`, which `WorkflowExecutorOps.decide`
runs on every decide of a native workflow (the sweeper's decide included), rather than in the
sweeper's task-repair loop as planned. Since the states are derived (section 9) there is nothing
to repair in the markers; the decide only pushes again the wake-up of the continuation that holds
the execution's slot, the leased one or the one it offers (`pushIfNotExists`, so a crash between
saving and pushing, or a lost message, is repaired and a duplicate is never queued). A lease whose
holder died is redelivered by the queue when its unack timeout passes.

The same checks apply to `bootstrap` and `handlers` on the workflow. The sweeper's "RUNNING with
no running task, so reset the last task" repair skips workflows with `nativeState`, where a leased
or queued continuation is the running work.

### 2.8 Failure handling that stays

- Native task updates are applied under the execution lock, as now; the late-completion race is
  about worker tasks too.
- Transient storage failures while scheduling abort the decide for a retry, as now.
- Message de-duplication by client id, the claim fence, reply pruning, variable fencing and
  completion deferral all keep their semantics; only where the state lives changes.

## 3. The SDK: internals change, and a few API changes

| Public API | Change |
|---|---|
| `Conductor.task/once/now/random/child/startWorkflow/sleep/sleepUntil/repeatUntil/forEach/awaitCondition/human/http/event/llm/mcp/agent` | none, except that every builder sends a fresh command per call, so one kept in a variable can be called again |
| `LlmCall.as(Type)` | reads the model's answer (the task's `result`); a type other than `String` asks the server for JSON (`jsonOutput`) and decodes it, so `llm(..).as(Routing.class)` gives a record |
| `Conductor.awaitExternal(ref, T).await(timeout)` | replaced by `external(ref).await(T.class, timeout)`: the result type is given when waiting, as for `human(..)` |
| `Conductor.taskCommand`, `Conductor.spec` | removed from the public API: they returned internal types (now `TaskStubs.taskCommand`, `WorkflowSpec.forMethod`) |
| `Conductor.async/join/fanOut` | `async` returns `java.util.concurrent.Future` (`Promise` is removed) and `join` takes futures; `fanOut`, which waits for every item, returns the `List` of results, as `forEach` does. `join` throws a branch's failure as the branch threw it, then `resultNow()` reads a result; `get()` waits too and wraps a failure in `ExecutionException`. A branch cannot be cancelled (`cancel` returns false) and `get(timeout, unit)` throws `UnsupportedOperationException` |
| `AgentCall`, `AgentResult.needsInput`, `AgentCall.pollInterval/pushNotifications` | `agent(ref)` is a conversation: each `ask` is an AGENT task that continues it, carrying the conversation id and, after a reply that `needsInput()`, the id of the agent task it answers. `pollInterval`/`pushNotifications` say how the server waits for a long-running agent. `AgentResult.output`, never filled, is replaced by `artifacts` |
| `Conductor.decision(name, value)` | removed after protocol 2, replaced by `switchOn(name, value).when(key, body)…otherwise(body).get()`/`.run()`, which emits a real `SWITCH` with every declared case (02 §9); `Engine.switchOn`, implemented by `Replayer.Lane.switchOn` and `DirectEngine.switchOn` |
| `Conductor.doWhile(name, body, condition)`, `Functions.Condition` | added: a loop the code names; `CodeLoops.enter` tags every step made from the body or condition with `name`, and `CodeReader` reads it into the registered definition. No server change |
| `@Workflow`, `@Variable`, `@Signal`, `@Update`, `@Query`, `@Schedule`, `@WorkerTask` | none |
| `Conductor.of/start/workflow(...).idempotencyKey/correlationId/priority/execute/attach` | none |
| `Conductor.host()` | replaced by `Conductor.workflows(...)`, `tasks(...)` and `workflows(Type.class, factory)`, each returning the `Workers` to start |
| `WorkflowHandle.signal/update/query/result/status/variables/completeTask` | `variables()` returns only `@Variable` values; replies move out of it. `resultAsync()` is added: the result as a `CompletableFuture`. `status()` returns `Workflow.WorkflowStatus`; `result(timeout)` throws `WorkflowTimeoutException` (an `IllegalStateException`) when the wait runs out; `query` makes the instance with the factory a class is hosted with |
| `Workers.workerId/devMode/continuationTimeout/settings/discovery/workflows/tasks/taskDefinitions/start` | `Workers` is `AutoCloseable`: `close()` replaces `shutdown()`; `start()` a second time throws. `Workers.timeScale` is removed; `registered()` is renamed `isRegistered()`; `workflow(Type.class, factory)` is `workflows(Type.class, factory)` |
| `DurableTestEnvironment`, `TestRun`, `DirectEngine` | `env.workflow(Type.class, factory)` is `env.workflows(Type.class, factory)`, as on `Conductor` |
| `DurableReplay.assertCompatible` | none; it reads the new recording shape |
| `TaskFailure`, `WorkflowFailedException` | `TaskFailure.getStatus()` returns `Task.Status` and `getReason()` is added; a `WorkflowFailedException` thrown to a client carries `getWorkflowId()` and `getStatus()` |
| `NonDeterminismException`, `Backoff` | none |

Internals:

- `Registration.skeleton`: `metadata.native` with `class`, `method`, `continuationTimeoutSeconds`
  and `handlers` (a boolean); `inputParameters`; and `tasks` holding
  the graph `Replayer.discover` found (was `tasks: []` when this plan landed; 02 §2). No task
  definition for the code type. `WorkflowDef` accepts an empty task list for a native definition,
  and `NativeDriver.initialize` (through `NativeContinuationService.initialize`) empties the execution's copy either way.
- Polling: each hosted workflow is a `Worker` of its code type on a `TaskRunner` (virtual threads,
  update-v2, no domain; a second runner polls the worker's pin domain while it holds executions in
  memory, section 9), with `threadCount`, `pollingInterval` and `pollTimeout` from
  `conductor.workflow.<name>.*`, `conductor.workflow.all.*` or `@Workflow` (20, 100 ms, 500 ms).
  `process(Task)` returns the completion: the lane and number come from
  `task.workflowTask.metadata` (`lane`, `continuation`; the synthetic tasks carry an entry that
  names them too), `retryCount` from `task.continuation`; the handler messages from
  `task.inputData.messages`. Unless the worker holds the execution in memory and the stamp matches (section 9), build the
  `Execution` from the workflow JSON with its tasks;
  segments are the applied markers, the bootstrap's and those on tasks (latest attempt per
  reference name), ordered by `endTime`; each segment's `resumedWith` and
  `recorded` are read from its
  marker; markers carry no result or error, so a joined lane's outcome comes from its replay. `TaskRunner` sends it with
  update-v2: a `4xx` other than 408 and 429 (`409` means stale) is final; anything else is retried
  three times, 2 s apart. The SDK's vendored `Task` and
  `Workflow` classes gain `continuation` and `nativeState`, and its `WorkflowTask` gains
  `metadata`, as on the server.
- `Replayer`: no `codeEntry`, `terminate` or RECORD entries. A pause puts
  `metadata.continuation` on the entry it pauses on (`Entries.continuation`); `once`, `now` and
  `random` report `recorded`; the lane's end reports `done` with `result` or `error`.
  `processHandlers` reports `releases` instead of posting task updates, and sends `replies` (every
  handled message) in the completion.
- `Entries.metadata` keeps `lane` and `site`; pause entries gain `continuation`. The fork entry
  carries nothing extra.
- `Execution.TaskView.isCode/lane/continuation/step` are replaced by `TaskView.marker`
  (`Execution.Marker`), read from `continuation` on the task with its lane and number from the
  task's `workflowTask.metadata`, and `Execution.bootstrap()` (`main` #1),
  `replies()` read from `nativeState`.

## 4. What an operator sees

- **Diagram and task list**: exactly the definition's tasks, so no fold, no `?showHidden`. Lanes
  come from `metadata.lane` on each task, as the diagram reads them now.
- **The task panel** (`ui-next/src/pages/execution/TaskSummary.tsx`) shows a task's continuation
  when it has one: lane and number (from the entry's metadata), the derived state and the applied
  time (`endTime`). Why the last attempt was deferred is the workflow's `reasonForIncompletion`. A
  diverged continuation shows on the task whose marker is leased, which is where an
  operator would look.
- **The execution summary** (`ExecutionSummary.tsx`) shows the native section: code type,
  continuations applied, the bootstrap state while pending, and the handler lane's run.
- **Source lines** stay on each task's panel (`metadata.site`).
- **Operator actions**: retry, rerun from a task, restart, pause, resume and terminate, unchanged.
- **Metrics**: retries, deferrals and stale completions (`native_completion_deferred`,
  `native_stale_completion`); continuation latency, from when its task ended to the marker's
  `endTime`, is what the durability bench's checker computes.

## 5. Durability guarantees, re-derived

| Guarantee | Mechanism after the change |
|---|---|
| A worker crash loses nothing | the lease expires; the queue redelivers the same offer, at the same `retryCount`, to the next poll |
| A server crash loses nothing | the marker is saved before the queue push; the next decide re-pushes the leased marker's message, or offers the next runnable continuation, which is derived from stored state and needs no repair |
| A continuation is applied exactly once | the `(n, retryCount)` check under the execution lock, and the marker must still be `LEASED` |
| Two workers never both apply the same continuation | one lease holder per marker; the loser gets `409` |
| Stale variables never erase a handler's write | one continuation runs at a time (the slot), so a completion's variables are applied as sent |
| An acknowledged signal is never lost | stored before the push returns, then a decide offers the handler lane; completion deferred while messages are queued or the handler marker is active; claims fenced by `n` |
| Recorded values never change | `recorded` is written with the continuation that produced them, in one write |
| Replay reads only applied state | only applied markers (`endTime > 0`) are segments, so the "unapplied completion" replay bug cannot recur |
| Failover time | lease (30 s default) plus the queue's redelivery (60 to 120 s on SQLite, Postgres and MySQL: a popped message is released 60 s after its unack timeout, by a sweep every 60 s); a lost wake-up is re-pushed by the next decide, which is at most `workflowOffsetTimeout` (30 s) plus the sweeper's pass away while a continuation is runnable or leased, because `WorkflowExecutorOps.decide` caps the postpone then |

## 6. Performance

- A pause costs the task row write the task needs anyway, plus one queue message; under protocol 1 it cost
  a CODE task row, its index row, its queue message and the workflow-row write.
- A signal costs one message-queue row, one queue message and one workflow-row write; under protocol 1 it
  cost those plus a PULL task, a handler CODE task, their index rows and a DO_WHILE iteration.
- A recorded value costs nothing beyond its bytes on its marker; under protocol 1 it was a task row.
- The decide loop loads the workflow and its tasks, as it does for every workflow; each marker
  adds under 100 bytes to the workflow row, plus the values it recorded.
- The definition in the workflow row still grows by one entry per call, as before, and the markers
  by one per pause. That is the size limit of long executions, and `continueAsNew` is its answer.

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
| 6 | bench, docs | checker reads markers for retried and stalled continuations; bench summaries carry markers and the native section; mutants re-mapped (`no-retry-count-fence` added for the lease check; the SDK's unapplied-replay mutant dropped, having no equivalent; `no-stale-write-guard` kept for the async executor); re-record `kitchensink-odd.json` (done 2026-11-08); rewrite 01, 02, 03 and 06, update DURABILITY, COMPARISON and the SDK README |
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
- **Markers on retried attempts**: `DeciderService.retry` links the new attempt to the one before
  (`retriedTaskId`), and the decide that schedules it gives it that attempt's marker reset
  (`retryCount + 1`, `NativeContinuationService.addMarkers`); `latestAttempts` is what
  `repairAndWake`, `adjustRerun` and `NativeWorkflows.lastMain` read; a completion names the task id it was leased on and the `retryCount`
  check catches an older offer.
- **The poll's other checks**: the continuation branch in `ExecutionService.poll` runs before the
  concurrency and rate-limit checks and skips them.
- **Metrics by task type**: poll and update metrics attribute continuations to the code type;
  `native_fragment_rejected`, `native_completion_deferred` and `native_stale_completion` were added.
- **Queue redelivery granularity**: a dead worker's continuation resumes within the lease plus the
  queue's redelivery of the unacknowledged message; `repairAndWake` closes the gap for lost
  messages, not for slow redelivery. `<!-- TODO: verify against live server -->` the redelivery
  interval of each queue implementation has not been measured on protocol 2.
- **Rerun**: `NativeContinuationServiceTest` (`rerunUndoesWhatTheCodeDidAfterTheTask`,
  `rerunFromABranchKeepsWhatTheOtherBranchDidBeforeTheTaskEnded`,
  `rerunAfterASwitchKeepsItsCasesAndTheCaseThatRan`,
  `rerunFromATaskSchedulesWhatFollowsItInItsListAgain`,
  `aRerunFromALoopItemOpensTheLoopAgainAtThatIteration`, `aRerunUnpins`) and `NativeDriverTest`
  cover the marker cases; the harness has no rerun scenario yet.
- **`metadata.lane` is the only link between a task and its lane.** The server refuses a fragment
  entry without it (`RejectedFragmentException`, reason `lane`).
- **Protocol 1 executions** became unreadable; the bench databases were disposable.

## 9. Drift from this plan

| Plan | Implementation |
|---|---|
| `native` field on the workflow, `@ProtoField(id = 27)` | `nativeState`, proto field 27 (`bootstrap`, `handlers`, `replies`); `Task.continuation` is proto field 48; `markers`, `pinnedTo`, `stamp` and the handed-out marker's `stamp` and `variables` are HTTP only |
| nested `lease: {epoch, worker, since}` on the marker | flat fields, then `retryCount`, `scheduledTime`, `endTime` and `reasonForIncompletion`, since cut to `leased`, `retryCount` and `endTime` (last row) (first `epoch`, `queuedAt`, `leasedAt`, `appliedAt`, `attempts` and `deferred`); the queue is the code type's, or first the pinned worker's (last rows) |
| `delta` against the lane's previous record | `resumedWith`, the variables the continuation started from |
| `lane` in the completion output | not sent; the server reads the lane from the task's entry (`metadata.lane`), or from the synthetic id |
| `metadata.lanes` on the fork entry | not needed; each entry carries `metadata.lane` |
| the SDK reads the marker's new state from the `update-v2` response | the response is a stand-in task of the code type; `update-v2` then polls the continuation queue |
| a message to a class without handlers is refused with 409 | queued, then a decide runs; with no handler marker nothing is offered, so no wake-up is pushed |
| marker repairs in the sweeper's task-repair loop | `repairAndWake` in every decide of a native workflow |
| the handler marker keeps its `n` between runs | a fresh marker for `n + 1` after each applied run (`TaskContinuation.handlerRun`) |
| `protocolVersions` property consulted | `NativeFragmentValidator.PROTOCOL = 2` is checked directly; the property and `nativeState.protocol` were removed |
| re-record `kitchensink-odd.json` | done 2026-11-08; the replay test passes against it; re-recorded again the same day with `oddEvenDecision` as a `SWITCH` |
| `decision(name, value)`: nothing emitted, a label for the diamond | removed; `switchOn` emits a real `SWITCH` (`value-param`, `switchCaseValue`, every declared case, `defaultCase`, `metadata.lane`), the taken body's entries go inside its case, and the first entry after the body carries `metadata.after` when the lane resumed inside the case |
| an untaken switch case is an empty list | the SDK reads each untaken case (and `otherwise` when a declared case was taken) from its compiled body: `Replayer.previewOf` → `CodeReader.read`, which runs nothing; its entries (`metadata.preview: true`, `<base>_if_<key>`, no marker, no `optional`) fill the case. The server validates them as ordinary entries and never schedules them |
| a fragment always goes after the awaited task | the first entry may name another anchor in `metadata.after` (`NativeFragmentValidator.runs`, `NativeDefinitionEdits.insertAfter` at any depth); an anchor that names no entry is a rejected fragment |
| the sweeper's decide may postpone a native workflow like any other, up to `maxPostponeDurationSeconds` | capped at `workflowOffsetTimeout` while a marker is `RUNNABLE` or `LEASED` (`needsPromptRevisit`), and shortened when an earlier postpone was longer (`setUnackTimeoutIfShorter`), so `repairAndWake` re-routes a wake-up stuck in a dead domain's queue; found by the chaos run under the earlier per-worker routing |
| the registered skeleton has `tasks: []`; the definition view comes later from an observed graph | the registered definition carries the graph discovered from the code (`Registration.skeleton` → `Replayer.discover`: the compiled method read by `CodeReader`, plain reference names, no `metadata.preview`, inline task definitions), plus `inputParameters` and `metadata.native.signals/updates/queries/discovered`; `NativeContinuationService.initialize` clears those tasks from the execution's copy at start and restart, so the rule above still holds: an execution contains exactly the tasks the code asked for. New public API: `Conductor.register(Class<?>...)` and `Conductor.definitions(Class<?>)` |
| continuations of one execution may run concurrently, one lease per marker | one per execution at a time across hosts (the one `LEASED` marker) |
| `owner` on the native section and in every completion; workers poll their worker id, their build id and no domain; a host runs an execution's continuations one at a time | all removed: `NativeState.owner`, the `owner` completion output (`OUTPUT_OWNER`), the worker-id poller and the SDK's per-execution `ReentrantLock`. `queueName` is the shared queue of the code type (start domains do not apply), which `Workers` polls; an execution held in memory is offered to its worker's pinned queue first (next row); the one `LEASED` marker is the slot that serializes an execution |
| every continuation replays the code from the top | a worker may keep an execution's code parked in memory between continuations (`ParkedExecutions`; `parkTimeout`, default 1 h; `maxParked`, default 1000) and names it in the completion's `pin: {domain, stamp}`. The server keeps them as `nativeState.pinnedTo` and `stamp`, and `offerTo` offers the next continuation to `<domain>:<codeType>` when that worker polled lately, with a copy on the shared queue visible after `pinTimeout` (5 s). `lease` accepts either queue: taken from the pinned one, the shared copy's unack is cut to the pin timeout; taken from the shared one, the pinned copy is removed. The handed-out marker carries `stamp` and `variables`; a worker whose parked copy has that stamp continues it without fetching or replaying. A handler run leaves the pin alone; a rerun, a retry of the code or a rejected fragment clears it |
| a deferred completion is re-queued at once | a main lane that returned while the handler lane has work is deferred with `waiting for handlers: ` and not re-queued (`Completion.waitsForHandlers`, in memory only); `repairAndWake` offers the handler lane before any other continuation while messages are queued, so the main lane is offered again once the slot is free and none are. The reason text is shown, never read back |
| a failed continuation's result needs no `protocol` | every continuation result carries `protocol`, or it lands on the task the code paused on; found as a bench failure |
| a diverged continuation fails the workflow | the execution is held: the marker is deferred, the workflow's `reasonForIncompletion` is `diverged: ...`, and it is retried with backoff up to 60 s |
| `epoch` counts the offers; every applied marker has `appliedAt`; `leasedAt` is the delivery time; `deferred` holds the reason; `waiting for handlers` is read back from it | `retryCount`, 0-based like a task's: 0 on the first offer, `+ 1` when an attempt ends without applying (a deferral, a retry of the code by `rearmLastMain`, or a reset by a task retry, a rerun or a restart); a redelivery of the same offer keeps it. A diverged or failed attempt is offered again after 2^`retryCount` seconds, counted before it goes up (1, 2, 4, ...), at most 60. `queuedAt` is `scheduledTime`; `appliedAt` is `endTime`, set only on a task's marker (the bootstrap applies first, and the SDK's replay puts it first); `leasedAt` is gone (the synthetic task's `startTime` is when it is handed out); `deferred` is `reasonForIncompletion`, display only. `nativeState` stores the bootstrap without `lane` and `n` and the handler marker without `lane`. `check_runs.py` replaced `AppliedOnce`, `AppliedAtLatestLease` and `OneContinuationAtATime`, which read `epoch` and `leasedAt`, with `AppliedInOrder` |
| the marker carries `lane`, `n` and `state`, and the lease, deferral and order fields (`retryCount`, `scheduledTime`, `endTime`, `reasonForIncompletion`) | the marker keeps what decides state: `leased`, `retryCount`, `endTime`, `done`, `recorded`, `resumedWith` (proto ids 1 to 6), and `n` (7) on the handler lane's marker only. Which continuation a task's marker is comes from its entry's `metadata.lane` and `metadata.continuation`; the bootstrap is `main` #1; the poll builds the synthetic tasks an entry that names them the same way, so the SDK reads lane and number from `task.workflowTask.metadata` for every continuation. `state` is derived (`APPLIED` is `endTime > 0`, `LEASED` is `leased`, `RUNNABLE` a terminal, evaluated task), so `repairAndWake` has no repairs left. `scheduledTime` is gone: the handler lane while messages are queued, else the bootstrap until it applied, else the continuation whose task ended first; a deferred one keeps its turn, offered again at once and holding the slot through its backoff. The deferral reason is the workflow's `reasonForIncompletion`, cleared by the next apply; the UI reads a hold from it and draws it at the leased marker's task. The bootstrap gets an `endTime` when it applies, so replay sorts every segment by `endTime`. `done` stays on the marker: a JOIN reads the definition and the tasks in two reads without the workflow lock, and `done` is read with the workflow row the JOIN evaluates. Stored: `{}` before a marker runs, `{"n": 4}` for the handler lane |
