# OpenSearch Persistence Modules Testing Plan

## Overview

This document provides a comprehensive testing plan for the new OpenSearch version-specific modules (`os-persistence-v2` and `os-persistence-v3`) introduced in PR #736.

**Branch:** `pr-736` (678-opensearch-version-modules-v2-v3)
**Epic:** #678 - Phase 2

## Current Status

✅ **Modules Created:**
- `os-persistence-v2` - OpenSearch 2.x support (opensearch-java:2.18.0)
- `os-persistence-v3` - OpenSearch 3.x support (opensearch-java:3.0.0)
- `os-persistence` - Migration stub (throws helpful error)

✅ **Configuration:**
- Both modules use shared `conductor.opensearch.*` namespace
- Activation via `conductor.indexing.type` property

⚠️ **Issue Identified:**
- Current `docker/server/config/config-redis-os.properties` uses OLD config style:
  - `conductor.indexing.type=opensearch` (triggers deprecation stub)
  - Needs update to `opensearch2` or `opensearch3`

## Prerequisites

### Java Environment
```bash
# This project requires Java 21 for compilation (per PR notes)
java -version  # Should show Java 21+
```

### Docker & Docker Compose
```bash
docker --version
docker-compose --version
```

## Testing Strategy

We'll test three scenarios:

1. **OpenSearch 2.x with os-persistence-v2** (production-ready)
2. **OpenSearch 3.x with os-persistence-v3** (upgraded client)
3. **Legacy config detection** (deprecation stub)

---

## Test 1: OpenSearch 2.x (os-persistence-v2)

### Step 1: Update Configuration File

Create/update config file for OpenSearch 2.x:

**File:** `docker/server/config/config-redis-os2.properties`

```properties
# Database persistence type
conductor.db.type=redis_standalone
conductor.queue.type=redis_standalone

conductor.redis.hosts=rs:6379:us-east-1c
conductor.redis-lock.serverAddress=redis://rs:6379
conductor.redis.taskDefCacheRefreshInterval=1
conductor.redis.workflowNamespacePrefix=conductor
conductor.redis.queueNamespacePrefix=conductor_queues

conductor.workflow-execution-lock.type=redis
conductor.app.workflowExecutionLockEnabled=true
conductor.app.lockTimeToTry=500

conductor.app.systemTaskWorkerThreadCount=20
conductor.app.systemTaskMaxPollCount=20

# NEW: OpenSearch 2.x configuration
conductor.indexing.enabled=true
conductor.indexing.type=opensearch2

# Shared OpenSearch namespace (from PR #675)
conductor.opensearch.url=http://os:9200
conductor.opensearch.indexPrefix=conductor
conductor.opensearch.indexReplicasCount=0
conductor.opensearch.clusterHealthColor=green

# Metrics (optional)
conductor.metrics-prometheus.enabled=true
management.endpoints.web.exposure.include=health,prometheus
management.health.redis.enabled=true

# Load sample workflow
loadSample=true
```

### Step 2: Create Docker Compose for OS 2.x

**File:** `docker/docker-compose-redis-os2.yaml`

