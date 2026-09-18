---
description: "Take a Conductor workflow from a successful local run to a production service with explicit contracts, bounded failures, safe deployment, and an operating model."
---

# Production path for durable workflows

Use this guide after [your first workflow](../../quickstart/first-workflow.md). It turns a successful local run into a service with an explicit contract, bounded failure behavior, repeatable deployment, and an operating model.

## Outcome

You will have a workflow whose callers know its input and output contract, whose tasks have deliberate reliability settings, and whose operators know how to inspect and recover an execution.

## 1. Define the contract

Treat a workflow definition and its `outputParameters` as an API. Document required inputs, validate or reject invalid requests at the boundary, and keep outputs stable for callers. When a change is not backward compatible, register a new workflow version instead of changing an active definition in place.

Read [workflow definitions](../concepts/workflows.md), [task inputs](../how-tos/Tasks/task-inputs.md), and [workflow versioning](../how-tos/Workflows/versioning-workflows.md) before publishing a caller-facing workflow.

## 2. Make the failure policy explicit

For every external side effect, decide whether it is safe to retry and how it is made idempotent. Set task retry behavior and timeouts deliberately; use a failure workflow or compensation when a later failure requires business rollback. Bound the workflow itself when the business operation has a maximum acceptable duration.

Verify the design by forcing one transient task failure and confirming that the expected retry, timeout, or compensation path is visible in the execution.

Continue with [task timeouts and retries](../cookbook/task-timeouts-and-retries.md), [error handling](../how-tos/Workflows/handling-errors.md), and [best practices](../bestpractices.md).

## 3. Test the real boundaries

Test the registered definition with representative input, not only worker functions in isolation. Cover success, retryable failure, terminal business failure, timeout, and the idempotency behavior of each side effect. Use real dependencies or Testcontainers where practical so queue, persistence, and concurrency behavior is exercised.

**Verification:** start the workflow with a test correlation ID, inspect its full execution, and assert its output contract and terminal status.

## 4. Deploy definitions and workers safely

Deploy worker code and task definitions before routing production traffic to a workflow that needs them. Keep workers idempotent because Conductor delivery is at least once. Roll out a new workflow version, update callers deliberately, and retain the old version until its executions are drained.

Use [creating workflows](../how-tos/Workflows/creating-workflows.md), [scaling workers](../how-tos/Workers/scaling-workers.md), and [deployment](../running/deploy.md) for the implementation details.

## 5. Operate the execution

Give operators a workflow name, version, correlation-ID convention, and owner. Monitor queue depth, task failures, timeouts, and execution status. During an incident, inspect the failed task before retrying; retry only failures that are safe to repeat, then pause, resume, rerun, or terminate according to the business policy.

**Recovery drill:** intentionally leave a workflow waiting or fail a retryable task, then find it through [searching workflows](../how-tos/Workflows/searching-workflows.md) and recover it with the documented [debugging](../how-tos/Workflows/debugging-workflows.md) controls.

## Next production step

For platform-level deployment and storage choices, continue to [Deploy Conductor](../running/deploy.md) and [Durable Execution](../../architecture/durable-execution.md). For an AI workflow or agent, add the controls in [Production Agent Architecture](../ai/production-agent-architecture.md) to this workflow baseline.
