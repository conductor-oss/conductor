# 01. Execution model

This document states what the server guarantees to the SDK. The mechanisms that provide each
guarantee are in [03-server-changes.md](03-server-changes.md).

## 1. The definition grows

A native workflow is registered with `metadata.native` naming the protocol (`2`), the code type
(`<workflow>.v<version>.code`), the continuation timeout, whether the class declares `@Signal`,
`@Update` or `@Query` methods (`handlers`) and their names (`signals`, `updates`, `queries`), and a
task list the SDK **discovered** from the code (`Registration.skeleton` → `Replayer.discover`):
read from the compiled workflow method without running it (`CodeReader`), every call becoming a
definition entry under the plain reference name an execution would allocate, switches with all
their cases, `async` blocks as `FORK_JOIN` + `JOIN`, loops as `DO_WHILE`. `metadata.native.discovered`
says whether that succeeded (`discoveryError` carries the exception otherwise, and the list is
empty). The definition also carries `inputParameters`. `WorkflowDef` still accepts an empty task
list when `metadata.native` is present, which is what the test harness registers.

The discovered tasks are the definition view and the inventory; they are not what runs.
`NativeContinuationService.initialize`, at start and at restart, clears the execution's own copy of
the definition's tasks (`WorkflowModel.workflowDefinition`), so every execution starts empty and
its copy grows as the code runs, holding exactly the tasks the code called. The registered
definition never changes; the per-execution copy does. A definition shows what the code can do; an
execution shows what it did.

At start, `NativeContinuationService.initialize` gives the execution its engine state,
`WorkflowModel.nativeState`: the protocol, code type and continuation timeout from the metadata,
`step` 0, a `bootstrap` marker (lane `main`, `n` 1, `RUNNABLE`) and, when the
class has handlers, a `handlers` marker (lane `handlers`, `n` 1, `WAITING`). A message with id
`<workflowId>|main` is pushed to the continuation queue, and a worker runs the first continuation.

The growth rule: when a continuation completes, the `fragment` in its output is inserted into the
execution's definition immediately after the task the lane paused on, wherever that task sits:
top level, inside a `FORK_JOIN` branch, inside a `SWITCH` case (`WorkflowDef.insertAfter`, which
descends into `forkTasks`, `decisionCases`, `defaultCase` and `loopOver`). One exception: when the
fragment's first entry carries `metadata.after`, the fragment is inserted after the entry it names
instead (`NativeContinuationService.anchorOf`). The SDK sets it when the lane paused inside a
`switchOn` case and the case body has since returned, so the code after the switch lands after the
switch rather than inside the case it paused in. An anchor that names no entry in the definition
is a rejected fragment, and the workflow fails. The bootstrap's fragment, which has no task before
it, is appended at the top level. The awaited task
is then marked `executed = false`, so the decider evaluates it again and `getNextTask` finds the
appended entries after it; the first of them is scheduled in that decide. No task row is written
for the pause itself.

The definition is persisted with the workflow row by `updateWorkflow`. When a completion is
applied, the workflow row (definition, variables, `nativeState`) is written before the task that
carries the marker, so a fragment is never recorded as applied without being in the definition.

## 2. Lanes and continuation markers

A lane is one thread of code. The `@Workflow` method is the main lane. Each `async` block is a
lane. The handler lane runs the signal and update handlers. A lane pauses when it needs a result
from the server. The pause is not a task: it is a `continuation` marker on the last entry the lane
appended, the task it waits on. The SDK puts `metadata.continuation = {"lane": …, "n": …}` on that
entry, and `TaskMapperContext.createTaskModel` copies it onto the scheduled `TaskModel` as a
`WAITING` marker. A JOIN, a DO_WHILE and a WAIT carry it like a worker task does, since the code
pauses on them too.

Example, the kitchensink's odd path. After `task_2` the main lane reaches
`switchOn("oddEvenDecision", …)`, takes case `"1"`, runs until `join(wf3, wf4)` and its
continuation completes with (reference names as the SDK derives them; the JSON kitchensink calls
them `forkx` and `join2`):

