# AGENTS.md

Instructions for AI coding agents working on the Conductor codebase.

## Project Overview

Conductor is an open-source, distributed workflow orchestration engine designed for microservices.
It uses a pluggable architecture with interface-based abstractions for persistence, queuing, and indexing.
The project is built with Java 21 and uses Gradle as the build system.

## Setup Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build the entire project |
| `./gradlew test` | Run all tests |
| `./gradlew :module-name:test` | Run tests for a specific module |
| `./gradlew spotlessApply` | Apply code formatting |
| `./gradlew clean build` | Clean and rebuild |

> **Important**: Always run `./gradlew spotlessApply` after making code changes to ensure consistent formatting.

## Java Version References

**Never link to a specific Java distribution** (e.g., Adoptium, Temurin, OpenJDK.org, Amazon Corretto) in docs, READMEs, or comments. Just say "Java 21+" and let users install it however they prefer.

## Code Style

- Use the Spotless plugin for uniform code formatting—always run before committing
- Conductor is pluggable: when introducing new concepts, always use an **interface-based approach**
- DAO interfaces **MUST** be defined in the `core` module
- Implementation classes go in their respective persistence modules (e.g., `postgres-persistence`, `redis-persistence`)
- Follow existing patterns in the codebase for consistency
- Do not use emojis such as ✅ in the code, logs, or comments.  Keep comments professionals
- When adding new logic, comment the algorithm, design etc.   

## Architecture Guidelines

### Module Structure

- **core**: Contains interfaces, domain models, and core business logic
- **persistence modules**: Implementations of DAO interfaces (postgres, redis, mysql, etc.)
- **server**: Spring Boot application that brings everything together
- **client**: SDK for interacting with Conductor
- **ui**: React-based user interface

### Key Patterns

- DAOs are defined as interfaces in `core` and implemented in persistence modules
- System tasks extend `WorkflowSystemTask` and are registered via Spring
- Worker tasks use the `@WorkerTask` annotation for automatic discovery
- Configuration is primarily done through Spring properties

## Testing

- **Avoid mocks**: Use real implementations whenever possible
- **Test actual behavior**: Tests must verify real implementation logic, not duplicate it
- **Use Testcontainers**: For database, cache, and other external dependencies
- **Cover concurrency**: Ensure multi-threading scenarios are tested
- **Run tests before submitting**: `./gradlew test` must pass

### Test Locations

- Unit tests: `src/test/java` in each module
- Integration tests: `test-harness` module and `*-integration-test` modules
- E2E tests: `e2e` module

## PR Guidelines

- Submit PRs against the `main` branch
- Use clear, descriptive commit messages
- Run `./gradlew spotlessApply` and `./gradlew test` before pushing
- Add or update tests for any code changes
- Keep PRs focused—one logical change per PR

## Dependency Pinning

Some dependencies have hard version constraints that **must not be auto-bumped**. These are marked with:

```groovy
// PINNED (#964): <reason>
```

The issue number links back to https://github.com/conductor-oss/conductor/issues/964, which documents the full audit and upgrade path for each constraint.

### What PINNED means

`// PINNED (#964):` means the version is intentionally locked and upgrading it without understanding the constraint will break the build or cause a runtime failure. Do not bump a PINNED dependency as part of routine dependency updates or refactoring.

### Current hard pins

| Dependency | Pinned at | Why |
|---|---|---|
| `com.google.protobuf:protobuf-java` | `3.x` | 4.x + GraalVM polyglot 25.x causes Gradle to require `polyglot4`, which does not exist on Maven Central |
| `com.google.protobuf:protoc` | `3.25.5` | Must match `grpc-protobuf:1.73.0`, which depends on protobuf-java 3.x |
| `org.graalvm.*` (all 5 artifacts) | same version | All must share one version — mixing causes a `"polyglot version X not compatible with Truffle Y"` runtime error |
| `redis.clients:jedis` in `redis-concurrency-limit` | `3.6.0` | `revJedis` (6.0.0) does not work with Spring Data Redis in that module |
| `org.codehaus.jettison:jettison` | `strictly 1.5.4` | Gradle `strictly` constraint — no higher version has been validated |
| `org.conductoross:conductor-client` in `test-harness` | `5.0.1` | Fat JAR classpath conflict with conductor-common; resolved via a stripped JAR task |
| `org.awaitility:awaitility` in functional tests | `4.x` | e2e tests call `pollInterval(Duration)` added in Awaitility 4.0 |

