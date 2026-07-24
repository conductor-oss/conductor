# Exception-to-status mapping (Issue #1393)

Supporting doc for `architecture.md` in this directory. All names and types are
reused from that document verbatim.

## Where the mapping lives

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`

The class is a global `@RestControllerAdvice` whose only handler,
`handleAll(HttpServletRequest, Throwable)`, converts any thrown `Throwable` into
an `ErrorResponse` plus an HTTP status. The status is resolved by exact class
lookup against a static map:

```java
private static final Map<Class<? extends Throwable>, HttpStatus> EXCEPTION_STATUS_MAP =
        new HashMap<>();
```

Resolution uses `getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR)`,
so any class absent from the map becomes `500`. The lookup is by **exact class**,
not `instanceof`; `HttpRequestMethodNotSupportedException` is a concrete final
class thrown directly by Spring MVC, so an exact-class entry is sufficient.

## Current entries (before)

| Exception class | Status |
|---|---|
| `NotFoundException` | `NOT_FOUND` (404) |
| `ConflictException` | `CONFLICT` (409) |
| `IllegalArgumentException` | `BAD_REQUEST` (400) |
| `InvalidFormatException` | `INTERNAL_SERVER_ERROR` (500) |
| `NoResourceFoundException` | `NOT_FOUND` (404) |
| `FileStorageException` | `PAYLOAD_TOO_LARGE` (413) |
| `AccessForbiddenException` | `FORBIDDEN` (403) |
| *(anything else — including `HttpRequestMethodNotSupportedException`)* | `INTERNAL_SERVER_ERROR` (500) ← **the bug** |

## Entry to add (after)

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

Add alongside the other `EXCEPTION_STATUS_MAP.put(...)` calls in the `static {}`
block, and add the import:

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

Result: `HttpRequestMethodNotSupportedException` → `METHOD_NOT_ALLOWED` (405).

## Downstream effects of a 4xx status (unchanged code, changed behavior)

`handleAll(...)` already branches on `status.is4xxClientError()`:

- **`retryable`**: set to `th instanceof TransientException`.
  `HttpRequestMethodNotSupportedException` is not transient, so `retryable =
  false` — consistent with the client-observed `retryable: false`.
- **Logging**: `logException(...)` logs 4xx at **WARN** and 5xx (and unmapped) at
  **ERROR**. Moving this case from 500 to 405 moves its log line from ERROR to
  WARN, which is correct: a caller using the wrong method is a client-side
  mistake, not a server fault, and should not pollute error logs.
- **Body**: `ErrorResponse` still carries `instance` (server id), `status` (now
  `405`), `message` (`Request method 'GET' is not supported`), and `retryable`
  (`false`). Shape is unchanged.

## Why this fixes the Java client

The client's `executeGetThenPutOnMethodNotAllowed` issues a GET, and only when it
sees a `405` does it retry with the correct `PUT`. Previously the server's `500`
short-circuited that logic and threw. With the server now returning a genuine
`405`, the fallback proceeds to the PUT and `pauseSchedule(...)` (and any other
GET-then-PUT scheduler call, e.g. resume) completes.
