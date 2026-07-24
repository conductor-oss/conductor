# API behavior — Issue #1393

Reuses names and types from [`architecture.md`](./architecture.md).

## Affected endpoint (unchanged, for reference)

Declared in `SchedulerResource`
(`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`):

```
PUT /api/scheduler/schedules/{name}/pause
      ?reason={reason}        (optional query param)
```

- Path variable: `name` — the schedule name.
- Query param: `reason` — optional, `required = false`.
- Success: `200 OK`, empty body (`@ResponseStatus(HttpStatus.OK)`, `void` return).

The sibling endpoints share the same shape and the same class of fix benefit:

```
PUT /api/scheduler/schedules/{name}/resume
```

## Behavior change — wrong HTTP method

The change is entirely in the error path handled by `ApplicationExceptionMapper.handleAll`.
The `ErrorResponse` body shape is unchanged; only the HTTP status (and log level) change.

### Before (defective)

A `GET` against a `PUT`-only path is intercepted by the catch-all `@ExceptionHandler(Throwable.class)`,
which does not find `HttpRequestMethodNotSupportedException` in `EXCEPTION_STATUS_MAP` and
defaults to 500:

```
GET /api/scheduler/schedules/eng_digest_99/pause
->
HTTP/1.1 500 Internal Server Error
{
  "instance": "<server-id>",
  "status": 500,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

The Java SDK's `executeGetThenPutOnMethodNotAllowed` sees a non-405 error and throws instead
of falling through to `PUT`.

### After (fixed)

```
GET /api/scheduler/schedules/eng_digest_99/pause
->
HTTP/1.1 405 Method Not Allowed
Allow: PUT
{
  "instance": "<server-id>",
  "status": 405,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

The SDK recognizes 405 and proceeds to the real request:

```
PUT /api/scheduler/schedules/eng_digest_99/pause
->
HTTP/1.1 200 OK
```

### Unsupported media type (sibling fix)

A request to a valid path with an unsupported `Content-Type` now resolves correctly:

```
POST /api/scheduler/schedules   (Content-Type: text/plain)
->
HTTP/1.1 415 Unsupported Media Type
```

(previously `500`).

## Status mapping summary

| Condition | Spring exception | Status before | Status after |
|---|---|---|---|
| Wrong HTTP verb on an existing path | `HttpRequestMethodNotSupportedException` | 500 | **405** |
| Unsupported `Content-Type` on an existing path | `HttpMediaTypeNotSupportedException` | 500 | **415** |
| All other mapped/unmapped exceptions | (existing entries) | unchanged | unchanged |

## Client-side end-to-end effect

The `Example99ScheduledAgent` scheduler lifecycle now completes end to end:

```
deploy -> create 2 schedules -> list -> pause -> resume -> preview -> delete
```

No documentation of a new route is needed — no route was added. The `/api-docs` OpenAPI
listing is unchanged.
