---
description: "Production best practices for Conductor — idempotency, retry logic with exponential backoff, timeouts, payload management, horizontal scaling of workers, saga patterns, and deployment strategies for durable execution at scale."
---

# Best Practices

This guide covers production best practices for running Conductor as a durable execution engine at scale. Every recommendation here comes from real-world operational experience.


## Idempotent workers

Conductor guarantees **at-least-once** task delivery. Network partitions, worker restarts, and response timeouts can all cause a task to be delivered more than once. Your workers must be idempotent — executing the same task twice should produce the same result without side effects.

**Patterns for idempotency:**

| Pattern | When to use |
| :--- | :--- |
| **Idempotency key** | Pass a unique key (e.g., `workflowId + taskId`) to downstream services. The service deduplicates on this key. |
| **Upsert instead of insert** | Use `INSERT ... ON CONFLICT UPDATE` or equivalent so repeated writes converge to the same state. |
| **Check-then-act** | Query current state before performing the action. Skip if already completed. |
| **Idempotent HTTP methods** | Prefer PUT over POST when the downstream API supports it. |

```python
from conductor.client.worker.worker_task import worker_task

@worker_task(task_definition_name="charge_payment")
def charge_payment(workflow_id: str, task_id: str, amount: float, currency: str) -> dict:
    idempotency_key = f"{workflow_id}-{task_id}"

    # Check if this charge was already processed
    existing = payment_gateway.get_charge(idempotency_key)
    if existing:
        return {"chargeId": existing.id, "status": "already_processed"}

    charge = payment_gateway.create_charge(
        amount=amount, currency=currency, idempotency_key=idempotency_key
    )
    return {"chargeId": charge.id, "status": "charged"}
```

The `workflowId` and `taskId` combination is unique per task execution attempt, making it an ideal idempotency key.


## Timeout configuration

Every task definition should have explicit timeouts. A task without timeouts can block a workflow indefinitely.

**The rule:** `responseTimeoutSeconds` < `timeoutSeconds`. The response timeout detects unresponsive workers; the overall timeout enforces the SLA.

### Recommended configurations

| Task pattern | `responseTimeoutSeconds` | `timeoutSeconds` | `timeoutPolicy` | `retryCount` |
| :--- | :--- | :--- | :--- | :--- |
| API call (< 5s expected) | 10 | 30 | `RETRY` | 3 |
| ML inference | 120 | 300 | `RETRY` | 1 |
| Human approval | 0 (disabled) | 86400 | `ALERT_ONLY` | 0 |
| Batch processing | 600 | 3600 | `TIME_OUT_WF` | 0 |
| Quick data transform | 5 | 15 | `RETRY` | 3 |

### Timeout policies

| Policy | Behavior | Use when |
| :--- | :--- | :--- |
| `RETRY` | Retries the task up to `retryCount` times. | Transient failures are expected (network calls, external APIs). |
| `TIME_OUT_WF` | Fails the entire workflow immediately. | The task is critical and retrying won't help (e.g., expired batch window). |
| `ALERT_ONLY` | Marks the task as timed out but keeps the workflow running. | Human-in-the-loop tasks or tasks with external completion signals. |

!!! warning
    Setting `responseTimeoutSeconds` to 0 disables the response timeout. Only do this for tasks that are completed externally (e.g., [WAIT](../documentation/configuration/workflowdef/systemtasks/wait-task.md) or [Human](../documentation/configuration/workflowdef/systemtasks/human-task.md) tasks).

See [Task Definitions](../documentation/configuration/taskdef.md) for the full parameter reference.


## Payload management

Conductor stores task inputs and outputs in its database. Large payloads degrade performance and increase storage costs.

### Size guidelines

| Payload | Recommended limit | Hard limit (configurable) |
| :--- | :--- | :--- |
| Task input | < 64 KB | 1 MB |
| Task output | < 64 KB | 1 MB |
| Workflow input | < 64 KB | 1 MB |

### External payload storage

For payloads exceeding 64 KB, use external payload storage. Conductor supports S3 out of the box:

```json
{
  "conductor.external-payload-storage.type": "s3",
  "conductor.external-payload-storage.s3.bucket-name": "my-conductor-payloads",
  "conductor.external-payload-storage.s3.region": "us-east-1",
  "conductor.external-payload-storage.s3.signed-url-expiration-seconds": 300
}
```

### Do's and don'ts

| Do | Don't |
| :--- | :--- |
| Return only data that downstream tasks need. | Dump entire API responses into task output. |
| Store large files in S3/GCS and pass the URI. | Pass file contents as base64 in payloads. |
| Use `inputTemplate` to set default values on the task definition. | Duplicate static config in every workflow definition. |
| Keep payload keys flat and descriptive. | Nest payloads 5 levels deep with ambiguous keys. |


## Workflow design

### Small, focused tasks over monolithic workers

Break work into small tasks that each do one thing. This gives you:

- **Granular retries** — only the failed step retries, not the entire pipeline.
- **Reusability** — small tasks compose into different workflows.
- **Visibility** — each step is independently observable in the Conductor UI.

### Sub-workflows vs inline tasks

| Approach | When to use |
| :--- | :--- |
| [Sub-workflow](../documentation/configuration/workflowdef/operators/sub-workflow-task.md) | Reusable logic shared across multiple parent workflows. Independently versioned and testable. |
| Inline tasks in a single workflow | Logic specific to one workflow. Fewer indirections to debug. |

Use sub-workflows when a group of tasks represents a **bounded business capability** (e.g., "process payment", "send notification bundle"). Don't create sub-workflows for a single task — the overhead isn't worth it.

### DYNAMIC_FORK vs sequential loops

