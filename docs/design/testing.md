# Testing — Fix #1393: method mismatch returns `405`

Verification plan for the change described in [`architecture.md`](architecture.md) and
[`error-mapping.md`](error-mapping.md). Reuses those names and types verbatim.

## 1. Unit test (primary)

Extend the existing
`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`.
That test already builds a standalone `MockMvc` over a mocked `QueueAdminResource` with
`ApplicationExceptionMapper` registered as controller advice:

```java
this.mockMvc =
        MockMvcBuilders.standaloneSetup(this.queueAdminResource)
                .setControllerAdvice(new ApplicationExceptionMapper())
                .build();
```

`QueueAdminResource.update(...)` is mapped to
`POST /api/queue/update/{workflowId}/{taskRefName}/{status}`. Sending a **`GET`** to that
same path makes Spring MVC raise the real
`HttpRequestMethodNotSupportedException` — no mocking of the exception is needed, which
matches the repo's "avoid mocks / test actual behavior" guidance.

### Assertions

Add one test that proves the new mapping and the WARN-not-ERROR logging that follows from
it (`405` is a `4xx`, so `logException` logs at `WARN`):

| Assertion | Expected |
|---|---|
| HTTP status | `405 Method Not Allowed` (`status().isMethodNotAllowed()`) |
| Logged level | `WARN` (reuse the `assertLoggedAtWarn` style) |
| `error(...)` calls | none (`verify(logger, never()).error(...)`) |

Sketch, following the file's existing patterns:

```java
@Test
public void testMethodNotAllowedMappedTo405AndLoggedAtWarn() throws Exception {
    clearInvocations(logger);
    // GET a POST-only route -> Spring MVC raises HttpRequestMethodNotSupportedException.
    this.mockMvc
            .perform(
                    MockMvcRequestBuilders.get(
                            "/api/queue/update/workflowId/taskRefName/{status}",
                            TaskModel.Status.SKIPPED))
            .andExpect(status().isMethodNotAllowed());
    verify(logger)
            .warn(
                    eq("Error {} url: '{}'"),
                    eq("HttpRequestMethodNotSupportedException"),
                    eq("/api/queue/update/workflowId/taskRefName/SKIPPED"),
                    any(Throwable.class));
    verify(logger, never()).error(any(), any(), any(), any());
}
```

The `message` and `retryable=false` fields of the returned `ErrorResponse` follow the
contract in [`architecture.md`](architecture.md) §4.2 and need no separate change to
assert; the status and log-level assertions are what regressed.

## 2. Regression guard

The existing tests (`testException`, `testClientErrorsLoggedAtWarn`) must still pass,
proving that:

- unmapped `Throwable` still maps to `5xx` and logs at `ERROR`;
- previously mapped `4xx` types (`ConflictException` → `409`, `NotFoundException` → `404`)
  are unchanged.

This confirms the one-line map addition does not perturb any other status.

## 3. End-to-end confirmation (manual, from the issue)

The issue's reproduction exercises the full path through the Java SDK. It cannot run in
this repo's CI (the SDK and `agent-examples` live in `java-sdk`), so record it in the PR
description as the acceptance check:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api \
       CONDUCTOR_AGENT_LLM_MODEL=anthropic/claude-sonnet-4-6 \
       AGENT_SECONDARY_LLM_MODEL=anthropic/claude-sonnet-4-6
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
```

Expected after the fix: the GET probe of `/api/scheduler/schedules/{name}/pause` returns
`405`, the SDK's `executeGetThenPutOnMethodNotAllowed` falls through to `PUT`, and the
lifecycle completes: deploy → create → list → **pause** → resume → preview → delete.

<!-- TODO: verify against live server — requires the java-sdk repo and a running
     3.32.x server, which are not available in this repo's CI. -->

## 4. Commands

```bash
./gradlew spotlessApply
./gradlew :rest:test
```

`:rest:test` runs `ApplicationExceptionMapperTest`, which is where the new coverage and
the regression guards live.
