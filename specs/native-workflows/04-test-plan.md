# 04. Test plan

The server can be tested without any SDK: a test plays the worker, polling the code type like any
task type and completing continuations through the ordinary task update with hand-written
fragments. That is the main integration tool (`NativeWorkflowSpec`), and it exercises the wire
protocol end to end with no SDK code involved.

## Conformance suite

`native-examples/src/main/java/conformance/Conformance.java` checks that code is translated and run
correctly against a live server: 160 scenarios combining the SDK's constructs in nested positions
(a switch in a fork, a fan-out in a case, a loop in a lane, failures caught at each level, value
types, `${` in every kind of value). Each has a hand-written expected result and graph; every run
also checks that each task ran in the right branch, that no preview was scheduled, and that the
finished execution replays against its code. It exits non-zero on any failure. A false
"diverged from its code" line in the worker log counts as a failure when reading a run.

## Unit tests

| Area | Tests | Location |
|---|---|---|
| `NativeDefinitionEdits.insertAfter`, `previousTask`, `removeAll` | top level; inside a `forkTasks` branch; inside `decisionCases` and `defaultCase`; inside `loopOver`; unknown reference returns false and changes nothing; `removeAll` removes at any depth | `core/src/test/java/.../execution/NativeDefinitionEditsTest` |
| `NativeContinuationService` | `initialize` sets up the bootstrap and handler markers; the bootstrap completion appends the first entries and records the continuation; a completion on a task inserts after it and re-evaluates it; a fragment whose first entry names an anchor in `metadata.after` is inserted after that entry (after the switch, at its level, not inside the case the code paused in) and an anchor that names no entry is rejected and fails the workflow (`aFragmentAnchoredAfterASwitchLeavesTheCaseItPausedIn`); a stale completion is refused; the main lane returning completes the workflow; the main lane throwing fails the workflow with the cause; a diverged continuation holds the execution and backs off, and the run's reason says it is held across retries until a matching build applies and clears it (`aDivergedContinuationHoldsTheExecutionAndBacksOff`, `aHoldLastsAcrossRetriesUntilTheCodeMatches`); a worker error releases the lease for another run at `retryCount + 1`; completion is deferred while messages are pending; the handler lane, with messages queued, is offered before a continuation whose task ended earlier (`theHandlerLaneGoesBeforeACodeContinuationThatWaitedLonger`); a restart counts as a retry of the bootstrap and handler markers (`aRestartIsARetryOfTheLanesWithoutATask`); a leased offer is pushed again at the same `retryCount` (`aLeasedOfferIsPushedAgainAtTheSameRetryCount`); the variables a completion wrote are applied and what it started from is recorded (`appliesTheVariablesItsCompletionWroteAndRecordsWhatItStartedFrom`); fragments that break the rules are rejected; handler completions carry replies, seen and releases; a continuation runs once its task is evaluated, and queued messages go first (`aContinuationRunsOnceItsTaskIsEvaluatedAndQueuedMessagesGoFirst`); `lanesSettled` reads the markers; rerun resets the markers whose continuations must run again; a decide gives each scheduled task the code waits on its marker, a retried attempt the marker of the attempt before, reset, and the API shows each marker on its task (`aDecideGivesEachScheduledTaskTheCodeWaitsOnItsMarker`, `aRetriedAttemptWaitsWithTheMarkerOfTheAttemptBeforeReset`, `theApiShowsEachMarkerOnItsTask`) | `core/src/test/java/.../execution/NativeContinuationServiceTest` |
| Pinning (`NativeContinuationService`) | a completion whose worker parks the code pins the execution to it; one that parks nothing unpins and is acknowledged on both queues; a pin must name a domain that can be a queue; a handler run and a deferred completion leave the pin and its stamp; a pinned execution is offered to its worker with a fallback on the shared queue after the pin timeout, and to any worker once its worker stopped polling; a lease from the pinned queue gives the worker the pin timeout again, never more than the continuation timeout; a lease from the shared queue takes the message off the pinned queue; a lease hands out the stamp and the variables and stores neither; a rerun, a retry of the code, a rejected fragment and a restart unpin (`aCompletionWhoseWorkerParksTheCodePinsTheExecutionToIt` … `aRestartStartsUnpinned`) | `core/src/test/java/.../execution/NativeContinuationServiceTest` |
| `NativeDriver` | an empty definition starts with no tasks; a failed optional task keeps how it failed; the output is not computed from the tasks; not complete until the code returned, nor while the tasks it called run, complete with no tasks when it returned; restart runs the registered (or latest) definition with no variables; a prompt revisit only shortens the decide offset; a retry reruns from the task that failed the run, or runs the last continuation again when the code failed, removing the mark of a throw; a rerun cannot start from the code's structure, removes what the code did after the task, starts a new child for a sub-workflow task and does not run a failure the code consumed | `core/src/test/java/.../execution/NativeDriverTest` |
| `NativeDriver.wakeJoins` | a JOIN is not woken while a lane runs; it is checked with no delay when its last lane ended, and queued again if its message was lost; a finished JOIN is not woken | `core/src/test/java/.../execution/NativeDriverJoinTest` |
| Stale task writes | a slow JOIN run or a resent worker result saved last leaves the marker its continuation applied; a JOIN evaluated out of order does not run its continuation again | `core/src/test/java/.../execution/NativeStaleTaskWriteTest` |
| `DeciderService` | failure matrix for native workflows (FAILED, TIMED_OUT, FAILED_WITH_TERMINAL_ERROR become COMPLETED_WITH_ERRORS with the failure kind in the output; optional continues); a native workflow retries an optional task then completes it with errors; a native workflow with no tasks yet is not complete and schedules nothing | `core/src/test/java/.../execution/TestDeciderOutcomes` |
| `Join` live targets | joins on the current last task of each branch, inside the taken case when a branch ends in a switch; waits while a branch lane still has a continuation to run; a detached join completes at once; a waiting join waits only for the lanes it names, for the forks they started, and for a lane not in the definition yet; waits when its fork is not in the definition yet | `core/src/test/java/.../tasks/NativeJoinTest` |
| Idempotent start | not implemented on this branch (it is in the standard-workflow PR #1857): key without id derives a stable id; `RETURN_EXISTING` returns the first id on a second call; `FAIL` conflicts | `core/src/test/java/.../execution/TestWorkflowExecutor` (in #1857) |
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
with `workflowExecutionService.poll(CODE_TYPE, workerId)`, and completes with
`workflowExecutionService.updateTask(new TaskResult(task))` whose output is built by a helper,
`completion(task, fragment, variables, done, result)`, that copies `n` from the polled task's
`workflowTask.metadata.continuation` and `retryCount` from its marker and sets `protocol: 2`. Entries are built by `simple(name, input, lane)`
(`SIMPLE`, `optional`, `metadata.lane`) and `paused(entry, lane, n)` (`metadata.continuation`).

| # | Scenario | Asserts |
|---|---|---|
| 1 | Linear: poll the code type → the bootstrap (`taskId` `<workflowId>\|main`, `taskType` the code type, entry metadata `main` #1, marker leased) → complete with `[integration_task_1 (paused main#2)]`, variables → complete the task → poll the shared queue → complete with `done` and a result | after the bootstrap: one task, `SCHEDULED`, entry metadata `main` #2, marker neither leased nor applied, input evaluated from the workflow input, variables applied, `nativeState.bootstrap` applied; the next poll returns the finished task itself, `COMPLETED` with its output, #2, marker leased; at the end the workflow is `COMPLETED` with the result as output, still one task, its marker applied |
| 2 | Fork with independent branches: the bootstrap completes with `FORK_JOIN fork_1` of `[integration_task_1 (paused fork_1.0#1)]` and `[integration_task_2 (paused fork_1.1#1)]`, `JOIN join_1` with `joinOn: []` paused `main#2` | both branch tasks `SCHEDULED`, the join `IN_PROGRESS`; completing only `integration_task_1` offers branch 0's continuation on the shared queue while `integration_task_2` is still `SCHEDULED`; completing it with `[integration_task_3 (paused fork_1.0#2)]` lands inside branch 0 only, `integration_task_3` is `SCHEDULED` before branch 1 moved, and the execution holds five tasks: fork, join and the three the code called |
| 3 | Stale completion: complete the bootstrap with a `retryCount` the server never handed out | `ConflictException`; no task, no definition entry, no variable applied. The lease holder's completion is then applied; the same completion delivered again (a retried update after a lost response) is refused and the execution still holds one task |
| 4 | A continuation fails for a reason unrelated to the task: the worker completes it `FAILED` with `protocol`, `n` and `retryCount` | the workflow stays `RUNNING` with `reasonForIncompletion` `worker reported FAILED`, the task keeps its `COMPLETED` status and output, the marker is offered again at once (leased) at `retryCount + 1`; no poll gets it within the 1 s backoff, and after it a poll hands it out at `retryCount + 1` |
| 5 | A JOIN in its backoff (postponed 600 s on the `JOIN` queue) after a fork of two lanes with one unpaused task each | when the first lane ends the JOIN stays in its backoff; when the last lane ends it is on its queue with no delay |
| 6 | Tasks named like code types (`plain.code`, `paused.code`), the second paused on | both are delivered to their workers and run in order; the continuation of the one paused on still arrives on the code type |

Scenario 2 is the acceptance test for the design: it is the kitchensink's `forkx` branch.

Covered end to end by the SDK repository rather than by the harness: handlers (claimed messages,
`replies`, `releases`, completion deferral), failover to another worker, `WAIT` and `HUMAN` timeouts,
restart, rerun and retry (`native-examples`: kitchensink, `operator.OperatorChecks`, `chaos.sh`
and the durability bench, see [06-implementation.md](06-implementation.md)), every construct with
its alternatives (`native-examples`' `primitives.Main`: one workflow per construct, each run against a
server with its result, the execution's shape and a replay of the execution against the code
checked), and `switchOn`
(`conductor-client`'s `SwitchOnTest`: the first continuation emits the switch with every case and
the taken body inside it; an empty case continues inline after the switch with no anchor; an
unhandled value takes the default case; an unhandled value without `otherwise` fails the workflow
with "no case for"; a lane that resumed inside a case anchors its next call after the switch with
`metadata.after`; a switch recorded on one case takes it again on replay whatever the code now
computes, with a warning (`aSwitchRecordedOnOneCaseTakesItAgainWhateverTheCodeNowComputes`), and
only a recorded case the code no longer declares is a `NonDeterminismException`
(`aRecordedCaseTheCodeNoLongerHasIsStillADivergence`); untaken cases show the tasks they would have called as preview entries
(`untakenCasesShowTheTasksTheyWouldHaveCalled`: `shipB_if_b`, a `fork_if_b` with two branches and
its `fork_if_b_join`, `notify_if_b`, a nested `inner_if_c` switch with `shipA_if_c_x` and
`shipB_if_c_default`, `notify_if_default`; every preview carries `metadata.preview` and no
marker, and the live instance's fields are untouched). `native-examples`' `switches.Switches` runs
a nested switch, an empty case, the default case and an unhandled value against a server; the
kitchensink checks the `SWITCH` task's `selectedCase` equals the input's `oddEven`). Discovery of
the SDK's result format and task-definition ownership by `conductor-client`'s `WorkerProtocolTest` (a failed continuation carries `protocol`; a definition owned by someone else is not overwritten); the registered definition is covered by `conductor-client`'s `DiscoveryTest`
(`theDefinitionHoldsTheGraphTheCodeDescribes`: name, description, `inputParameters`
`[item, region, quantity]`, `metadata.native.handlers: true` (it has a signal), tasks `[pick, fork, fork_join,
sleep, region]` with no `metadata.preview`, an inline `taskDefinition` on `pick`, two fork branches
`pack` and `pack_2`, cases `eu: [ship]` and `us: [ship_2]`, an empty default;
`discoveryReadsCodeThatWouldNeedARealValue`: `[pick, if_length]`, with `ship` in case `true`; `codeThatLoopsForeverIsReadWithoutRunningIt`; `discoveryDoesNotRunOnceSuppliers`), and reading compiled code by `CodeReaderTest` (code that would need real values is read to the end, both sides of an `if` and a loop body are seen, no plain Java runs, a finally block copied to every exit is one step, lambdas and helpers are followed, recursion ends, every kind of constant case key is read). Not covered by any
server-only test: that `initialize` clears a discovered task list from the execution's copy
(verified live, see 06). Not covered by any
server-only test: a lease expiring and the queue redelivering the same offer at the same `retryCount`; the handler lane through
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
| `no-retry-count-fence` | the `retryCount` check on completion | `NativeContinuationService.complete` |
| `join-ignores-lanes` | `lanesSettled` | `NativeWorkflows.lanesSettled` |
| `workflow-saved-last` | the workflow-row-then-tasks write order | `WorkflowExecutorOps.completeContinuation` |
| `no-claim-fence` | the ordinal fence on message claims | `JdbcWorkflowMessageQueueDAO` |
| `destructive-pull` | claim-based pulls (pop instead) | `PullWorkflowMessages`, `AsyncSystemTaskExecutor` |
| `no-stale-write-guard` | the executor's refusal to save over a task another run completed | `AsyncSystemTaskExecutor` |
| `terminate-on-storage-errors` | treating storage outages while scheduling as transient | `WorkflowExecutorOps` |

SDK mutants (`Workers`): `no-message-dedupe`, `drop-some-signals`, `no-reply-prune`.

Some server patches no longer apply to the current server code (`git apply --check` fails):
`join-ignores-lanes` (it still targets `NativeContinuationService`), `workflow-saved-last`,
`destructive-pull`, `no-stale-write-guard` and `terminate-on-storage-errors`; and
`no-variable-fencing.patch` is obsolete, since the server no longer checks variable conflicts.

Each mutant run must fail a bench check (`lost-signal`, `duplicate-signal`, `incomplete`,
`wrong-output`, `nondeterminism`, …) or an independent check (Elle, Porcupine).

## Performance checks

Run on Postgres with the `test-harness` functional profile:

| Check | Method | Pass |
|---|---|---|
| Append cost | 1,000 sequential continuations in one execution, each with a 2-task fragment; measure decide time per cycle and workflow row size | decide time grows linearly, not quadratically, up to the default `maxDefinitionTasks`; row size reported |
| Fork independence | scenario 2 with branch-1 task delayed 60 s | branch-0 progresses through 5 continuations before branch-1 completes |

`<!-- TODO: verify against live server -->` These checks have not been run on protocol 2.

## UI checks (manual until `ui-next` has component tests)

- An execution shows exactly the tasks the code called; nothing to fold.
- The task panel shows the continuation row (`lane #n — state`, lane and number from the entry's
  metadata, the state derived from the marker and the task) and the applied time from `endTime`
  on a task the code paused on, and the source line from `metadata.site`.
- A held execution (the workflow's `reasonForIncompletion` starts with `diverged: `) draws the
  held step after the task whose marker is leased; its panel shows "Waiting since" from when that
  task ended and "Retries so far" from its marker's `retryCount`.
- The execution summary shows the code type, the number of continuations
  applied, the bootstrap state while it is pending, and the handler lane's run number, running or
  idle.
- An appended `FORK_JOIN` renders with both branches and status overlay.
- An appended `SWITCH` renders with every declared case and the default case, the case named by
  `outputData.selectedCase` marked as taken; the fork and join a case body called sit inside that
  case, and the untaken cases show their preview entries (`<base>_if_<key>`) without a status, as
  the untaken branch of a JSON switch renders.
