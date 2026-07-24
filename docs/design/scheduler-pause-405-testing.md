# Testing plan — 405 on wrong verb (#1393)

Supporting doc for
[`scheduler-pause-405-architecture.md`](./scheduler-pause-405-architecture.md). Names, types,
and statuses match that document verbatim.

## Guiding principle (per AGENTS.md)

Tests verify real behavior with real implementations — no mocking the class under test. The
primary unit lives in `ApplicationExceptionMapper`, so the primary test extends the existing
`ApplicationExceptionMapperTest`, which already exercises `handleAll(...)` directly and asserts
on the returned `ResponseEntity`.

## Test 1 — unit: mapper returns 405 for method-not-supported (required)

File: `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`

Add one test that drives the real `handleAll(...)` with a real
`HttpRequestMethodNotSupportedException` and asserts the mapping.

Behavior to assert:

- Given `new HttpRequestMethodNotSupportedException("GET")` (Spring builds the message
  `Request method 'GET' is not supported`),
- When passed to `ApplicationExceptionMapper.handleAll(request, ex)`,
- Then:
  - `response.getStatusCode()` equals `HttpStatus.METHOD_NOT_ALLOWED` (`405`);
  - `response.getBody().getStatus()` equals `405`;
  - `response.getBody().isRetryable()` is `false`;
  - `response.getBody().getMessage()` equals the exception message.

Sketch (follow the existing test's style for constructing the request and mapper):

```java
@Test
public void testMethodNotSupportedMapsTo405() {
    HttpRequestMethodNotSupportedException ex =
            new HttpRequestMethodNotSupportedException("GET");

    ResponseEntity<ErrorResponse> response =
            applicationExceptionMapper.handleAll(request, ex);

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    assertEquals(405, response.getBody().getStatus());
    assertFalse(response.getBody().isRetryable());
    assertEquals(ex.getMessage(), response.getBody().getMessage());
}
```

Regression guard: without the production change this test fails with an observed status of
`500`, pinning the exact defect from the issue.

## Test 2 — unit: unmapped exceptions still default to 500 (guard against over-broadening)

Confirm the fix is surgical: any `Throwable` not in `EXCEPTION_STATUS_MAP` still yields `500`.
If `ApplicationExceptionMapperTest` already covers a generic `RuntimeException → 500` case,
no new test is needed; otherwise add one asserting
`handleAll(request, new RuntimeException("boom")).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR`.

## Test 3 — integration (optional): GET to a PUT-only scheduler route returns 405

File: `test-harness/src/test/java/com/netflix/conductor/test/integration/http/SchedulerIntegrationTest.java`

With the scheduler wired into the running test server, issue a `GET` against a `PUT`-only
route and assert the HTTP status is `405`, not `500`:

- `GET /api/scheduler/schedules/{name}/pause` → expect `405`.
- `PUT /api/scheduler/schedules/{name}/pause` (against an existing schedule) → expect `200`.

This exercises the full Spring MVC dispatch path (real `DispatcherServlet` raising
`HttpRequestMethodNotSupportedException`), proving the advice intercepts the framework
exception in production wiring — not just when invoked directly in a unit test.

## What is deliberately NOT tested here

- The Java SDK `executeGetThenPutOnMethodNotAllowed(...)` fallback lives in the separate
  `conductor-oss/java-sdk` repository and is out of scope for this repo's test suite.
- No new tests for `SchedulerResource` verb routing — those mappings are unchanged.

## Commands

Per AGENTS.md, run formatting and the affected module tests before submitting:

```bash
./gradlew spotlessApply
./gradlew :rest:test
# optional, if Test 3 is added:
./gradlew :test-harness:test
```

## Traceability

| Requirement (issue #1393) | Covered by |
|---|---|
| Wrong-verb GET on pause returns a clean 405, not 500 | Test 1 (unit), Test 3 (integration) |
| Fix does not broaden other exception → 500 mappings | Test 2 |
| `pauseSchedule` succeeds end-to-end (GET→405→PUT→200) | Test 3 + Java SDK fallback (external) |
