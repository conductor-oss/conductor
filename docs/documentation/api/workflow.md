---
description: "Conductor Workflow API — manage workflow executions including pause, resume, retry, restart, rerun, terminate, search, and test workflows via REST."
---

# Workflow API

The Workflow API manages workflow executions. All endpoints use the base path `/api/workflow`.

For starting workflows, see [Start Workflow API](startworkflow.md).

## Retrieve Workflows

| Endpoint | Method | Description |
|---|---|---|
| `/{workflowId}` | `GET` | Get workflow execution by ID |
| `/{workflowId}/tasks` | `GET` | Get tasks for a workflow execution (paginated) |
| `/running/{name}` | `GET` | Get running workflow IDs by type |
| `/{name}/correlated/{correlationId}` | `GET` | Get workflows by correlation ID |
| `/{name}/correlated` | `POST` | Get workflows for multiple correlation IDs |

### Get Workflow by ID

```
GET /api/workflow/{workflowId}?includeTasks=true
```

| Parameter | Description | Default |
|---|---|---|
| `workflowId` | Workflow execution ID | — |
| `includeTasks` | Include task details in response | `true` |

```shell
curl 'http://localhost:8080/api/workflow/3a5b8c2d-1234-5678-9abc-def012345678'
```

**Response** `200 OK`

```json
{
  "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
  "workflowName": "order_processing",
  "workflowVersion": 1,
  "status": "COMPLETED",
  "startTime": 1700000000000,
  "endTime": 1700000005000,
  "input": {"orderId": "ORD-123"},
  "output": {"paymentId": "PAY-456"},
  "tasks": [
    {
      "taskId": "task-uuid",
      "taskType": "HTTP",
      "referenceTaskName": "validate",
      "status": "COMPLETED",
      "outputData": {"response": {"statusCode": 200}}
    }
  ],
  "correlationId": "order-123"
}
```

### Get Tasks for a Workflow

```
GET /api/workflow/{workflowId}/tasks?start=0&count=15&status=
```

Returns a paginated list of tasks for a workflow execution.

| Parameter | Description | Default |
|---|---|---|
| `start` | Page offset | `0` |
| `count` | Number of results | `15` |
| `status` | Filter by task status (can specify multiple) | All statuses |

```shell
# Get first 10 tasks
curl 'http://localhost:8080/api/workflow/3a5b8c2d.../tasks?count=10'

# Get only failed tasks
curl 'http://localhost:8080/api/workflow/3a5b8c2d.../tasks?status=FAILED'
```

**Response** `200 OK`

```json
{
  "totalHits": 5,
  "results": [
    {
      "taskId": "task-uuid",
      "taskType": "HTTP",
      "referenceTaskName": "validate",
      "status": "COMPLETED"
    }
  ]
}
```

### Get Running Workflows

```
GET /api/workflow/running/{name}?version=1&startTime=&endTime=
```

Returns a list of workflow IDs for running workflows of the given type.

| Parameter | Description | Default |
|---|---|---|
| `name` | Workflow name | — |
| `version` | Workflow version | `1` |
| `startTime` | Filter by start time (epoch ms) | — |
| `endTime` | Filter by end time (epoch ms) | — |

```shell
curl 'http://localhost:8080/api/workflow/running/order_processing?version=1'
```

**Response** `200 OK`

```json
["3a5b8c2d-1234-...", "7f8e9d0c-5678-..."]
```

### Get Workflows by Correlation ID

```
GET /api/workflow/{name}/correlated/{correlationId}?includeClosed=false&includeTasks=false
```

| Parameter | Description | Default |
|---|---|---|
| `includeClosed` | Include completed/terminated workflows | `false` |
| `includeTasks` | Include task details | `false` |

```shell
curl 'http://localhost:8080/api/workflow/order_processing/correlated/order-123?includeClosed=true'
```

### Get Workflows for Multiple Correlation IDs

```
POST /api/workflow/{name}/correlated?includeClosed=false&includeTasks=false
```

