# 02. SDK to server protocol

Everything in this document is JSON that travels over existing endpoints: workflow definition
registration, workflow start, the task batch poll, the task update (`update-v2`), task update by
reference, and the workflow message queue. No endpoint exists only for native workflows. Field
names are those of `TaskContinuation`, `NativeState`, `NativeContinuationService` (server) and
`Registration`, `Workers`, `Replayer`, `Entries` (SDK).

## 1. Names

| Thing | Name | Notes |
|---|---|---|
| Code type (the poll type) | `<workflow>.v<version>.code` | `metadata.native.codeType` in the skeleton. Workers poll it like a task type; `ExecutionService.poll` tells a continuation from a task by its data: a synthetic id, or a message naming a task with a marker of another type than the one polled. A task may not be named after its workflow's code type. |
| Continuation queue | `<domain>:<codeType>` or `<codeType>` | `QueueUtils.getQueueName(codeType, domain, null, null)`. No domain by default: the shared queue. A domain only when the start's `taskToDomain` names the code type or `*` (01 §3). |
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
      "sdk": "java",
      "protocol": 2,
      "class": "…DiscoveryTest$Fulfilment",
      "method": "run",
      "codeType": "fulfilment.v1.code",
      "continuationTimeoutSeconds": 30,
      "handlers": true,
      "signals": ["cancel"], "updates": [], "queries": [],
      "discovered": true
    }
  },
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
    { "name": "region", "taskReferenceName": "region", "type": "SWITCH", "evaluatorType": "value-param",
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
| `metadata.native.signals`, `updates`, `queries` | `Registration.skeleton` from `WorkflowSpec` | the handler inventory: the names of the `@Signal`, `@Update` and `@Query` methods |
| `metadata.native.discovered`, `discoveryError` | `Registration.skeleton` | `true` when `Replayer.discover` returned; `false` with the exception's `toString()` when it threw, and `tasks` is then `[]` |
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
steps. Reading follows lambdas and methods of the workflow's own package, and sees every path: both
sides of a plain `if` and the body of a loop appear once each, one after the other, and a catch
block follows its try. Inputs are not read, apart from constant reference names, case keys, an HTTP
method and URI and an event sink; a task name computed at run time shows as `task`; a call through
an interface or into another package is not followed; a case key that is not a constant shows as
`unknown_<n>`.
`Conductor.definitions(Class)` returns this JSON without registering it; `Conductor.register(Class...)`
registers it without starting workers; `Workers.start()` registers it too.

`NativeContinuationService.initialize` clears the execution's copy of `tasks` (an execution starts
empty and holds only what the code calls) and reads `protocol`, `codeType` (default
`<name>.v<version>.code`), `continuationTimeoutSeconds` (default 30) and `handlers`.
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

Continuations need no `taskToDomain`: by default they go to the shared queue, and any worker can
run any of them. The optional `taskToDomain` reaches workers that poll in a domain: for ordinary
tasks (`.taskToDomain(Map.of("legacy_enrich", "team-a"))` on the start builder), and for
continuations when the entry is the code type or `*` (`Map.of("kitchensink.v1.code",
"build-7,NO_DOMAIN")`, 01 §3). The start copies the registered definition onto the execution, empties its `tasks`
(`initialize`), creates `nativeState` with the bootstrap marker `RUNNABLE` and pushes
`<workflowId>|main` to the execution's continuation queue (`queueName`).

## 4. The execution's engine state

`Workflow.nativeState` (`NativeState`), O(1) in the length of the execution:

