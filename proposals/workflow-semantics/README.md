# What a workflow means: a TLA+ specification

`Workflow.tla` states what a Conductor workflow means, independent of how the server runs it. It is
the reference that implementations are checked against: the server's decider today, and native
workflows, whose code supplies the program as it runs.

A workflow is a program: a sequence of tasks. A leaf task is run by someone else: a worker, an HTTP
call, an event, a person, or a wait. Every other task composes tasks:

| Construct | Meaning in the spec |
|---|---|
| sequence | each task starts when the one before it has finished |
| `SWITCH` | runs the one case its value picks; the last case is the default, possibly empty |
| `FORK_JOIN` + `JOIN` | runs its branches in parallel, starting them together. What follows starts when the branches the `JOIN` names have finished; other branches run on |
| `FORK_JOIN_DYNAMIC` | runs a number of copies of one task, chosen at run time, and finishes when all have |
| `DO_WHILE` | runs its body, then goes on or stops |
| `SUB_WORKFLOW` | runs another program; the task takes the child's outcome |
| `TERMINATE` | ends the workflow with the status it names; what started at the same moment is cancelled |

Data is abstract. The world chooses each leaf's answer (completed, failed, failed with a terminal
error) and every choice that depends on data: a switch's case, a dynamic fork's width, a loop's
condition. The spec says what follows from those choices.

Each leaf has its own rules. A failed attempt is retried while its budget lasts (`retryCount`), unless
it failed with a terminal error or its total time budget is spent. When no attempt is left, an optional
task completes with errors and the workflow goes on; otherwise the workflow ends `FAILED`, `TIMED_OUT`
(the task timed out) or `TERMINATED` (the task was cancelled).

Every timeout of a task follows its timeout policy: `timeoutSeconds`, `pollTimeoutSeconds` (no
worker took the attempt), `responseTimeoutSeconds` (the worker stopped reporting) and
`totalTimeoutSeconds` (the budget across all attempts and retry delays). Under `RETRY` the attempt
times out and may be retried; a spent total budget allows no further attempt, so the task then fails
like any other, and an optional one completes with errors. Under `TIME_OUT_WF` the task and the
workflow time out. Under `ALERT_ONLY` nothing changes.

A fork starts the first task of each branch in the same step as itself, as the server schedules
them in one decision, so a `TERMINATE` at the head of one branch does not stop the other branches'
first tasks from starting; they are then cancelled. Several `TERMINATE`s can run together this way,
and the workflow ends as one of them says. A `TERMINATE` that starts after another has run, in
the same decision (inside a switch case or loop at the head of a branch), may run too or be
cancelled.

A loop iteration is a scope: the next iteration starts only once everything the last one started has
settled, including fork branches no JOIN waits for. Ending a workflow cancels its running tasks and
terminates the children of its sub-workflow tasks.

Operators can pause, resume, terminate, skip a task that has not run, rerun from the start or from a
task, and retry. A retry gives each task that had not succeeded one new attempt; a rerun runs them
again in place and uses no retry, and cannot start from a skipped task. A `TERMINATE` that ran
ends the workflow again whenever it is reopened; a skipped one does nothing. A workflow that ends with a failure of its own starts its failure workflow.

## What it promises

