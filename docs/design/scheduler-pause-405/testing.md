# Testing Plan — Method-not-supported → 405 (Issue #1393)

Supporting doc for `architecture.md`. Reuses its names/paths verbatim.

## 1. Goals

1. `HttpRequestMethodNotSupportedException` maps to `405 METHOD_NOT_ALLOWED`.
2. The `ErrorResponse` body is well-formed: `status == 405`, message preserved,
   `retryable == false`.
3. No regression: an unmapped `Throwable` still maps to `500`; already-mapped
   exceptions (e.g. `NotFoundException` → 404) are unchanged.

## 2. Unit test

**File:**
`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`

Instantiate `ApplicationExceptionMapper` directly and invoke
`handleAll(HttpServletRequest, Throwable)` with a mocked/stub `HttpServletRequest`
(only `getRequestURI()` is used, for logging). Assert on the returned
`ResponseEntity<ErrorResponse>`.

### Cases

| # | Input `Throwable` | Expected `ResponseEntity` status | Expected `ErrorResponse.status` | Expected `retryable` |
|---|---|---|---|---|
| 1 | `new HttpRequestMethodNotSupportedException("GET")` | `405 METHOD_NOT_ALLOWED` | `405` | `false` |
| 2 | `new NotFoundException("x")` | `404 NOT_FOUND` | `404` | `false` |
| 3 | `new RuntimeException("boom")` (unmapped) | `500 INTERNAL_SERVER_ERROR` | `500` | `false` |

Notes:

- `new HttpRequestMethodNotSupportedException("GET")` produces the message
  `Request method 'GET' is not supported`; assert the body message contains that
  text.
- Lookup is by exact class (`th.getClass()`); constructing the concrete
  `HttpRequestMethodNotSupportedException` matches how Spring throws it.
- Keep to the module's testing style (avoid mocks where a real object works; a
  lightweight stub for `HttpServletRequest` is fine since Servlet types are
  interfaces).

### Sketch

```java
ApplicationExceptionMapper mapper = new ApplicationExceptionMapper();
HttpServletRequest req = /* stub returning a URI from getRequestURI() */;

ResponseEntity<ErrorResponse> resp =
        mapper.handleAll(req, new HttpRequestMethodNotSupportedException("GET"));

assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
assertEquals(405, resp.getBody().getStatus());
assertTrue(resp.getBody().getMessage().contains("Request method 'GET' is not supported"));
assertFalse(resp.getBody().isRetryable());
```

## 3. Optional web-layer test

If an existing `@WebMvcTest`/MockMvc harness is available for the scheduler
`SchedulerResource`, add:

```
mockMvc.perform(get("/api/scheduler/schedules/eng_digest_99/pause"))
       .andExpect(status().isMethodNotAllowed());   // 405, not 500
```

Only add this if such a harness already exists in the `scheduler/core` or
`rest` module; do not introduce new integration infrastructure for this fix.

## 4. Manual repro (from the issue)

Against a local server on `http://localhost:8080/api`:

```bash
# Probe (what the SDK does first) — expect 405 now, previously 500
curl -i -X GET "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"

# Real call — expect 200
curl -i -X PUT "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"
```

End-to-end (external java-sdk repo): the `Example99ScheduledAgent` scheduler
lifecycle (deploy → create → list → **pause** → resume → preview → delete)
completes without throwing.

## 5. Commands

```bash
./gradlew :rest:test --tests "com.netflix.conductor.rest.controllers.ApplicationExceptionMapperTest"
./gradlew spotlessApply
```

`spotlessApply` per repo convention after touching Java sources.
