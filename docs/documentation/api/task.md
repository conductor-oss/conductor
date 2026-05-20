---
description: "Conductor Task API — poll, update, search, and manage tasks. Includes batch polling, task logs, queue management, and poll data."
---

# Task API

The Task API manages task execution — polling, updating, logging, and queue management. All endpoints use the base path `/api/tasks`.

## Get Task

```
GET /api/tasks/{taskId}
```

Returns the task details for a given task ID.

```shell
curl 'http://localhost:8080/api/tasks/a1b2c3d4-5678-90ab-cdef-111111111111'
```

**Response** `200 OK`

```json
{
  "taskType": "my_task",
  "status": "COMPLETED",
  "referenceTaskName": "my_task_ref",
  "retryCount": 0,
  "seq": 1,
  "startTime": 1700000001000,
  "endTime": 1700000003000,
  "updateTime": 1700000003000,
  "pollCount": 1,
  "taskId": "a1b2c3d4-5678-90ab-cdef-111111111111",
  "workflowInstanceId": "3a5b8c2d-1234-5678-9abc-def012345678",
  "inputData": {"key": "value"},
  "outputData": {"result": "success"},
  "workerId": "worker-host-1"
}
```

---

## Poll and Update Tasks

These endpoints are used by workers to poll for tasks and update their results. They are typically called by the [SDK](../clientsdks/index.md), not manually.

### Poll for a Task

```
GET /api/tasks/poll/{taskType}?workerid=&domain=
```

Polls for a single task of the given type. Returns `204 No Content` if no task is available.

| Parameter | Description | Required |
|---|---|---|
| `taskType` | Task type to poll for | Yes |
| `workerid` | Identifier for the worker polling | No |
| `domain` | Task domain. See [Task Domains](taskdomains.md). | No |

```shell
curl 'http://localhost:8080/api/tasks/poll/my_task?workerid=worker-1'
```

**Response** `200 OK` — returns a task object (same format as Get Task above), or `204 No Content` if no tasks are queued.

### Batch Poll

```
GET /api/tasks/poll/batch/{taskType}?count=1&timeout=100&workerid=&domain=
```

Polls for multiple tasks in a single request. This is a **long poll** — the connection waits until `timeout` or at least 1 task is available.

| Parameter | Description | Default |
|---|---|---|
| `taskType` | Task type to poll for | — |
| `count` | Maximum number of tasks to return | `1` |
| `timeout` | Long poll timeout in milliseconds | `100` |
| `workerid` | Worker identifier | — |
| `domain` | Task domain | — |

```shell
# Poll for up to 5 tasks, wait up to 1 second
curl 'http://localhost:8080/api/tasks/poll/batch/my_task?count=5&timeout=1000&workerid=worker-1'
```

**Response** `200 OK` — returns a list of task objects, or an empty list if no tasks are available.

```json
[
  {
    "taskType": "my_task",
    "status": "IN_PROGRESS",
    "taskId": "task-uuid-1",
    "workflowInstanceId": "workflow-uuid-1",
    "inputData": {"key": "value1"}
  },
  {
    "taskType": "my_task",
    "status": "IN_PROGRESS",
    "taskId": "task-uuid-2",
    "workflowInstanceId": "workflow-uuid-2",
    "inputData": {"key": "value2"}
  }
]
```

### Update Task

```
POST /api/tasks
```

Updates the result of a task execution. Returns the task ID.

```shell
curl -X POST 'http://localhost:8080/api/tasks' \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowInstanceId": "3a5b8c2d-1234-5678-9abc-def012345678",
    "taskId": "a1b2c3d4-5678-90ab-cdef-111111111111",
    "status": "COMPLETED",
    "outputData": {
      "result": "processed successfully",
      "recordCount": 42
    }
  }'
```

**Request body fields:**

