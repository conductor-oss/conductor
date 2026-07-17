# #1321 (duplicate blocking system-task execution) — fix options

Bug: async system tasks that block in `start()` longer than the queue's
redelivery window (Redis 30s, sqlite/postgres/mysql ~60s) are popped again by a
second worker and executed twice. Full sequence:
[chat-turn sequence doc](2026-07-17-agentspan-chat-turn-sequence.md).

## Option A — per-execution lease via executor hook ✅ (implemented)

Task declares how long it may block; executor extends the message's visibility
before invoking it. Per-message precision, works on all 4 queue backends, zero
cost for tasks that don't opt in. Same idiom core already uses for the decider
queue (`WorkflowExecutorOps.java:1371`, `WorkflowSweeper.java:129`).

- `WorkflowSystemTask.getExecutionLease()` — hook, default `Optional.empty()`
- `AsyncSystemTaskExecutor.extendLease()` — one `setUnackTimeout` before `start()`
- `AnnotatedWorkflowSystemTask.getExecutionLease()` — returns `responseTimeoutSeconds`
- `HttpTask.getExecutionLease()` — returns configured timeouts when > 20s

## Option B — fix inside AnnotatedWorkflowSystemTask only (superseded)

The original branch fix: the adapter called `setUnackTimeout` itself in
`start()`. Same behavior for LLM tasks, but queue writes live in a task class
instead of the layer that owns them, and `HttpTask` (same bug, long timeouts)
stays exposed. Refactored into Option A.

- was: `AnnotatedWorkflowSystemTask.extendQueueVisibility()` + `QueueDAO` ctor param

## Option C — unconditional extension in AsyncSystemTaskExecutor (rejected)

The issue's original proposal: extend visibility to `responseTimeoutSeconds`
before every `systemTask.start()`. Covers everything, but changes lease/crash-
recovery behavior for all system tasks on all backends, and most task defs
resolve to `responseTimeoutSeconds=0` anyway (`MetadataMapperService.java:128`).

- `AsyncSystemTaskExecutor.java:171` (the `start()` call site)

## Option D — per-queue visibility window (rejected as primary)

Port Redis's `queueUnackTime` model: each queue gets a configured window applied
at pop. Free on the write side (pop is already an `UPDATE`), but needs a new
`QueueDAO` surface + schema in 3 SQL stores + task→queue registration, and it's
per-queue coarse — wrong for mixed-duration queues like `HTTP`. Fine as a
Redis-only ops tuning on top of A.

- `QueueMonitor.queueUnackTime` (default 30000ms) and `ConductorRedisQueue` are
  NOT in this repo — they ship in the `io.orkes.queues:orkes-conductor-queues`
  jar (`redis-persistence/build.gradle:21`; source: orkes-io/orkes-queues).
  `setQueueUnackTime` is public but nothing in conductor wires it; changing its
  defaults/pop semantics requires an orkes-queues release.
- SQL pop: `SqliteQueueDAO.popMessages`, `PostgresQueueDAO`, `MySQLQueueDAO`

## Option E — Redis-only config + document the bug elsewhere (rejected)

Wire `setQueueUnackTime` for LLM queues and declare SQL backends unsupported.
Still requires new code (no wiring exists), abandons postgres/sqlite users —
including the deployment the bug was reported on — and leaves upstream #1321
unfixed by default.

## Option F — give system tasks the SIMPLE-task recovery model (rejected)

SIMPLE tasks don't have this bug because poll deletes the queue message
(`ExecutionService.java:242` → `:379` → `ack` = remove), persists `IN_PROGRESS`
immediately (`:179`), and recovery is the decider's def-aware response timeout
(`DeciderService.java:928`). Porting that to `AsyncSystemTaskExecutor`
(ack-at-pop before `start()`) sounds like unification but unravels: the kept
message is also every long-lived system task's re-evaluation timer and
rate-limit deferral (postpone at `AsyncSystemTaskExecutor.java:195`, `:224`),
decider recovery means timeout→retry which is unsafe for side-effectful system
tasks (the reason `MetadataMapperService.java:125-131` zeroes their timeouts),
and paused-then-resumed attempts re-open #1322, requiring the terminal-update
guard that broke legitimate flows. Core surgery for a problem two task types
have.

Long-term variant worth keeping in mind: run annotated workers as **in-process
SIMPLE workers** (SDK worker model polling the task API) — they'd inherit
ack-at-poll + def-timeout recovery with zero core changes, at the cost of poll
latency, worker pools, and a rewrite of the annotated-worker feature.

## Follow-up (needed regardless): make the lease value reliable

Option A reads `responseTimeoutSeconds` from the embedded task def. Single-agent
LLM tasks get 3600s only via `TaskDef`'s field default; multi-agent LLM tasks
get an ad-hoc def with `0` → **no lease**. Fix: set it explicitly in the
compilers (or register a `TaskDef` per `@WorkerTask` at scanner startup).

- `AgentCompiler.java:1545` (`llmRetryDef` — never sets responseTimeout)
- `MultiAgentCompiler.java:388`, `:1000`, `:1310` (no embedded def at all)
- `TaskDef.java:96` (the accidental 3600 default)
- alt: `WorkerTaskAnnotationScanner` + `MetadataDAO.registerTaskDef`