```
SWITCH oddEvenDecision, switchCaseValue "1"
  case "0": task_4_if_0  (metadata.preview true; no marker)
  case "1":
    FORK_JOIN fork
      branch 0: task_10  (continuation {lane fork_1.0, n 1})
      branch 1: task_11  (continuation {lane fork_1.1, n 1})
    JOIN fork_join, joinOn [] (live targets), continuation {lane main, n 4}
  defaultCase: (empty: the otherwise body throws before any call)
```

The switch is the only top-level entry of that fragment; the fork and the join sit inside the case
the value took, because the case body ran inline and its calls were placed there. The untaken
case `"0"` is not empty: the SDK previews it (see below) and it shows the task its body would have
called, `task_4_if_0`, as a preview entry.

**Preview entries.** When the SDK first emits a switch, every untaken case (and the `otherwise`
body, when a declared case was taken) is read from its compiled code, without running it, to find
the calls it would make (`Replayer.previewOf`, `CodeReader`). Those calls arrive as ordinary
entries inside the untaken case's list, with `metadata.preview: true`, reference names
`<base>_if_<caseKey>`, no `metadata.continuation` and no `optional`. The server treats them as it
treats any fragment entry: they are validated (02 §8: unique references, known types, `lane`),
count toward the size limits, and are inserted with the switch. They are never scheduled, because
the `SWITCH` takes another case; they exist so the execution shows every branch's work, as a JSON
switch does.

When `task_10` completes, the decider evaluates it, its marker becomes `RUNNABLE`, and a message
with `task_10`'s id goes to the continuation queue. A worker polls it, lane `fork_1.0` resumes,
calls the child workflow, pauses, and completes the continuation with:

```
fragment: SUB_WORKFLOW sub_flow_1  (continuation {lane fork_1.0, n 2})
```

which is appended inside branch 0 after `task_10`. Branch 1 is untouched and progresses at its own
pace. When a lane returns, the continuation on its last task completes with `done: true` and an
empty fragment; the marker becomes `APPLIED` with `done`, and the branch's last task is terminal.
`fork_join` completes when every branch's current last task is terminal and settled (section 6),
and the main lane's marker on `fork_join` becomes runnable. That continuation resumes inside case
`"1"`; the body returns, and the first call after the switch (`get_es_1`) is emitted with
`metadata.after: "oddEvenDecision"`, so the server inserts it after the switch at the top level,
not after `fork_join` inside the case.

Marker states:

| State | Meaning | Leaves it when |
|---|---|---|
| `WAITING` | the task is not terminal yet | the decider evaluates the terminal task (`onEvaluated`) |
| `RUNNABLE` | the code must run; one queue message with the task's id is outstanding | a poll leases it |
| `LEASED` | a worker holds it under `epoch` until the continuation timeout | a completion with that `n` and `epoch`, or the lease expires and a poll re-leases it with `epoch + 1` |
| `APPLIED` | the continuation ran and its fragment was appended after this task | never; a rerun resets it to `WAITING` |

Guarantees:

- Lanes never wait for each other except at a `JOIN`, or when the code itself calls
  `Promise.get()`.
- A marker inside a `FORK_JOIN` branch becomes runnable when its task completes, independent of
  other branches.
- A fragment pauses each lane at most once: the server refuses a fragment in which one lane
  carries two markers.
- A retried attempt of a task copies the marker reset to `WAITING`, so the continuation runs after
  the attempt that finishes.

## 3. Any worker can run any continuation

By default continuations are fully distributed. The continuation queue of an execution is the
shared queue of its code type, and any worker polling it can lease any continuation; nothing ties
an execution to the machine that last ran it. `nativeState` records no owner and a completion names
none.

A start can opt in to domains, with the `taskToDomain` map tasks use, for example
`.taskToDomain(Map.of("<name>.v1.code", "build-7,NO_DOMAIN"))` on the SDK's start builder.
`NativeContinuationService.queueName` reads the entry for the code type, else `*`; no entry means
the shared queue. `ExecutorUtils.activeDomain`, the code `WorkflowExecutorOps.getActiveDomain` also
uses, resolves the list: the first domain with a poller for the code type in the last
`activeWorkerLastPollTimeout` (default 10 seconds), else the last entry, where `NO_DOMAIN` means the
shared queue. A list that does not end in `NO_DOMAIN` therefore queues for its last domain even
when nobody polls it.

