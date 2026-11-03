---
description: Validate workflow schemas, run mocked workflow tests, and verify real Conductor executions.
---

# Validate and test workflows

Use three layers. Schema validation catches an invalid definition, mocked workflow testing checks orchestration decisions, and a real execution verifies workers and integrations.

## Prerequisites

- A reachable Conductor server.
- A workflow definition saved as `workflow.json`.
- Real workers and external dependencies only for the final execution layer.

## 1. Validate the definition

Validation checks metadata and graph rules but does not prove that a worker is polling or an external endpoint is reachable.

```bash
curl -i -X POST 'http://localhost:8080/api/metadata/workflow/validate' \
  -H 'Content-Type: application/json' \
  --data-binary @workflow.json
```

Success is an empty `200 OK` response. Fix validation errors before registration.

## 2. Test orchestration with mocked tasks

`POST /api/workflow/test` executes the decision logic with task outputs supplied by reference name. Each reference maps to a list because loops or retries can consume multiple mocks.

```json
--8<-- "docs/devguide/cookbook/examples/workflow-test.json"
```

```bash
curl -sS -X POST 'http://localhost:8080/api/workflow/test' \
  -H 'Content-Type: application/json' \
  --data-binary @workflow-test.json
```

Success is a simulated execution whose task states and workflow output match the expected branch. `executionTime` and `queueWaitTime` on a mock can exercise timeout behavior. Nested `SUB_WORKFLOW` tests use `subWorkflowTestRequest`.

## 3. Run the real boundaries

Register the definition, start it, and inspect the returned workflow ID.

```bash
conductor workflow create workflow.json
conductor workflow start -w order_workflow -i '{"orderId":"order-123"}'
conductor workflow get-execution <workflow-id> -c
```

Success is a terminal status you expect and verified task output. A `SIMPLE` task without a registered task definition and polling worker remains queued; mock testing cannot detect that deployment gap.

## Limitations

Mock testing does not call workers, brokers, databases, or HTTP endpoints and cannot establish their authentication, latency, or retry behavior. Keep a real integration or smoke test for each production boundary.

Next, add reliability policies with [Reliability and error handling](handling-errors.md) and rehearse recovery with [Debug and recover](debugging-workflows.md).
