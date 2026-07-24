# HTTP contract: wrong-method responses (Issue #1393)

Supporting doc for
[scheduler-method-not-allowed-architecture.md](scheduler-method-not-allowed-architecture.md).
Names and types below match that document verbatim.

## 1. Affected endpoints (unchanged mappings)

The scheduler pause/resume endpoints are `PUT`-only and are not modified:

| Method | Path | Handler |
|---|---|---|
| `PUT` | `/api/scheduler/schedules/{name}/pause` | `SchedulerResource.pauseSchedule(name, reason)` |
| `PUT` | `/api/scheduler/schedules/{name}/resume` | `SchedulerResource.resumeSchedule(name)` |

The `reason` query parameter on pause is optional
(`@RequestParam(value = "reason", required = false)`).

## 2. Wrong-method behaviour

The change is generic to Spring MVC: it applies to **any** endpoint reached with
an unsupported HTTP method, not only the scheduler.

### Before (defective)

```
GET /api/scheduler/schedules/eng_digest_99/pause
--> 500 Internal Server Error
    { "status": 500, "message": "Request method 'GET' is not supported",
      "retryable": false, "instance": "<server-id>" }
    (no Allow header)
```

### After (fixed)

```
GET /api/scheduler/schedules/eng_digest_99/pause
--> 405 Method Not Allowed
    Allow: PUT
    { "status": 405, "message": "Request method 'GET' is not supported",
      "retryable": false, "instance": "<server-id>" }
```

The correct call is unchanged and continues to succeed:

```
PUT /api/scheduler/schedules/eng_digest_99/pause?reason=maintenance
--> 200 OK   (empty body)
```

## 3. Response body schema

The body is `com.netflix.conductor.common.validation.ErrorResponse` — the same
type every other mapped exception uses.

| Field | Type | Value on 405 |
|---|---|---|
| `instance` | string | `Utils.getServerId()` |
| `status` | int | `405` |
| `message` | string | `ex.getMessage()`, e.g. `Request method 'GET' is not supported` |
| `retryable` | boolean | `false` |

The `Allow` response header is set from
`HttpRequestMethodNotSupportedException.getSupportedHttpMethods()` when that set
is non-null and non-empty. For the pause/resume endpoints this is `Allow: PUT`.

## 4. Effect on the Java SDK client

No client change is required. `executeGetThenPutOnMethodNotAllowed` in the SDK's
`io.orkes.conductor.client.http.SchedulerResource` already recognises `405` as
the signal to re-issue the request as `PUT`. Once the server returns `405`
instead of `500`, the GET-probe → PUT fallback completes and
`SchedulerClient.pauseSchedule(...)` succeeds.
</content>
