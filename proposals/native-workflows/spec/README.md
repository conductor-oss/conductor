# The native protocol: a TLA+ specification

`NativeProtocol.tla` models how a native workflow runs. What a workflow means is in
[`../../workflow-semantics/Workflow.tla`](../../workflow-semantics/Workflow.tla). The
code is a tree: what a lane does next given what it has observed. The server holds a definition that
grows and a continuation marker on each task the code paused on. A worker leases a marker, replays
the code against what the server recorded, and submits a fragment of new tasks. Workers can crash,
leases can expire while a worker is slow, queue messages can be lost or delivered twice, and a deploy
can change the code. Signals, updates and variables come later; task retries are in `Workflow.tla`.

| Property | Promise |
|---|---|
| `AppliedOnce` | a continuation is applied at most once, however many workers ran it |
| `AppliedAtLatestLease` | only the latest lease can apply a result, so a stale worker changes nothing |
| `ReplayIsStable` | replaying the same marker on the same code gives the same decisions |
| `AsIfRunOnce` | every task is one the code reaches, on one build, given the results recorded: crashes, duplicates and replays add nothing |
| `OutcomeMatchesCode` | the workflow completed only if the code returned, and failed only if it raised, whatever crashes and replays happened |
| `JoinWaitsForLanes` | a join completes only when its lanes, and what they started, are over |
| `CompletedSound` | `COMPLETED` means the code returned and no lane has work left |
| `FailedSound` | `FAILED` comes only from the main lane raising, so a lane nothing joined cannot fail the workflow |
| `NeverStuck` | every entry lands where it will run: with nothing left to do, the workflow can complete |
| `SequenceOrder` | a task runs only after the entry before it ended |
| `OneContinuationAtATime` | only the worker holding the slot can have its result count |
| `AppliedOnFreshHistory` | a result applies only if nothing else was applied since its worker read the history |
| `Settles` | with a live worker every workflow ends, unless it is held waiting for a compatible build |

Eight small models (`MC_N*`) check these with up to two faults, up to about 10,000 states each.

`TLA2TOOLS=/path/to/tla2tools.jar python3 check.py` runs every model, 20 mutants that each break one
rule and must be caught, and 14 outcomes the models must reach. It takes a few seconds.

`check_runs.py` compares real native executions on a server with these promises, using what they
stored: tasks, the definition that grew, and the markers with their lease and apply times. On the
current build, 173 executions from the conformance suite and kitchen sink, and 80 run while workers
were killed, paused and cut off from the server, showed no violation. It flags the stuck workflow
from the kitchen sink (execution `e1ddf9a5`), so it can fail.

**Found by this work, and fixed.**

1. A join missed a lane started inside a switch case; `Join.addBranchTargets` now goes into the case
   the switch took. The kitchen sink checks it on a live server.
2. A slow worker's result was accepted after another continuation had run. `complete` now also requires
   that the worker still holds the slot; a refused continuation runs again. `AppliedOnFreshHistory`
   states this.
3. The SDK did not notice a recorded call the code no longer makes. Replay now holds such an execution.
4. A preview name for an untaken switch case could repeat a name from an earlier fragment, which failed
   the workflow. The expanded kitchen sink found this.

**What this does not show yet.** The link to `Workflow.tla` is by matching properties, not by a
formal refinement between the two modules, because `async` and joins that name lanes have no
equivalent there. Real executions are compared by their final state and timestamps, not step by
step. Continuations take about 20 ms, so the crash runs almost never caught one in progress: none of
those executions shows a re-lease. Re-leases, stale results and duplicates are checked only in the
model.
The model leaves out calls that do not pause the code, the separate database writes of a lease or a
completion (it treats each as one step), a worker reading state that is newer than its lease, and
a queue delivering one message twice (a duplicate result is modelled). Reference names are fixed per
node, so "each lane gets its own results" cannot fail in the model. Generated programs
(`enumerate.py`, in `workflow-semantics`) cover `Workflow.tla` only. Up to two faults and small programs
are checked, so this is not a proof for programs of any size.
