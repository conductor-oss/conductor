# conductor-webhook-task

Provides the `WAIT_FOR_WEBHOOK` system task for Conductor OSS.

## Overview

`WAIT_FOR_WEBHOOK` allows a workflow to pause at a task and wait for an inbound HTTP webhook event before continuing. When a matching event arrives at `POST /webhook/{id}`, the task is completed with the webhook payload as its output.

This module defines the task type, its lifecycle, and the extension interfaces that allow alternative storage backends to be plugged in.

## Task definition

```json
{
  "name": "wait_for_payment",
  "taskReferenceName": "waitForPayment",
  "type": "WAIT_FOR_WEBHOOK",
  "inputParameters": {
    "matches": {
      "$['event']['type']": "payment.completed",
      "$['data']['orderId']": "${workflow.input.orderId}"
    }
  }
}
```

### `matches`

A map of JSONPath expressions to expected values. Both sides are resolved at task registration time (values may reference workflow input via `${workflow.input.xxx}`). An inbound webhook event is routed to this task only if all expressions evaluate to the expected values against the event payload.

### Output

When completed by a webhook event, the task output contains:

```json
{
  "payload": { ... }
}
```

Where `payload` is the full parsed body of the inbound webhook request.

## Webhook configuration

Webhook configs are managed via `/api/metadata/webhook` (implemented in PR 2 / #889). A config links an inbound webhook endpoint to one or more workflows.

```json
{
  "name": "payment-provider",
  "verifier": "HEADER_BASED",
  "headers": {
    "X-Webhook-Secret": "my-secret-value"
  },
  "receiverWorkflowNamesToVersions": {
    "order_payment_workflow": 1
  }
}
```

## Extension interfaces

### `WebhookTaskDAO`

Stores the `hash → taskId` mapping that routes inbound events to waiting tasks.

```java
public interface WebhookTaskDAO {
    void put(String hash, String taskId);
    List<String> get(String hash);
    void remove(String hash, String taskId);
}
```

The default implementation (`InMemoryWebhookTaskDAO`) stores mappings in memory. **Mappings are lost on server restart.** For multi-node or production-grade deployments, register a durable implementation as a Spring bean — the `@ConditionalOnMissingBean` annotation on `InMemoryWebhookTaskDAO` ensures it will be skipped.

```java
@Component
public class MyWebhookTaskDAO implements WebhookTaskDAO {
    // backed by Postgres, Redis, etc.
}
```

### `WebhookVerifier`

Validates inbound webhook requests before processing them.

```java
public interface WebhookVerifier {
    String getVerifierType();  // matches WebhookConfig.verifier field
    boolean verify(HttpServletRequest request, WebhookConfig config);
    default Optional<String> extractChallenge(HttpServletRequest request) { return Optional.empty(); }
}
```

The default implementation (`HeaderBasedVerifier`) checks that configured header key/value pairs are present in the request. Additional verifiers (e.g., HMAC-based) can be registered as Spring beans and selected per webhook config via the `verifier` field.

## Implementation status

| PR | Scope | Status |
|----|-------|--------|
| PR 1 | Task type, mapper, system task, DAO interface + in-memory impl | ✅ merged |
| PR 2 (#889) | `WebhookConfig` model + CRUD API | ✅ merged |
| PR 3+4 (#890, #892) | Hash computation, `WebhookVerifier` / `HeaderBasedVerifier`, inbound endpoint + task completion | ✅ merged |
| PR 5 (#891) | `workflowsToStart` — trigger new workflows from webhooks | pending |
| PR 6 (#893) | Reference documentation | pending |

See epic: https://github.com/conductor-oss/conductor/issues/888

## Example workflow

`src/test/resources/examples/order_payment_workflow.json` — an order processing workflow that pauses at a payment step and resumes when the payment provider POSTs a callback.
