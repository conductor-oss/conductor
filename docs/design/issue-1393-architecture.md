# Architecture — Fix 500 on unsupported HTTP method (Issue #1393)

Source of truth for this change set. The supporting docs
[issue-1393-api-contract.md](./issue-1393-api-contract.md) and
[issue-1393-testing.md](./issue-1393-testing.md) reuse the names, types, and status
contract defined here verbatim.

> Note: `docs/design/architecture.md` already documents an unrelated feature (Agent Worker
> Architecture). These issue-scoped files intentionally do not overwrite it.

## 1. Overview

`SchedulerClient.pauseSchedule(...)` in the Conductor Java SDK fails with:

```
com.netflix.conductor.client.exception.ConductorClientException:
    Request method 'GET' is not supported {status=500, retryable: false}
```

The SDK's `SchedulerResource.executeGetThenPutOnMethodNotAllowed(...)` implements a
compatibility fallback: it probes an endpoint with `GET`, and when the server answers
`405 Method Not Allowed`, it retries the same URL with `PUT`. The pause endpoint on the
server is mapped **only** for `PUT`
(`scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java:121`):

```java
@PutMapping("/schedules/{name}/pause")
public void pauseSchedule(@PathVariable("name") String name,
                          @RequestParam(value = "reason", required = false) String reason) { ... }
```

So a probing `GET /api/scheduler/schedules/{name}/pause` is genuinely unsupported and
Spring MVC raises `org.springframework.web.HttpRequestMethodNotSupportedException`, whose
message is exactly `Request method 'GET' is not supported`. Spring's own default would map
this to **405**. Conductor overrides that default with a catch-all advice, and that is
where the status gets corrupted to 500.

### Root cause

`rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java`
is a `@RestControllerAdvice` with a single `@ExceptionHandler(Throwable.class)`. It looks
the thrown type up in `EXCEPTION_STATUS_MAP` and, on a miss, falls back to
`HttpStatus.INTERNAL_SERVER_ERROR` (500):

```java
HttpStatus status =
        EXCEPTION_STATUS_MAP.getOrDefault(th.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
```

`HttpRequestMethodNotSupportedException` is not in the map, so a legitimate
"wrong HTTP method" condition is reported as **500** instead of **405**. The SDK fallback
only recognizes 405 and therefore surfaces the 500 as a hard exception instead of
proceeding to the `PUT`.

This affects **every** endpoint reached with the wrong method, not just scheduler pause;
pause is simply the first place the SDK's probe-then-retry pattern exercises it.

### Fix (minimal, server-side)

Add one entry to `EXCEPTION_STATUS_MAP` mapping
`HttpRequestMethodNotSupportedException` → `HttpStatus.METHOD_NOT_ALLOWED`. This restores
correct HTTP semantics (405 for a method mismatch) so the existing, unchanged SDK fallback
works as designed. No SDK change is required and no new route is added.

Rejected alternatives:

- **Add a `@GetMapping` alias for pause on the server** — pause is a state mutation; a GET
  handler for it would violate GET's safe/read-only semantics, and it would not fix the
  same class of bug for the many other write-only endpoints.
- **Change the SDK to treat "500 with that message" as 405** — the SDK lives in a separate
  repo (`conductor-oss/java-sdk`), is not in this repository, and matching on an error
  string is brittle. Returning the correct status from the server is the right contract and
  fixes all current and future SDK versions at once.

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Build | Gradle (`./gradlew :rest:test`) |
| Web framework | Spring Boot / Spring MVC (`spring-web`) |
| Error handling | `@RestControllerAdvice` (`ApplicationExceptionMapper`) |
| Test framework | JUnit (existing `ApplicationExceptionMapperTest`) |
| Metrics | `com.netflix.conductor.metrics.Monitors` |

## 3. Module / file layout

Only the `rest` module changes. No new production files; one existing file edited and one
existing test file extended.

| File | Change | Responsibility |
|---|---|---|
| `rest/src/main/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapper.java` | **Edit** — add import + one map entry | Central exception→HTTP-status mapping. Now maps method-not-supported to 405. |
| `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java` | **Edit** — add test case | Verifies `HttpRequestMethodNotSupportedException` yields 405 with the expected body. |

Referenced but **unchanged** (do not modify):

- `scheduler/core/src/main/java/io/orkes/conductor/scheduler/rest/SchedulerResource.java`
  — pause is `@PutMapping` only; this is correct and stays as-is.

## 4. Shared contracts

Every component in this change reuses these exact names.

### 4.1 Exception type

- Fully qualified: `org.springframework.web.HttpRequestMethodNotSupportedException`
- Thrown by Spring MVC's `DispatcherServlet` when a request reaches a mapped path with an
  HTTP method that has no handler.
- `getMessage()` returns e.g. `Request method 'GET' is not supported`.

### 4.2 Status mapping entry (verbatim)

Import to add (grouped with the other `org.springframework.web...` imports):

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
```

Entry to add inside the existing `static { ... }` block of `EXCEPTION_STATUS_MAP` in
`ApplicationExceptionMapper`:

```java
EXCEPTION_STATUS_MAP.put(
        HttpRequestMethodNotSupportedException.class, HttpStatus.METHOD_NOT_ALLOWED);
```

No other lines in `handleAll` or `logException` change. Because 405 is a 4xx status,
`logException` already routes it to `LOGGER.warn` rather than `LOGGER.error`, which is the
desired behavior: a client using the wrong method is a client-side condition, not a server
fault, and should not pollute error logs.

### 4.3 HTTP status contract (post-fix)

The `ErrorResponse` (`com.netflix.conductor.common.validation.ErrorResponse`) body shape is
unchanged. For an unsupported-method request the response becomes:

| Field | Value |
|---|---|
| HTTP status | `405 Method Not Allowed` |
| `status` | `405` |
| `message` | `Request method 'GET' is not supported` (from `th.getMessage()`) |
| `retryable` | `false` (not a `TransientException`) |
| `instance` | server id (unchanged) |

### 4.4 Behavior the SDK depends on

The Java SDK's `SchedulerResource.executeGetThenPutOnMethodNotAllowed` treats **405** as
the signal to retry with `PUT`. The only requirement this change places on the server is:

> A request to an existing path with an HTTP method that has no handler MUST return HTTP
> `405`, not `500`.

After the fix, the scheduler lifecycle in `Example99ScheduledAgent`
(deploy → create → list → **pause** → resume → preview → delete) completes end to end.
