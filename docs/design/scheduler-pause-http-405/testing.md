# Testing — Issue #1393

Reuses names and types from [`architecture.md`](./architecture.md).

## Scope

The fix touches one production file (`ApplicationExceptionMapper`). Tests live in the existing
`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`
and extend the current JUnit 4 + MockMvc standalone setup — no new test class, no mocks of the
class under test (the mapper is exercised as a real `@RestControllerAdvice`).

## Existing test harness (reuse verbatim)

`ApplicationExceptionMapperTest` already:

- builds `MockMvcBuilders.standaloneSetup(this.queueAdminResource).setControllerAdvice(new ApplicationExceptionMapper()).build();`
- mocks `LoggerFactory` so log level (`warn` vs `error`) can be verified;
- drives requests through `QueueAdminResource` (`/api/queue/update/...`).

New tests reuse this harness. Because 405/415 are raised by Spring MVC dispatch itself (not by
throwing from a controller method), the new tests trigger them by calling the existing mapped
route with the **wrong verb** and the **wrong content type**.

## New test cases

### 1. Wrong HTTP method resolves to 405 and logs at WARN

`POST /api/queue/update/...` exists; issuing a `GET` (or `DELETE`) against it raises
`HttpRequestMethodNotSupportedException`.

- Perform: `MockMvcRequestBuilders.get("/api/queue/update/workflowId/taskRefName/{status}", TaskModel.Status.SKIPPED)`
- Expect: `status().isMethodNotAllowed()` (405)
- Assert: logged via `logger.warn(...)` (4xx path), never `logger.error(...)` — mirroring
  `assertLoggedAtWarn`.

This is the direct regression guard for the issue: prior to the fix this returned
`status().is5xxServerError()`.

### 2. Unsupported media type resolves to 415

`POST /api/queue/update/...` with an unsupported `Content-Type` raises
`HttpMediaTypeNotSupportedException`.

- Perform: the existing `POST` but with `.contentType(MediaType.TEXT_PLAIN)` and a text body.
- Expect: `status().isUnsupportedMediaType()` (415)
- Assert: logged at `warn` (4xx), never `error`.

### 3. Regression — generic exceptions still 500

Keep the existing `testException()` unchanged: an unmapped `Exception` still yields
`status().is5xxServerError()` and is logged at `error`. This proves the catch-all default was
not weakened.

## Response-body assertions

For at least the 405 case, additionally assert the `ErrorResponse` fields to lock the contract
from [`api.md`](./api.md):

- `jsonPath("$.status").value(405)`
- `jsonPath("$.retryable").value(false)`
- `jsonPath("$.message")` contains `is not supported`

## Commands

```bash
./gradlew :rest:test --tests "com.netflix.conductor.rest.controllers.ApplicationExceptionMapperTest"
./gradlew spotlessApply
```

## Manual verification (matches the issue repro)

Against a local server with the scheduler module active:

```bash
# 1. wrong verb now returns 405 (was 500)
curl -i -X GET http://localhost:8080/api/scheduler/schedules/anything/pause
# -> HTTP/1.1 405 Method Not Allowed

# 2. the real call still works
curl -i -X PUT http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause
# -> HTTP/1.1 200 OK
```

End-to-end, the Java SDK repro completes the full lifecycle:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
# deploy -> create -> list -> pause -> resume -> preview -> delete  (no exception)
```

<!-- TODO: verify against live server — the manual curl/SDK steps require a running
     3.32.x server and are documented from the issue repro, not executed in this change. -->
