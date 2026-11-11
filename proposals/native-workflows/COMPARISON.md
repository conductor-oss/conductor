# Conductor native workflows, Temporal and Restate: how they behave

Internal working document, to become public documentation at launch. It describes behaviour, not
marketing claims. Temporal and Restate facts are from their documentation (docs.temporal.io and
docs.restate.dev, read November 2026); re-check them before publishing, since both change often.
Durability guarantees for native workflows, with how each was verified, are in
[DURABILITY.md](DURABILITY.md).

## The shared idea

All three are durable execution engines. Workflow code advances in steps, the engine persists the
outcome of every step, and a crash of a worker, a service instance or the server loses nothing:
when something comes back, the code resumes from its last recorded step, possibly on a different
machine. None of them runs a workflow on one fixed machine; each step can run anywhere, and they
differ in how a step resumes and in where the work between steps happens.

## Execution model

| | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| Unit of progress | workflow task: the code runs until it is blocked, emitting commands | journal entry; the handler runs while its connection is open | continuation: the code runs until it needs a server result; the task it pauses on carries the continuation marker |
| How a step reaches the code | workers poll a task queue | the server pushes the invocation to a service instance over HTTP/2 (or a FaaS call) | workers poll a task queue (long poll) |
| Resuming on another machine | replay of the event history | replay of the journal after the invocation suspends | replay against the recorded task list |
| Affinity to one machine | sticky execution: the next workflow task goes to the same worker, which keeps the workflow state in memory and skips replay; falls back to any worker after 5 s | the handler stays on one instance until it suspends (after 1 min of inactivity by default) | none by default: continuations go to any worker polling the shared queue; opt-in domains with fallback; no in-memory fast path |
| Concurrency of one workflow's code | single-threaded, deterministic scheduler | single handler; awaitables for concurrency | one continuation per execution at a time, enforced by the server across all hosts; `async` lanes take turns, while their tasks run in parallel |
| Where the server holds state | event history per execution | replicated log and partition store | Conductor's database: definition, task rows with their continuation markers, variables, a small `nativeState` block |

## Where work happens

| | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| Side effects | activities, run by any worker polling the activity task queue | `ctx.run`, inline in the handler's own process | tasks, run by any worker polling the task name, in any language |
| Parallel work | activities and child workflows in parallel | calls to other services in parallel | fork branches in parallel; dynamic fan-out |
| Timers | recorded by the server, fire with no worker involved | recorded by the server | WAIT tasks on the server |
| Loops that poll something | workflow code (each iteration a workflow task) | handler code | can run entirely on the server (`repeatUntil`: DO_WHILE with WAITs), waking the code once |
| HTTP calls, LLM calls, human tasks, events | activities you write | `ctx.run` you write | built-in server tasks, no worker code |
| Definition and inventory | workers register workflow types with the SDK; the docs we checked describe no server-side catalog or graph of a workflow's steps | a registered deployment lists its services and handlers, with input and output schemas, through the admin API; steps inside a handler are not listed | registered definitions: name, description, input parameters, signal, update and query names, and the graph discovered from the code (best effort); rendered and searchable in the UI |
| Reusing work someone else runs | activity or child workflow by name, on its task queue | call another registered service by name | any existing worker by task name, typed or untyped, in any language and any domain (`taskToDomain`); its task definition stays its owner's |
| A branch the operator can see | plain code; the history shows the commands the taken path issued | plain code; the journal shows the entries the taken path wrote | plain `if`/`switch` is code and leaves no trace; `switchOn(name, value)` is the opt-in: a SWITCH task showing every declared case and the one the value took, the taken case's tasks inside it, and the tasks each untaken case would have called (best effort) |

## Determinism and code changes

| | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| Non-deterministic values | SideEffect, deterministic time and random APIs | `ctx.run`, `ctx.rand`, `ctx.date` | `once`, `now`, `random` |
| How replay matches | positional: the command sequence must match history | positional journal | by reference name; independent reorderings and added calls in new code paths are tolerated |
| Divergence | non-determinism error; the workflow task fails and retries | journal mismatch; retry, pause or fail (configurable) | the execution is held: it stays running and the continuation is retried with backoff up to 1 min, naming the call and source line, until a compatible build continues it or an operator terminates it |
| In-flight code changes | patching (`getVersion`), Worker Versioning with pinned workflows | immutable deployments: an invocation finishes on the version that started it | name-matched replay, divergence detection; an opt-in domain list on the start prefers a build (`buildId` on its workers), with fallback to any worker; a new `@Workflow(version)` gets its own queue |
| Replay testing | replayer against exported histories | not documented | `DurableReplay.assertCompatible` against recorded executions |

## Messaging

| | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| Fire-and-forget message | signal | signal (named, multi-resolution), awakeable (one shot) | `@Signal` |
| Request with a result | update, with optional validation | call to a shared handler | `@Update`, no validation yet |
| Read-only query | query; also on completed workflows | shared handler | `@Query`, run by the client on the execution's applied state; also on completed executions; cannot change state |
| Message sent before the receiver is ready | stored in history | stored until received | stored in the workflow message queue |
| External system completes a wait | signal or async activity completion | awakeable or durable promise | `awaitExternal`: complete the WAIT by reference |
| Completing while messages are buffered | refused while signals are buffered | not documented | the completion is deferred while messages are queued or a handler run is pending, and runs again on the current state |

