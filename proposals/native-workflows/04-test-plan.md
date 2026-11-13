# 04. Test plan

The server can be tested without any SDK: a test plays the worker, polling the code type like any
task type and completing continuations through the ordinary task update with hand-written
fragments. That is the main integration tool (`NativeWorkflowSpec`), and it exercises the wire
protocol end to end with no SDK code involved.

## Conformance suite

`native-examples/src/main/java/conformance/Conformance.java` checks that code is translated and run
correctly against a live server: 151 scenarios combining the SDK's constructs in nested positions
(a switch in a fork, a fan-out in a case, a loop in a lane, failures caught at each level, value
types, `${` in every kind of value). Each has a hand-written expected result and graph; every run
also checks that each task ran in the right branch, that no preview was scheduled, and that the
finished execution replays against its code. It exits non-zero on any failure. A false
"diverged from its code" line in the worker log counts as a failure when reading a run.

## Unit tests

| Area | Tests | Location |
|---|---|---|
| `NativeContinuationService.insertAfter`, `previousTask`, `removeAll` | top level; inside a `forkTasks` branch; inside `decisionCases` and `defaultCase`; inside `loopOver`; unknown reference returns false and changes nothing; `removeAll` removes at any depth | `core/src/test/java/.../execution/NativeDefinitionEditsTest` |
| `NativeContinuationService` | `initialize` sets up the bootstrap and handler markers; the bootstrap completion appends the first entries and records the continuation; a completion on a task inserts after it and re-evaluates it; a fragment whose first entry names an anchor in `metadata.after` is inserted after that entry (after the switch, at its level, not inside the case the code paused in) and an anchor that names no entry is rejected and fails the workflow (`aFragmentAnchoredAfterASwitchLeavesTheCaseItPausedIn`); a stale completion is refused; the main lane returning completes the workflow; the main lane throwing fails the workflow with the cause; a diverged continuation fails the workflow; a worker error releases the lease for another run; completion is deferred while messages are pending; only changed variables are applied and a conflict defers; fragments that break the rules are rejected; handler completions carry replies, seen and releases; evaluation makes a waiting marker runnable and repair collects wake-ups; `lanesSettled` reads the markers; rerun resets the markers whose continuations must run again; the mapper copies the marker onto scheduled tasks | `core/src/test/java/.../execution/NativeContinuationServiceTest` |
| `DeciderService` | failure matrix for native workflows (FAILED, TIMED_OUT, FAILED_WITH_TERMINAL_ERROR become COMPLETED_WITH_ERRORS with the failure kind in the output; optional continues); a native workflow retries an optional task then completes it with errors; a native workflow with no tasks yet is not complete and schedules nothing | `core/src/test/java/.../execution/TestDeciderOutcomes` |
| `Join` live targets | joins on the current last task of each branch, inside the taken case when a branch ends in a switch; waits while a branch lane still has a continuation to run; a detached join completes at once; a waiting join waits only for the lanes it names, for the forks they started, and for a lane not in the definition yet; waits when its fork is not in the definition yet | `core/src/test/java/.../tasks/NativeJoinTest` |
| Idempotent start | key without id derives a stable id; `RETURN_EXISTING` returns the first id on a second call; `FAIL` conflicts | `core/src/test/java/.../execution/TestWorkflowExecutor` |
| Durable message queue | push, claim by claimant and ordinal, de-duplicated re-claim, concurrency on SQLite WAL, migrations | `SqliteWorkflowMessageQueueDAOTest`, `PostgresWorkflowMessageQueueDAOTest` |

## Integration specs (test-harness, Spock)

