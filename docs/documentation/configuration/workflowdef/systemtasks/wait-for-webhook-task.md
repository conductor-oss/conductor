---
description: "Configure WAIT_FOR_WEBHOOK tasks in Conductor to pause workflow execution until a matching incoming HTTP webhook arrives. Supports HMAC, Slack, Stripe, Twitter, SendGrid, and custom signature verifiers."
---

# Wait For Webhook Task
```json
"type" : "WAIT_FOR_WEBHOOK"
```

The Wait For Webhook task (`WAIT_FOR_WEBHOOK`) pauses workflow execution until an HTTP webhook delivery arrives that matches the task's expected payload criteria. The task stays `IN_PROGRESS` until a matching event is received, at which point it is marked `COMPLETED` with the webhook payload as its output.

## How it works

1. The workflow reaches the `WAIT_FOR_WEBHOOK` task. Conductor registers it in an in-memory hash keyed by `workflowName;version;taskReferenceName + matches`.
2. An external system POSTs an event to `POST /api/webhook/{webhookId}`.
3. The signature is verified using the registered webhook config's verifier.
4. The verified event is enqueued; a background worker picks it up.
5. The worker recomputes the hash for each registered matcher and completes any waiting task whose hash matches the event payload.

The webhook must be registered first via `POST /api/metadata/webhook` (see below).

## Task parameters

Use these parameters inside `inputParameters`:

| Parameter | Type | Description | Required / Optional |
| --- | --- | --- | --- |
| matches | Map<String, Object> | Key/value criteria the incoming webhook payload must contain for this task to complete. The keys are JSONPath-flat field names; values are matched stringwise (stable for strings, numbers, booleans). Required. | Required |

## JSON configuration

```json
{
  "name": "wait_for_payment",
  "taskReferenceName": "wait_payment_ref",
  "type": "WAIT_FOR_WEBHOOK",
  "inputParameters": {
    "matches": {
      "event": "payment.completed",
      "customer_id": "cus_123"
    }
  }
}
```

## Registering a webhook config

Before a webhook can deliver to a workflow, register a config that tells Conductor (a) which verifier to use, (b) which workflow(s) the event targets.

### Endpoint

`POST /api/metadata/webhook` — see `WebhookConfigResource.java`.

### Body

```json
{
  "name": "stripe-payments",
  "verifier": "HMAC_BASED",
  "headerKey": "X-Signature",
  "secretKey": "X-Signature",
  "secretValue": "<base64-encoded HMAC key>",
  "sourcePlatform": "stripe",
  "receiverWorkflowNamesToVersions": {
    "payment_processing_workflow": 1
  }
}
```

Either `receiverWorkflowNamesToVersions` (complete existing `WAIT_FOR_WEBHOOK` tasks in those workflows) or `workflowsToStart` (kick off a brand-new workflow) is required.

### Available verifiers

`WebhookConfig.Verifier` enum, from `common/src/main/java/org/conductoross/conductor/webhook/model/WebhookConfig.java`:

| Verifier | Use for |
| --- | --- |
| `HMAC_BASED` | Generic HMAC-SHA256 signature in a configurable header. `secretValue` is **base64-encoded key bytes**. |
| `SIGNATURE_BASED` | Custom signature scheme with shared secret. |
| `HEADER_BASED` | Match a literal header value (lowest security). |
| `SLACK_BASED` | Slack's `X-Slack-Signature` scheme. |
| `STRIPE` | Stripe's `Stripe-Signature` scheme. |
| `TWITTER` | Twitter's CRC-based handshake + signed events. |
| `SENDGRID` | SendGrid's ECDSA event signature. |

## Delivering a webhook

`POST /api/webhook/{id}` — see `IncomingWebhookResource.java`. Body and headers are passed through to the configured verifier.

Successful verification returns HTTP 200 with the verifier-provided challenge response (or empty for normal deliveries). Returns:

- **404** if the webhook id is unregistered.
- **500** with `NonTransientException` if signature verification fails.

## End-to-end example

```shell
# 1. Register the webhook config (base64-encoded secret "sekret-key" = "c2VrcmV0LWtleQ==")
curl -X POST http://localhost:8080/api/metadata/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "name": "smoke-hook",
    "verifier": "HMAC_BASED",
    "headerKey": "X-Sig",
    "secretKey": "X-Sig",
    "secretValue": "c2VrcmV0LWtleQ==",
    "sourcePlatform": "smoke",
    "workflowsToStart": {"wf-smoke": 1}
  }'
# → {"name":"smoke-hook","id":"<webhook-id>",...}

# 2. Register the target workflow def
curl -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d '{
    "name": "wf-smoke",
    "version": 1,
    "tasks": [{
      "name": "echo",
      "taskReferenceName": "echo_ref",
      "type": "INLINE",
      "inputParameters": {
        "evaluatorType": "javascript",
        "expression": "function e(){return $.payload}e();"
      }
    }],
    "schemaVersion": 2,
    "timeoutSeconds": 60
  }'

# 3. Deliver an event
BODY='{"event":"smoke-fired"}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "sekret-key" -binary | base64)
curl -X POST "http://localhost:8080/api/webhook/<webhook-id>" \
  -H "Content-Type: application/json" \
  -H "X-Sig: $SIG" \
  -d "$BODY"
# → HTTP 200, empty body. Background worker dequeues and starts wf-smoke v1.
```

## Worker tuning

`WebhookWorkerProperties` exposes Spring properties under `conductor.webhook.worker.*`:

| Property | Default | Description |
| --- | --- | --- |
| `conductor.webhook.worker.threadCount` | 1 | Number of poller threads. Set to 0 to disable the worker (useful in tests). |
| `conductor.webhook.worker.pollingInterval` | 1000 | Milliseconds between polls of the `_webhook_queue`. |
| `conductor.webhook.worker.pollBatchSize` | 10 | Max messages popped per poll. |
| `conductor.webhook.worker.lastRunWorkflowIdSize` | 10 | Cap on per-config execution-history retention. |

## Failure semantics

- **Verification failure**: returns 500, event is NOT enqueued. Caller should retry with a corrected signature.
- **Worker dispatch failure** (DB blip, OOM, etc.): the message is NOT acked, and the underlying queue's unack timeout will redeliver. Poison messages eventually hit the queue impl's dead-letter behavior (varies by `conductor.queue.type`).
- **Rejected events** are logged at WARN level. Persistent audit-log capture is a planned enhancement.

## Deployment: authentication and network exposure

The webhook configuration endpoints — `POST/PUT/DELETE/GET /api/metadata/webhook` — follow conductor OSS's standard model: **the OSS server ships without authentication**, and securing it is a deployment concern.

- The `/api/metadata/webhook` endpoints will create, modify, delete, or read webhook configurations for any caller that can reach them on the network. Anyone with reachability can register a webhook (and its secret) or delete an existing one.
- The inbound `/api/webhook/{id}` event endpoint is signature-verified per the webhook's configured verifier — see the table above — but it does not check caller identity beyond that.

When deploying conductor with webhooks enabled, place the server behind an authenticating proxy or API gateway (mTLS, OAuth-bearer, IP allowlist, or your platform's equivalent) and restrict `/api/metadata/*` to operator identities. Treat `/api/webhook/*` as the public-facing surface and `/api/metadata/webhook` as the operator-facing surface — they have different exposure requirements.
