# Architecture — Fix 500 on unsupported HTTP method (issue #1393)

## Problem statement

`SchedulerClient.pauseSchedule(...)` in the Java SDK fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
```

The SDK's `SchedulerResource.executeGetThenPutOnMethodNotAllowed(...)` implements a
**GET-then-PUT-on-405 fallback**: it issues a `GET` first and, if the server answers
`405 Method Not Allowed`, retries the call as a `PUT`. The pause endpoint on the server is
`PUT /api/scheduler/schedules/{name}/pause` (see
`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java:121`),
so the client expects to receive a `405` for the probing `GET` and then fall through to the `PUT`.

Instead, the **server returns `500`** for the probing `GET`. The SDK only treats a clean `405`
as the signal to retry with `PUT`; a `500` is surfaced as a hard exception. The lifecycle
therefore breaks at the pause step even though the route exists and works via `PUT`.

The Python SDK does not use the GET-then-PUT fallback and calls `PUT` directly, which is why its
scheduling examples pass against the same server. **The defect is on the server side**, not the SDK.

## Root cause

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` is a
`@RestControllerAdvice` whose single handler is annotated `@ExceptionHandler(Throwable.class)`.
Because it catches `Throwable`, it also intercepts Spring MVC's
`org.springframework.web.HttpRequestMethodNotSupportedException` — the exception Spring raises when
a request reaches a handler mapping with a method that has no mapping (here: `GET` on a `PUT`-only
path). Spring's own machinery would normally translate that exception into a
`405 Method Not Allowed` response with an `Allow` header.

`ApplicationExceptionMapper` short-circuits that behavior. Its status lookup is:

```java
HttpStatus status =
        EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
```

`HttpRequestMethodNotSupportedException` is **not** in `EXCEPTION_STATUS_MAP`, so it falls to the
default `HttpStatus.INTERNAL_SERVER_ERROR` (`500`). The exception message
(`"Request method 'GET' is not supported"`) is copied verbatim into the `ErrorResponse`, which is
exactly the string observed by the SDK.

## Fix (minimal, focused)

Map `HttpRequestMethodNotSupportedException` to `HttpStatus.METHOD_NOT_ALLOWED` (`405`) in
`ApplicationExceptionMapper.EXCEPTION_STATUS_MAP`. This restores the semantically correct status
code for the exact condition the SDK's fallback probes for, so the GET-then-PUT fallback proceeds
to the `PUT` and pause/resume succeed.

This is the smallest change that resolves the issue at its source:

- It is a **general correctness fix** — any unsupported-method request now returns `405`, the HTTP
  standard status, instead of a misleading `500`.
- It does **not** require changing the scheduler routes, the service layer, or the SDK.
- `405` is a `4xx` client error, so — via the existing `logException(...)` branch in the same class
  — these are now logged at `WARN` instead of `ERROR`, which correctly stops treating a routine
  method-probe as a server fault.

### Scope boundary — what this change does NOT do

- No change to `SchedulerResource` routes or HTTP method mappings; `PUT .../pause` stays `PUT`.
- No change to `SchedulerService` or any persistence module.
- No change to the Java SDK (that lives in a separate `java-sdk` repository); the SDK's fallback is
  correct once the server returns `405`.
- No new dependency; `HttpRequestMethodNotSupportedException` is already on the classpath via
  `spring-web`.

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Web framework | Spring MVC (Spring Boot) |
| Exception mapping | `@RestControllerAdvice` + `@ExceptionHandler` |
| Error payload | `com.netflix.conductor.common.validation.ErrorResponse` |
| Build | Gradle (`:rest` module) |
| Test | JUnit 4 + Spring `MockMvc` standalone setup + Mockito |

## Module / file layout

Only one production file changes; one test file gains a case. No new files are created.

| File | Module | Responsibility | Change |
|---|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | `rest` | Central `@RestControllerAdvice` that maps thrown exceptions to HTTP status + `ErrorResponse` | Add `HttpRequestMethodNotSupportedException -> METHOD_NOT_ALLOWED` to `EXCEPTION_STATUS_MAP` and import the exception class |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | `rest` | Unit tests for the mapper via `MockMvc` | Add a test asserting an unsupported method yields `405` and is logged at `WARN` |
| `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java` | `scheduler/core` | Scheduler REST endpoints (reference only — unchanged) | none |

## Shared contracts (reused verbatim by every component)

These names/types are the single source of truth. Supporting docs (`api.md`, `testing.md`) reuse
them exactly.

### Exception → status mapping entry

The new entry added to the `static` initializer block of `EXCEPTION_STATUS_MAP` in
`ApplicationExceptionMapper`:

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

Required import (added alongside the existing `org.springframework...` imports):

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

### Exception type

- Fully-qualified: `org.springframework.web.HttpRequestMethodNotSupportedException`
- Raised by: Spring MVC dispatcher when a request method has no matching handler mapping.
- Message shape (server-produced, propagated into `ErrorResponse.message`):
  `Request method 'GET' is not supported`

### Target status

- `org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED` — numeric value `405`.

### Error response contract (unchanged structure)

`com.netflix.conductor.common.validation.ErrorResponse` fields set by `handleAll(...)`:

| Field | Source | Value for this case |
|---|---|---|
| `instance` | `Utils.getServerId()` | server id |
| `status` | `status.value()` | `405` |
| `message` | `th.getMessage()` | `Request method 'GET' is not supported` |
| `retryable` | `th instanceof TransientException` | `false` |

### Logging contract (unchanged behavior, now also applies to 405)

`logException(...)` branches on `status.is4xxClientError()`:

- `405` is `4xx` ⇒ logged via `LOGGER.warn("Error {} url: '{}'", simpleName, uri, exception)`.
- `simpleName` for this case is `HttpRequestMethodNotSupportedException`.

### Naming conventions

- Map constant: `EXCEPTION_STATUS_MAP` (existing).
- Handler method: `handleAll(HttpServletRequest request, Throwable th)` (existing, unchanged).
- Test method for the new case: `testUnsupportedMethodReturns405` (see `testing.md`).

## Sequence after the fix

1. SDK `pauseSchedule` fallback issues `GET /api/scheduler/schedules/{name}/pause`.
2. Spring finds only a `PUT` mapping for that path ⇒ raises `HttpRequestMethodNotSupportedException`.
3. `ApplicationExceptionMapper.handleAll` looks up the class in `EXCEPTION_STATUS_MAP` ⇒ `405`.
4. Server returns `405` (logged at `WARN`).
5. SDK's `executeGetThenPutOnMethodNotAllowed` sees `405` and retries as `PUT`.
6. `PUT .../pause` reaches `SchedulerResource.pauseSchedule` ⇒ schedule paused (`200 OK`).

See `api.md` for the HTTP contract and `testing.md` for the verification plan.
