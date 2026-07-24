# HTTP contract — wrong-verb responses & scheduler pause/resume (#1393)

Supporting doc for
[`scheduler-pause-405-architecture.md`](./scheduler-pause-405-architecture.md). All names,
types, and statuses here match that document verbatim.

## Affected routes

The scheduler pause/resume endpoints are defined in
`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`,
mapped under `@RequestMapping("/api/scheduler")`.

| Method | Path | Handler | Success status |
|---|---|---|---|
| `PUT` | `/api/scheduler/schedules/{name}/pause` | `pauseSchedule(name, reason?)` | `200 OK` |
| `PUT` | `/api/scheduler/schedules/{name}/resume` | `resumeSchedule(name)` | `200 OK` |

`pause` accepts an optional `reason` query parameter
(`@RequestParam(value = "reason", required = false)`). `resume` takes no body or query params.

Neither route declares a `GET` mapping, so a `GET` is a legitimate method mismatch and Spring
MVC raises `HttpRequestMethodNotSupportedException`.

## Correct pause invocation (`curl`)

Read from `SchedulerResource.pauseSchedule` — `PUT`, name in the path, `reason` as a query
param, no request body:

```bash
# Pause a schedule named "eng_digest_99_daily", with an optional reason
curl -i -X PUT \
  "http://localhost:8080/api/scheduler/schedules/eng_digest_99_daily/pause?reason=maintenance"
```

Expected on success:

```
HTTP/1.1 200 OK
```

Resume:

```bash
curl -i -X PUT \
  "http://localhost:8080/api/scheduler/schedules/eng_digest_99_daily/resume"
```

## Wrong-verb probe — before vs. after the fix

The Java SDK deliberately probes with `GET` before falling back to `PUT`:

```bash
curl -i -X GET \
  "http://localhost:8080/api/scheduler/schedules/eng_digest_99_daily/pause"
```

**Before the fix** (defect): the catch-all `@ExceptionHandler(Throwable.class)` in
`ApplicationExceptionMapper` maps the unmapped `HttpRequestMethodNotSupportedException` to the
default `500`:

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

**After the fix:** the new `EXCEPTION_STATUS_MAP` entry maps it to `405`:

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

Only the HTTP status line and the `status` field change; `message` and `retryable` are
identical because the same exception instance flows through the same `handleAll(...)` body.

<!-- TODO: verify the exact ErrorResponse JSON field ordering against a live server; body
shown here is derived from ApplicationExceptionMapper.handleAll(...) field-setting order. -->

## `ErrorResponse` schema

`com.netflix.conductor.common.validation.ErrorResponse`, populated identically for both the
`500` and `405` cases:

| Field | Type | Meaning |
|---|---|---|
| `instance` | `String` | Server id, `Utils.getServerId()` |
| `status` | `int` | HTTP status code (`405` after fix) |
| `message` | `String` | Exception message, `Request method 'GET' is not supported` |
| `retryable` | `boolean` | `false` for this exception |

## End-to-end sequence restored by the fix

```
Client                                  Server
  |  GET  /schedules/{name}/pause  ----->|
  |<---- 405 Method Not Allowed ---------|   (was 500 before the fix)
  |  PUT  /schedules/{name}/pause  ----->|
  |<---- 200 OK -------------------------|
```

This is exactly the flow `io.orkes.conductor.client.http.SchedulerResource`
`.executeGetThenPutOnMethodNotAllowed(...)` implements; the `405` lets it fall through to the
`PUT`.
