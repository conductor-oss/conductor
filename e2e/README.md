# Conductor E2E Tests

End-to-end tests for Conductor workflow orchestration.

## Overview

These tests validate Conductor functionality by running a Conductor server instance and using the Conductor Java SDK client to interact with it via REST API. The tests:

1. Build the Conductor server and create a Docker image (server-only, no UI)
2. Start two Conductor server instances with Redis and Elasticsearch using Docker Compose
3. Use the `conductor-client` SDK (v4.1.3) to register workflow definitions
4. Start workflows and poll/update tasks to drive execution
5. Assert expected workflow behavior and task states

## Prerequisites

- Docker and Docker Compose
- Java 17+
- Gradle

## Usage

Build, start services, and run tests:
```bash
cd conductor/e2e
./run-e2e-tests.sh
```

Options:
- `--skip-build` - Skip building the project and Docker image
- `--skip-docker` - Skip starting Docker services (assumes they're already running)
- `--stop-after` - Stop Docker services after tests complete

Or manually (from conductor root directory):
```bash
# Build the server
./gradlew :conductor-server:build -x test -x spotlessCheck

# Prepare Docker build context
cp server/build/libs/*boot*.jar e2e/docker/conductor-server.jar
cp -r e2e/config e2e/docker/

# Build the Docker image
docker build -t conductor:server-e2e e2e/docker

# Cleanup build artifacts
rm e2e/docker/conductor-server.jar
rm -rf e2e/docker/config

# Start Docker services
cd e2e
docker compose up -d

# Wait for Conductor to be ready, then run tests
cd ..
CONDUCTOR_SERVER_URL=http://localhost:8080/api ./gradlew :conductor-e2e:test
```

## Docker Services

The `docker-compose.yml` starts the following services:

| Service           | Port  | Description                              |
|-------------------|-------|------------------------------------------|
| HAProxy           | 8080  | Load balancer (round-robin to servers)   |
| Conductor Server 1| 8081  | Conductor server instance 1              |
| Conductor Server 2| 8082  | Conductor server instance 2              |
| Redis             | 7379  | Queue and persistence backend            |
| Elasticsearch     | 9201  | Workflow indexing                        |

## Environment Variables

| Variable              | Default                        | Description           |
|-----------------------|--------------------------------|-----------------------|
| CONDUCTOR_SERVER_URL  | http://localhost:8080/api      | Conductor API URL     |
| E2E_HIGH_PARALLELISM  | false                          | Enable parallel tests |
| E2E_DISABLE_RETRY     | false                          | Disable test retries  |