| Field | Description | Required |
|---|---|---|
| `workflowInstanceId` | Workflow execution ID | Yes |
| `taskId` | Task ID | Yes |
| `status` | `IN_PROGRESS`, `COMPLETED`, `FAILED`, or `FAILED_WITH_TERMINAL_ERROR` | Yes |
| `outputData` | JSON map of output data | No |
| `reasonForIncompletion` | Reason for failure (when status is `FAILED`) | No |
| `callbackAfterSeconds` | Callback delay — task will be put back in queue after this time | No |
| `logs` | List of log entries to append | No |

**Response** `200 OK` — returns the task ID as plain text.

### Update Task V2

```
POST /api/tasks/update-v2
```

Updates a task and returns the **next available task** to be processed — combining an update and poll in one call. Returns `204 No Content` if no next task is available.

```shell
curl -X POST 'http://localhost:8080/api/tasks/update-v2' \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowInstanceId": "3a5b8c2d-1234-5678-9abc-def012345678",
    "taskId": "a1b2c3d4-5678-90ab-cdef-111111111111",
    "status": "COMPLETED",
    "outputData": {"result": "done"}
  }'
```

**Response** `200 OK` — returns the next task object, or `204 No Content` if no tasks are queued.

### Update Task by Reference Name

```
POST /api/tasks/{workflowId}/{taskRefName}/{status}?workerid=
```

Updates a task using the workflow ID and task reference name instead of the task ID. This is useful for completing WAIT or HUMAN tasks from external systems.

| Parameter | Description | Required |
|---|---|---|
| `workflowId` | Workflow execution ID | Yes |
| `taskRefName` | Task reference name in the workflow | Yes |
| `status` | `IN_PROGRESS`, `COMPLETED`, `FAILED`, or `FAILED_WITH_TERMINAL_ERROR` | Yes |
| `workerid` | Worker identifier | No |

Request body: JSON map of output data.

```shell
# Complete a WAIT task with output data
curl -X POST 'http://localhost:8080/api/tasks/3a5b8c2d.../wait_for_approval/COMPLETED' \
  -H 'Content-Type: application/json' \
  -d '{"approved": true, "approver": "jane@example.com"}'
```

**Response** `200 OK` — no response body.

### Update Task by Reference Name (Synchronous)

```
POST /api/tasks/{workflowId}/{taskRefName}/{status}/sync?workerid=
```

Same as above, but returns the **updated workflow** after the task update is processed. Useful for synchronous execution patterns where you need the workflow state immediately after updating a task.

```shell
curl -X POST 'http://localhost:8080/api/tasks/3a5b8c2d.../wait_for_signal/COMPLETED/sync' \
  -H 'Content-Type: application/json' \
  -d '{"signal": "proceed"}'
```

**Response** `200 OK` — returns the full workflow execution object.

---

## Task Logs

### Add a Task Log

```
POST /api/tasks/{taskId}/log
```

Adds an execution log entry to a task. Request body: log message as a plain string.

```shell
curl -X POST 'http://localhost:8080/api/tasks/a1b2c3d4.../log' \
  -H 'Content-Type: text/plain' \
  -d 'Processing started for batch #42'
```

**Response** `200 OK` — no response body.

### Get Task Logs

```
GET /api/tasks/{taskId}/log
```

Returns execution logs for a task. Returns `204 No Content` if no logs exist.

```shell
curl 'http://localhost:8080/api/tasks/a1b2c3d4.../log'
```

**Response** `200 OK`

```json
[
  {
    "log": "Processing started for batch #42",
    "taskId": "a1b2c3d4-5678-90ab-cdef-111111111111",
    "createdTime": 1700000001000
  },
  {
    "log": "Batch #42 completed: 100 records processed",
    "taskId": "a1b2c3d4-5678-90ab-cdef-111111111111",
    "createdTime": 1700000003000
  }
]
```

---

## Queue Management

| Endpoint | Method | Description |
|---|---|---|
| `/queue/all` | `GET` | Get pending task counts for all queues |
| `/queue/all/verbose` | `GET` | Get detailed queue info including per-shard counts |
| `/queue/size` | `GET` | Get queue size for a specific task type |
| `/queue/sizes` | `GET` | *(Deprecated)* Get queue sizes for task types. Use `/queue/size` instead. |
| `/queue/requeue/{taskType}` | `POST` | Requeue pending tasks of a given type |

