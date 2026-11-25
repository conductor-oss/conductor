---
description: "PULL_WORKFLOW_MESSAGES system task — wait for and pull messages from Conductor workflow message queues."
---

# Pull Workflow Messages Task

```json
"type": "PULL_WORKFLOW_MESSAGES"
```

`PULL_WORKFLOW_MESSAGES` waits for messages made available to the workflow message queue and makes the received batch available to the workflow. It is intended for workflows that use the workflow-message-queue feature rather than a worker poller.

## Availability

This task is registered only when `conductor.workflow-message-queue.enabled=true`. It also requires the corresponding workflow-message-queue infrastructure and configuration. If the feature is disabled, a workflow using this type cannot be mapped.

## Configuration

The mapper resolves the task's `inputParameters`; the queue worker consumes them. Supply `batchSize` when the workflow needs to limit one pull, along with any queue-specific inputs required by the configured message-queue implementation.

```json
{
  "name": "pull_messages",
  "taskReferenceName": "pull_messages",
  "type": "PULL_WORKFLOW_MESSAGES",
  "inputParameters": {
    "batchSize": 10
  }
}
```

The task remains in progress until messages are available. See [Workflow Message Queue](../../../../wmq/workflow-message-queue.md) for feature configuration and delivery semantics.