## Failures and retries

| | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| Default retries | activities: unlimited, exponential from 1 s | exponential from 50 ms, 70 attempts, then pause | 3 retries, exponential from 1 s; per task |
| Non-retryable error | non-retryable application failure | terminal error | `NonRetryableException`, task FAILED_WITH_TERMINAL_ERROR |
| What the code sees after retries | `ActivityFailure` | terminal error at the await | `TaskFailure`, with how the task failed |
| Uncaught failure | workflow fails (workflows are not retried by default) | invocation fails, error to the caller | workflow fails, with the task as cause |
| Lost worker detection | workflow task timeout (10 s default), activity start-to-close and heartbeat timeouts | stream loss, inactivity and abort timeouts | task response timeout; continuation lease (30 s default); on the shared queue any other worker takes it; with domains, one queued for a domain nobody has polled for 10 s moves at the next decider pass (up to 30 s) |
| Stale workers | task tokens | epochs | a lease epoch per continuation; a stale completion is refused (409) |
| Cancellation | delivered to the code (cancellation scopes) | delivered to the code at the next await | terminate cancels pending tasks; not delivered to the code |
| Step delivery | activities: at least once | `ctx.run`: at least once | tasks: at least once, the same contract as Conductor tasks; make side effects idempotent |
| Terminal step error | a non-retryable activity error is raised in the workflow code, which decides | a terminal error propagates to the caller | FAILED_WITH_TERMINAL_ERROR: the task is not retried, and the code decides whether the workflow ends |

## Operations

| | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| What an operator sees | event history, timeline | invocation list, journal | the Conductor UI graph: tasks with status, inputs, outputs, source line |
| Recovery actions | reset to an event, terminate, cancel | cancel, kill, pause, resume, restart-as-new, restart from a journal prefix | retry from the failed task, rerun from any task, restart, pause, resume, terminate |
| Idempotent start | workflow id reuse and conflict policies | idempotency keys (24 h retention) | idempotency keys: return existing or fail |
| Schedules | built in | timers in code | Conductor scheduler |
| Size limits | 51,200 events or 50 MB of history | journal grows; retention 24 h by default | no fixed limit on tasks per execution; a native execution's code-appended definition is capped by `maxDefinitionTasks` (configurable, 5000 entries by default), and iterations of `repeatUntil` and `forEach` loops run on the server do not count against it |
| Server availability | Cloud: three zones, synchronous; multi-region async | quorum-replicated log | as available as the database backing Conductor |

## Durability guarantees

The guarantees in [DURABILITY.md](DURABILITY.md), against what Temporal and Restate document.
"Conductor" is what native workflows provide today.

| Guarantee | Temporal | Restate | Conductor native workflows |
|---|---|---|---|
| Survives worker crash, resumes without re-running completed steps | yes, history replay | yes, journal replay | yes, replay against the applied continuations and tasks |
| Survives server crash | yes; Cloud replicates across three zones | yes; quorum-replicated log | yes; as durable as the database (Postgres, MySQL, Redis replication) |
| Lost-worker detection | workflow task timeout, 10 s default | stream loss, inactivity timeout | continuation lease, 30 s default; on the shared queue any other worker takes over, and with domains a queued continuation moves at the next decider pass |
| Stale or zombie workers fenced | task tokens | epochs | a lease epoch per continuation; a stale completion is refused |
| Recorded non-determinism (time, random, side effects) | SideEffect, deterministic APIs | `ctx.run`, `ctx.rand`, `ctx.date` | `once`, `now`, `random` |
| Durable timers, no worker while waiting | yes | yes | yes |
| Messages buffered before the receiver is ready | signals stored in history | signals stored until received | message queue |
| Synchronous update with a result | yes, with validation | via shared handlers | yes, without validation |
| Query a running execution | yes, also completed ones | via shared handlers | yes, on the execution's applied state, also when completed |
| Retries with backoff | activities retry by default, unlimited | 70 attempts by default, then pause | 3 retries by default, then the code decides |
| Non-retryable errors | yes | terminal errors | `NonRetryableException` |
| Idempotent start | workflow id reuse and conflict policies | idempotency keys, 24 h | idempotency keys, return existing or fail |
| Cancellation delivered to code for compensation | yes, cancellation scopes | yes, at the next await | **no**: terminate cancels pending tasks |
| Reset or restart from a point | reset to an event | restart-from-prefix | rerun from a task, retry, restart |
| Code changes in flight | patching, worker versioning (pinned) | immutable deployments | name-matched replay (tolerant), divergence detection that holds the execution until a compatible build runs, opt-in build domains |
| Replay testing in CI | replayer | not documented | `DurableReplay` |
| History limits | 51,200 events or 50 MB | journal grows; retention 24 h | no fixed limit on tasks per execution; a native execution's code-appended definition is capped by `maxDefinitionTasks` (configurable, 5000 entries by default), and iterations of `repeatUntil` and `forEach` loops run on the server do not count against it |
| Visual graph of what ran, with per-task operator actions | event history view | invocation journal | full Conductor UI graph of exactly the code's tasks, pause, rerun, retry, restart |

<!-- TODO: re-check before publishing: the Temporal and Restate cells of "Reusing work someone else runs" are from general knowledge; the Temporal cell of "Definition and inventory" reflects what its docs did not mention; the Restate cells of "Step delivery" and "Completing while messages are buffered" are from general knowledge. -->
