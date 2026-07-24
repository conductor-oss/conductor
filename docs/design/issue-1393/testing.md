# Testing — Method-not-supported status (Issue #1393)

Reuses names/types from [`architecture.md`](./architecture.md) verbatim.

## Goal

Prove that `org.springframework.web.HttpRequestMethodNotSupportedException`
maps to HTTP `405` (not `500`) through `ApplicationExceptionMapper`, and that
the `ErrorResponse` body is populated as specified in `architecture.md` §4.3.

## Unit test (primary)

Extend
`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`.

Follow the existing pattern in that file: construct the advice, invoke
`handleAll(request, throwable)` directly, and assert on the returned
`ResponseEntity<ErrorResponse>`.

New case:

```java
@Test
public void testHandleHttpRequestMethodNotSupported() {
    HttpRequestMethodNotSupportedException ex =
            new HttpRequestMethodNotSupportedException("GET");

    ResponseEntity<ErrorResponse> response =
            applicationExceptionMapper.handleAll(request, ex);

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), response.getStatusCode().value());
    assertEquals(405, response.getBody().getStatus());
    assertFalse(response.getBody().isRetryable());
    // message comes straight from the exception, e.g. "Request method 'GET' is not supported"
    assertNotNull(response.getBody().getMessage());
}
```

Reuse whatever `HttpServletRequest request` mock/stub the test class already
sets up (e.g. `getRequestURI()` returning a path). Do not introduce new mocking
utilities beyond what the file already uses.

## Regression guard

Add (or confirm) a case asserting an **unmapped** exception still yields `500`,
so the new entry does not accidentally broaden the mapping:

```java
@Test
public void testUnmappedExceptionStillMapsTo500() {
    ResponseEntity<ErrorResponse> response =
            applicationExceptionMapper.handleAll(request, new RuntimeException("boom"));
    assertEquals(500, response.getBody().getStatus());
}
```

## Integration verification (optional, manual)

Against a running server (`http://localhost:8080/api`) with the scheduler
module enabled:

```bash
# Wrong method on the pause endpoint now returns 405, not 500
curl -i -X GET http://localhost:8080/api/scheduler/schedules/does-not-matter/pause
# => HTTP/1.1 405 Method Not Allowed
```

End-to-end, the reported repro should complete without throwing:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
./gradlew :agent-examples:run \
  -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
```

Expected: deploy → create 2 schedules → list → **pause** → resume → preview →
delete all succeed.

<!-- TODO: verify against live server — no running server/SDK available in this environment -->

## Commands

```bash
./gradlew :rest:test
./gradlew spotlessApply
```

`spotlessApply` must be run after the edit to keep formatting consistent
(AGENTS.md).

## Out of scope

- No SDK tests are added here; the SDK is a separate repository. The server
  test above is sufficient to prove the fix, and the SDK's existing
  GET-then-PUT fallback consumes the corrected `405` unchanged.
