# Synchronous Workflows and Signal APIs

## Overview

Orkes Conductor provides synchronous execution APIs that allow clients to start workflows or update tasks and wait for specific conditions before receiving a response. These APIs are useful for scenarios where the client needs to block until a workflow reaches a certain state or a specific task completes.

## Table of Contents

- [Synchronous Workflow Execution](#synchronous-workflow-execution)
  - [Execute Workflow (Recommended)](#execute-workflow-recommended)
- [Task Signal APIs](#task-signal-apis)
  - [Signal Workflow Task (Synchronous)](#signal-workflow-task-synchronous)
  - [Signal Workflow Task (Asynchronous)](#signal-workflow-task-asynchronous)
- [Task Update APIs](#task-update-apis)
  - [Update Task by Reference Name (Synchronous)](#update-task-by-reference-name-synchronous)
  - [Update Task by Reference Name (Asynchronous)](#update-task-by-reference-name-asynchronous)
  - [Update Task by Task Result](#update-task-by-task-result)
- [Workflow State Update API](#workflow-state-update-api)
- [Common Concepts](#common-concepts)
  - [Return Strategies](#return-strategies)
  - [Workflow Consistency](#workflow-consistency)
  - [Response Types](#response-types)

---

## Synchronous Workflow Execution

### Execute Workflow (Recommended)

Execute a workflow synchronously and wait for it to reach a terminal state or until specific tasks are scheduled.

#### API Signature

```
POST /api/workflow/execute/{name}/{version}
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `name` | Path | Yes | - | Name of the workflow to execute |
| `version` | Path | No | Latest | Version of the workflow definition |
| `requestId` | Query | No | Auto-generated UUID | Unique identifier for tracking this execution request |
| `waitUntilTaskRef` | Query | No | - | Comma-separated list of task reference names to wait for |
| `waitForSeconds` | Query | No | 10 | Maximum seconds to wait before timing out |
| `consistency` | Query | No | DURABLE | Workflow consistency level (SYNCHRONOUS, DURABLE, REGION_DURABLE) |
| `returnStrategy` | Query | No | TARGET_WORKFLOW | Strategy for determining what to return in the response |

#### Request Body

```json
{
  "name": "workflow-name",
  "version": 1,
  "input": {
    "key1": "value1",
    "key2": "value2"
  },
  "correlationId": "optional-correlation-id",
  "priority": 0,
  "taskToDomain": {},
  "workflowDef": null,
  "externalInputPayloadStoragePath": null,
  "createdBy": "user-id"
}
```

#### Response

Returns a `Mono<SignalResponse>` which can be one of:
- `WorkflowRun` - Contains workflow state, tasks, variables, and metadata
- `TaskRun` - Contains task state and metadata (when using BLOCKING_TASK strategies)

#### Behavior

1. **Workflow Creation**: Starts the workflow with the provided input
2. **Monitoring**: Registers a completion monitor with the given `requestId`
3. **Waiting**: Blocks until one of these conditions is met:
   - Workflow reaches a terminal state (COMPLETED, FAILED, TERMINATED, TIMED_OUT)
   - Tasks specified in `waitUntilTaskRef` are scheduled
   - Workflow encounters a WAIT or YIELD task
   - Timeout is reached (`waitForSeconds`)
4. **Response**: Returns the workflow/task state based on the `returnStrategy`
5. **Timeout Handling**: On timeout, returns the current state of the target workflow

#### Example

```bash
curl -X POST "http://localhost:8080/api/workflow/execute/my-workflow/1?waitUntilTaskRef=task1,task2&waitForSeconds=30&returnStrategy=BLOCKING_WORKFLOW" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "orderId": "12345",
      "customerId": "67890"
    }
  }'
```

#### Response Example

```json
{
  "responseType": "BLOCKING_WORKFLOW",
  "targetWorkflowId": "abc-123-def-456",
  "targetWorkflowStatus": "RUNNING",
  "workflowId": "abc-123-def-456",
  "correlationId": "order-12345",
  "status": "RUNNING",
  "input": {
    "orderId": "12345",
    "customerId": "67890"
  },
  "output": {},
  "variables": {},
  "tasks": [...],
  "priority": 0,
  "createdBy": "user-id",
  "createTime": 1699564800000,
  "updateTime": 1699564805000
}
```

---

## Task Signal APIs

Signal APIs allow you to update the first pending (non-terminal) task in a workflow without knowing the specific task ID.

### Signal Workflow Task (Synchronous)

Update the first pending task in a workflow and wait for the workflow to progress.

#### API Signature

```
POST /api/tasks/{workflowId}/{status}/signal/sync
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `workflowId` | Path | Yes | - | ID of the workflow containing the task |
| `status` | Path | Yes | - | Status to set on the task (COMPLETED, FAILED, etc.) |
| `returnStrategy` | Query | No | TARGET_WORKFLOW | Strategy for determining what to return |

#### Request Body

```json
{
  "key1": "value1",
  "key2": "value2"
}
```

The request body contains the output data to merge into the task's output.

#### Response

Returns a `Mono<SignalResponse>` with a 5-second timeout.

#### Behavior

1. **Task Selection**: Finds the first non-terminal task in the workflow
2. **Permission Check**: Verifies the user has EXECUTE permission on the task definition
3. **Task Update**: Updates the task with the provided status and output data
4. **Decision**: Runs the workflow decider
5. **Monitoring**: Waits up to 5 seconds for the workflow to reach a blocking state or terminal state
6. **Timeout Handling**: Returns current workflow state if timeout is reached

#### Example

```bash
curl -X POST "http://localhost:8080/api/tasks/abc-123-def-456/COMPLETED/signal/sync?returnStrategy=BLOCKING_TASK" \
  -H "Content-Type: application/json" \
  -d '{
    "result": "success",
    "processedItems": 42
  }'
```

---

### Signal Workflow Task (Asynchronous)

Update the first pending task in a workflow without waiting for the workflow to progress.

#### API Signature

```
POST /api/tasks/{workflowId}/{status}/signal
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `workflowId` | Path | Yes | - | ID of the workflow containing the task |
| `status` | Path | Yes | - | Status to set on the task |

#### Request Body

Same as synchronous version - task output data to merge.

#### Response

Returns `void` (HTTP 200 OK)

#### Behavior

1. Finds the first non-terminal task
2. Updates the task with provided status and output
3. Runs the workflow decider asynchronously
4. Returns immediately without waiting for workflow to progress

#### Example

```bash
curl -X POST "http://localhost:8080/api/tasks/abc-123-def-456/COMPLETED/signal" \
  -H "Content-Type: application/json" \
  -d '{
    "result": "success"
  }'
```

---

## Task Update APIs

### Update Task by Reference Name (Synchronous)

Update a specific task by its reference name and return the updated workflow.

#### API Signature

```
POST /api/tasks/{workflowId}/{taskRefName}/{status}/sync
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `workflowId` | Path | Yes | - | ID of the workflow |
| `taskRefName` | Path | Yes | - | Reference name of the task to update |
| `status` | Path | Yes | - | New status for the task |
| `workerid` | Query | No | - | ID of the worker updating the task |

#### Request Body

```json
{
  "outputKey1": "value1",
  "outputKey2": "value2"
}
```

#### Response

Returns a `Workflow` object with the updated state.

#### Behavior

1. Finds the pending task with the given reference name
2. Creates a `TaskResult` with the provided status and output
3. Updates the task
4. Runs the decider up to 3 times to allow workflow to progress
5. Returns the updated workflow

#### Example

```bash
curl -X POST "http://localhost:8080/api/tasks/abc-123-def-456/my-task-ref/COMPLETED/sync?workerid=worker-1" \
  -H "Content-Type: application/json" \
  -d '{
    "processedCount": 100,
    "status": "success"
  }'
```

---

### Update Task by Reference Name (Asynchronous)

Update a specific task by reference name and return just the task ID.

#### API Signature

```
POST /api/tasks/{workflowId}/{taskRefName}/{status}
```

#### Request Parameters

Same as synchronous version.

#### Request Body

Same as synchronous version.

#### Response

Returns the task ID as a plain text string.

#### Behavior

1. Finds the task by reference name
2. Updates the task output (merges with existing output)
3. Returns the task ID immediately
4. Decider runs asynchronously

#### Example

```bash
curl -X POST "http://localhost:8080/api/tasks/abc-123-def-456/my-task-ref/COMPLETED?workerid=worker-1" \
  -H "Content-Type: application/json" \
  -d '{
    "result": "done"
  }'
```

---

### Update Task by Task Result

Standard task update using a complete `TaskResult` object.

#### API Signature

```
POST /api/tasks
```

#### Request Body

```json
{
  "taskId": "task-uuid",
  "workflowInstanceId": "workflow-uuid",
  "status": "COMPLETED",
  "outputData": {
    "key1": "value1"
  },
  "logs": [],
  "workerId": "worker-1"
}
```

#### Response

Returns the task ID as plain text.

#### Behavior

Standard Conductor task update behavior - updates the task and triggers workflow evaluation.

---

## Workflow State Update API

Update workflow variables and/or task state, then wait for specific conditions.

#### API Signature

```
POST /api/workflow/{workflowId}/state?requestId={requestId}&waitUntilTaskRef={taskRefs}&waitForSeconds={seconds}
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `workflowId` | Path | Yes | - | ID of the workflow to update |
| `requestId` | Query | Yes | - | Unique identifier for tracking this request |
| `waitUntilTaskRef` | Query | No | - | Comma-separated task reference names to wait for |
| `waitForSeconds` | Query | No | 10 | Maximum seconds to wait |

#### Request Body

```json
{
  "variables": {
    "var1": "value1",
    "var2": "value2"
  },
  "taskReferenceName": "optional-task-ref",
  "taskResult": {
    "status": "COMPLETED",
    "outputData": {
      "key": "value"
    }
  }
}
```

#### Response

Returns a `WorkflowRun` object.

#### Behavior

1. Updates workflow variables if provided
2. Updates the specified task if `taskReferenceName` and `taskResult` are provided
3. Registers a completion monitor
4. Waits for specified conditions or timeout
5. Returns the workflow state

---

## Common Concepts

### Return Strategies

The `returnStrategy` parameter controls what data is returned in synchronous API responses:

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `TARGET_WORKFLOW` | Returns the state of the workflow specified in the request | Default - get the main workflow state |
| `BLOCKING_WORKFLOW` | Returns the state of the workflow that is currently blocking | Useful when you want the subworkflow that's blocking |
| `BLOCKING_TASK` | Returns the state of the task that is currently blocking | Get details of the specific task that's waiting |
| `BLOCKING_TASK_INPUT` | Returns only the input of the blocking task | Lightweight response with just task input |

**Example Scenario**:
- You start workflow A which starts subworkflow B
- Subworkflow B has a WAIT task
- With `TARGET_WORKFLOW`: Returns workflow A's state
- With `BLOCKING_WORKFLOW`: Returns workflow B's state (the subworkflow)
- With `BLOCKING_TASK`: Returns the WAIT task's details

### Workflow Consistency

Controls how workflow state is persisted during execution:

| Level | Description | Trade-offs |
|-------|-------------|------------|
| `SYNCHRONOUS` | Workflow kept in memory until evaluation completes, then persisted | Fastest; Data loss possible if node crashes |
| `DURABLE` | Workflow persisted before evaluation | Default; Good balance of durability and performance |
| `REGION_DURABLE` | Replicated across regions before evaluation | Slowest; Highest durability in multi-region setups |

### Response Types

#### WorkflowRun

Extends `SignalResponse` with workflow-specific fields:

```json
{
  "responseType": "TARGET_WORKFLOW",
  "targetWorkflowId": "target-id",
  "targetWorkflowStatus": "RUNNING",
  "workflowId": "workflow-id",
  "correlationId": "correlation-id",
  "input": {},
  "output": {},
  "priority": 0,
  "variables": {},
  "tasks": [],
  "createdBy": "user-id",
  "createTime": 1699564800000,
  "status": "RUNNING",
  "updateTime": 1699564805000
}
```

#### TaskRun

Extends `SignalResponse` with task-specific fields:

```json
{
  "responseType": "BLOCKING_TASK",
  "targetWorkflowId": "workflow-id",
  "targetWorkflowStatus": "RUNNING",
  "workflowId": "workflow-id",
  "taskType": "SIMPLE",
  "taskId": "task-id",
  "referenceTaskName": "my_task",
  "retryCount": 0,
  "taskDefName": "my_task_def",
  "status": "IN_PROGRESS",
  "input": {},
  "output": {},
  "correlationId": "correlation-id",
  "priority": 0,
  "createdBy": "worker-id",
  "createTime": 1699564800000,
  "updateTime": 1699564805000
}
```

---

## Implementation Details

### Notification Mechanism

1. **WorkflowNotifications**: Carries `requestId`, `hostIp`, and `waitUntilTasks`
2. **WorkflowExecutionMonitor**: Manages `CompletableFuture` instances keyed by `requestId`
3. **PeerNotificationService**: Handles cross-node notifications in distributed deployments
4. **NotificationResult**: Encapsulates `targetWorkflow`, `blockingWorkflow`, and `blockingTasks`

### Multi-Node Support

In a clustered deployment:
- The `hostIp` field identifies which node is waiting for the response
- `PeerNotificationService` makes HTTP calls to notify peer nodes
- Local notifications are handled directly via `WorkflowExecutionMonitor`

### Blocking Detection

A workflow/task is considered "blocking" when:
- Workflow reaches a terminal state
- A WAIT task is encountered and not terminal
- A YIELD task is encountered and not terminal
- Tasks specified in `waitUntilTaskRef` are scheduled

---

## Best Practices

1. **Use Appropriate Timeouts**: Set `waitForSeconds` based on expected workflow duration
2. **Choose the Right Return Strategy**: Use `BLOCKING_TASK` for task-level details, `TARGET_WORKFLOW` for workflow state
3. **Handle Timeouts Gracefully**: Check `targetWorkflowStatus` in responses to determine if more waiting is needed
4. **Use Signal APIs for Simplicity**: When you don't know the exact task ID, signal APIs find pending tasks automatically
5. **Consider Consistency Level**: Use `DURABLE` for most cases; `SYNCHRONOUS` only for very high throughput scenarios where data loss is acceptable
6. **Monitor Performance**: Synchronous APIs hold connections open - ensure your infrastructure can handle the concurrent connections

---

## Error Scenarios

| Error Code | Scenario | Solution |
|------------|----------|----------|
| 404 NOT_FOUND | Task not found by reference name | Verify task reference name and workflow state |
| 401 UNAUTHORIZED | User lacks EXECUTE permission | Grant appropriate permissions to the user |
| 409 CONFLICT | Workflow is locked (being updated) | Retry the request |
| 408 TIMEOUT | Request timeout reached | Increase `waitForSeconds` or check workflow for issues |

---

## See Also

- Workflow Definition Documentation
- Task Definition Documentation
- Security and Permissions Documentation
- Worker Configuration
