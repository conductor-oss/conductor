---
description: "Configure Human tasks in Conductor to pause workflows for manual approval or external signals. Supports human-in-the-loop and agentic workflow patterns."
---

# Human Task
```json
"type" : "HUMAN"
```

The Human task (`HUMAN`) is used to pause the workflow and wait for an external signal. It acts as a gate that remains in IN_PROGRESS until marked as COMPLETED or FAILED by an external trigger.

The Human task can be used when the workflow needs to pause and wait for human intervention, such as manual approval. It can also be used with an event coming from external source such as Kafka, SQS, or Conductor's internal queueing mechanism.

## Task parameters

No parameters are required to configure the Human task.

## JSON configuration

Here is the task configuration for a Human task.

```json
{
	"name": "human",
  "taskReferenceName": "human_ref",
	"inputParameters": {},
	"type": "HUMAN"
}
```

## Completing the Human task

There are several ways to complete the Human task:

- Using the Task Update API
- Using an event handler


### Task Update API
Use the Task Update API (`POST api/tasks`) to complete a Human task. Provide the `taskId`, the task status, and the desired task output.

Using the CLI:

```bash
conductor task update-execution --workflow-id {workflowId} --task-ref-name waiting_around_ref --status COMPLETED --output '{"data_key":"somedatatoWait1","data_key2":"somedatatoWAit2"}'
```

### Event handler
If SQS integration is enabled, the Human task can also be resolved using the Update Queue APIs:

1. `POST api/queue/update/{workflowId}/{taskRefName}/{status}`
2. `POST api/queue/update/{workflowId}/task/{taskId}/{status}`

Any parameter that is sent in the body of the POST message will be repeated as the output of the task. For example, if we send a COMPLETED message as follows:

??? note "Using cURL"
    ```bash
    curl -X "POST" "{{ server_host }}{{ api_prefix }}/queue/update/{workflowId}/waiting_around_ref/COMPLETED" \
      -H 'Content-Type: application/json' \
      -d '{"data_key":"somedatatoWait1","data_key2":"somedatatoWAit2"}'
    ```

The output of the Human task will be:

```json
{
  "data_key":"somedatatoWait1",
  "data_key2":"somedatatoWAit2"
}
```


Alternatively, an [event handler](../../eventhandlers.md) using the `complete_task` action can also be configured.

## Monitoring Human Tasks: Getting Callbacks and Notifications

When a workflow reaches a Human task, you may want to receive a notification or callback to trigger the next action (e.g., send an email, notify a Slack channel, or trigger an external system). Here are the recommended patterns:

### Pattern 1: Polling the Workflow Status API

The simplest approach is to poll the workflow execution status and check for Human tasks in `IN_PROGRESS` state:

```bash
# Get workflow execution status
curl '{{ server_host }}/api/workflow/{workflowId}' \
  -H 'accept: application/json'
```

Parse the response to find tasks with `taskType: "HUMAN"` and `status: "IN_PROGRESS"`.

**Pros:** Simple to implement, no additional configuration
**Cons:** Requires polling, not real-time

### Pattern 2: Event Handlers with Conductor Internal Events

Conductor can publish internal events when tasks change state. You can configure an event handler to listen for these events:

```json
{
  "name": "human_task_notification_handler",
  "event": "conductor:TASK_STATUS_CHANGE",
  "condition": "$.taskType == 'HUMAN' && $.status == 'IN_PROGRESS'",
  "actions": [
    {
      "action": "start_workflow",
      "start_workflow": {
        "name": "notification_workflow",
        "input": {
          "workflowId": "${workflowId}",
          "taskRefName": "${taskRefName}",
          "taskStatus": "${status}"
        }
      }
    }
  ]
}
```

This triggers a notification workflow whenever a Human task enters `IN_PROGRESS` state.

### Pattern 3: Webhook Integration via Event Task

Add an EVENT task immediately before the Human task to send a webhook notification:

```json
{
  "name": "notify_human_task",
  "taskReferenceName": "notify_ref",
  "type": "EVENT",
  "sink": "kafka:human-task-notifications",
  "inputParameters": {
    "workflowId": "${workflow.input.workflowId}",
    "taskRefName": "human_ref",
    "eventType": "HUMAN_TASK_PENDING"
  }
},
{
  "name": "human_approval",
  "taskReferenceName": "human_ref",
  "type": "HUMAN"
}
```

Then configure an event handler or external consumer to process these notifications.

### Pattern 4: Complete Task with Callback Output

When completing the Human task, include callback information in the output:

```bash
curl -X POST "{{ server_host }}/api/tasks" \
  -H 'Content-Type: application/json' \
  -d '{
    "taskId": "${taskId}",
    "status": "COMPLETED",
    "output": {
      "approvedBy": "user@example.com",
      "approvedAt": "2026-04-22T10:30:00Z",
      "comments": "Approved for production deployment"
    }
  }'
```

The output becomes available to downstream tasks and can be used for audit trails or further notifications.

### Pattern 5: External System Integration

For real-time notifications, integrate with external systems:

1. **Slack/Teams**: Use an event handler to trigger a notification workflow that posts to Slack/Teams webhooks
2. **Email**: Send email notifications via SMTP or email service APIs
3. **SMS/Push**: Integrate with Twilio, Pushover, or similar services
4. **Custom Webhooks**: POST to your internal systems when human tasks are pending

### Best Practices

- **Use correlation IDs**: Include `workflowId` and `taskRefName` in all notifications for easy tracking
- **Set timeouts**: Consider adding timeout logic to escalate unapproved human tasks
- **Audit trail**: Log all human task completions with timestamps and user information
- **Idempotency**: Ensure notification handlers are idempotent to handle duplicate events

## Example: Complete Notification Flow

```json
{
  "name": "approval_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "send_approval_request",
      "taskReferenceName": "send_request_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "method": "POST",
          "url": "https://hooks.slack.com/services/xxx",
          "body": {
            "text": "Approval needed for workflow ${workflow.input.requestId}"
          }
        }
      }
    },
    {
      "name": "wait_for_approval",
      "taskReferenceName": "approval_ref",
      "type": "HUMAN"
    },
    {
      "name": "send_approval_result",
      "taskReferenceName": "send_result_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "method": "POST",
          "url": "https://hooks.slack.com/services/xxx",
          "body": {
            "text": "Approval ${approval_ref.output.status} by ${approval_ref.output.approvedBy}"
          }
        }
      }
    }
  ]
}
```

This workflow:
1. Sends a Slack notification when approval is needed
2. Waits for human approval
3. Sends a follow-up notification with the approval result