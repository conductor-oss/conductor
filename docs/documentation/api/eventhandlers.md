---
description: REST endpoints and status behavior for OSS Conductor event handlers.
---

# Event Handlers API

The controller is mounted at `/api/event`. Successful mutating operations return an empty `200 OK` response.

## Endpoints

| Method | Path | Request/response |
|---|---|---|
| `POST` | `/api/event` | Create one event-handler object; empty response |
| `PUT` | `/api/event` | Replace/update one handler object; empty response |
| `GET` | `/api/event` | Array of all handlers |
| `DELETE` | `/api/event/{name}` | Remove by handler name; empty response |
| `GET` | `/api/event/{event}?activeOnly=true` | Handlers for the exact event; `activeOnly` defaults to `true` |

The `{event}` path value can contain provider separators and must be URL-encoded when required by the client/proxy.

## Create example

```bash
curl -sS -X POST 'http://localhost:8080/api/event' \
  -H 'Content-Type: application/json' \
  --data-binary @docs/devguide/cookbook/examples/events/start-workflow-handler.json
```

## Handler fields

| Field | Required | Behavior |
|---|---|---|
| `name` | Yes | Non-empty, unique handler name |
| `event` | Yes | `provider:<provider-specific queue URI>`; split at first colon |
| `condition` | No | Evaluated against payload root; omitted means true |
| `actions` | Yes | Non-empty list; actions execute concurrently |
| `active` | No | Defaults to `false` |
| `evaluatorType` | No | Selects a registered evaluator; otherwise the default script evaluator is used |

The shared model declares five enum values, but the OSS action processor implements only `start_workflow`, `complete_task`, and `fail_task`. Requests using `terminate_workflow` or `update_workflow_variables` can deserialize but fail during processing as unsupported.

## Task targeting

For `complete_task` and `fail_task`, provide `taskId`, or `workflowId` plus `taskRefName`. `reasonForIncompletion` is meaningful for `fail_task`. Output fields are expression-resolved from the event payload root.

## Status and delivery behavior

A false condition records `SKIPPED`. Each action has its own persisted event-execution record. Duplicate suppression depends on a stable broker message ID and the persisted record; actions are concurrent and not atomic.

See [Event handler configuration](../configuration/eventhandlers.md) for the data model and [Event orchestration](../../devguide/how-tos/event-bus.md) for provider configuration and operating guidance.
