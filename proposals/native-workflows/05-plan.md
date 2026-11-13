# 05. Plan

Status: delivered. The work went in two rounds. The first built the growing-definition engine
with engine-only tasks for the pauses (protocol 1); the second, planned in
[07-engine-state.md](07-engine-state.md), replaced those tasks with continuation markers and engine
state (protocol 2), which is what this branch runs. This page records how the work was staged,
what shipped in each PR-sized unit, and the decisions taken.

## PR breakdown

| PR | Contents | Depends on | Size | Risk |
|---|---|---|---|---|
| 1 | Definition edits at any depth (`insertAfter`, `removeAll`), empty task list for native definitions; properties under `conductor.app.nativeWorkflow`; `NativeContinuationService` validation, append and variables; decider hook; metrics; unit tests | – | 5 d | medium; the core change |
| 2 | `Join` live targets; `NativeJoinTest`; harness scenario 2 | 1 | 2 d | medium; touches fork semantics |
| 3 | Durable workflow message queue (`JdbcWorkflowMessageQueueDAO`, migrations for SQLite, Postgres and MySQL); claim-based pulls, claim fence; registration-time check for the queue flag | 1 | 2 d | low |
| 4 | (dropped: `WaitTaskMapper` time scale) | | | |
| 5 | Idempotency key on public start | – | 1 d | low |
| 6 | (dropped: `JOIN` any-of) | | | |
| 7 | Operator operations: native-aware `restart`, `rerunWF`, `retry` | 2 | 3 d | medium; touches operator paths |
| 8 | Durability fixes from the bench: write order, completion deferral, variable fencing, message de-duplication, storage outages as transient, stale-write guard, Jepsen review items | 1 to 7 | 4 d | medium |
| 9 | Protocol 2: `TaskContinuation` and `NativeState`; `TaskMapperContext` copies the marker; `lease` in `ExecutionService.poll`; `completeContinuation` in `WorkflowExecutorOps.updateTask`; `repairAndWake` in `decide`; markers in `Join`, rerun, retry, restart; the message endpoint wakes the handler marker; removal of the `CODE` and `RECORD` task types; harness spec rewritten; mutants re-mapped (`ignore-epoch`) | 1 to 8 | 5 d | medium; replaces the pause mechanism |
| 10 | `ui-next`: continuation on the task panel, native state on the execution summary | 9 | 0.5 d | low |
| 11 | SDK protocol 2: `Registration`, `Workers`, `Replayer`, `Execution`, `Entries`, `WorkflowHandle`; bench summaries and checker on markers | 9 | 3 d | medium |
| 12 | Documentation: this proposal set, `DURABILITY.md`, `COMPARISON.md`, the SDK design pages and bench page | 9, 11 | 1 d | – |

PRs 1 and 2 were the critical path of the first round; PR 9 is the critical path of the second.

## Order and milestones

1. **M1, flat linear execution** (PR 1): a test can start a native skeleton and see a flat
   definition grow one continuation at a time.
2. **M2, independent branches** (PR 2): the kitchensink `forkx` scenario passes. This is the
   acceptance milestone for the design.
3. **M3, handlers, timers and operators** (PRs 3, 4, 7): the handler lane runs on a durable
   queue; dev mode can scale timers; restart, rerun and retry behave on a grown definition.
4. **M4, durability** (PR 8): the chaos run and the Jepsen-style bench pass with every fault
   profile, on one server and on a three-server Postgres cluster.
5. **M5, exactly the code's tasks** (PRs 9 to 12): an execution holds only the tasks the code
   called; the UI needs no fold; the bench passes again on protocol 2.

## Rollout and configuration

- No feature flag is needed for safety: nothing happens unless a definition carries
  `metadata.native`. `conductor.app.nativeWorkflow.enabled` (default true) lets an operator refuse
  registration of native definitions.
- The handler lane needs `conductor.workflow-message-queue.enabled=true`. The SDK documents it;
  the server logs at registration when it is missing.
- Limits default conservatively (02 §8, 03 B2) and are raised by the large-loops round.
- The continuation lease is `metadata.native.continuationTimeoutSeconds`, set per worker build
  with `workers().continuationTimeout(...)` (30 s default, an hour in dev mode). Executions
  snapshot it at start.