`test-harness/src/test/groovy/com/netflix/conductor/test/integration/NativeWorkflowSpec.groovy`
extends `AbstractSpecification`, with the fixture
`test-harness/src/test/resources/native_skeleton_integration_test.json`: `tasks: []` (the harness
plays an SDK with nothing discovered; a real SDK registers the discovered graph, which
`initialize` clears from the execution's copy either way) and
`metadata.native = { sdk: "test-harness", protocol: 2, codeType: "native_skeleton.v1.code",
continuationTimeoutSeconds: 60 }`. The spec registers it with
`workflowTestUtil.registerWorkflows(...)`, starts with `startWorkflow(...)`, polls the code type
with `workflowExecutionService.poll(CODE_TYPE, workerId[, domain])`, and completes with
`workflowExecutionService.updateTask(new TaskResult(task))` whose output is built by a helper,
`completion(task, fragment, variables, done, result)`, that copies `n` and `epoch` from the
polled task's marker and sets `protocol: 2`. Entries are built by `simple(name, input, lane)`
(`SIMPLE`, `optional`, `metadata.lane`) and `paused(entry, lane, n)` (`metadata.continuation`).

| # | Scenario | Asserts |
|---|---|---|
| 1 | Linear: poll the code type → the bootstrap (`taskId` `<workflowId>\|main`, `taskType` the code type, marker `main#1` `LEASED`) → complete with `[integration_task_1 (paused main#2)]`, variables → complete the task → poll the shared queue → complete with `done` and a result | after the bootstrap: one task, `SCHEDULED`, marker `main#2` `WAITING`, input evaluated from the workflow input, variables applied, `nativeState.bootstrap` `APPLIED` with `step` 1; the next poll returns the finished task itself, `COMPLETED` with its output, marker `main#2` `LEASED`, `continuation.queue` `worker-a:native_skeleton.v1.code`, `domain` `worker-a`; at the end the workflow is `COMPLETED` with the result as output, still one task, its marker `APPLIED` and `done` |
| 2 | Fork with independent branches: the bootstrap completes with `FORK_JOIN fork_1` of `[integration_task_1 (paused fork_1.0#1)]` and `[integration_task_2 (paused fork_1.1#1)]`, `JOIN join_1` with `joinOn: []` paused `main#2` | both branch tasks `SCHEDULED`, the join `IN_PROGRESS`; completing only `integration_task_1` offers branch 0's continuation on the shared queue while `integration_task_2` is still `SCHEDULED`; completing it with `[integration_task_3 (paused fork_1.0#2)]` lands inside branch 0 only, `integration_task_3` is `SCHEDULED` before branch 1 moved, and the execution holds five tasks: fork, join and the three the code called |
| 3 | Stale completion: complete the bootstrap with an `epoch` the server never handed out | `ConflictException`; no task, no definition entry, no variable applied. The lease holder's completion is then applied; the same completion delivered again (a retried update after a lost response) is refused and the execution still holds one task |
| 4 | A continuation fails for a reason unrelated to the task: the worker completes it `FAILED` with `protocol`, `n` and `epoch` | the workflow stays `RUNNING`, the task keeps its `COMPLETED` status and output, the marker returns to `RUNNABLE`; after the 1 s backoff a poll hands it out again with `epoch + 1` |

Scenario 2 is the acceptance test for the design: it is the kitchensink's `forkx` branch.

Covered end to end by the SDK repository rather than by the harness: handlers (claimed messages,
`replies`, `seen`, `releases`, completion deferral), failover to another worker, `WAIT` and `HUMAN` timeouts,
restart, rerun and retry (`native-examples`: kitchensink, `operator.OperatorChecks`, `chaos.sh`
and the durability bench, see [06-implementation.md](06-implementation.md)), and `switchOn`
(`conductor-client`'s `SwitchOnTest`: the first continuation emits the switch with every case and
the taken body inside it; an empty case continues inline after the switch with no anchor; an
unhandled value takes the default case; an unhandled value without `otherwise` fails the workflow
with "no case for"; a lane that resumed inside a case anchors its next call after the switch with
`metadata.after`; a switch that takes another case on replay is a `NonDeterminismException`
naming both cases; untaken cases show the tasks they would have called as preview entries
(`untakenCasesShowTheTasksTheyWouldHaveCalled`: `shipB_if_b`, a `fork_if_b` with two branches and
its `fork_if_b_join`, `notify_if_b`, a nested `inner_if_c` switch with `shipA_if_c_x` and
`shipB_if_c_default`, `notify_if_default`; every preview carries `metadata.preview` and no
marker, and the live instance's fields are untouched). `native-examples`' `switches.Switches` runs
a nested switch, an empty case, the default case and an unhandled value against a server; the
kitchensink checks the `SWITCH` task's `selectedCase` equals the input's `oddEven`). Discovery of
the SDK's result format and task-definition ownership by `conductor-client`'s `WorkerProtocolTest` (a failed continuation carries `protocol`; a definition owned by someone else is not overwritten); the registered definition is covered by `conductor-client`'s `DiscoveryTest`
(`theDefinitionHoldsTheGraphTheCodeDescribes`: name, description, `inputParameters`
`[item, region, quantity]`, `discovered: true`, `signals: [cancel]`, tasks `[pick, fork, fork_join,
sleep, region]` with no `metadata.preview`, an inline `taskDefinition` on `pick`, two fork branches
`pack` and `pack_2`, cases `eu: [ship]` and `us: [ship_2]`, an empty default;
`discoveryReadsCodeThatWouldNeedARealValue`: `[pick, ship]`; `codeThatLoopsForeverIsReadWithoutRunningIt`), and reading compiled code by `CodeReaderTest` (code that would need real values is read to the end, both sides of an `if` and a loop body are seen, no plain Java runs, a finally block copied to every exit is one step, lambdas and helpers are followed, recursion ends, every kind of constant case key is read). Not covered by any
server-only test: that `initialize` clears a discovered task list from the execution's copy
(verified live, see 06). Not covered by any
server-only test: a lease expiring and being re-leased with `epoch + 1`; the handler lane through
the harness; rerun from a task inside a fork branch through the harness (covered against a live
server by `operator.OperatorChecks`).

## Failure injection

The durability bench (`java-sdk/native-examples/bench.sh`) runs with deliberate bugs switched on
to prove its checker catches them. The bugs are not in the server or SDK code: each is a patch in
`java-sdk/native-examples/mutants/` (`server/<name>.patch` against the server repository,
`sdk/<name>.patch` against the SDK), applied to a scratch copy that is built and benched. See the
SDK's `native-examples/DURABILITY-BENCH.md`. Server mutants:

| Mutant | What it disables | Where |
|---|---|---|
| `no-completion-deferral` | the completion guard while handler messages are pending | `NativeContinuationService.pendingMessages` |
| `no-variable-fencing` | applying only changed variables and the conflict check | `NativeContinuationService.applyVariables`, `variableConflict` |
| `ignore-epoch` | the epoch check on completion | `NativeContinuationService.complete` |
| `join-ignores-lanes` | `lanesSettled` | `NativeContinuationService.lanesSettled` |
| `workflow-saved-last` | the workflow-row-then-tasks write order | `WorkflowExecutorOps.completeContinuation` |
| `no-claim-fence` | the ordinal fence on message claims | `JdbcWorkflowMessageQueueDAO` |
| `destructive-pull` | claim-based pulls (pop instead) | `PullWorkflowMessages`, `AsyncSystemTaskExecutor` |
| `no-stale-write-guard` | the executor's refusal to save over a task another run completed | `AsyncSystemTaskExecutor` |
| `terminate-on-storage-errors` | treating storage outages while scheduling as transient | `WorkflowExecutorOps` |

SDK mutants (`Workers`): `no-message-dedupe`, `drop-some-signals`, `no-reply-prune`.

Each mutant run must fail a bench check (`lost-signal`, `duplicate-signal`, `incomplete`,
`wrong-output`, `nondeterminism`, …) or an independent check (Elle, Porcupine).

## Performance checks

Run on Postgres with the `test-harness` functional profile:

| Check | Method | Pass |
|---|---|---|
| Append cost | 1,000 sequential continuations in one execution, each with a 2-task fragment; measure decide time per cycle and workflow row size | decide time grows linearly, not quadratically, up to the default `maxDefinitionTasks`; row size reported |
| Domain lookups | an execution started with a long `taskToDomain` list for its code type; measure `queueName` (one `getTaskPollDataByDomain` per listed domain) per wake-up | sub-millisecond lookups; no table scan in the query plan |
| Fork independence | scenario 2 with branch-1 task delayed 60 s | branch-0 progresses through 5 continuations before branch-1 completes |

`<!-- TODO: verify against live server -->` These checks have not been run on protocol 2.

## UI checks (manual until `ui-next` has component tests)

- An execution shows exactly the tasks the code called; nothing to fold.
- The task panel shows the continuation row (`lane #n — state`, deferred reason, worker,
  applied time) on a task the code paused on, and the source line from `metadata.site`.
- The execution summary shows the code type, the number of continuations
  applied, the bootstrap state while it is pending, and the handler lane's run number and state.
- An appended `FORK_JOIN` renders with both branches and status overlay.
- An appended `SWITCH` renders with every declared case and the default case, the case named by
  `outputData.selectedCase` marked as taken; the fork and join a case body called sit inside that
  case, and the untaken cases show their preview entries (`<base>_if_<key>`) without a status, as
  the untaken branch of a JSON switch renders.
