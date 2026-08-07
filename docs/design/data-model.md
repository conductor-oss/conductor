# Agent Worker Data Model

The portable agent workers use the public Conductor `Task` and `TaskResult` types. The task output
is the durable checkpoint shared by embedded execution and remote SDK polling.

## 1. AgentClient

`AgentClient` is the control-plane interface used by the `conductor` branch. Its methods cover
compile, deploy, start, status, execution search, respond, stop, signal, cancel, and SSE streaming.
The worker depends on this interface rather than on an HTTP controller or core
`WorkflowExecutor`.

## 2. ConductorAgentRequest

`ConductorAgentRequest` extends `AgentStartRequest`. It therefore supports the controller's start
fields, including:

| Field | Type | Purpose |
|---|---|---|
| `name` | `String` | Previously deployed agent name |
| `version` | `Integer` | Optional deployed version |
| `prompt` | `String` | User input for start or resume |
| `model` | `String` | Per-call model override |
| `sessionId` | `String` | Session association |
| `media` | `List<String>` | Media inputs |
| `context` | `Map<String,Object>` | Additional execution context |
| `idempotencyKey` | `String` | Optional caller-supplied key |
| `framework` / `rawConfig` | framework-specific values | Inline foreign-agent construction |
| `runId` | `String` | Per-execution worker-domain isolation |

The worker adds:

| Field | Type | Purpose |
|---|---|---|
| `executionId` | `String` | Resume an in-flight run instead of starting a new one |
| `pollIntervalSeconds` | `Integer` | Poll delay; default 5 |
| `maxDurationSeconds` | `Integer` | Absolute deadline; default 86400 |
| `maxPollFailures` | `Integer` | Consecutive transient-failure cap; default 30 |

## 3. ConductorAgentExecution

`ConductorAgentExecution` is the normalized snapshot consumed by `ConductorAgentDelegate`:

| Field | Meaning |
|---|---|
| `executionId` | Agent execution identifier |
| `agentName` | Executed agent |
| `state` | Normalized `ConductorAgentState` |
| `output` | Structured result |
| `text` | Latest or final text |
| `pendingTool` | Pending human/tool request |
| `reasonForIncompletion` | Failure or cancellation reason |
| `startTime` | Agent execution start time in epoch milliseconds |
| `endTime` | Terminal agent execution end time in epoch milliseconds |

States are `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`, and `CANCELED`.

## 4. Durable task output

`ConductorAgentResults` owns the persisted keys:

```json
{
  "executionId": "exec-xyz",
  "agentName": "research_agent",
  "state": "working",
  "taskId": "exec-xyz",
  "contextId": "session-123",
  "task": {
    "kind": "task",
    "id": "exec-xyz",
    "contextId": "session-123",
    "status": { "state": "working" },
    "metadata": {
      "agentType": "conductor",
      "executionId": "exec-xyz",
      "agentName": "research_agent"
    }
  },
  "agentStartTime": 1752451200000,
  "agentPollFailures": 0
}
```

The shared `state`, `taskId`, `contextId`, `task`, `agentMessage`, `artifacts`, and `text` fields use
the same A2A v0.3 wire model as remote A2A agents. Native states map as follows: `RUNNING` to
`working`, `WAITING` to `input-required`, `COMPLETED` to `completed`, `FAILED` to `failed`, and
`CANCELED` to `canceled`.

Terminal executions additionally expose `agentEndTime`. Completed executions retain the legacy
`output` map and add A2A message/artifact parts, including a data part with the full structured
result. Waiting executions complete the current task with `waiting: true`, an A2A
`input-required` status, and may expose `pendingTool` and `text`.

## 5. Agent tool JOIN envelope

Dynamic agent tools use two internal keys shared by the compiler, JOIN, and the state-merge script:

| Key | Type | Owner | Purpose |
|---|---|---|---|
| `_agent_tool_name` | `String` | Dynamic `WorkflowTask.inputParameters`; copied into JOIN output | Stable logical tool name independent of generated and iteration-qualified task references |
| `_agent_tool_output` | `Map<String,Object>` | JOIN output | Complete terminal output from the HTTP, MCP, HUMAN, SIMPLE, or other generated tool task |

The JOIN output type is `Map<String,Object>`, keyed by the actual forked task reference name. Each
marked tool value is another `Map<String,Object>` with exactly the two keys above. For example:

```json
{
  "weather_0__1": {
    "_agent_tool_name": "weather",
    "_agent_tool_output": {
      "response": {
        "statusCode": 200,
        "body": { "temperature": 64 }
      }
    }
  }
}
```

The task reference remains an execution identity and may include a DO_WHILE iteration suffix. The
logical name is always read from `_agent_tool_name`; consumers must not reconstruct it from the
task reference.

## 6. Invariants

1. `agentStartTime` is set once and survives retries and process restarts.
2. `executionId` is persisted before later invocations poll status.
3. A successful status call resets `agentPollFailures` to zero.
4. The idempotency key uses retry-stable task identity, never `taskId`.
5. Every invocation returns a complete `TaskResult`; the annotation runtime applies and persists it.
6. A completed dynamic agent tool with non-empty output contributes one non-empty JOIN observation.
7. `_agent_tool_name` is resolved from runtime input first and the persisted `WorkflowTask`
   definition second.
8. `_agent_tool_output` preserves the complete tool output so the next LLM turn receives the
   observation without provider-specific reconstruction.
