---
description: Run a deployed agent on a cadence using the Conductor CLI, the scheduler API, or the UI — no code required.
---

# Scheduling Agents

A deployed agent is a workflow with the agent's name, so the ordinary scheduler runs it. You do not need to touch the SDK to put an agent on a cadence — the CLI, the API, and the UI all work.

Two things must already be true:

- The agent is **deployed**, so a workflow with its name exists.
- Something is **serving** its workers, or fired executions will never progress. See [Deploying Agents](deploying-agents.md).

## With the CLI

```bash
conductor schedule create \
  -n nightly_digest-nightly \
  -c "0 0 2 * * ?" \
  -w nightly_digest \
  -i '{"prompt":"Summarise yesterday."}'
```

| Flag | Meaning |
|---|---|
| `-n`, `--name` | Schedule name. Conventionally `{agent}-{purpose}` |
| `-c`, `--cron` | Quartz cron — **six fields**, seconds first |
| `-w`, `--workflow` | The deployed agent's name |
| `-i`, `--input` | Input for each fire, as JSON |
| `-p`, `--paused` | Create it without starting it |
| `--version` | Pin an agent version (`0` = latest) |

You can also create from a file, which is the better fit for a release pipeline:

```bash
conductor schedule create schedule.json
```

Inspect what exists:

```bash
conductor schedule list
conductor schedule get nightly_digest-nightly
conductor schedule search -w nightly_digest      # executions the schedule produced
```

`conductor schedule list` prints the schedule, its cron, the workflow it starts, and whether it is active:

```text
NAME                     CRON          WORKFLOW              STATUS   CREATED TIME
nightly_digest-nightly   0 0 2 * * ?   llm_with_guardrails   active   2026-07-27 19:48:38
```

Pause and resume a schedule through the API:

```bash
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/nightly_digest-nightly/pause'
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/nightly_digest-nightly/resume'
```

## With the API

Everything lives under `/api/scheduler`.

**Create or update** — the same endpoint does both:

```bash
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "nightly_digest-nightly",
    "cronExpression": "0 0 2 * * ?",
    "zoneId": "UTC",
    "paused": false,
    "runCatchupScheduleInstances": false,
    "description": "nightly incident digest",
    "startWorkflowRequest": {
      "name": "nightly_digest",
      "version": 1,
      "input": { "prompt": "Summarise yesterday." }
    }
  }'
```

| Field | Default | What it does |
|---|---|---|
| `name` | *required* | Schedule name |
| `cronExpression` | *required* | Six-field Quartz cron |
| `startWorkflowRequest` | *required* | Which agent to start, and with what input |
| `zoneId` | `UTC` | Timezone the cron is evaluated in |
| `paused` | `false` | Register without starting |
| `runCatchupScheduleInstances` | `false` | Replay fires missed while the server was down |
| `scheduleStartTime` / `scheduleEndTime` | — | Epoch bounds for when the schedule is live |
| `cronSchedules` | — | Several cron/timezone pairs; takes priority over `cronExpression` |
| `description` | — | Free text, shown in the UI |

**The rest of the operations:**

| Action | Call |
|---|---|
| List all | `GET /api/scheduler/schedules` |
| List for one agent | `GET /api/scheduler/schedules?workflowName=nightly_digest` |
| Get one | `GET /api/scheduler/schedules/{name}` |
| Pause | `PUT /api/scheduler/schedules/{name}/pause` |
| Resume | `PUT /api/scheduler/schedules/{name}/resume` |
| Delete | `DELETE /api/scheduler/schedules/{name}` |
| Executions it produced | `GET /api/scheduler/search/executions` |

**Check a cron before you commit to it.** This returns the next fire times as epoch milliseconds:

```bash
curl 'http://localhost:8080/api/scheduler/nextFewSchedules?cronExpression=0+0+2+*+*+%3F&limit=3'
# [1785290400000,1785376800000,1785463200000]
```

There are also server-wide admin controls — `GET /api/scheduler/admin/pause`, `/admin/resume`, and `/admin/requeue` — which stop or restart *every* schedule. Useful during an incident, dangerous by accident.

## In the UI

Schedules appear at **[http://localhost:8080/scheduler](http://localhost:8080/scheduler)**, and an individual one at `/scheduler/edit/{name}`. The UI is the quickest way to pause a misbehaving schedule and to see the next fire time without computing a cron by hand.

Each fired run shows up in **[Executions](http://localhost:8080/executions)** like any other agent execution.

## The cron is six fields

Conductor uses Quartz cron, where the first field is **seconds**. A five-field Unix cron will not do what you expect.

| Cron | Meaning |
|---|---|
| `0 0 2 * * ?` | 02:00 every day |
| `0 0 * ? * *` | Top of every hour |
| `0 */15 * ? * *` | Every 15 minutes |
| `0 0 9 ? * MON-FRI` | 09:00 on weekdays |

## Production notes

- **Deploying is not enough — serve the workers too.** A scheduled agent with nothing serving accumulates executions that never progress.
- **Pause rather than delete** while debugging; the definition and history survive.
- **Leave catchup off unless the work is idempotent.** After an outage it fires every missed run at once.
- **Set `zoneId` explicitly** for anything business-facing. `UTC` is rarely what "daily at 2am" means to a user.
- **Watch for overlap.** A cadence shorter than the agent's runtime starts the next fire before the last finishes.
- **Name schedules `{agent}-{purpose}`** so `conductor schedule list` stays readable as the count grows.

## Next steps

- [Deploying Agents](deploying-agents.md) — getting the agent and its workers running first
- [Agent Configuration](agent-configuration.md) — bounding an agent that runs unattended
- [Scheduling Workflows](../how-tos/Workflows/scheduling-workflows.md) — the same scheduler, for plain workflows
