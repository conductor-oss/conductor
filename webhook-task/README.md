# conductor-webhook-task

Provides the `WAIT_FOR_WEBHOOK` system task for Conductor OSS.

## Overview

`WAIT_FOR_WEBHOOK` pauses a workflow at a task and waits for an inbound HTTP webhook event
before continuing. When a matching event arrives at `POST /webhook/{id}`, the task is completed
with the webhook payload as its output.

The module also supports triggering new workflow instances when a webhook fires, via
`workflowsToStart` on the webhook configuration.

## Quick start

### 1. Define a webhook configuration

```http
POST /api/metadata/webhook
Content-Type: application/json

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

The response body includes the auto-assigned `id` — e.g. `"id": "abc-123"`.  Use this id as
the inbound webhook URL: `POST /webhook/abc-123`.

### 2. Add a `WAIT_FOR_WEBHOOK` task to your workflow

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

### 3. Send the webhook event

```http
POST /webhook/abc-123
X-Webhook-Secret: my-secret-value
Content-Type: application/json

{"event": {"type": "payment.completed"}, "data": {"orderId": "ORDER-42"}}
```

The task matching `orderId = ORDER-42` is completed immediately. The workflow continues with the
inbound JSON keys directly on `waitForPayment.output` — e.g. `${waitForPayment.output.event.type}`.

---

## Task input reference

### `matches`

A map of **JSONPath expression → expected value**. Both keys and values are resolved at task
start:

- Keys are JSONPath expressions evaluated against the inbound payload (e.g.
  `$['event']['type']`).
- Values are either:
  - **Literal strings** — the extracted value must equal this exactly
    (case-insensitive, trimmed).
  - **Workflow variable references** (`${workflow.input.orderId}`) — resolved to the actual
    value at task-start time. The resolved value is used as the exact match target; the task
    only completes when the inbound payload contains that specific value.

A task is only completed when **all** entries in `matches` are satisfied simultaneously.

### Task output

The inbound body (parsed as JSON) merged with any query parameters, spread directly into the task's output data:

```json
{
  "event": { "type": "payment.completed" },
  "data": { "orderId": "ORDER-42" }
}
```

Keys from the inbound payload are accessible as `${taskRef.output.<key>}` (e.g.
`${waitForPayment.output.event.type}`). This matches Orkes Enterprise behavior.

---

## Webhook configuration reference

| Field | Type | Description |
|---|---|---|
| `name` | string | Human-readable name for the webhook |
| `id` | string | Auto-assigned UUID. Appears in the inbound URL: `/webhook/{id}` |
| `verifier` | enum | Verification strategy. See [Verifiers](#verifiers) |
| `headers` | map | Required headers for `HEADER_BASED` verifier |
| `headerKey` | string | Header name carrying the signature for `HMAC_BASED` / `SIGNATURE_BASED` |
| `secretValue` | string | Secret used in signature verification. **Masked as `***` in list responses.** |
| `receiverWorkflowNamesToVersions` | map | `workflowName → version` pairs. WAIT_FOR_WEBHOOK tasks in these workflow definitions receive matching events. |
| `workflowsToStart` | map | `workflowName → version` pairs. New workflow instances are started on each matching event, with the inbound payload as input. Also supports reserved keys `idempotencyKey` and `idempotencyStrategy` (Orkes Enterprise only). |
| `expression` | string | Optional evaluator expression for dynamic workflow selection (future). |
| `evaluatorType` | string | Evaluator type for `expression`, e.g. `javascript` (future). |
| `sourcePlatform` | string | Informational label (e.g. `"stripe"`, `"github"`). |
| `createdBy` | string | User who created the config. Set by the REST layer if available. |
| `urlVerified` | boolean | Set to `true` after the first successful verification or ping handshake. Read-only via API. |

At least one of `expression`, `receiverWorkflowNamesToVersions`, or `workflowsToStart` must be
provided.

---

## Verifiers

### `HEADER_BASED`

The simplest verifier. Every key/value pair in `headers` must be present in the inbound request
with an exact value match. Suitable for shared-secret header schemes.

```json
{
  "verifier": "HEADER_BASED",
  "headers": {
    "X-Webhook-Secret": "my-secret"
  }
}
```

Requires: `headers` must be non-empty.

### `HMAC_BASED`

Verifies a HMAC-SHA256 signature. The signature is computed over the request body using the
configured `secretValue` and compared to the value in the header named by `headerKey`.
Ported from Orkes Enterprise; implementation in a future PR.

### Other verifier types

`SIGNATURE_BASED`, `SLACK_BASED`, `STRIPE`, `TWITTER`, `SENDGRID` — defined in the enum for
round-trip compatibility with Orkes Enterprise configurations. Not yet implemented in OSS.

### Adding a custom verifier

Register a Spring bean implementing `WebhookVerifier`. It will be auto-discovered and selected
when a webhook config's `verifier` field matches the value returned by `getType()`.

```java
@Component
public class MyHmacVerifier implements WebhookVerifier {
    @Override
    public String getType() { return "HMAC_BASED"; }

    @Override
    public List<String> verify(WebhookConfig config, IncomingWebhookEvent event) {
        // ... return empty list on success, error messages on failure
    }
}
```

---

## REST API

### Webhook configuration (metadata)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/metadata/webhook` | Create webhook configuration |
| `GET` | `/api/metadata/webhook` | List all webhook configurations (secrets masked) |
| `GET` | `/api/metadata/webhook/{id}` | Get single webhook configuration |
| `PUT` | `/api/metadata/webhook/{id}` | Update webhook configuration |
| `DELETE` | `/api/metadata/webhook/{id}` | Delete webhook configuration |