```yaml
version: '2.3'

services:
  conductor-server:
    environment:
      - CONFIG_PROP=config-redis-os2.properties
      - JAVA_OPTS=-Dpolyglot.engine.WarnInterpreterOnly=false
    image: conductor:server
    container_name: conductor-server-os2
    build:
      context: ../
      dockerfile: docker/server/Dockerfile
      args:
        YARN_OPTS: ${YARN_OPTS}
        INDEXING_BACKEND: opensearch
    networks:
      - internal
    ports:
      - 8080:8080
      - 8127:5000
    healthcheck:
      test: ["CMD", "curl","-I" ,"-XGET", "http://localhost:8080/health"]
      interval: 60s
      timeout: 30s
      retries: 12
    links:
      - conductor-opensearch:os
      - conductor-redis:rs
    depends_on:
      conductor-opensearch:
        condition: service_healthy
      conductor-redis:
        condition: service_healthy
    logging:
      driver: "json-file"
      options:
        max-size: "1k"
        max-file: "3"

  conductor-redis:
    image: redis:6.2.3-alpine
    volumes:
      - ../server/config/redis.conf:/usr/local/etc/redis/redis.conf
    networks:
      - internal
    ports:
      - 6379:6379
    healthcheck:
      test: [ "CMD", "redis-cli","ping" ]

  conductor-opensearch:
    image: opensearchproject/opensearch:2.18.0
    container_name: opensearch-2
    environment:
      - plugins.security.disabled=true
      - cluster.name=opensearch-cluster
      - node.name=conductor-opensearch
      - discovery.seed_hosts=conductor-opensearch
      - cluster.initial_cluster_manager_nodes=conductor-opensearch
      - OPENSEARCH_INITIAL_ADMIN_PASSWORD=P4zzW)rd>>123_
    volumes:
      - osdata2-conductor:/usr/share/opensearch/data
    networks:
      - internal
    ports:
      - 9201:9200
    healthcheck:
      test: curl http://localhost:9200/_cluster/health -o /dev/null
      interval: 5s
      timeout: 5s
      retries: 12
    logging:
      driver: "json-file"
      options:
        max-size: "1k"
        max-file: "3"

volumes:
  osdata2-conductor:
    driver: local

networks:
  internal:
```

### Step 3: Build & Run

```bash
cd docker

# Build the server image with OpenSearch support
docker-compose -f docker-compose-redis-os2.yaml build

# Start services
docker-compose -f docker-compose-redis-os2.yaml up

# Watch logs
docker-compose -f docker-compose-redis-os2.yaml logs -f conductor-server
```

### Step 4: Verify OpenSearch 2.x Module Activation

Check server logs for confirmation that `os-persistence-v2` module loaded:

```bash
# Look for Spring Boot conditionals activation
docker-compose -f docker-compose-redis-os2.yaml logs conductor-server | grep -i "opensearch"
docker-compose -f docker-compose-redis-os2.yaml logs conductor-server | grep -i "os2"
```

Expected log indicators:
- No deprecation warnings
- Index creation logs showing OpenSearch connection
- No class conflicts

### Step 5: Test Workflows

```bash
# Access Conductor UI
open http://localhost:8127

# Access API
curl http://localhost:8080/api/metadata/workflow

# Run sample workflow (loaded via loadSample=true)
# Check that workflow execution creates indices in OpenSearch
```

### Step 6: Verify OpenSearch Indices

```bash
# Check indices created
curl http://localhost:9201/_cat/indices?v

# Should see conductor_* indices
# Expected: conductor_workflow, conductor_task, etc.
```

---

## Test 2: OpenSearch 3.x (os-persistence-v3)

### Step 1: Update Configuration File

Create config file for OpenSearch 3.x:

**File:** `docker/server/config/config-redis-os3.properties`

```properties
# Database persistence type
conductor.db.type=redis_standalone
conductor.queue.type=redis_standalone

conductor.redis.hosts=rs:6379:us-east-1c
conductor.redis-lock.serverAddress=redis://rs:6379
conductor.redis.taskDefCacheRefreshInterval=1
conductor.redis.workflowNamespacePrefix=conductor
conductor.redis.queueNamespacePrefix=conductor_queues

conductor.workflow-execution-lock.type=redis
conductor.app.workflowExecutionLockEnabled=true
conductor.app.lockTimeToTry=500

conductor.app.systemTaskWorkerThreadCount=20
conductor.app.systemTaskMaxPollCount=20

# NEW: OpenSearch 3.x configuration
conductor.indexing.enabled=true
conductor.indexing.type=opensearch3

# Shared OpenSearch namespace (from PR #675)
conductor.opensearch.url=http://os:9200
conductor.opensearch.indexPrefix=conductor
conductor.opensearch.indexReplicasCount=0
conductor.opensearch.clusterHealthColor=green

# Metrics (optional)
conductor.metrics-prometheus.enabled=true
management.endpoints.web.exposure.include=health,prometheus
management.health.redis.enabled=true

# Load sample workflow
loadSample=true
```

### Step 2: Create Docker Compose for OS 3.x

**File:** `docker/docker-compose-redis-os3.yaml`

