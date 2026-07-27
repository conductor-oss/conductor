# Issue #1322 — First-terminal-write-wins for `ExecutionDAO.updateTask`

Status: WIP. Stacked on #1369 (`fix/system-task-poll-reserve`).

## Problem

A system task can be executed twice — e.g. queue redelivery while a long-running task is still
in flight, or the timed-out-overrun path introduced by #1369. Both attempts load the task while
it is non-terminal, both run, and both persist their result through
`AsyncSystemTaskExecutor`'s `finally { executionDAOFacade.updateTask(task); }`.

Every `ExecutionDAO.updateTask` implementation is last-write-wins, so the second ("zombie")
completion overwrites the `output`, `endTime`, and `finishReason` of an already-terminal task —
values the workflow (and any downstream task) already consumed. For AI/agent loops that rebuild
LLM message history from prior task records, this corrupts the reconstructed conversation
(spurious assistant turns, missing tool exchanges, timestamps after workflow completion).

Root cause: `updateTask` has no terminal-state protection, and its contract documents no
concurrency semantics.

## Design

### Contract change

`ExecutionDAO.updateTask` is now specified as **first-terminal-write-wins**: once the stored task
for a `taskId` is terminal (`TaskModel.Status.isTerminal()`), a later `updateTask` for that
`taskId` MUST be rejected atomically (dropped, logged). Non-terminal writes and the first
terminal write always apply.

The engine legitimately re-writes already-terminal tasks on the same `taskId` in several trusted
state-machine paths (rerun/retry reopen, the `decide()` re-persist of terminal tasks, sub-workflow
sync, reconciliation). Those must not be blocked, so a sibling method is added:

```java
default void forceUpdateTask(TaskModel task) { updateTask(task); }
```

`forceUpdateTask` bypasses the guard. It is a `default` on the interface, so only the Redis
backend overrides it in this change; all other backends keep today's behaviour. `updateTask`
stays `void` — rejections are logged, not signalled to callers.

### Redis enforcement (atomic, no TOCTOU)

A naive `getTask()`-then-`set()` check would race. Instead the Redis backend uses a Lua script
executed via `EVALSHA`, keyed on the task payload key plus a companion **terminal-marker** key:

```
marker = "{" + nsKey(TASK, taskId) + "}.TERM"
```

The `{...}` hash tag makes the marker hash to the **same cluster slot** as the task key (the tag
content is the full task key), so the two-key script is safe on Redis Cluster without changing the
existing task-key format.

Script (`KEYS=[taskKey, markerKey]`, `ARGV=[payload, isTerminal, bypass]`):

```lua
if ARGV[3] == '0' and redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end
redis.call('SET', KEYS[1], ARGV[1])
if ARGV[2] == '1' then
    redis.call('SET', KEYS[2], '1')
elseif ARGV[3] == '1' then
    redis.call('DEL', KEYS[2])
end
return 1
```

- Guarded write (`bypass=0`): if the marker exists → reject (return 0). Otherwise write the
  payload; if the incoming status is terminal, set the marker.
- Bypass write (`bypass=1`): always write the payload; set the marker when terminal, **delete**
  it when non-terminal. The delete-on-reopen is what lets rerun/retry reopen a terminal task and
  have its later re-completion accepted by the guarded path.

The marker has no TTL; it is deleted/expired alongside the task key in `removeTask` and
`removeTaskWithExpiry` (the only two sites that touch the task key). The marker key is never read
as a task (`getTask` reads the task key directly).

Fallbacks: the in-memory Jedis backend used in single-process dev/tests makes scripting a no-op
(`scriptLoad` returns empty) — detected and downgraded to a plain `set` (the guard is inert there;
real deployments run the Lua). A `NOSCRIPT` reply (e.g. a freshly restarted node) triggers a
one-shot reload-and-retry.

### Call-site classification