### Before bumping a PINNED dependency

1. Read the comment carefully — it will name the incompatibility and often link to an upstream issue.
2. Check whether the upstream blocker has been resolved (e.g., new grpc-java release, new GraalVM release).
3. Test locally: `./gradlew clean build` plus `./gradlew test` in the affected modules.
4. If bumping GraalVM, bump **all five** `org.graalvm.*` artifacts together using `revGraalVM` in `dependencies.gradle`.
5. Update or remove the `// PINNED` comment once the constraint is lifted.

### PINNED vs. version floors

Hard caps use `// PINNED (#964):`. Version floors — where a minimum is enforced but higher versions are always welcome — use one of two lowercase prefixes instead:

```groovy
// Security: CVE-2025-12183 — lz4-java minimum patched version
// Compat: commons-lang3 3.18.0+ required by Testcontainers/commons-compress
```

- `// Security:` — minimum set to address a CVE or known vulnerability
- `// Compat:` — minimum set for compatibility with another library or framework

These are grep-able (`grep "// Security:" **/*.gradle`, `grep "// Compat:" **/*.gradle`) but read as normal developer comments. Dependabot may raise these freely; no special review needed beyond the usual.

## Security Considerations

- Never commit secrets, API keys, or credentials
- Be cautious with external dependencies—prefer well-maintained libraries
- Follow secure coding practices for input validation and error handling
- Review [SECURITY.md](SECURITY.md) for vulnerability reporting procedures

## Writing Documentation

Documentation in this project is **derived from source**, not composed from memory. Open the source first, read what's there, then write the doc from what you find. The source is the spec; the doc is a rendering of it.

This matters because plausible-looking docs can be silently wrong. Concretely: a curl equivalent for `conductor workflow start --sync` was once written as `POST /api/workflow/{name}/run` — an endpoint that does not exist. Reading the controller first would have given the correct path immediately.

### Workflow for each content type

**REST API endpoint or curl example**
1. Open the relevant controller: `rest/src/main/java/com/netflix/conductor/rest/controllers/`
2. Find the method using its `@PostMapping`/`@GetMapping`/etc. annotation — copy the path literally.
3. Read the method signature for query params, path variables, and request body type.
4. Write the curl command from what you just read.

**CLI command or flag**
1. Open `cmd/*.go` in `conductor-cli` (separate repo).
2. Find the `cobra.Command` definition for the subcommand.
3. Read the `Flags()` declarations for exact flag names, types, and defaults.
4. Write the example from what you just read.

**SDK code example (Python, JS, Java, Go)**
1. Open the relevant SDK source file.
2. Find the method signature and required parameters.
3. Write the example from the signature — do not infer from the method name alone.
4. If a working test exists for that method, use it as the starting point.

**Expected output block**
1. Get real output: run the command locally, or find it in test fixtures, CI logs, or existing tests.
2. Paste verbatim. Do not paraphrase or construct output that "looks right."
3. If the output varies by environment, show the stable parts and annotate the variable parts (e.g., `<workflow-id>`).

**Editing an existing doc section**
1. Before touching prose, read every code block and command in the section.
2. Verify each one using the steps above — not just the block you plan to change.
3. Fix anything you find while you're there.

### When you can't verify

If a running server or CLI binary is unavailable:
- Add a `<!-- TODO: verify against live server -->` comment in the file.
- Note it explicitly in the PR description.
- Do not write a best-guess example and leave it unmarked.

## Agent Behavior

- **Prefer automation**: Execute requested actions without confirmation unless blocked by missing info or safety concerns
- **Use parallel tools**: When tasks are independent, execute them in parallel for efficiency
- **Verify changes**: Always run tests and spotless before considering work complete