# API Behavior — Method-not-supported status (Issue #1393)

Reuses names/types from [`architecture.md`](./architecture.md) verbatim.

## Affected endpoint (root symptom)

Declared in
`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`:

```java
@PutMapping("/schedules/{name}/pause")   // line 121
@ResponseStatus(HttpStatus.OK)
public void pauseSchedule(
        @PathVariable("name") String name,
        @RequestParam(value = "reason", required = false) String reason)
```

Full path: `PUT /api/scheduler/schedules/{name}/pause` (the class is
`@RequestMapping("/api/scheduler")`).

The correct call succeeds today. The failure only occurs on the SDK's
diagnostic `GET` probe against this path.

## Behavior change

The change is in `ApplicationExceptionMapper` and applies to **all** endpoints,
not just scheduler pause. Any request whose path matches a handler but whose
HTTP method does not causes Spring MVC to throw
`HttpRequestMethodNotSupportedException`.

### Before

```
GET /api/scheduler/schedules/eng_digest_99/pause
```

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "status": 500,
  "message": "Request method 'GET' is not supported",
  "retryable": false,
  "instance": "<server-id>"
}
```

### After

```
GET /api/scheduler/schedules/eng_digest_99/pause
```

```
HTTP/1.1 405 Method Not Allowed
Content-Type: application/json

{
  "status": 405,
  "message": "Request method 'GET' is not supported",
  "retryable": false,
  "instance": "<server-id>"
}
```

`<server-id>` is `Utils.getServerId()` and varies per host.

## Effect on the Java SDK flow

The SDK method `io.orkes.conductor.client.http.SchedulerResource.pauseSchedule`
calls `executeGetThenPutOnMethodNotAllowed(...)`, which:

1. Issues `GET .../pause`.
2. On `405 Method Not Allowed`, falls through and issues the real
   `PUT .../pause`.

Previously step 1 returned `500`, which the SDK treats as a hard error and
throws. After this change step 1 returns `405`, so the SDK proceeds to the
`PUT` and the pause succeeds. No SDK code change is required.

The full `Example99ScheduledAgent` lifecycle then completes:
deploy → create 2 schedules → list → **pause** → resume → preview → delete.

## Endpoints that share the fix

Every controller in the server benefits identically. Notable wrong-method
cases now returning `405` instead of `500` include the scheduler mutating
routes:

| Path | Correct method |
|---|---|
| `/api/scheduler/schedules/{name}/pause` | `PUT` |
| `/api/scheduler/schedules/{name}/resume` | `PUT` |
| `/api/scheduler/schedules/{name}` | `GET` / `DELETE` |

## Compatibility

- Response body schema (`ErrorResponse`) is unchanged; only `status` differs
  for this class of error.
- Clients that (incorrectly) relied on `500` for a wrong method would already
  have been failing; `405` is the correct, standard status per RFC 7231.
