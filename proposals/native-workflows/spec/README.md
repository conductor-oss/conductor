# The native protocol: a TLA+ specification

`NativeProtocol.tla` models how a native workflow runs. What a workflow means is in
[`../../workflow-semantics/Workflow.tla`](../../workflow-semantics/Workflow.tla). The
code is a tree: what a lane does next given what it has observed. The server holds a definition that
grows and a continuation marker on each task the code paused on. A worker leases a marker, replays
the code against what the server recorded, and submits a fragment of new tasks. The epoch of a lease
is its offer's: a marker that becomes runnable is offered at a new epoch, and a lease that ran out is
either delivered again under the same offer, so two workers may run it and the first result applies,
or offered again at a new epoch when its queue is no longer polled; no other continuation takes the
slot meanwhile. Workers can crash, report that they could not run a continuation (it is deferred and
offered again), and be slow past their lease; queue messages can be lost or delivered twice; and a
deploy can change the code. A switch draws the cases it did not take as previews. When the main
lane's code raises, the execution names where: the task whose
failure it did not catch, or a mark entry where it threw, created failed and never scheduled,
followed by previews of what the code would have called next. An operator may retry such a run: the
mark and previews go and the code runs again, on whatever build runs then. Signals, updates and
variables come later; task retries are in `Workflow.tla`.

| Property | Promise |
|---|---|
| `AppliedOnce` | a continuation is applied at most once, however many workers ran it |
| `AppliedAtLatestLease` | only a result of the current offer can apply, so a worker from an earlier offer changes nothing |
| `ReplayIsStable` | replaying the same marker on the same code gives the same decisions |
| `AsIfRunOnce` | every task is one the code reaches, on one build, given the results recorded: crashes, duplicates and replays add nothing |
| `OutcomeMatchesCode` | the workflow completed only if the code returned, and failed only if it raised, whatever crashes and replays happened |
| `JoinWaitsForLanes` | a join completes only when its lanes, and what they started, are over (their previews never run); a lane that never paused, because it only started other lanes, is over when they are |
| `CompletedSound` | `COMPLETED` means the code returned and no lane has work left, and no mark of an earlier failure is left |
| `FailedSound` | `FAILED` comes only from the main lane raising, so a lane nothing joined cannot fail the workflow |
| `FailedNamesItsCause` | a `FAILED` workflow names what the main lane's raise names: the task whose failure it did not catch, or the mark where it threw; never another failed task, such as one whose failure the code caught |
| `MarksAndPreviewsNeverRun` | a mark is failed from the start and a preview, of a case not taken or of what thrown code skipped, never starts: they are drawn, never run |
| `EndedIsQuiet` | an ended workflow has nothing running: the main lane raising cancels what other lanes still run |
| `UntakenStayDrawn` | a switch shows the cases it did not take for as long as it is drawn: a retry removes only the mark and what the throw skipped |
| `NeverStuck` | every entry lands where it will run: with nothing left to do, the workflow can complete |
| `SequenceOrder` | a task runs only after the entry before it ended |
| `OneContinuationAtATime` | only the worker holding the slot can have its result count |
| `AppliedOnFreshHistory` | a result applies only if nothing else was applied since its worker read the history |
| `Settles` | each time it runs, including after a retry, a workflow ends, unless it is held waiting for a compatible build (`running ~> ended or held`) |

Fourteen small models (`MC_N*`) check these with up to two faults, up to about 12,000 states each.
`MC_NThrow` has code that throws between two calls, a deploy that fixes it, and up to two retries;
`MC_NSwitch`'s switch has a case it does not take; in `MC_NNestedSwitch` a switch inside another's
case is followed by a call in the outer case, so the code goes on in the outer case after the inner
one (a switch belongs to the case it is in, like any other entry). `MC_NSpawnOnly` has a lane that
only starts another and returns, `MC_NLanePreview` a joined lane whose switch draws a preview,
`MC_NThrowSwitch` a switch preview before code that throws and is retried, and `MC_NFailRunning` a
main lane that raises while another lane's task runs.

`TLA2TOOLS=/path/to/tla2tools.jar python3 check.py` runs every model, 31 mutants that each break one
rule and must be caught, and 25 outcomes the models must reach. It takes a few seconds.

`check_runs.py` compares real native executions on a server with these promises, using what they
stored: tasks, the definition that grew, and the markers with their lease and apply times. On the
current build, 173 executions from the conformance suite and kitchen sink, and 80 run while workers
were killed, paused and cut off from the server, showed no violation. It flags the stuck workflow
from the kitchen sink (execution `e1ddf9a5`), so it can fail.

`check_paths.py` checks, for any native execution, that the workflow it built matches the path its
code took: every task that ran is drawn and every drawn entry ran (a failed run may stop short), no
preview ran and each sits in an untaken arm or after the mark of the exception that ended the run,
every switch took exactly the arm of its `selectedCase`, each lane's entries stay in one fork branch,
the tasks of a list ran in its order, loop tags nest, and a failed run names its failed task. On
2026-11-11 it passed the 690 executions made under the current rules on the test server, and flagged
the earlier ones: failed runs from before the mark existed, and one run a retry bug had corrupted.
`check_paths_mutants.py` shows it rejects 21 of 21 executions broken in one way each, among them a
reference drawn twice. `check_runs.py` takes every task to be in the definition (a fan-out's copies
are drawn too): on 2026-11-13 it passed the 1,105 native executions on the test server.