Workers poll the shared queue, plus their `buildId` as a domain when one is set. Nothing captures
a worker's build automatically: an execution is pinned only if its start asks. `*` also applies to
ordinary tasks (`setTaskDomains`), and only workers polling that domain receive them; the SDK's
task workers poll the shared queue, so a `*` list used for continuations needs a final `NO_DOMAIN`,
and naming the code type avoids the question.

Failover:

1. A worker dies while holding a lease. On the shared queue the message was left unacknowledged
   for `continuationTimeoutSeconds` (default 30), so the queue redelivers it when the lease expires
   and the next poll re-leases with `epoch + 1`. With domains, `repairAndWake` pushes the expired
   lease to the queue that is now active.
2. A worker is alive but slow. Same as 1 for that continuation. When it later completes under the
   old epoch, `complete` refuses it with `ConflictException` (HTTP 409): the marker is not `LEASED`
   at that `n` and `epoch`.
3. Every worker of a domain dies (domain lists only). This is the remaining gap: domains are
   resolved when a message is pushed, so a continuation pushed inside the 10-second window lands in
   the dead domain's queue and waits for the next decider pass, up to about 30 seconds.
   `repairAndWake`, run on every decide, then pushes a `RUNNABLE` marker's message again to the
   queue that is now active (pushes are `pushIfNotExists`), and the stale copy is dropped when the
   lease is already held elsewhere. While any continuation is `RUNNABLE` or `LEASED` the decider's
   postpone is capped at the normal offset (30 s) and always written, replacing a longer postpone
   from an earlier decide, so this does not wait for a long postpone.

Whichever worker leases a continuation replays the method from the top (an SDK mechanism) against
the applied markers and the task list. For the replay to be exact the server keeps every applied
continuation's record on its marker (`step`, `recorded`, `resumedWith`, `done`, `result`, `error`),
every task's output, and the bootstrap's record on the workflow. Handler runs are not replayed;
their effect is in the variables.

Exactly-one-writer per continuation is provided by the offer: only a decide, under the lock it
already holds, offers a continuation (marker `LEASED`, `epoch + 1`), and a completion must name the
offer's `n` and `epoch` and find the marker still `LEASED`. The poll takes no lock and writes
nothing. Two workers handed the same offer (a redelivery) run the same replay; the first completion
applies, the other gets 409 and stops.

One continuation of an execution runs at a time, across all hosts. Reference names and recorded
values are allocated from what a replay saw, so two lanes running at once on different machines
could pick the same names. `nativeState.activeContinuation` and `activeUntil` hold the current
offer. A decide offers the slot to one runnable continuation at a time (`repairAndWake`); a poll
drops any message that is not the one offered. `complete` releases the slot whatever the outcome,
and the decide it runs in offers the next. A holder that dies leaves its message to the queue's
redelivery after the continuation timeout, and the next worker runs the same offer. Tasks are unaffected and still run in parallel.

## 4. Variables

`@Variable` fields are workflow variables. A completion may carry `variables`, the lane's
`@Variable` values after the continuation, and `resumedWith`, the values it started from. The
server applies only the variables that differ from `resumedWith`, under the same size limit
`SetVariable` enforces (`maxWorkflowVariablesPayloadSizeThreshold`, 256 KB). If another completion
changed one of those variables since this continuation read them, the completion is deferred: the
marker goes back to `RUNNABLE` with `deferred` naming the variable, a wake-up is pushed, and the
continuation runs again on the current values. The applied `resumedWith` is kept on the marker, so
a replay restores each continuation's variables to what it saw the first time.

The handler lane runs on an instance restored from the current variables and completes with
`variables` and `resumedWith` too, so a handler's write is fenced the same way.

Variables hold only `@Variable` fields. Update replies and the handled-message ids live in
`nativeState.replies` and `nativeState.seen`, not in the variables.

## 5. Handlers

When the class has handlers, `nativeState.handlers` is the handler lane's marker; no task is
scheduled for it. A signal, update or query is a message pushed to the execution's message queue
(`POST /api/workflow/{workflowId}/messages`, `WorkflowMessageQueueResource`). The endpoint pushes
the message inside a decide (`WorkflowExecutor.decide(workflowId, change)`), and that decide marks
the handler marker `RUNNABLE` if it was `WAITING` and offers it (`repairAndWake`). A worker's poll
hands out the offered marker and claims up to `handlerBatchSize` messages (default 10) from the message
queue under claimant `handlers#<n>` with ordinal `n`; the messages arrive in the handed task's
`inputData.messages`. The worker runs the handlers between pauses of the other lanes and completes
with the changed variables, `replies`, `seen` and `releases`. The server then installs a fresh
`handlers` marker with `n + 1`, `WAITING`.

