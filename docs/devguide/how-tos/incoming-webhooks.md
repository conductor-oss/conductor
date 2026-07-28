---
description: Configure Orkes webhooks to verify HTTP callbacks, start workflows, resume WAIT_FOR_WEBHOOK tasks, or do both.
---

# Incoming webhooks

> **Orkes only.** Incoming webhooks are an HTTP ingress feature. They are not OSS event handlers and do not expose the event-handler action list.

<section class="concept-hero concept-hero--event-bus" aria-labelledby="webhook-title">
  <div class="concept-hero__content">
    <p class="concept-hero__eyebrow">Verified HTTP ingress</p>
    <h2 id="webhook-title">Verify a callback before durable processing</h2>
    <p>An Orkes webhook verifies the provider callback, records it durably, then can start a workflow, resume a matching <code>WAIT_FOR_WEBHOOK</code>, or use both compatible delivery modes.</p>
    <p><a href="consume-route-events.html">Consume broker events instead</a> · <a href="../cookbook/sending-signals.html">Signal a known WAIT</a></p>
  </div>
  <svg class="concept-hero__graphic event-hero__graphic" viewBox="0 0 440 190" role="img" aria-labelledby="webhook-svg-title webhook-svg-desc" xmlns="http://www.w3.org/2000/svg">
    <title id="webhook-svg-title">Incoming webhook processing flow</title>
    <desc id="webhook-svg-desc">An external provider sends an HTTP callback to an Orkes webhook. After verification and durable processing, the configuration can start a workflow, resume a Wait for Webhook task, or do both.</desc>
    <defs><marker id="webhook-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="currentColor"/></marker></defs>
    <rect x="14" y="68" width="98" height="54" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="63" y="91" text-anchor="middle" class="concept-hero__label">Provider</text><text x="63" y="108" text-anchor="middle" class="concept-hero__detail">HTTP callback</text>
    <path d="M112 95 H148" class="concept-hero__line" marker-end="url(#webhook-arrow)"/>
    <rect x="156" y="58" width="119" height="74" rx="10" class="concept-hero__node concept-hero__node--accent"/><text x="216" y="83" text-anchor="middle" class="concept-hero__label">Orkes webhook</text><text x="216" y="100" text-anchor="middle" class="concept-hero__detail">verify + persist</text><text x="216" y="117" text-anchor="middle" class="concept-hero__detail">configured delivery</text>
    <path d="M275 81 H312" class="concept-hero__line" marker-end="url(#webhook-arrow)"/>
    <path d="M275 109 H295 V146 H312" class="concept-hero__line" marker-end="url(#webhook-arrow)"/>
    <rect x="320" y="55" width="106" height="44" rx="10" class="concept-hero__node event-hero__node--action"/><text x="373" y="82" text-anchor="middle" class="concept-hero__label">Start workflow</text>
    <rect x="320" y="124" width="106" height="44" rx="10" class="concept-hero__node event-hero__node--action"/><text x="373" y="145" text-anchor="middle" class="concept-hero__label">Resume WAIT</text><text x="373" y="160" text-anchor="middle" class="concept-hero__detail">FOR_WEBHOOK</text>
  </svg>
</section>

## Endpoints and lifecycle

Webhook delivery uses these routes relative to the Conductor API base URL:

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/webhook/{id}` | Receive a callback body, query parameters, and headers |
| `GET` | `/webhook/{id}` | Handle a provider URL-verification or ping request |
| `POST` | `/metadata/webhook` | Create a webhook configuration |
| `GET` | `/metadata/webhook` | List configurations |
| `GET` | `/metadata/webhook/{id}` | Read a configuration |
| `PUT` | `/metadata/webhook/{id}` | Update a configuration |
| `DELETE` | `/metadata/webhook/{id}` | Delete a configuration |

For example, if the API base URL is `https://tenant.orkesconductor.com/api`, give the provider `https://tenant.orkesconductor.com/api/webhook/<webhook-id>`.

The inbound request is verified before it is accepted for processing. The recorded event and queue make delivery durable across worker restarts; processing then evaluates the configuration, starts any configured workflows, and matches eligible `WAIT_FOR_WEBHOOK` tasks. Inspect the webhook/event records and the resulting workflow or task state when diagnosing a delivery.

## Choose a delivery mode

Webhook configuration can apply either or both effects to one verified callback:

- **Start:** launch each configured receiver workflow.
- **Resume:** match and advance eligible `WAIT_FOR_WEBHOOK` tasks.
- **Both:** start the configured workflows and resume matching waits from the same durable callback.

Choose the mode from the state you need to create or advance; a webhook is not an event-handler action dispatcher.

## Configure without exposing secrets

The configuration identifies the verifier, optional expected headers, receiver workflow versions or workflows to start, and matching behavior. Keep verifier material in the Orkes secrets system and reference it; never put a signing secret, HMAC key, or private key literal in a workflow or documentation example.

```json
{
  "name": "payment-provider-callback",
  "sourcePlatform": "Custom",
  "verifier": "HMAC_BASED",
  "headerKey": "X-Provider-Signature",
  "secretValue": "${workflow.secrets.PAYMENT_WEBHOOK_SECRET}",
  "receiverWorkflowNamesToVersions": {
    "process_payment_callback": 1
  }
}
```

Use the secret-reference form supported by the Orkes environment rather than copying an actual secret into the configuration. Treat callback payloads and headers as potentially sensitive too.

## Verifier choices

| Verifier | Verification input | GET challenge / ping behavior |
|---|---|---|
| `HEADER_BASED` | Every configured header must be present exactly once and equal its configured value. | No provider challenge behavior. |
| `SIGNATURE_BASED` | A configured header contains `sha256=` plus an HMAC-SHA-256 of the raw body using the configured secret. | No provider challenge behavior. |
| `HMAC_BASED` | A configured header carries the HMAC-SHA-256 of the raw body; the configured secret is Base64-decoded before verification. | No provider challenge behavior. |
| `SLACK_BASED` | `X-Slack-Signature`, `X-Slack-Request-Timestamp`, and the raw body; the timestamp is replay-window checked. | Returns Slack's JSON `challenge` value during URL verification. |
| `STRIPE` | `Stripe-Signature`, raw body, and the Stripe signing secret. | No provider challenge behavior. |
| `TWITTER` | Configured signature header and raw body, using the Twitter HMAC encoding. | On `crc_token`, returns a `response_token` signed with the configured secret. |
| `SENDGRID` | SendGrid event-webhook signature and timestamp headers, raw body, and the configured ECDSA public key. | No provider challenge behavior. |

Verification is a security boundary, not an authorization model for arbitrary workflow actions. Limit each webhook configuration to the workflows and task matches it genuinely needs.

## Webhooks versus event handlers

An [event handler](consume-route-events.md) subscribes to a broker event and can dispatch its documented actions. An incoming webhook receives HTTP and only carries out the webhook configuration's workflow-start and `WAIT_FOR_WEBHOOK` matching behavior. Do not model a webhook as a way to invoke `complete_task`, `fail_task`, `terminate_workflow`, or `update_workflow_variables` actions.

For a broker message instead of an HTTP callback, use [Consume and route events](consume-route-events.md). To complete the current `WAIT` in a known workflow directly, use [Sending signals to workflows](../cookbook/sending-signals.md).

## Next steps

<div class="event-next-steps">
  <a href="consume-route-events.html">Route broker events →</a>
  <a href="../cookbook/sending-signals.html">Signal a WAIT task →</a>
  <a href="event-bus.html">Return to the overview →</a>
</div>
