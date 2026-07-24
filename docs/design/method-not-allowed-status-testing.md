# Testing — 405 for unsupported HTTP methods (Issue #1393)

Supporting doc for `method-not-allowed-status-architecture.md`. Reuses its names,
types, and file paths verbatim.

## Unit test (primary)

File: `rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`

This existing test class already exercises `ApplicationExceptionMapper` through a
Spring `MockMvc` standalone setup wired with `.setControllerAdvice(new
ApplicationExceptionMapper())`. Add one test that sends a request with a method the
target handler does not support and asserts the mapped status and logging level.

Because `HttpRequestMethodNotSupportedException` is raised by Spring's dispatcher
when no handler method matches the request method, the test should register a
controller with a known mapping (e.g. a `@GetMapping`) and then call it with a
different verb (e.g. `POST`) so the mismatch triggers the exception the advice maps.

Assertions:

- Status is `405` (`status().isMethodNotAllowed()`) — **not** `500`.
- Response body `status` field equals `405` and `retryable` is `false`
  (matching the `ErrorResponse` contract in the architecture doc).
- The exception is logged at **WARN**, not ERROR — verify with the class's existing
  mocked `logger`: `verify(logger, never()).error(anyString(), any(), any(), any())`
  and a corresponding `warn(...)` expectation, mirroring how the existing 4xx tests
  in this class assert logging behavior.

Keep the existing tests (`NotFoundException` → 404, `ConflictException` → 409, etc.)
untouched; only append the new case.

## Regression / integration coverage

File: `test-harness/src/test/java/com/netflix/conductor/test/integration/http/SchedulerIntegrationTest.java`

The scheduler integration test already drives the real HTTP surface. The pause path
is what surfaced this bug, so confirm this suite covers the full lifecycle
(create → list → pause → resume → delete) against a running server. If the pause
assertion is not present, add it so a future regression of the status-code mapping
would fail here as well. No change to the mapping code is validated by this file
directly, but it protects the end-to-end behavior the SDK depends on.

## Manual verification

With a local server (`http://localhost:8080/api`):

```bash
# Wrong method now yields a clean 405 (was 500 before the fix)
curl -i -X GET "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"
# -> HTTP/1.1 405 Method Not Allowed

# Correct method still works
curl -i -X PUT "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause?reason=maintenance"
# -> HTTP/1.1 200 OK
```

End-to-end, the reported repro should now complete the full lifecycle:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
# deploy -> create 2 schedules -> list -> pause -> resume -> preview -> delete
```

## Commands

```bash
./gradlew :rest:test --tests \
  com.netflix.conductor.rest.controllers.ApplicationExceptionMapperTest
./gradlew spotlessApply
```

`spotlessApply` must be run after the code edit, per repo conventions.
