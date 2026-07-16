# Data Model — Conductor-Agent Runtime Branch

Types and JSON shapes for the `agentType: "conductor"` branch. All names are defined in
[architecture.md](./architecture.md) and reused here verbatim. This document is the reference the
examples and docs render from — do not introduce a field that is not listed here.

## 1. Java types (existing production code)

### 1.1 Core `WorkflowExecutor`

```java
public interface WorkflowExecutor {
    AgentStartResponse startAgentExecution(AgentStartRequest request);
    WorkflowModel getWorkflow(String workflowId, boolean includeTasks);
    TaskModel updateTask(TaskResult taskResult);
    void terminateWorkflow(String workflowId, String reason);
}
```

### 1.2 `AgentStartRequest` (`common.metadata.agent`)

| Field | Type | Source (`A2ACallRequest` → start request) |
|---|---|---|
| `name` | `String` | `agentName` |
| `version` | `Integer` | `agentVersion` |
| `prompt` | `String` | resolved per [architecture.md §4.2](./architecture.md) |
| `context` | `Map<String,Object>` | `context` |
| `sessionId` | `String` | `sessionId` |
| `runId` | `String` | `runId` |
| `idempotencyKey` | `String` | derived, [architecture.md §4.6](./architecture.md) |

### 1.3 `ConductorAgentExecution` (`@Data @Builder`, immutable snapshot)

| Field | Type | Meaning |
|---|---|---|
| `executionId` | `String` | Runtime-assigned id for poll/respond/cancel. |
| `agentName` | `String` | Executing agent. |
| `sessionId` | `String` | Owning session. |
| `state` | `ConductorAgentState` | Current lifecycle state. |
| `output` | `Map<String,Object>` | Structured output of a completed run. |
| `text` | `String` | Latest text (partial while running, final when complete). |
| `pendingTool` | `Map<String,Object>` | Set only when `state == WAITING`. |
| `reasonForIncompletion` | `String` | Explanation for `FAILED`/`CANCELED`. |

Convenience: `isComplete()`, `isRunning()`, `isWaiting()`.

### 1.4 `ConductorAgentState` (enum)

`RUNNING`, `WAITING`, `COMPLETED`, `FAILED`, `CANCELED`.
`isTerminal()` → COMPLETED/FAILED/CANCELED; `isInterrupted()` → WAITING.

## 2. Task input JSON (`inputParameters`)

Deserialised into `A2ACallRequest`. Conductor-branch subset (see
[architecture.md §4.2](./architecture.md)):

```json
{
  "agentType": "conductor",
  "agentName": "research_agent",
  "agentVersion": 1,
  "text": "Summarize the latest release notes",
  "context": { "locale": "en-US" },
  "sessionId": "session-123",
  "runId": "run-abc",
  "executionId": "exec-xyz",
  "pollIntervalSeconds": 5,
  "maxDurationSeconds": 86400,
  "maxPollFailures": 30
}
```

- `agentName` + a prompt are required on a fresh start.
- `executionId` present → resume: the resolved prompt is fed back as `{"result": <prompt>}` via
  `respond(executionId, ...)`, then the snapshot is re-read.

## 3. Task output JSON (`outputData`)

Keyed by `ConductorAgentResults` constants ([architecture.md §4.3](./architecture.md)). Shape varies
by terminal state.

**Completed run:**

```json
{
  "executionId": "exec-xyz",
  "agentName": "research_agent",
  "sessionId": "session-123",
  "state": "COMPLETED",
  "text": "Here is the summary...",
  "output": { "summary": "...", "citations": ["..."] },
  "agentStartedAt": 1752451200000,
  "agentPollFailures": 0
}
```

**Waiting for human/tool input** (task status is `COMPLETED`, `waiting=true`):

```json
{
  "executionId": "exec-xyz",
  "agentName": "research_agent",
  "sessionId": "session-123",
  "state": "WAITING",
  "waiting": true,
  "pendingTool": { "name": "ask_human", "arguments": { "question": "Which region?" } },
  "text": "I need one clarification before continuing."
}
```

**Failed run** (task status `FAILED`, `reasonForIncompletion` set on the task):

```json
{
  "executionId": "exec-xyz",
  "agentName": "research_agent",
  "state": "FAILED"
}
```

## 4. Invariants (asserted by the tests in [testing.md](./testing.md))

1. `state` in output always equals `ConductorAgentState.name()` of the applied snapshot; a null
   snapshot state is treated as `RUNNING`.
2. `agentStartedAt` is written exactly once and never overwritten across polls/retries.
3. `agentPollFailures` resets to `0` on any successful `getStatus`.
4. On `WAITING`, the Conductor task status is `COMPLETED` (not IN_PROGRESS) and `waiting=true`.
5. The idempotency key is byte-for-byte identical across retries of the same
   `(workflowInstanceId, referenceTaskName, iteration)`.