```shell
curl -X POST 'http://localhost:8080/api/workflow/order_processing/correlated?includeClosed=true' \
  -H 'Content-Type: application/json' \
  -d '["order-123", "order-456", "order-789"]'
```

**Response** `200 OK` — a map of correlation ID to list of workflows.

---

## Manage Workflows

| Endpoint | Method | Description |
|---|---|---|
| `/{workflowId}/pause` | `PUT` | Pause a workflow |
| `/{workflowId}/resume` | `PUT` | Resume a paused workflow |
| `/{workflowId}/restart` | `POST` | Restart a completed workflow from the beginning |
| `/{workflowId}/retry` | `POST` | Retry the last failed task |
| `/{workflowId}/rerun` | `POST` | Rerun from a specific task |
| `/{workflowId}/skiptask/{taskReferenceName}` | `PUT` | Skip a task in a running workflow |
| `/{workflowId}/resetcallbacks` | `POST` | Reset callback times for SIMPLE tasks |
| `/decide/{workflowId}` | `PUT` | Trigger the decider for a workflow |
| `/{workflowId}` | `DELETE` | Terminate a running workflow |
| `/{workflowId}/remove` | `DELETE` | Remove a workflow from the system |
| `/{workflowId}/terminate-remove` | `DELETE` | Terminate and remove in one call |

### Pause

```
PUT /api/workflow/{workflowId}/pause
```

Pauses the workflow. No further tasks will be scheduled until resumed. Currently running tasks are **not** affected.

```shell
curl -X PUT 'http://localhost:8080/api/workflow/3a5b8c2d.../pause'
```

### Resume

```
PUT /api/workflow/{workflowId}/resume
```

```shell
curl -X PUT 'http://localhost:8080/api/workflow/3a5b8c2d.../resume'
```

### Restart

```
POST /api/workflow/{workflowId}/restart?useLatestDefinitions=false
```

Restarts a completed workflow from the beginning. Current execution history is wiped out.

| Parameter | Description | Default |
|---|---|---|
| `useLatestDefinitions` | Use latest workflow and task definitions | `false` |

```shell
curl -X POST 'http://localhost:8080/api/workflow/3a5b8c2d.../restart'
```

### Retry

```
POST /api/workflow/{workflowId}/retry?resumeSubworkflowTasks=false
```

Retries the last failed task in the workflow.

| Parameter | Description | Default |
|---|---|---|
| `resumeSubworkflowTasks` | Also resume failed sub-workflow tasks | `false` |

```shell
curl -X POST 'http://localhost:8080/api/workflow/3a5b8c2d.../retry'
```

### Rerun

```
POST /api/workflow/{workflowId}/rerun
```

Re-runs a completed workflow from a specific task.

```shell
curl -X POST 'http://localhost:8080/api/workflow/3a5b8c2d.../rerun' \
  -H 'Content-Type: application/json' \
  -d '{
    "reRunFromWorkflowId": "3a5b8c2d...",
    "workflowInput": {"orderId": "ORD-999"},
    "reRunFromTaskId": "task-uuid",
    "taskInput": {"override": true}
  }'
```

### Skip Task

```
PUT /api/workflow/{workflowId}/skiptask/{taskReferenceName}
```

Skips a task in a running workflow and continues forward. Optionally provide updated input/output:

```shell
curl -X PUT 'http://localhost:8080/api/workflow/3a5b8c2d.../skiptask/validate_ref' \
  -H 'Content-Type: application/json' \
  -d '{
    "taskInput": {},
    "taskOutput": {"skipped": true, "reason": "manual override"}
  }'
```

### Reset Callbacks

```
POST /api/workflow/{workflowId}/resetcallbacks
```

Resets callback times of all non-terminal SIMPLE tasks to 0, causing them to be re-evaluated immediately.

### Decide

```
PUT /api/workflow/decide/{workflowId}
```