Guarantees:

- Messages pushed before any worker exists are buffered in the queue; `repairAndWake` makes the
  handler marker runnable when messages are queued and it is `WAITING`, so an early signal is not
  lost.
- A handler run claims its messages under its number. A re-run of the same continuation gets the
  same messages; the next run's claim deletes them. The claim is fenced by `n`, so a stale run can
  never take newer messages.
- The handler lane never appends a fragment (its `fragment` is empty). Its effects are variable
  changes, replies, the de-duplication list, and `releases`: the `WAIT` tasks of `awaitCondition`
  whose condition now holds, which the server completes with the given output in the same write,
  inside the decide that applies the completion.
- A main-lane completion that would end the execution successfully is deferred while the handler
  marker is `RUNNABLE` or `LEASED` or messages are still queued, so an acknowledged signal is
  never discarded by a completion. Its `deferred` starts with `waiting for handlers: ` and it is
  not re-queued (`Completion.waitsForHandlers`): competing for the one-continuation slot, it could
  win every time and the signal would never run. `repairAndWake` skips it while the handler marker
  is `RUNNABLE` or `LEASED` and wakes it once that lane has settled.

The handler lane requires `conductor.workflow-message-queue.enabled=true`. Without handlers,
`nativeState.handlers` is null and no message is ever claimed.

## 6. Joins

A `JOIN` that follows a `FORK_JOIN` and declares no `joinOn` joins on whatever each branch
currently ends with, evaluated every time the join is checked (03, change B3). A branch is settled
when its last task is terminal (for a branch ending in a `SWITCH`, the last task of the case it
took, at any depth, since a `SWITCH` finishes as soon as it has chosen) and either carries no marker or carries an `APPLIED` marker with
`done` (`NativeContinuationService.lanesSettled`). A marker still `WAITING`, `RUNNABLE` or `LEASED`
means the lane has more to append; an `APPLIED` marker that did not end the lane means the branch's
real last entry is not this one yet in the copy of the definition being read. A JOIN whose fork is
missing from the definition it reads waits for the next evaluation. Failure semantics of
`Join.execute` are unchanged: `optional` on the branch's current last task applies.
`inputParameters.joinStrategy: ANY` completes on the first settled successful branch.

`async` does not make the code wait. Lanes still running when the code next does something durable
are forked with a `JOIN` marked `metadata.detached`, which completes at once, and the code carries
on. `join(p)` or `p.get()` on a lane already running adds its own `JOIN`, `<fork>_wait`, whose
`metadata.waitFor` lists the lane ids it waits for. Such a `JOIN` can sit anywhere after its fork.
A lane's targets include what it started: each branch end, and the branch ends of any fork inside
it, so a wait bubbles up through nested lanes. Lanes never joined still run: the execution completes
only when no task has a continuation left to apply. A lane nothing joins can fail without failing
the execution; a join on it raises the failure in the code that joins.

## 7. Completion and failure

- The main lane ends when its continuation completes with `done: true`. With `result`, the engine
  completes the workflow with that output (`completeWorkflow`). With `error`, it fails the
  workflow through `terminate(workflow, TerminateWorkflowException(error.message, FAILED,
  causeTask))`, where `causeTask` is the task named by `error.ref` when the error was an uncaught
  `TaskFailure`; `reasonForIncompletion`, `failedTaskId`, the failure workflow and the status
  listeners behave as for any failed workflow. No `TERMINATE` task is involved, and a fragment that
  contains one is refused.
- `checkForWorkflowCompletion` never completes a native workflow on its own: a definition with no
  tasks yet, or with every task terminal while a marker is runnable or leased, is not complete.
- An `async` lane that throws completes its continuation with `done: true` and an `error` object;
  the exception is raised in the lane that calls `get()`. The server sees data, not failure.
- A `switchOn` whose value matches no declared case and has no `otherwise` throws
  `WorkflowFailedException` in the code (`switch <name> has no case for <value>`); the lane ends
  with `done` and `error` like any uncaught exception, and the workflow fails with that message.
