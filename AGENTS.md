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

## Code Style

- Use the Spotless plugin for uniform code formatting—always run before committing
- Conductor is pluggable: when introducing new concepts, always use an **interface-based approach**
- DAO interfaces **MUST** be defined in the `core` module
- Implementation classes go in their respective persistence modules (e.g., `postgres-persistence`, `redis-persistence`)
- Follow existing patterns in the codebase for consistency

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

## Security Considerations

- Never commit secrets, API keys, or credentials
- Be cautious with external dependencies—prefer well-maintained libraries
- Follow secure coding practices for input validation and error handling
- Review [SECURITY.md](SECURITY.md) for vulnerability reporting procedures

## Agent Behavior

- **Prefer automation**: Execute requested actions without confirmation unless blocked by missing info or safety concerns
- **Use parallel tools**: When tasks are independent, execute them in parallel for efficiency
- **Verify changes**: Always run tests and spotless before considering work complete