### Inbound webhook endpoint

| Method | Path | Description |
|---|---|---|
| `POST` | `/webhook/{id}` | Receive inbound webhook event |
| `GET` | `/webhook/{id}` | Handle ping / URL verification challenge |

The `GET` endpoint is used by providers (e.g. Slack) that send a challenge request to verify
that the URL is live before sending real events.

---

## How routing works

The routing mechanism is designed to match the Orkes Enterprise implementation exactly, enabling
drop-in replacement.

### Hash format

```
workflowName;version;taskRefName;value1;value2;...
```

Values are the resolved `matches` values, sorted by JSONPath key (ascending lexicographic order).

### At task-start time

`WaitForWebhookTask.start()` computes:
```
hash = workflowName + ";" + version + ";" + taskRefName + ";" + sorted(resolvedValues)
```

The hash is stored in `WebhookTaskDAO` as `hash → taskId` and written to the task's input
under `__webhookHash` for use during cancellation.

DO_WHILE iteration suffixes (`__1`, `__2`) are stripped from `taskRefName` so that the hash
is stable across loop iterations.

### Matcher index (pre-computed on config create/update)

`WebhookConfigService` scans the workflow definitions listed in
`receiverWorkflowNamesToVersions` and builds a matcher index:

```
key   = workflowName;version;taskRefName   (base hash prefix)
value = unresolved matches map from the task definition
        e.g. {"$['event']['type']": "payment.completed",
               "$['data']['orderId']": "${workflow.input.orderId}"}
```

The unresolved template is used — `${...}` references starting with `$` are treated as
wildcards at inbound-event time (any extracted value is accepted).

### At inbound event time

`IncomingWebhookService.handleWebhook()`:

1. Verifies the request.
2. For each stored matcher `(baseKey → criteria)`:
   - Evaluates each JSONPath in `criteria` against the inbound payload.
   - If the extracted value matches the expected value (or is a wildcard), appends it to the
     hash with `;` separator.
   - If all criteria match, the full hash equals the registration hash → task found.
3. Completes matching tasks via `WorkflowExecutor.updateTask()`.
4. Starts any workflows in `workflowsToStart` with the payload as input.

---

## Extension points (Orkes Enterprise adapter pattern)

All enterprise-specific concerns are isolated behind Spring interfaces with OSS no-op defaults.
Orkes Enterprise overrides these with durable implementations as Spring beans.

### `WebhookTaskDAO`

Stores `hash → Set<taskId>`. Default: `InMemoryWebhookTaskDAO` (lost on restart).

```java
@Component
public class MyDurableWebhookTaskDAO implements WebhookTaskDAO { ... }
```

### `WebhookConfigDAO`

Stores webhook configurations and matcher indexes. Default: `InMemoryWebhookConfigDAO`.

### `WebhookOrgContextProvider`

Called at the start of every inbound request. OSS default: no-op. Orkes Enterprise
implementation extracts `orgId` from the webhook ID (encoded by `TimeBasedUUIDGenerator`) and
sets it on `OrkesRequestContext` so that DAO calls are correctly tenant-scoped.

```java
@Bean
public WebhookOrgContextProvider orkesWebhookOrgContextProvider() {
    return webhookId -> {
        String orgId = TimeBasedUUIDGenerator.getOrgId(webhookId);
        OrkesRequestContext.get().setOrgId(orgId);
    };
}
```

### `WebhookVerifier` (custom verifiers)

Register any additional verifiers as Spring beans implementing `WebhookVerifier`.

---

## Orkes Enterprise convergence notes

This module is designed to be a drop-in replacement for the Orkes Enterprise webhook
implementation. Key design decisions:

| Concern | OSS approach | Orkes Enterprise approach |
|---|---|---|
| orgId | `WebhookOrgContextProvider` (no-op) | Extracts from time-based UUID, sets `OrkesRequestContext` |
| Storage | In-memory (single-node) | Postgres / Redis with `org_id` partition in every query |
| Processing | Synchronous (inline in HTTP request) | Async via `_webhook_queue` + `WebhookWorker` thread pool |
| Matcher index | Pre-computed by `WebhookConfigService` via `MetadataDAO` | Pre-computed by `PostgresWebhookDAO.createMatchers()` |
| Hash format | Identical: `workflowName;version;taskRefName;sortedValues` | Same |
| Idempotency | Not supported (keys are stripped) | Supported via `StartWorkflowRequest.idempotencyKey` |
| Expression eval | Not supported (stored for round-trip only) | JavaScript evaluator via `TargetWorkflowCollector` |

---

## Implementation status

| PR | Scope | Status |
|----|-------|--------|
| PR 1 | Task type, mapper, system task, DAO interface + in-memory impl | ✅ merged |
| PR 2 (#889) | `WebhookConfig` model + CRUD API | ✅ merged |
| PR 3+4 (#890, #892) | Hash computation, `WebhookVerifier` / `HeaderBasedVerifier`, inbound endpoint + task completion | ✅ merged |
| PR 5 (#891) | `workflowsToStart` — trigger new workflows from webhooks | ✅ merged |
| PR 6 (#893) | Reference documentation | ✅ merged |

See epic: https://github.com/conductor-oss/conductor/issues/888

---

## Example workflow

`src/test/resources/examples/order_payment_workflow.json` — an order processing workflow
that pauses at a payment step and resumes when the payment provider POSTs a callback.