| Pattern | When to use |
| :--- | :--- |
| [DYNAMIC_FORK](../documentation/configuration/workflowdef/operators/dynamic-fork-task.md) | Process N items in parallel. Use when items are independent and parallelism improves throughput. |
| [DO_WHILE](../documentation/configuration/workflowdef/operators/do-while-task.md) | Process items sequentially when ordering matters or a shared resource requires serialization. |

!!! tip
    Keep DYNAMIC_FORK fan-out under 500 concurrent tasks per workflow. Beyond that, consider batching items into chunks and forking over the chunks.


## Worker scaling

Workers are stateless and scale horizontally. Tune these parameters to match your workload.

### Polling interval

The polling interval controls how frequently workers check for new tasks. Shorter intervals reduce latency; longer intervals reduce server load.

| Workload | Recommended polling interval |
| :--- | :--- |
| Low-latency (< 1s SLA) | 100-250 ms |
| Standard processing | 500 ms - 1s |
| Background / batch | 5-10s |

### Thread pool sizing

Each worker instance runs a configurable number of polling threads. Start with:

```
threads = (target_throughput * avg_task_duration_seconds) / num_worker_instances
```

For example, 100 tasks/sec with 2s average execution across 5 instances: `(100 * 2) / 5 = 40 threads` per instance.

### Rate limiting and concurrency

Use task definition settings to protect downstream services:

```json
{
  "name": "call_external_api",
  "rateLimitPerFrequency": 50,
  "rateLimitFrequencyInSeconds": 1,
  "concurrentExecLimit": 20
}
```

This limits the task to 50 executions per second globally, with at most 20 running concurrently.

### Domain isolation

Use [task domains](../documentation/api/taskdomains.md) to route tasks to specific worker pools. Common use cases:

- **Environment isolation** — dev workers only pick up dev tasks.
- **Priority lanes** — premium customers routed to dedicated capacity.
- **Regional affinity** — route tasks to workers closest to the data.

See [Scaling Workers](how-tos/Workers/scaling-workers.md) for more detail.


## Error handling patterns

### Retries vs terminal failure

By default, a failed task is retried according to `retryCount` and `retryLogic` (`FIXED`, `EXPONENTIAL_BACKOFF`, or `LINEAR_BACKOFF`). For errors that should **not** be retried, set the task status to `FAILED_WITH_TERMINAL_ERROR`:

```python
from conductor.client.http.models import TaskResult, TaskResultStatus

@worker_task(task_definition_name="validate_order")
def validate_order(order_id: str, items: list) -> TaskResult:
    if not items:
        result = TaskResult()
        result.status = TaskResultStatus.FAILED_WITH_TERMINAL_ERROR
        result.reason_for_incompletion = "Order has no items — not retryable"
        return result

    # ... validation logic
    return {"valid": True}
```

| Error type | Strategy |
| :--- | :--- |
| Transient (network timeout, 503) | Let Conductor retry with backoff. |
| Client error (400, validation failure) | Return `FAILED_WITH_TERMINAL_ERROR`. |
| Partial failure in batch | Return partial results as output; use workflow logic to handle remainder. |

### Compensation and saga patterns

For workflows that span multiple services, design compensation tasks to undo completed steps when a later step fails.

**Forward compensation** — Fix the problem and continue. Use a [SWITCH](../documentation/configuration/workflowdef/operators/switch-task.md) after the failed task to route to a recovery path.

**Backward compensation** — Undo completed work in reverse order. Model this as a separate workflow triggered by the [failure workflow](how-tos/Workflows/handling-errors.md) mechanism:

1. The main workflow fails at step 3.
2. Conductor invokes the configured `failureWorkflow`.
3. The failure workflow runs compensating tasks: undo step 2, then undo step 1.

!!! tip
    Store compensation metadata (transaction IDs, resource handles) in each task's output so the failure workflow has everything it needs to roll back.


## Versioning and deployments

Conductor supports [workflow versioning](how-tos/Workflows/versioning-workflows.md) natively. Use this for safe deployments.

### Blue-green with versions

1. Deploy workflow version N+1 with your changes.
2. Start new executions on version N+1.
3. Let existing version N executions drain to completion.
4. Once all version N executions are complete, deprecate or remove it.

### Migrating running executions

Running workflows continue on the version they were started with. You cannot migrate a running execution to a new version. Plan for this:

- **Short-lived workflows** — Wait for drain. Most complete within minutes.
- **Long-running workflows** — If a critical fix is needed, terminate and restart on the new version. Use the [Terminate](../documentation/configuration/workflowdef/operators/terminate-task.md) API with a reason, then re-trigger.

### Safe rollback

If version N+1 has issues:

1. Stop starting new executions on N+1 (route traffic back to N).
2. Let N+1 executions fail or terminate them.
3. Resume on version N, which was never modified.

Because workers are decoupled from workflow definitions, you can roll back the workflow version independently of worker deployments.


## Monitoring

Track these metrics to maintain healthy Conductor operations:

| Metric | What it tells you | Alert threshold |
| :--- | :--- | :--- |
| Task queue depth | Backlog of unprocessed tasks. | Growing consistently over 5 minutes. |
| Task poll count (per task type) | Whether workers are actively polling. | Drops to zero. |
| Workflow failure rate | Percentage of workflows ending in FAILED state. | > 5% over a 15-minute window. |
| Task response time (p99) | How close workers are to the response timeout. | > 80% of `responseTimeoutSeconds`. |
| Worker thread utilization | Whether workers are saturated. | > 90% sustained for 10 minutes. |
| External payload storage errors | S3/GCS write failures blocking tasks. | Any non-zero count. |

See [Monitoring and Scaling Workers](how-tos/Workers/scaling-workers.md) for built-in monitoring tools.
