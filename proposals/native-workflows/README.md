# Native workflows: server design

Status: implemented on this branch, protocol 2, with the Java SDK on `conductor-oss/java-sdk` branch `design/native-workflows`. The Java kitchensink runs end to end against this server. See [06-implementation.md](06-implementation.md) for how to run it, what was validated, and where the implementation differs from the design; [07-engine-state.md](07-engine-state.md) is the plan protocol 2 was built from. Branch `design/native-workflows`, based on `main`. Lives under `proposals/` because `/design` is git-ignored as local notes in this repository, and `docs/` is the published site.

Native workflows are workflows written as ordinary code in an SDK (Java first; the proposal lives
in the Java SDK repository under `design/native-workflows/`). The server does not run the code.
It runs a normal workflow definition that the code's worker builds up as the code executes. The
code only ever pauses on a task; that task carries a **continuation marker** saying which lane of
the code waits on it and which continuation runs next. When the task finishes, the engine hands
the continuation to a worker through the ordinary task poll, and the ordinary task update that
completes it carries the next piece of the definition. Forks, joins, loops, waits, HTTP, human,
event, sub-workflow and LLM tasks are ordinary server tasks and render as they do today. An
execution holds exactly the tasks the code called: a hello world that calls two tasks has two
tasks.

The one sentence, from the server's side: **a native workflow is a definition that grows after the
task each lane last paused on, one continuation at a time.**

## Documents

| File | Contents |
|---|---|
| [01-execution-model.md](01-execution-model.md) | What the server guarantees: lanes, continuation markers, continuation routing and failover, variables, handlers, completion, failure. Read this first. |
| [02-protocol.md](02-protocol.md) | The contract between SDK and server: skeleton definition, the poll response, the completion output, fragment rules, markers on entries, handler lane, how each SDK call lowers to server tasks, endpoints used. |
| [03-server-changes.md](03-server-changes.md) | Each change in detail, with the code it touches, the algorithm, edge cases, configuration and metrics. |
| [04-test-plan.md](04-test-plan.md) | Unit tests, integration specs with the harness playing the SDK, failure injection, performance checks. |
| [05-plan.md](05-plan.md) | How the work was staged, rollout, risks, decisions, acceptance criteria. |
| [DURABILITY.md](DURABILITY.md) | The durability guarantees: what survives which failure, how, how it was verified, what must be configured, and what is not guaranteed. |
| [COMPARISON.md](COMPARISON.md) | How native workflows compare with other durable execution engines: execution model, where work happens, determinism, messaging, failures, operations. Internal draft for launch documentation. |
| [06-implementation.md](06-implementation.md) | Implementation status: how to run the kitchensink, validation results, server bugs found, differences from the design. |
| [07-engine-state.md](07-engine-state.md) | The protocol 2 plan, now implemented: a continuation is a marker on the task the code paused on, recorded values live in that marker, the first continuation and the handler lane are engine state on the workflow, so an execution holds exactly the tasks the code asked for. Notes where the implementation drifted from the plan. |

## Scope

In scope: everything the server needs so that the Java kitchensink example in the SDK proposal runs
as one flat execution with independent fork branches, visible in the UI as the JSON kitchensink is,
with no engine-only tasks in the execution.

Out of scope, each with its own round: request/response latency (a push transport for
continuations) and very large loops (incremental decide, bounded definition growth). Nothing here
blocks either.

## Glossary

| Term | Meaning |
|---|---|
| lane | One thread of workflow code: the `@Workflow` method (`main`), each `async` block (`fork_<k>.<i>`), and the handler lane (`handlers`). |
| continuation | One run of a lane's code between two pauses. Numbered `n` per lane, starting at 1. |
| continuation marker | The `continuation` field on the task a lane paused on (`TaskContinuation`): lane, `n`, state `WAITING` → `RUNNABLE` → `LEASED` → `APPLIED`, lease epoch, and once applied the record replay reads (recorded values, `resumedWith`, `done`, `result`, `error`). |
| bootstrap | The main lane's first continuation. It runs before any task exists, so its marker lives on the workflow (`nativeState.bootstrap`). |
| handler lane | Signal and update handlers. They schedule no tasks, so their marker lives on the workflow (`nativeState.handlers`), one run per batch of messages. |
| fragment | The list of `WorkflowTask` entries a lane scheduled during one continuation. Carried in the completion and inserted right after its anchor. |
| anchor | Where a fragment is inserted: the task the lane paused on, or the entry named by the fragment's first entry's `metadata.after` (`NativeContinuationService.runs`, `insertAfter` at any depth). The SDK names the switch when the lane resumed inside a `switchOn` case and the body returned. An anchor that names no entry is a rejected fragment. |
| switch (`switchOn`) | The SDK call that emits a real `SWITCH` task: `evaluatorType: native-code` on `switchCaseValue`, every declared case as a branch, a `defaultCase`, the taken case's body run inline with its calls inside that branch, and a preview of every untaken case. Plain Java `if`/`switch` stays invisible to the server; `switchOn` is the opt-in for a branch the execution should show. |
| preview entry | An entry in an untaken switch case showing a call that case would have made: `metadata.preview: true`, reference `<base>_if_<caseKey>`, no continuation marker, no `optional`. Produced by the SDK by reading the compiled case body, without running it. The server validates it like any entry and never schedules it. |
| code type | `<workflow>.v<version>.code`: the task type workers poll for continuations. Its queue is the continuation queue, shared by every worker of the code by default; a domain prefix applies only when the start's `taskToDomain` asks for one. |
| active continuation | The one continuation of an execution leased right now (`nativeState.activeContinuation`, until `activeUntil`); others wait for it. |
| replay | What a worker does for every continuation: run the method from the top against the applied markers and the tasks, then run the active continuation. |
| skeleton | The registered definition of a native workflow: `metadata.native` with the protocol, code type, continuation timeout, whether the class has handlers and their names, and the discovered task list below. Originally it had no tasks; the test harness still registers it that way. |
| discovered definition | The task list the SDK registers (`Replayer.discover`): read from the compiled workflow method without running it (`CodeReader`), every call an entry under the reference name an execution would allocate, switches with all cases, forks, loops; `metadata.native.discovered` says whether it succeeded. It is the definition view and the inventory, not what runs: `NativeContinuationService.initialize` clears it from each execution's copy, which then grows from the code. |

## Conventions in these documents

Code references are paths in this repository on this branch; method names are given rather than
line numbers. Sizes are engineer-days for one person including tests. Nothing here changes a
database schema; the additions are two JSON fields (`continuation` on tasks, `nativeState` on
workflows), new task-output keys, and new properties.
