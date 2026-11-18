---
description: "Conductor Workflow API — manage workflow executions including pause, resume, retry, restart, rerun, terminate, search, and test workflows via REST."
---

# Workflow API

The Workflow API manages workflow executions. All endpoints use the base path `/api/workflow`.

Workflow responses are runtime objects; their detailed contract is [Workflow.json](../configuration/schemas.md#runtime-objects). The registered blueprint is [WorkflowDef.json](../configuration/schemas.md#definition-objects).

For starting workflows, see [Start Workflow API](startworkflow.md).

## Workflow Messages

`POST /api/workflow/{workflowId}/messages` pushes an arbitrary JSON object into a running workflow's message queue. This endpoint is available only when `conductor.workflow-message-queue.enabled=true`; when the feature is disabled, the controller is not registered and the endpoint returns `404 Not Found`.

```shell
curl -X POST 'http://localhost:8080/api/workflow/3a5b8c2d-1234-5678-9abc-def012345678/messages' \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello"}'
```

**Response** `200 OK` — plain-text generated message ID.

| Status | Condition |
|---|---|
| `404 Not Found` | The WMQ feature is disabled, or the workflow does not exist. |
| `409 Conflict` | The workflow is not `RUNNING`. |
| `429 Too Many Requests` | The workflow queue has reached `maxQueueSize`. |
| `503 Service Unavailable` | The workflow is busy: another evaluation holds its execution lock. The message was not queued; retry the push. |

Conductor pushes the message inside a workflow evaluation (decide) that holds the workflow's execution lock. The `RUNNING` check, the push, and the evaluation happen under that one lock, so a push cannot be acknowledged and then lost to a completion that commits in between: the workflow is either still `RUNNING` when the message is queued, or the push is refused with `409`. The same evaluation wakes a waiting `PULL_WORKFLOW_MESSAGES` task. Messages still queued when the workflow ends are discarded. See [Workflow Message Queue](../../wmq/workflow-message-queue.md) and [Pull Workflow Messages task](../configuration/workflowdef/systemtasks/pull-workflow-messages-task.md) for configuration and consumption.

## Retrieve Workflows

| Endpoint | Method | Description |
|---|---|---|
| `/{workflowId}` | `GET` | Get workflow execution by ID |
| `/{workflowId}/status` | `GET` | Get a lightweight workflow status summary |
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

**Status and failure fields**

| Field | Description |
|---|---|
| `status` | `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `TIMED_OUT`, or `TERMINATED`. |
| `reasonForIncompletion` | Why the workflow stopped without completing: the failing task's reason, a timeout message, or the reason passed to terminate. Empty while running; cleared by retry, restart, and rerun. See [Understanding reasonForIncompletion](../../devguide/how-tos/Workflows/debugging-workflows.md#understanding-reasonforincompletion). |
| `failedReferenceTaskNames` | Reference names of the tasks that failed. |
| `failedTaskNames` | Definition names of the tasks that failed. |
| `lastRetriedTime` | Epoch milliseconds of the most recent retry; `0` if never retried. |
| `reRunFromWorkflowId` | Set when this execution was rerun from another execution. |
| `parentWorkflowId`, `parentWorkflowTaskId` | Present on sub-workflows: the parent execution and its `SUB_WORKFLOW` task. |
| `event` | Name of the event that started the workflow, when started by an event handler. |

### Get Workflow Status Summary

```http
GET /api/workflow/{workflowId}/status?includeOutput=false&includeVariables=false
```

This endpoint returns `WorkflowStatus`, a lightweight summary. `includeOutput` and `includeVariables` both default to `false`; set either to `true` only when that data is required.

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

Restarts a workflow that has ended (any terminal status) from the beginning. Current execution history is wiped out. A workflow that has not ended is refused with `409 Conflict`; a `COMPLETED` workflow whose definition sets `restartable: false` is refused with `404 Not Found`. **Response** `204 No Content`.

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

Retries the last failed task in the workflow. Only a `FAILED`, `TIMED_OUT` or `TERMINATED` workflow can be retried.

| Parameter | Description | Default |
|---|---|---|
| `resumeSubworkflowTasks` | Also resume failed sub-workflow tasks | `false` |

```shell
curl -X POST 'http://localhost:8080/api/workflow/3a5b8c2d.../retry'
```

**Response** `204 No Content`.

| Status | Condition |
|---|---|
| `404 Not Found` | The workflow is still running (`Workflow is still running.  status=...`). |
| `409 Conflict` | The workflow is `COMPLETED` (`Workflow <id> is COMPLETED and cannot be retried; rerun or restart it instead`), or it has no tasks yet. |

A retry gives a failed workflow another chance. A `COMPLETED` workflow can still hold tasks that did not succeed, such as those cancelled by a `TERMINATE` task with `terminationStatus: COMPLETED`; retrying them would reopen a workflow that succeeded. Use [Rerun](#rerun) or [Restart](#restart) to run it again.

A retried task gets a fresh total time budget: the new attempt's `totalTimeoutSeconds` counts from when it is scheduled, not from the first attempt.

### Rerun

```
POST /api/workflow/{workflowId}/rerun
```

Re-runs a workflow that has ended (any terminal status) from a specific task, or from the beginning when `reRunFromTaskId` is omitted. A workflow that has not ended is refused with `409 Conflict`. The `reRunFromWorkflowId` in the body is overwritten by the `{workflowId}` path parameter. The response is the workflow ID as plain text.

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

When you rerun from a task, Conductor removes every task scheduled after it and runs the workflow forward again from that task:

- **The task gets a fresh total time budget.** Its `totalTimeoutSeconds` counts from the rerun, not from when the task was first scheduled.
- **Inside a fork, other branches are redone too.** Tasks that other branches scheduled after the rerun task are removed, so finished tasks the rerun keeps schedule their successors again (the next task in the branch, a fork's branches, a switch's chosen case). A finished `JOIN` that waits for a task now removed or running again goes back to waiting.
- **Inside a `DO_WHILE`, the loop reopens at that iteration.** The loop task goes back to `IN_PROGRESS` at the rerun task's iteration, drops the output of that iteration and later ones, and then evaluates its condition as usual.
- **Sub-workflows start fresh.** Rerunning a `SUB_WORKFLOW` task, or rerunning from a task before it, starts a new child workflow instead of reusing the previous child's result. See [Sub Workflow](../configuration/workflowdef/operators/sub-workflow-task.md#rerun-and-retry).
- **A task ID that a retry replaced** reruns the task's latest attempt.

If `reRunFromTaskId` is not found in the workflow or any of its sub-workflows, the request fails with `400 Bad Request` (`Task <id> not found`).

### Skip Task

```
PUT /api/workflow/{workflowId}/skiptask/{taskReferenceName}
```

Skips a task in a running workflow that has not been scheduled yet. Conductor records it as `SKIPPED`, and when the flow reaches it, treats it as finished and moves on to the next task. This holds wherever the task is: the first task, the next task in a list, a task in a fork branch, or a task in a switch case. A task that has already been scheduled cannot be skipped. Skipping a `FORK_JOIN` or `FORK_JOIN_DYNAMIC` also skips its `JOIN`, so the flow continues after the `JOIN` without running any branch.

The request body is optional. When given, `taskInput` and `taskOutput` become the skipped task's input and output, which later tasks can reference:

```shell
curl -X PUT 'http://localhost:8080/api/workflow/3a5b8c2d.../skiptask/validate_ref' \
  -H 'Content-Type: application/json' \
  -d '{
    "taskInput": {},
    "taskOutput": {"skipped": true, "reason": "manual override"}
  }'
```

Without a body, the skipped task has empty input and output.

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

Only a running or paused workflow can be terminated. Terminating one that is `TERMINATED` already does nothing. One that is `COMPLETED`, `FAILED` or `TIMED_OUT` is refused with `409 Conflict`, and keeps its status and `reasonForIncompletion`. `terminate-remove` removes such a workflow without terminating it.

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
      "my_task_ref": [{
        "status": "COMPLETED",
        "output": {"key": "mocked_value"}
      }]
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
