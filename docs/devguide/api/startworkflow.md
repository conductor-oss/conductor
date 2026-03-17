---
description: "Start Conductor workflow executions — asynchronous, synchronous, and dynamic workflow execution via REST API with curl examples."
---

# Start Workflow API

## Start a Workflow (Asynchronous)

```
POST /api/workflow
```

Starts a new workflow execution asynchronously. Returns the workflow ID immediately.

### Request Body

| Field | Description | Required |
|---|---|---|
| `name` | Workflow name (must be registered) | Yes |
| `version` | Workflow version | No (defaults to latest) |
| `input` | JSON object with input parameters for the workflow | No |
| `correlationId` | Unique ID to correlate multiple workflow executions | No |
| `taskToDomain` | Task-to-domain mapping. See [Task Domains](taskdomains.md). | No |
| `workflowDef` | Inline [Workflow Definition](../../documentation/configuration/workflowdef/index.md) for dynamic workflows. See [Dynamic Workflows](#dynamic-workflows). | No |
| `externalInputPayloadStoragePath` | Path to external payload storage. See [External Payload Storage](../advanced/externalpayloadstorage.md). | No |
| `priority` | Priority level (0–99) for tasks within this workflow | No |

### Example

```shell
curl -X POST 'http://localhost:8080/api/workflow' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "myWorkflow",
    "version": 1,
    "correlationId": "order-123",
    "priority": 1,
    "input": {
      "customerId": "CUST-456",
      "amount": 99.99
    },
    "taskToDomain": {
      "*": "mydomain"
    }
  }'
```

**Response** `200 OK` — returns the workflow ID as plain text:

```
3a5b8c2d-1234-5678-9abc-def012345678
```

### Start with Path Parameters

```
POST /api/workflow/{name}
```

Alternative way to start a workflow — specify the name in the path and pass input as the request body.

| Parameter | Type | Description | Required |
|---|---|---|---|
| `name` | Path | Workflow name | Yes |
| `version` | Query | Workflow version | No |
| `correlationId` | Query | Correlation ID | No |
| `priority` | Query | Priority 0–99 (default: 0) | No |

```shell
curl -X POST 'http://localhost:8080/api/workflow/myWorkflow?version=1&correlationId=order-123' \
  -H 'Content-Type: application/json' \
  -d '{"customerId": "CUST-456", "amount": 99.99}'
```

**Response** `200 OK` — returns the workflow ID as plain text.

---

## Execute a Workflow (Synchronous)

```
POST /api/workflow/execute/{name}/{version}
```

Starts a workflow and **waits for completion** (or a specified condition) before returning the result. This eliminates the need to poll for workflow status.

| Parameter | Type | Description | Required |
|---|---|---|---|
| `name` | Path | Workflow name | Yes |
| `version` | Path | Workflow version (use `0` for latest) | Yes |
| `requestId` | Query | Idempotency key | No (auto-generated) |
| `waitUntilTaskRef` | Query | Comma-separated task reference names to wait for | No |
| `waitForSeconds` | Query | Maximum wait time in seconds | No (default: 10) |
| `consistency` | Query | `DURABLE` or `EVENTUAL` | No (default: `DURABLE`) |
| `returnStrategy` | Query | Controls which workflow state is returned | No (default: `TARGET_WORKFLOW`) |

Request body: a StartWorkflowRequest object (same format as the [async start](#start-a-workflow-asynchronous)).

### Example

```shell
curl -X POST 'http://localhost:8080/api/workflow/execute/my_workflow/1?waitForSeconds=30' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "my_workflow",
    "version": 1,
    "input": {
      "url": "https://api.example.com/data"
    }
  }'
```

**Response** `200 OK` — returns the workflow execution result:

```json
{
  "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
  "requestId": "req-uuid",
  "status": "COMPLETED",
  "output": {
    "response": {...}
  },
  "tasks": [...]
}
```

### Wait Behavior

- If `waitUntilTaskRef` is specified, the API returns when any listed task reaches a terminal state (or a WAIT task is encountered)
- If the workflow completes before the timeout, the result is returned immediately
- If the timeout is reached, the current workflow state is returned — the workflow continues running in the background
- Sub-workflow WAIT tasks are detected recursively

---

## Dynamic Workflows

Start a one-time workflow without pre-registering its definition. Provide the full workflow definition inline via the `workflowDef` field.

```shell
curl -X POST 'http://localhost:8080/api/workflow' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "my_adhoc_workflow",
    "workflowDef": {
      "ownerApp": "my_app",
      "ownerEmail": "owner@example.com",
      "name": "my_adhoc_workflow",
      "version": 1,
      "tasks": [
        {
          "name": "fetch_data",
          "type": "HTTP",
          "taskReferenceName": "fetch_data",
          "inputParameters": {
            "uri": "${workflow.input.uri}",
            "method": "GET"
          },
          "taskDefinition": {
            "name": "fetch_data",
            "retryCount": 0,
            "timeoutSeconds": 3600,
            "timeoutPolicy": "TIME_OUT_WF",
            "responseTimeoutSeconds": 3000
          }
        }
      ]
    },
    "input": {
      "uri": "https://api.example.com/data"
    }
  }'
```

**Response** `200 OK` — returns the workflow ID as plain text.

!!! note
    If a `taskDefinition` is already registered via the Metadata API, it does not need to be included inline in the dynamic workflow definition.
