# 06. Implementation status

Everything in [03-server-changes.md](03-server-changes.md) is implemented on this branch, and the
Java SDK side is implemented in `conductor-oss/java-sdk` on branch `design/native-workflows`. The
protocol is version 2 (continuation markers on the tasks the code paused on; see
[07-engine-state.md](07-engine-state.md)). This page records how to run it, what was validated,
the server bugs found along the way, and where the implementation differs from the earlier
documents. Where they disagree, this page and the code are right.

## Running it

Server, from this repository:

```bash
./gradlew :conductor-server:bootJar -x test
java -jar server/build/libs/conductor-server-*-boot.jar \
  --server.port=8099 \
  --conductor.workflow-message-queue.enabled=true \
  --conductor.app.nativeWorkflow.waitTimeScaleEnabled=true
```

Kitchensink, from the Java SDK repository. The SDK's Gradle build needs JDK 21 to run.

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8099/api
./gradlew :native-examples:run      # three executions; prints each check; exits 0 on success
./gradlew :native-examples:test     # in-memory tests and the replay test
./gradlew :native-examples:installDist
java -cp "native-examples/build/install/native-examples/lib/*" operator.OperatorChecks
```

## What was validated

`<!-- TODO: verify against live server -->` The rows below were recorded while the branch ran
protocol 1 and are being re-run on protocol 2; the server unit tests and the harness spec were
rewritten for protocol 2 with it.

| Check | How | Result |
|---|---|---|
| Kitchensink odd path: `switch_oddEven`, a plain Java `switch` whose case `"1"` holds two async lanes, each a task then a child workflow, joined | `kitchensink.Main` against the server | passed, 2026-11-11 (118 checks over the odd, even and failure executions): the `SWITCH` task's `selectedCase` equals the input's `oddEven` (`Main.caseTaken`), and every plain `if` and `switch` of the composition section ran as a `SWITCH`, with the loop tags of the loops around it |
| Kitchensink even path: fan-out over planned tasks | same | passed |
| Kitchensink failure path: charge fails terminally, code catches `TaskFailure` | same | passed |
| Update with reply, query, signal, condition wait released by a handler | same, all three executions | passed |
| Human task answered, callback posted, HTTP failure caught, LLM call, event, fire-and-forget child, timers, server-side polling loop with backoff | same | passed |
| Idempotent start (S1) | same: a second start with the key returns the same execution | passed |
| Task retries: the HTTP step makes four attempts before its failure reaches the code; a worker task failing once succeeds on retry | kitchensink and operator checks | passed |
| Retry after an uncaught task failure reruns from that task, without re-running earlier tasks (B7) | `operator.OperatorChecks` | passed |
| Rerun from a task keeps values recorded before it; restart records new ones (B7) | same | passed |
| Every rerun of a decision in code (`RouteWorkflow`): no task and from before the switch decide again; from inside the case and from after it keep the decision and still draw the other case; from the switch itself is refused and changes nothing; restart decides again (B7) | `operator.OperatorChecks` | passed (2026-11-10) |
| Reruns inside a fork (`ForkedWorkflow`): from either branch's task that branch and the task after the join run again and the other branch does not; from the task after the join only it runs again; from the `FORK` or `JOIN` task the rerun is refused and changes nothing | `operator.OperatorChecks` | passed (2026-11-10) |
| Reruns inside the loops the server runs (`LoopedWorkflow`): from the second `forEach` item the second and third items run again and the first does not; from the first `repeatUntil` check the loop polls until the next done check; from the task after both loops only it runs again | `operator.OperatorChecks` | passed (2026-11-10) |
| Reruns from every task of a three-iteration `DO_WHILE` in a standard workflow, with and without a task after the loop (13 reruns) | script against the server | passed (2026-11-10); not kept as a check |
| Reruns from every task of a deeper fork (two-step branch; a branch with its own fork, join and a later task) complete with the first run's output and no divergence | throwaway probe against the server | passed (2026-11-10); not kept as a check |
| Replay test: current code matches a recorded execution, and an inserted call is detected | `KitchenSinkTest.currentCodeStillMatchesARecordedProductionRun`, plus a manual mutation | passed; fails on the mutation. The recording `native-examples/src/test/resources/executions/kitchensink-odd.json` was re-recorded on protocol 2 (2026-11-08): markers on tasks, `nativeState`, no CODE or RECORD entries; and re-recorded again the same day once `oddEvenDecision` became a `switchOn`, and once more when untaken cases gained previews: the definition holds the `SWITCH` with `decisionCases` `"0": [task_4_if_0 (metadata.preview)]` and `"1": [fork, fork_join]`, `inputParameters.switchCaseValue: "1"`, an empty `defaultCase` (the `otherwise` body throws before any call), the task row's `outputData.selectedCase: "1"`, no task row for the preview, and `get_es_1` carrying `metadata.after: "oddEvenDecision"` |
| In-memory test environment | `KitchenSinkTest`, two scenarios | passed |
| Execution shows exactly the code's tasks; continuation on the task panel, engine state on the summary (B5) | `ui-next` on a recorded execution | not yet checked on protocol 2 |
| Server unit tests | `common`, `core`, `http-task`; `NativeContinuationServiceTest` (20 tests, including `aFragmentAnchoredAfterASwitchLeavesTheCaseItPausedIn`), `NativeJoinTest`, `TestDeciderOutcomes`, `WorkflowDefInsertAfterTest` | `NativeContinuationServiceTest`: 17 passed, 0 failures (protocol 2, build results of 2026-11-08) |
| Server integration tests | full `test-harness` suite; `NativeWorkflowSpec` (3 scenarios: linear, fork branch independence, stale completion) | `NativeWorkflowSpec`: 3 passed, 0 failures (protocol 2, build results of 2026-11-08) |
| SDK client tests | `conductor-client`, excluding the four classes that need a server on port 8080 | 984 passed (protocol 1); `SwitchOnTest`: 7 passed, 0 failures (test results of 2026-11-08, including `untakenCasesShowTheTasksTheyWouldHaveCalled`) |
| Switch previews on a live server | kitchensink even path; `switches.Switches` nested run | reported from live runs, not re-run for this page `<!-- TODO: verify against live server -->`: the even run's untaken case `1` shows `fork_if_1` with `task_10_if_1` and `task_11_if_1` leading to `sub_flow_1_if_1` previews; the switches run shows `hold_if_default`, `ship_slow_if_slow`, `ship_fast_if_us` and `hold_if_default_2` |
| Discovered definitions | `DiscoveryTest` (2 tests); hello and kitchensink registered against a live server | `DiscoveryTest`: 2 passed, 0 failures (test results of 2026-11-08). Live, as reported `<!-- TODO: verify against live server -->`: `hello` registered with `[greet, shout]` and an execution held exactly those 2 tasks; the kitchensink registered with 22 top-level entries (the SWITCH with both cases, the fork, the loops) and its executions grew their own 18-entry copies; the definition page renders the discovered graph |
| Conformance suite | `conformance.Conformance` on SQLite: 151 scenarios, each with an expected result and graph, plus branch placement, no scheduled previews and replay of the finished execution | 151 passed in each run on the final code (2026-11-09), no false divergence. Java SDK only; Postgres and MySQL not run |
| Chaos run: worker killed, 20 s with no worker, new worker takes over, server killed and restarted, worker restarted | `native-examples/chaos.sh` | all 49 checks passed; a continuation queued for the killed worker was picked up by the new one. On protocol 2 the run found bug 17 below (a continuation queued to the dead worker's own domain queue while the sweeper had postponed the workflow by an hour) `<!-- TODO: verify against live server -->` the run after that fix is not recorded here |
| Durable message queue | `SqliteWorkflowMessageQueueDAOTest`, `PostgresWorkflowMessageQueueDAOTest`, `MySQLWorkflowMessageQueueDAOTest` | passed; the SQLite test includes concurrent push and pop on a WAL database |
| Durability bench, Jepsen-style | `java-sdk/native-examples/bench.sh` (see `DURABILITY-BENCH.md` there): 30 to 40 executions on three worker processes while workers and the server are killed and paused and workers are partitioned from the server; history, worker ledgers and final state checked | the final two protocol 1 runs (40 executions each, 11 and 16 faults including 4 and 1 server kills) passed every check; earlier runs found bugs 5, 6 and 8 to 12 below; one failure seen once ("Error scheduling tasks") is unexplained, see `DURABILITY-BENCH.md`. On protocol 2 with `switchOn`, the cluster re-run with seed 23 (`p2-seed23b`) was reported valid: 16 faults, 50 of 50 executions `COMPLETED`. The earlier `p2-switch` run was INVALID (one execution FAILED) and was a real bug, not an artifact: a continuation that failed sent no `protocol`, so the server treated it as an update to an ordinary task; `update-v2` handed back a scheduled task of another execution, and the SDK replayed it with the wrong workflow's code. Fixed by sending `protocol` on failed continuations and processing only leased hand-backs. <!-- TODO: verify against live server --> |

`<!-- TODO: verify against live server -->` Per-worker continuation routing has since been removed (continuations go to the shared
queue by default). The chaos and bench rows above were recorded with it; results for the
shared-queue design are not in yet.

### Final verification of the shared-queue design (2026-11-09)

Run in stages that stop at the first failure, on the code as it stands.

| Stage | Result |
|---|---|
| SDK native packages, model tests and examples | 599 tests, 0 failed (not the whole `conductor-client` suite) |
| Server `common`, `core`, `rest`, `sqlite-persistence` | 1391 tests, 0 failed |
| `NativeWorkflowSpec` | 5 of 5 |
| Kitchensink, three rounds | 9 of 9 executions completed in 18 to 23 s, every continuation leased once |
| Chaos script | 52 of 52 checks |
| Quick bench | valid, 15 faults, 15 of 15 executions completed |
| Cluster bench, seeds 47, 23, 31 | valid, 32, 22 and 28 faults, 50 of 50 executions completed in each |

Not run on this design: bug injection (mutants), Elle and Porcupine, and the Postgres and MySQL persistence suites. Continuation latency under faults has a long tail that is not yet explained: p50 151 to 320 ms, p99 16 to 149 s, maximum 221 to 426 s, against a 30 s lease.

## Server bugs found by running the kitchensink and the bench

Each entry names the problem and the mechanism that fixes it as it exists now.

1. **The decide loop could drop workflow changes.** When an iteration scheduled a task, it
   continued the loop without saving the workflow row. A final iteration with nothing new then
   skipped the write, and variables applied from a completion were lost. `WorkflowExecutorOps.decide`
   now tracks a dirty flag and writes the row before it returns.
2. **A live-target JOIN could complete before its fork existed.** The JOIN is queued during the
   decide that appends it, and the system task worker could evaluate it from a copy of the
   workflow loaded before that decide saved the grown definition. With no fork in sight it had no
   targets and completed at once, ahead of the lanes. A live-target JOIN that is missing from the
   loaded definition now waits for the next evaluation. Live targets apply only to native
   workflows, so every other JOIN behaves exactly as before.
3. **A retry re-ran superseded attempts.** Rerun finalization reopened failed tasks the code had
   not consumed, and an attempt that a task retry had already replaced looked like one. Retry of
   the shipment workflow packed the order again. Native retries now rerun only from the cause task
   (`failedTaskId`, set from the lane's `error.ref`), never from other failed tasks.
4. **The SQL message queue failed under concurrent signals on SQLite.** A pop read the queue and
   then deleted from it in one transaction. In WAL mode SQLite fails that lock upgrade at once
   with SQLITE_BUSY_SNAPSHOT when another connection has written, and busy_timeout does not
   apply. On SQLite, push and pop are now single statements (`INSERT ... SELECT ... WHERE`,
   `DELETE ... RETURNING`); lock conflicts on every database are retried.
5. **A JOIN could complete while a lane still had work.** Found by the durability bench with no
   faults. A lane's continuation completed and appended the lane's next task, but the JOIN was
   evaluated from a copy of the definition loaded before that decide was saved, where the task the
   lane had paused on was still the branch's last task. The code resumed after the join with a
   lane unfinished, and the execution failed. `lanesSettled` now counts a branch as finished only
   when its last task is terminal and its marker, if any, is `APPLIED` with `done`.
6. **Acknowledged signals were lost when an execution completed.** Found by the durability bench
   with faults. A continuation's poll response was lost in a partition, so the handler lane
   stalled for its timeout. Signals sent meanwhile stayed queued, the main lane finished, and the
   queue was deleted with the execution. A main-lane completion that would end the execution
   successfully is now deferred while messages are queued or the handler marker is `RUNNABLE` or
   `LEASED`: the marker goes back to `RUNNABLE` with the reason in `deferred`, and the continuation
   runs again on the current state. Messages still queued when an execution ends anyway (failed, terminated) are logged
   and counted (`workflow_messages_discarded`) instead of dropped silently.
7. **A lost poll response stalled a continuation for two minutes.** Also found by the bench: a
   continuation handed to a worker during a partition stayed leased until its timeout, which was
   120 s (an hour in dev mode). The lease (`continuationTimeoutSeconds`) is now 30 s by default,
   set with `workers().continuationTimeout(...)`.
8. **A signal could be applied twice.** The client retries a message send that fails ambiguously,
   such as a timeout while the server is paused, and the server had already queued the first
   attempt. The client now puts its own id in each message, reused across retries; the handler
   lane remembers the last 500 ids it applied (`nativeState.seen`) and skips repeats, and update
   replies (`nativeState.replies`) are keyed by that id.
9. **A continuation's stale variables could overwrite a handler's write.** Two continuations of
   one execution can run at once on different workers during a failover. Every completion carried
   a full copy of the variables, so the later one erased what the earlier one wrote: a signal
   handled while the first continuation ran disappeared. Each completion now also carries the
   variables it started from (`resumedWith`, which handlers send too). The server applies only
   the variables the continuation changed, and when another completion changed one of them in
   the meantime it defers the completion and runs the continuation again on the current values.
10. **A JOIN could fail an execution on a continuation that was about to run again.** A refused or
   deferred completion left the branch's last task in a state a live-target JOIN read as failure.
   `lanesSettled` now waits on any marker that is not `APPLIED` with `done`, whatever happened to
   the previous lease.
11. **A server crash mid-apply could strand an execution.** The continuation's record was saved
   before the grown definition, so a server killed in between had a continuation marked applied
   with nothing to schedule its next step. `completeContinuation` now saves the workflow row
   (definition, variables, `nativeState`) before the task that carries the marker. A crash at any
   point either leaves the marker leased, so the continuation runs again, or leaves the
   definition saved.
12. **A replay could report a false divergence.** A worker replaying the execution between a
   completion being saved and the decide that created its tasks replayed the completion, expected
   its tasks, and failed the execution as diverged. The SDK now replays only `APPLIED` markers
   (`Execution.segments`); a continuation not yet applied leaves its lane paused.
17. **A continuation queued for a dead worker could wait an hour.** Found by the chaos run on
   protocol 2, under the per-worker routing that has since been removed. A worker was killed right
   after its continuation was queued to its own domain queue. `repairAndWake` would have re-pushed
   the wake-up to a live queue on the next decide, but the sweeper's decide had postponed the
   workflow's decider-queue entry on the strength of its in-progress tasks, up to
   `maxPostponeDurationSeconds` (an hour), so no decide came. `WorkflowExecutorOps.decide` now caps
   that postpone at `workflowOffsetTimeout` (30 s) while a continuation marker is `RUNNABLE` or
   `LEASED` (`NativeContinuationService.needsPromptRevisit`), and writes it even when it equals the
   default, to replace a longer postpone set earlier. The cap still matters for domain lists and
   for re-offering a lease whose holder died.
18. **A pop on SQLite took the same message id in other queues.** The `popMessages` update selected
   its messages within the queue but marked them popped by `message_id` alone, so a message with
   the same id in another queue was marked popped too; a continuation's id can be in several queues
   of its code type when it is re-pushed to another domain. The update is now scoped to its queue
   (`SqliteQueueDAOTest.poppingAMessageLeavesTheSameIdInAnotherQueueAlone`). Postgres and MySQL were
   already scoped.

### Found by the conformance suite

19. **A switch value could be evaluated as an expression.** A value containing `${...}` was not
    escaped, so the server took a different case from the code and scheduled a preview entry. The
    value is now escaped like all task input.
20. **A JOIN could complete before a branch's tasks finished.** A branch ending in a `SWITCH` was
    treated as ended by the switch, which finishes as soon as it chooses. The JOIN now targets the
    last task of the taken case; before this the code saw a false divergence until the lane caught up.
21. **A workflow that called no task never completed.** The completion check treated "no tasks" as
    "not done yet".
22. **Code errors were retried forever.** Calling a task before joining `async` lanes, or leaving
    them unjoined, surfaced as a generic worker failure, so the execution stayed `RUNNING` with no
    error. It now fails with a message naming the problem.
23. **A task in a `finally` block failed the workflow.** The block also ran while a lane was torn
    down at a pause, adding a second pause to the fragment. Calls made during teardown now unwind.
24. **The same `switchOn`, `fanOut` or `once` name twice failed or hung.** A name in a loop or a
    fan-out now gets `_2`, `_3`; only a duplicate explicit `.ref()` is an error.
25. **Results that are not JSON objects failed.** An enum or a UUID returned by a task could not be
    encoded or decoded; such a value is carried under `result`.
26. **Joins were evaluated without pause.** The SDK set `joinMode: SYNC` on every JOIN, so the server
    re-evaluated it continuously (1,409 evaluations in under three minutes). Each evaluation took the
    workflow lock, so task updates were refused as busy until the worker gave up and the result was
    lost, leaving the task to its 120-second response timeout. Removing it took busy refusals from
    145 across four conformance rounds to 0 to 3 per round, with no lost results.

### Found on a three-server cluster

The bench also runs against three Conductor servers on Postgres (database, queues, index and
execution lock), with three worker processes, a partitionable proxy between each server and the
database, and database faults (pause, kill, partition one server from it) and cluster-wide kills.

13. **Workers starting together crashed.** Postgres registered task and workflow definitions with
   an update, then an insert when nothing was updated; two servers registering the same
   definition at once both inserted, and one failed on the primary key. Two of three workers
   exited at startup. Both upserts are now single `INSERT ... ON CONFLICT DO UPDATE`
   statements, and the SDK retries registration for two minutes.
14. **A server death stalled a handler lane for an hour.** The async executor reserves a system
   task's queue message for the task's response timeout (an hour by default) while it runs it. A
   server killed while running the handler lane's message pull left the message invisible for an
   hour; every signal waited. Messages are now claimed, not popped (`claimed_by`, new migrations
   for SQLite, Postgres and MySQL): running the same step again returns the same messages, and the
   next step deletes them. In protocol 2 the handler lane's poll claims under `handlers#<n>` and
   the lease is 30 s; `PULL_WORKFLOW_MESSAGES` keeps the claim mechanism for JSON workflows, with a
   short reservation (`isExecuteIdempotent`).