| Property | Promise |
|---|---|
| `Justified` | nothing runs unless the program reached it |
| `SequenceOrder` | a node starts only after everything before it in its sequence is over; stated with its own definition of "over", separate from the rules |
| `CopiesWithinWidth` | a dynamic fork makes no more copies than its width |
| `TerminateDecides` | once a `TERMINATE` has run, the workflow ends as it says, unless its parent ended it first |
| `NoAnswerAfterTerminate` | once a `TERMINATE` has run, a running attempt can only be cancelled |
| `ForkStartsTogether` | a fork starts together the first task of each branch that starts with a task or a `TERMINATE` |
| `EarlierIterationsSettled` | every task in a loop's finished iterations succeeded |
| `SwitchExclusive` | a switch runs only the case it chose |
| `JoinWaits` | what follows a fork starts only after the branches its `JOIN` names have finished |
| `LoopInOrder` | a loop moves to its next iteration, or stops, only after the current one finished and settled |
| `SkippedNeverRuns` | a skipped task never runs |
| `OptionalOnlyWhenExhausted` | a task completes with errors only when it is optional and failed with nothing left: a terminal error, no retries, or no time |
| `RetryBudget` | a task is retried automatically at most as often as configured |
| `TimeoutsFollowPolicy` | an attempt times out, or a total budget is spent, only under `RETRY` or `TIME_OUT_WF`, and under `TIME_OUT_WF` the workflow times out with it |
| `RetryOnlyWhenAllowed` | an automatic retry follows only an attempt that failed or timed out, with retries and time left |
| `EndedIsQuiet` | nothing runs in an ended workflow |
| `CompletedSound` | `COMPLETED` means everything that started finished, every branch of every fork included, and nothing is left, or a `TERMINATE` that ran said so |
| `FailedSound` | a failed end has a reason: a task with no recovery left, a `TERMINATE`, a timeout, an operator, or the parent ending |
| `OutputSound` | the output comes from the `TERMINATE`, the `outputParameters`, or the task scheduled last |
| `FailureIsNotSuccess` | after a failure, the only way to a successful status is `COMPLETED_WITH_ERRORS` |
| `SubMirrorsChild` | a `SUB_WORKFLOW` task's outcome is its child's |
| `NewRunNewChild` | every new run of a `SUB_WORKFLOW` task gets a new child |
| `ParentEndTerminates` | a child its parent ended is `TERMINATED` |
| `ChildrenFollowParent` | a child runs only while its parent's `SUB_WORKFLOW` task waits for it |
| `NeverStuck` | a running workflow with nothing running and nothing to start has finished its body, so it can complete |
| `FailureWorkflowSound` | the failure workflow starts exactly when the workflow ends with a failure of its own, not an operator's or its parent's terminate |
| `EndIsFinal` | an ended workflow stays ended until an operator reruns or retries it |
| `SuccessIsFinal` | a task that succeeded keeps its result until an operator reopens the workflow |
| `ReopenSound` | a retry gives each task that had not succeeded one new attempt; a rerun runs it again in place; both give a fresh time budget and keep what had succeeded, except the task a rerun starts from |
| `PausedIsFrozen` | while paused, nothing starts and nothing is decided; running attempts can still finish |
| `Settles` | if the server keeps going and tasks are answered, every workflow ends, unless it is paused or waits on a paused child |

## How it is checked

**Models.** Each is a small program checked against every behaviour the world and the operators
allow.

| Model | Program | States |
|---|---|---|
| `MC_Compose` | a task retried once; a switch whose default is empty; an optional task; a fork whose `JOIN` waits for one of two branches; a last task | 416 |
| `MC_Loop` | a loop of up to two iterations whose body is a switch and a dynamic fork of up to two optional copies | 7,384 |
| `MC_LoopFork` | a loop around a fork whose first branch ends in a switch and whose `JOIN` waits for that branch only; then a sub-workflow | 1,219 |
| `MC_Sub` | a sub-workflow retried once, whose child may `TERMINATE` as failed; a task; a failure workflow; one operator action | 12,530 |
| `MC_Timeout` | a sub-workflow; a task that can time the workflow out; a workflow timeout; a failure workflow | 194 |
| `MC_Term` | a fork of a task and a `TERMINATE` as failed, then a task; a failure workflow; one operator action | 1,149 |
| `MC_ForkEnd` | a fork of a `TERMINATE` as completed, a `TERMINATE` as failed then a task, and a task; one operator action | 45 |
| `MC_Skip` | a task, a `TERMINATE` as completed, a task; a failure workflow; two operator actions | 21,416 |
| `MC_Dyn` | a dynamic fork of up to two copies of a task retried once, then a task; one operator action | 8,025 |
| `MC_Budget` | a task with a retry and a total budget under `TIME_OUT_WF`, then a task | 30 |
| `MC_Unjoined` | a fork of a task and a loop, joining only the task, then a task | 689 |
| `MC_Ops` | a task with a retry and a total budget; a fork of an optional task with a retry and a total budget and a plain task; a task with timeouts and a total budget under `ALERT_ONLY`; two operator actions of any kind | 93,047 |
| `MC_LoopOps` | a loop of up to two iterations around a switch whose case retries once, then a task; two operator actions of any kind | 24,449 |

**Are the properties able to fail?** `check.py` runs the models, then:

- 63 mutants each break one rule, and each must be caught by a property it names, or must make an
  outcome unreachable that the rule allows.
- 26 witnesses each name an outcome a model must reach: the workflow completes, fails, and times
  out; a branch runs on past its join; a child is terminated with its parent; a reopened workflow
  ends again at its `TERMINATE`; a skipped `TERMINATE` is passed over.

**Every program, not chosen ones.** `enumerate.py` generates every program up to a size from all the
constructs and every task kind, and checks each one with all the properties.

