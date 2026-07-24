# Testing plan (issue #1393)

Verifies the fix in `architecture.md`: an unsupported HTTP method returns `405 Method Not Allowed`
(not `500`) and is logged at `WARN`. Uses the exact names/types declared in `architecture.md`.

## Existing test harness

`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`
already sets up a standalone `MockMvc` around `QueueAdminResource` with
`ApplicationExceptionMapper` as the sole controller advice, and mocks `LoggerFactory` so log level
can be asserted. The new case reuses this harness — no new test fixtures or files.

Relevant existing structure to reuse verbatim:

- Field: `private MockMvc mockMvc;` wired in `@Before before()` via
  `MockMvcBuilders.standaloneSetup(this.queueAdminResource).setControllerAdvice(new ApplicationExceptionMapper()).build()`.
- Static logger mock: `private static final Logger logger = mock(Logger.class);`, with
  `clearInvocations(logger)` at the start of each case.
- The `QueueAdminResource` update route used by the other tests:
  `POST /api/queue/update/workflowId/taskRefName/{status}`.

## New test case

Add to `ApplicationExceptionMapperTest`:

```java
@Test
public void testUnsupportedMethodReturns405() throws Exception {
    // logger is a static mock reused across tests; start clean.
    clearInvocations(logger);

    // The update route is mapped for POST only. Issuing GET to it makes Spring
    // raise HttpRequestMethodNotSupportedException, the same exception the SDK's
    // GET-then-PUT scheduler fallback probes for. It must map to 405, not 500.
    this.mockMvc
            .perform(
                    MockMvcRequestBuilders.get(
                            "/api/queue/update/workflowId/taskRefName/{status}",
                            TaskModel.Status.SKIPPED))
            .andDo(print())
            .andExpect(status().isMethodNotAllowed());

    // 405 is a 4xx client error, so it is logged at WARN, never ERROR.
    verify(logger)
            .warn(
                    eq("Error {} url: '{}'"),
                    eq("HttpRequestMethodNotSupportedException"),
                    eq("/api/queue/update/workflowId/taskRefName/SKIPPED"),
                    any(HttpRequestMethodNotSupportedException.class));
    verify(logger, never()).error(any(), any(), any(), any());
    verifyNoMoreInteractions(logger);
}
```

Notes:

- `status().isMethodNotAllowed()` asserts `405`. Before the fix this route would produce `500`
  through the catch-all handler, so the assertion also guards against regression.
- The message parameter is asserted by type (`any(HttpRequestMethodNotSupportedException.class)`)
  rather than by exact string, because the exact wording of Spring's message can vary across Spring
  versions; the class and the `WARN` level are the stable contract.
- Requires the import `org.springframework.web.HttpRequestMethodNotSupportedException` in the test.

## Regression coverage retained

The existing tests continue to assert:

- `testException` — an unmapped generic `Exception` still yields `5xx` and is logged at `ERROR`.
- `testClientErrorsLoggedAtWarn` — `ConflictException -> 409`, `NotFoundException -> 404` still map
  correctly and log at `WARN`.

The new mapping does not affect these, because it adds a single, specific class key to
`EXCEPTION_STATUS_MAP`; the `getOrDefault(..., INTERNAL_SERVER_ERROR)` fallback for truly unmapped
throwables is unchanged.

## Manual / end-to-end verification

Reproduce the original issue scenario from #1393 against a running server:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
```

Expected after the fix: the lifecycle completes end to end —
deploy → create 2 schedules → list → **pause** → resume → preview → delete — with no
`Request method 'GET' is not supported` exception.

Direct HTTP check (no SDK required):

```bash
# unsupported method now returns 405, not 500
curl -s -o /dev/null -w "%{http_code}\n" \
  -X GET "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"   # -> 405

# correct method still works
curl -s -o /dev/null -w "%{http_code}\n" \
  -X PUT "http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause"   # -> 200
```

## Commands

- Module unit tests: `./gradlew :rest:test`
- Formatting (required before commit): `./gradlew spotlessApply`
