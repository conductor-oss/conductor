# Durability guarantees for native workflows

A native workflow is a Conductor workflow written as Java code. This page states what survives
which failure, how, and how each guarantee was verified. It builds on Conductor's own guarantees in
[docs/architecture/durable-execution.md](../../docs/architecture/durable-execution.md). How these
guarantees compare with other durable execution engines is in [COMPARISON.md](COMPARISON.md).

The model in one paragraph: the server owns all state. An execution is its persisted definition,
its task rows, its variables and a small block of engine state (`nativeState`). Workflow code
holds no state of its own between continuations: every continuation is processed by re-running
the method against the continuations the server has applied, which are recorded on the tasks the
code paused on, and every value the code computed that must not change (ids, clock reads, random
numbers) is recorded on the continuation that produced it. So a worker, or the server, can
disappear at any moment and the execution continues from its last recorded step when something
comes back.

## 1. Guarantees

Status: **verified** means exercised end to end against a running server; **tested** means
covered by unit or integration tests; **by design** means it follows from existing Conductor
behaviour and is not separately tested for native workflows.

`<!-- TODO: verify against live server -->` The "verified" statuses below were earned on protocol
1 (continuations as tasks). The mechanisms are described as they are on protocol 2; the chaos run
and the bench are being re-run on it, and have no results yet for the shared-queue routing described below.

### Surviving infrastructure failure

| # | Guarantee | How | Status |
|---|---|---|---|
| 1 | A server restart, including a crash (SIGKILL), loses nothing; executions continue where they stopped. | All state is in the database: definition, tasks with their continuation markers, `nativeState`, variables, queues, timers, and the workflow message queue (signals). The sweeper re-evaluates running executions on start, and every decide of a native workflow repairs its markers (`repairAndWake`): a runnable marker gets its wake-up pushed again, a waiting marker on an evaluated task becomes runnable, a waiting handler lane with queued messages becomes runnable. | **verified**: chaos run kills the server mid-execution |
| 2 | A worker crash or restart does not lose or repeat workflow progress. | Workers are stateless. Each continuation re-runs the code against the applied markers and the tasks, so any host of the same code can resume. | **verified**: chaos run kills workers twice |
| 3 | When a worker is gone, another worker continues its executions. | By default a wake-up goes to the shared queue of the code type, and any worker polling it can lease it. A continuation handed to a dead worker stays unacknowledged for the lease (`continuationTimeoutSeconds`, 30 s by default); the queue redelivers it and the next worker runs the same offer. With an opt-in domain list, a wake-up sitting in a dead domain's queue past its lease is offered again, at a new epoch, in the live queue by the next decide. While a marker is `RUNNABLE` or `LEASED` the decide is never postponed past `workflowOffsetTimeout` (30 s), whatever the in-progress tasks would otherwise allow, and the cap replaces a longer earlier postpone (`WorkflowExecutorOps.decide`, `NativeContinuationService.needsPromptRevisit`). | `<!-- TODO: verify against live server -->` chaos and bench results for this design are not in yet |
| 4 | A restarted worker picks up work immediately. | It polls the shared queue again; nothing is tied to its identity. | `<!-- TODO: verify against live server -->` chaos and bench results for this design are not in yet |
| 5 | With no worker at all, executions wait, then continue when one returns. | A runnable marker's message stays queued; a leased one is redelivered when its lease expires. Nothing times a waiting continuation into failure. | **verified**: chaos run has 20 s with no worker |
| 6 | A task worker that dies holding a task does not lose the task. | The response timeout reschedules it (policy `RETRY`). | by design (Conductor) |
| 7 | A crash in the middle of applying a continuation leaves the execution consistent. | A completion is applied inside the decide it triggers, under the execution lock, in memory, then the workflow row (definition, variables, `nativeState`) is saved before the task that carries the marker. A crash before the first write leaves the lease to expire and the continuation runs again; a crash between the writes leaves the fragment in the definition with the marker still leased, and the re-run continuation finds its entries already present. The bootstrap's record and its fragment are in the same row. | **tested** (unit); the durability bench kills the server mid-apply; the lost-write bug this guards against was found and fixed on protocol 1 |

