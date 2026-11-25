# Spring Boot 4 upgrade notes

Constraints and behaviour changes that came out of the Spring Boot 4 / Jackson 3 upgrade, kept
because they are not obvious from the code alone.

## spring-retry replaced by the retry support in spring-core

Spring Boot 4 removed `org.springframework.retry:spring-retry` from its dependency management,
because Framework 7 grew its own retry support under `org.springframework.core.retry`. Conductor
uses the framework API; the library is gone from every build file.

Points worth knowing when touching retry code:

- `maxRetries` counts retries where `maxAttempts` counted attempts, so every budget is one lower
  than the value it replaced.
- The three SQL deadlock policies subclassed `SimpleRetryPolicy` to override `canRetry(RetryContext)`
  and only ever read the last throwable from that context, which is what
  `predicate(Predicate<Throwable>)` does. They are plain predicates now.
- `execute` throws a checked `RetryException` that wraps the cause. Where a callback only throws
  unchecked exceptions the code calls `invoke` instead, which rethrows the original cause once
  retries are exhausted. `DefaultEventProcessor` depends on this: its redelivery decision tests for
  `TransientException`, and the SQL DAOs rely on the `SQLException` still being the cause of the
  `NonTransientException` they raise.
- The two Elasticsearch `executeWithRetry` helpers unwrap `RetryException` before testing the
  exception type; without that every failure collapses into a generic `IOException`.

Backoff mapping: `NoBackOffPolicy` to `delay(Duration.ZERO)`, `FixedBackOffPolicy(1000ms)` to
`delay(Duration.ofSeconds(1))`, sqlite's exponential policy to
`delay(50ms).multiplier(2.0).maxDelay(5s)`.

## Jackson 3 conversion failures are no longer IllegalArgumentException

`ObjectMapper.convertValue` reported a failed conversion as `IllegalArgumentException` in Jackson 2.
Jackson 3 raises `JacksonException`, so a `catch (IllegalArgumentException)` around a conversion
silently stops catching. `StartWorkflow.getRequest` was affected: a malformed `startWorkflow` payload
would have escaped instead of failing the task.

The remaining `catch (IllegalArgumentException)` sites wrap `Enum.valueOf` or `UUID.fromString` and
are unaffected.

## Jackson 3 writes dates as ISO strings by default

`ObjectMapperProvider` enables `WRITE_DATES_AS_TIMESTAMPS`. Jackson 2 produced epoch millis through
`JavaTimeModule`; without the setting every date field on the API and in stored task and workflow
documents would change shape.

## Spring Boot 4 no longer opens plain @Mock fields in Spring tests

Boot 3 shipped a Mockito test-execution listener that called `MockitoAnnotations.openMocks` for
`@Mock` fields in tests using `SpringRunner`. Boot 4 dropped it along with the deprecated
`@MockBean` support, so those fields arrive null unless the test opens them itself.

## Groovy 5 honours private access

Groovy 4 let Groovy code read `private` members of Java classes; Groovy 5, which arrives with the
Boot 4 BOM, does not. `StartWorkflow.START_WORKFLOW_PARAMETER` is package-private so the spec in the
same package can still import it.

## jackson-jq is on a pre-release

`net.thisptr:jackson-jq:2.0.0-alpha1` is the only release built against Jackson 3. It is the 1.x
engine with the packages moved. Queries compile against the jq 1.6 dialect, matching the behaviour of
the previous release. Worth revisiting when a stable 2.x appears.

## Spring AI 2.0 dropped internalToolExecutionEnabled

Conductor set `internalToolExecutionEnabled(false)` so tool calls are surfaced to the workflow rather
than executed inside the model call. Spring AI 2.0 removed the flag and moved the decision to
`ToolExecutionEligibilityChecker` and the `ToolCallingManager` on the model builders. The `toolNames`
setter went the same way; it was only ever populated from the tool callbacks that are still passed.

Providers built on Conductor's own ChatModel classes never auto-executed. For the Spring-supplied
models the behaviour is covered by `LLMHelperChatCompleteTest`, which asserts tool calls are
surfaced. If a future Spring AI release changes that default, the fix is a no-op
`ToolCallingManager` on those builders.

## The SDK-driven test-harness suites are excluded

The published `conductor-client` is a Jackson 2 artifact. The suites that drive it hand
conductor-common model objects to the SDK, so the SDK links against this project's conductor-common,
which is now Jackson 3, and its compiled calls to `ObjectMapperProvider.getObjectMapper()` fail to
resolve. Relocating the SDK's bundled copy is not an option precisely because the model types are
shared. The suites stay excluded until a conductor-client built on Jackson 3 is published.

## redis-concurrency-limit cannot be enabled

Two problems, both older than this upgrade.

`spring-data-redis` was declared `compileOnly` in the module and never shipped by the server, so
enabling the feature failed at startup with `NoClassDefFoundError` on `RedisConnectionFactory`. The
server now declares it `runtimeOnly`, which makes the feature loadable.

With that fixed, startup fails on an ambiguous `ConcurrentExecutionLimitDAO`: the module's DAO and
the ExecutionDAO of whichever backend is configured both implement the interface, and neither is
primary. It happens with sqlite as well as redis, so no bundled backend can use the module as
shipped. Fixing it needs a decision about which bean should win.

## Hibernate Validator warns about @Valid on a Map

Hibernate Validator 9 logs HV000271 for `@Valid` applied to a `Map` container, pointing at
`onStateChange`. Validation still runs; the annotation belongs on the type argument.
