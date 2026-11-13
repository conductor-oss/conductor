# Decider bugs

Server bugs found while checking `Workflow.tla` against the server. All are in standard (non-native)
workflows. All are fixed on this branch. The expected behaviour is what the
spec states; see `README.md`.

Bugs 1 to 8 were observed on a running server by `server_check.py`, except bug 1's example, which was
run by hand. Bugs 9 to 13 were found by reading the code; each fix note says how it was checked.

Line numbers are from the `design/native-workflows` working tree on 2026-11-10. Execution ids are
from a local development server (SQLite, port 8099) and will not exist elsewhere.

| # | Bug | Evidence | Priority | Status |
|---|---|---|---|---|
| 1 | An optional task that fails last keeps `FAILED` | observed, frequent | high | fixed |
| 2 | A JOIN can fail the workflow while a branch task still has a retry | observed twice | high | fixed |
| 3 | A rerun from a task in a fork loses what other branches scheduled after it | observed, repeated | high | fixed |
| 4 | A rerun from a `SUB_WORKFLOW` task does not run the child again | observed, repeated | high | fixed |
| 5 | A rerun from a task inside a loop can hang | observed, repeated | high | fixed |
| 6 | A retry after a failing `TERMINATE` ends the workflow `TERMINATED` | observed, repeated | medium | fixed |
| 7 | A workflow can end with a task stuck `IN_PROGRESS` | observed once | medium | fixed |
| 8 | A rerun from an older attempt leaves a JOIN waiting forever | observed once | low | fixed |
| 9 | A JOIN naming a SWITCH finishes before the case does | code only | medium | fixed |
| 10 | An operator can terminate a workflow that already ended | reproduced | medium | fixed |
| 11 | A `COMPLETED` workflow can be retried | code only | low | fixed |
| 12 | The total time budget survives a retry or rerun | code only | low | fixed |
| 13 | Skipping can leave a workflow waiting | code only | medium | fixed |

## Observed on a running server

### 1. An optional task that fails last keeps `FAILED`

- **Expected:** after its retries, an optional task ends `COMPLETED_WITH_ERRORS`.
- **Actual:** when it is the last task, it stays `FAILED` or `FAILED_WITH_TERMINAL_ERROR` with
  `executed: false`, and the workflow ends `COMPLETED`. The same task earlier in the workflow is
  saved correctly.
- **Cause:** `DeciderService.decide` sets the status and adds the task to `tasksToBeUpdated`.
  When the outcome is also complete, `WorkflowExecutorOps.decide` (line 1652) calls `endExecution`
  and returns before `executionDAOFacade.updateTasks`.
- **Reproduce:** a workflow of two optional tasks with `retryCount: 0`, where the worker fails both.
- **Example:** `f172d71b-7646-48ff-ab0f-27e021dd7750`.
- **Fix:** `WorkflowExecutorOps.decide` saves `outcome.tasksToBeUpdated` before it ends the
  workflow. Checked by `TestWorkflowExecutor.testAnOptionalTaskThatFailsLastIsSavedAsCompletedWithErrors`
  (fails without the fix) and live: both tasks end `COMPLETED_WITH_ERRORS`.

### 2. A JOIN can fail the workflow while a branch task still has a retry

- **Expected:** a task with retries left is retried, and the workflow goes on.
- **Actual:** the JOIN fails citing the first failed attempt, the workflow ends `FAILED`, and the
  retry already scheduled is cancelled.
- **Cause (inferred):** `Join.execute` runs on a copy of the workflow loaded after the attempt failed
  and before the decider added the retry. `getTaskByRefName` returns the latest attempt, which in
  that copy is the failed one.
