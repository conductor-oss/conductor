---
description: "Monitor task queues and scale Conductor workers — queue depth, poll data, Prometheus metrics, autoscaling policies, and performance tuning."
---

# Scaling Task Workers

Workers execute business logic outside the Conductor server. Keeping them healthy requires two things: **monitoring** queue and worker state, and **scaling** based on what the data tells you.


## Monitoring task queues

Conductor tracks queue size and worker poll activity for every task type. Use this data to detect backlogs, stalled workers, and capacity issues.

### Using the UI

Navigate to **Home > Task Queues** (or `<your UI server URL>/taskQueue`). For each task, the UI shows:

- **Queue Size** — tasks waiting to be picked up.
- **Workers** — count and instance details of workers polling this task.

### Using the CLI

```bash
# List all tasks with queue info
conductor task list

# Get details for a specific task
conductor task get <TASK_NAME>
```

### Using APIs

Get the number of tasks waiting in a queue:

```shell
curl '{{ server_host }}{{ api_prefix }}/tasks/queue/sizes?taskType=<TASK_NAME>' \
  -H 'accept: */*'
```

Get worker poll data (which workers are polling, last poll time):

```shell
curl '{{ server_host }}{{ api_prefix }}/tasks/queue/polldata?taskType=<TASK_NAME>' \
  -H 'accept: */*'
```

!!! note
    Replace `<TASK_NAME>` with your task name.


## Prometheus metrics

Conductor publishes metrics that feed dashboards, alerts, and autoscaling policies. All metrics include `taskType` as a tag so you can monitor per-task.

### Queue depth (Gauge)

```promql
max(task_queue_depth{taskType="my_task"})
```

- Keep queue depth stable. It doesn't need to be zero (especially for long-running tasks), but sustained growth means workers can't keep up.
- Alert on queue depth increasing over a sustained period and use it to trigger autoscaling.

### Task completion rate (Counter)

```promql
rate(task_completed_seconds_count{taskType="my_task"}[$__rate_interval])
```

- Measures throughput — tasks completed per second.
- A sudden drop indicates workers are struggling, failing, or have stopped polling.
- Set a minimum throughput threshold and alert when it drops below.

### Queue wait time

```promql
max(task_queue_wait_time_seconds{quantile="0.99", taskType="my_task"})
```

How long tasks sit in the queue before a worker picks them up. If this is more than a few seconds:

1. **Check worker count** — if all workers are busy, add more instances.
2. **Check polling interval** — reduce it if workers aren't polling frequently enough.

!!! warning
    Reducing the polling interval increases API requests to the server. Balance responsiveness against server load.


## Scaling strategies

### When to scale

| Signal | Action |
|---|---|
| Queue depth growing steadily | Add worker instances |
| Queue wait time > 5s at p99 | Add worker instances or reduce polling interval |
| Throughput dropping while queue grows | Investigate worker health (CPU, memory, downstream dependencies) |
| Queue consistently empty, workers idle | Scale down to save resources |

### Horizontal scaling

Add more worker instances. Conductor distributes tasks automatically — every worker polling the same task type competes for work from the same queue. No configuration changes needed on the Conductor server.

### Polling interval tuning

The polling interval controls how frequently workers check for new tasks. Shorter intervals mean lower latency but higher server load.

| Scenario | Recommended interval |
|---|---|
| Latency-sensitive tasks | 100–500ms |
| Standard processing | 1–5s |
| Batch / background work | 5–30s |

### Thread pool sizing

Each worker instance can run multiple polling threads. A good starting point:

```
threads = (task_throughput × avg_task_duration) / num_worker_instances
```

For I/O-bound tasks (HTTP calls, database queries), use more threads than CPU cores. For CPU-bound tasks, match thread count to available cores.

### Rate limiting

If downstream services have rate limits, configure task-level rate limits to prevent workers from overwhelming them:

```json
{
  "name": "call_external_api",
  "rateLimitPerFrequency": 100,
  "rateLimitFrequencyInSeconds": 60
}
```

This limits the task to 100 executions per 60-second window across all workers.

### Domain isolation

Use [task-to-domain](../../../documentation/api/taskdomains.md) to route tasks to specific worker pools. This prevents noisy neighbors — a high-volume workflow won't starve workers serving a latency-sensitive one.
