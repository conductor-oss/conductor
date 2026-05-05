---
description: "Handle workflow errors in Conductor using the saga pattern with compensation flows, retry strategies, task-level error handling, timeout policies, and workflow status listener notifications."
---

# Handling Workflow Errors

In production microservice architectures, failures are inevitable. Conductor provides multiple layers of error handling so you can build resilient, self-healing workflows:

* **Saga pattern** — run a compensation flow to undo completed steps when a workflow fails.
* **Retry strategies** — automatically retry failed tasks with configurable backoff.
* **Task-level error handling** — mark tasks as optional, fail immediately on terminal errors, or set per-task timeouts.
* **Timeout policies** — control what happens when a task or workflow exceeds its time limit.
* **Workflow status listener** — send notifications to external systems on workflow completion or failure.

## Saga pattern: compensation on failure

The saga pattern is a well-established approach for managing distributed transactions across microservices. Instead of a single atomic transaction that spans multiple services, a saga breaks the work into a sequence of local transactions. Each step has a corresponding **compensating action** that undoes its effect. When any step in the sequence fails, the previously completed steps are rolled back in reverse order by executing their compensating actions.

This pattern is essential in microservice architectures where two-phase commits are impractical. Because each service owns its own data, you cannot rely on a traditional database transaction to maintain consistency across services. The saga pattern gives you eventual consistency with explicit rollback logic, making failures predictable and recoverable.

### Configuring a failure workflow

You can configure a workflow to automatically run a compensation flow upon failure by adding the `failureWorkflow` parameter to your main workflow definition:

```json
"failureWorkflow": "<name of your compensation flow>",
```

If your main workflow fails, Conductor will trigger this failure workflow. By default, the following parameters are passed to the failure workflow as input:

* **`reason`** — The reason for the workflow's failure.
* **`workflowId`** — The failed workflow's execution ID.
* **`failureStatus`** — The failed workflow's status.
* **`failureTaskId`** — The execution ID for the task that failed in the workflow.
* **`failedWorkflow`** — The full workflow execution JSON for the failed workflow.

You can use these parameters to implement compensation actions in the failure workflow, such as notification alerts, resource clean-up, or reversing completed transactions.

### Example: Slack notification on failure

Here is a failure workflow that sends a Slack message when the main workflow fails. It posts the `reason` and `workflowId` so the team can debug the failure:

```json
{
  "name": "shipping_failure",
  "description": "Notification workflow for shipping workflow failures",
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
  "timeoutPolicy": "ALERT_ONLY"
}
```

### Example: saga compensation for order processing

A realistic saga implementation involves a main workflow that processes an order through multiple services and a compensation workflow that reverses each completed step if any step fails.

**Main workflow** — `order_processing` processes a customer order through three stages: charge the payment, reserve inventory, and arrange shipping.

```json
{
  "name": "order_processing",
  "description": "Process a customer order through payment, inventory, and shipping",
  "version": 1,
  "failureWorkflow": "order_compensation",
  "tasks": [
    {
      "name": "charge_payment",
      "taskReferenceName": "charge_payment_ref",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}",
        "customerId": "${workflow.input.customerId}",
        "amount": "${workflow.input.totalAmount}"
      },
      "type": "SIMPLE",
      "retryCount": 2,
      "retryLogic": "EXPONENTIAL_BACKOFF",
      "retryDelaySeconds": 5
    },
    {
      "name": "reserve_inventory",
      "taskReferenceName": "reserve_inventory_ref",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}",
        "items": "${workflow.input.items}",
        "paymentTransactionId": "${charge_payment_ref.output.transactionId}"
      },
      "type": "SIMPLE",
      "retryCount": 2,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 3
    },
    {
      "name": "arrange_shipping",
      "taskReferenceName": "arrange_shipping_ref",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}",
        "shippingAddress": "${workflow.input.shippingAddress}",
        "items": "${workflow.input.items}",
        "inventoryReservationId": "${reserve_inventory_ref.output.reservationId}"
      },
      "type": "SIMPLE",
      "retryCount": 1,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 10
    }
  ],
  "restartable": true,
  "workflowStatusListenerEnabled": true,
  "ownerEmail": "order-team@example.com",
  "timeoutPolicy": "TIME_OUT_WF",
  "timeoutSeconds": 600
}
```

