# Architecture — Fix #1393: `HttpRequestMethodNotSupportedException` returned as `500` instead of `405`

## 1. Overview

### Problem

`SchedulerClient.pauseSchedule(...)` in the Java SDK fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
```

The Java SDK's `io.orkes.conductor.client.http.SchedulerResource` calls the pause
endpoint through a compatibility helper, `executeGetThenPutOnMethodNotAllowed`, that:

1. issues a `GET` to `/api/scheduler/schedules/{name}/pause`, and
2. **on a `405 Method Not Allowed`**, falls through and re-issues the request as a `PUT`.

The endpoint is declared `PUT`-only on the server
(`SchedulerResource.pauseSchedule`, `@PutMapping("/schedules/{name}/pause")`).
When the client probes it with `GET`, Spring MVC throws
`org.springframework.web.HttpRequestMethodNotSupportedException` with the message
`Request method 'GET' is not supported`. The correct HTTP status for that exception
is `405 Method Not Allowed`.

The server instead returns `500 Internal Server Error`. The client's fallback only
recognizes `405`, so it surfaces the `500` as a hard exception rather than proceeding
to the `PUT`.

### Root cause

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`
is a `@RestControllerAdvice` with a single catch-all
`@ExceptionHandler(Throwable.class)`. It maps exception classes to HTTP statuses via a
static `EXCEPTION_STATUS_MAP` and falls back to `HttpStatus.INTERNAL_SERVER_ERROR` for
any class not present in that map:

```java
HttpStatus status =
        EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
```

`HttpRequestMethodNotSupportedException` is **not** in `EXCEPTION_STATUS_MAP`, so every
unsupported-method request (including the client's `GET` probe of a `PUT`-only route)
is reported as `500`. This is a server-side defect: an HTTP method mismatch is a client
error (`4xx`), not a server failure (`5xx`), and Spring already models it precisely.

### Fix (minimal, focused)

Add one entry to `EXCEPTION_STATUS_MAP` so the catch-all handler maps
`HttpRequestMethodNotSupportedException` to `405 METHOD_NOT_ALLOWED`. No new endpoint,
no controller change, and no client change is required in this repository. Once the
server returns a clean `405`, the Java SDK's existing GET-then-PUT-on-405 fallback
proceeds to the `PUT` and the scheduler lifecycle (deploy → create → list → pause →
resume → preview → delete) completes.

This is the smallest change that resolves the issue at its source. Because the mapper is
a shared `@RestControllerAdvice`, the correction applies uniformly to every controller —
including `SchedulerResource` — and to any future method mismatch across the whole REST
surface.

### Why fix the server, not the client

- The client (`java-sdk`) lives in a **separate repository**; this repo cannot change it.
- The server is objectively wrong: a method mismatch is `405`, not `500`. Returning the
  spec-correct status (RFC 7231 §6.5.5 for `405` vs §5.5.6 for `5xx`) is a bug fix that
  benefits every SDK and every raw HTTP caller, and it is exactly what the client's
  fallback expects.
- The Python SDK works because it issues `PUT` directly and never triggers the probe; the
  server's mis-mapping is latent for any client that relies on `405` semantics.

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Build | Gradle |
| Web framework | Spring Boot / Spring MVC (`@RestControllerAdvice`, `@ExceptionHandler`) |
| Error model | `com.netflix.conductor.common.validation.ErrorResponse` |
| Method-mismatch exception | `org.springframework.web.HttpRequestMethodNotSupportedException` |
| Metrics | `com.netflix.conductor.metrics.Monitors` |
| Test framework | JUnit (existing `ApplicationExceptionMapperTest`) |

## 3. Module / file layout

Everything needed lives in the existing `rest` module. No new source files are required;
the fix is a one-line addition to a static map plus test coverage.

| File | Change | Responsibility |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | **Edit** | Add `HttpRequestMethodNotSupportedException.class → HttpStatus.METHOD_NOT_ALLOWED` to `EXCEPTION_STATUS_MAP`; add the `org.springframework.web.HttpRequestMethodNotSupportedException` import. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | **Edit** | Add a test asserting that `HttpRequestMethodNotSupportedException` maps to `405`, `retryable=false`, and preserves the original message. |

No changes are made to:

- `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`
  (the `@PutMapping("/schedules/{name}/pause")` mapping is already correct).
- Any persistence module, DAO, service, or SDK class.

## 4. Shared contracts

These names and types are reused verbatim by the supporting documents.

### 4.1 The exception-to-status mapping

`ApplicationExceptionMapper.EXCEPTION_STATUS_MAP` is a
`Map<Class<? extends Throwable>, HttpStatus>`. After the fix it contains:

| Exception class | HTTP status |
|---|---|
| `NotFoundException` | `404 NOT_FOUND` |
| `ConflictException` | `409 CONFLICT` |
| `IllegalArgumentException` | `400 BAD_REQUEST` |
| `InvalidFormatException` | `500 INTERNAL_SERVER_ERROR` |
| `NoResourceFoundException` | `404 NOT_FOUND` |
| `FileStorageException` | `413 PAYLOAD_TOO_LARGE` |
| `AccessForbiddenException` | `403 FORBIDDEN` |
| **`HttpRequestMethodNotSupportedException`** | **`405 METHOD_NOT_ALLOWED`** *(new)* |
| *(any other `Throwable`)* | `500 INTERNAL_SERVER_ERROR` (default) |

Lookup is by **exact class** (`getOrDefault(th.getClass(), ...)`), not `instanceof`.
`HttpRequestMethodNotSupportedException` is a concrete class thrown directly by Spring
MVC's dispatcher, so an exact-class entry is sufficient and consistent with the existing
entries.

### 4.2 The error response body

The handler returns a `ResponseEntity<ErrorResponse>`. For a `405` the body fields are:

| `ErrorResponse` field | Value for `405` |
|---|---|
| `instance` | server id (`Utils.getServerId()`) |
| `status` | `405` |
| `message` | the exception message, e.g. `Request method 'GET' is not supported` |
| `retryable` | `false` (only `TransientException` is retryable) |

### 4.3 Logging contract

`logException(...)` already emits `4xx` at `WARN` and `5xx`/unmapped at `ERROR`. Because
`405` is a `4xx` client error, moving it out of the `500` default also correctly moves it
from `ERROR` to `WARN`: a method-mismatch probe is expected client behavior and should not
pollute server error logs. No change to `logException` is needed; the behavior follows
automatically from the new status.

### 4.4 Endpoint contract (unchanged)

The scheduler pause endpoint remains:

```
PUT /api/scheduler/schedules/{name}/pause?reason={reason}
```

Declared in `SchedulerResource.pauseSchedule` with
`@PutMapping("/schedules/{name}/pause")` and `@ResponseStatus(HttpStatus.OK)`. A `GET` to
this path now returns `405` (was `500`).

## 5. Sequence: SDK pause after the fix

```
Java SDK (SchedulerResource.pauseSchedule)
  └─ executeGetThenPutOnMethodNotAllowed(...)
       ├─ GET  /api/scheduler/schedules/{name}/pause
       │     Spring MVC → HttpRequestMethodNotSupportedException
       │     ApplicationExceptionMapper → 405 METHOD_NOT_ALLOWED   ← fixed here
       ├─ (client recognizes 405) fall through
       └─ PUT  /api/scheduler/schedules/{name}/pause?reason=...
             SchedulerResource.pauseSchedule → 200 OK
```

## 6. Supporting documents

- [`error-mapping.md`](error-mapping.md) — the exception→status contract and edit detail.
- [`testing.md`](testing.md) — verification plan and the exact assertions to add.
