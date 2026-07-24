# Architecture — Fix 500 for unsupported HTTP method (Issue #1393)

> **Status:** Design — single source of truth for this change.
> **Scope:** Server-side only. Restore the standard `405 Method Not Allowed`
> response for requests that hit an existing route with an unsupported HTTP
> method, so the Java SDK's GET-then-PUT-on-405 fallback in
> `SchedulerClient.pauseSchedule(...)` proceeds to the PUT instead of throwing.

This document is authoritative. The supporting docs (`api.md`, `testing.md`)
reuse the names, types, and file paths defined here verbatim.

---

## 1. Overview

### 1.1 Problem statement

`SchedulerClient.pauseSchedule(...)` in `conductor-oss/java-sdk` fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
    at io.orkes.conductor.client.http.SchedulerResource.executeGetThenPutOnMethodNotAllowed(...)
    at io.orkes.conductor.client.http.SchedulerResource.pauseSchedule(...)
```

The pause endpoint exists on the server as **`PUT /api/scheduler/schedules/{name}/pause`**
(see §2.1). The Java client uses a compatibility shim
(`executeGetThenPutOnMethodNotAllowed`) that first issues a `GET` and, on a
`405 Method Not Allowed`, falls through to the real `PUT`. Against this server,
the probing `GET` comes back as **`500 Internal Server Error`** with the message
`Request method 'GET' is not supported`, which the client surfaces as a fatal
exception instead of treating it as "wrong method, try PUT".

The Python SDK does not use this GET-then-PUT probe, which is why its scheduling
examples pass against the same server.

### 1.2 Root cause

The message `Request method 'GET' is not supported` is the text of Spring MVC's
`org.springframework.web.HttpRequestMethodNotSupportedException`. By default
Spring maps this exception to HTTP **405**. In this repository, however, a
catch-all `@RestControllerAdvice` intercepts it first:

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`

```java
@RestControllerAdvice
@Order(ValidationExceptionMapper.ORDER + 1)
public class ApplicationExceptionMapper {

    private static final Map<Class<? extends Throwable>, HttpStatus> EXCEPTION_STATUS_MAP =
            new HashMap<>();

    static {
        EXCEPTION_STATUS_MAP.put(NotFoundException.class, HttpStatus.NOT_FOUND);
        EXCEPTION_STATUS_MAP.put(ConflictException.class, HttpStatus.CONFLICT);
        EXCEPTION_STATUS_MAP.put(IllegalArgumentException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_STATUS_MAP.put(InvalidFormatException.class, HttpStatus.INTERNAL_SERVER_ERROR);
        EXCEPTION_STATUS_MAP.put(NoResourceFoundException.class, HttpStatus.NOT_FOUND);
        EXCEPTION_STATUS_MAP.put(FileStorageException.class, HttpStatus.PAYLOAD_TOO_LARGE);
        EXCEPTION_STATUS_MAP.put(AccessForbiddenException.class, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleAll(HttpServletRequest request, Throwable th) {
        HttpStatus status =
                EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
        ...
    }
}
```

Because `@ExceptionHandler(Throwable.class)` is broad enough to catch Spring's
own MVC dispatch exceptions, and `HttpRequestMethodNotSupportedException` is not
a key in `EXCEPTION_STATUS_MAP`, `getOrDefault(...)` returns the fallback
`HttpStatus.INTERNAL_SERVER_ERROR` (500). The correct status is
`405 Method Not Allowed`.

### 1.3 Chosen fix

Map `HttpRequestMethodNotSupportedException` to `HttpStatus.METHOD_NOT_ALLOWED`
in `EXCEPTION_STATUS_MAP`. This is the minimal, focused change the issue asks
for: it restores standards-compliant behaviour for **every** route in the
server (not just the scheduler), and it makes the Java SDK's existing
GET-then-PUT-on-405 fallback work as designed — no client change required.

Explicitly **out of scope** (do not implement as part of this issue):

- Changing the Java SDK client (separate repo `conductor-oss/java-sdk`).
- Removing the client's GET-then-PUT compatibility shim.
- Adding a GET variant of the pause endpoint on the server.
- Broad reclassification of other Spring MVC dispatch exceptions
  (e.g. `HttpMediaTypeNotSupportedException`). Those are unrelated to #1393 and
  belong in their own change if ever needed.

### 1.4 Tech stack (unchanged)

| Concern | Technology |
|---|---|
| Language / runtime | Java 21 |
| Build | Gradle |
| Web framework | Spring Boot / Spring MVC (`@RestControllerAdvice`, `@ExceptionHandler`) |
| REST module | `rest` (Conductor "rest" Gradle module) |
| Scheduler REST surface | `scheduler/core` module (`io.orkes.conductor.scheduler.rest`) |
| Error payload type | `com.netflix.conductor.common.validation.ErrorResponse` |

---

## 2. Relevant existing components (context, do not change unless noted)

### 2.1 Server pause endpoint — already correct

