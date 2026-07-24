# Architecture — Fix HTTP 405 mapping for method-not-supported (Issue #1393)

> These design docs live under `docs/design/issue-1393/` to avoid colliding with
> the unrelated pre-existing `docs/design/architecture.md` (Agent Worker). This
> file is the single source of truth for the #1393 change.

## 1. Overview

`SchedulerClient.pauseSchedule(...)` in the Java SDK fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
```

The Java SDK's `io.orkes.conductor.client.http.SchedulerResource.pauseSchedule`
uses a **GET-then-PUT-on-405 fallback** (`executeGetThenPutOnMethodNotAllowed`).
It first probes the endpoint with `GET`; if the server replies `405 Method Not
Allowed`, it falls through to the real `PUT` call. The pause endpoint is
correctly declared server-side as:

```java
@PutMapping("/schedules/{name}/pause")   // SchedulerResource.java:121
```

so a `GET` against it **should** produce a clean `405`. Instead the server
returns **`500 Internal Server Error`** with the message
`Request method 'GET' is not supported`, and the SDK surfaces that as an
exception rather than proceeding to the `PUT`.

### Root cause (server-side)

The bug is **not** in the SDK and **not** a missing route. It is in the
server's global exception handler:

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`

```java
@ExceptionHandler(Throwable.class)
public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
    HttpStatus status =
            EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
    ...
}
```

This `@RestControllerAdvice` handles `Throwable.class`, which **intercepts
Spring MVC's own `org.springframework.web.HttpRequestMethodNotSupportedException`**
before Spring's default handling can map it to `405`. Because that exception
class is absent from `EXCEPTION_STATUS_MAP`, it falls back to the default
`HttpStatus.INTERNAL_SERVER_ERROR` (500). The exception's message
(`Request method 'GET' is not supported`) is copied into the response body,
producing exactly the symptom reported.

This affects **every** endpoint in the server, not just scheduler pause — any
wrong-method request returns 500 instead of the correct 405. The scheduler
pause path is simply the first place a client relied on the standard 405
semantics.

### Fix

Add one entry to `EXCEPTION_STATUS_MAP` so `HttpRequestMethodNotSupportedException`
maps to `HttpStatus.METHOD_NOT_ALLOWED` (405). This restores standard HTTP
semantics, the SDK's GET-then-PUT fallback then works unchanged, and the
scheduler lifecycle completes end to end.

This is the **minimal, focused** change the issue asks for. No SDK changes are
made (the SDK lives in a separate repository), no scheduler route changes are
made, and no new behavior is introduced beyond correcting the status code.

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Build | Gradle |
| Web framework | Spring MVC (Spring Boot) |
| Exception handling | `@RestControllerAdvice` + `@ExceptionHandler` |
| Test framework | JUnit (direct advice invocation, as in the existing test) |
| Module touched | `rest` (REST controllers + advice) |

## 3. Module / file layout

Exactly one production file changes; one test file is extended.

| File | Change | Responsibility |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | **edit** | Add `HttpRequestMethodNotSupportedException` → `METHOD_NOT_ALLOWED` to `EXCEPTION_STATUS_MAP`, plus the import. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | **edit** | Add a test asserting the exception maps to HTTP 405 with the exception message in the body; add a regression test that an unmapped exception is still 500. |

No other files are created or modified. The scheduler controller
(`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`)
is **unchanged** — its `@PutMapping("/schedules/{name}/pause")` is already correct.

## 4. Shared contracts

Every component below reuses these names/types verbatim.

### 4.1 The exception being mapped

- Fully-qualified: `org.springframework.web.HttpRequestMethodNotSupportedException`
- Thrown by Spring MVC's `DispatcherServlet` when a request path resolves to a
  handler but the HTTP method does not match any mapping for that path.
- `getMessage()` returns e.g. `Request method 'GET' is not supported`.

### 4.2 The status map entry

Added to the `static {}` block of `ApplicationExceptionMapper`:

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

Required import (placed with the other `org.springframework.web` imports, i.e.
alongside `org.springframework.web.servlet.resource.NoResourceFoundException`):

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

### 4.3 Error response contract (unchanged shape)

The response body type is the existing
`com.netflix.conductor.common.validation.ErrorResponse`, populated by
`handleAll(...)`:

| Field | Value for method-not-supported |
|---|---|
| `status` | `405` |
| `message` | the exception message, e.g. `Request method 'GET' is not supported` |
| `retryable` | `false` (not a `TransientException`) |
| `instance` | server id (`Utils.getServerId()`) |

The HTTP response status line is `405 Method Not Allowed`.

### 4.4 Logging contract (unchanged behavior, corrected outcome)

`logException(...)` already logs `4xx` at `WARN` and everything else at
`ERROR`. Because the status becomes `405` (a `4xx`), a wrong-method request now
logs at `WARN` instead of polluting error logs with a spurious `ERROR`/500 —
consistent with the intent documented in that method's comment.

### 4.5 Naming conventions

- Keep the existing `EXCEPTION_STATUS_MAP` name and the one-line
  `EXCEPTION_STATUS_MAP.put(Foo.class, HttpStatus.BAR);` style.
- Do not introduce a dedicated `@ExceptionHandler` method for this case; adding
  a map entry is consistent with how `NotFoundException`, `ConflictException`,
  etc. are handled today.

## 5. Non-goals

- No changes to the Java SDK (`io.orkes.conductor.client.http.SchedulerResource`).
- No changes to scheduler routes or `SchedulerService`.
- No change to the `ErrorResponse` schema.
- No blanket remap of other unmapped exceptions — only the one Spring MVC
  method-mismatch exception is added.

## 6. Supporting documents

- [`api.md`](./api.md) — HTTP behavior before/after and the affected endpoints.
- [`testing.md`](./testing.md) — verification plan.