- Task failures reach the code, after the usual retries. Every task a lane waits on is retried per
  its task definition like any Conductor task: worker tasks with three retries and exponential
  backoff by default (configurable on `@WorkerTask`), HTTP and LLM steps with the same policy
  through an inline task definition. Each task is emitted with `optional: true`, so when its
  retries are exhausted `DeciderService.retry` returns empty instead of terminating the workflow,
  and the task is evaluated, which makes the lane's marker runnable. As for any optional task, the
  decider sets it to COMPLETED_WITH_ERRORS. For native workflows it records how the task failed
  (FAILED, FAILED_WITH_TERMINAL_ERROR, TIMED_OUT) in the task's output under
  `conductor.failedStatus`, so the code receives the true outcome as `TaskFailure`. If the code does not catch it, the lane ends with `done` and `error` (with
  `error.ref` naming the task), so an unhandled failure still fails the workflow, one continuation
  later. A worker that throws `NonRetryableException` fails the task terminally, with no retries.
- Timeouts follow the same path when the task definition's timeout policy is `RETRY`.
  `TIME_OUT_WF` terminates the workflow regardless of `optional`, so the SDK never uses it for
  tasks native code waits on; a `human(...).await(timeout)` is a HUMAN task with an inline
  definition: `timeoutSeconds` equal to the timeout, `timeoutPolicy: RETRY` and `retryCount: 0`.
- Fire-and-forget tasks (`EVENT`, `START_WORKFLOW`) are optional too; their failure is visible in
  the UI but never reaches the code, which has moved on.
- A replay that diverged from the recording holds the execution: the worker completes with
  `FAILED_WITH_TERMINAL_ERROR`, the marker returns to `RUNNABLE` with `deferred` set to
  `diverged: ...`, and it is offered again with backoff until a compatible build runs it.
- An invalid completion fails the workflow: the server rejects it (unknown protocol, duplicate reference name, unsupported type, size limit,
  missing lane, malformed marker, two markers for one lane). The marker is recorded `APPLIED` with
  `done` and `error`, and the workflow fails with the message as the reason. A completion with
  status `FAILED` (an unexpected exception in the worker) releases the lease instead: the marker
  goes back to `RUNNABLE` and the continuation runs again.
- Operators keep their tools. Restart, rerun from a task, retry and terminate work on native
  executions with native-aware semantics (03, change B7): restart begins from the registered
  skeleton with a fresh `nativeState`, and so does a rerun that names no task; rerun from a task
  runs that task again and undoes what the code did after it ended: every continuation applied
  after that moment runs again, the entries those continuations appended go with their tasks
  (each entry's `metadata.appendedBy` names the continuation that appended it), and the tasks
  after it in its own list are scheduled again. What another lane started before the task ended
  stays, so a rerun from a task in one branch of a fork does not run the other branch's tasks
  again; joins whose code runs again wait again, and the untaken cases a switch draws stay. In a
  loop the server runs (`forEach`, `repeatUntil`), a rerun from a task in iteration k reopens the
  loop at k: the rest of that iteration and the later ones run again, as the loop decides. A
  rerun from a `SWITCH`, `FORK` or `JOIN` task is refused, because where the flow goes there
  belongs to the code (rerun from a task before it, or inside it); retry of a workflow that failed through an
  uncaught `TaskFailure` reruns from the task that failed, and never re-executes a failure the code
  already handled; retry after the code itself failed runs the main lane's last continuation
  again.

## 8. What the server does not do

- It does not run user code, interpret the fragment beyond validating it, or know what a lane is
  beyond the `lane` label in each entry's metadata and the markers.
- It does not detect divergence on replay; that is the SDK's job, using the markers and the task
  list. A `switchOn` that takes a different case than the recorded `switchCaseValue` is one such
  divergence.
- It does not know that a `SWITCH` was decided in code. The entry arrives with
  `evaluatorType: value-param` and `inputParameters.switchCaseValue` already fixed to the taken
  key, so `SwitchTaskMapper` selects the case whose entries the code scheduled and records
  `outputData.selectedCase` and `evaluationResult` as for any switch. It does not know what
  `metadata.preview` means either: the preview entries in untaken cases are definition entries the
  switch never reaches, exactly like the untaken branches of a JSON switch.
- It does not need `continue-as-new`, history limits or replay of its own.