Only duplicate-prone completion entry points stay on the guarded `updateTask`
(`AsyncSystemTaskExecutor`, worker completion — the latter also already self-guards in memory).
Every path that deliberately re-writes a possibly-terminal task on the same `taskId` is switched
to `forceUpdateTask`/`forceUpdateTasks`:

- `WorkflowExecutorOps`: `updateAndPushParents`, `resetUnsuccessfulJoinTasks`, `retry(...)`,
  the `decide()` re-persist loop, `adjustStateIfSubWorkflowChanged`, `terminate`, `rerunWF`,
  `finalizeRerun`, `updateParentWorkflowTask`.
- Reconciliation: `WorkflowSweeper.repairSubWorkflowTask`, `WorkflowRepairService.repairSubWorkflowTask`.

The `decide()` re-persist fires on nearly every workflow (it flips `executed`/`retried` flags and
`FAILED → COMPLETED_WITH_ERRORS` on terminal tasks); leaving it on the guarded path would drop
those writes, which is why it uses the bypass.

## Marker lifecycle

1. Create (IN_PROGRESS): payload written, no marker.
2. First completion (terminal): marker absent → write + set marker.
3. `decide()` re-persist (bypass, terminal): marker stays set (idempotent).
4. **Zombie duplicate (guarded, terminal): marker present → rejected + logged. Bug fixed.**
5. rerun/retry reopen (bypass, non-terminal): marker deleted → reopened.
6. Re-completion after reopen (guarded, terminal): marker absent → accepted.
7. `removeTask`/`removeWorkflow`: marker deleted/expired with the task.

## Backend rollout

Only Redis enforces the guard in this change. Postgres/MySQL/SQLite/Cassandra inherit the
unguarded `default forceUpdateTask == updateTask` and keep last-write-wins for now. Follow-ups:

- SQL: an atomic conditional upsert, e.g. `... ON CONFLICT (task_id) DO UPDATE ... WHERE
  <table>.status NOT IN (<terminal statuses>)`.
- Cassandra: a lightweight transaction (`UPDATE ... IF status IN (<non-terminal>)`), or a
  documented limitation if LWT cost is unacceptable.

## Alternatives considered

- **Block only non-terminal-over-terminal / monotonic-timestamp guard:** does not stop a duplicate
  *terminal* completion overwriting output — i.e. does not fix #1322.
- **Reject all writes to terminal tasks (no bypass):** breaks rerun/retry and the `decide()`
  re-persist, which legitimately re-write terminal tasks on the same `taskId`.
- **Java `getTask()`-then-`set()` check:** a TOCTOU race under concurrent duplicates; the whole
  point is to make the check-and-set atomic.

## Accepted limitation

Because `updateTask` stays `void`, a rejected write still lets the facade re-index the stale task
summary when synchronous indexing is enabled. The authoritative version is re-indexed by the
immediately following `decide()`, and the production default (async indexing) only indexes at
workflow-terminal, so this is cosmetic. Fixing it would require a non-`void` contract, which is
out of scope here.

## Testing

`RedisExecutionDAOTest` (real Redis via Testcontainers — the guard cannot be exercised on the
in-memory backend, where scripting is a no-op):

- `testFirstTerminalWriteWins` — a second terminal write does not overwrite the first; marker set.
- `testNonTerminalWritesNotBlocked` — repeated non-terminal writes all apply; no marker.
- `testForceUpdateOverwritesTerminalThenReopenClearsMarker` — bypass overwrites a terminal task;
  reopening to a non-terminal status clears the marker; re-completion is then accepted.
- `testRemoveTaskClearsMarker` — `removeTask` deletes the marker.
- `testConcurrentTerminalWrites` — concurrent terminal writes yield one intact value and one marker.

Regression: `TestWorkflowExecutor`, `TestDeciderService`, `AsyncSystemTaskExecutorTest`,
`WorkflowServiceTest`, `WorkflowBulkServiceTest` (rerun/retry/decide paths now use the bypass).