```json
"nativeState": {
  "protocol": 2,
  "codeType": "kitchensink.v1.code",
  "continuationTimeoutSeconds": 30,
  "step": 7,
  "bootstrap": { "lane": "main", "n": 1, "state": "APPLIED", "epoch": 1, "worker": "worker-7f3a",
                 "queue": "kitchensink.v1.code", "queuedAt": 1794128766000, "leasedAt": 1794128766040,
                 "appliedAt": 1794128766110, "attempts": 1, "step": 1,
                 "recorded": { "runId": { "value": "ks-3c1e…", "type": "java.lang.String" } },
                 "resumedWith": {}, "done": false },
  "handlers": { "lane": "handlers", "n": 3, "state": "WAITING", "step": 4, "attempts": 0,
                "recorded": {}, "resumedWith": {}, "done": false },
  "replies": { "<client message id>": { "value": 1, "at": 1794128766000 } },
  "seen": ["<client message id>", "…"]
}
```

- `bootstrap` is the main lane's first continuation; it is the only continuation record not on a
  task.
- `handlers` is the handler lane's live marker, `n` being the next (or current) run. After a run
  is applied the server replaces it with a `WAITING` marker for `n + 1`.
- `step` orders applied continuations across lanes for replay; each applied marker takes
  `step = ++nativeState.step`.
- `replies` and `seen` are written by handler completions: replies kept 10 minutes, at most 500;
  the last 500 handled message ids.

## 5. The continuation marker on a task

`Task.continuation` (`TaskContinuation`). A task carries one when the code paused on it.

```json
"continuation": {
  "lane": "main", "n": 3, "state": "LEASED",
  "epoch": 2, "worker": "worker-7f3a", "queue": "kitchensink.v1.code",
  "queuedAt": 1794128769900, "leasedAt": 1794128769940, "appliedAt": 0, "attempts": 2,
  "deferred": null,
  "step": 0, "recorded": {}, "resumedWith": {}, "done": false, "result": null, "error": null
}
```

Once applied:

```json
"continuation": {
  "lane": "main", "n": 3, "state": "APPLIED",
  "epoch": 2, "worker": "worker-7f3a", "queue": "kitchensink.v1.code",
  "queuedAt": 1794128769900, "leasedAt": 1794128769940, "appliedAt": 1794128770010, "attempts": 2,
  "step": 7,
  "recorded": { "now": { "value": "2026-11-08T…", "type": "java.time.Instant" } },
  "resumedWith": { "stage": "new" },
  "done": false, "result": null, "error": null
}
```

