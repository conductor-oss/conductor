# 02. SDK to server protocol

Everything in this document is JSON that travels over existing endpoints: workflow definition
registration, workflow start, the task batch poll, the task update (`update-v2`), task update by
reference, and the workflow message queue. No endpoint exists only for native workflows. Field
names are those of `TaskContinuation`, `NativeState`, `NativeContinuationService` (server) and
`Registration`, `Workers`, `Replayer`, `Entries` (SDK).

## 1. Names

| Thing | Name | Notes |
|---|---|---|
| Code type (the poll type) | `<workflow>.v<version>.code` | From the definition's name and version: one per workflow version. Workers poll it like a task type; `ExecutionService.poll` tells a continuation from a task by its data: a synthetic id, or a message naming a task with a marker of another type than the one polled. A task may not be named after its workflow's code type. |
| Continuation queue | `<codeType>` | The shared queue of the code type. Task domains do not apply to workflow code (01 §3). |
| Message ids on the queue | the awaited task's id; `<workflowId>\|main`; `<workflowId>\|handlers` | The two synthetic ids are the bootstrap and the handler lane, which have no task. `NativeContinuationService.syntheticId`. |
| Lane ids | `main`, `fork_<k>.<i>`, `handlers` | `k` is the ordinal of the fork in its parent lane, `i` the branch index. |
| Continuation number | `n`, from 1 per lane | The SDK's per-lane counter; the server checks it on completion. |
| Task reference names emitted by code | `<taskName>` then `<taskName>_2`, `_3`, … | Per execution. Explicit `.ref()` overrides. The server validates uniqueness against the whole current definition. |
| Recorded value names | the `once(name, …)` name, then `<name>_2`, …; `now`, `random`, then `now_2`, `random_2`, … | Keys of the marker's `recorded` map; not tasks. |
| Switch and fan-out reference names | the `switchOn(name, …)` or `fanOut(name, …)` name, then `<name>_2`, `_3`, … | `Replayer.alloc`, so the same call in a loop or a fan-out works. Only an explicit `.ref()` used twice is an error. |
| Preview entry names (untaken switch cases) | `<base>_if_<caseKey>`, then `_2`, `_3`, … on a clash; nested previews compose the keys (`shipA_if_c_x`, `shipB_if_c_default`); a preview fork's join is `<fork>_join` | `Replayer.Lane.previewRef`; non-alphanumeric characters of the key become `_`; the names are reserved in the live run (`reserve`) so later real calls never collide with them. |

## 2. Registered definition

`Registration.skeleton` (SDK). `timeoutSeconds`/`timeoutPolicy` and `failureWorkflow` appear only
when the annotation sets them. The task list is the graph discovered from the code
(`Replayer.discover`, below); `WorkflowDef` also accepts an empty list when `metadata.native` is
present, which the test harness uses. Shown with the shape of the `DiscoveryTest` fixture
(`fulfilment`: a task, two `async` tasks joined, a sleep, a switch):

```json
{
  "name": "fulfilment",
  "version": 1,
  "description": "Picks, packs and ships an order",
  "schemaVersion": 2,
  "ownerEmail": "…",
  "inputParameters": ["item", "region", "quantity"],
  "metadata": {
    "native": {
      "class": "…DiscoveryTest$Fulfilment",
      "method": "run",
      "continuationTimeoutSeconds": 30,
      "handlers": true
    }
  },
  "inputSchema": { "name": "fulfilment_input", "version": 1, "type": "JSON" },
  "outputSchema": { "name": "fulfilment_output", "version": 1, "type": "JSON" },
  "enforceSchema": false,
  "tasks": [
    { "name": "pick", "taskReferenceName": "pick", "type": "SIMPLE", "inputParameters": { "item": "" },
      "optional": true, "taskDefinition": { "name": "pick", "retryCount": 3, "timeoutSeconds": 0,
      "responseTimeoutSeconds": 120, "ownerEmail": "native@conductor.local" },
      "metadata": { "lane": "main", "site": "…" } },
    { "name": "fork", "taskReferenceName": "fork", "type": "FORK_JOIN",
      "forkTasks": [ [ { "taskReferenceName": "pack", "type": "SIMPLE", "…": "…" } ],
                     [ { "taskReferenceName": "pack_2", "type": "SIMPLE", "…": "…" } ] ], "…": "…" },
    { "name": "fork_join", "taskReferenceName": "fork_join", "type": "JOIN", "joinOn": [], "…": "…" },
    { "name": "sleep", "taskReferenceName": "sleep", "type": "WAIT", "…": "…" },
    { "name": "region", "taskReferenceName": "region", "type": "SWITCH", "evaluatorType": "native-code",
      "expression": "switchCaseValue", "inputParameters": { "switchCaseValue": "" },
      "decisionCases": { "eu": [ { "taskReferenceName": "ship", "…": "…" } ],
                         "us": [ { "taskReferenceName": "ship_2", "…": "…" } ] },
      "defaultCase": [], "…": "…" }
  ]
}
```