On this version: every program up to 3 nodes (1,314), a sample of 600 programs of 4 nodes, and a
sample of 400 programs of 3 nodes with one operator action. 2,303 pass, none breaks a property, and
11 hit the 15-minute limit and remain unchecked, all of them with an operator action.

**Against a real server.** `server_check.py` runs generated programs on a server as ordinary
workflows. It fixes the world's choices in advance: each attempt's answer, each switch's case, each
loop's iterations, each dynamic fork's width. It answers every task as planned, and some tests then
retry or rerun the ended workflow. TLC then confirms that the server's final state (each task's
status and attempts, the workflow's status, whether the failure workflow started) is one the spec
reaches under those choices. Each test also checks an observation the spec cannot reach, which must
not match.

On this version, before the fix of item 5, 384 of 400 tests matched (200 of 3 nodes, 200 of 4).
The other 16 are server behaviours listed below: 6 of item 1, 2 of item 2, 1 of item 6, and 2 of
item 5 where the loop is the last task. 4 reruns never settled and 1 rerun of a `SUB_WORKFLOW` in a
fork ended in a state the spec does not reach; these fit items 3, 4, 5 and 8 but were not examined
one by one. With item 5 fixed, the same 400 shapes gave 389 matches: no loop mismatch is left
except a rerun of a `SUB_WORKFLOW` inside a loop, which takes item 4's path. The other 10 are 6 of
item 1, 1 of item 6, 1 dynamic-fork rerun (item 3), 1 `SUB_WORKFLOW` rerun (item 4) and 2 reruns
that never settled. With items 1 to 4 and 8 also fixed, all 400 matched.

**Reviews.** Three independent reviews, each given only the code and the spec, found rules that
disagreed with the server and properties that could not fail; this version addresses them.