- **Reproduce:** a fork with a task that has `retryCount: 1` and fails once. It is a race.
- **Examples:** `87662ade-8966-4969-97d3-3220948ab822`, `0488fa0d-bffe-43ca-8623-3e5181660d6f`.
- **Fix:** `Join.execute` treats a failed attempt as over only once the decider has looked at it
  (`executed` or `retried` is set). Until then the JOIN waits. A failure with no retries left still
  ends the workflow, through the decider, as before. Checked by
  `TestJoin.testJoinWaitsForAFailedAttemptTheDeciderHasNotLookedAt` (fails without the fix). The
  race itself was not reproduced on demand.

### 3. A rerun from a task in a fork loses what other branches scheduled after it

- **Expected:** what is forgotten is started again when the flow reaches it.
- **Actual:** `rerunWF` (line 2664) removes every task with a later `seq`, and nothing creates them
  again. In a static fork the JOIN waits forever and the workflow stays `RUNNING`. In a dynamic fork
  the workflow completes without the later copies, or never settles.
- **Reproduce:** rerun from the first branch's task in a fork of two branches, or from the first copy
  of a dynamic fork.
- **Examples:** static `691b15de-9807-436a-aabc-2bd414e1cce1`,
  `8aff0c35-94eb-4975-8170-78ddaa7f8396`; dynamic `2e701a34-8226-4643-a4db-bd9ecae7248b`,
  `958ce348-24b0-42fb-9b89-7e8d7898bac4`.
- **Fix:** `WorkflowExecutorOps.scheduleForgotten`, called by `rerunWF` after the removal. Each
  finished task the rerun kept gets back what it had started and the rerun removed: the next task
  in its list, a static fork's branches, a dynamic fork's copies and JOIN (mapped against the tasks
  scheduled before the fork), a switch's recorded case. A finished JOIN waiting for a task that is
  gone or running again goes back to `IN_PROGRESS`. What is removed is unchanged: everything
  scheduled after the task, as the spec says. Native executions keep their own rerun plan. Checked
  by `TestWorkflowExecutor.testRerunFromABranchSchedulesAgainWhatTheOtherBranchScheduledAfterIt`
  (fails without the fix) and live: reruns from every task of a static fork of two branches of two
  tasks, and from every copy of a dynamic fork of three, all complete.

### 4. A rerun from a `SUB_WORKFLOW` task does not run the child again

- **Expected:** the rerun starts a new child, and what came after the task is forgotten.
- **Actual:** the task completes almost at once with the old child's result, and later tasks are
  kept.
- **Cause:** `SubWorkflow.start` (line 173) derives the child id from the parent, the task and the
  retry count, and `startWorkflowIdempotent` returns the child that already has that id. A rerun
  keeps the task id and the retry count. The path through `rerunWF` for a direct `SUB_WORKFLOW`
  target also skips the removal of later tasks and resets the failed ones through `finalizeRerun`.
- **Examples:** `5017e32c-2cd3-4d27-84f1-891e0202cbb4` (inside a loop),
  `12946a01-5151-4d59-98bd-017fbaf5804d` (the child finished in 1 ms).
