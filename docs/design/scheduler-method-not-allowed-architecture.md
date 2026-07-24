# Design: Correct HTTP status for unsupported request methods (Issue #1393)

> Source of truth for this change. Supporting docs
> [scheduler-method-not-allowed-api.md](scheduler-method-not-allowed-api.md) and
> [scheduler-method-not-allowed-testing.md](scheduler-method-not-allowed-testing.md)
> reuse the names, types, and layout defined here verbatim.

## 1. Problem statement

`SchedulerClient.pauseSchedule(...)` in the Java SDK fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
```

The Java client's `io.orkes.conductor.client.http.SchedulerResource.pauseSchedule`
uses a **GET-then-PUT-on-405 fallback** (`executeGetThenPutOnMethodNotAllowed`):
it first probes the endpoint with `GET`, and if the server answers
`405 Method Not Allowed` it re-issues the call as `PUT`. The pause endpoint on the
server is `PUT /api/scheduler/schedules/{name}/pause`, so the probe `GET` is
expected to be rejected with `405`, after which the client proceeds to the `PUT`.

Instead, the server answers the probe `GET` with **`500 Internal Server Error`**
carrying the message `Request method 'GET' is not supported`. The client only
treats a clean `405` as "fall through to PUT"; a `500` is surfaced as a hard
exception. The lifecycle therefore breaks at the first pause call even though the
`PUT` endpoint itself is healthy (create + list succeed beforehand).

## 2. Root cause (server side)

When a request reaches an existing path with a method that has no handler, Spring
MVC throws `org.springframework.web.HttpRequestMethodNotSupportedException`. In
this repository that exception is caught by the catch-all handler in
`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`:

```java
@ExceptionHandler(Throwable.class)
public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
    HttpStatus status =
            EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
    ...
}
```

`HttpRequestMethodNotSupportedException` is **not** present in
`EXCEPTION_STATUS_MAP`, so it defaults to `HttpStatus.INTERNAL_SERVER_ERROR`
(`500`). The correct status for this condition is `405 Method Not Allowed`.

This is a **server-wide** defect, not scheduler-specific: any wrong-method request
to any Conductor endpoint currently returns `500` rather than `405`. The scheduler
pause path is simply the first place a well-behaved client trips over it. Fixing
it in the server restores standards-compliant behaviour for every endpoint and
unblocks the Java client's fallback with no client change.

The pause/resume mappings themselves are already correct and are **not** modified:

```java
// scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java
@PutMapping("/schedules/{name}/pause")
public void pauseSchedule(@PathVariable("name") String name,
        @RequestParam(value = "reason", required = false) String reason) { ... }

@PutMapping("/schedules/{name}/resume")
public void resumeSchedule(@PathVariable("name") String name) { ... }
```

## 3. Scope

Minimal, focused change as the issue requests.

- **In scope:** map `HttpRequestMethodNotSupportedException` to `405 Method Not
  Allowed` in the REST exception mapper, and emit the standards-required `Allow`
  response header listing the methods the endpoint does support.
- **Out of scope:** the Java SDK's `SchedulerResource` fallback logic (separate
  `conductor-oss/java-sdk` repo); any change to `SchedulerResource.java` mappings
  (the `PUT` mappings are correct); the Python SDK (already passes).

## 4. Overview & tech stack

| Aspect | Detail |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot / Spring MVC (`@RestControllerAdvice`) |
| Build | Gradle |
| Module touched | `rest` |
| Error model | `com.netflix.conductor.common.validation.ErrorResponse` |
| Exception class | `org.springframework.web.HttpRequestMethodNotSupportedException` |
| Metrics | `com.netflix.conductor.metrics.Monitors.error(...)` |

The fix stays entirely inside the existing `@RestControllerAdvice` mechanism; no
new component, bean, or configuration property is introduced.

## 5. Complete file layout

Every file this change touches or adds.

| File | Kind | Responsibility |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | Edit | Map `HttpRequestMethodNotSupportedException` → `405`; add a dedicated handler that also sets the `Allow` header. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | Add | Unit test: a `HttpRequestMethodNotSupportedException` produces a `405` `ResponseEntity` with a populated `Allow` header and an `ErrorResponse` body with `retryable=false`. |

No production file other than `ApplicationExceptionMapper.java` changes.
`SchedulerResource.java` is inspected only to confirm the pause/resume endpoints
are `PUT`-mapped; it is left untouched.

## 6. Shared contracts (reuse verbatim)

These names and signatures are the single source of truth; supporting docs reuse
them exactly.

### 6.1 Exception → status mapping

Entry added to `EXCEPTION_STATUS_MAP` (keeps the catch-all coherent even if the
dedicated handler is ever bypassed):

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

### 6.2 Dedicated handler contract

Because the `Allow` header is part of a correct `405` response (RFC 9110
§15.5.6), a dedicated `@ExceptionHandler` is added ahead of the catch-all. It
reuses the same `ErrorResponse` shape as `handleAll` and the same
`Monitors.error(...)` accounting.

```java
@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<ErrorResponse> handleMethodNotSupported(
        HttpServletRequest request, HttpRequestMethodNotSupportedException ex) {

    HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
    logException(request, ex, status);

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setInstance(host);
    errorResponse.setStatus(status.value());
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setRetryable(false);

    Monitors.error("error", String.valueOf(status.value()));

    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    Set<HttpMethod> supported = ex.getSupportedHttpMethods();
    if (supported != null && !supported.isEmpty()) {
        builder.allow(supported.toArray(new HttpMethod[0]));
    }
    return builder.body(errorResponse);
}
```

### 6.3 Imports to add

```java
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

### 6.4 Reused existing members

The handler reuses, verbatim, members already on the mapper:

```java
private final String host = Utils.getServerId();

private void logException(HttpServletRequest request, Throwable exception, HttpStatus status)
```

`logException(...)` already routes `4xx` to `WARN`, so a wrong-method probe no
longer pollutes the `ERROR` logs.

### 6.5 Error response body (unchanged type)

`ErrorResponse` (from `conductor-common`) is populated identically to `handleAll`:

| Field | Value on 405 |
|---|---|
| `instance` | `Utils.getServerId()` |
| `status` | `405` |
| `message` | `ex.getMessage()` (e.g. `Request method 'GET' is not supported`) |
| `retryable` | `false` |

## 7. Behaviour after the fix

1. Client probes `GET /api/scheduler/schedules/{name}/pause`.
2. Spring throws `HttpRequestMethodNotSupportedException`
   (`supportedHttpMethods = [PUT]`).
3. `handleMethodNotSupported` returns `405` with `Allow: PUT` and an
   `ErrorResponse` body.
4. The Java client's `executeGetThenPutOnMethodNotAllowed` sees `405` and
   re-issues the call as `PUT /api/scheduler/schedules/{name}/pause`.
5. The `PUT` handler `pauseSchedule(name, reason)` runs → `200 OK`.
6. The scheduler lifecycle (deploy → create → list → **pause** → resume →
   preview → delete) completes.

## 8. Verification

Per repo conventions, after editing:

```
./gradlew spotlessApply
./gradlew :rest:test
```

See [scheduler-method-not-allowed-testing.md](scheduler-method-not-allowed-testing.md)
for the test plan and
[scheduler-method-not-allowed-api.md](scheduler-method-not-allowed-api.md) for the
HTTP contract detail.
</content>
