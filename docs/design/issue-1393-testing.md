# Testing — Fix 500 on unsupported HTTP method (Issue #1393)

Reuses names/types from [issue-1393-architecture.md](./issue-1393-architecture.md).
The fix is a one-line mapping addition, so testing is likewise focused: prove that an
unsupported HTTP method now produces `405`, is logged at `WARN` (a 4xx, not a server
fault), and that no previously-passing behavior regresses.

## 1. Test location and harness

Extend the existing test, which already stands up the mapper against a `standaloneSetup`
`MockMvc`:

`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`

Existing harness facts to reuse (do not re-invent):

- `MockMvcBuilders.standaloneSetup(this.queueAdminResource).setControllerAdvice(new ApplicationExceptionMapper()).build()`
  wires only `QueueAdminResource` plus the mapper under test.
- `LoggerFactory` is a static mock; `logger` is the shared `Logger` mock. Each assertion
  calls `clearInvocations(logger)` first so verifications are order-independent.
- Existing helper `assertLoggedAtWarn(RuntimeException, ResultMatcher)` proves 4xx mapping
  + `WARN` logging + `never().error(...)` for exception types thrown from the handler body.

## 2. New test case

`HttpRequestMethodNotSupportedException` is raised by the framework's request dispatch,
not thrown from a handler method — so the existing `doThrow(...).when(update(...))` pattern
does not apply. Instead, drive a request with a method the mapped path does not support.

`QueueAdminResource.update` is mapped to `POST /api/queue/update/...`. Issuing a `GET`
against that same path yields `HttpRequestMethodNotSupportedException`, which the mapper
must now translate to `405`.

### `testMethodNotAllowedMappedTo405`

Assertions:

1. **Status** — response is `405 Method Not Allowed`
   (`status().isMethodNotAllowed()`).
2. **Logged at WARN, not ERROR** — the message-not-supported condition is a 4xx, so verify
   `logger.warn("Error {} url: '{}'", "HttpRequestMethodNotSupportedException", "<uri>", <ex>)`
   and `verify(logger, never()).error(any(), any(), any(), any())`.

Sketch (aligned to existing style; `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;`
is already present):

```java
@Test
public void testMethodNotAllowedMappedTo405() throws Exception {
    // GET on a path that is only mapped for POST triggers Spring MVC's
    // HttpRequestMethodNotSupportedException. The mapper must translate that
    // framework-level method mismatch to 405, not 500, so the Java SDK's
    // GET-then-PUT-on-405 fallback (issue #1393) can proceed to the PUT.
    this.mockMvc
            .perform(
                    MockMvcRequestBuilders.get(
                            "/api/queue/update/workflowId/taskRefName/{status}",
                            TaskModel.Status.SKIPPED))
            .andDo(print())
            .andExpect(status().isMethodNotAllowed());

    // A wrong-method request is a client (4xx) condition: WARN, never ERROR.
    verify(logger).warn(eq("Error {} url: '{}'"), any(), any(), any());
    verify(logger, never()).error(any(), any(), any(), any());
}
```

> Note: the exact `warn(...)` argument matchers may need to accommodate how Spring's
> standalone `MockMvc` surfaces the dispatch exception through the advice. If the
> standalone setup does not route the framework exception through the advice, promote this
> to a slice test (see §3).

## 3. Fallback / integration option

If `standaloneSetup` does not route `HttpRequestMethodNotSupportedException` through the
advice the same way the full `DispatcherServlet` does, cover the contract with a
`@WebMvcTest` slice (or a small full-context test) that:

1. Registers a controller with a single `PUT`-only endpoint (mirroring `pauseSchedule`).
2. Performs a `GET` on that path.
3. Asserts `405` and the `ErrorResponse` body: `status == 405`, `retryable == false`,
   `message` contains `is not supported`.

This mirrors real dispatch more faithfully than the standalone unit setup.

## 4. Regression guard (existing tests must still pass)

- `testException` — an unmapped `Exception` thrown from a handler still maps to `5xx` and
  is logged at `ERROR`. The new map entry must not affect the default-500 fallback.
- `testClientErrorsLoggedAtWarn` — `ConflictException → 409`, `NotFoundException → 404`,
  logged at `WARN`. Unchanged.

## 5. Commands

```bash
./gradlew :rest:test --tests \
  com.netflix.conductor.rest.controllers.ApplicationExceptionMapperTest
./gradlew spotlessApply
```

## 6. End-to-end acceptance (manual, per the issue repro)

Against a server carrying the fix, the Java SDK scheduler lifecycle must complete:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api \
       CONDUCTOR_AGENT_LLM_MODEL=anthropic/claude-sonnet-4-6 \
       AGENT_SECONDARY_LLM_MODEL=anthropic/claude-sonnet-4-6
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
```

Pass criterion: execution proceeds past `pauseSchedule(...)` through resume → preview →
delete with no `Request method 'GET' is not supported` exception.

<!-- TODO: verify against live server — end-to-end acceptance requires a running server and
     the external java-sdk repo, neither exercised here. -->