```yaml
version: '2.3'

services:
  conductor-server:
    environment:
      - CONFIG_PROP=config-redis-os3.properties
      - JAVA_OPTS=-Dpolyglot.engine.WarnInterpreterOnly=false
    image: conductor:server
    container_name: conductor-server-os3
    build:
      context: ../
      dockerfile: docker/server/Dockerfile
      args:
        YARN_OPTS: ${YARN_OPTS}
        INDEXING_BACKEND: opensearch
    networks:
      - internal
    ports:
      - 8081:8080
      - 8128:5000
    healthcheck:
      test: ["CMD", "curl","-I" ,"-XGET", "http://localhost:8080/health"]
      interval: 60s
      timeout: 30s
      retries: 12
    links:
      - conductor-opensearch:os
      - conductor-redis:rs
    depends_on:
      conductor-opensearch:
        condition: service_healthy
      conductor-redis:
        condition: service_healthy
    logging:
      driver: "json-file"
      options:
        max-size: "1k"
        max-file: "3"

  conductor-redis:
    image: redis:6.2.3-alpine
    volumes:
      - ../server/config/redis.conf:/usr/local/etc/redis/redis.conf
    networks:
      - internal
    ports:
      - 6380:6379
    healthcheck:
      test: [ "CMD", "redis-cli","ping" ]

  conductor-opensearch:
    image: opensearchproject/opensearch:3.0.0
    container_name: opensearch-3
    environment:
      - plugins.security.disabled=true
      - cluster.name=opensearch-cluster
      - node.name=conductor-opensearch
      - discovery.seed_hosts=conductor-opensearch
      - cluster.initial_cluster_manager_nodes=conductor-opensearch
      - OPENSEARCH_INITIAL_ADMIN_PASSWORD=P4zzW)rd>>123_
    volumes:
      - osdata3-conductor:/usr/share/opensearch/data
    networks:
      - internal
    ports:
      - 9202:9200
    healthcheck:
      test: curl http://localhost:9200/_cluster/health -o /dev/null
      interval: 5s
      timeout: 5s
      retries: 12
    logging:
      driver: "json-file"
      options:
        max-size: "1k"
        max-file: "3"

volumes:
  osdata3-conductor:
    driver: local

networks:
  internal:
```

**Note:** Different ports to avoid conflicts if running both tests:
- Server: 8081 (instead of 8080)
- UI: 8128 (instead of 8127)
- Redis: 6380 (instead of 6379)
- OpenSearch: 9202 (instead of 9201)

### Step 3: Build & Run

```bash
cd docker

# Clean up previous test if needed
docker-compose -f docker-compose-redis-os2.yaml down -v

# Build for OS 3.x
docker-compose -f docker-compose-redis-os3.yaml build

# Start services
docker-compose -f docker-compose-redis-os3.yaml up

# Watch logs
docker-compose -f docker-compose-redis-os3.yaml logs -f conductor-server
```

### Step 4: Verify OpenSearch 3.x Module Activation

Check server logs for `os-persistence-v3` activation:

```bash
docker-compose -f docker-compose-redis-os3.yaml logs conductor-server | grep -i "opensearch"
docker-compose -f docker-compose-redis-os3.yaml logs conductor-server | grep -i "os3"
```

**Important:** Watch for breaking changes in opensearch-java 3.x:
- SearchAfter type changes
- DanglingIndex field type changes
- Statistics field types
- Task info/state unification

### Step 5: Test Workflows

```bash
# Access Conductor UI
open http://localhost:8128

# Access API
curl http://localhost:8081/api/metadata/workflow

# Test workflow execution
```

### Step 6: Verify OpenSearch 3.x Indices

```bash
# Check indices
curl http://localhost:9202/_cat/indices?v

# Verify OpenSearch 3.x API compatibility
curl http://localhost:9202/
```

---

## Test 3: Legacy Configuration Detection

This test verifies that the migration stub correctly detects old configuration and provides helpful guidance.

### Step 1: Use Existing Config

Keep the existing `config-redis-os.properties` unchanged:

```properties
conductor.indexing.type=opensearch  # OLD style - triggers stub
```