Manually triggers the decider for a workflow. The decider evaluates workflow state and schedules the next tasks. Normally automatic — use this for debugging.

### Terminate

```
DELETE /api/workflow/{workflowId}?reason=
```

| Parameter | Description | Required |
|---|---|---|
| `reason` | Reason for termination | No |

```shell
curl -X DELETE 'http://localhost:8080/api/workflow/3a5b8c2d...?reason=cancelled+by+user'
```

### Remove

```
DELETE /api/workflow/{workflowId}/remove?archiveWorkflow=true
```

| Parameter | Description | Default |
|---|---|---|
| `archiveWorkflow` | Archive before removing | `true` |

!!! warning
    This permanently removes the workflow execution data. Use with caution.

### Terminate and Remove

```
DELETE /api/workflow/{workflowId}/terminate-remove?reason=&archiveWorkflow=true
```

Terminates a running workflow and removes it from the system in one call.

---

## Search Workflows

All search endpoints support the same query parameters:

| Parameter | Description | Default |
|---|---|---|
| `start` | Page offset | `0` |
| `size` | Number of results | `100` |
| `sort` | Sort order: `<field>:ASC` or `<field>:DESC` | — |
| `freeText` | Full-text search query | `*` |
| `query` | SQL-like where clause | — |

### Search (Summary)

```
GET /api/workflow/search?start=0&size=100&sort=&freeText=&query=
```

Returns `SearchResult<WorkflowSummary>` — lightweight results without full workflow details.

```shell
# Find completed workflows of a specific type
curl 'http://localhost:8080/api/workflow/search?query=workflowType%3D%27order_processing%27+AND+status%3D%27COMPLETED%27&size=10'

# Free-text search
curl 'http://localhost:8080/api/workflow/search?freeText=order-123'
```

**Response** `200 OK`

```json
{
  "totalHits": 42,
  "results": [
    {
      "workflowType": "order_processing",
      "version": 1,
      "workflowId": "3a5b8c2d...",
      "correlationId": "order-123",
      "startTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:30:05Z",
      "endTime": "2024-01-15T10:30:05Z",
      "status": "COMPLETED",
      "executionTime": 5000
    }
  ]
}
```

### Search V2 (Full)

```
GET /api/workflow/search-v2
```

Same parameters as search, but returns `SearchResult<Workflow>` — full workflow objects including task details.

### Search by Tasks

```
GET /api/workflow/search-by-tasks
```

Search for workflows based on task-level parameters. Returns `SearchResult<WorkflowSummary>`.

### Search by Tasks V2

```
GET /api/workflow/search-by-tasks-v2
```

Returns `SearchResult<Workflow>` with full workflow objects.

### Query Syntax

The `query` parameter supports SQL-like expressions:

| Example | Description |
|---|---|
| `workflowType = 'order_processing'` | Filter by workflow type |
| `status = 'FAILED'` | Filter by status |
| `startTime > 1700000000000` | Filter by start time (epoch ms) |
| `workflowType = 'order_processing' AND status = 'COMPLETED'` | Combine conditions |

The `freeText` parameter supports Elasticsearch query syntax:

| Example | Description |
|---|---|
| `workflowType:"order_processing"` | Match workflow type |
| `order-123` | Match any field |

---

## Test Workflow

```
POST /api/workflow/test
```

Test a workflow execution using mock data without actually running it. Useful for validating workflow definitions and task wiring before deployment.

```shell
curl -X POST 'http://localhost:8080/api/workflow/test' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "my_workflow",
    "version": 1,
    "workflowDef": {...},
    "taskRefToMockOutput": {
      "my_task_ref": {
        "key": "mocked_value"
      }
    }
  }'
```

**Response** `200 OK` — returns the simulated workflow execution with mocked task outputs.

---

## External Storage

```
GET /api/workflow/externalstoragelocation?path=&operation=&payloadType=
```

Get the URI for external payload storage. See [External Payload Storage](../advanced/externalpayloadstorage.md).