| Key | Written by | Meaning |
|---|---|---|
| `inputParameters` | `Registration.inputParameters` | the components of the method's single record parameter, or the parameter names when there are several; empty for a single non-record parameter or none |
| `metadata.native.class`, `method` | `Registration.skeleton` | the code the definition is generated from; the UI shows the definition read only |
| `metadata.native.continuationTimeoutSeconds`, `handlers` | `Registration.skeleton` | the continuation lease (default 30), and whether the class has handler methods (the server then keeps a handler lane) |
| `inputSchema`, `outputSchema`, `enforceSchema` | `Registration.Schemas` from `JsonSchemas` | the workflow method's input and output as JSON Schemas, registered by name (`<workflow>_input`, `_output`) and versioned by content; never enforced. Each worker task's definition, and the inline one in `tasks`, references its own (`<task>_input`, `_output`) the same way |
| `tasks` | `Replayer.discover` + `Registration.inlineTaskDefinitions` | the discovered graph; every `SIMPLE` entry without a `taskDefinition` gets an inline one (`retryCount` 3, `timeoutSeconds` 0, `responseTimeoutSeconds` 120, `ownerEmail native@conductor.local`), at any depth, so registration validates |

**Discovery** (`Replayer.discover(spec)`): `CodeReader.read` reads the compiled workflow method
with ASM and no workflow code runs, so no instance or input is needed. Every call becomes an entry
as in a switch preview (§9), except: reference names are the plain ones an execution would allocate
(`alloc`: `pick`, `pack`, `pack_2`, `fork`, `fork_join`, `sleep`, `region`, `ship`, `ship_2`), there
is no `metadata.preview` and `optional` is kept. A `switchOn` emits the switch with
`switchCaseValue: ""` and every case; `async` bodies become one `FORK_JOIN` + `JOIN` when the code
joins them next, or a `JOIN` with `metadata.detached` when it does not, and a later `join` or `get`
becomes a `JOIN` `fork_wait` with `metadata.waitForFork`; `fanOut`
becomes a `FORK_JOIN_DYNAMIC` + `JOIN`, since its items exist only at run time; `repeatUntil` and
`forEach` become a `DO_WHILE` with `loopCondition: "false"`; `once`, `now` and `random` are not
steps; an `if` or `switch` whose arms call the server becomes a `SWITCH` with every arm. Reading
follows lambdas and methods of the workflow's own package, and sees every path: every arm, the body
of a loop once, and a catch block after its try. Inputs are not read, apart from constant reference names, case keys, an HTTP
method and URI and an event sink; a task name computed at run time shows as `task`; a call through
an interface or into another package is not followed; a case key that is not a constant shows as
`unknown_<n>`.
`Conductor.definitions(Class)` returns this JSON without registering it; `Conductor.register(Class...)`
registers it without starting workers; `Workers.start()` registers it too.

`NativeContinuationService.initialize` clears the execution's copy of `tasks` (an execution starts
empty and holds only what the code calls) and reads `handlers`. `codeType` (default
`<name>.v<version>.code`) and `continuationTimeoutSeconds` (default 30) are read from the
execution's copy of the definition when they are needed, not copied into `nativeState`.
`continuationTimeoutSeconds` is the lease: how long a continuation handed to a worker may go
unanswered before the queue redelivers it. Dev mode registers 3600. `MetadataServiceImpl` refuses
the skeleton when `conductor.app.nativeWorkflow.enabled=false`, and logs when `handlers` is true
and the workflow message queue is disabled.

Task definitions are registered alongside for every `@WorkerTask` method
(`Registration.register`): `retryCount` 3, `retryLogic EXPONENTIAL_BACKOFF`, `retryDelaySeconds`
1, `timeoutSeconds` 0, `responseTimeoutSeconds` 120, `timeoutPolicy RETRY`, unless the annotation
sets them. No task definition is registered for the code type.

Task definitions for the tasks native code waits on never use `timeoutPolicy: TIME_OUT_WF`, which
terminates the workflow regardless of `optional`. A `human(...).await(timeout)` carries an inline
`taskDefinition` with `timeoutSeconds` equal to the timeout, `timeoutPolicy: RETRY` and
`retryCount: 0`.

## 3. Start request

```json
{ "name": "kitchensink", "version": 1,
  "input": { "mod": 5, "oddEven": "1", "task2Name": "task_5" },
  "correlationId": "demo",
  "idempotencyKey": "kitchensink-demo-1" }
```

Workflow code has no domains: every continuation goes to the shared queue of the code type,
`<name>.v<version>.code` (`NativeContinuationService.queueName`, `codeType`), and any worker
polling it can run any of them, whatever the start's `taskToDomain` (01 §3). Task domains still
apply to the tasks the code calls (`.taskToDomain(Map.of("legacy_enrich", "team-a"))` on the start
builder); an entry for the code type or `*` routes those tasks, never a continuation. The start copies the registered definition onto the execution, empties its `tasks`
(`initialize`) and creates `nativeState` with a bootstrap marker not yet applied; the decide that
follows offers it, pushing `<workflowId>|main` to the execution's continuation queue (`queueName`).