**Compensation workflow** — `order_compensation` reverses each completed step in reverse order: cancel the shipment, restore inventory, and refund the payment.

```json
{
  "name": "order_compensation",
  "description": "Undo completed order steps when order_processing fails",
  "version": 1,
  "tasks": [
    {
      "name": "cancel_shipment",
      "taskReferenceName": "cancel_shipment_ref",
      "inputParameters": {
        "orderId": "${workflow.input.failedWorkflow.input.orderId}",
        "shipmentId": "${workflow.input.failedWorkflow.tasks[arrange_shipping_ref].output.shipmentId}"
      },
      "type": "SIMPLE",
      "optional": true,
      "retryCount": 3,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 5
    },
    {
      "name": "restore_inventory",
      "taskReferenceName": "restore_inventory_ref",
      "inputParameters": {
        "orderId": "${workflow.input.failedWorkflow.input.orderId}",
        "reservationId": "${workflow.input.failedWorkflow.tasks[reserve_inventory_ref].output.reservationId}"
      },
      "type": "SIMPLE",
      "optional": true,
      "retryCount": 3,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 5
    },
    {
      "name": "refund_payment",
      "taskReferenceName": "refund_payment_ref",
      "inputParameters": {
        "orderId": "${workflow.input.failedWorkflow.input.orderId}",
        "transactionId": "${workflow.input.failedWorkflow.tasks[charge_payment_ref].output.transactionId}",
        "amount": "${workflow.input.failedWorkflow.input.totalAmount}"
      },
      "type": "SIMPLE",
      "retryCount": 5,
      "retryLogic": "EXPONENTIAL_BACKOFF",
      "retryDelaySeconds": 10
    }
  ],
  "restartable": true,
  "workflowStatusListenerEnabled": false,
  "ownerEmail": "order-team@example.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 1200
}
```

Notice that compensation tasks are marked `optional: true` for steps that may not have completed before the failure occurred. The refund task uses aggressive retries with exponential backoff because it is critical that the customer receives their money back.

## Retry strategies

When a task fails, Conductor can automatically retry it according to the retry logic configured on the task definition. You control the retry behavior with three parameters:

* **`retryCount`** — Maximum number of retry attempts.
* **`retryLogic`** — The backoff strategy between retries.
* **`retryDelaySeconds`** — The base delay between retries, in seconds.

### FIXED

Retries at a constant interval. Every retry waits the same amount of time.

```json
{
  "retryCount": 3,
  "retryLogic": "FIXED",
  "retryDelaySeconds": 5
}
```

This retries up to 3 times, waiting exactly 5 seconds between each attempt.

### EXPONENTIAL_BACKOFF

Each retry waits exponentially longer than the previous one. The delay is calculated as `retryDelaySeconds * 2^(attemptNumber)`. This reduces load on downstream services that may be experiencing pressure.

```json
{
  "retryCount": 4,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 2
}
```

This retries up to 4 times with delays of approximately 2, 4, 8, and 16 seconds.

### LINEAR_BACKOFF

Each retry waits incrementally longer by a fixed amount. The delay is calculated as `retryDelaySeconds * attemptNumber`. This provides a gentler ramp-up than exponential backoff.

```json
{
  "retryCount": 4,
  "retryLogic": "LINEAR_BACKOFF",
  "retryDelaySeconds": 5
}
```

This retries up to 4 times with delays of approximately 5, 10, 15, and 20 seconds.

### Choosing a retry strategy

| Strategy | Delay pattern | Best for |
|---|---|---|
| `FIXED` | Constant (e.g., 5s, 5s, 5s) | Predictable transient failures like brief network blips or short-lived lock contention. |
| `EXPONENTIAL_BACKOFF` | Doubling (e.g., 2s, 4s, 8s, 16s) | Rate-limited APIs, overloaded services, or any case where you want to reduce pressure on a struggling dependency. |
| `LINEAR_BACKOFF` | Incremental (e.g., 5s, 10s, 15s, 20s) | Moderate recovery scenarios where you need longer waits over time but exponential growth would be too aggressive. |

