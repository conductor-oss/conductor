# Agent Worker Architecture

This document describes the shipped implementation of the `GET_AGENT_CARD`, `AGENT`, and
`CANCEL_AGENT` task types. The implementation is portable: the same annotation-backed worker
methods run inside OSS Conductor or in an external Java SDK worker process.

## 1. Runtime shape

`org.conductoross.conductor.ai.tasks.worker.A2AWorkers` is the only task implementation. It exposes:

```java
@WorkerTask("GET_AGENT_CARD")
AgentCard getAgentCard(A2AAgentCardRequest request)

@WorkerTask(value = "AGENT", leaseExtendEnabled = true)
TaskResult agent(Task task)

@WorkerTask("CANCEL_AGENT")
TaskResult cancelAgent(Task task)
```

There are no parallel `WorkflowSystemTask` implementations for these task types.

The runtime selects one of two registration modes:

- `A2AWorkers` is a Spring component implementing `AnnotatedSystemTaskWorker`, so the annotation
  scanner registers its methods as embedded system tasks in OSS Conductor.
- An external Java SDK runtime instantiates `A2AWorkers` and polls the same task types through the
  public `Task` and `TaskResult` contracts.

Both modes persist durable state in task output and return `IN_PROGRESS` with
`callbackAfterSeconds` while an agent is still running. No worker thread is held between polls.

## 2. Agent runtimes

The `AGENT` and `CANCEL_AGENT` methods dispatch on `agentType`:

- `a2a` or blank: call a remote Agent2Agent endpoint through `A2AService`.
- `conductor`: call the Conductor agent control plane through `AgentClient`.

Remote A2A behavior, including polling, streaming, push callbacks, deterministic message IDs,
deadlines, and poll-failure limits, remains in `A2AWorkers`.

The Conductor branch delegates its state machine to `ConductorAgentDelegate`. The delegate only
knows about `AgentClient`; it never receives or calls `WorkflowExecutor`.

## 3. AgentClient boundary

`org.conductoross.conductor.ai.agent.ConductorAgentClient` mirrors the Java SDK agent-client surface using
Conductor-owned DTOs. This keeps worker code independent of where the agent control plane lives.

Runtime implementations are injected:

- AgentSpan embedded mode supplies `ServiceAgentClient`, which calls `AgentService` directly and
  avoids loopback network calls.
- External workers supply the Java SDK adapter, which calls the remote AgentController API.
- Deployments without the Conductor agent control plane receive `UnavailableAgentClient`; remote
  A2A tasks continue to work, while `agentType: conductor` fails clearly.

## 4. Embedded cancellation

`A2AWorkers` also implements `AnnotatedTaskCancellationHandler`. When an embedded `AGENT` task is
canceled, its cancellation hook propagates cancellation to either the remote A2A task or the
Conductor agent execution.

External worker processes do not receive parent-workflow cancellation callbacks. Workflows that
require explicit remote propagation should use `CANCEL_AGENT`.

## 5. Conductor-agent contract

The `conductor` branch deserializes task input as `ConductorAgentRequest`, which extends the same
`AgentStartRequest` accepted by the agent controller. A fresh call requires `name` and `prompt`.
Supplying `executionId` resumes an existing run and sends the prompt as the response payload.

Task-only durability fields are:

| Field | Default | Purpose |
|---|---:|---|
| `pollIntervalSeconds` | 5 | Delay before the next status poll |
| `maxDurationSeconds` | 86400 | Absolute execution deadline |
| `maxPollFailures` | 30 | Consecutive transient status failures before terminal failure |

The deterministic idempotency key is:

```text
conductor-agent-<workflowInstanceId>:<referenceTaskName>:<iteration>
```

The principal output fields are:

| Key | Meaning |
|---|---|
| `executionId` | Agent execution used for poll, respond, and cancel |
| `agentName` | Executed agent |
| `state` | `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`, or `CANCELED` |
| `waiting` | True when external input is required |
| `pendingTool` | Pending human or tool request |
| `text` | Latest or final text |
| `output` | Structured completed output |
| `agentStartTime` | Agent execution start time and durable deadline anchor |
| `agentEndTime` | Time a terminal agent state was observed |
| `agentPollFailures` | Consecutive transient status failures |