### State and side effects

| # | Guarantee | How | Status |
|---|---|---|---|
| 8 | A completed task never runs again on recovery, retry or rerun of later tasks. | The code reads the recorded result instead of scheduling the call. Operator retry reruns only from the task that failed (`failedTaskId`, from the lane's `error.ref`), never from failures the code handled. | **verified**: kitchensink and operator checks |
| 9 | Values recorded with `once`, `now` and `random` never change. | They are written in `recorded` on the marker of the continuation that produced them, in the same write as its fragment; a replay reads them back from the applied markers. | **verified** on protocol 1: rerun keeps the recorded tracking id |
| 10 | `@Variable` fields are durable and consistent with progress. | Written to `workflow.variables` by every completion, only the entries the continuation changed; `resumedWith` on the marker records what the continuation started from, and each replayed segment restores the fields to it. | **verified** |
| 11 | Starting twice with the same idempotency key starts once. | Workflow id derived from name and key; `RETURN_EXISTING` returns the first execution, `FAIL` refuses. | **verified** |
| 12 | Tasks are at-least-once; make side effects idempotent. | A worker can crash after its side effect and before reporting; the task is redelivered. This is the same contract as Conductor tasks. | by design |

### Waiting

| # | Guarantee | How | Status |
|---|---|---|---|
| 13 | Timers survive restarts and fire after at least their duration. | `sleep` is a WAIT task with an absolute deadline in the database; `repeatUntil` is a server-side DO_WHILE with WAITs. No worker is involved while waiting. | **verified** at runtime; across a server restart by design |
| 14 | Human tasks and external callbacks wait indefinitely, across restarts and deploys. | HUMAN and WAIT tasks stay IN_PROGRESS in the database until completed by reference or timed out. | **verified** at runtime; across restarts by design |
| 15 | Signals, updates and queries are never lost, even when sent before the code is ready, while no worker is running, or across a server restart. | Messages go to the workflow message queue, which is a database table on SQLite, Postgres and MySQL (and Redis on Redis deployments). The endpoint pushes under the execution lock and wakes the handler lane (`nativeState.handlers`); the handler poll claims messages under `handlers#<n>`, fenced by `n`, so a re-run gets the same messages and a stale run can take none. An execution does not complete successfully while messages are queued or a handler run is runnable or leased: the completing continuation is deferred and run again, so its result reflects every message accepted before it ended. It is not offered again until the handler lane has settled (`waiting for handlers: `, `repairAndWake`), so it cannot keep winning the one-continuation slot ahead of the handlers. | **verified** on protocol 1: messages sent right after start and during worker outages are delivered; the durability bench checks every acknowledged signal is applied exactly once and in order under worker and server kills, pauses and partitions; durable queue **tested** on SQLite across a simulated restart and under concurrency, on Postgres and MySQL |
| 16 | An update returns its handler's value to the caller, and updates to one execution apply in a single order consistent with real time. | The handler's reply is written to `nativeState.replies`, keyed by the client's message id, and kept for 10 minutes (at most 500); the client reads the execution until it appears. One handler run at a time per execution (the marker's lease). | **verified** on protocol 1: the bench checks that an update acknowledged before another was sent returned the smaller value, under cluster faults; 1000 concurrent updates to one execution returned exactly 1..1000 with replies capped at 500 |
| 16a | A query returns the execution's applied state and never changes it. | The client runs the `@Query` method on an instance restored from the execution's variables; nothing is written. Works on completed executions. A signal is visible once applied, not when acknowledged. | **verified**: the bench checks every answer is a prefix of the final state, a client's answers never go backwards, a query after completion equals the output, and a query's attempted write never appears |

### Failure handling

