# API Contract — Method-not-supported responses (Issue #1393)

Supporting doc for `architecture.md`. All names and paths are defined there and
reused verbatim here.

## 1. Endpoint under discussion

Defined in
`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`,
mounted under `@RequestMapping("/api/scheduler")`:

| Method | Path | Handler | Success status |
|---|---|---|---|
| `PUT` | `/api/scheduler/schedules/{name}/pause` | `pauseSchedule(name, reason)` | `200 OK` |
| `PUT` | `/api/scheduler/schedules/{name}/resume` | `resumeSchedule(name)` | `200 OK` |

`pauseSchedule` accepts an optional query param `reason`
(`@RequestParam(value = "reason", required = false)`).

There is **no** `GET` mapping for these paths. A `GET` therefore hits an existing
route with an unsupported method → Spring throws
`HttpRequestMethodNotSupportedException`.

## 2. Correct pause call (curl)

```bash
curl -i -X PUT \
  "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause?reason=maintenance"
```

Expected:

```
HTTP/1.1 200 OK
```

(empty body — the handler returns `void` with `@ResponseStatus(HttpStatus.OK)`)

## 3. Method-mismatch response — before vs. after

Probe request the Java SDK issues before the real PUT:

```bash
curl -i -X GET "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"
```

### Before this change (buggy)

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json
```

```json
{
  "instance": "<server-id>",
  "status": 500,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

### After this change (correct)

```
HTTP/1.1 405 Method Not Allowed
Content-Type: application/json
```

```json
{
  "instance": "<server-id>",
  "status": 405,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

> The message text is unchanged — it is `HttpRequestMethodNotSupportedException.getMessage()`.
> Only the HTTP status and the `ErrorResponse.status` field change (500 → 405).
> `instance` is `Utils.getServerId()`; `retryable` is `false` because the
> exception is not a `TransientException`.

## 4. Error body schema

Returned type: `com.netflix.conductor.common.validation.ErrorResponse`.

| Field | Type | Value for this case |
|---|---|---|
| `instance` | string | server id (`Utils.getServerId()`) |
| `status` | int | `405` |
| `message` | string | `Request method 'GET' is not supported` |
| `retryable` | boolean | `false` |

## 5. Client interaction (java-sdk, external repo — reference only)

`io.orkes.conductor.client.http.SchedulerResource.executeGetThenPutOnMethodNotAllowed(...)`
issues a `GET`, and on a `405` proceeds to the intended `PUT`. With the server
now returning `405` instead of `500`, `pauseSchedule(...)` completes and the
`Example99ScheduledAgent` lifecycle (deploy → create → list → **pause** →
resume → preview → delete) succeeds end to end. No java-sdk change is part of
this issue.
