# Workflow Scheduler — Quickstart

This guide walks through the full lifecycle of a scheduled workflow using `curl`.
All examples assume Conductor is running locally on port 8080.

---

## Prerequisites

- Conductor running with a scheduler-compatible persistence backend
  (`conductor-scheduler-postgres-persistence`, `conductor-scheduler-mysql-persistence`, etc.)
- `conductor.scheduler.enabled=true` (default)
- The `http-task` worker available (built-in for HTTP tasks; swap for `SIMPLE` if needed)

---

## Step 1 — Register the workflow

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d @daily-report-workflow.json
```

---

## Step 2 — Create a schedule

`every-minute-schedule.json` fires every minute — useful for seeing results quickly.
Swap in `daily-report-schedule.json` for a realistic weekday 9 AM (New York) schedule.

```bash
curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" \
  -d @every-minute-schedule.json | jq .
```

Expected response:
```json
{
  "name": "every-minute-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "paused": false,
  "nextRunTime": 1708300860000
}
```

---

## Step 3 — Preview next execution times

```bash
curl -s "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/next-execution-times?count=5" \
  | jq '[.[] | (. / 1000 | todate)]'
```

---

## Step 4 — Check execution history

After a minute or two, executions will appear:

```bash
curl -s "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/executions?limit=5" \
  | jq '[.[] | {state, workflowId, scheduledTime}]'
```

Expected output:
```json
[
  { "state": "EXECUTED", "workflowId": "abc123...", "scheduledTime": 1708300860000 },
  { "state": "EXECUTED", "workflowId": "def456...", "scheduledTime": 1708300800000 }
]
```

---

## Step 5 — Pause the schedule

```bash
curl -s -X PUT "http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/pause?reason=testing+pause"
```

Verify it's paused:
```bash
curl -s http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule | jq '{paused, pausedReason}'
```

---

## Step 6 — Resume the schedule

```bash
curl -s -X PUT http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule/resume
```

---

## Step 7 — List all schedules

```bash
curl -s http://localhost:8080/api/scheduler/schedules | jq '[.[] | {name, cronExpression, paused, nextRunTime}]'
```

Filter by workflow name:
```bash
curl -s "http://localhost:8080/api/scheduler/schedules?workflowName=daily_report_workflow" | jq .
```

---

## Step 8 — Delete the schedule

```bash
curl -s -X DELETE http://localhost:8080/api/scheduler/schedules/every-minute-demo-schedule
```

---

## API Reference

| Method   | Path                                                   | Description                                    |
|----------|--------------------------------------------------------|------------------------------------------------|
| `POST`   | `/api/scheduler/schedules`                             | Create or update a schedule                    |
| `GET`    | `/api/scheduler/schedules`                             | List all (optional `?workflowName=` filter)    |
| `GET`    | `/api/scheduler/schedules/{name}`                      | Get a schedule by name                         |
| `DELETE` | `/api/scheduler/schedules/{name}`                      | Delete a schedule                              |
| `PUT`    | `/api/scheduler/schedules/{name}/pause`                | Pause (optional `?reason=`)                    |
| `PUT`    | `/api/scheduler/schedules/{name}/resume`               | Resume                                         |
| `GET`    | `/api/scheduler/schedules/{name}/executions`           | Execution history (`?limit=10`)                |
| `GET`    | `/api/scheduler/schedules/{name}/next-execution-times` | Preview next N times (`?count=5`)              |

---

## Cron expression format

The scheduler uses **6-field Spring cron** (second-level precision):

```
┌─────────────── second (0-59)
│ ┌───────────── minute (0-59)
│ │ ┌─────────── hour (0-23)
│ │ │ ┌───────── day of month (1-31)
│ │ │ │ ┌─────── month (1-12 or JAN-DEC)
│ │ │ │ │ ┌───── day of week (0-7 or MON-SUN)
│ │ │ │ │ │
* * * * * *
```

| Expression                    | Meaning                          |
|-------------------------------|----------------------------------|
| `0 * * * * *`                 | Every minute                     |
| `0 0 9 * * MON-FRI`           | Weekdays at 9:00 AM              |
| `0 0 0 1 * *`                 | First day of every month         |
| `0 0/30 9-17 * * MON-FRI`     | Every 30 min, business hours     |

---

## Configuration

```yaml
conductor:
  scheduler:
    enabled: true                      # default: true
    polling-interval: 1000             # ms between polls; default: 100
    polling-thread-count: 1            # default: 1
    poll-batch-size: 5                 # schedules processed per cycle; default: 5
    scheduler-time-zone: UTC           # default: UTC
    archival-max-records: 5            # history rows to keep per schedule; default: 5
    archival-max-record-threshold: 10  # prune when over threshold; default: 10
    jitter-max-ms: 0                   # dispatch jitter per schedule; default: 0 (disabled)
```

> **Tip:** For deployments with many schedules firing at the same cron tick, increase
> `poll-batch-size` to match expected fanout and set `jitter-max-ms` to a small value
> (e.g. 200) to smooth burst load on the DB and executor pool.

---

## Example Scenarios

Eight verified scenarios covering common patterns. Each has a workflow definition and a
matching schedule definition. All were tested live.

### 1. Basic (`every-minute-schedule.json` + `daily-report-workflow.json`)

Fires every minute, fetches a sample JSON dataset via HTTP. Good first test after setup.

**Register and run:**
```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @daily-report-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @every-minute-schedule.json
```

---

### 2. Catchup mode (`catchup-schedule.json` + `catchup-workflow.json`)

Sets `runCatchupScheduleInstances: true`. If the scheduler is offline for N minutes, it fires
once per missed slot on restart — stepping slot-by-slot rather than jumping to the current time.

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @catchup-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @catchup-schedule.json
```

