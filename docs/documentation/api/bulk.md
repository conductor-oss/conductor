---
description: "Conductor Bulk Operations API — pause, resume, restart, retry, terminate, remove, and search workflows in batch."
---

# Bulk Operations API

The Bulk Operations API lets you perform workflow management operations on multiple workflows in a single request. All endpoints use the base path `/api/workflow/bulk`.

Every endpoint accepts a list of workflow IDs in the request body and returns a `BulkResponse`:

```json
{
  "bulkSuccessfulResults": ["workflow-id-1", "workflow-id-2"],
  "bulkErrorResults": {
    "workflow-id-3": "Workflow is not in a running state"
  }
}
```

Operations are **best-effort** — each workflow is processed independently. If one fails, the rest still proceed.

## Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/bulk/pause` | `PUT` | Pause multiple workflows |
| `/bulk/resume` | `PUT` | Resume multiple paused workflows |
| `/bulk/restart` | `POST` | Restart multiple completed workflows |
| `/bulk/retry` | `POST` | Retry the last failed task in multiple workflows |
| `/bulk/terminate` | `POST` | Terminate multiple running workflows |
| `/bulk/remove` | `DELETE` | Remove multiple workflows from the system |
| `/bulk/terminate-remove` | `DELETE` | Terminate and remove multiple workflows |
| `/bulk/search` | `POST` | Search/fetch multiple workflows by ID |

### Bulk Pause

```
PUT /api/workflow/bulk/pause
```

```shell
curl -X PUT 'http://localhost:8080/api/workflow/bulk/pause' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2", "workflow-id-3"]'
```

**Response** `200 OK`

```json
{
  "bulkSuccessfulResults": ["workflow-id-1", "workflow-id-2"],
  "bulkErrorResults": {
    "workflow-id-3": "Workflow is already paused"
  }
}
```

### Bulk Resume

```
PUT /api/workflow/bulk/resume
```

```shell
curl -X PUT 'http://localhost:8080/api/workflow/bulk/resume' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2"]'
```

**Response** `200 OK` — returns a `BulkResponse`.

### Bulk Restart

```
POST /api/workflow/bulk/restart?useLatestDefinitions=false
```

| Parameter | Description | Default |
|---|---|---|
| `useLatestDefinitions` | Use latest workflow and task definitions | `false` |

```shell
curl -X POST 'http://localhost:8080/api/workflow/bulk/restart?useLatestDefinitions=true' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2"]'
```

**Response** `200 OK` — returns a `BulkResponse`.

### Bulk Retry

```
POST /api/workflow/bulk/retry
```

Retries the last failed task for each workflow.

```shell
curl -X POST 'http://localhost:8080/api/workflow/bulk/retry' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2"]'
```

**Response** `200 OK` — returns a `BulkResponse`.

### Bulk Terminate

```
POST /api/workflow/bulk/terminate?reason=
```

| Parameter | Description | Required |
|---|---|---|
| `reason` | Reason for termination | No |

```shell
curl -X POST 'http://localhost:8080/api/workflow/bulk/terminate?reason=batch+cleanup' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2", "workflow-id-3"]'
```

**Response** `200 OK` — returns a `BulkResponse`.

### Bulk Remove

```
DELETE /api/workflow/bulk/remove?archiveWorkflow=true
```

| Parameter | Description | Default |
|---|---|---|
| `archiveWorkflow` | Archive before removing | `true` |

```shell
curl -X DELETE 'http://localhost:8080/api/workflow/bulk/remove' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2"]'
```

!!! warning
    This permanently removes workflow execution data.

**Response** `200 OK` — returns a `BulkResponse`.

### Bulk Terminate and Remove

```
DELETE /api/workflow/bulk/terminate-remove?reason=&archiveWorkflow=true
```

Terminates running workflows and removes them in one call.

| Parameter | Description | Default |
|---|---|---|
| `reason` | Reason for termination | — |
| `archiveWorkflow` | Archive before removing | `true` |

```shell
curl -X DELETE 'http://localhost:8080/api/workflow/bulk/terminate-remove?reason=decommissioned' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2"]'
```

**Response** `200 OK` — returns a `BulkResponse`.

### Bulk Search

```
POST /api/workflow/bulk/search?includeTasks=true
```

Fetches multiple workflows by their IDs in a single call. Unlike the other bulk endpoints, this returns workflow objects rather than a `BulkResponse`.

| Parameter | Description | Default |
|---|---|---|
| `includeTasks` | Include task details | `true` |

```shell
curl -X POST 'http://localhost:8080/api/workflow/bulk/search?includeTasks=false' \
  -H 'Content-Type: application/json' \
  -d '["workflow-id-1", "workflow-id-2"]'
```

**Response** `200 OK` — returns a `BulkResponse` where `bulkSuccessfulResults` contains the full workflow objects.
