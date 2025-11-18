# Handling Workflow Errors

In Conductor there are several ways to handle workflow failure automatically:

* Set a compensation flow in the workflow definition.
* Configure workflow status notifications by implementing Workflow Status Listener.

## Set a compensation flow

You can configure a workflow to automatically run upon failure by adding the `failureWorkflow` parameter to your main workflow definition:

```json
"failureWorkflow": "<name of your compensation flow>",
```
If your main workflow fails, Conductor will trigger this failure workflow.

By default, the following parameters will be passed to the failure workflow:

* **`reason`**—The reason for workflow's failure.
* **`workflowId`**—The failed workflow's execution ID.
* **`failureStatus`**—The failed workflow's status.
* **`failureTaskId`**—The execution ID for task that failed in the workflow.
* **`failedWorkflow`**—The workflow execution JSON for the failed workflow.

You can use these parameters to implement compensation actions in the failure workflow, like notification alerts or clean-up.

**Example**

Here is a sample failure workflow that sends a Slack message when the main workflow fails. It posts the `reason` and `workflowId` to enable relevant teams to debug the failure:

```json
{
  "name": "shipping_failure",
  "description": "workflow for failures with Bobs widget workflow",
  "version": 1,
  "tasks": [
    {
      "name": "slack_message",
      "taskReferenceName": "send_slack_message",
      "inputParameters": {
        "http_request": {
          "headers": {
            "Content-type": "application/json"
          },
          "uri": "https://hooks.slack.com/services/<_unique_Slack_generated_key_>",
          "method": "POST",
          "body": {
            "text": "workflow: ${workflow.input.workflowId} failed. ${workflow.input.reason}"
          },
          "connectionTimeOut": 5000,
          "readTimeOut": 5000
        }
      },
      "type": "HTTP",
      "retryCount": 3
    }
  ],
  "restartable": true,
  "workflowStatusListenerEnabled": false,
  "ownerEmail": "conductor@example.com",
  "timeoutPolicy": "ALERT_ONLY",
}
```

##  Implement a Workflow Status Listener 

Using a Workflow Status Listener, you can send a notification to an external system or an event to Conductor's internal queue upon failure. Here is the high-level overview for using a Workflow Status Listener:

1. Set the `workflowStatusListenerEnabled` parameter to true in your main workflow definition:
  ```json
  "workflowStatusListenerEnabled": true,
  ```
2. Implement the [WorkflowStatusListener interface](https://github.com/conductor-oss/conductor/blob/1be02a711dc20682718c6111c09d2b02ce7edde2/core/src/main/java/com/netflix/conductor/core/listener/WorkflowStatusListener.java#L20) to plug into a custom notification or eventing system upon workflow failure. 
