# API behavior — 405 for unsupported HTTP methods (Issue #1393)

Supporting doc for `method-not-allowed-status-architecture.md`. Reuses its names,
types, and file paths verbatim.

## What changes on the wire

Only the **HTTP status code** for wrong-method requests changes: `500` → `405`.
Routes, request bodies, query params, and success responses are all unchanged.

### Before the fix

`GET` against a `PUT`-only route:

```
GET /api/scheduler/schedules/eng_digest_99/pause
```

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "instance": "<server-id>",
  "status": 500,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

The SDK's `executeGetThenPutOnMethodNotAllowed` does not recognize 500 as its
fallback trigger and throws `ConductorClientException`.

### After the fix

Same request:

```
GET /api/scheduler/schedules/eng_digest_99/pause
```

```
HTTP/1.1 405 Method Not Allowed
Content-Type: application/json

{
  "instance": "<server-id>",
  "status": 405,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

The SDK recognizes 405 and retries with the correct method:

```
PUT /api/scheduler/schedules/eng_digest_99/pause
```

```
HTTP/1.1 200 OK
```

The `message` string is Spring's own and is unchanged; only `status` moves to 405.

## Endpoint contract (unchanged, for reference)

Source: `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`.

| Operation | Method + path | Notes |
|---|---|---|
| Pause a schedule | `PUT /api/scheduler/schedules/{name}/pause` | optional `?reason=` query param |
| Resume a schedule | `PUT /api/scheduler/schedules/{name}/resume` | — |
| Create/update | `POST /api/scheduler/schedules` | body: `WorkflowSchedule` |
| List | `GET /api/scheduler/schedules` | optional `?workflowName=` |
| Get one | `GET /api/scheduler/schedules/{name}` | — |
| Delete | `DELETE /api/scheduler/schedules/{name}` | — |

The correct manual call for pause (matches the SDK's PUT branch and the server
`@PutMapping`):

```bash
curl -X PUT \
  "$CONDUCTOR_SERVER_URL/scheduler/schedules/eng_digest_99/pause?reason=maintenance"
```

## Scope of impact

Because the fix lives in the shared `ApplicationExceptionMapper`
(`@RestControllerAdvice`), the 405 mapping applies to **every** Conductor REST
controller, not just the scheduler. Any client that sends an unsupported method to
any endpoint now receives a spec-compliant 405 instead of a misleading 500. This is
a strict improvement in correctness and is backward compatible for well-behaved
clients that already use the documented methods.