### Step 2: Run with Legacy Config

```bash
cd docker
docker-compose -f docker-compose-redis-os.yaml up
```

### Step 3: Verify Deprecation Error

Expected behavior:
- Server should start but log a clear deprecation error
- Error message should guide users to use `opensearch2` or `opensearch3`
- No actual indexing should occur (stub prevents activation)

```bash
# Check logs for deprecation message
docker-compose -f docker-compose-redis-os.yaml logs conductor-server | grep -i "deprecat"
docker-compose -f docker-compose-redis-os.yaml logs conductor-server | grep -i "opensearch"
```

Expected error message format:
```
OpenSearch persistence is deprecated with conductor.indexing.type=opensearch.
Please use conductor.indexing.type=opensearch2 or opensearch3 instead.
See: https://github.com/conductor-oss/conductor/issues/678
```

---

## Test 4: Classpath Isolation (Critical!)

This test verifies that shading prevents conflicts when both modules are present.

### Verification Steps

```bash
# 1. Ensure both modules are in the build
./gradlew :conductor-server:dependencies | grep -E "os-persistence-(v2|v3)"

# 2. Check that shaded packages are relocated
# Extract v2 jar
cd os-persistence-v2/build/libs
jar -tf conductor-os-persistence-v2.jar | grep "opensearch"
# Should show: com/netflix/conductor/os2/shaded/opensearch/client/*

# Extract v3 jar
cd ../../os-persistence-v3/build/libs
jar -tf conductor-os-persistence-v3.jar | grep "opensearch"
# Should show: com/netflix/conductor/os3/shaded/opensearch/client/*

# 3. Verify no unshaded opensearch classes leak
cd ../../../server/build/libs
jar -tf conductor-server-*.jar | grep "org/opensearch/client" | grep -v "shaded"
# Should return EMPTY (no unshaded packages)
```

### Expected Results

✅ Both modules present in server classpath
✅ Packages relocated to separate namespaces
✅ No duplicate class errors at runtime
✅ Only configured module activates

---

## Comprehensive Test Checklist

### Pre-Flight
- [ ] Java 21+ installed
- [ ] Docker & Docker Compose working
- [ ] On `pr-736` branch
- [ ] Build successful: `./gradlew build`

### OpenSearch 2.x Tests
- [ ] Config file created: `config-redis-os2.properties`
- [ ] Docker compose created: `docker-compose-redis-os2.yaml`
- [ ] Build succeeds
- [ ] Services start without errors
- [ ] os-persistence-v2 module activates (check logs)
- [ ] OpenSearch 2.18.0 accessible
- [ ] Indices created successfully
- [ ] Sample workflow executes
- [ ] UI accessible at localhost:8127
- [ ] API returns workflow metadata
- [ ] No class conflict errors

### OpenSearch 3.x Tests
- [ ] Config file created: `config-redis-os3.properties`
- [ ] Docker compose created: `docker-compose-redis-os3.yaml`
- [ ] Build succeeds
- [ ] Services start without errors
- [ ] os-persistence-v3 module activates (check logs)
- [ ] OpenSearch 3.0.0 accessible
- [ ] Indices created successfully
- [ ] Sample workflow executes
- [ ] UI accessible at localhost:8128
- [ ] API returns workflow metadata
- [ ] Breaking changes handled correctly
- [ ] No class conflict errors