| Field | Written by | Meaning |
|---|---|---|
| `lane`, `n` | `TaskMapperContext` from `metadata.continuation` | which continuation runs when this task finishes |
| `state` | decider, poll, complete | `WAITING`, `RUNNABLE`, `LEASED`, `APPLIED` |
| `epoch`, `queue`, `leasedAt`, `attempts` | `offer` (in a decide) | `epoch` increments per offer; `queue` is the queue the message was pushed to, acknowledged on completion; `leasedAt` is when the message is delivered (after a retry's backoff); `attempts` counts offers |
| `worker` | `complete` | the worker whose completion applied; the poll writes nothing |
| `queuedAt` | `onEvaluated`, `defer`, `initialize` | when the marker became runnable |
| `deferred` | `defer` | why the last completion was not applied (variable conflict, pending messages, worker error); cleared when the continuation applies. A value starting `waiting for handlers: ` is a main lane that returned while the handler lane has work: it is not re-queued, and `repairAndWake` wakes it once that lane has settled |
| `step`, `recorded`, `resumedWith`, `done`, `result`, `error`, `appliedAt` | `complete` | the record a replay reads |

## 6. Poll

`GET /api/tasks/poll/batch/{tasktype}?workerid=&domain=&count=&timeout=` (`TaskResource`), with
`tasktype` the code type. The SDK calls `TaskClient.batchPollTasksInDomain(codeType, domain,
workerId, 10, 500)` on the shared queue (no domain) and, when `buildId` is set, on that domain.

Only a decide gives out a continuation. Under its lock, `repairAndWake` offers the execution's
slot to the runnable continuation that has waited longest (`NativeContinuationService.offer`):
`nativeState.activeContinuation` and `activeUntil`, and on the marker `LEASED`, `epoch + 1`,
`leasedAt` (when the message is delivered), `queue` (resolved now), `attempts + 1`. The decide saves
the workflow, and the task when there is one, then pushes the message. A deferred completion is
offered the same way, inside the decide that applies it, with its backoff as the push's delay.

`ExecutionService.poll` pops message ids from the queue and, for a continuation queue, hands each
to `NativeContinuationService.lease(queueName, messageId, workerId)`, which takes no lock and writes
nothing:

- The marker is `LEASED`, the message is the slot holder and came from the marker's `queue`: leave
  it unacknowledged with `setUnackTimeout(queue, id, continuationTimeoutSeconds)` and return the
  task. If the worker dies the queue redelivers the message, and the next worker runs the same
  offer; the first completion applies, the other is refused (§7).
- Anything else (marker `APPLIED`, `WAITING`, `RUNNABLE` or absent; another continuation holds the
  slot; a duplicate in another domain's queue; the workflow terminal): a stale message, remove it.

The slot holder's message is pushed again on every decide (idempotent), which repairs a lost
message. A holder offered in a queue no worker polls any more (its domain's workers went away) is
offered again in the queue resolved now, at a new epoch, once `activeUntil` has passed.

The awaited task's own status, worker id and poll count are untouched: the task is finished; only
its marker changes. The returned `Task` is the awaited task itself, with `continuation` now
`LEASED`:

```json
{
  "taskId": "6f1c…", "referenceTaskName": "task_10", "taskType": "task_10", "status": "COMPLETED",
  "workflowInstanceId": "a4e2…",
  "outputData": { "…": "…" },
  "continuation": { "lane": "fork_1.0", "n": 2, "state": "LEASED", "epoch": 1,
                    "worker": "worker-7f3a", "queue": "kitchensink.v1.code", "…": "…" }
}
```

For `<workflowId>|main` and `<workflowId>|handlers` the poll builds a task that is not persisted
(`syntheticTask`): `taskType` and `taskDefName` the code type, `referenceTaskName` the lane,
`status IN_PROGRESS`, `scheduledTime` the marker's `queuedAt`, `startTime` its `leasedAt`, the
marker, and in `inputData` either `input` (the workflow input, bootstrap) or `messages` (the
handler lane):

```json
{
  "taskId": "a4e2…|handlers", "taskType": "kitchensink.v1.code", "taskDefName": "kitchensink.v1.code",
  "referenceTaskName": "handlers", "workflowInstanceId": "a4e2…", "workflowType": "kitchensink",
  "status": "IN_PROGRESS", "workerId": "worker-7f3a",
  "inputData": { "messages": [ { "id": "…", "workflowId": "a4e2…", "payload": { "kind": "signal", "name": "cancel", "id": "<client id>", "args": { "…": "…" } }, "receivedAt": "…" } ] },
  "continuation": { "lane": "handlers", "n": 3, "state": "LEASED", "epoch": 1, "…": "…" }
}
```

The messages are claimed from the workflow message queue with
`claim(workflowId, handlerBatchSize, "handlers#<n>", n)`: a re-run of the same continuation gets
the same messages, the next run's claim deletes them, and a claim with a lower ordinal than the
highest seen is fenced.

## 7. Completion

`POST /api/tasks/update-v2` with a `TaskResult` (`TaskClient.updateTaskV2`). `taskId` is the id the
poll returned: the awaited task's id, or a synthetic id. `WorkflowExecutorOps.updateTask` applies a
continuation's completion in the decide it triggers, in that decide's one acquisition of the
execution lock; `updateTaskLocked` routes a synthetic id, or a task whose
marker is `LEASED` and whose `outputData` has `protocol`, to `completeContinuation`. Any other
result for the same task id is an ordinary task result (the worker that ran `task_10` reporting
it), so the two never collide.

The output, as `Replayer.process` and `Workers.complete` build it:

```json
{
  "workflowInstanceId": "a4e2…", "taskId": "6f1c…", "workerId": "worker-7f3a", "status": "COMPLETED",
  "outputData": {
    "protocol": 2,
    "n": 2,
    "epoch": 1,
    "resumedWith": { "stage": "new" },
    "recorded": { "token": { "value": "t-91…", "type": "java.lang.String" } },
    "done": false,
    "fragment": [
      { "name": "sub_flow_1", "taskReferenceName": "sub_flow_1", "type": "SUB_WORKFLOW",
        "inputParameters": { "…": "…" }, "subWorkflowParam": { "name": "sub_flow_1", "version": 1 },
        "optional": true,
        "metadata": { "lane": "fork_1.0", "site": "KitchenSinkWorkflow.kitchenSink:112",
                      "continuation": { "lane": "fork_1.0", "n": 3 } } }
    ],
    "variables": { "stage": "classic" }
  }
}
```

| Key | Required | Meaning |
|---|---|---|
| `protocol` | yes | Must be the number `2`; anything else is rejected (`RejectedFragmentException`, reason `protocol`). |
| `n`, `epoch` | yes | Must equal the marker's; otherwise `ConflictException` (HTTP 409), metric `native_stale_completion`. A late, duplicate or superseded completion. |
| `fragment` | yes, may be empty | Inserted after the awaited task (top level for the bootstrap), or after the entry the first entry's `metadata.after` names (§8 rule 7, §9 `switchOn`). |
| `recorded` | no | Values of `once`, `now`, `random` computed by this continuation, `{ "<name>": { "value": …, "type": "<class>" } }`; stored on the marker. |
| `variables` | no | The lane's `@Variable` values after the continuation. Only entries that differ from `resumedWith` are applied. |
| `resumedWith` | no | The variables the continuation started from. A variable it changed that another completion changed meanwhile defers the completion (`deferred: "variable 'x' was changed by another completion"`). |
| `done` | yes | `true` when the lane returned or threw. |
| `result` | when `done` without error | Main lane: the workflow output. Other lanes: `{ "value": … }`. |
| `error` | when `done` by throwing | `{ "type": "…", "message": "…", "ref": "<task>" }`; `ref` is present for an uncaught `TaskFailure`. Main lane: the workflow fails with `message` and `failedTaskId` from `ref`. Other lanes: raised in the lane that joins. |
| `releases` | handler lane | `[ { "ref": "condition", "output": { "satisfied": true } } ]`: `WAIT` tasks the server completes with that output in the same write. |
| `replies` | handler lane | `{ "<client message id>": { "value": …, "at": <millis> } }` or `{ "error": "…", "at": … }`; replaces `nativeState.replies`. |
| `seen` | handler lane | the handled message ids, at most 500; replaces `nativeState.seen`. |

A completion must carry `protocol`, `n` and `epoch` whatever its status: a result on a task row
without `protocol` is an ordinary task update. Statuses other than `COMPLETED`: `FAILED_WITH_TERMINAL_ERROR` (the SDK's `NonDeterminismException`)
holds the execution: the marker goes back to `RUNNABLE` with `deferred` set to
`diverged: <reasonForIncompletion>`, and the workflow stays `RUNNING` so that a rollback or a fixed
build continues it. `FAILED` (an unexpected worker exception) defers the marker the same way. Both
are offered again after 1, 2, 4, ... seconds by `attempts`, at most 60. A main lane that returned while the handler lane has work is not offered again until that lane has settled; `repairAndWake` wakes it after the handler run.

The SDK processes a task returned here only if its marker is `LEASED`. The response is a `Task` of the code type (a stand-in, not the awaited task): `update-v2` then
polls the code type in the completion's domain, so the worker's next continuation comes back in
the same request. A deferred completion is not an error; the marker is `RUNNABLE` again with
`deferred` set and the wake-up is pushed (except a main lane waiting for the handler lane, §5).

The SDK retries a 5xx or a connection failure four times, 250 ms apart and growing, and treats a
4xx as final: nothing is lost, because the marker either advanced or was re-leased elsewhere.

## 8. Fragment rules

`NativeContinuationService.validateFragment`, applied before anything is written. A violation is a
`RejectedFragmentException`; the marker is recorded `APPLIED` with `done` and
`error.message`, metric `native_fragment_rejected` with the reason, and the workflow fails with the
message.

1. `size`: at most `nativeWorkflow.maxFragmentTasks` entries (nested counted, default 500),
   `nativeWorkflow.maxFragmentBytes` serialized (1 MB), and the definition after the append at most
   `nativeWorkflow.maxDefinitionTasks` (5000).
2. `reference`: every entry at any depth has a `taskReferenceName`, absent from the current
   definition and unique within the fragment.
3. `type`: a type `DeciderService` has a mapper for. `TERMINATE` is refused: a native workflow ends
   through its code.
4. `lane`: every entry carries `metadata.lane`.
5. `continuation`: a `metadata.continuation` is `{ "lane": "<lane>", "n": <number> }`, and a lane
   pauses at most once in one fragment.
6. `definition`: `SIMPLE` tasks get their task definition through
   `MetadataMapperService.populateTaskDefinitions`, with the same missing-definition policy as
   registration; `SUB_WORKFLOW` entries may carry an inline `workflowDefinition` or a name and
   version.
7. `fragment`: the awaited task's reference must still be in the definition (a rerun can remove
   entries). When the first entry carries `metadata.after`, the entry it names must be in the
   definition too (`NativeContinuationService.anchorOf`, `WorkflowDef.insertAfter`): the fragment
   goes after that entry instead of after the awaited task. An anchor that names nothing is
   rejected with "task <anchor> is not in the execution's definition".

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
6. `metadata.after` is set only on the first entry of a fragment, and only when the lane resumed
   inside a `switchOn` case whose body has now returned; it names the switch (`Lane.anchorAfter`,
   consumed by the next `emit`). Within one fragment nothing is anchored: the case body's entries
   go straight into the case list, and the entry after the body goes back to the list that holds
   the switch.
7. A `SWITCH` entry declares every case the code declared, plus a `defaultCase`, and fixes
   `inputParameters.switchCaseValue` to the key the code took, so the server's `value-param`
   evaluation selects the case whose entries the code scheduled. The untaken cases (and
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
| `tasks.task1(a, b)` on a task stub, or `task(Tasks::task1, a, b)` | `SIMPLE` `task_1` with `inputParameters {a, b}`, `optional: true`; pauses |
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
| `awaitExternal(ref, T).await(timeout)` | `WAIT` `ref` with `duration` = timeout; completed by `POST /api/tasks/{workflowId}/{ref}/COMPLETED`; pauses |
| `awaitCondition(cond, timeout)` | `WAIT` `condition` (then `condition_2`, …) with `duration`; the handler lane's completion names it in `releases` when `cond` holds; pauses |
| `human(ref)…await(T, timeout)` | `HUMAN` `ref` with assignment and form input, `optional: true`, inline `taskDefinition` (`timeoutSeconds` = timeout, `timeoutPolicy: RETRY`, `retryCount: 0`); `TIMED_OUT` becomes an empty `Optional`; pauses |
| `http(ref)…` | `HTTP` `ref` with `http_request` and an inline retrying definition; pauses |
| `event(ref).to(sink).send(p)` | `EVENT` `ref` with `sink`, `optional: true`; no pause |
| `llm(ref)…`, `mcp(ref)…`, `agent(ref)…` | `LLM_CHAT_COMPLETE`, `LIST_MCP_TOOLS` / `CALL_MCP_TOOL`, `AGENT`; pauses |
| `switchOn(name, value).when(key, body)…otherwise(body).get()` / `.run()` | `SWITCH` `name` with `evaluatorType: value-param`, `expression: switchCaseValue`, `inputParameters: {switchCaseValue: "<value as a string>"}`, `decisionCases` with one list per declared case, `defaultCase`, `metadata.lane`; no `optional`, no pause of its own. The taken body runs inline and its entries go inside that case's list; the lane's marker sits on whatever it paused on in there. Every untaken case, and `defaultCase` when a declared case was taken and `otherwise` exists, holds the preview of its body (rule 8 above): the entries it would have emitted, named `<base>_if_<key>`, with `metadata.preview: true`. When the lane resumes inside the case and the body returns, the next entry carries `metadata.after: "<name>"` and the server inserts it after the switch. The value is written with `${` escaped, so it is never evaluated. No match and no `otherwise`: the `SWITCH` is still emitted with every case previewed, then `WorkflowFailedException`, the lane ends with `error` |
| `return value` | empty fragment, `done: true`, `result` |
| `throw` | empty fragment, `done: true`, `error`; `error.ref` for an uncaught `TaskFailure` |

The kitchensink's `oddEvenDecision`, as recorded in `kitchensink-odd.json` (odd path, so case
`"1"` was taken; the fork and the join of the two `async` blocks are inside it and the main lane
paused on the join; case `"0"` holds the preview of `task_4` and of the dynamic fork and join its
`fanOut` would run; the `otherwise` body throws
before any call, so `defaultCase` is empty):

```json
{ "name": "oddEvenDecision", "taskReferenceName": "oddEvenDecision", "type": "SWITCH",
  "evaluatorType": "value-param", "expression": "switchCaseValue",
  "inputParameters": { "switchCaseValue": "1" },
  "decisionCases": {
    "0": [ { "name": "task_4", "taskReferenceName": "task_4_if_0", "type": "SIMPLE",
             "inputParameters": { "oddEven": { "mod": 5, "oddEven": "1" } },
             "metadata": { "lane": "main", "site": "…", "preview": true } },
           { "name": "fanout1", "taskReferenceName": "fanout1_if_0", "type": "FORK_JOIN_DYNAMIC",
             "dynamicForkTasksParam": "dynamicTasks",
             "dynamicForkTasksInputParamName": "dynamicTasksInput",
             "metadata": { "lane": "main", "site": "…", "preview": true } },
           { "name": "fanout1_join", "taskReferenceName": "fanout1_if_0_join", "type": "JOIN", "joinOn": [],
             "metadata": { "lane": "main", "site": "…", "preview": true } } ],
    "1": [ { "name": "fork", "taskReferenceName": "fork", "type": "FORK_JOIN", "…": "…" },
           { "name": "fork_join", "taskReferenceName": "fork_join", "type": "JOIN", "joinOn": [],
             "metadata": { "lane": "main", "site": "…", "continuation": { "lane": "main", "n": 4 } }, "…": "…" } ]
  },
  "defaultCase": [],
  "metadata": { "lane": "main", "site": "KitchenSinkWorkflow.kitchenSink:…" } }
```

The continuation that resumes on `fork_join` returns from the case body and completes with a
fragment whose first entry is anchored after the switch:

```json
[ { "name": "get_es_1", "taskReferenceName": "get_es_1", "type": "HTTP", "optional": true, "…": "…",
    "metadata": { "lane": "main", "site": "…", "after": "oddEvenDecision",
                  "continuation": { "lane": "main", "n": 5 } } } ]
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

`protocol` is carried in the skeleton's `metadata.native.protocol` and in every completion. The
server accepts protocol `2` (`NativeContinuationService.PROTOCOL`) and rejects anything else with
a terminal failure naming the version it expected. Fragment rules may tighten in a new protocol
version without touching running executions, since each execution carries its version in
`nativeState.protocol`. Protocol 1 executions (CODE and RECORD tasks) are not readable by this
server.