15. **A database outage failed a handler loop.** Scheduling a loop's next iteration failed on a
   closed connection; the loop caught the error, failed itself and the workflow with it.
   Scheduling now treats storage outages (lost connections, rolled-back transactions, a database
   shutting down, and HikariCP's stateless "Connection is closed") as transient: the decide is
   aborted and retried by the sweeper, and DO_WHILE rethrows instead of failing.
16. **A stale pull stole signals.** With queries and more updates in the bench, three overlapping
   fault injectors lost the close signal and three deposits. A handler step that had completed
   ran again later, most likely on a server resuming from a pause with it still in memory: it
   claimed the newest messages under its own old name and overwrote its completed record, and the
   current step then deleted those messages as delivered. Two fixes: claims are fenced by an
   ordinal (`workflow_message_claim` keeps the highest ordinal that claimed for each execution; an
   older claimant reads only its own earlier claims; the handler lane's `n` is the ordinal), and
   the async executor does not save its copy of a task that another run completed meanwhile.

Three Jepsen findings about Orkes Conductor were checked against OSS and fixed, though the bench
did not hit them:

- **Abandoned workflows.** A start wrote the workflow, then queued it for the decider; a crash in
  between left a RUNNING workflow nothing evaluates. The queue entry is now written first.
- **Lost workflows.** A start that failed after writing the workflow deleted it, even though it
  was committed, queued and possibly running. Starts no longer delete: a failure before the write
  is reported as an error with an indefinite outcome, a failure after it leaves the workflow to
  the sweeper and reports success.
- **Concurrent task execution from the sweeper.** The sweeper re-queued a task it saw as
  SCHEDULED in its decide snapshot after a worker had polled it since, handing it to a second
  worker. It now re-reads the task before re-queueing.

A note on the bench runs before its double-start bug was fixed: they ran with several headless
server instances on one SQLite database (see `DURABILITY-BENCH.md`). A Conductor server that
failed to bind its port stayed alive and kept running background workers. The cause was not
investigated further; it is worth checking that a server exits when its web server cannot start.

## Failures, retries and `optional`

Tasks native code waits on are marked `optional`, not `permissive`. They are retried like any
Conductor task first: worker tasks with three retries and exponential backoff by default,
configurable with `@WorkerTask(retryCount, retryDelaySeconds)`, and HTTP and LLM steps with the
same policy through an inline task definition (`http(ref).retries(count, delay)`). Once retries are
exhausted, `optional` lets the decider evaluate the task, which makes the lane's marker runnable;
the code receives the failure as `TaskFailure`. The task becomes COMPLETED_WITH_ERRORS as for any optional task; for
native workflows the decider records how it failed in its output, so the code still sees FAILED or
TIMED_OUT. The kitchensink checks that the HTTP step made four attempts, and the
operator checks that a worker task failing once is retried rather than raised.

## Protocol 2: what changed from the first implementation

| Topic | Protocol 1 (first implementation) | Protocol 2 (this branch) |
|---|---|---|
| A pause of the code | an engine-only `CODE` task per pause, hidden in the UI, queued, leased, timed out and retried as a worker task | a `continuation` marker on the task the code paused on; one queue message per wake-up; the queue's unack timeout is the lease |
| Recorded values (`once`, `now`, `random`) | a `RECORD` task each | `recorded` on the marker of the continuation that produced them |
| The end of the workflow | a `TERMINATE` task in the main lane's last fragment | the engine completes or fails the workflow from the main lane's final completion (`done`, `result` or `error`) |
| The first continuation | the skeleton's first `CODE` task | `nativeState.bootstrap`, message `<workflowId>\|main` |
| Handlers | `FORK_JOIN` + `DO_WHILE` + `PULL_WORKFLOW_MESSAGES` + handler `CODE` + `JOIN` | `nativeState.handlers`; the message endpoint wakes it; the poll claims the messages under `handlers#<n>` |
| Replies and handled message ids | workflow variables `__replies`, `__seen` | `nativeState.replies`, `nativeState.seen` |
| Continuation routing | `taskToDomain[<codeType>]`, the worker's id captured per execution | the shared queue of the code type; `taskToDomain` only when the start names the code type or `*` (01 §3) |
| Poll | batch poll of the `CODE` task type | the same batch poll of the code type; `ExecutionService.poll` leases the marker and returns the finished task itself (or a synthetic task for the bootstrap and handler lanes) |
| Complete | `POST /api/tasks` on the `CODE` task | `POST /api/tasks/update-v2` on the awaited task's id or the synthetic id; stale completions get 409 |
| Fencing | owner refusal, stale-write guard, response and poll timeouts with retry | one lease per marker, `(n, epoch)` check under the execution lock |
| Deferral | the attempt set aside as `CANCELED`, retried with the next retry count | the marker back to `RUNNABLE` with `deferred` set; the same message re-pushed |
| Replay input | `CODE` outputs with `applied`, `step`, `resumedWith`; `RECORD` outputs | `APPLIED` markers ordered by `step`, plus the bootstrap |
| Task count | hello world: 6 tasks (3 `CODE`, 2 tasks, `TERMINATE`) | hello world: 2 tasks |
| UI | fold hidden tasks, `?showHidden=true` | nothing to fold; continuation on the task panel, engine state on the summary |

## Differences from the earlier design documents that still stand

| Topic | Earlier documents | Implemented |
|---|---|---|
| Worker execution model | live thread as the fast path, re-run only on worker loss | The worker always replays the code against the applied markers and the tasks, so it is stateless. Segments replay in the order the server applied them (`step`). `@Variable` fields are restored at each continuation to the values recorded when it was first processed (`resumedWith`). Any worker can run any continuation; the server's slot (`nativeState.activeContinuation`) lets one continuation of an execution run at a time, across all hosts, and the SDK holds no per-execution lock. The live-thread fast path is left for the latency round. |
| Fan-out | FORK_JOIN_DYNAMIC, in stages | a FORK_JOIN generated at run time with one lane per item; items progress independently, with no stages |
| Native JOIN mode | `joinMode: SYNC` | default asynchronous evaluation; SYNC re-evaluates continuously and would busy-poll long branches |
| Any-of join (S3) | `joinMode: ANY` | `inputParameters.joinStrategy: ANY`, because `joinMode` already means sync or async evaluation; other branches are not cancelled |
| Condition waits | reference `cond`, `cond_2` | `condition`, `condition_2` |
| `startWorkflow` | returns the new execution id | returns nothing, since the id is not deterministic on replay |
| `repeatUntil` condition | a predicate | a method reference to a boolean property of the result, such as `JobStatus::done`, translated to the DO_WHILE condition |
| Continuation routing | per worker, then per build (`nativeState.owner`, sent in every completion; workers polled their worker id too) | removed. The shared queue by default; `Workers` polls it plus `buildId` as a domain when set; pinning is opt-in with a domain list on the start, such as `.taskToDomain(Map.of("<name>.v1.code", "build-7,NO_DOMAIN"))`, and a worker's build is never captured automatically |
| Human task timeout | a registered task definition | an inline `taskDefinition` on the HUMAN entry, with `timeoutPolicy: RETRY` and `retryCount: 0` |
| Branching visible in the graph | `decision(name, value)`: nothing emitted, a label for the observed-graph diamond; a plain Java `switch` left no trace | a plain `if` or `switch` whose arms call the server is a `SWITCH` with every arm (read from the compiled code, the arm known from the line each call is made on); `switchOn(name, value).when(key, body)…otherwise(body).get()`/`.run()`: a real `SWITCH` entry with every declared case, the taken body run inline with its calls inside that case, every untaken case holding a preview of the calls its body would have made (`<base>_if_<key>`, `metadata.preview: true`, read from the compiled case body, so nothing runs), and `metadata.after` on the first entry after the body when the lane resumed inside the case. Plain `if`/`switch` stays plain code; `switchOn` is the opt-in for a branch the execution should show. `decision` no longer exists. The kitchensink's `oddEvenDecision` is a `switchOn`; `switches.Switches` shows a nested switch, an empty case, the default case and an unhandled value |
| Registration checks (S2) | log when the message queue is off | as designed, plus native definitions are refused when `conductor.app.nativeWorkflow.enabled=false` |
| Embedded-server test tier | `DurableTestEnvironment.embedded()` | not implemented; the in-memory tier and the replay test are |
| Attach-to-execution debugger, CLI commands | listed | not implemented |
| Observed-graph definition view (merge the fragments of every execution by call site) | listed, deferred | replaced: the registered definition carries the graph `Replayer.discover` finds by reading the compiled method (`metadata.native.discovered`), so the definition view is filled before the first run; `Conductor.definitions(Class)` returns that JSON and `Conductor.register(Class...)` registers it without starting workers. `NativeContinuationService.initialize` clears the discovered tasks from each execution's copy, so executions still hold only what the code called |

## Known limitations

- A native JOIN is checked once a second after its fast start
  (`conductor.app.nativeWorkflow.joinPollInterval`), so it can finish up to about that long after a
  long branch.
- Previews and the discovered definition cannot match a `join` to a particular `async` block: a
  `join` or `get` after other calls waits for the most recent fork not yet waited for.
- A lane nothing joins keeps the execution `RUNNING` until it finishes, and its failure does not
  fail the execution.
- A taken `fanOut` is a `FORK_JOIN` with one real branch per item; an untaken one is drawn as a
  `FORK_JOIN_DYNAMIC` + `JOIN`, because its items exist only at run time.
- Plain Java in a `finally` block runs each time the lane pauses, as the lane is torn down. SDK
  calls made there have no effect; other side effects do happen.
- A worker's task result is dropped when every update attempt is refused (for example as busy),
  leaving the task `IN_PROGRESS` until its response timeout, when it is retried. Bug 26 removed the
  common cause, but the worker still gives up after its retries.
- Divergence is always strict: a replay that reaches a call the recording does not have is
  refused, with a message naming the call and its source line. The execution is held rather than
  failed (`marker.deferred` starts `diverged: `; ui-next shows "Waiting for compatible code"
  beside the status, as a dashed step after the task the code paused on that opens an explanation, and with the reason on
  both summaries) and retried with
  backoff up to a minute, indefinitely, until a compatible build runs it, it times out or an
  operator terminates it.
- Discovery and previews read compiled code and run nothing (`CodeReader`, on ASM), so plain Java
  in a workflow never runs for them. `Workers.discovery(false)` leaves the graph out of the
  registered definition; untaken switch cases are always read.
- Data placed in task input has `${` escaped as `$${`, because the server evaluates expressions.
- `switchOn` compares the value's string form (`null` for a null value) with the case keys; a
  value no case handles fails the workflow unless `otherwise` is given, and a case key declared
  twice is an error at the call. Replay is strict here too: a switch that takes a case other than
  the recorded `switchCaseValue` is a divergence.
- Previews and the discovered definition show every call the code can make, not the path a run
  would take: every arm of an `if` or `switch` whose arms call the server, as a `SWITCH`; the body
  of a loop once; a catch block after its try. An `if` or `switch` is drawn at run time only when
  the lines its arms call from are its own (a one-line ternary with a call in each arm is plain
  code), its cases do not fall through, and, when the code went past it without a call, only one
  arm could have been passed that way. `fanOut` appears as a
  `FORK_JOIN_DYNAMIC` + `JOIN`; `repeatUntil` and `forEach` as a `DO_WHILE` that never loops; a
  nested `switchOn` shows every case with an empty `switchCaseValue`. Inputs are not read, a task
  name computed at run time shows as `task`, a call through an interface or into another package
  is not followed, and a finally block's calls are deduplicated by line. Case bodies are
  serializable lambdas so that the runtime lambda names its compiled method. The discovered
  definition is only a view: `NativeContinuationService.initialize` clears it from each execution.
- Every continuation replays the method from the top, so the cost of a continuation grows with the
  length of the execution. The large-loops and latency rounds address this.
- Domain lists are resolved when a message is pushed. If every worker of a domain dies inside the
  10-second poll window, a continuation pushed then waits for the next decider pass (up to about
  30 s, `repairAndWake`). On the shared queue a continuation held by a dead worker is redelivered
  when its lease expires.
- `continuation` and `nativeState` are carried over gRPC (`Task.continuation`,
  `Workflow.native_state`, covered by `TestProtoMapper`), but workers only use REST: no worker has
  polled or completed a continuation over gRPC.