## 4. The execution's engine state

`Workflow.nativeState` (`NativeState`), O(1) in the length of the execution:

```json
"nativeState": {
  "bootstrap": { "endTime": 1794128766010,
                 "recorded": { "runId": { "value": "ks-3c1e…", "type": "java.lang.String" } } },
  "handlers": { "n": 3 },
  "replies": { "<update id>": { "value": 1, "at": 1794128766000 },
               "<signal id>": { "at": 1794128766020 } }
}
```

- `bootstrap` is the main lane's first continuation; it is the only continuation record not on a
  task. It is always `main` #1, so neither is on the marker: the server names it by its synthetic
  id, and the SDK (`Execution.bootstrap()`), the UI (`continuationsOf` in `held.ts`) and the
  checkers take it as `main` #1. Stored as `{}` until it applies.
- `handlers` is the handler lane's live marker; its `n` is the run number, the only marker field
  that says which continuation it is (every handler run has the same synthetic id). Its lane is
  always `handlers`. After a run is applied the server replaces it with a fresh marker for
  `n + 1`, stored as `{"n": 4}`.
- Replay follows the applied markers' `endTime`, the bootstrap's included: each apply takes
  `max(now, latest + 1)`, so the times keep increasing within a run, across lanes and servers
  (applies are serialized by the run's lock), and the bootstrap, which nothing can run before,
  has the earliest.
- The continuation offered right now is the one leased marker (§6); there is no other record of
  the slot.
- `replies` is written by handler completions: every handled message by the sender's id, an
  update's with its value or error, a signal's with only its time; kept 10 minutes, at most 500.
  A message already in it was handled, so a retried send applies once.

## 5. The continuation marker on a task

`Task.continuation` (`TaskContinuation`). A task carries one when the code paused on it. Which
continuation it is comes from the task's entry, not the marker: `metadata.lane` and
`metadata.continuation` (the lane's `n`-th, counting its pauses), which the SDK put there and
`TaskMapperContext` reads when it creates the marker.

```json
"continuation": { "leased": true, "retryCount": 1 }
```

Empty values (a zero `retryCount` or time, `false`, empty maps) are not stored: a marker that has
not run yet is just `{}`.

Once applied:

```json
"continuation": {
  "endTime": 1794128770010,
  "recorded": { "now": { "value": "2026-11-08T…", "type": "java.time.Instant" } },
  "resumedWith": { "stage": "new" },
  "retryCount": 1
}
```

| Field | Written by | Meaning |
|---|---|---|
| `leased` | `offer` (in a decide); cleared by `complete` and `defer` | offered to a worker and not yet applied or handed back. At most one marker of an execution is leased: it is the slot (§6) |
| `retryCount` | `defer`, `rearmLastMain`, `reset()` (a task retry, a rerun, a restart) | how many times the continuation was offered again, 0-based like a task's `retryCount`: 0 on the first offer, one more each time an attempt ends without applying. An offer, and a redelivery of it, leave it alone. A completion must name it (§7); the retry backoff reads it, and the UI shows it as "Retries so far" |
| `endTime` | `complete` | when it applied, 0 until then: applied means `endTime > 0`. `endTimeNow` keeps it increasing within a run, the bootstrap's included (§4), so it is the order replay follows and where a rerun cuts |
| `done` | `complete` | the lane returned or threw there. What the main lane returned is the run's output, written when it applies; its exception is the run's `reasonForIncompletion`, and where it threw is the `TERMINATE` task `failedTaskId` names. Kept on the marker, not derived from the definition: a JOIN reads the definition and the tasks in two reads without the workflow lock (`lanesSettled`), and `done` is read with the task |
| `recorded`, `resumedWith` | `complete` | what a replay reads: the values `once`, `now` and `random` returned, and the variables the continuation started from |
| `n` | `TaskContinuation.handlerRun` | the handler lane's run number, on `nativeState.handlers` only (§4); absent on every other marker |

Where a continuation stands is derived, not stored:

| | Derived from |
|---|---|
| applied | `endTime > 0` |
| leased | `leased` |
| runnable | not applied or leased, and its task has ended and been evaluated (terminal, `executed`); the bootstrap until it applied; the handler lane while messages are queued |
| waiting | not applied or leased, and its task has not ended |

Why a completion did not apply is on the workflow, not the marker: `defer` sets
`workflow.reasonForIncompletion` to `diverged: …` (held), `worker reported <status>`,
`variable '…' was changed by another completion` or `waiting for handlers: …`, and the next
continuation that applies clears it. One continuation runs at a time, so the reason is the run's.

## 6. Poll

`GET /api/tasks/poll/batch/{tasktype}?workerid=&domain=&count=&timeout=` (`TaskResource`), with
`tasktype` the code type. The SDK polls it with a `TaskRunner`, as any worker polls its task type,
on the shared queue: workflows have no domains.

Only a decide gives out a continuation. Under its lock, `repairAndWake` offers the execution's
slot to the handler lane when messages are queued (messages are handled before the code
continues); otherwise to the bootstrap until it applied; otherwise to the runnable task marker
whose task ended first (the task's `endTime`, latest attempts) (`NativeContinuationService.offer`):
the marker `leased` (which makes it the slot), at its current `retryCount`. The decide saves
the workflow, and the task when there is one, then pushes the message. A deferred completion is
offered the same way, inside the decide that applies it, with its backoff as the push's delay;
one waiting for handlers is not (§7).

`ExecutionService.poll` pops message ids from the queue and, for a continuation queue, hands each
to `NativeContinuationService.lease(queueName, messageId, workerId)`, which takes no lock and writes
nothing:

- The marker is leased (it is the slot holder) and the message came from the code type's queue:
  leave it unacknowledged with `setUnackTimeout(queue, id, continuationTimeoutSeconds)` and return the
  task. If the worker dies the queue redelivers the message, and the next worker runs the same
  offer; the first completion applies, the other is refused (§7).
- Anything else (marker not leased, or absent; the workflow terminal): a stale message, remove
  it.

The slot holder's message is pushed again on every decide (idempotent), which repairs a lost
message.

The awaited task's own status, worker id and poll count are untouched: the task is finished; only
its marker changes. The returned `Task` is the awaited task itself, with `continuation` now
leased; its `workflowTask.metadata` names the continuation:

```json
{
  "taskId": "6f1c…", "referenceTaskName": "task_10", "taskType": "task_10", "status": "COMPLETED",
  "workflowInstanceId": "a4e2…",
  "outputData": { "…": "…" },
  "workflowTask": { "name": "task_10", "taskReferenceName": "task_10",
                    "metadata": { "lane": "fork_1.0", "site": "…", "continuation": 2 }, "…": "…" },
  "continuation": { "leased": true }
}
```

For `<workflowId>|main` and `<workflowId>|handlers` the poll builds a task that is not persisted
(`syntheticTask`): `taskType` and `taskDefName` the code type, `referenceTaskName` the lane,
`status IN_PROGRESS`, `scheduledTime` and `startTime` the time it is handed out, `workerId` the
poller, the marker, a `workflowTask` entry named as the code type whose `metadata` names the
continuation as a task's entry does (`lane` `main` and `continuation` 1 for the bootstrap; `lane`
`handlers` and `continuation` the handler marker's `n`), and, for the handler lane,
`inputData.messages`; the bootstrap's `inputData` is empty:

```json
{
  "taskId": "a4e2…|handlers", "taskType": "kitchensink.v1.code", "taskDefName": "kitchensink.v1.code",
  "referenceTaskName": "handlers", "workflowInstanceId": "a4e2…", "workflowType": "kitchensink",
  "status": "IN_PROGRESS", "workerId": "worker-7f3a",
  "workflowTask": { "name": "kitchensink.v1.code", "taskReferenceName": "handlers",
                    "type": "kitchensink.v1.code",
                    "metadata": { "lane": "handlers", "continuation": 3 }, "…": "…" },
  "inputData": { "messages": [ { "id": "…", "workflowId": "a4e2…", "payload": { "kind": "signal", "name": "cancel", "id": "<client id>", "args": { "…": "…" } }, "receivedAt": "…" } ] },
  "continuation": { "leased": true, "n": 3 }
}
```

The SDK reads the lane and number of every continuation it is handed from
`task.workflowTask.metadata` (`Workers.laneOf`, `numberOf`), and echoes the number as `n`.
The marker JSON above is what the server's mapper writes (fields in alphabetical order, empty
values left out); the surrounding poll responses are abridged from `syntheticTask` and
`TaskModel.toTask`. <!-- TODO: verify against live server -->

The messages are claimed from the workflow message queue with
`claim(workflowId, 10, "handlers#<n>", n)`: a re-run of the same continuation gets
the same messages, the next run's claim deletes them, and a claim with a lower ordinal than the
highest seen is fenced.

## 7. Completion

`POST /api/tasks/update-v2` with a `TaskResult` (`TaskClient.updateTaskV2`). `taskId` is the id the
poll returned: the awaited task's id, or a synthetic id. `WorkflowExecutorOps.updateTask` applies a
continuation's completion under the execution lock, followed by the decide it triggers in the same
acquisition: a synthetic id, or a task with a marker whose `outputData` has `protocol`, goes to
`completeContinuation`; a stale one is refused with 409. Any other
result for the same task id is an ordinary task result (the worker that ran `task_10` reporting
it), so the two never collide.

The output, as `Replayer.process` and `Workers.complete` build it:

```json
{
  "workflowInstanceId": "a4e2…", "taskId": "6f1c…", "workerId": "worker-7f3a", "status": "COMPLETED",
  "outputData": {
    "protocol": 2,
    "n": 2,
    "retryCount": 0,
    "resumedWith": { "stage": "new" },
    "recorded": { "token": { "value": "t-91…", "type": "java.lang.String" } },
    "done": false,
    "fragment": [
      { "name": "sub_flow_1", "taskReferenceName": "sub_flow_1", "type": "SUB_WORKFLOW",
        "inputParameters": { "…": "…" }, "subWorkflowParam": { "name": "sub_flow_1", "version": 1 },
        "optional": true,
        "metadata": { "lane": "fork_1.0", "site": "KitchenSinkWorkflow.kitchenSink:112",
                      "continuation": 3 } }
    ],
    "variables": { "stage": "classic" }
  }
}
```

| Key | Required | Meaning |
|---|---|---|
| `protocol` | yes | Must be the number `2`; anything else is rejected (`RejectedFragmentException`, reason `protocol`). |
| `n`, `retryCount` | yes | `n` must be the continuation's number (its entry's `metadata.continuation`; 1 for the bootstrap; the run number for the handler lane) and `retryCount` the leased marker's, and the marker must be leased; otherwise `ConflictException` (HTTP 409), metric `native_stale_completion`. A late, duplicate or superseded completion. |
| `fragment` | no | Inserted after the awaited task (top level for the bootstrap); an entry carrying `metadata.after` starts a run inserted after the entry it names (§8 rule 7, §9 `switchOn`). |
| `recorded` | no | Values of `once`, `now`, `random` computed by this continuation, `{ "<name>": { "value": …, "type": "<class>" } }`; stored on the marker. |
| `variables` | no | The lane's `@Variable` values after the continuation. Only entries that differ from `resumedWith` are applied. |
| `resumedWith` | no | The variables the continuation started from. A variable it changed that another completion changed meanwhile defers the completion (the workflow's `reasonForIncompletion`: `variable 'x' was changed by another completion`). |
| `done` | no (false) | `true` when the lane returned or threw. |
| `result` | main lane, `done` without error | The workflow output: the server writes it to the run's output as the completion applies. |
| `error` | main lane, `done` by throwing | `{ "message": "…", "ref": "<task>" }`; `ref` is present for an uncaught `TaskFailure`. The workflow fails with `message` and `failedTaskId` from `ref`. Another lane's error is raised by replay in the lane that joins it, so the SDK does not send it. |
| `releases` | handler lane | `[ { "ref": "condition", "output": { "satisfied": true } } ]`: `WAIT` tasks the server completes with that output in the same write. |
| `replies` | handler lane | every handled message by the sender's id: `{ "value": …, "at": <millis> }` or `{ "error": "…", "at": … }` for an update, `{ "at": … }` for a signal; replaces `nativeState.replies`. |

A completion must carry `protocol`, `n` and `retryCount` whatever its status: a result on a task row
without `protocol` is an ordinary task update. Statuses other than `COMPLETED`: `FAILED_WITH_TERMINAL_ERROR` (the SDK's `NonDeterminismException`)
holds the execution: the marker's lease is released, the workflow's `reasonForIncompletion` is set
to `diverged: ` and the result's reason, and the workflow stays `RUNNING` so that a rollback or a
fixed build continues it. `FAILED` (an unexpected worker exception) defers the marker the same way,
with `worker reported FAILED`. Every deferral adds one to `retryCount`. A deferred continuation
keeps its turn: the decide that applies the completion offers it again at once, so it holds the
slot through its backoff. These two are pushed with a delay of 2^`retryCount` seconds, counted
before it goes up (1, 2, 4, ...), at most 60; a variable conflict with none. A main lane that
returned while the handler lane has work is deferred with `waiting for handlers: …` and not
offered again at once (`Completion.waitsForHandlers`, in memory only): `repairAndWake` offers the
handler lane first while messages are queued, and the main lane once the slot is free and none
are.

A task returned here comes from the same poll (`TaskResource.updateTaskV2` polls the task type
after the update), so its marker is leased. The response is a `Task` of the code type (a stand-in,
not the awaited task): `update-v2` then polls the shared queue of the code type (the stand-in has
no domain), so the worker's next continuation comes back in the same request. A deferred
completion is not an error; the marker is offered again at its new `retryCount`, with the reason on
the workflow (except a main lane waiting for the handler lane).

The SDK retries a 5xx or a connection failure four times, 250 ms apart and growing, and treats a
4xx as final: nothing is lost, because the marker either advanced or was re-leased elsewhere.

## 8. Fragment rules

`NativeContinuationService.validateFragment`, applied before anything is written. A violation is a
`RejectedFragmentException`; the marker is recorded applied (`endTime` set) with `done`, metric
`native_fragment_rejected` with the reason, and the workflow fails with the message as its
`reasonForIncompletion`.

1. `size`: at most `nativeWorkflow.maxFragmentBytes` serialized (1 MB), and the definition after
   the append at most `nativeWorkflow.maxDefinitionTasks` entries, nested ones counted (5000).
2. `reference`: every entry at any depth has a `taskReferenceName`, absent from the current
   definition and unique within the fragment.
3. `type`: a type `DeciderService` has a mapper for. `TERMINATE` is refused: a native workflow ends
   through its code. The one exception is the entry that marks where the main lane's code threw,
   in the completion that ends the lane with that error and names it in `error.ref`. A `SWITCH`
   is decided by the code: its `evaluatorType` must be `native-code`.
4. `lane`: every entry carries `metadata.lane`.
5. `continuation`: a `metadata.continuation` is a number, the `n` of the lane's next continuation
   (the lane is the entry's `metadata.lane`), and a lane pauses at most once in one fragment.
6. `definition`: `SIMPLE` tasks get their task definition through
   `MetadataMapperService.populateTaskDefinitions`, with the same missing-definition policy as
   registration; `SUB_WORKFLOW` entries may carry an inline `workflowDefinition` or a name and
   version.
7. `fragment`: the awaited task's reference must still be in the definition (a rerun can remove
   entries). An entry that carries `metadata.after` starts a run of entries that goes after the
   entry it names, which must be in the definition or drawn, at any depth, by an earlier run of the
   same fragment (`NativeContinuationService.runs`, `insertAfter`); the entries before the first
   such entry go after the awaited task. An anchor that names nothing is rejected with "task
   <anchor> is not in the execution's definition".
8. `variables`: the variables after the change at most
   `maxWorkflowVariablesPayloadSizeThreshold` serialized, reason `variables`.

SDK obligations the server does not validate:

1. Every task a lane waits on carries `optional: true` (`Entries.of` sets it unless the command
   says otherwise). Once its retries are exhausted, this is how a failure reaches the code
   instead of failing the workflow (01 §7).
2. `WAIT` durations are never zero. `Wait.execute` treats a zero timeout as "complete externally"
   and waits forever. Durations use the server's format, `"1d 2h 3m 4s"`, not ISO-8601.
3. A `FORK_JOIN` never has an empty branch. A lane that finished inside the continuation
   contributes its entries inline before the fork; a fork with no branches left is not emitted.
4. The entry that carries a lane's marker is the last entry of that lane in the fragment.
5. Data placed in task input has `${` written as `$${` (`Entries.escape`), because the server
   evaluates expressions.
6. `metadata.after` is set only when the lane resumed inside a case (of a `switchOn`, or of a plain
   `if` or `switch`) that has now ended; it names the switch (`Lane.anchorAfter`, consumed by the
   next `emit`). It is usually the fragment's first entry; a plain `if` the code went past inside
   the case before leaving it comes first, unanchored, and stays in the case. Within one
   continuation's own branches nothing is anchored: their entries go straight into the case list,
   and the entry after the body goes back to the list that holds the switch.
7. A `SWITCH` entry declares every case the code declared, plus a `defaultCase`, and fixes
   `inputParameters.switchCaseValue` to the key the code took, with `evaluatorType: native-code`
   (the server rejects any other evaluator on a native `SWITCH`), so the evaluation selects the case whose entries the code scheduled. The untaken cases (and
   `defaultCase` when a declared case was taken and `otherwise` exists) hold preview entries.
8. Preview entries (`metadata.preview: true`) sit only inside untaken switch cases. They carry
   `metadata.lane` and `metadata.site` like any entry, never `metadata.continuation` and never
   `optional` (`Drawing.finish` removes it), and have unique reference names. Each is a real task
   type the SDK would have emitted for the call (`SIMPLE`, `SUB_WORKFLOW`, `HTTP`, `WAIT`, `EVENT`,
   `HUMAN`, `LLM_CHAT_COMPLETE`, …, through `Entries.of`), a `FORK_JOIN` + `JOIN` for `async` bodies,
   a `FORK_JOIN_DYNAMIC` + `JOIN` for `fanOut`, a `DO_WHILE` with `loopCondition: "false"` for `repeatUntil` and `forEach`, or a
   nested `SWITCH` with `switchCaseValue: ""` whose every case is previewed. They are read from
   the compiled case body by `CodeReader`, so no code runs and input parameters are not known. The server validates them like every other entry and never schedules them.

## 9. Lowering table

What each SDK call emits (`Replayer`, `Entries`). "pauses" means the entry carries the lane's
`metadata.continuation`. Every entry carries `metadata.lane` and `metadata.site`.

| SDK call | Fragment entries |
|---|---|
| `tasks.task1(a, b)` on a task stub | `SIMPLE` `task_1` with `inputParameters {a, b}`, `optional: true`; pauses |
| `task("name")…as(T)` | same, `name` from the call |
| `once(name, …)`, `now()`, `random()` | nothing; the value goes in the completion's `recorded` under `name`, `now` or `random` (then `now_2`, `random_2`, …) |
| `async` ×2 then `join` | `FORK_JOIN` `fork` (then `fork_2`, …) with one branch per lane that paused, each branch the lane's entries ending in its marker; lanes that finished contribute their entries before the fork; `JOIN` `fork_join` with `joinOn: []`; the join pauses the lane that called it |
| `async`, then a durable call, or the lane returns, with lanes still running | the same `FORK_JOIN`, with a `JOIN` carrying `metadata.detached: true`, which completes at once and does not pause |
| `join(p)` or `p.get()` naming only some of the lanes just forked | the `FORK_JOIN` + `JOIN`, with `metadata.waitFor: [<lane ids>]` on the `JOIN` |
| `join(p)` or `p.get()` on a lane already forked and still running | `JOIN` `<fork>_wait` with `joinOn: []` and `metadata.waitFor: [<lane ids>]`; pauses the calling lane. Nothing when the lane and everything it started have finished |
| `fanOut(items, fn)` | a `FORK_JOIN` with one lane per item, as `async` |
| `child(Sub::run, in)` | `SUB_WORKFLOW` `sub_flow_1` with `subWorkflowParam {name, version}`; pauses |
| `childAsync` | same inside an `async` lane |
| `startWorkflow(name, v, in)` / `startWorkflow(Other::method, in)` | `START_WORKFLOW`, `optional: true`; no pause; failure visible in the UI only |
| `sleep(d)`, `sleepUntil(t)` | `WAIT` `sleep` (then `sleep_2`, …) with `duration` in `"1d 2h 3m 4s"` form, or `until`; pauses |
| `repeatUntil(body, cond, backoff)` | `DO_WHILE` `poll` with `loopCondition` over the check task's output; `loopOver: [INLINE poll_backoff (next duration from the iteration, never zero), WAIT poll_wait duration=${poll_backoff.output.result}, SIMPLE check]`, so the first check happens after one interval; the loop pauses |
| `forEach(name, items, tasks::method)` on a task stub, or `forEach(name, items, Tasks::method)` | `DO_WHILE` with `inputParameters._items`, `loopCondition: "true"`, `loopOver: [the task]`; pauses |
| `doWhile(name, body, cond)` | no task of its own: `body` runs as ordinary code, then again while `cond` holds, and every entry emitted from `body` or `cond` carries `name` in `metadata.loops`, inside the Java loops around the call, as a Java `do`-`while` read from the compiled code is tagged; the console draws those entries as one loop named `name`; pauses at each call inside |
| `awaitExternal(ref, T).await(timeout)` | `WAIT` `ref` with `duration` = timeout; completed by `POST /api/tasks/{workflowId}/{ref}/COMPLETED`; pauses |
| `awaitCondition(cond, timeout)` | `WAIT` `condition` (then `condition_2`, …) with `duration`; the handler lane's completion names it in `releases` when `cond` holds; pauses |
| `human(ref)…await(T, timeout)` | `HUMAN` `ref` with assignment and form input, `optional: true`, inline `taskDefinition` (`timeoutSeconds` = timeout, `timeoutPolicy: RETRY`, `retryCount: 0`); `TIMED_OUT` becomes an empty `Optional`; pauses |
| `http(ref)…` | `HTTP` `ref` with `http_request` and an inline retrying definition; pauses |
| `event(ref).to(sink).send(p)` | `EVENT` `ref` with `sink`, `optional: true`; no pause |
| `llm(ref)…`, `mcp(ref)…`, `agent(ref)…` | `LLM_CHAT_COMPLETE`, `LIST_MCP_TOOLS` / `CALL_MCP_TOOL`, `AGENT`; pauses |
| `switchOn(name, value).when(key, body)…otherwise(body).get()` / `.run()` | `SWITCH` `name` with `evaluatorType: native-code`, `expression: switchCaseValue`, `inputParameters: {switchCaseValue: "<value as a string>"}`, `decisionCases` with one list per declared case, `defaultCase`, `metadata.lane`; no `optional`, no pause of its own. The taken body runs inline and its entries go inside that case's list; the lane's marker sits on whatever it paused on in there. Every untaken case, and `defaultCase` when a declared case was taken and `otherwise` exists, holds the preview of its body (rule 8 above): the entries it would have emitted, named `<base>_if_<key>`, with `metadata.preview: true`. When the lane resumes inside the case and the body returns, the next entry carries `metadata.after: "<name>"` and the server inserts it after the switch. The value is written with `${` escaped, so it is never evaluated. No match and no `otherwise`: the `SWITCH` is still emitted with every case previewed, then `WorkflowFailedException`, the lane ends with `error` |
| an `if` or `switch` whose arms call the server | the same `SWITCH` as `switchOn`, named after what it decides on (`if_<name>`, `switch_<name>`, numbered when repeated). An `if` has the case `"true"` and its else as `defaultCase`; a `switch` has a case per arm keyed by its labels and `"default"`. `switchCaseValue` is the arm taken. Emitted when the first call inside an arm is made (the SDK knows the arm from the call's line), or, when the code went past without a call and only one arm can be passed that way, with that arm and nothing inside it |
| `return value` | empty fragment, `done: true`, `result` |
| `throw` | `done: true`, `error`. For an uncaught `TaskFailure`, `error.ref` names the task, which the server puts back to the status it failed with (`outputData["conductor.failedStatus"]`) and makes the workflow's failed task (`failedTaskId` on the workflow returned by `GET /api/workflow/{workflowId}`; ui-next's "Jump to task" goes there). For any other exception, the fragment ends with a `TERMINATE` entry where the code threw (named after the exception, `metadata.site` the line it was thrown from, `metadata.loops` the Java and `doWhile` loops around that line) and `error.ref` names it: the server creates it as a `FAILED` task with the exception as its reason and makes it the failed task; it is never scheduled. The ifs and switches the code threw inside are drawn first, as at a call, so the entry lands in the arm it was in. After it come previews of what the code would have called had it not thrown (`<task>_if_no_error`, `metadata.preview`, `metadata.previewAfter: "<ref of the TERMINATE>"`), read from the compiled code: the rest of each arm in that arm's case, then the rest of each method up to the workflow method. A retry removes all the continuation that threw drew (that task, those previews, and any if, switch or lane it drew before throwing: the workflow failed in that same completion, so none of it ran) and runs the code again from that continuation |

The kitchensink's plain `switch (oddEven.oddEven())`, as recorded in `kitchensink-odd.json` (odd
path, so case `"1"` was taken; the fork and the join of the two `async` blocks are inside it and
the main lane paused on the join; case `"0"` holds the preview of `task_4` and of the dynamic fork
and join its `fanOut` would run; the default arm throws before any call, so `defaultCase` is
empty):

```json
{ "name": "switch_oddEven", "taskReferenceName": "switch_oddEven", "type": "SWITCH",
  "evaluatorType": "native-code", "expression": "switchCaseValue",
  "inputParameters": { "switchCaseValue": "1" },
  "decisionCases": {
    "0": [ { "name": "task_4", "taskReferenceName": "task_4_if_0", "type": "SIMPLE",
             "inputParameters": {},
             "metadata": { "lane": "main", "site": "…", "preview": true } },
           { "name": "fanout1", "taskReferenceName": "fanout1_if_0", "type": "FORK_JOIN_DYNAMIC",
             "dynamicForkTasksParam": "dynamicTasks",
             "dynamicForkTasksInputParamName": "dynamicTasksInput",
             "metadata": { "lane": "main", "site": "…", "preview": true } },
           { "name": "fanout1_join", "taskReferenceName": "fanout1_if_0_join", "type": "JOIN", "joinOn": [],
             "metadata": { "lane": "main", "site": "…", "preview": true } } ],
    "1": [ { "name": "fork", "taskReferenceName": "fork", "type": "FORK_JOIN", "…": "…" },
           { "name": "fork_join", "taskReferenceName": "fork_join", "type": "JOIN", "joinOn": [],
             "metadata": { "lane": "main", "site": "…", "continuation": 4 }, "…": "…" } ]
  },
  "defaultCase": [],
  "metadata": { "lane": "main", "site": "KitchenSinkWorkflow.kitchenSink:…", "loops": [] } }
```

The continuation that resumes on `fork_join` leaves the case and completes with a fragment whose
first entry is anchored after the switch:

```json
[ { "name": "get_es_1", "taskReferenceName": "get_es_1", "type": "HTTP", "optional": true, "…": "…",
    "metadata": { "lane": "main", "site": "…", "after": "switch_oddEven",
                  "continuation": 5 } } ]
```

The task row the server writes for the switch has `outputData.selectedCase: "1"` and
`evaluationResult: ["1"]` (`SwitchTaskMapper`).

## 10. Endpoints used by the SDK

| Purpose | Endpoint | Source |
|---|---|---|
| Register skeleton and task definitions | `PUT /api/metadata/workflow`, `POST /api/metadata/taskdefs` | `MetadataResource` |
| Register schedules | `POST /api/scheduler/schedules` | scheduler module |
| Start | `POST /api/workflow` | `WorkflowResource` |
| Poll continuations | `GET /api/tasks/poll/batch/{tasktype}` with `domain` | `TaskResource` |
| Complete a continuation | `POST /api/tasks/update-v2` | `TaskResource` |
| Complete a `WAIT` by reference (external callbacks, human tasks) | `POST /api/tasks/{workflowId}/{taskRefName}/{status}` | `TaskResource` |
| Signal, update, query | `POST /api/workflow/{workflowId}/messages` | `WorkflowMessageQueueResource` |
| Fetch execution for replay, and an update's reply (`nativeState.replies`) | `GET /api/workflow/{workflowId}?includeTasks=true` | `WorkflowResource` |

## 11. Versioning

`protocol` is carried in every completion. The server accepts protocol `2`
(`NativeContinuationService.PROTOCOL`) and rejects anything else with a terminal failure naming the
version it expected. A server that accepts a new version would record it per execution then; one
version needs nothing stored. Protocol 1 executions (CODE and RECORD tasks) are not readable by
this server.