`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`

```java
@RestController
@RequestMapping("/api/scheduler")
public class SchedulerResource {

    @PutMapping("/schedules/{name}/pause")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Pause a schedule")
    public void pauseSchedule(
            @PathVariable("name") String name,
            @RequestParam(value = "reason", required = false) String reason) {
        schedulerService.pauseSchedule(name, reason);
    }
}
```

The route exists and only accepts `PUT`. A `GET` to the same path is what
triggers `HttpRequestMethodNotSupportedException`. **No change here.**

### 2.2 Exception advice ordering — context

- `ValidationExceptionMapper` — `@Order(Ordered.HIGHEST_PRECEDENCE)`, handles
  `jakarta.validation.ValidationException`.
- `ApplicationExceptionMapper` — `@Order(ValidationExceptionMapper.ORDER + 1)`,
  the catch-all being modified.

Ordering is unchanged. We only extend the status map inside
`ApplicationExceptionMapper`.

---

## 3. Complete file layout

### 3.1 Source files to modify

| File | Change | Responsibility after change |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | Add one import + one map entry (see §4) | Map `HttpRequestMethodNotSupportedException` → `405 METHOD_NOT_ALLOWED`; keep all other mappings and the 500 default unchanged. |

No new source files are created. No files are deleted or moved.

### 3.2 Test files to add / modify

| File | Change | Responsibility |
|---|---|---|
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | Add or extend | Unit-verify that `HttpRequestMethodNotSupportedException` maps to 405 and preserves the message/`ErrorResponse` shape; confirm unmapped throwables still map to 500. |

If `ApplicationExceptionMapperTest.java` does not already exist, create it in the
path above. Test details are in `testing.md`.

### 3.3 Design docs (this change set)

```
docs/design/scheduler-pause-405/
├── architecture.md   # this file — source of truth
├── api.md            # HTTP contract: request/method matrix, 405 response body
└── testing.md        # verification plan (unit + repro)
```

---

## 4. Shared contract — the exact code change

Every supporting doc references these exact identifiers.

### 4.1 Import to add

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

### 4.2 Map entry to add (inside the existing `static { ... }` block)

```java
// A request reached an existing route with an unsupported HTTP method.
// Spring's default is 405; without this entry the catch-all below would
// remap it to 500 (see issue #1393). 405 lets HTTP clients that probe
// with one method and fall back to another behave correctly.
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

### 4.3 Invariants that must hold after the change

- `EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR)`
  is the only lookup; the default remains `INTERNAL_SERVER_ERROR` (500).
- Lookup is by **exact class** (`th.getClass()`), so the key must be the concrete
  `HttpRequestMethodNotSupportedException` class, matching how Spring throws it.
- The `ErrorResponse` body is still populated with
  `status = 405`, `message = th.getMessage()` (e.g.
  `Request method 'GET' is not supported`), `instance = host`,
  `retryable = false` (not a `TransientException`).
- `logException(...)` now logs this case at **WARN** (it is a 4xx), not ERROR —
  this follows automatically from the existing `status.is4xxClientError()`
  branch; no extra code.

### 4.4 Naming conventions (reused verbatim by other docs)

| Name | Meaning |
|---|---|
| `ApplicationExceptionMapper` | The `@RestControllerAdvice` under change |
| `EXCEPTION_STATUS_MAP` | Static `Class → HttpStatus` map inside it |
| `handleAll(HttpServletRequest, Throwable)` | The catch-all handler method |
| `HttpRequestMethodNotSupportedException` | Spring exception thrown for wrong HTTP method on an existing route |
| `HttpStatus.METHOD_NOT_ALLOWED` | Target status (405) |
| `ErrorResponse` | `com.netflix.conductor.common.validation.ErrorResponse` returned to clients |

---

## 5. Behaviour before vs. after

| Scenario | Before | After |
|---|---|---|
| `GET /api/scheduler/schedules/{name}/pause` | 500, message `Request method 'GET' is not supported` | **405**, same message |
| `PUT /api/scheduler/schedules/{name}/pause` (valid) | 200 | 200 (unchanged) |
| Java SDK `pauseSchedule(...)` GET-then-PUT probe | throws on the 500 GET | sees 405, falls through to PUT, succeeds |
| Any genuinely unhandled `Throwable` | 500 | 500 (unchanged) |

---

## 6. Risks & considerations

- **Blast radius:** Global — affects every controller behind this advice. This
  is intended and correct; 405 is the standard for method mismatch and no route
  previously relied on receiving 500 for it.
- **Log noise:** Reclassifying to 4xx moves these from ERROR to WARN, reducing
  false 5xx alerts. Desirable.
- **`Allow` header:** Spring's own 405 handling would normally add an `Allow`
  header listing permitted methods. Routing through this advice does not emit
  that header. The client fallback keys only on the 405 status, so this is
  acceptable and left out of scope for the minimal fix; noted for follow-up.
