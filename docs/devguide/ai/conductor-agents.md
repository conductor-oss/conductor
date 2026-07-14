---
description: "Conductor agents — run an agent on the embedded agentspan runtime as a durable AGENT task. The conductor branch of the AGENT task, its input/output contract, human-in-the-loop resume, and durability guards."
---

# Conductor agents (embedded runtime)

The `AGENT` task selects its runtime with an **`agentType`** input:

- `agentType: "a2a"` (default) — call a **remote** Agent2Agent endpoint over HTTP. See [A2A integration](a2a-integration.md).
- `agentType: "conductor"` — run an agent on the **embedded agentspan runtime** in-process. This page.

Both values drive the same `AGENT` task type with one consistent input/output contract; the branch is chosen per task by `agentType`.


## What it is

With `agentType: "conductor"`, the `AGENT` task runs a registered agent on Conductor's embedded agentspan runtime instead of calling out to a remote agent. Like the A2A branch, it is **non-blocking**: a fast reply completes immediately; a long-running run moves to `IN_PROGRESS` and is polled at a cadence (no worker thread is held), so the call survives a server crash, restart, or redeploy and resumes from persisted state.

This branch requires the embedded runtime, enabled with:

```properties
agentspan.embedded=true
```

On a deployment without it, the runtime bean is absent and any `agentType: "conductor"` task fails terminally with:

```
Conductor agents require the embedded agentspan runtime (agentspan.embedded=true)
```


## Task input

The conductor branch reads these fields from the `AGENT` task input (`A2ACallRequest`). Fields specific to the A2A branch (`agentUrl`, `streaming`, `pushNotification`, `headers`, `contextId`, `taskId`, …) are ignored here.

| Field | Type | Meaning |
|---|---|---|
| `agentType` | String | Must be `"conductor"` to select this branch. |
| `agentName` | String | **Required** on a fresh start. The registered agent to run. |
| `agentVersion` | Integer | Optional. When null, the runtime uses the latest registered version. |
| `sessionId` | String | Optional. Associates the run with an existing conversation/session. |
| `runId` | String | Optional caller-supplied run id. |
| `executionId` | String | When set, **resume** an in-flight run instead of starting a new one (see [Human-in-the-loop](#human-in-the-loop--resume)). |
| `context` | Map | Extra context values passed to the run. |
| `text` / `prompt` / `parts` / `message` | — | Prompt source (see resolution order below). |
| `pollIntervalSeconds` | Integer | Poll cadence while the run is not terminal. Default 5. |
| `maxDurationSeconds` | Integer | Absolute deadline. Default 86400 (24h). |
| `maxPollFailures` | Integer | Consecutive transient poll failures tolerated before failing terminally. Default 30. |

**Prompt resolution.** The prompt is the first non-blank of, in order: text extracted from `message` (its parts) → text extracted from `parts` → `text` → `prompt`.


## Task output

The task writes these keys to its output (`ConductorAgentResults`):

| Output key | Meaning |
|---|---|
| `executionId` | Runtime-assigned execution id — carry it into a follow-up `AGENT` call to resume. |
| `agentName` | Name of the executed agent. |
| `sessionId` | Session id the execution belongs to. |
| `state` | Normalized execution state (uppercase): `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`, `CANCELED`. |
| `waiting` | `true` when the run paused for external input (human answer / tool result). |
| `pendingTool` | The pending tool/human request surfaced while waiting. |
| `text` | Latest / final text emitted by the agent. |
| `output` | Structured output of a completed run. |

The agent's execution state maps onto the Conductor task status as follows:

| `state` | Conductor task status | Notes |
|---|---|---|
| `RUNNING` | `IN_PROGRESS` | Keep polling at the evaluation cadence. |
| `WAITING` | `COMPLETED` | Sets `waiting=true` and surfaces `pendingTool` / `text`. Resume with a new `AGENT` call carrying `executionId`. |
| `COMPLETED` | `COMPLETED` | Surfaces `output` + `text`. |
| `FAILED` | `FAILED` | Sets `reasonForIncompletion`. |
| `CANCELED` | `CANCELED` | Sets `reasonForIncompletion` when present. |

Downstream tasks read these with `${agent.output.text}`, `${agent.output.executionId}`, etc.


## Minimal workflow

A single `AGENT` task that runs an embedded agent to completion:

```json
{
  "name": "conductor_agent_basic",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "run_agent",
      "taskReferenceName": "agent",
      "type": "AGENT",
      "inputParameters": {
        "agentType": "conductor",
        "agentName": "${workflow.input.agentName}",
        "text": "${workflow.input.prompt}",
        "pollIntervalSeconds": 5
      }
    }
  ]
}
```

Register and run it (the embedded runtime must be enabled — `agentspan.embedded=true`):

```bash
# register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @conductor_agent_basic.json

# run
curl -X POST 'http://localhost:8080/api/workflow/conductor_agent_basic' \
  -H 'Content-Type: application/json' \
  -d '{"agentName":"my-agent","prompt":"Summarize the latest release notes"}'
```


## Human-in-the-loop / resume

When the agent pauses for external input (a human answer or a tool result), it reaches `WAITING`. The `AGENT` task then **completes** with `waiting=true` and surfaces the pending request in `pendingTool` (and any `text`), rather than holding a thread open.

The workflow branches on that state and resumes by issuing a **second `AGENT` task carrying the same `executionId`** — the resume feeds the caller's message back into the waiting execution as the pending tool/human result:

```json
{
  "name": "resume_agent",
  "taskReferenceName": "resume",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "executionId": "${agent.output.executionId}",
    "text": "${workflow.input.answer}"
  }
}
```

`agentName` is not required on a resume — the `executionId` identifies the in-flight run. Full example: `ai/examples/32-conductor-agent-human-in-loop.json`.


## Durability

The conductor branch mirrors the A2A branch's guards; the run's state lives in the persisted task output, not a thread.

- **Deterministic idempotency key.** A fresh start uses a restart-stable key so a re-issued start (after a retry or restart) is deduped by the runtime:

    ```
    "conductor-agent-" + workflowInstanceId + ":" + referenceTaskName + ":" + iteration
    ```

    It is built from retry-stable identity — **not** `taskId`, which changes per retry attempt.
- **Absolute deadline.** Anchored once at start; the task fails terminally after `maxDurationSeconds` (default 86400) if the run never reaches a terminal state.
- **Poll-failure cap.** Consecutive transient poll failures are counted and reset to 0 on any success; the task fails terminally at `maxPollFailures` (default 30).


## Examples

Runnable workflow definitions live in [`ai/examples/`](https://github.com/conductor-oss/conductor/tree/main/ai/examples):

| File | Shows |
|---|---|
| `31-conductor-agent-basic.json` | Run an embedded agent to completion (poll mode) |
| `32-conductor-agent-human-in-loop.json` | `WAITING` → resume with the same `executionId` |
