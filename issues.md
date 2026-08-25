# Spring Boot 4 upgrade: issues and decisions

Problems found while executing `SPRING4-Upgrade.md`, the decision taken for each, and any follow-up
work that was deliberately left out of the upgrade.

## 1. spring-retry is no longer managed by the Spring Boot BOM

Spring Boot 4 removed `org.springframework.retry:spring-retry` from its dependency management.
Framework 7 grew its own retry support under `org.springframework.core.retry`, so Boot no longer
ships the standalone library's version.

Conductor uses `RetryTemplate`, `SimpleRetryPolicy` and the backoff policies in 77 places across
core, every SQL persistence module, the scheduler modules, and the ES/OS index DAOs.

Decision: pin `spring-retry` to 2.0.13 in `dependencies.gradle` and version the declarations. The
library only needs `spring-context`, which it declares as optional, so it links against Framework 7
without conflict.

Follow-up: migrating to `org.springframework.core.retry` is a genuine API change. The core API models
retry policies and backoff differently and has no direct equivalent for the `RetryContext` callbacks
Conductor uses in the SQL configurations. Worth doing on its own, not inside this upgrade.

## 2. Jackson 3 conversion failures are no longer IllegalArgumentException

`ObjectMapper.convertValue` reported a failed conversion as `IllegalArgumentException` in Jackson 2.
Jackson 3 raises `JacksonException` (a `MismatchedInputException` for this case), so a
`catch (IllegalArgumentException)` around a conversion silently stops catching.

Found in `StartWorkflow.getRequest`, where a malformed `startWorkflow` payload would have escaped
instead of failing the task. `StartWorkflowSpec` caught it. The catch now handles both.

The other `catch (IllegalArgumentException)` sites in the codebase wrap `Enum.valueOf` or
`UUID.fromString`, not Jackson, and are unaffected.

## 3. Spring Boot 4 no longer opens plain @Mock fields in Spring tests

Spring Boot 3 shipped a Mockito test-execution listener that called
`MockitoAnnotations.openMocks` for any `@Mock` field in a test using `SpringRunner`. Boot 4 dropped
it along with the deprecated `@MockBean` support, so those fields arrive null.

Six tests relied on it (core, redis-lock, grpc-client). Each now opens its mocks explicitly in
setup; no assertion or stub behaviour changed.

## 4. Groovy 5 honours private access

Groovy 4 let Groovy code read `private` members of Java classes. Groovy 5, which arrives with the
Boot 4 BOM, does not.

`StartWorkflowSpec` statically imports `StartWorkflow.START_WORKFLOW_PARAMETER`. The constant is now
package-private, which the spec is entitled to since it sits in the same package. Duplicating the
literal in the spec was the alternative and would have let the two drift.

## 5. jackson-jq is on a pre-release

`net.thisptr:jackson-jq:2.0.0-alpha1` is the only release built against Jackson 3. It is the same
engine as the 1.x line with the packages moved, and the JSON_JQ_TRANSFORM tests pass against it, but
it is an alpha. Queries compile against the jq 1.6 dialect, matching the behaviour of the previous
release. Worth revisiting when a stable 2.x appears.

## 6. Spring AI 2.0 dropped internalToolExecutionEnabled

Conductor set `internalToolExecutionEnabled(false)` so tool calls are surfaced to the workflow
rather than executed inside the model call. Spring AI 2.0 removed the flag from the options API and
moved the decision to `ToolExecutionEligibilityChecker` and the `ToolCallingManager` on the model
builders. The `toolNames` setter went the same way; it was only ever populated from the tool
callbacks that are still passed.

The calls are removed. Providers built on Conductor's own ChatModel classes are unaffected because
they never auto-executed. For the Spring-supplied models (mistral, ollama, bedrock) the behaviour is
covered by `LLMHelperChatCompleteTest`, which asserts tool calls are surfaced. If a future Spring AI
release changes that default, the fix is a no-op `ToolCallingManager` on those builders.
