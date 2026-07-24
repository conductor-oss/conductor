# Architecture — Return 405 for unsupported HTTP methods (Issue #1393)

> Source of truth for this change. Supporting docs
> (`method-not-allowed-status-api.md`, `method-not-allowed-status-testing.md`)
> reuse the names, types, and file paths defined here verbatim.

## Problem statement

`SchedulerClient.pauseSchedule(...)` in the Java SDK fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
    at io.orkes.conductor.client.http.SchedulerResource.executeGetThenPutOnMethodNotAllowed(SchedulerResource.java:175)
    at io.orkes.conductor.client.http.SchedulerResource.pauseSchedule(SchedulerResource.java:125)
```

The Java SDK's `SchedulerResource` calls the pause endpoint through a
**GET-then-PUT-on-405 fallback** (`executeGetThenPutOnMethodNotAllowed`): it first
issues a `GET`, and if the server answers `405 Method Not Allowed` it retries the
same URL with `PUT`. The fallback exists so a single client build can talk to both
old servers (which exposed pause as `GET`) and current servers (which expose it as
`PUT`).

The current server exposes pause as `PUT` only:

```java
// scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java
@PutMapping("/schedules/{name}/pause")
@ResponseStatus(HttpStatus.OK)
public void pauseSchedule(
        @PathVariable("name") String name,
        @RequestParam(value = "reason", required = false) String reason) { ... }
```

So the client's probing `GET /api/scheduler/schedules/{name}/pause` reaches a route
mapped only for `PUT`. Spring MVC raises
`org.springframework.web.HttpRequestMethodNotSupportedException`, which by itself
maps to **405**. The SDK is written to treat that 405 as its signal to fall through
to `PUT`.

## Root cause

The server never returns 405. The catch-all advice in the `rest` module intercepts
the method-not-supported exception and re-maps it to **500**:

```java
// rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java
@ExceptionHandler(Throwable.class)
public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
    HttpStatus status =
            EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
    ...
}
```

`HttpRequestMethodNotSupportedException` is not a key in `EXCEPTION_STATUS_MAP`, so
`getOrDefault` returns `INTERNAL_SERVER_ERROR`. The SDK sees `500 "Request method
'GET' is not supported"`, which is neither the success it expects nor the `405` its
fallback branch keys on, so it throws instead of proceeding to `PUT`.

This is a **general** defect, not scheduler-specific: any unsupported-method request
to any Conductor controller currently returns 500 instead of the semantically
correct 405. The scheduler pause path is simply the first caller that depends on the
correct status code.

## Fix (minimal, focused)

Map `HttpRequestMethodNotSupportedException` to `405 METHOD_NOT_ALLOWED` in
`ApplicationExceptionMapper.EXCEPTION_STATUS_MAP`. No SDK change and no scheduler
code change; the server simply returns the status code the HTTP spec and the SDK
already expect. Once the probing `GET` returns a clean 405, the SDK's existing
`executeGetThenPutOnMethodNotAllowed` fallback issues the `PUT` and the pause
succeeds.

The change is intentionally scoped to the status-code mapping. We do **not** add a
`GET` route for pause/resume, and we do **not** modify the SDK, because the correct
server behavior (405 for a wrong method) is sufficient and benefits every endpoint.

## Tech stack

- Java 21, Spring Boot / Spring MVC.
- Module `rest` — REST controllers and the global exception advice (the one file
  changed here).
- Module `scheduler/core` — the scheduler REST surface (`SchedulerResource`),
  unchanged by this fix; referenced only for endpoint context.
- JUnit 4 + Spring `MockMvc` standalone setup for the exception-mapper unit test.

## Complete file layout

| File | Change | Responsibility |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | **edit** | Add import of `HttpRequestMethodNotSupportedException`; add its entry to `EXCEPTION_STATUS_MAP` mapping to `METHOD_NOT_ALLOWED`. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | **edit** | Add a test asserting a wrong-method request returns 405 (not 500) and is logged at WARN, not ERROR. |

No other production files change. `scheduler/core/.../SchedulerResource.java` is
listed only as context for the endpoint contract; it is **not** modified.

## Shared contracts

The following names/types are reused verbatim in the supporting docs.

### The exact code change

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;

// inside the static { } initializer of ApplicationExceptionMapper, alongside the
// existing EXCEPTION_STATUS_MAP.put(...) lines:
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

### Exception → HTTP status contract (after fix)

| Exception | HTTP status |
|---|---|
| `NotFoundException` | `404 NOT_FOUND` |
| `ConflictException` | `409 CONFLICT` |
| `IllegalArgumentException` | `400 BAD_REQUEST` |
| `InvalidFormatException` | `500 INTERNAL_SERVER_ERROR` |
| `NoResourceFoundException` | `404 NOT_FOUND` |
| `FileStorageException` | `413 PAYLOAD_TOO_LARGE` |
| `AccessForbiddenException` | `403 FORBIDDEN` |
| **`HttpRequestMethodNotSupportedException`** | **`405 METHOD_NOT_ALLOWED`** *(new)* |
| *(any other `Throwable`)* | `500 INTERNAL_SERVER_ERROR` |

### Logging contract (unchanged, but now applies to 405)

`ApplicationExceptionMapper.logException` logs `4xx` at `WARN` and `5xx` at `ERROR`
(see the existing rationale comment in that method). Because 405 is a `4xx` client
error, mapping it correctly also moves it out of the ERROR log — consistent with how
404/409 are already handled.

### `ErrorResponse` body (unchanged)

The advice returns `com.netflix.conductor.common.validation.ErrorResponse` with:
- `instance` = server id
- `status` = numeric HTTP status (now `405` for wrong-method)
- `message` = exception message, e.g. `Request method 'GET' is not supported`
- `retryable` = `true` only for `TransientException` (so `false` here)

### Scheduler pause endpoint (context, unchanged)

- Route: `PUT /api/scheduler/schedules/{name}/pause`
- Optional query param: `reason`
- Handler: `SchedulerResource.pauseSchedule(String name, String reason)`

## Rejected alternatives

- **Add a `GET` alias for pause/resume on the server.** Rejected: pause is a
  state-mutating operation and must not be a `GET`; it would also mask the real
  status-code defect for every other endpoint.
- **Change the SDK to treat 500 as 405.** Rejected: this repo is the server; the
  server returning 500 for a wrong method is the actual bug, and fixing it repairs
  the entire API surface rather than one client path.
