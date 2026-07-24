# HTTP contract — unsupported method on scheduler endpoints (issue #1393)

This document describes the externally observable HTTP behavior before and after the fix defined in
`architecture.md`. Names and types (`ApplicationExceptionMapper`, `EXCEPTION_STATUS_MAP`,
`HttpRequestMethodNotSupportedException`, `HttpStatus.METHOD_NOT_ALLOWED`, `ErrorResponse`) are used
exactly as declared there.

## Endpoint under test

Source of truth: `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`.

| Method | Path | Handler | Line |
|---|---|---|---|
| `PUT` | `/api/scheduler/schedules/{name}/pause` | `pauseSchedule(name, reason)` | `SchedulerResource.java:121` |
| `PUT` | `/api/scheduler/schedules/{name}/resume` | `resumeSchedule(name)` | `SchedulerResource.java:130` |

The pause endpoint accepts an optional query parameter `reason` (`@RequestParam(required = false)`).
There is **no** `GET` mapping for either path. A `GET` to `.../pause` is therefore an unsupported
method and triggers `HttpRequestMethodNotSupportedException` in the Spring dispatcher.

## Behavior — before the fix

Request (the SDK's probing GET):

```
GET /api/scheduler/schedules/eng_digest_99/pause HTTP/1.1
Host: localhost:8080
```

Response:

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

The SDK's `executeGetThenPutOnMethodNotAllowed(...)` only falls through to `PUT` on `405`; it raises
`ConductorClientException` on `500`. Result: `pauseSchedule(...)` throws.

## Behavior — after the fix

Same probing request:

```
GET /api/scheduler/schedules/eng_digest_99/pause HTTP/1.1
Host: localhost:8080
```

Response:

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

The SDK sees `405`, retries with `PUT`:

```
PUT /api/scheduler/schedules/eng_digest_99/pause?reason=<reason> HTTP/1.1
Host: localhost:8080
```

```
HTTP/1.1 200 OK
```

## Correct direct call (no fallback needed)

For clients that call the endpoint directly (e.g. the Python SDK, or a curl equivalent), the pause
endpoint is invoked with `PUT`:

```bash
curl -X PUT "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"

# with an optional reason:
curl -X PUT "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause?reason=maintenance"
```

Expected: `200 OK` with an empty body (`pauseSchedule` returns `void`,
`@ResponseStatus(HttpStatus.OK)`).

## Generality

The `405` mapping applies to every route served through `ApplicationExceptionMapper`, not just the
scheduler. Any request whose HTTP method has no handler mapping now returns `405 Method Not Allowed`
instead of `500 Internal Server Error`. The response envelope (`ErrorResponse`) and the message text
are unchanged; only the status code changes from `500` to `405`.
