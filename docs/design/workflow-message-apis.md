---
description: Proposal for workflow-native queries, signals, updates, and with-start APIs in Conductor.
---

# Workflow Message APIs Proposal

## Summary

Conductor already has three useful building blocks for workflow-native messaging:

- synchronous workflow start with `returnStrategy`
- task-by-reference updates for `WAIT` and `HUMAN`
- persisted workflow variables and schema metadata

What it does not have today is a workflow-defined message contract. Clients still need to know task reference names, and there is no first-class notion of:

- a named workflow query
- a named workflow signal
- a named workflow update
- signal-with-start / update-with-start

This proposal adds those four capabilities without changing Conductor's JSON-first model. The recommended v1 keeps handler execution declarative and local to workflow state mutation. It does not try to introduce a second workflow programming model.

## Current Surface Review

| Capability | Current surface | Gap |
|---|---|---|
| Query | `GET /api/workflow/search` and related endpoints expose index/search queries | This is search, not a workflow-defined read API |
| Signal | `POST /api/tasks/{workflowId}/{taskRefName}/{status}` updates a pending task by ref name | Client must know internal task refs and task status semantics |
| Sync signal-like behavior | `POST /api/tasks/{workflowId}/{taskRefName}/{status}/sync` returns the workflow after task update | Still task-centric; no named handler or validation |
| Sync start | `POST /api/workflow/execute/{name}/{version}` supports waiting and `returnStrategy` | Waiting logic is coupled to workflow start, not reusable message handling |
| Update | No named workflow-native update with validation and return value | Missing |
| With-start | No "find running or start new, then deliver message" endpoint | Missing |

## Design Goals

- Add workflow-native message contracts without breaking the existing task APIs.
- Keep the contract JSON-native and versioned with the workflow definition.
- Reuse existing primitives where possible: `SchemaDef`, workflow variables, `ParametersUtils`, evaluator types, and `NotificationResult`.
- Keep v1 implementable in OSS without requiring a deterministic code runtime.
- Make the client contract stable even when the internal workflow graph changes.

## Recommended v1 Scope

The recommended v1 is intentionally narrow:

- Queries are read-only projections over persisted workflow state.
- Signals are asynchronous state transitions defined by the workflow.
- Updates are synchronous state transitions defined by the workflow, with validation and a return value.
- Signal/update handlers may mutate workflow variables and complete or fail pending tasks in the addressed workflow.
- Handler execution runs under the workflow lock and completes within a single decision cycle.

The recommended v1 does **not** include:

- arbitrary long-running update executions that span waits, activities, or subworkflow boundaries
- recursive delivery into a blocking subworkflow hierarchy
- per-handler auth policies
- durable dedupe / update-history storage beyond what already exists for workflow start
- non-JSON schema enforcement for handler requests and responses

Those can be layered on after the workflow-native contract exists.

## Workflow Definition Extension

Add three optional top-level sections to `WorkflowDef`:

```json
{
  "name": "order_processing",
  "version": 1,
  "schemaVersion": 2,
  "queries": {
    "order_view": {
      "description": "Return the current customer-visible order state",
      "inputSchema": {
        "name": "order_view_query",
        "version": 1,
        "type": "JSON",
        "data": {
          "type": "object",
          "properties": {}
        }
      },
      "outputSchema": {
        "name": "order_view_response",
        "version": 1,
        "type": "JSON",
        "data": {
          "type": "object"
        }
      },
      "responseTemplate": {
        "workflowId": "${workflow.workflowId}",
        "status": "${workflow.status}",
        "paymentStatus": "${workflow.variables.paymentStatus}",
        "approvalTaskStatus": "${wait_for_approval.status}"
      }
    }
  },
  "signals": {
    "payment_received": {
      "description": "Apply a payment event to the running workflow",
      "inputSchema": {
        "name": "payment_received_signal",
        "version": 1,
        "type": "JSON",
        "data": {
          "type": "object",
          "required": ["paymentId", "amount"]
        }
      },
      "actions": [
        {
          "type": "MERGE_VARIABLES",
          "variables": {
            "paymentStatus": "RECEIVED",
            "paymentId": "${request.input.paymentId}",
            "paidAmount": "${request.input.amount}"
          }
        },
        {
          "type": "COMPLETE_TASK",
          "taskRefName": "wait_for_payment",
          "status": "COMPLETED",
          "output": {
            "paymentId": "${request.input.paymentId}",
            "amount": "${request.input.amount}"
          }
        }
      ]
    }
  },
  "updates": {
    "change_shipping_address": {
      "description": "Change the shipping address before fulfillment",
      "inputSchema": {
        "name": "change_shipping_address_request",
        "version": 1,
        "type": "JSON",
        "data": {
          "type": "object",
          "required": ["address"]
        }
      },
      "outputSchema": {
        "name": "change_shipping_address_response",
        "version": 1,
        "type": "JSON",
        "data": {
          "type": "object"
        }
      },
      "validator": {
        "evaluatorType": "javascript",
        "condition": "$.workflow.status === 'RUNNING' && $.workflow.variables.fulfillmentState !== 'SHIPPED'",
        "message": "Address can only be changed before shipment"
      },
      "actions": [
        {
          "type": "MERGE_VARIABLES",
          "variables": {
            "shippingAddress": "${request.input.address}",
            "addressVersion": "${request.input.version}"
          }
        }
      ],
      "responseTemplate": {
        "workflowId": "${workflow.workflowId}",
        "shippingAddress": "${workflow.variables.shippingAddress}",
        "addressVersion": "${workflow.variables.addressVersion}",
        "status": "${workflow.status}"
      }
    }
  }
}
```