- **Fix:** a direct rerun from a `SUB_WORKFLOW` task now takes the common rerun path, so later
  tasks are removed (and scheduled again by bug 3's fix), the enclosing loop reopens, and the task
  starts a child at once. `SubWorkflow.start` no longer reuses a finished child created before the
  task was last scheduled; it starts one with a new id. A redelivered start of the same run still
  attaches to the child it started. Checked by
  `TestSubWorkflow.testStartAfterARerunRunsANewChildInsteadOfReusingTheFinishedOne` (fails without
  the fix) and live, in sequence and in a fork: a new child runs and the task after it runs again.
- **Left:** the rerun starts the child before it saves the parent task. A child that finishes
  within milliseconds can have its completion overwritten, and the parent then notices only at
  the next sweep (30 s on the test server). The old path did the same. The final state is
  correct.

### 5. A rerun from a task inside a loop can hang

- **Expected:** the loop continues and what follows it runs.
- **Actual:** the workflow stays `RUNNING` with nothing running. The completed `DO_WHILE` is kept, so
  `getNextTask` (`DeciderService`, line 572) finds the existing loop and returns nothing, and the
  tasks after the loop have been removed.
- **Examples:** `4c039094-f031-4c92-96c4-316ea555e858`, `18014913-338d-473d-a09d-8878c95844db`.
- **When the loop is the last task:** with nothing after the loop to lose, the workflow instead
  ends `COMPLETED` without running the remaining iterations. Examples
  `22fd3b66-0fc1-4356-80d1-598b38513829`, `06dc6c32-71ef-44b6-93cf-e3945f09177e` (rerun from
  the first iteration's task of a two-iteration loop).
- **Fix:** `WorkflowExecutorOps.reopenLoopHolding`, called by `rerunWF` after it removes the later
  tasks: the innermost `DO_WHILE` holding the rerun task goes back to `IN_PROGRESS` at the task's
  iteration, with its end time, `executed` flag and the outputs of that iteration and later ones
  cleared. The loop then waits for the iteration, decides by its condition whether to go on, and
  once it completes the decider schedules what follows it. Checked by
  `TestWorkflowExecutor.testRerunFromATaskInsideACompletedLoopReopensTheLoop` (fails without the
  fix) and, live, by a rerun from every task of a three-iteration loop, with and without a task
  after it (13 reruns, all complete with three iterations).
- **Not covered:** nested loops. On this server a `DO_WHILE` inside a `DO_WHILE` does not finish
  even without a rerun (the inner loop's second copy stays `IN_PROGRESS` with nothing scheduled),
  so reruns in nested loops were not checked.

### 6. A retry after a failing `TERMINATE` ends the workflow `TERMINATED`

- **Expected:** the `TERMINATE` that ran ends the workflow again, as `FAILED`.
- **Actual:** it ends `TERMINATED`.
- **Cause (inferred):** in `DeciderService.decide`, a cancelled `JOIN` reaches `retry`, which throws
  with `TERMINATED` before the check for a successful `TERMINATE` at the end of `decide` is reached.
- **Reproduce:** a fork of a task and a `TERMINATE` with status `FAILED`, then retry the workflow.
- **Examples:** `126c539b-90bb-4da5-939b-cbaf3ca1f2a9`, `d683d851-57f5-489a-95bd-4bbc57c70a8f`.
- **Fix:** `DeciderService.decide` looks for a `TERMINATE` that ran before it judges anything else;
  when there is one, the workflow ends as it says. Checked by
  `TestWorkflowExecutor.testATerminateThatRanEndsTheWorkflowAsItSaysAfterARetry` (fails without the
  fix) and live: three retries of a fork of a task and a failing `TERMINATE` all end `FAILED`.

### 7. A workflow can end with a task stuck `IN_PROGRESS`

- **Expected:** ending a workflow cancels every running task.
- **Actual:** a workflow ended `FAILED` with a task `IN_PROGRESS` for good. The task's update time is
  after the workflow's end.
- **Cause (inferred):** `ExecutionService.poll` checks the task (line 165) and later writes
  `IN_PROGRESS` (line 206). A cancellation between the two is overwritten, and the worker's update is
  then refused because the workflow has ended.
- **Example:** `7e2183af-e74b-46f9-9ea1-809098030aa3`. A second run,
  `38bb98de-01c3-4467-ac89-07c134e62068`, fits this too but could not be confirmed.
- **Fix:** the poll and a worker's task update save the task only if its stored status is still
  the one they read (`ExecutionDAO.updateTaskIfStatus`), so neither can overwrite a cancellation
  or a timeout saved meanwhile. A refused poll does not hand the task out. Postgres, MySQL and
  SQLite check and write in one `UPDATE`; Redis in a Lua script. Cassandra and the in-memory Redis
  use the interface's default, which reads, then writes, and is not atomic. A native task update
  no longer takes the workflow lock; a native continuation's completion still does, since it
  also changes the workflow row. Checked by
  `ExecutionServiceTest.testPollDoesNotHandOutATaskThatChangedSinceItWasRead` (fails without the
  fix) and by `testUpdateTaskIfStatusNeverOverwritesAChangedStatus` against SQLite, Postgres,
  MySQL and Redis.

### 8. A rerun from an older attempt leaves a JOIN waiting forever

- **Actual:** a rerun from a superseded attempt of a retried task, inside a fork, leaves the `JOIN`
  `IN_PROGRESS` with every task finished, and the workflow `RUNNING`.
- **Note:** the spec has no older attempts, so the expected behaviour is undecided: either refuse
  such a rerun, or treat it as a rerun from the latest attempt.
- **Example:** `a9f775e5-d69f-4841-82b2-ab291a585ae8`.
- **Fix:** a rerun from an attempt a retry replaced reruns from the task's latest attempt; the
  replaced attempt stays as it was. Checked by
  `TestWorkflowExecutor.testRerunFromAnAttemptARetryReplacedRerunsFromTheLatestAttempt` (fails
  without the fix) and live.

## From reading the code, not reproduced
<!-- TODO: verify against live server -->

### 9. A JOIN naming a SWITCH finishes before the case does

`Switch.execute` marks the SWITCH `COMPLETED` as soon as it picks a case, and `Join.execute` waits only
for the tasks `joinOn` names. So a JOIN naming a SWITCH completes while the case's tasks are still
running. Final states are the same, which is why the server tests cannot see it.

**Fix:** `Join.execute` waits, for a `SWITCH` or `DECISION` it names, for the last task of the case
the switch recorded (its `case` input), following a switch that ends that case too, with the
iteration when it runs in a loop. An empty case leaves the switch itself, as before. The JOIN's
output under the switch's name is still the switch's own. Checked by three tests in `TestJoin`
(a case, a nested switch, a loop), each failing without the fix, and live: in a fork whose branch
ends in a switch with a slow case, the JOIN stays `IN_PROGRESS` and the task after it unscheduled
until the case completes.

### 10. An operator can terminate a workflow that already ended

`terminateWorkflow(workflowId, reason)` (line 814) refuses only `COMPLETED` and `TERMINATED`, so a
`FAILED` or `TIMED_OUT` workflow becomes `TERMINATED`. Likewise, `SubWorkflow.terminateSubWorkflow` sets
a child to `TERMINATED` without checking whether it has already ended.

**Reproduced** on the server: a workflow whose task failed ("card declined") was terminated with
reason "cleanup" (HTTP 200), and ended `TERMINATED` with its reason replaced by "cleanup".

**Fix:** `terminateWorkflow(workflowId, reason)` refuses a `FAILED` or `TIMED_OUT` workflow with 409
Conflict, as it did a `COMPLETED` one; a `TERMINATED` one is still a no-op. `terminateSubWorkflow`
leaves a child that has already ended. Terminate-and-remove, single and bulk, removes a workflow
that has already ended without terminating it. Checked by
`TestWorkflowExecutor.testAWorkflowThatAlreadyEndedCannotBeTerminated` and
`TestSubWorkflow.testCancelLeavesAChildThatAlreadyEnded`, both failing before the fix, by
`WorkflowServiceTest.testTerminateRemoveRemovesAWorkflowThatAlreadyEnded`, and live: the same
terminate is refused with 409 and the workflow keeps `FAILED` and its reason; a running workflow is
still terminated, and terminate-remove still removes an ended one. The sub-workflow race was not
reproduced live.

### 11. A `COMPLETED` workflow can be retried

`retry` (line 308) requires only a terminal status. A workflow a `TERMINATE` completed, with cancelled
tasks left, goes back to `RUNNING` and schedules them again before its `TERMINATE` ends it once more.

**Fix:** `retry` refuses a `COMPLETED` workflow with 409 Conflict, naming rerun and restart as the
way to run it again, and ui-next no longer offers "Retry - from failed task" on one. Checked by
`TestWorkflowExecutor.testACompletedWorkflowCannotBeRetried` (fails without the fix) and live: a
fork whose `TERMINATE` completed the workflow and cancelled the other branch's task is refused,
and nothing is scheduled.

### 12. The total time budget survives a retry or rerun

`firstScheduledTime` is copied by `taskToBeRescheduled` (line 602) and kept by a rerun, so a task whose
budget was spent times out again at once. The spec gives a reopened task a fresh budget.

**Fix:** the task a rerun starts from, the failed tasks a rerun reopens, and each attempt an
operator's retry creates get a fresh `firstScheduledTime`. Automatic retries keep the budget across
attempts, as before. Checked by
`TestWorkflowExecutor.testAnOperatorRetryGivesTheTaskAFreshTotalTimeBudget` and, for reruns,
`testRerunFromABranchSchedulesAgainWhatTheOtherBranchScheduledAfterIt`, and live: after a task
spent its 2 s budget and failed, both a retry and a rerun let it complete.

### 13. Skipping can leave a workflow waiting

Only the sequential path through `getNextTask` passes over a skipped task (`isTaskSkipped`, line 1107).
Skipping the first task of a switch case or fork branch stops that case or branch. Skipping a
`FORK_JOIN` schedules its `JOIN` alone, waiting for tasks that never run.

**Fix:** `DeciderService.getTasksToBeScheduled`, which every path that schedules a task goes
through, passes over a skipped task to the one after it, as the spec says: a skipped task counts as
finished when reached. Skipping a `FORK_JOIN` or `FORK_JOIN_DYNAMIC` also skips its `JOIN`, so the
flow goes on after the `JOIN`. Checked by three tests in `TestWorkflowExecutor` (a branch's first
task, a case's first task, a fork), each failing without the fix, and live: a workflow with a
branch head, a case head and a fork skipped completes, with the skipped fork's branches never
run.

## Observed by the step-by-step check

### 14. A retry after a failure at a fork can fail the workflow again at once

- **Actual:** a switch leads to a dynamic fork whose only copy fails with no retries left; the
  workflow fails and its `JOIN` is cancelled. A retry gives the copy a second attempt and reopens the
  `JOIN`; 15 ms later the `JOIN` fails with the first attempt's reason and ends the workflow
  `FAILED` again, cancelling the second attempt, which was to complete.
- **Cause:** `retry` set the cancelled `JOIN` to `IN_PROGRESS` and put it on the system-task queue
  while still walking the tasks, before it created the new attempt, marked the old one retried and
  saved them. The async executor ran the `JOIN` on the stored workflow, where the failed attempt was
  settled and not yet retried. On a rerun, `resetUnsuccessfulJoinTasks` and `scheduleForgotten` also
  queued a `JOIN` before saving it, or before the rerun task's reset was saved.
- **Example:** `f17be104-d6b4-45d1-a260-25b664f80344` (its decide trace shows the `JOIN` failing
  after the retry).
- **Fix:** `retry` queues the `JOIN`s and loops it reopens after it saved the retry;
  `resetUnsuccessfulJoinTasks` saves before it queues; `scheduleForgotten` returns the `JOIN`s it
  reopened, and the rerun saves and queues them after the rerun task. Checked by
  `TestWorkflowExecutor.testRetryQueuesAReopenedJoinOnlyAfterSavingTheRetry` and by the
  step-by-step check: the same test matches on the fixed server.

## How to check a fix

```
TLA2TOOLS=/path/to/tla2tools.jar python3 server_check.py --server http://localhost:8099/api --tests 200 --size 3
TLA2TOOLS=/path/to/tla2tools.jar python3 server_check.py --server http://localhost:8099/api --tests 200 --size 4
```

Each test that does not match prints its execution id and final state. With `--trace-dir` (a
server started with `-Dconductor.trace.dir`) it checks every step of each run, not only its end. It
uses no timeouts, pause, skip or operator terminate, so bugs 9 to 13 need their own tests.
