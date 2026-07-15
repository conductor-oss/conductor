# Conductor-Agent Branch — Example Workflows

Addresses review item 3 ("add examples, mirroring `ai/examples/`"). Field names come from
[data-model.md](./data-model.md); the file list and slugs are fixed by
[architecture.md](./architecture.md) §3.2/§5.

## 1. Placement and naming

Four new files in `ai/examples/`, continuing the numbering after `30` (architecture.md §5):

| File | Workflow `name` | Demonstrates | Requirements |
|---|---|---|---|
| `31-conductor-agent-basic.json` | `conductor_agent_basic` | Minimal single-task run to completion (poll mode) | A registered agent |
| `32-conductor-agent-human-in-loop.json` | `conductor_agent_human_in_loop` | `WAITING` → `HUMAN` task → resume with `executionId` | A registered agent |
| `33-conductor-agent-multi-agent.json` | `conductor_agent_multi_agent` | Two `AGENT` (conductor) branches via `FORK_JOIN` → `JOIN` | Two registered agents |
| `34-conductor-agent-cancel.json` | `conductor_agent_cancel` | Start an agent run, then cancel it | A registered agent |

`README.md` in `ai/examples/` gets a new **"Conductor agent workflow examples"**
section after the A2A table with these four rows, plus a `## Example Commands` entry per file
using the existing register/execute curl pattern.

## 2. Shared task shape

Every `AGENT` task in these examples uses `agentType: "conductor"`:

```json
{
  "name": "run_agent",
  "taskReferenceName": "run_agent_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "agentName": "planner",
    "text": "${workflow.input.prompt}"
  }
}
```

Outputs are read from the keys in architecture.md §4.3, e.g.
`${run_agent_ref.output.text}`, `${run_agent_ref.output.output}`,
`${run_agent_ref.output.executionId}`, `${run_agent_ref.output.state}`,
`${run_agent_ref.output.waiting}`.

## 3. Example specifics

### 31 — `conductor-agent-basic`
Single `AGENT` (conductor) task; workflow output surfaces `text`, `output`, and `state`. The
optional tuning fields (`pollIntervalSeconds`, `maxDurationSeconds`, `maxPollFailures`) are
mentioned in the README prose, not the JSON (workflow JSON has no comments).

### 32 — `conductor-agent-human-in-loop`
1. `AGENT` (conductor) → completes with `waiting == true` and a `pendingTool` question.
2. `SWITCH` on `${run_agent_ref.output.waiting}` → `HUMAN` task to collect the answer.
3. Second `AGENT` (conductor) with `executionId: "${run_agent_ref.output.executionId}"` and
   `text: "${collect_answer_ref.output.<field>}"` to resume.

Mirrors the waiting/resume path in [testing.md](./testing.md) §4.2 and the shipped `WAITING`
mapping (architecture.md §4.5).

### 33 — `conductor-agent-multi-agent`
A `FORK_JOIN` fans out to two `AGENT` (conductor) tasks (`agentName` differs per branch), then a
`JOIN` collects both. Demonstrates that each branch gets an independent `executionId` and that the
two runs poll concurrently without blocking a worker thread (architecture.md §2, non-blocking).

### 34 — `conductor-agent-cancel`
An `AGENT` (conductor) task started with a long `maxDurationSeconds`, followed by a control path
that terminates the run; documents the `CANCELED` → task `CANCELED` mapping (architecture.md §4.5)
and how `cancel` terminates the child workflow.

## 4. README curl blocks

Follow the existing register + execute pattern exactly:

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @31-conductor-agent-basic.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/conductor_agent_basic' \
  -H 'Content-Type: application/json' \
  -d '{"prompt": "Draft a project plan for a mobile app launch"}'
```

The `POST /api/workflow/{name}` execute path is the one already used throughout the existing
README — kept identical so the doc stays consistent and verifiable.

## 5. Prerequisites note

Add a short prerequisite to the README's new section: these examples require at least one agent
registered with the server (example 33 needs two). If unavailable at authoring time, mark the section
`<!-- TODO: verify against live server -->` per AGENTS.md.