To observe catchup: stop Conductor for a few minutes, then restart and watch executions fire
in sequence for the missed slots.

---

### 3. Bounded schedule (`bounded-schedule-template.json` + `bounded-workflow.json`)

Uses `scheduleStartTime` / `scheduleEndTime` (epoch ms) to confine execution to a window.
The template has `__START_MS__` / `__END_MS__` placeholders — populate with `sed`:

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @bounded-workflow.json

NOW=$(python3 -c "import time; print(int(time.time()*1000))")
END=$((NOW + 300000))  # 5-minute window
sed "s/__START_MS__/$NOW/; s/__END_MS__/$END/" bounded-schedule-template.json | \
  curl -s -X POST http://localhost:8080/api/scheduler/schedules \
    -H "Content-Type: application/json" -d @-
```

---

### 4. Multi-step FORK/JOIN (`multistep-schedule.json` + `multistep-workflow.json`)

Two parallel HTTP calls (UTC time + America/New_York time), joined into one output map.

> **Gotcha:** Use a literal `/` in timezone query params — not `%2F`. Conductor's HTTP task
> passes percent-encoded slashes literally, which the remote API rejects as an invalid timezone.

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @multistep-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @multistep-schedule.json
```

---

### 5. Failure scenario (`retry-schedule.json` + `retry-workflow.json`)

Workflow always fails (404). Confirms the scheduler fires every minute regardless of prior
outcome — each tick produces a new `EXECUTED` record in scheduler history even as the workflow
itself records `FAILED`.

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @retry-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @retry-schedule.json
```

---

### 6. Concurrent execution (`concurrent-schedule.json` + `concurrent-workflow.json`)

A 90-second WAIT task fired every 60 seconds. OSS Conductor has no built-in concurrent-execution
guard, so instances stack up. Demonstrates the behavior users need to design around.

> **Gotcha:** WAIT task duration must be `"90s"` / `"2m"` / `"1h"` — not ISO-8601 `PT90S`.
> Conductor's `DateTimeUtils.parseDuration` uses its own regex, not the Java Duration parser.

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @concurrent-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @concurrent-schedule.json
```

---

### 7. Input parameterization (`input-param-schedule.json` + `input-param-workflow.json`)

Every triggered workflow automatically receives `scheduledTime` and `executionTime` (epoch ms)
injected by the scheduler. Static keys from `startWorkflowRequest.input` are preserved.
An INLINE JavaScript task computes a 24-hour report window from `scheduledTime`.

Sample output from a live run:
```
scheduledAt:       2026-02-19T23:22:00.000Z   ← exact cron slot
triggeredAt:       2026-02-19T23:22:00.837Z   ← actual dispatch (~837ms poll overhead)
reportWindowStart: 2026-02-18T23:22:00.000Z
reportWindowEnd:   2026-02-19T23:22:00.000Z
```

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @input-param-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @input-param-schedule.json
```

---

### 8. DO_WHILE variant (`dowhile-schedule.json` + `dowhile-workflow.json`)

Internally loops 3 times via DO_WHILE, fetching current time on each iteration.

> **Gotcha:** DO_WHILE output is keyed by iteration number as a string (`"1"`, `"2"`, `"3"`),
> not by task reference name. Reference the last iteration's output via:
> `${poll_loop.output.3.fetch_current_time.response.body.dateTime}`

```bash
curl -s -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" -d @dowhile-workflow.json

curl -s -X POST http://localhost:8080/api/scheduler/schedules \
  -H "Content-Type: application/json" -d @dowhile-schedule.json
```

---

## Concurrency / Load Test Scripts

The `../scripts/` directory contains four scripts from live concurrency testing.
All require `curl`, `python3`, and a running Conductor instance.

### test-09-concurrent-write.sh — simultaneous schedule registration

Run on two machines at the same epoch second to verify UPSERT correctness:
```bash
# Both machines run this pointing at the same Conductor instance
./scripts/test-09-concurrent-write.sh http://localhost:8080
```

### test-10-concurrent-resume.sh — simultaneous resume

Verifies that a paused schedule resumed from two machines fires exactly once:
```bash
# Machine 1 (setup + fire)
./scripts/test-10-concurrent-resume.sh setup http://localhost:8080
# Follow the printed instructions to run the fire command on both machines simultaneously
```

### test-11-thundering-herd.sh — N schedules at the same tick

Registers N schedules all firing at `0 * * * * *`, then verifies each fires exactly once:
```bash
./scripts/test-11-thundering-herd.sh 50 http://localhost:8080
```

> **Note:** Requires `poll-batch-size >= N` (or multiple poll cycles). With the default
> `poll-batch-size=5`, only 5 schedules fire per cycle. Increase it before running with N > 5.

### test-12-load-blast.py — concurrent workflow submissions

Blasts N `POST /api/workflow` requests simultaneously, reports latency percentiles:
```bash
# Single machine
python3 scripts/test-12-load-blast.py --url http://localhost:8080 --count 25

# Two machines synchronized to the same epoch second
TARGET=$(python3 -c "import time; print(int(time.time())+15)")
# Machine 1:
python3 scripts/test-12-load-blast.py --url http://localhost:8080 --count 25 --target $TARGET
# Machine 2:
python3 scripts/test-12-load-blast.py --url http://<other-host>:8080 --count 25 --target $TARGET
```
