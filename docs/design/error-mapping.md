# Error Mapping — `HttpRequestMethodNotSupportedException` → `405`

This document details the single edit that resolves issue #1393. It reuses the contracts
defined in [`architecture.md`](architecture.md) verbatim.

## 1. Target file

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`

This class is a `@RestControllerAdvice` (`@Order(ValidationExceptionMapper.ORDER + 1)`)
with one catch-all handler:

```java
@ExceptionHandler(Throwable.class)
public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
    HttpStatus status =
            EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
    ...
}
```

The `status` for every response is decided solely by `EXCEPTION_STATUS_MAP` (exact-class
lookup) with a `500` default. This is the only place that needs to change.

## 2. The change

### 2.1 Import

Add, alongside the existing `org.springframework.web.servlet.resource.NoResourceFoundException`
import:

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

### 2.2 Map entry

Add one line inside the static initializer block that populates `EXCEPTION_STATUS_MAP`:

```java
static {
    EXCEPTION_STATUS_MAP.put(NotFoundException.class, HttpStatus.NOT_FOUND);
    EXCEPTION_STATUS_MAP.put(ConflictException.class, HttpStatus.CONFLICT);
    EXCEPTION_STATUS_MAP.put(IllegalArgumentException.class, HttpStatus.BAD_REQUEST);
    EXCEPTION_STATUS_MAP.put(InvalidFormatException.class, HttpStatus.INTERNAL_SERVER_ERROR);
    EXCEPTION_STATUS_MAP.put(NoResourceFoundException.class, HttpStatus.NOT_FOUND);
    EXCEPTION_STATUS_MAP.put(FileStorageException.class, HttpStatus.PAYLOAD_TOO_LARGE);
    EXCEPTION_STATUS_MAP.put(AccessForbiddenException.class, HttpStatus.FORBIDDEN);
    // An unsupported HTTP method is a client error (RFC 7231 §6.5.5), not a server
    // failure. Spring MVC raises HttpRequestMethodNotSupportedException directly, so map
    // it to 405 rather than letting it fall through to the 500 default. This lets clients
    // that probe a route with the wrong method (e.g. the Java SDK's GET-then-PUT-on-405
    // fallback for scheduler pause/resume) receive a clean 405 and recover. See #1393.
    EXCEPTION_STATUS_MAP.put(
            HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
}
```

## 3. Why exact-class lookup is safe here

The handler uses `getOrDefault(th.getClass(), ...)`, keyed on the runtime class. Spring
MVC throws `HttpRequestMethodNotSupportedException` as a concrete, final-in-practice type
directly from `DispatcherServlet`; it is not subclassed within Conductor. An exact-class
entry therefore matches every occurrence and stays consistent with the existing entries,
which are all exact-class keys.

## 4. Behavior before vs after

| Request | Before | After |
|---|---|---|
| `GET /api/scheduler/schedules/{name}/pause` | `500`, message `Request method 'GET' is not supported`, logged at `ERROR` | `405`, same message, logged at `WARN` |
| `PUT /api/scheduler/schedules/{name}/pause` | `200 OK` | `200 OK` (unchanged) |
| `SchedulerClient.pauseSchedule(...)` (SDK) | throws `ConductorClientException {status=500}` | GET probe returns `405`, SDK falls through to `PUT`, pause succeeds |

The response body continues to be an `ErrorResponse` with `instance`, `status`,
`message`, and `retryable=false`, exactly as specified in
[`architecture.md`](architecture.md) §4.2.

## 5. Blast radius

The mapper is global, so the correction applies to every REST controller, not just the
scheduler. This is intentional and desirable: any wrong-method request across the API now
returns the spec-correct `405` instead of a misleading `500`. No endpoint's success path,
request/response body, or existing mapped status (`404`, `409`, `400`, `413`, `403`,
`500` for `InvalidFormatException`) is altered.
