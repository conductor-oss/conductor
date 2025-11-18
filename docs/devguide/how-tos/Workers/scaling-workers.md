# Scaling Workers

Workers execute business logic in workflow applications. Scaling and optimizing worker performance depend on the following metrics:

- Number of pending requests in the task queue
- Throughput of an individual worker
- Total number of worker processes running

Conductor servers publish metrics that help monitor worker health. Learn how to use these metrics with [PromQL](https://prometheus.io/docs/prometheus/latest/querying/basics/).


**Tip**: Each of the following metrics includes `taskType` as a tag. Use this tag to monitor metrics for a specific task.

## Pending requests (Gauge)​

```json
max(task_queue_depth{taskType=})
```

How to use the metric:

- The goal should be to keep the queue depth constant. It may not always be zero, especially for long-running tasks.
- Configure alerts and autoscaling policies for workers based on changes in queue depth over a specified period.

## Number of tasks completed per second (Counter)​

The `task_completed_seconds_count` metric is published as a counter and includes `taskType` as a tag.

```json
rate(task_completed_seconds_count{taskType=}[$__rate_interval])
```

How to use the metric:

- The metric measures the throughput. The goal is to keep the throughput at a threshold depending on the application's needs.
- Configure alerts and autoscaling policies for workers based on fluctuations in throughput.

## Duration the task remained in the queue 

This metric tracks how long a task remains in the queue before a worker picks it up.

```json
max(task_queue_wait_time_seconds{quantile=, taskType=})
```

How to use the metric:

If the value is too high (more than a few seconds), check the following:
- The number of workers. If they are all busy, consider increasing the number of workers.
- The worker polling interval. Reduce it if necessary.

**Note**: Reducing the polling interval may increase API requests to the server, which could trigger system limits.