## Risks

| Risk | Mitigation |
|---|---|
| Definition growth per append rewrites the workflow row, and decide loads all tasks; long native loops degrade | `maxDefinitionTasks` fails cleanly with a named limit; the large-loops round addresses incremental decide; the SDK's `repeatUntil` and `forEach` keep loops server-side |
| `joinOn: []` after `FORK_JOIN` changes meaning | gated on `metadata.native`; other definitions unchanged |
| Poll data lookups | `queueName` makes one `getTaskPollDataByDomain` per domain listed for the code type, and none by default |
| Two workers holding the same continuation | one lease per marker under the execution lock; a completion must name the lease's `n` and `epoch`; the loser gets 409 |
| A dead domain's queue holds a wake-up | only when an execution was started with task domains: `queueName` skips a domain whose poll data is stale; `repairAndWake` re-pushes on every decide; `lease` drops the duplicate once the lease is held elsewhere |
| Lock lease with large fragments | validation is linear in fragment size; limits bound it; `maxRuntime` guard persists and re-queues if ever hit |
| Queue redelivery granularity after a worker death | the lease (30 s) plus the queue's redelivery of an unacknowledged message; the sweeper's `repairAndWake` covers a lost message |

## Open questions, resolved

1. **Variables via the completion output or via `SET_VARIABLE` tasks.** Applied directly in
   `complete`, fenced by `resumedWith`; no task row. Handler completions could not append tasks
   anyway.
2. **Per-worker domains for ownership versus per-host.** Neither: resolved on 2026-11-09 by removing
   per-worker routing. Continuations go to the shared queue of the code type by default and follow
   `taskToDomain` when the workflow is started with domains.
3. **`@Update` replies.** `nativeState.replies`, keyed by the client's message id, pruned to 10
   minutes and 500 entries by the handler completion; the client reads them from the execution.
4. **Observed-graph definition view.** Deferred at the time; superseded on 2026-11-08 by the discovered definition registered from the code (see 01 §1 and 02 §2).
5. **`conductor.app.nativeWorkflow.enabled` default.** True.
6. **Pauses as tasks or as markers.** Markers (protocol 2): the hidden tasks doubled the task
   count of every execution, needed a UI fold, and their retry and timeout semantics duplicated
   what a queue lease provides.

## Decisions taken after the end-to-end trace

Tracing the kitchensink through the design found these gaps; each is now part of the documents.

| Gap | Resolution | Where |
|---|---|---|
| A task failure terminated the workflow before the lane's next continuation, so `catch (TaskFailure)` could never run | every task a lane waits on is `optional` and keeps its real status in native workflows; timeout policy `RETRY` for awaited tasks | 01 §7, 02 §8 |
| A signal arriving before the main lane started could reach a worker holding stale state | the handler lane runs on an instance restored from the current variables; its writes are fenced by `resumedWith`; completion is deferred while messages are pending | 01 §4, §5 |
| A fresh instance started from class defaults | every continuation restores `@Variable` fields from the variables recorded when it first ran (`resumedWith` on the marker) | 02 §5 |
| An `async` lane with no server call would give `ForkJoinTaskMapper` an empty branch | the SDK inlines finished lanes' entries before the fork and omits a fork with no branches | 02 §8 |
| A zero-duration `WAIT` waits forever | the SDK never emits one; `repeatUntil` checks after the first interval | 02 §8, §9 |
| Hidden pause tasks doubled every execution and needed a fold | continuation markers on the tasks the code paused on; bootstrap and handler lane as engine state | 07 |

## Acceptance criteria

- `NativeWorkflowSpec` passes on the in-memory and Postgres profiles.
- The kitchensink, driven by the SDK, produces an execution whose definition, after completion,
  contains the same task and reference names as `rest/src/main/resources/kitchensink/kitchensink.json`
  and nothing else.
- Branch independence (scenario 2) holds with a 60-second delay on one branch.
- Failover completes within the continuation lease plus the queue's redelivery of a worker's death.
- No change to any existing integration spec's expectations.
