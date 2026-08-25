---
description: "Conductor cookbook — poll a slow third-party job to completion with a single HTTP_POLL task: terminationCondition, pollingInterval, pollingStrategy, and maxPollCount instead of a DO_WHILE loop."
---

# Polling a long-running external job

You submit work to a third-party API and it hands back a job id. The job takes minutes, sometimes hours. You need the workflow to wait for it without holding a thread, without a worker, and without hammering the vendor.

`HTTP_POLL` is one task that does this. You give it the status URL and a condition that says "stop when this is true".

## The shape

```text
submit_job (HTTP)  ──>  await_job (HTTP_POLL)  ──>  SUCCEEDED ──> record artifact
                            │  polls the status URL          FAILED ──> TERMINATE
                            │  until terminationCondition
                            └─ sleeps between polls, holds nothing open
```

## Why not a loop

A `DO_WHILE` wrapped around an `HTTP` task also works, and you will see it in older examples. It costs you more than it looks:

| | `DO_WHILE` + `HTTP` | `HTTP_POLL` |
|---|---|---|
| Tasks in the execution | Two per iteration, forever growing | One |
| Backoff between polls | You build it | `pollingStrategy` |
| Poll ceiling | You count iterations yourself | `maxPollCount` |
| Reading the execution | Scroll past 40 iterations | One task with a poll count |

The loop version also makes the *interesting* part — the termination condition — an expression buried in `loopCondition`, evaluated against loop state rather than the response.

## The task

```json
{
  "name": "await_job",
  "taskReferenceName": "await_job",
  "type": "HTTP_POLL",
  "inputParameters": {
    "http_request": {
      "uri": "${workflow.input.jobApiUrl}/jobs/${submit_job.output.response.body.jobId}",
      "method": "GET",
      "terminationCondition": "(function(){ var s = $.output.response.body.state; return s === 'SUCCEEDED' || s === 'FAILED'; })();",
      "pollingInterval": 60,
      "pollingStrategy": "FIXED",
      "maxPollCount": 60
    }
  }
}
```

`HTTP_POLL` takes the same `http_request` block as `HTTP` — `uri`, `method`, `headers`, `body`, `accept`, `contentType`, `connectionTimeOut`, `readTimeOut`, `acceptedStatusCodes`, `outputFilter` — plus four polling fields:

| Field | Default | What it does |
|---|---|---|
| `terminationCondition` | — | Expression evaluated after each poll. Truthy stops the task |
| `pollingInterval` | — | Seconds between polls |
| `pollingStrategy` | — | `FIXED`, `LINEAR_BACKOFF`, or `EXPONENTIAL_BACKOFF` |
| `maxPollCount` | `1000` | Give up after this many polls |

### Writing the termination condition

The expression sees two objects:

- **`$.output`** — the current poll's result, including `response.body`, `response.headers`, `response.statusCode`
- **`$.input`** — the task's input

Return a boolean to say "done" or "keep going". You can also return a number for three-way control: `1` completes the task, `0` polls again, `-1` fails it.

**Terminate on failure too.** A condition that only matches `SUCCEEDED` keeps polling a dead job until `maxPollCount` runs out. Match every terminal state and branch on the outcome afterwards:

```javascript
(function(){ var s = $.output.response.body.state; return s === 'SUCCEEDED' || s === 'FAILED'; })();
```

### Polling intervals have a server floor

`pollingInterval` is clamped to `conductor.worker.http_poll.min_poll_interval`, which defaults to **60 seconds**. Asking for `pollingInterval: 5` gets you 60 unless an operator lowered the floor. Size `maxPollCount` against the effective interval, not the one you asked for: 60 polls at 60 seconds is a one-hour ceiling.

## Prerequisites

A running Conductor server and a job API to poll. A stub is included so you can run the shape without a vendor account.

Save this as `job_stub_service.py` and leave it running:

```python
--8<-- "docs/devguide/cookbook/assets/job_stub_service.py"
```

```bash
python3 job_stub_service.py       # http://localhost:8089
```

It advances one state per poll — `QUEUED` → `RUNNING` → `RUNNING` → `SUCCEEDED` — so you can watch the whole lifecycle without waiting on wall-clock time. `POST /jobs/{id}/fail` forces the failure branch, and `GET /polls` shows how many times each job was polled.

## Runnable definition

Save this as `http-poll-external-job.json`:

```json
--8<-- "docs/devguide/cookbook/assets/http-poll-external-job.json"
```

## Register and run

```bash
conductor workflow create http-poll-external-job.json
conductor workflow start -w http_poll_external_job \
  -i '{"jobApiUrl":"http://localhost:8089","dataset":"orders_2026_q2"}'
```

Open **[Executions](http://localhost:8080/executions)** in the Conductor UI and select the new execution to review the task graph, and each task's inputs and outputs.

`await_job` stays as a single task and its poll count climbs. When the stub reports `SUCCEEDED`, the `SWITCH` records the artifact; force a failure with `POST /jobs/{id}/fail` and the same workflow terminates with `remote_job_failed` instead.

Cross-check what the vendor actually saw:

```bash
curl -s http://localhost:8089/polls
```

## Production notes

- **Match every terminal state in the condition,** not just success, or a dead job polls until `maxPollCount`.
- **`pollingInterval` has a server-side floor** (`min_poll_interval`, default 60s). Your value is a request, not a guarantee.
- **Set `maxPollCount` from a wall-clock budget.** Interval × count is the real ceiling; give the workflow a `timeoutSeconds` above it.
- **Use `EXPONENTIAL_BACKOFF` for jobs of unknown length** so a five-hour job does not generate 300 identical requests.
- **Poll a cheap endpoint.** If the vendor's status call is rate-limited or returns the full payload, ask for a lightweight status URL, or use `outputFilter` to keep the response out of workflow state.
- **The submit step needs an idempotency key.** A retried submit that creates a second job leaves you polling the wrong one.
- **Do not use it for sub-second work.** Below the poll floor, a synchronous `HTTP` task is the right tool.

## Related

- [Wait and timer patterns](wait-and-timers.md) — waiting on a signal or a clock rather than a status URL
- [Task timeouts and retries](task-timeouts-and-retries.md) — bounding the submit call
- [Saga: compensating a partial failure](saga-compensation.md) — undoing a submitted job when a later step fails