`kitchensink_spec.py` goes further for the kitchen sink: a reference semantics of
`KitchenSinkWorkflow`, `SubFlow` and `KitchenSinkTasks`, written from the Java source without the
SDK, interprets the program against a recorded execution step by step. It proves that the
recording is exactly the execution the code denotes for its input, given the values the code does
not determine (external results, generated ids, when handler messages arrived): every step and
input in every lane, task outputs, the arm every `if` and `switch` took and the arms and previews
its SWITCH shows, order within lanes and at joins, attempts, the output and the variables, and each
child `sub_flow_1`. On 2026-11-11 the odd, even and failure executions of a `kitchensink.Main` run
and their 13 children passed (838, 777 and 810 checks), and `kitchensink_mutants.py` showed it
rejects 19 of 19 recordings broken in one way each (18 under `--chaos`, where an extra attempt is
allowed). It first checks that the recording as it is passes, so a recording that does not cannot
make every mutant look rejected, and a server error never counts as a rejection. It covers those executions, not every run.

    python3 kitchensink_spec.py --server http://localhost:8099/api <odd id> <even id> <failure id>
    python3 kitchensink_mutants.py --server http://localhost:8099/api <odd id>

With `--chaos` it checks executions run under faults (`durability.KitchenSinkChaos` in
`native-examples`: workers killed, paused and cut off from the server at random, seeded): what
infrastructure failures decide is then part of what the code does not determine (extra failed
attempts of a task whose worker died, when the per-process `check_status` counter reports done, a
`charge` that fails because its worker died), and everything else is checked as before.

None of these checkers passes on no evidence: `check_runs.py` fails when it finds no native
execution, `check_paths.py` when given none, and `check_drawing.py` when it ran no program or when
an execution it recorded got no verdict from the replay (the replay tool failing or stopping early).

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
step. Continuations take about 20 ms, so a crash rarely catches one in progress; the kitchen sink
chaos runs of 2026-11-11 did (a worker killed holding a join's continuation, delivered again after
the lease), but stale results and duplicates are still checked only in the model.
Not modelled: the handler lane (signals, updates, variables, queries), which runs between the main
lane's continuations, and whose messages a completion waits for (modelled only as a worker's
deferred report); retrying a run from a task that failed, which reruns that task by the decider's
rules (`Workflow.tla`); an operator skipping a task the code waits for; server-side loops (`forEach`, `repeatUntil`), which are DO_WHILE tasks to the
protocol. The model leaves out calls that do not pause the code, the separate database writes of a lease or a
completion (it treats each as one step), a worker reading state that is newer than its lease, and
a queue delivering one message twice (a duplicate result is modelled). Reference names are fixed per
node, so "each lane gets its own results" cannot fail in the model. Generated programs
(`enumerate.py`, in `workflow-semantics`) cover `Workflow.tla` only. Up to two faults and small programs
are checked, so this is not a proof for programs of any size.

## How plain Java control flow is drawn

`check_drawing.py` is the spec checker for how plain `if`, `switch` and loops are drawn. The rules
are written once, as definitions on the control flow (the java-sdk's
`design/native-workflows/README.md`, "Plain ifs and switches"): natural loops and their follow,
decisions and their outcomes, an arm as what runs only because its outcome was taken, and when a
decision is drawn. The SDK implements them on the bytecode; the checker computes the same
definitions on its own graph of each program, never asking the SDK or the server. It generates
workflows (loops nested in loops, if / else if / else, old-style and arrow switches over ints,
strings and enums, `continue` and `break`, guard clauses, helpers, `return` and `throw`, a `while`
whose condition calls a task, do-while, mixed and nested at random), interprets each to know every
pass and arm taken, runs it on a server, and checks the execution: S (every SWITCH drawn is a
decision the code made, with its arm; a task inside a case is in that arm's region), C (every
decision the calls make known is drawn, and every task in an arm's region is drawn in its case),
P (cases not taken preview the calls in their arm's region), L (loop tags), R (result), D (the
execution replays against the code). When two calls cannot tell whether the code went round a
loop to a decision again, the call is not drawn in it until a later call shows it for sure; the
checker tracks the same certainty. A catalogue of shapes that once failed always runs.

The java-sdk's `native-examples` `branches.Check` runs every way of writing an if or a switch
(else-if chains, `&&`/`||`, negation, ternaries, `instanceof` patterns, guard clauses with
`return`/`continue`/`break`/`throw`, old and arrow switches, stacked and comma labels, fall-through,
string, char, enum, boxed and task-valued switches, switch expressions with `yield`, exhaustive
enum and sealed switches, guarded patterns, `case null`) and nested mixes of them (ifs in cases,
switches in arms, switch expressions in else-ifs, pattern switches in loops, guards inside nested
arms, loops inside arms inside loops), 126 executions, each checked for result, drawing, previews
and replay.

    python3 check_drawing.py --server http://localhost:8099/api --programs 0 --seeds 6       # the catalogue
    python3 check_drawing.py --server http://localhost:8099/api --programs 60 --seeds 4 --seed 2

On 2026-11-12, after the SDK's reading was rebuilt on these definitions: the catalogue (14
programs, 56 executions), seed 2 (60 programs, 240 executions, 1,679 calls, 745 switches), seed 13
(100 programs, 400 executions, 4,225 calls, 1,641 switches) and `branches.Check` (126 executions)
had no failing execution, and every execution replayed. Aligning the checker to the same
definitions removed its exemptions for what javac "cannot tell apart"; the disagreements that
remained were each a fault on one side, fixed there: in the SDK, `break` blocks javac lays after a
loop's last jump, arms that are not one stretch of bytecode, a loop whose only other way out throws,
a recursive call not counted as a step, a case falling into the next laid out last, an
`else { break; }` taken for the loop's condition, and branches nested by line span instead of by
their arms; in the checker, arms that ran into the next pass of a loop or past a helper's return,
and an arm defined as what the other arms do not reach (rather than what runs only through it).