State mapping:

| Agent state | Worker result |
|---|---|
| `RUNNING` | `IN_PROGRESS` with callback delay |
| `WAITING` | `COMPLETED`, with `waiting=true` |
| `COMPLETED` | `COMPLETED` |
| `FAILED` | `FAILED` |
| `CANCELED` | `FAILED_WITH_TERMINAL_ERROR` |

## 6. Source layout

| File | Responsibility |
|---|---|
| `ai/.../tasks/worker/A2AWorkers.java` | Portable task implementation, OSS bean registration, and cancellation hook |
| `ai/.../agent/AgentClient.java` | Portable agent control-plane contract |
| `ai/.../agent/ConductorAgentDelegate.java` | Durable Conductor-agent state machine |
| `agentspan/.../service/ServiceAgentClient.java` | In-process AgentService implementation |
| `ai/.../a2a/A2AService.java` | Remote Agent2Agent transport |
| `agentspan/.../runtime/util/JavaScriptBuilder.java` | Builds dynamic tool tasks and stamps each task definition with `_agent_tool_name` |
| `agentspan/.../runtime/compiler/ToolCompiler.java` | Compiles `FORK_JOIN_DYNAMIC` → `JOIN` → state/tool-result merge chains |
| `core/.../execution/tasks/Join.java` | Aggregates fork outputs and preserves agent tool observations across loop iterations |

The test strategy is documented in [testing.md](./testing.md), with the coverage matrix in
[test-plan.md](./test-plan.md).

## 7. Agent loop JOIN contract

Issue-specific rationale, the implementation sequence, and regression coverage are documented in
[`issue-1499-agent-loop-join/`](./issue-1499-agent-loop-join/architecture.md). This section remains
the source of truth for the shared runtime contract and exact names.

Tool-enabled agents compile each tool turn into this durable sequence:

```text
LLM_CHAT_COMPLETE
  → enrich dynamic tool definitions
  → FORK_JOIN_DYNAMIC
  → JOIN
  → merge state and tool observations
  → SET_VARIABLE(_agent_state, _last_tool_results)
  → next DO_WHILE iteration
```

`JavaScriptBuilder` writes the logical tool name to the generated `WorkflowTask` input parameters
as `_agent_tool_name`. The marker is orchestration metadata, not a provider argument. A mapped
`TaskModel` normally also carries it in `inputData`, but JOIN correctness must not depend on that
resolved copy: task mappers, payload storage, and system-task request models may retain the marker
only on `TaskModel.workflowTask.inputParameters`.

For an agent workflow, `Join.compactAgentOutput(TaskModel)` resolves the marker in this order:

1. `forkedTask.inputData["_agent_tool_name"]`;
2. `forkedTask.workflowTask.inputParameters["_agent_tool_name"]` when the runtime input does not
   contain it.

When a marker is found, JOIN writes one namespaced observation under the forked task's actual
iteration-qualified reference name:

```json
{
  "<forked-task-ref>": {
    "_agent_tool_name": "<logical-tool-name>",
    "_agent_tool_output": { "<complete-tool-output>": "..." }
  }
}
```

The complete tool output is preserved inside `_agent_tool_output`; it is not reduced to the
general agent merge keys. `JavaScriptBuilder.stateMergeScript()` consumes this envelope, appends
`{name, output}` to `workflow.variables._last_tool_results`, and makes the observation available to
the next LLM turn.

Agent fork branches without `_agent_tool_name` retain the existing compact behavior and propagate
only `_state_updates` and `state`. Non-agent workflows retain stock JOIN behavior and copy the full
fork output directly. JOIN completion, failure, optional-task, permissive-task, and DO_WHILE
iteration semantics do not change.

The implementation is limited to `Join.compactAgentOutput(TaskModel)` plus focused tests. It does
not change `FORK_JOIN_DYNAMIC`, `DO_WHILE`, task mapping, payload storage, workflow metadata,
`JavaScriptBuilder.stateMergeScript()`, or any public API.