## Task-level error handling

Beyond retries, Conductor provides several task-level controls for managing failures within a running workflow.

### Optional tasks

Setting `optional` to `true` on a task tells Conductor to continue the workflow even if that task fails after exhausting all retries. The workflow will proceed to the next task rather than failing entirely.

```json
{
  "name": "send_analytics_event",
  "taskReferenceName": "send_analytics_ref",
  "type": "SIMPLE",
  "optional": true,
  "retryCount": 2,
  "retryLogic": "FIXED",
  "retryDelaySeconds": 3
}
```

Use optional tasks for non-critical side effects like logging, analytics, or notifications where a failure should not block the primary business logic.

### Failing immediately with terminal errors

When a worker encounters an error that no amount of retrying will fix, such as invalid input data or a business rule violation, it should return a `FAILED_WITH_TERMINAL_ERROR` status. This tells Conductor to skip all remaining retries and fail the task immediately.

Workers signal this by setting the task status to `FAILED_WITH_TERMINAL_ERROR` in the task result. This avoids wasting time on retries when the failure is deterministic. For example, if a payment is declined due to insufficient funds, retrying the same charge will never succeed.

### Per-task timeout configuration

You can set timeouts on individual tasks to prevent them from blocking the workflow indefinitely:

```json
{
  "name": "call_external_api",
  "taskReferenceName": "call_api_ref",
  "type": "SIMPLE",
  "timeoutSeconds": 120,
  "responseTimeoutSeconds": 60,
  "timeoutPolicy": "RETRY"
}
```

* **`timeoutSeconds`** — Maximum total time for the task, including all retries.
* **`responseTimeoutSeconds`** — Maximum time to wait for a worker to pick up and respond to the task. If a worker does not update the task within this window, Conductor marks it as timed out.

## Timeout policies

Timeout policies determine what Conductor does when a task exceeds its `timeoutSeconds` or `responseTimeoutSeconds` limit.

### RETRY

Re-queue the task for another attempt. The retry counts against the task's `retryCount`.

```json
{
  "timeoutPolicy": "RETRY",
  "timeoutSeconds": 60,
  "retryCount": 3
}
```

### TIME_OUT_WF

Fail the entire workflow immediately when the task times out. Use this for tasks where a timeout indicates a critical problem that makes continuing the workflow pointless.

```json
{
  "timeoutPolicy": "TIME_OUT_WF",
  "timeoutSeconds": 300
}
```

### ALERT_ONLY

Log an alert but allow the task to continue running. The task is not terminated or retried. This is useful for long-running tasks where you want visibility into slow execution without interrupting work.

```json
{
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 600
}
```

### Choosing a timeout policy

| Policy | Behavior on timeout | Best for |
|---|---|---|
| `RETRY` | Retries the task (counts against `retryCount`) | Tasks that may hang due to transient issues like network timeouts or unresponsive workers. |
| `TIME_OUT_WF` | Fails the entire workflow | Critical tasks where a timeout means the workflow cannot produce a valid result. |
| `ALERT_ONLY` | Logs an alert, task keeps running | Long-running or best-effort tasks where you want monitoring without enforcement. |

## Implement a Workflow Status Listener

Using a Workflow Status Listener, you can send a notification to an external system or an event to Conductor's internal queue upon failure. Here is the high-level overview for using a Workflow Status Listener:

1. Set the `workflowStatusListenerEnabled` parameter to true in your main workflow definition:
    ```json
    "workflowStatusListenerEnabled": true,
    ```
2. Implement the [WorkflowStatusListener interface](https://github.com/conductor-oss/conductor/blob/1be02a711dc20682718c6111c09d2b02ce7edde2/core/src/main/java/com/netflix/conductor/core/listener/WorkflowStatusListener.java#L20) to plug into a custom notification or eventing system upon workflow failure.
