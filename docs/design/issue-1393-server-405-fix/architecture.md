# Architecture — Return 405 (not 500) for wrong HTTP method (Issue #1393)

Single source of truth for this change. The supporting docs
(`exception-mapping.md`, `testing.md`) in this same directory reuse the names,
types, and file paths defined here verbatim.

## 1. Overview

`SchedulerClient.pauseSchedule(...)` in the Java SDK
(`io.orkes.conductor.client.http.SchedulerResource`) fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
```

The Java client probes the pause endpoint with a **GET first** and falls through
to a **PUT** only when the server answers with a *clean* `405 Method Not
Allowed` (`executeGetThenPutOnMethodNotAllowed`). The Conductor server instead
answers that probe with **`500 Internal Server Error`** carrying the message
`Request method 'GET' is not supported`, so the client's `405` detection never
triggers and the exception is surfaced to the caller.

The pause route is present and correct:

- Server: `PUT /api/scheduler/schedules/{name}/pause`
  (`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`,
  method `pauseSchedule(...)`, annotated `@PutMapping("/schedules/{name}/pause")`).
- There is **no** `@GetMapping` for that path, so a GET must yield `405`, not `500`.

### Root cause

`ApplicationExceptionMapper` (a `@RestControllerAdvice` in the `rest` module) has
one catch-all handler:

```java
@ExceptionHandler(Throwable.class)
public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
    HttpStatus status =
            EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
    ...
}
```

Spring MVC raises
`org.springframework.web.HttpRequestMethodNotSupportedException` (message
`Request method 'GET' is not supported`) when a request hits a route with the
wrong HTTP method. That class is **not** in `EXCEPTION_STATUS_MAP`, so
`getOrDefault(...)` falls back to `HttpStatus.INTERNAL_SERVER_ERROR` (500). That
is the entire defect: a protocol condition HTTP defines as `405` is reported as
`500`.

The catch-all advice runs for the whole server, so the mis-mapping affects
**every** wrong-method request across all controllers; the scheduler pause path
is simply where a real client depends on the correct code.

### The fix (minimal, focused)

Add one entry to `EXCEPTION_STATUS_MAP` mapping
`HttpRequestMethodNotSupportedException` → `HttpStatus.METHOD_NOT_ALLOWED`
(`405`). Then:

1. A GET to `/api/scheduler/schedules/{name}/pause` returns a clean `405`.
2. The Java client's `executeGetThenPutOnMethodNotAllowed` detects the `405` and
   proceeds to the intended `PUT`, completing the pause.

No client code and no scheduler code changes in this repository. The correction
is made once, at the server's exception-to-status boundary, aligning the whole
API with HTTP semantics.

### Non-goals

- Do **not** add a `@GetMapping` alias to the pause endpoint. The route is
  correctly PUT-only; the bug is the wrong status code, not a missing route.
- Do **not** change the Java SDK (`java-sdk` is a separate repository).
- Do **not** change the `ErrorResponse` body shape, retryability semantics, or
  logging levels.

## 2. Tech stack

- Java 21, Gradle.
- Spring Boot / Spring Web MVC (`@RestControllerAdvice`, `@ExceptionHandler`).
- Test: JUnit 4 + `MockMvc` (`MockMvcBuilders.standaloneSetup`), Mockito static
  mock of the logger — matching the existing `ApplicationExceptionMapperTest`.

## 3. Complete file layout

Only files in the **`rest`** module change. Other files are context, untouched.

| File | Change | Responsibility |
|------|--------|----------------|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | **EDIT** | Add `HttpRequestMethodNotSupportedException` → `METHOD_NOT_ALLOWED` to `EXCEPTION_STATUS_MAP`, plus the import. Sole production change. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | **EDIT** | Add `testMethodNotSupportedMapsTo405` asserting a wrong-method request returns `405` and is logged at WARN, not ERROR. See `testing.md`. |
| `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java` | none (context) | Defines `PUT /api/scheduler/schedules/{name}/pause`; confirms the route is correctly PUT-only. |

No new production files are created; no files are moved or deleted.

## 4. Shared contracts

Used verbatim by the supporting docs.

### 4.1 Exception class

```java
org.springframework.web.HttpRequestMethodNotSupportedException
```

- Thrown by Spring MVC dispatch when a resolved path is invoked with an
  unsupported HTTP method.
- `getMessage()` returns strings like `Request method 'GET' is not supported`
  (the exact text the client and the issue observe).
- HTTP defines this condition as `405 Method Not Allowed`.

### 4.2 Mapping table entry (production contract)

In `ApplicationExceptionMapper`, the static initializer for
`EXCEPTION_STATUS_MAP` gains exactly one entry:

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

and the corresponding import:

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

Nothing else in the class changes. `handleAll(...)` continues to build an
`ErrorResponse` with `instance`, `status`, `message`, `retryable`; set
`retryable = (th instanceof TransientException)` (so `405` is **non-retryable**,
matching the client's observed `retryable: false`); and log via
`logException(...)`, which — because `405.is4xxClientError()` is `true` — now
logs at **WARN**, not ERROR.

### 4.3 Behavioral contract after the change

| Request | Before | After |
|---------|--------|-------|
| `GET /api/scheduler/schedules/{name}/pause` | `500`, message `Request method 'GET' is not supported`, logged ERROR | `405 Method Not Allowed`, same message, logged WARN |
| `PUT /api/scheduler/schedules/{name}/pause` | `200 OK` | `200 OK` (unchanged) |
| Java client `pauseSchedule(...)` | throws `ConductorClientException {status=500}` | GET probe returns `405`, client falls through to PUT, pause succeeds |

### 4.4 Naming conventions

- Map field name: `EXCEPTION_STATUS_MAP` (existing).
- Status constant: `HttpStatus.METHOD_NOT_ALLOWED` (Spring, value `405`).
- New test method: `testMethodNotSupportedMapsTo405` (see `testing.md`).
