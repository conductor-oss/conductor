---
description: Receive broker events and webhooks, publish workflow events, or signal a workflow already blocked on WAIT.
---

# Event-Driven Orchestration

<section class="concept-hero concept-hero--event-bus" aria-labelledby="event-overview-title">
  <div class="concept-hero__content">
    <p>Event-driven orchestration connects workflows to the messages around them. A workflow can publish to a broker, an incoming message or webhook can start or advance workflows, and a signal can resume one specific execution that is waiting. Each page in this section covers one of those directions, and the table below routes you to the right one.</p>
  </div>
  <svg class="concept-hero__graphic event-hero__graphic" viewBox="0 0 440 220" role="img" aria-labelledby="event-overview-svg-title event-overview-svg-desc" xmlns="http://www.w3.org/2000/svg">
    <title id="event-overview-svg-title">Event-driven orchestration paths</title>
    <desc id="event-overview-svg-desc">A workflow publishes to a broker, which an event handler can route to a workflow or task. A webhook is verified HTTP ingress, while a signal directly advances a blocked wait task.</desc>
    <defs><marker id="event-overview-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="currentColor"/></marker></defs>
    <rect x="14" y="16" width="103" height="48" rx="10" class="concept-hero__node concept-hero__node--accent"/><text x="66" y="37" text-anchor="middle" class="concept-hero__label">Workflow</text><text x="66" y="53" text-anchor="middle" class="concept-hero__detail">EVENT</text>
    <path d="M117 40 H153" class="concept-hero__line" marker-end="url(#event-overview-arrow)"/>
    <rect x="161" y="16" width="105" height="48" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="214" y="37" text-anchor="middle" class="concept-hero__label">Broker</text><text x="214" y="53" text-anchor="middle" class="concept-hero__detail">topic or queue</text>
    <path d="M266 40 H298" class="concept-hero__line" marker-end="url(#event-overview-arrow)"/>
    <rect x="306" y="16" width="120" height="48" rx="10" class="concept-hero__node event-hero__node--action"/><text x="366" y="37" text-anchor="middle" class="concept-hero__label">Handler</text><text x="366" y="53" text-anchor="middle" class="concept-hero__detail">start or update</text>
    <rect x="14" y="103" width="112" height="48" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="70" y="124" text-anchor="middle" class="concept-hero__label">Webhook</text><text x="70" y="140" text-anchor="middle" class="concept-hero__detail">verified HTTP</text>
    <path d="M126 127 H174" class="concept-hero__line" marker-end="url(#event-overview-arrow)"/>
    <rect x="182" y="103" width="109" height="48" rx="10" class="concept-hero__node event-hero__node--action"/><text x="236" y="124" text-anchor="middle" class="concept-hero__label">Durable work</text><text x="236" y="140" text-anchor="middle" class="concept-hero__detail">start or resume</text>
    <rect x="14" y="172" width="112" height="34" rx="10" class="concept-hero__node event-hero__node--broker"/><text x="70" y="194" text-anchor="middle" class="concept-hero__label">Signal caller</text>
    <path d="M126 189 H174" class="concept-hero__line" marker-end="url(#event-overview-arrow)"/>
    <rect x="182" y="172" width="109" height="34" rx="10" class="concept-hero__node concept-hero__node--accent"/><text x="236" y="194" text-anchor="middle" class="concept-hero__label">Blocked WAIT</text>
    <path d="M291 189 H341" class="concept-hero__line" marker-end="url(#event-overview-arrow)"/>
    <text x="383" y="194" text-anchor="middle" class="concept-hero__detail">continue</text>
  </svg>
</section>

| Need | Start here | Availability |
|---|---|---|
| Publish workflow data to a queue or broker | [Publish events](publish-events.md) | OSS and Orkes |
| Consume a broker message and start or update workflow work | [Consume and route events](consume-route-events.md) | OSS and Orkes |
| Receive an HTTP callback from an external service | [Incoming webhooks](incoming-webhooks.md) | Orkes only |
| Continue a workflow blocked on `WAIT` | [Send signals to workflows](../cookbook/sending-signals.md) | OSS and Orkes |
| Notify external systems when executions change state | [Workflow status events](workflow-status-events.md) | OSS and Orkes |

`EVENT` publishes messages; an event handler consumes and routes them. A webhook is HTTP ingress, not a general-purpose event handler. A signal changes an existing workflow and does not create a new execution.

## Broker provider matrix

Provider support depends on the Conductor distribution and enabled server integration. The destination after the first colon in an event name is provider-specific.

| Provider | OSS Conductor | Orkes |
|---|:---:|:---:|
| Conductor internal queue | Yes | — |
| Kafka | Yes | Yes |
| Amazon SQS | Yes | Yes |
| NATS | Yes | Yes |
| NATS JetStream | Yes | — |
| NATS Streaming | Yes | — |
| AMQP queue / exchange | Yes | Yes (including RabbitMQ) |
| Azure Service Bus | — | Yes |
| Google Cloud Pub/Sub | — | Yes |
| IBM MQ | — | Yes |

## Operate the whole path

Monitor broker queue depth (`event_queue_depth`), message processing (`event_queue_messages_processed`, `event_queue_messages_handled`, and `event_queue_messages_error`), and handler actions (`event_execution_success` and `event_execution_error`). Then check the resulting workflow or task: broker acknowledgement alone does not prove the downstream action reached its intended state.

## Next steps

<div class="event-next-steps">
  <a href="publish-events.html">Publish events →</a>
  <a href="consume-route-events.html">Consume and route events →</a>
  <a href="incoming-webhooks.html">Receive webhooks →</a>
  <a href="../cookbook/sending-signals.html">Send workflow signals →</a>
</div>