### Get Queue Size

```
GET /api/tasks/queue/size?taskType=&domain=&isolationGroupId=&executionNamespace=
```

Returns the queue depth for a specific task type, optionally filtered by domain and isolation group.

```shell
curl 'http://localhost:8080/api/tasks/queue/size?taskType=my_task'
```

**Response** `200 OK`

```json
5
```

### Get All Queue Sizes

```
GET /api/tasks/queue/all
```

Returns a map of task type to pending count for all queues.

```shell
curl 'http://localhost:8080/api/tasks/queue/all'
```

**Response** `200 OK`

```json
{
  "my_task": 5,
  "http_task": 0,
  "email_task": 12
}
```

### Get All Queue Details (Verbose)

```
GET /api/tasks/queue/all/verbose
```

Returns detailed queue information including per-shard counts.

```shell
curl 'http://localhost:8080/api/tasks/queue/all/verbose'
```

**Response** `200 OK`

```json
{
  "my_task": {
    "size": 5,
    "shards": {"0": 3, "1": 2}
  }
}
```

### Requeue Pending Tasks

```
POST /api/tasks/queue/requeue/{taskType}
```

Requeues all pending tasks of the specified type. Useful for recovery after worker issues.

```shell
curl -X POST 'http://localhost:8080/api/tasks/queue/requeue/my_task'
```

**Response** `200 OK` — returns the number of tasks requeued.

---

## Poll Data

### Get Poll Data for a Task Type

```
GET /api/tasks/queue/polldata?taskType=
```

Returns the last poll data for a given task type — useful for monitoring worker health and activity.

```shell
curl 'http://localhost:8080/api/tasks/queue/polldata?taskType=my_task'
```

**Response** `200 OK`

```json
[
  {
    "queueName": "my_task",
    "domain": null,
    "workerId": "worker-host-1",
    "lastPollTime": 1700000005000
  }
]
```

### Get Poll Data for All Task Types

```
GET /api/tasks/queue/polldata/all
```

Returns the last poll data for all task types.

```shell
curl 'http://localhost:8080/api/tasks/queue/polldata/all'
```

**Response** `200 OK` — returns a list of poll data objects (same format as above) for all task types.

---

## Search Tasks

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
GET /api/tasks/search?start=0&size=100&sort=&freeText=&query=
```

Returns `SearchResult<TaskSummary>` — lightweight results.

```shell
# Find failed tasks for a specific workflow type
curl 'http://localhost:8080/api/tasks/search?query=workflowType%3D%27order_processing%27+AND+status%3D%27FAILED%27&size=10'

# Free-text search
curl 'http://localhost:8080/api/tasks/search?freeText=timeout'
```

**Response** `200 OK`

```json
{
  "totalHits": 3,
  "results": [
    {
      "taskId": "task-uuid",
      "taskType": "my_task",
      "referenceTaskName": "my_task_ref",
      "workflowId": "workflow-uuid",
      "workflowType": "order_processing",
      "status": "FAILED",
      "startTime": "2024-01-15T10:30:00Z",
      "updateTime": "2024-01-15T10:30:05Z",
      "executionTime": 5000
    }
  ]
}
```

### Search V2 (Full)

```
GET /api/tasks/search-v2?start=0&size=100&sort=&freeText=&query=
```

Returns `SearchResult<Task>` — full task objects including input/output data.

---

## External Storage

```
GET /api/tasks/externalstoragelocation?path=&operation=&payloadType=
```

Get the URI for external task payload storage. See [External Payload Storage](../advanced/externalpayloadstorage.md).

```shell
curl 'http://localhost:8080/api/tasks/externalstoragelocation?path=task/output&operation=WRITE&payloadType=TASK_OUTPUT'
```

**Response** `200 OK`

```json
{
  "uri": "s3://conductor-payloads/task/output/...",
  "path": "task/output/..."
}
```
