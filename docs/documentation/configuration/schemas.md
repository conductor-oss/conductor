---
description: "Canonical JSON Schemas for Conductor workflow definitions, task definitions, workflow executions, and task executions."
---

# Schemas

Conductor publishes JSON Schema files as the detailed, versioned contract for its definition and runtime objects. The schemas are the source of truth for field-level validation; this page identifies the objects and relationships most useful when designing an integration.

## Definition objects { .schema-table-heading }

| Schema | Purpose | Identity and important relationships |
|---|---|---|
| [WorkflowDef.json](https://github.com/conductor-oss/conductor/blob/main/schemas/WorkflowDef.json) | A reusable workflow blueprint. | `name` and `version` identify a definition. `tasks` contains `WorkflowTask` configurations; `inputParameters`, `outputParameters`, timeouts, owner, and failure-workflow settings shape its contract. |
| [TaskDef.json](https://github.com/conductor-oss/conductor/blob/main/schemas/TaskDef.json) | Registered configuration for a worker (`SIMPLE`) task type. | `name` identifies the task definition. Retry policy, timeout values, rate limits, and concurrency settings apply when a workflow task refers to that type. |

## Runtime objects { .schema-table-heading }

| Schema | Purpose | Identity and lifecycle relationships |
|---|---|---|
| [Workflow.json](https://github.com/conductor-oss/conductor/blob/main/schemas/Workflow.json) | One execution of a `WorkflowDef`. | `workflowId` identifies the execution; `workflowName`, `workflowVersion`, `status`, timestamps, input/output, variables, and `tasks` record its lifecycle. |
| [Task.json](https://github.com/conductor-oss/conductor/blob/main/schemas/Task.json) | One scheduled or executed task inside a workflow. | `taskId` identifies the runtime task; `workflowInstanceId` links it to its workflow. `taskType`, `referenceTaskName`, status, input/output, and retry/timeout state describe execution. |

Definition objects are submitted through the [Metadata API](../api/metadata.md). Runtime objects are returned by the [Workflow API](../api/workflow.md) and [Task API](../api/task.md). Use the linked schema files when generating clients, validating payloads, or checking the complete list of fields.