| # | Guarantee | How | Status |
|---|---|---|---|
| 17 | Every task is retried before its failure counts. | Worker tasks: 3 retries, exponential backoff, by default, set with `@WorkerTask(retryCount, retryDelaySeconds)`. HTTP and LLM steps: the same, set with `retries(count, delay)`. `NonRetryableException` fails at once. | **verified**: an HTTP step makes 4 attempts; a worker task failing once succeeds on retry |
| 18 | A failure the code does not handle fails the workflow, with the failing task as the cause. A failure it handles does not. | Tasks are `optional`; after retries the failure reaches the code as `TaskFailure`, and the task keeps its real status (FAILED, TIMED_OUT). Uncaught, the lane's continuation ends with `error.ref` naming the task, and the engine fails the workflow with it as `failedTaskId`. | **verified** |
| 19 | Operators can recover any execution: retry, rerun from a task, restart. | Native-aware versions of Conductor's operations, on the markers. | **verified** on protocol 1 |
| 20 | Timeouts at every level: workflow (`@Workflow(timeout)`), task response, task poll, human task, external wait. | Conductor task and workflow timeouts; waits are WAIT and HUMAN tasks with durations. | **verified** for human and external waits; others by design |

### Correctness over time

| # | Guarantee | How | Status |
|---|---|---|---|
| 21 | A continuation is applied exactly once, and two workers never both apply the same one. | Only a decide offers a continuation, under the execution lock, one at a time per execution, with an epoch that increments per offer; the poll writes nothing. A completion must name the offer's `n` and `epoch` and find the marker still `LEASED`, or it is refused with HTTP 409 (`native_stale_completion`); two workers handed the same offer (a redelivery) cannot both apply it. | **tested** (unit and harness: `aStaleCompletionIsRefused`, "a completion that does not match the current lease is refused") |
| 22 | Code changes with executions in flight are either compatible or detected. | Calls are matched by name on replay, so adding logging, reordering independent calls, or adding calls in new code paths is safe. A replay that reaches a call the recording does not have holds the execution, with a message naming the call and its source line, and retries with backoff until a compatible build runs it. `DurableReplay.assertCompatible` runs the same check in CI against recorded executions. | **verified** on protocol 2: the replay test passes against an execution re-recorded on protocol 2; the check that it fails on an inserted call was last run on protocol 1 |
| 23 | An execution can prefer the workers of one build, when its start asks. | A domain list on the start (`.taskToDomain(Map.of("<name>.v1.code", "build-7,NO_DOMAIN"))`); workers poll their `buildId` as a domain; a wake-up goes to the first domain with a poller in the last 10 s, else the last entry. A worker's build is never captured automatically. | implemented; `<!-- TODO: verify against live server -->` not chaos-tested |
| 24 | Child workflows are durable executions in their own right. | `child(...)` is a SUB_WORKFLOW; the child has its own state. | **verified** at runtime |

### Failure semantics: FAILED, FAILED_WITH_TERMINAL_ERROR and optional

"Terminal" describes the task, not the workflow: a task that ends FAILED_WITH_TERMINAL_ERROR is not
retried. Whether the workflow ends is decided by the code.