### Common handler fields

| Field | Applies to | Meaning |
|---|---|---|
| `description` | all | Human-readable description |
| `inputSchema` | all | Request schema using existing `SchemaDef` (v1 should enforce `type=JSON` only) |
| `outputSchema` | queries, updates | Response schema using existing `SchemaDef` (v1 should enforce `type=JSON` only) |
| `metadata` | all | Optional free-form metadata |

### Query fields

| Field | Meaning |
|---|---|
| `responseTemplate` | JSON template resolved against the workflow state and request |

### Signal fields

| Field | Meaning |
|---|---|
| `validator` | Optional precondition checked before actions are applied |
| `actions` | Ordered list of workflow-state actions |

### Update fields

| Field | Meaning |
|---|---|
| `validator` | Optional precondition checked before actions are applied |
| `actions` | Ordered list of workflow-state actions |
| `responseTemplate` | JSON template resolved after actions and decide complete |

## Action Model

V1 should keep actions deliberately small:

| Action type | Semantics |
|---|---|
| `MERGE_VARIABLES` | Shallow merge key/value pairs into `workflow.variables` |
| `COMPLETE_TASK` | Complete the first pending task matching `taskRefName` |
| `FAIL_TASK` | Fail the first pending task matching `taskRefName` |
| `TERMINATE_WORKFLOW` | Terminate the workflow with a reason |

### Action notes

- `MERGE_VARIABLES` should follow the same shallow key replacement semantics as `SET_VARIABLE`.
- `COMPLETE_TASK` and `FAIL_TASK` should reuse the existing "pending task by workflow + ref name" resolution behavior.
- Each action input should be resolved via `ParametersUtils` against a message context containing:
  - `workflow`
  - `request`
  - task refs already exposed by the workflow input-resolution model

## REST API Proposal

### Queries

```
POST /api/workflow/{workflowId}/queries/{queryName}
```

Request:

```json
{
  "input": {}
}
```

Response:

```json
{
  "workflowId": "abc123",
  "status": "RUNNING",
  "paymentStatus": "RECEIVED"
}
```

Behavior:

- Load the workflow with tasks.
- Validate `input` against `inputSchema` when present.
- Resolve `responseTemplate` against the workflow state and request input.
- Validate the response against `outputSchema` when present.
- Return `200 OK`.

### Signals

```
POST /api/workflow/{workflowId}/signals/{signalName}
POST /api/workflow/{workflowId}/signals/{signalName}/sync?waitForSeconds=5&returnStrategy=TARGET_WORKFLOW
```

Request:

```json
{
  "requestId": "sig-001",
  "input": {
    "paymentId": "pay_123",
    "amount": 149.99
  }
}
```

Async response:

```json
{
  "accepted": true,
  "workflowId": "abc123",
  "signalName": "payment_received",
  "requestId": "sig-001"
}
```

Sync response:

- Reuse the existing `SignalResponse` model and `returnStrategy` enum.

Behavior:

- Load the workflow with tasks.
- Resolve the named signal handler from the workflow definition snapshot.
- Validate request input.
- Evaluate the optional validator against the workflow + request context.
- Apply actions under the workflow lock.
- Run `decide`.
- For `/sync`, reuse the same blocking-task detection and `NotificationResult` conversion already used by synchronous workflow start.

### Updates

```
POST /api/workflow/{workflowId}/updates/{updateName}
```

Request:

```json
{
  "requestId": "upd-001",
  "input": {
    "address": {
      "line1": "1 Main St",
      "city": "Los Angeles",
      "state": "CA",
      "zip": "90001"
    },
    "version": 3
  }
}
```

Response:

```json
{
  "workflowId": "abc123",
  "updateName": "change_shipping_address",
  "requestId": "upd-001",
  "state": "COMPLETED",
  "result": {
    "workflowId": "abc123",
    "shippingAddress": {
      "line1": "1 Main St",
      "city": "Los Angeles",
      "state": "CA",
      "zip": "90001"
    },
    "addressVersion": 3,
    "status": "RUNNING"
  }
}
```

Behavior:

- Load the workflow with tasks.
- Resolve the named update handler from the workflow definition snapshot.
- Validate request input.
- Evaluate the validator before any mutation.
- Apply actions under the workflow lock.
- Run `decide`.
- Resolve `responseTemplate` against the updated workflow state.
- Validate the result against `outputSchema`.
- Return `200 OK`.

### Signal With Start

```
POST /api/workflow/{workflowName}/signals/{signalName}/with-start?version=1
```

Request:

```json
{
  "correlationId": "order-123",
  "startWorkflow": {
    "input": {
      "orderId": "order-123"
    },
    "priority": 0,
    "createdBy": "api"
  },
  "signal": {
    "requestId": "sig-001",
    "input": {
      "paymentId": "pay_123",
      "amount": 149.99
    }
  }
}
```

Response:

```json
{
  "workflowId": "abc123",
  "startedNew": false,
  "signalName": "payment_received",
  "requestId": "sig-001"
}
```

Behavior:

- Require `correlationId` in v1.
- Look up running workflows by `{workflowName, correlationId}`.
- If zero running matches exist, start a new workflow using `startWorkflow`.
- If exactly one running match exists, deliver the signal there.
- If more than one running match exists, return `409 Conflict`.

### Update With Start

```
POST /api/workflow/{workflowName}/updates/{updateName}/with-start?version=1
```

Request shape:

- Same as signal-with-start, but the `update` object replaces the `signal` object.

Behavior:

- Same lookup/start semantics as signal-with-start.
- Deliver the update to the resolved or newly started workflow.
- Return the update result envelope.

## Error Semantics

| Condition | Status |
|---|---|
| Workflow or handler not found | `404` |
| Invalid input schema | `400` |
| Validator rejected the request | `409` |
| Multiple running workflows matched `with-start` lookup | `409` |
| Handler output failed schema validation | `500` in v1, tighten to `409` or `422` later if desired |

## Backend Changes

### 1. New workflow metadata models

Add new metadata classes under `common`:

- `WorkflowQueryDef`
- `WorkflowSignalDef`
- `WorkflowUpdateDef`
- `WorkflowMessageValidatorDef`
- `WorkflowMessageActionDef`

Then extend `WorkflowDef` with:

- `Map<String, WorkflowQueryDef> queries`
- `Map<String, WorkflowSignalDef> signals`
- `Map<String, WorkflowUpdateDef> updates`

### 2. New REST controller

Add `WorkflowMessageResource` in `rest` for:

- query routes
- signal routes
- update routes
- with-start routes

This keeps the new API surface separate from `WorkflowResource` and `TaskResource`.

### 3. New service layer

Add `WorkflowMessageService` in `core` and a default implementation that:

- loads the addressed workflow and definition snapshot
- validates schemas
- evaluates validators
- applies actions
- runs `decide`
- resolves response templates

This service should depend on:

- `WorkflowService` / `WorkflowExecutor`
- `ExecutionService`
- `ParametersUtils`
- `JsonSchemaValidator`
- the existing evaluator registry already used for event handler conditions

### 4. Extract reusable wait logic

The current synchronous wait implementation lives directly in `WorkflowResource.executeWorkflow`.
That logic should be extracted into a reusable component, for example `WorkflowWaitService`, so signal sync and future update sync behavior can reuse:

- `waitForSeconds`
- blocking task detection
- `NotificationResult`
- `WorkflowSignalReturnStrategy`

### 5. Reuse current task mutation path

`COMPLETE_TASK` and `FAIL_TASK` should reuse the same pending-task lookup and task update flow already behind task-by-reference updates. That keeps message handlers aligned with current WAIT/HUMAN semantics.

### 6. Finish the existing variable-update gap

Conductor already models `update_workflow_variables` as an event action type, but the default action processor does not implement it yet. That should be finished anyway because the same primitive is useful for workflow-native signals and updates.

## Concrete Backend Gaps

These are the specific gaps between the current codebase and the proposed API:

1. `WorkflowDef` has schemas and variables, but no handler metadata for queries, signals, or updates.
2. `WorkflowResource` exposes sync start, lifecycle, and search, but no workflow-native message routes.
3. `TaskResource` exposes task-by-reference updates, but those are low-level task operations, not named workflow handlers.
4. The sync waiting and `returnStrategy` logic is only wired into workflow start.
5. There is no service abstraction for message evaluation, validation, or action application.
6. There is no read-only query projection mechanism beyond returning the raw workflow.
7. `update_workflow_variables` exists in the event action model but is not implemented in the default action processor.

## Recommended Rollout

### Phase 1

- Add metadata model for `queries`, `signals`, and `updates`
- Add query endpoint
- Add async signal endpoint
- Add synchronous signal endpoint that reuses `SignalResponse`
- Implement `MERGE_VARIABLES`, `COMPLETE_TASK`, `FAIL_TASK`
- Extract `WorkflowWaitService`

### Phase 2

- Add update endpoint with validation and result templating
- Add correlation-ID-based signal-with-start
- Add correlation-ID-based update-with-start

### Phase 3

- Add durable handler idempotency / dedupe store
- Add per-handler auth
- Add long-running update executions if needed
- Add subworkflow-aware delivery policies

## Why This Shape

This proposal keeps Conductor's advantage intact:

- handlers are declared in JSON with the workflow
- runtime workflows still work
- the client contract becomes workflow-native instead of task-native
- the engine stays polyglot and durable without introducing a code-only programming model

At the same time, it closes the biggest comparison gap with systems that expose named workflow messages:

- read-only query handlers
- async signal handlers
- validated update handlers with return values
- with-start delivery