To run the checks, download `tla2tools.jar` from the
[TLA+ releases](https://github.com/tlaplus/tlaplus/releases) (version 1.8.0 was used) and run, with
Java 11 or later:

```
TLA2TOOLS=/path/to/tla2tools.jar python3 check.py
TLA2TOOLS=/path/to/tla2tools.jar python3 enumerate.py --size 3 --limit 5000
TLA2TOOLS=/path/to/tla2tools.jar python3 server_check.py --server http://localhost:8099/api --tests 200
```

`TLA2TOOLS=/path/to/tla2tools.jar ./verify.sh` validates the spec in about a minute: every model,
mutant and witness, and 100 generated programs at 20 seconds each. Give it a server URL to add 40
tests against that server. The commands above are the full runs, for a final sign-off.

`./run.sh MC_Compose` checks one model.

**What these checks do not show.** TLC checks small programs, within bounds of two loop iterations,
two dynamic copies and two operator actions. Nothing here is a proof for programs of any size.
Some properties still mirror the rule they check (`RetryOnlyWhenAllowed`, `TimeoutsFollowPolicy`,
`FailureWorkflowSound`, `LoopInOrder`, `Justified`); for those, the independent evidence is the server
tests. The server tests compare final states only, so ordering during a run is not compared, and they
check only that the server stays within the spec, so a spec that allows too much goes unnoticed. They
use no timeouts, pause, skip or operator terminate, no dynamic fork inside a loop, and each switch
takes the same case in every iteration.

## Where the server differs

The spec states the intended meaning; these are places where the server did something else.
All are fixed on this branch; each is marked *(fixed)*.
Details, evidence, fixes and examples are in [DECIDER_BUGS.md](DECIDER_BUGS.md).

**Observed on a running server** by `server_check.py`:

1. *(fixed)* **An optional task that fails last keeps `FAILED`.** When the decision that changes it to
   `COMPLETED_WITH_ERRORS` also completes the workflow, `WorkflowExecutorOps.decide` calls
   `endExecution` before saving the task. An optional task that fails earlier is saved correctly.
2. *(fixed)* **A JOIN can fail the workflow while a branch task still has a retry.** The JOIN sees the failed
   attempt before the retry exists, fails, and the retry is cancelled. It is a race, seen twice; the
   mechanism is inferred from the code.
3. *(fixed)* **A rerun from a task in a fork loses what the other branches scheduled after it.** The rerun
   removes every task with a later `seq` and does not create those again. In a static fork the `JOIN`
   then waits forever. In a dynamic fork the workflow completes without the later copies, or never
   settles.
4. *(fixed)* **A rerun from a `SUB_WORKFLOW` task does not run the child again.** Child ids are deterministic,
   so the old child's result is reused. That path also keeps the tasks after it, and resets failed
   ones, so they run at the same time.
5. *(fixed)* **A rerun from a task inside a loop can hang.** The workflow stays `RUNNING` with nothing running.
6. *(fixed)* **A retry after a `TERMINATE` that failed the workflow ends it `TERMINATED`,** not `FAILED`: the
   cancelled `JOIN` ends the workflow before the `TERMINATE` is looked at.
7. *(fixed)* **A workflow ended `FAILED` with a task still `IN_PROGRESS`, for good.** It looks like a worker's
   poll overwriting the cancellation. Seen once; the mechanism is inferred.
8. *(fixed)* **A rerun from an older attempt of a retried task leaves a fork's `JOIN` waiting forever.** The
   spec has no older attempts, so this is outside it. Seen once. A rerun from such an attempt now
   reruns from the task's latest attempt.

**From reading the code**, not reproduced on a running server:
<!-- TODO: verify against live server -->

9. *(fixed)* **A JOIN after a branch that ends in a SWITCH completes early.** When `joinOn` names the SWITCH,
   `Join.execute` waits for it to be terminal, and `Switch.execute` marks the SWITCH `COMPLETED` as
   soon as it picks a case, while the case's tasks are still running.
10. *(fixed)* **An operator can terminate a workflow that already ended.** `terminateWorkflow(workflowId,
    reason)` refuses only `COMPLETED` and `TERMINATED` workflows, so a `FAILED` or `TIMED_OUT` one
    becomes `TERMINATED`. Likewise, cancelling a `SUB_WORKFLOW` task whose child has just ended sets
    the child to `TERMINATED`.
11. *(fixed)* **A `COMPLETED` workflow can be retried.** `retry` requires only a terminal status.
12. *(fixed)* **The total time budget survives a retry or rerun.** `firstScheduledTime` is copied, so a task
    whose budget was spent times out again at once. In the spec, a reopened task has a fresh budget.
13. *(fixed)* **Skipping can leave the workflow waiting.** Only the decider's sequential path (`getNextTask`)
    passes over a skipped task. Skipping the first task of a switch case or a fork branch stops that
    case or branch, and skipping a `FORK_JOIN` schedules its `JOIN` alone (*inferred*).

## Decisions

Each of these is a choice about what a workflow means, reviewed and confirmed by the maintainer. The
spec follows the server on all of them; the fresh time budget is what the server does since bug 12
was fixed (see `DECIDER_BUGS.md`).

- A loop iteration waits for everything it started, including fork branches no JOIN waits for.
- A `TERMINATE` that ran ends the workflow again when it is retried; only a rerun from before it
  undoes it.
- A retry and a rerun both give a task a fresh total time budget.
- An operator terminating a child ends its parent `TERMINATED` with the cancelled task as the cause,
  so the parent's failure workflow starts.
- The workflow's own timeout can fire while it is paused, whenever something has the server evaluate
  it (a task update, for example); task timeouts cannot. Pausing takes it off the sweeper's queue.
- Terminating a workflow, by an operator or because its parent ended, does not start its failure
  workflow: that is an action taken on the workflow, not a business failure of its
  own.
- A workflow with no `outputParameters` outputs the last scheduled task's output, so with parallel
  branches the output depends on timing.
- A rerun from a task forgets everything scheduled after it, in time order, not program order: a
  rerun from a task in one branch also redoes tasks another branch scheduled later.
- A retry counts against the retry budget, so a task retried after using its retries gets one
  attempt and no automatic retries; a rerun does not count.

## Not covered

- Data: variables, `SET_VARIABLE`, inputs and outputs, expressions, and schema validation.
- The statuses of operator tasks themselves (`SWITCH`, `FORK`, `JOIN`, `DO_WHILE`), and evaluation
  errors that fail them: a switch expression or loop condition that cannot be evaluated.
- `DO_WHILE` over `items`, which can run no iteration; nested loops; a `JOIN` whose `joinOn` names a
  task other than a branch's last.
- `EXCLUSIVE_JOIN`, `START_WORKFLOW`, `joinStrategy: ANY`, permissive tasks, and rate limits.
- Timeouts on `SUB_WORKFLOW` tasks, and skipping a task inside a loop.
- Rerunning from an operator task or from a task inside a sub-workflow, and retrying or rerunning a
  child on its own.
- Caching, which changes when a task's code runs, not what its answer means.
- The claim that equal answers give equal outcomes. It compares two runs, which TLC cannot check
  directly, and needs a model that runs two copies side by side.