| Task outcome | Standard workflow, not optional | Standard workflow, optional | Native workflow (tasks the code waits on are optional) |
|---|---|---|---|
| FAILED, TIMED_OUT | retried; when retries run out the workflow fails | retried; then COMPLETED_WITH_ERRORS and the next task runs | retried; then COMPLETED_WITH_ERRORS and the code receives `TaskFailure` naming FAILED or TIMED_OUT |
| FAILED_WITH_TERMINAL_ERROR (`NonRetryableException`) | not retried; the workflow fails | not retried; COMPLETED_WITH_ERRORS, next task runs | not retried; COMPLETED_WITH_ERRORS and the code receives `TaskFailure` naming FAILED_WITH_TERMINAL_ERROR |
| `TaskFailure` caught by the code | | | the workflow continues (the kitchensink's charge-failure path) |
| `TaskFailure` not caught, including in an `async` lane that is joined | | | the workflow fails, with the task as the cause; operator retry reruns from that task |
| The code's replay diverges from its recording, or a completion is invalid | | | the workflow fails: the marker is recorded with `done` and `error`, and the message is the reason |
| A human task times out | | | the code receives an empty result, not a failure |

The standard columns are Conductor's existing behaviour, unchanged; the native column is tested
by `failureMatrixForNativeWorkflows` (server) and `FailureSemanticsTest` (SDK), and on the live
server by the kitchensink and the operator checks.

## 2. What you must configure

- `conductor.workflow-message-queue.enabled=true`, or signal, update and query handlers never run
  (the server logs this when a native definition with handlers is registered).
- A persistent database (SQLite, Postgres, MySQL or Redis); the server itself is stateless.
- The workflow execution lock enabled (`conductor.app.workflowExecutionLockEnabled`, default true):
  leases and completions are serialized by it.
- Nothing per worker: any worker can run any continuation. `CONDUCTOR_WORKER_ID` (default the host name) only labels poll data and leases.

## 3. What is not guaranteed

- **Exactly-once side effects in tasks.** Tasks are at-least-once (guarantee 12).
- **Exactly-once delivery of messages sent by other clients.** The Java client stamps each
  message with its own id and the handler lane skips an id it applied among the last 500
  (`nativeState.seen`), so a send the client retried is applied once. A client that posts to the
  message endpoint directly and retries without that id can still have a message applied twice.
- **Delivery to an execution that fails or is terminated.** Messages still queued when an
  execution ends other than by completing are discarded, logged and counted
  (`workflow_messages_discarded`). A message accepted in the instant between the last check and
  the completion write can also be discarded this way.
- **Prompt recovery of a continuation whose poll response was lost.** If the network fails after
  the server leases a continuation to a worker and before the worker receives it, the continuation
  stays leased until the lease expires and the queue redelivers it. The lease is 30 s by default
  (`workers().continuationTimeout(...)`; an hour in dev mode, for breakpoints), and must exceed the
  longest replay of an execution. An execution started before its workers registered a new
  timeout keeps the old one, since `nativeState` snapshots it at start. The execution, and its
  handler lane if that is where it happened, waits that long plus the queue's redelivery
  interval. This is the stall Jepsen reported for Conductor tasks.
- **Instant failover.** On the shared queue, takeover from a dead worker takes up to the lease
  plus the queue's redelivery of the unacknowledged message. With domain lists there is one more
  gap: domains are resolved when a message is pushed, so if every worker of a domain dies inside
  the 10-second poll window, a continuation pushed then waits for the next decider pass (up to about
  30 s, plus the sweeper's pass) before `repairAndWake` moves it; an open continuation caps the
  decider-queue postpone at that offset.
- **Unbounded executions.** Each continuation re-runs the method from the top, and an execution's
  definition is capped at `conductor.app.nativeWorkflow.maxDefinitionTasks` (5000). Very long
  loops belong in `repeatUntil` and `forEach` (server-side) for now; the large-loops round removes
  the limit.
- **Automatic pinning to a build.** An execution uses a build's
  domain only when its start names one, and a list ending in `NO_DOMAIN` falls back to any worker
  when the build has no live pollers (guarantee 23). A list without it waits for its last domain.
- **Cancellation delivered to the code.** Terminating an execution cancels its pending tasks; the
  code does not get a chance to compensate.

## 4. How it is verified

| Check | Where | What it does |
|---|---|---|
| Kitchensink | `java-sdk/native-examples`, `./gradlew :native-examples:run` | 3 executions, 49 checks: forks, child workflows, fan-out, timers, server-side loop, human task, callback, signals, update, query, retries, caught and uncaught failures, idempotent start |
| Chaos run | `java-sdk/native-examples/chaos.sh` | the kitchensink while a worker is killed, no worker runs for 20 s, a worker with a new id takes over, the server is killed and restarted, and a worker is killed and restarted with its id; all 49 checks must pass |
| Operator checks | `operator.OperatorChecks` | retry, rerun from a task and restart on a native execution, and task retries |
| Replay test | `KitchenSinkTest` | current code against a recorded execution |
| Durability bench | `java-sdk/native-examples/bench.sh`, see `DURABILITY-BENCH.md` there | Jepsen-style: executions with tasks, async lanes, a timer, a child workflow, recorded values, signals and updates run on three worker processes while workers and the server are killed and paused and workers are partitioned from the server; a checker compares the client history, worker ledgers and final state against these guarantees. Summaries carry each task's `continuation` and the workflow's `nativeState`; the checker derives retried continuations (`attempts > 1` or `deferred`) and stalled ones (`appliedAt - queuedAt > 20 s`) from the markers |
| Durability bench, cluster mode | the same bench against three servers on Postgres, with database pauses, kills and per-server partitions, cluster-wide kills, and up to three overlapping fault injectors | every acknowledged signal and update applied once, every execution completed with the right output. With `switchOn` on protocol 2, the seed 23 re-run (`p2-seed23b`) was reported valid with 16 faults and 50 of 50 executions completed; the earlier `p2-switch` run was INVALID (one execution FAILED) and was a real bug, not an artifact: a continuation that failed sent no `protocol`, so the server treated it as an update to an ordinary task; `update-v2` handed back a scheduled task of another execution, and the SDK replayed it with the wrong workflow's code. Fixed by sending `protocol` on failed continuations and processing only leased hand-backs. <!-- TODO: verify against live server --> |
| Bench mutants | server patches (`no-completion-deferral`, `no-variable-fencing`, `ignore-epoch`, `join-ignores-lanes`, `workflow-saved-last`, `no-claim-fence`, `destructive-pull`, `no-stale-write-guard`, `terminate-on-storage-errors`) and SDK patches (`no-message-dedupe`, `drop-some-signals`, `no-reply-prune`) | patches in `java-sdk/native-examples/mutants/`, never in the shipped code; each deliberate bug must fail a bench check or an independent check (Elle, Porcupine) |
| Server tests | `NativeContinuationServiceTest`, `NativeJoinTest`, `TestDeciderOutcomes`, `NativeDefinitionEditsTest`, `SqliteWorkflowMessageQueueDAOTest`, `PostgresWorkflowMessageQueueDAOTest`, `MySQLWorkflowMessageQueueDAOTest`, `NativeWorkflowSpec` (harness) | lease and completion rules, stale completions, deferral, variable fencing, fragment rules, live joins, retry then keep status, rerun on markers, durable message queue, the wire protocol end to end |

Results of the last runs are in [06-implementation.md](06-implementation.md).

## 5. Gaps found while writing this page, and how they were closed

Each row is a gap and the mechanism that closes it as it stands on protocol 2.

| Gap | Fix |
|---|---|
| Each worker took a random id at start, and a continuation queued for a dead worker's id could wait forever | continuations go to the shared queue by default, so no worker id holds one; a leased continuation is redelivered when its lease (30 s) expires, and with domains a queued wake-up is re-pushed to a live queue by the next decide |
| Signals were held in memory on SQL backends and lost on a server restart | `JdbcWorkflowMessageQueueDAO` with migrations for SQLite, Postgres and MySQL, registered when the queue is enabled |
| The build id was recorded but workers never polled it, so routing to the build did nothing | workers poll their `buildId` as a domain; an execution uses it only when its start names that domain |
| Server steps (HTTP, LLM) had no retries | inline task definitions with 3 retries and exponential backoff |
| Native tasks used the rarely used `permissive` flag | `optional`, with how the task failed recorded in its output in native workflows |
| An operator retry re-ran a task attempt that a retry had already superseded | native retry reruns only from the cause task |
| The client failed if the server restarted while it waited for a result | client reads and message sends retry for up to two minutes |
| (bench) Concurrent pulls and pushes on SQLite failed with SQLITE_BUSY_SNAPSHOT, and a failed pull stopped the handler lane | single-statement push and pop on SQLite, lock-conflict retries |
| (bench) A JOIN could complete while a lane's last continuation still had tasks to append, failing the execution | `lanesSettled`: a branch ends only when its last task's marker is applied and ended the lane |
| (bench) Acknowledged signals were discarded when an execution completed with them queued | completion deferred while messages are queued or the handler lane is runnable or leased; discarded messages logged and counted |
| (bench) An update sent to an execution that then ended waited three minutes; a refused message was retried for two | the client stops waiting when the execution is terminal, and does not retry a 4xx |
| (bench) A message the client re-sent after an ambiguous failure was applied twice | client-chosen message ids, de-duplicated against `nativeState.seen` |
| (bench) Two continuations running at once on different workers: the later completion's stale copy of the variables erased the other's writes, losing signals | completions apply only the variables they changed (`resumedWith`); a conflicting completion is deferred and run again on the current values |
| (bench) A JOIN failed the execution on a continuation that was being retried or deferred | `lanesSettled` waits on any marker that is not applied and done |
| (bench) A lost poll response stalled a continuation for 120 s | 30 s default lease, configurable |
| (bench) A server killed mid-apply saved the continuation as applied but not the definition holding its fragment; the execution was stuck forever | the workflow row is saved before the task that carries the marker |
| (bench) A worker replaying an execution between a completion being saved and applied reported a false divergence and failed the execution | the SDK replays only `APPLIED` markers |
| (cluster bench) Workers registering the same definitions at once on Postgres crashed on a duplicate key | atomic upserts; the SDK retries registration |
| (cluster bench) A server dying mid-pull stalled the handler lane for the hour-long reservation | messages are claimed, not popped; the handler lane's poll claims under `handlers#<n>` with a 30 s lease |
| (cluster bench) A database outage while scheduling a loop iteration failed the loop and its workflow | storage outages abort the decide for a retry instead of terminating |
| (cluster bench) A stale run of an old pull, resumed after a pause, claimed new signals that the current pull then deleted as delivered | claims fenced by an ordinal (the handler run number `n`); the executor does not overwrite a task another run completed |
| (review) Update replies were kept forever in the execution's variables, and a query ran in the handler lane, could change state, and failed on completed executions | replies in `nativeState.replies`, expiring after 10 minutes, at most 500 kept; queries run in the client on the applied state |
| (review) Every pause was a hidden task, doubling the task count and needing a UI fold; recorded values were tasks too | protocol 2: continuation markers on the tasks the code paused on; recorded values on the marker; the bootstrap and handler lane as engine state |
| (Jepsen review) Abandoned workflows: written, then queued; a crash between left them unevaluated | queued first, then written |
| (Jepsen review) Lost workflows: a start that failed after writing the workflow deleted it | starts never delete a workflow they may have written |
| (Jepsen review) The sweeper re-queued a task a worker had just polled, so two workers ran it | the sweeper re-reads the task before re-queueing |
| (chaos) A start failed with a retryable server error (the execution lock was contended right after a restart) and the client gave up | starts with an idempotency key retry server errors and lost connections for up to two minutes; starts without a key are never retried, since a repeat could create a second execution |
| (bench, protocol 2) A continuation that failed sent no `protocol`, so its result landed on an ordinary task, and `update-v2` handed back a scheduled task of another execution, which the SDK replayed with the wrong workflow's code | every continuation result carries `protocol`; the SDK processes only leased hand-backs; `WorkerProtocolTest`, harness spec |
| (design, protocol 2) Two lanes' continuations on different machines could allocate the same reference names | one continuation per execution at a time, held by the server (`oneContinuationOfAnExecutionIsOfferedAtATimeAndAPollWritesNothing`) |
| (chaos, protocol 2, under the since-removed per-worker routing) A worker was killed right after its continuation was queued to its own domain queue; the sweeper's decide had postponed the workflow's decider-queue entry on the strength of its in-progress tasks, up to `maxPostponeDurationSeconds` (an hour), so no decide ran `repairAndWake` and the wake-up sat in the dead worker's queue | `WorkflowExecutorOps.decide` caps the postpone at `workflowOffsetTimeout` (30 s) and always writes it while a continuation marker is `RUNNABLE` or `LEASED` (`needsPromptRevisit`: a task marker, the bootstrap or the handler lane), so the next decide re-routes the wake-up to a live queue |