### Legacy Config Tests
- [ ] Uses old config: `conductor.indexing.type=opensearch`
- [ ] Deprecation error logged
- [ ] Clear migration guidance provided
- [ ] No actual indexing occurs
- [ ] Server continues to run (doesn't crash)

### Classpath Isolation Tests
- [ ] Both modules in server build
- [ ] Shaded packages properly relocated
- [ ] No unshaded opensearch classes in server jar
- [ ] No duplicate class errors at runtime
- [ ] Correct module activates based on config

### Functional Tests
- [ ] Create workflow via API
- [ ] Execute workflow
- [ ] Query workflow status
- [ ] Search workflows in UI
- [ ] Verify data persisted in OpenSearch
- [ ] Test workflow termination (#615 bug fix)
- [ ] Test workflow archival (#615 bug fix)

---

## Known Issues & Blockers

### From PR Description

1. **Java 21 Required** - Compilation blocked locally without Java 21
2. **opensearch-java 3.x Breaking Changes** - Need to handle in v3 module:
   - SearchAfter: String → FieldValue
   - DanglingIndex.creationDateMillis: String → long
   - Statistics field types: Number → int/Integer
   - tasks.Info/State unification

### Related Issues

- #615 - Workflow archival/termination bugs (should test both versions)
- #650 - Blocked upgrade PR (will unblock after this merges)
- #348 - Build spotless issues (may affect this PR)

---

## Success Criteria

Before marking Phase 2 complete:

- [ ] Both modules build successfully
- [ ] Both modules pass all tests
- [ ] Docker compose examples work for v2 and v3
- [ ] Shading prevents classpath conflicts
- [ ] Only configured module activates at runtime
- [ ] Migration stub provides clear guidance
- [ ] No regressions in core functionality
- [ ] Issue #615 bugs fixed (or tracked separately)

---

## Next Steps After Testing

1. **Fix compilation issues** (if Java 21 unavailable locally)
2. **Handle opensearch-java 3.x breaking changes** in v3 module
3. **Add docker-compose files** to PR
4. **Update documentation** with new configuration
5. **Create migration guide** for users
6. **Test on CI** (should have Java 21)
7. **Mark PR ready for review**
8. **Merge** and unblock #650

---

## Quick Start (TL;DR)

```bash
# Prerequisites
java -version  # Must be 21+
docker --version

# Get on the right branch
git checkout pr-736

# Test OpenSearch 2.x
cd docker
docker-compose -f docker-compose-redis-os2.yaml build
docker-compose -f docker-compose-redis-os2.yaml up

# In another terminal - verify
curl http://localhost:8080/api/metadata/workflow
curl http://localhost:9201/_cat/indices?v
open http://localhost:8127

# Test OpenSearch 3.x
docker-compose -f docker-compose-redis-os2.yaml down -v
docker-compose -f docker-compose-redis-os3.yaml build
docker-compose -f docker-compose-redis-os3.yaml up

# Verify
curl http://localhost:8081/api/metadata/workflow
curl http://localhost:9202/_cat/indices?v
open http://localhost:8128
```

---

## Troubleshooting

### Issue: Build fails with Java version error

**Solution:** Install Java 21 or use CI

```bash
# Check Java version
java -version

# If using jenv (macOS)
jenv versions
jenv local 21

# Or use JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew build
```

### Issue: OpenSearch container fails to start

**Solution:** Check Docker resources, increase memory

```bash
# Check logs
docker-compose logs conductor-opensearch

# Common issues:
# - Insufficient memory (need 2GB+)
# - Port conflicts (9201/9202 in use)
# - Volume permission issues
```

### Issue: Server can't connect to OpenSearch

**Solution:** Verify networking

```bash
# Check if OpenSearch is healthy
curl http://localhost:9201/_cluster/health

# Check Docker network
docker network ls
docker network inspect docker_internal
```

### Issue: Wrong module activates

**Solution:** Check configuration

```bash
# Verify config file
cat docker/server/config/config-redis-os2.properties | grep "indexing.type"

# Should be: conductor.indexing.type=opensearch2 (not opensearch!)

# Check server logs for which module loaded
docker-compose logs conductor-server | grep -i "conditional"
```

### Issue: Class conflicts at runtime

**Solution:** Verify shading worked

```bash
# Check shaded JAR contents
jar -tf os-persistence-v2/build/libs/conductor-os-persistence-v2.jar | grep opensearch

# Should see relocated paths like:
# com/netflix/conductor/os2/shaded/opensearch/client/*
```

---

## References

- **Epic:** #678
- **PR:** #736
- **Related PRs:** #675 (merged), #650 (blocked)
- **Related Issues:** #615, #539, #505, #348
- **Conductor Docs:** https://conductor-oss.org/devguide/how-tos/persistence/
- **OpenSearch Docs:** https://opensearch.org/docs/latest/
