# Architecture — Correct HTTP status for unsupported request methods (Issue #1393)

## Problem statement

`SchedulerClient.pauseSchedule(...)` in the Java SDK fails with:

```
Request method 'GET' is not supported {status=500, retryable: false}
```

The Java client's `io.orkes.conductor.client.http.SchedulerResource` implements a
**GET-then-PUT-on-405 fallback** (`executeGetThenPutOnMethodNotAllowed`): it probes an
endpoint with `GET`, and when the server answers `405 Method Not Allowed` it falls through
to the real `PUT` call. The pause endpoint is `PUT /api/scheduler/schedules/{name}/pause`.

Against this server the probing `GET` comes back as **HTTP 500**, not **405**, so the client
treats it as a hard error and never issues the `PUT`. The pause call therefore throws.

## Root cause

The endpoint exists and is correctly declared as `PUT`
(`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java:121`):

```java
@PutMapping("/schedules/{name}/pause")
@ResponseStatus(HttpStatus.OK)
public void pauseSchedule(
        @PathVariable("name") String name,
        @RequestParam(value = "reason", required = false) String reason) { ... }
```

When Spring MVC receives a `GET` for a path that is only mapped for `PUT`, it raises
`org.springframework.web.HttpRequestMethodNotSupportedException`. By servlet/Spring default
this maps to **405 Method Not Allowed** with an `Allow` header.

However, this repository installs a global exception mapper,
`com.netflix.conductor.rest.controllers.ApplicationExceptionMapper`
(`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`),
annotated `@RestControllerAdvice` with a catch-all handler:

```java
@ExceptionHandler(Throwable.class)
public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
    HttpStatus status =
            EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
    ...
}
```

Because `Throwable.class` is handled, this advice intercepts
`HttpRequestMethodNotSupportedException` **before** Spring's built-in
`ResponseEntityExceptionHandler` can turn it into a 405. The exception class is absent from
`EXCEPTION_STATUS_MAP`, so `getOrDefault` returns `HttpStatus.INTERNAL_SERVER_ERROR` (500).
The message `"Request method 'GET' is not supported"` is the exception's own message, surfaced
verbatim in the `ErrorResponse`.

This is a server-side defect: a wrong-method request is a **client error (4xx)**, not a
server failure (5xx). The Python SDK does not exercise the GET-then-PUT probe, which is why
its scheduling examples pass against the same server.

## Chosen fix (minimal, focused)

Add explicit mappings in `ApplicationExceptionMapper.EXCEPTION_STATUS_MAP` so the two standard
Spring "request not acceptable for this endpoint" exceptions resolve to their correct HTTP
status codes instead of falling through to 500:

| Exception | Correct status |
|---|---|
| `org.springframework.web.HttpRequestMethodNotSupportedException` | `405 METHOD_NOT_ALLOWED` |
| `org.springframework.web.HttpMediaTypeNotSupportedException` | `415 UNSUPPORTED_MEDIA_TYPE` |

`HttpMediaTypeNotSupportedException` is included because it is the sibling defect from the same
catch-all interception (a valid request path with an unsupported `Content-Type` would likewise
be reported as 500). Both are one-line map entries and share the same root cause; no other
behavior changes.

With the 405 restored, the Java client's `executeGetThenPutOnMethodNotAllowed` recognizes the
`405` on its probing `GET` and proceeds to the real `PUT`, and `pauseSchedule` succeeds. No
change is required in the scheduler module or the Java SDK.

### Why not change the scheduler endpoint or the Java SDK

- The scheduler endpoint is already correct (`@PutMapping`). Adding a `GET` alias would be
  redundant and would broaden the public API surface beyond what the issue requires.
- The Java SDK lives in a separate repository (`conductor-oss/java-sdk`) and is not part of
  this build. Its fallback logic is behaving correctly *given a correct 405*; the server is
  the component returning the wrong status.
- Restoring standard HTTP semantics in `ApplicationExceptionMapper` fixes this for every
  endpoint and every client, not just the scheduler pause path.

## Tech stack

- Java 21, Spring Boot (Spring MVC), Gradle.
- Affected module: `rest` (the REST controller-advice layer shared by all controllers).
- No new dependencies. No new files. No schema or data-model changes.

## Complete file layout

Exactly one production file changes; one test file is extended.

| File | Change | Responsibility |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | **Edit** | Add two entries to the static `EXCEPTION_STATUS_MAP`; add the two Spring imports. No logic change to `handleAll` or `logException`. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | **Edit** | Add tests asserting `HttpRequestMethodNotSupportedException` -> 405 and `HttpMediaTypeNotSupportedException` -> 415. |

No files are created or deleted.

## Shared contract (verbatim names/types)

Every component in this change reuses the following exact identifiers.

### The exception-to-status map

Located in `ApplicationExceptionMapper`:

```java
private static final Map<Class<? extends Throwable>, HttpStatus> EXCEPTION_STATUS_MAP =
        new HashMap<>();
```

New entries added to the existing `static { ... }` block (do not remove existing entries):

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
EXCEPTION_STATUS_MAP.put(
        HttpMediaTypeNotSupportedException.class, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
```

New imports (Spring web, alongside the existing imports):

```java
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

### The error payload (unchanged)

The response body remains `com.netflix.conductor.common.validation.ErrorResponse`, populated
by the unchanged `handleAll` method:

- `instance` = server id (`Utils.getServerId()`)
- `status`   = `405` (or `415`)
- `message`  = the exception message, e.g. `Request method 'GET' is not supported`
- `retryable`= `false` (only `TransientException` is retryable; unchanged)

### Logging contract (unchanged, but now correct)

`logException(...)` already routes `status.is4xxClientError()` to `WARN` and everything else to
`ERROR`. Because the wrong-method case now resolves to 405 (a 4xx), it is logged at `WARN`
rather than polluting the error log at `ERROR` — the behavior the existing comment intends.

### Naming conventions

- Follow the existing style of `EXCEPTION_STATUS_MAP.put(<Exception>.class, HttpStatus.<CODE>);`
  entries, one per line, inside the single `static` initializer.
- No emojis in code, comments, or logs.
- Run `./gradlew spotlessApply` after editing.

## Supporting documents

- [`api.md`](./api.md) — HTTP-level before/after behavior and the scheduler pause contract.
- [`testing.md`](./testing.md) — unit test additions and manual verification against the repro.
