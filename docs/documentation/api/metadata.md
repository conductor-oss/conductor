---
description: "Conductor Metadata API — register, update, validate, and delete workflow and task definitions. Manage your orchestration blueprints via REST."
---

# Metadata API

The Metadata API manages workflow and task definitions — the blueprints that Conductor uses to orchestrate executions. All endpoints use the base path `/api/metadata`.

## Workflow Definitions

| Endpoint | Method | Description |
|---|---|---|
| `/metadata/workflow` | `GET` | Get all workflow definitions |
| `/metadata/workflow` | `POST` | Create a new workflow definition |
| `/metadata/workflow` | `PUT` | Create or update workflow definitions (batch) |
| `/metadata/workflow/{name}` | `GET` | Get a workflow definition by name |
| `/metadata/workflow/{name}/{version}` | `DELETE` | Delete a workflow definition by name and version |
| `/metadata/workflow/validate` | `POST` | Validate a workflow definition without saving |
| `/metadata/workflow/names-and-versions` | `GET` | Get all workflow names and versions (no definition bodies) |
| `/metadata/workflow/latest-versions` | `GET` | Get only the latest version of each workflow definition |

### Get All Workflow Definitions

```
GET /api/metadata/workflow
```

Returns a list of all registered workflow definitions.

```shell
curl http://localhost:8080/api/metadata/workflow
```

**Response** `200 OK`

```json
[
  {
    "name": "order_processing",
    "version": 1,
    "tasks": [...],
    "inputParameters": [],
    "outputParameters": {},
    "schemaVersion": 2
  }
]
```

### Create a Workflow Definition

```
POST /api/metadata/workflow
```

Registers a new workflow definition. Request body is a [Workflow Definition](../configuration/workflowdef/index.md).

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "my_workflow",
    "version": 1,
    "tasks": [
      {
        "name": "my_task",
        "taskReferenceName": "my_task_ref",
        "type": "SIMPLE"
      }
    ],
    "schemaVersion": 2,
    "ownerEmail": "dev@example.com"
  }'
```

**Response** `200 OK` — no response body.

### Create or Update Workflow Definitions

```
PUT /api/metadata/workflow
```

Creates or updates workflow definitions in bulk. Request body is a list of [Workflow Definitions](../configuration/workflowdef/index.md). Returns a `BulkResponse` indicating success and failure for each definition.

```shell
curl -X PUT 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d '[
    {"name": "workflow_a", "version": 1, "tasks": [...], "schemaVersion": 2},
    {"name": "workflow_b", "version": 1, "tasks": [...], "schemaVersion": 2}
  ]'
```

**Response** `200 OK`

```json
{
  "bulkSuccessfulResults": ["workflow_a", "workflow_b"],
  "bulkErrorResults": {}
}
```

### Get Workflow Definition by Name

```
GET /api/metadata/workflow/{name}?version={version}
```

| Parameter | Description | Required |
|---|---|---|
| `name` | Workflow name | Yes (path) |
| `version` | Workflow version | No (defaults to latest) |

```shell
curl 'http://localhost:8080/api/metadata/workflow/my_workflow?version=1'
```

**Response** `200 OK` — returns the full workflow definition JSON.

### Delete a Workflow Definition

```
DELETE /api/metadata/workflow/{name}/{version}
```

Removes a workflow definition by name and version. Does **not** remove workflow executions associated with the definition.

| Parameter | Description | Required |
|---|---|---|
| `name` | Workflow name | Yes (path) |
| `version` | Workflow version | Yes (path) |

```shell
curl -X DELETE 'http://localhost:8080/api/metadata/workflow/my_workflow/1'
```

**Response** `200 OK` — no response body.

### Validate a Workflow Definition

```
POST /api/metadata/workflow/validate
```

Validates a workflow definition without registering it. Useful for CI/CD pipelines or pre-deployment checks.

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow/validate' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "my_workflow",
    "version": 1,
    "tasks": [
      {
        "name": "my_task",
        "taskReferenceName": "my_task_ref",
        "type": "SIMPLE"
      }
    ],
    "schemaVersion": 2
  }'
```

**Response** `200 OK` if valid. `400 Bad Request` with error details if invalid.

### Get Workflow Names and Versions

```
GET /api/metadata/workflow/names-and-versions
```

Returns a lightweight map of workflow names to their available versions (no definition bodies). Useful for building UIs or listing available workflows.

```shell
curl http://localhost:8080/api/metadata/workflow/names-and-versions
```

**Response** `200 OK`

```json
{
  "order_processing": [
    {"name": "order_processing", "version": 1},
    {"name": "order_processing", "version": 2}
  ],
  "user_onboarding": [
    {"name": "user_onboarding", "version": 1}
  ]
}
```

### Get Latest Versions Only

```
GET /api/metadata/workflow/latest-versions
```

Returns only the latest version of each workflow definition.

```shell
curl http://localhost:8080/api/metadata/workflow/latest-versions
```

**Response** `200 OK` — returns a list of workflow definitions (one per workflow name, latest version only).

---

## Task Definitions

| Endpoint | Method | Description |
|---|---|---|
| `/metadata/taskdefs` | `GET` | Get all task definitions |
| `/metadata/taskdefs` | `POST` | Create new task definitions |
| `/metadata/taskdefs` | `PUT` | Update a task definition |
| `/metadata/taskdefs/{taskType}` | `GET` | Get a task definition by name |
| `/metadata/taskdefs/{taskType}` | `DELETE` | Delete a task definition |

### Get All Task Definitions

```
GET /api/metadata/taskdefs
```

```shell
curl http://localhost:8080/api/metadata/taskdefs
```

**Response** `200 OK`

```json
[
  {
    "name": "my_task",
    "retryCount": 3,
    "retryLogic": "FIXED",
    "retryDelaySeconds": 10,
    "timeoutSeconds": 300,
    "timeoutPolicy": "TIME_OUT_WF",
    "responseTimeoutSeconds": 180
  }
]
```

### Create Task Definitions

```
POST /api/metadata/taskdefs
```

Registers new task definitions. Request body is a list of [Task Definitions](../configuration/taskdef.md).

```shell
curl -X POST 'http://localhost:8080/api/metadata/taskdefs' \
  -H 'Content-Type: application/json' \
  -d '[
    {
      "name": "my_task",
      "retryCount": 3,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 10,
      "timeoutSeconds": 300,
      "timeoutPolicy": "TIME_OUT_WF",
      "responseTimeoutSeconds": 180,
      "ownerEmail": "dev@example.com"
    }
  ]'
```

**Response** `200 OK` — no response body.

### Update a Task Definition

```
PUT /api/metadata/taskdefs
```

Updates an existing task definition. Request body is a single [Task Definition](../configuration/taskdef.md).

```shell
curl -X PUT 'http://localhost:8080/api/metadata/taskdefs' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "my_task",
    "retryCount": 5,
    "retryLogic": "EXPONENTIAL_BACKOFF",
    "retryDelaySeconds": 5,
    "timeoutSeconds": 600,
    "timeoutPolicy": "TIME_OUT_WF",
    "responseTimeoutSeconds": 300
  }'
```

**Response** `200 OK` — no response body.

### Get Task Definition by Name

```
GET /api/metadata/taskdefs/{taskType}
```

```shell
curl http://localhost:8080/api/metadata/taskdefs/my_task
```

**Response** `200 OK` — returns the task definition JSON.

### Delete a Task Definition

```
DELETE /api/metadata/taskdefs/{taskType}
```

```shell
curl -X DELETE http://localhost:8080/api/metadata/taskdefs/my_task
```

**Response** `200 OK` — no response body.
