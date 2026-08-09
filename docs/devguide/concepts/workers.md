---
description: "Learn about workers in Conductor — the code that executes tasks in workflows, written in any language and hosted anywhere you choose."
---

# Workers

<section class="concept-hero concept-hero--workers">
  <div class="concept-hero__content">
    <p class="concept-hero__eyebrow">Your code, durable orchestration</p>
    <h2>Poll, execute, and report without owning workflow state.</h2>
    <p>Workers implement business logic in your preferred language while Conductor handles dispatch, retries, timeouts, and the durable task record.</p>
  </div>
  <svg class="concept-hero__graphic" viewBox="0 0 440 190" role="img" aria-label="Conductor queues a task for a worker, which executes it and reports a result">
    <defs><marker id="worker-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="currentColor" /></marker></defs>
    <rect x="14" y="68" width="102" height="54" rx="10" class="concept-hero__node concept-hero__node--accent" />
    <text x="65" y="91" text-anchor="middle" class="concept-hero__label">Conductor</text>
    <text x="65" y="108" text-anchor="middle" class="concept-hero__detail">dispatch</text>
    <path d="M116 95 H161" class="concept-hero__line" marker-end="url(#worker-arrow)" />
    <rect x="169" y="68" width="99" height="54" rx="10" class="concept-hero__node" />
    <text x="218" y="91" text-anchor="middle" class="concept-hero__label">Task queue</text>
    <text x="218" y="108" text-anchor="middle" class="concept-hero__detail">poll</text>
    <path d="M268 95 H311" class="concept-hero__line" marker-end="url(#worker-arrow)" />
    <rect x="319" y="36" width="106" height="54" rx="10" class="concept-hero__node" />
    <text x="372" y="59" text-anchor="middle" class="concept-hero__label">Worker</text>
    <text x="372" y="76" text-anchor="middle" class="concept-hero__detail">execute</text>
    <path d="M372 90 V139 H268" class="concept-hero__line" marker-end="url(#worker-arrow)" />
    <rect x="169" y="123" width="99" height="42" rx="10" class="concept-hero__outcome-box" />
    <text x="218" y="149" text-anchor="middle" class="concept-hero__label">Result</text>
  </svg>
</section>

A worker is responsible for executing a task in a workflow. Each type of worker implements the core functionality of each task, handling the logic as defined in its code.

System task workers are managed by Conductor within its JVM, while `SIMPLE` task workers are to be implemented by yourself. These workers can be implemented in any programming language of your choice (Python, Java, JavaScript, C#, Go, and Clojure) and hosted anywhere outside the Conductor environment.

!!! Note
    Conductor provides a set of worker frameworks in its SDKs. These frameworks come with comes with features like polling threads, metrics, and server communication, making it easy to create custom workers.

These workers communicate with the Conductor server via REST/gRPC, allowing them to poll for tasks and update the task status. Learn more in [Architecture](../architecture/index.md).


## How workers work

1. **Poll** — The worker polls the Conductor server for tasks of a specific type.
2. **Execute** — The worker receives a task, executes the business logic, and produces an output.
3. **Report** — The worker reports the task result (COMPLETED or FAILED) back to the server.

Conductor handles scheduling, retries, and state persistence. Your worker just focuses on business logic.


## Worker configuration

Workers are configured through the task definition on the Conductor server. Key settings:

| Parameter | Description |
| :--- | :--- |
| `retryCount` | Number of times Conductor retries a failed task. |
| `retryDelaySeconds` | Delay between retries. |
| `responseTimeoutSeconds` | Max time for a worker to respond after polling. |
| `timeoutSeconds` | Overall SLA for task completion. |
| `pollTimeoutSeconds` | Max time for a worker to poll before timeout. |
| `rateLimitPerFrequency` | Max task executions per frequency window. |
| `concurrentExecLimit` | Max concurrent executions across all workers. |

See [Task Definitions](../../documentation/configuration/taskdef.md) for the full reference.


## Scaling task workers

Workers can be scaled independently of the Conductor server:

- **Horizontal scaling** — Run multiple instances of the same worker. Conductor distributes tasks across all polling workers automatically.
- **Rate limiting** — Use `rateLimitPerFrequency` to control throughput per task type.
- **Concurrency limits** — Use `concurrentExecLimit` to cap parallel executions.
- **Domain isolation** — Use [task domains](../../documentation/api/taskdomains.md) to route tasks to specific worker groups.

See [Scaling Workers](../how-tos/Workers/scaling-workers.md) for detailed guidance.
