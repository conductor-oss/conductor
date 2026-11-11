---
description: "Upgrading to Conductor 3.34.0: the Spring Boot 4 / Jackson 3 / Jakarta EE 11 framework move and the Elasticsearch 7 to 8 migration."
---

# Upgrading to 3.34.0

3.34.0 is a breaking release along two independent axes:

- **The framework stack moved** to Spring Boot 4.1.1, Jackson 3, and Jakarta EE 11. This affects anyone
  who compiles against `conductor-common` or `conductor-core`, or who extends the server with their own
  Spring beans and modules.
- **Elasticsearch 8 is now the default indexing backend.** The ES7 backend is deprecated and carries the
  unpatchable CVE-2025-37731. Moving to the default is a cluster upgrade, not a config flip, because the
  ES8 typed client cannot talk to a 7.17 server.

Read the section that applies to you. They are independent -- you can be affected by one and not the other.

The 3.33 line remains supported for deployments that need to stay on an ES7-default build. 3.33 is the
last series to default to ES7.


## Who this affects

| You are... | Read |
|---|---|
| Running the Docker image or standalone JAR with the bundled stack | [Elasticsearch](#elasticsearch-7-to-8) only -- the framework move is internal to the image |
| Pointing Conductor at your own Elasticsearch cluster | [Elasticsearch](#elasticsearch-7-to-8) |
| Compiling your code against `conductor-common` / `conductor-core` | [Framework](#framework-spring-boot-4-jackson-3-jakarta-ee-11) |
| Shipping custom Spring modules, tasks, or persistence plugins | [Framework](#framework-spring-boot-4-jackson-3-jakarta-ee-11) |

Conductor 3.34.0 builds and runs on **Java 25**.


## Framework: Spring Boot 4, Jackson 3, Jakarta EE 11

The build moved from the Spring Boot 3.5 BOM to Spring Boot 4.1.1, which brings Spring Framework 7,
Spring Security 7, Tomcat 11, Jakarta EE 11, springdoc 3.1, and Spring AI 2.0. Versions now come from the
Boot BOM unless there is a stated reason to override. The codebase migrated from Jackson 2 to Jackson 3.

### Jackson 2 to 3

This is the change most likely to break downstream code.

**The package moved.** Jackson 3 lives under `tools.jackson.*` (databind, the `JsonMapper` builder, modules).
The annotations remain under `com.fasterxml.jackson.annotation.*` and are shared across Jackson 2 and 3,
so annotated model classes are unaffected. Update your imports for everything that touches the mapper.

**`JacksonException` no longer extends `IOException`.** A `catch (IOException)` that used to catch Jackson
serialization and deserialization failures silently stops catching them. If you wrap Jackson calls in
`catch (IOException)`, add `JacksonException` (or catch it in its place). This surfaced inside Conductor at
`DummyPayloadStorage`, which now accepts `JacksonException` directly.

**`convertValue` failures now throw `JacksonException`, not `IllegalArgumentException`.** In Jackson 2 a
failed `ObjectMapper.convertValue` reported an `IllegalArgumentException`; in Jackson 3 it raises a
`JacksonException`. A `catch (IllegalArgumentException)` around a conversion stops catching. This bit
`StartWorkflow.getRequest` in Conductor -- a malformed `startWorkflow` payload stopped failing the task --
and it is now caught explicitly. Audit your own `catch (IllegalArgumentException)` blocks that wrap a
`convertValue`. Blocks that wrap `Enum.valueOf` or `UUID.fromString` are unaffected.

**Mappers are immutable.** Jackson 3 configures the mapper at build time through `JsonMapper.builder()`;
there is no post-construction `mapper.configure(...)`. Conductor's `ObjectMapperProvider` builds an
immutable mapper via the builder, and the old `ObjectMapperConfiguration` is gone, with its settings moved
into the builder customizer. If you call `ObjectMapperProvider.getObjectMapper()`, the returned mapper is
read-only -- configure your own builder if you need different settings.

**Date encoding is unchanged.** `ObjectMapperProvider` enables `WRITE_DATES_AS_TIMESTAMPS`, so date fields
on the API and in stored task and workflow documents still serialize as epoch millis, exactly as under
Jackson 2. You do not need to migrate stored documents for date shape.

**A couple of method renames you may hit:** `JsonNode.findValuesAsText` is now `findValuesAsString`.

**At the Elasticsearch and OpenSearch boundary, Conductor keeps a Jackson 2 mapper.** The ES and OS Java
clients serialize with Jackson 2, so the transport layer deserializes into Jackson 2 node types at that
boundary only. This is internal to the persistence modules and does not affect the model objects you work
with, which are Jackson 3.

### Spring Boot 3 to 4 / Jakarta EE 10 to 11

If you ship custom Spring beans, modules, or tests against the server, these are the breaking points:

- **Test autoconfiguration relocated.** `@AutoConfigureMockMvc` moved to `spring-boot-webmvc-test`, and
  the `DataSource` and Flyway auto-configuration moved to `spring-boot-jdbc` and `spring-boot-flyway`.
  Update the module these are pulled from.
- **A metrics property was renamed.** `management.metrics.web.server.request.autotime.percentiles` no
  longer exists in Boot 4. Percentiles moved to `management.metrics.distribution.percentiles`, which is
  what restores the quantile series on `/actuator/prometheus`. Update this in your config if you set it.
- **`javax.inject` is gone.** Spring dropped `javax.inject` support in Framework 6.x. `@Inject` and
  `@Singleton` no longer do anything; use the Spring stereotypes and constructor injection.
- **Spring tests no longer open `@Mock` fields for you.** Boot 4 dropped the Mockito test-execution
  listener (along with the deprecated `@MockBean` support) that used to call `MockitoAnnotations.openMocks`
  for `@Mock` fields in tests using `SpringRunner`. Those fields now arrive `null` unless the test opens
  them itself.
- **Groovy 5 honours `private` access.** The Boot 4 BOM brings Groovy 5, which -- unlike Groovy 4 -- will
  not let Groovy (Spock) specs read `private` members of Java classes. Widen access to package-private if a
  same-package spec needs a field.
- **Hibernate Validator 9 cascades `@Valid` into container elements.** Validator 9 validates the elements
  of a `Map` or collection where 8 validated only the container, and logs HV000271 when `@Valid` is applied
  directly to a `Map`. Put `@Valid` on the type argument.
- **Spring AI 2.0 removed `internalToolExecutionEnabled`.** The decision of whether tool calls run inside
  the model call moved to `ToolExecutionEligibilityChecker` and the `ToolCallingManager` on the model
  builders. If you relied on the flag, set a no-op `ToolCallingManager` on the builder instead.

### Removed and replaced dependencies

- **`spring-retry` is gone.** Spring Boot 4 dropped `org.springframework.retry:spring-retry` because the
  functionality moved into Spring Framework 7 under `org.springframework.core.retry`. Conductor uses the
  framework API now. If you build retry logic on the old library, note that `RetryPolicy.builder()`'s
  `maxRetries` counts *retries* where the old `maxAttempts` counted *attempts* -- every budget is one lower
  than the value it replaced for the same number of calls.
- **Afterburner is gone**, folded into Jackson core. Remove any explicit Afterburner module registration.
- **`jackson-jq` is on a pre-release.** `net.thisptr:jackson-jq:2.0.0-alpha1` is the only release built
  against Jackson 3. It is the 1.x engine with the packages moved, and queries compile against the jq 1.6
  dialect, so behaviour is unchanged. This is an accepted risk tracked in
  [#1750](https://github.com/conductor-oss/conductor/issues/1750).
- **protobuf moved to 4.x** and **httpclient5 now comes from the BOM** (the old 5.3.1 pin lacked
  `TlsSocketStrategy`). **json-schema-validator moved to 3.x.**

### The published client SDK stays on Jackson 2

The published `conductor-client` artifact is still a Jackson 2 build. If you drive the server through the
Java SDK, you are unaffected by the Jackson 3 move in your application. Inside this repository the
SDK-driven test-harness suites are excluded for exactly this reason: they link the SDK against the
now-Jackson-3 `conductor-common` and the compiled calls fail to resolve. A conductor-client built on
Jackson 3 is a separate piece of work.

### What to change, in short

1. Repoint Jackson imports from `com.fasterxml.jackson` to `tools.jackson` (annotations stay where they are).
2. Add `JacksonException` anywhere a `catch (IOException)` or `catch (IllegalArgumentException)` used to
   catch a Jackson failure.
3. Configure your own `JsonMapper.builder()` if you were mutating the shared mapper.
4. Replace `javax.inject` annotations and any `spring-retry` usage.
5. Rename the metrics percentiles property if you set it.
6. Target Java 25.


## Elasticsearch 7 to 8

3.34.0 defaults to **Elasticsearch 8**. The default `docker-compose.yaml` runs ES 8.19.x, and the default
server image is built with the ES8 persistence module.

### Why the default moved

The ES7 backend shades `org.elasticsearch:elasticsearch:7.17.x`, which carries **CVE-2025-37731** (an
improper-authentication flaw in the PKI realm allowing user impersonation). There is no 7.17.x fix -- the
CVE is fixed in 8.19.8 and later, and the 7.x line is EOL for it. The only way to remove the vulnerable jar
from the shipped image is to stop building the ES7 backend by default. The ES8 backend does not pull the
core jar at all; it uses the modern `co.elastic.clients` typed client.

### This is a cluster upgrade, not a config flip

**The ES8 typed client cannot talk to an ES 7.17 server.** This was confirmed by running the ES8 module's
integration suite against both server versions: clean against 8.19, and every server-touching test fails
against 7.17. The client dies at the first `cluster.health` call, because the 8.x typed deserializer
hard-requires the `unassignedPrimaryShards` field, which a 7.17 server never returns. A REST compatibility
header cannot add a field that is genuinely absent from the 7.x response, so there is no graceful
degradation. An ES8 build requires an ES 8.x cluster.

### Migration path

1. **Stand up or upgrade to Elasticsearch 8.19.x** (8.19.8 or later clears the CVE). Follow Elastic's
   rolling-upgrade procedure; ES 7.17 is the designated bridge release to 8.x.
2. **Reindex if your upgrade path requires it.** A 7.x-created index that an 8.x server refuses to read
   must be reindexed per Elastic's guidance. Conductor creates its indices on startup, so for a fresh
   index you need nothing beyond pointing it at the new cluster.
3. **Run the default (ES8) Conductor build and point it at the upgraded cluster.** Set the runtime config:

    ```properties
    conductor.indexing.type=elasticsearch8
    conductor.elasticsearch.url=http://your-es8-host:9200
    conductor.elasticsearch.version=8
    conductor.elasticsearch.indexName=conductor
    conductor.elasticsearch.clusterHealthColor=yellow
    ```

See the indexing section of [Production Deployment](deploy.md#indexing) for the full property set.

### Staying on ES7 as a stopgap

ES7 is retained as an opt-in. Build the server with:

```shell
./gradlew build -PindexingBackend=elasticsearch7
```

and set `conductor.elasticsearch.version=7` at runtime. The Docker image supports the same via the
`INDEXING_BACKEND=elasticsearch7` build arg.

This is a stopgap, not a destination. An ES7 build still ships the `elasticsearch:7.17.x` core jar, so
**CVE-2025-37731 is present in it** with no upstream fix available. The server logs a deprecation warning
at startup when the ES7 backend is active. Plan the cluster move before adopting a release that removes
ES7 entirely.

If you are not ready to leave ES7 at all, staying on the **3.33 line** -- the last series to default to
ES7 -- is the supported option.
