# Architecture — Fix 405 handling so scheduler pause/resume probes work (#1393)

## Problem statement

`SchedulerClient.pauseSchedule(...)` in the Java SDK throws:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
    at io.orkes.conductor.client.http.SchedulerResource.executeGetThenPutOnMethodNotAllowed(...)
    at io.orkes.conductor.client.http.SchedulerResource.pauseSchedule(...)
```

The Java client resolves the pause endpoint with a **GET-then-PUT-on-405 fallback**:
it first issues `GET /api/scheduler/schedules/{name}/pause`, and *if the server answers
`405 Method Not Allowed`*, it falls through to `PUT` (the real verb). The fallback exists
so the same client works against server builds that historically exposed pause under
different verbs.

The server-side pause endpoint is a **`PUT`-only** mapping
(`SchedulerResource.pauseSchedule`, see below). A `GET` against it *should* produce a clean
`405`, which the client is designed to handle. Instead this server returns **`500`** with
message `Request method 'GET' is not supported`, so the client raises an exception instead
of falling through to the `PUT`.

### Root cause

The offending translation happens in the server, not the SDK.

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`
declares a catch-all advice:

```java
@RestControllerAdvice
@Order(ValidationExceptionMapper.ORDER + 1)
public class ApplicationExceptionMapper {
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
        HttpStatus status =
                EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
        ...
    }
}
```

Because the handler is bound to `Throwable`, it intercepts Spring's
`org.springframework.web.HttpRequestMethodNotSupportedException` — the exception Spring MVC
raises when a request hits a route whose HTTP method does not match. Spring's own default
would render that exception as `405 Method Not Allowed`, but this catch-all takes over first.
Since `HttpRequestMethodNotSupportedException` is **not** in `EXCEPTION_STATUS_MAP`, it falls
to the default branch `HttpStatus.INTERNAL_SERVER_ERROR` (`500`).

The message text (`Request method 'GET' is not supported`) is
`HttpRequestMethodNotSupportedException.getMessage()`, confirming the source.

This is a server-wide defect: **every** wrong-verb request to **any** Conductor endpoint
currently returns `500` instead of `405`. The scheduler pause path is simply the first place
it surfaces, because a well-behaved client deliberately probes with the "wrong" verb.

## Scope of the fix

Minimal and focused, per the issue. The change is entirely server-side and lives in the REST
exception mapper. We map `HttpRequestMethodNotSupportedException` to `405 METHOD_NOT_ALLOWED`
so the server emits the status Spring MVC and HTTP semantics intend, and the Java SDK's
existing GET-then-PUT-on-405 fallback proceeds to the `PUT` as designed.

We do **not**:

- change `SchedulerResource` route verbs (the `PUT` mapping is correct);
- add a `GET` alias for pause/resume (would mask the real bug and pollute the API surface);
- modify the Java SDK (separate `conductor-oss/java-sdk` repository — out of scope here);
- alter any other entry in `EXCEPTION_STATUS_MAP`.

## Tech stack

- Java 21, Spring Boot / Spring MVC (`@RestControllerAdvice`, `@ExceptionHandler`).
- Gradle multi-module build.
- Module under change: `rest`.
- Test framework already used by the module: JUnit + Spring MVC `ResponseEntity` assertions
  (see `ApplicationExceptionMapperTest`).

## Module / file layout

Every file below is in the existing tree; this change touches one production file and one
test file. No new files are created for the production fix.

| File | Module | Responsibility | Action |
|---|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | `rest` | Global `@RestControllerAdvice` translating exceptions to HTTP status + `ErrorResponse`. **Add the `HttpRequestMethodNotSupportedException → 405` mapping here.** | Edit |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | `rest` | Unit tests for the mapper. **Add a case asserting `HttpRequestMethodNotSupportedException` maps to `405`.** | Edit |
| `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java` | `scheduler/core` | Defines `PUT /api/scheduler/schedules/{name}/pause` (and `/resume`). Reference only — **unchanged**. | Read-only |
| `test-harness/src/test/java/com/netflix/conductor/test/integration/http/SchedulerIntegrationTest.java` | `test-harness` | Existing scheduler HTTP integration coverage. Optional end-to-end assertion that a `GET` to a `PUT`-only scheduler route yields `405`. | Optional edit |

## Shared contract (reused verbatim by every component)

### The exception → status mapping

The single authoritative change is one entry added to the static `EXCEPTION_STATUS_MAP` in
`ApplicationExceptionMapper`:

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

- **Exception type:** `org.springframework.web.HttpRequestMethodNotSupportedException`
- **Mapped status:** `org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED` (`405`)
- **Import to add:** `import org.springframework.web.HttpRequestMethodNotSupportedException;`

All other behavior of `handleAll(...)` is unchanged: it still builds the same `ErrorResponse`,
sets `instance`/`status`/`message`/`retryable`, records `Monitors.error(...)`, and logs.
Because `405` is a `4xx` status, `logException(...)` already routes it to `WARN` (not `ERROR`),
which is the correct severity for a client-side method mismatch.

### `ErrorResponse` payload (unchanged, documented for consistency)

`com.netflix.conductor.common.validation.ErrorResponse`, as populated by `handleAll`:

| Field | Type | Value for this case |
|---|---|---|
| `instance` | `String` | `Utils.getServerId()` (host) |
| `status` | `int` | `405` |
| `message` | `String` | `HttpRequestMethodNotSupportedException.getMessage()`, e.g. `Request method 'GET' is not supported` |
| `retryable` | `boolean` | `false` (not a `TransientException`) |

### Server route under test (unchanged)

`io.orkes.conductor.scheduler.rest.SchedulerResource`, mapped at `/api/scheduler`:

```java
@PutMapping("/schedules/{name}/pause")
@ResponseStatus(HttpStatus.OK)
public void pauseSchedule(
        @PathVariable("name") String name,
        @RequestParam(value = "reason", required = false) String reason) {
    schedulerService.pauseSchedule(name, reason);
}

@PutMapping("/schedules/{name}/resume")
@ResponseStatus(HttpStatus.OK)
public void resumeSchedule(@PathVariable("name") String name) {
    schedulerService.resumeSchedule(name);
}
```

Both are `PUT`-only; issuing `GET` against either must yield `405`.

### Client contract this restores (informational)

The Java SDK method `io.orkes.conductor.client.http.SchedulerResource.pauseSchedule(...)`
calls `executeGetThenPutOnMethodNotAllowed(...)`, whose contract is:

1. Issue `GET` to the resolved path.
2. On HTTP `405`, retry the same path with `PUT`.
3. On any other non-2xx, surface a `ConductorClientException`.

Returning `405` (this fix) satisfies step 2. No SDK change is required.

## Behavioral contract after the fix

| Request | Before | After |
|---|---|---|
| `GET /api/scheduler/schedules/{name}/pause` | `500` "Request method 'GET' is not supported" | `405` Method Not Allowed |
| `PUT /api/scheduler/schedules/{name}/pause` | `200` | `200` (unchanged) |
| `SchedulerClient.pauseSchedule(name, reason)` | throws | succeeds (GET→405→PUT→200) |
| Any wrong-verb request to any Conductor route | `500` | `405` |

See [`scheduler-pause-405-api.md`](./scheduler-pause-405-api.md) for the full HTTP contract
and [`scheduler-pause-405-testing.md`](./scheduler-pause-405-testing.md) for the verification
plan.
