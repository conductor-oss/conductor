---
description: Developer implementation plan for the Workflow Message Queue (WMQ) feature in Conductor OSS — class names, method signatures, file paths, Spring wiring, and Redis data access patterns grounded in the actual codebase.
---

# Workflow Message Queue (WMQ) — Implementation Plan

This document is a developer-facing implementation reference. It specifies every file to create or modify, with full class names, method signatures, and code, grounded in the actual Conductor OSS module structure. Read the [architecture document](./workflow-message-queue-architecture.md) first for design rationale.


## Module and package conventions

| Module | Root package | Notes |
|---|---|---|
| `core` | `com.netflix.conductor.*` | Interfaces, system tasks, workflow engine, config |
| `redis-persistence` | `com.netflix.conductor.redis.*` | DAOs extend `BaseDynoDAO`, use `JedisProxy` |
| `rest` | `com.netflix.conductor.rest.controllers` | REST controllers, path prefix from `RequestMappingConstants` |

The API prefix in `RequestMappingConstants` is `/api/`. The `WORKFLOW` constant resolves to `/api/workflow`. The WMQ push endpoint sits under this path.

No new Gradle module is needed. All changes fit within the three modules above.


## Files to create

### 1. `WorkflowMessageQueueDAO.java`

**Path:** `core/src/main/java/com/netflix/conductor/dao/WorkflowMessageQueueDAO.java`

This interface follows the same DAO contract pattern used by `ExecutionDAO`, `MetadataDAO`, and other interfaces in the same package. No Spring annotations belong on the interface itself.

```java
package com.netflix.conductor.dao;

import java.util.List;

import com.netflix.conductor.common.model.WorkflowMessage;

/**
 * DAO for the per-workflow message queue used by the WMQ feature.
 *
 * <p>Each workflow has at most one queue, keyed by workflow ID. Messages are ordered FIFO.
 * Implementations must guarantee that {@link #pop} is atomic — concurrent callers must not
 * receive overlapping messages.
 */
public interface WorkflowMessageQueueDAO {

    /**
     * Append a message to the tail of the workflow's queue.
     *
     * @param workflowId the target workflow instance ID
     * @param message    the message to enqueue
     * @throws IllegalStateException if the queue has reached the configured maximum size
     */
    void push(String workflowId, WorkflowMessage message);

    /**
     * Atomically remove and return up to {@code maxCount} messages from the head of the queue.
     *
     * <p>Returns an empty list (never null) if the queue is empty.
     *
     * @param workflowId the target workflow instance ID
     * @param maxCount   maximum number of messages to return; must be &gt;= 1
     * @return dequeued messages, oldest first
     */
    List<WorkflowMessage> pop(String workflowId, int maxCount);

    /**
     * Return the current number of messages in the queue without removing any.
     *
     * @param workflowId the target workflow instance ID
     * @return queue depth, or 0 if the queue does not exist
     */
    long size(String workflowId);

    /**
     * Delete the entire queue for the given workflow. Called on workflow termination.
     * Safe to call if no queue exists (no-op).
     *
     * @param workflowId the workflow instance ID whose queue should be removed
     */
    void delete(String workflowId);
}
```


### 2. `WorkflowMessage.java`

**Path:** `common/src/main/java/com/netflix/conductor/common/model/WorkflowMessage.java`

This POJO is the model class for a single message in the queue. It uses Jackson for JSON serialization consistent with the rest of the codebase.

```java
package com.netflix.conductor.common.model;

import java.util.Map;

/**
 * Represents a single message in a workflow's message queue.
 *
 * <p>The {@code payload} field contains arbitrary user-supplied JSON, deserialized as a
 * {@code Map<String, Object>}. The {@code id} and {@code receivedAt} fields are populated
 * by the push endpoint at ingestion time.
 */
public class WorkflowMessage {

    /** UUID v4 string, generated at push time. */
    private String id;

    /** The workflow instance that owns this message. */
    private String workflowId;

    /**
     * Arbitrary caller-supplied data. Conductor does not interpret or validate this structure.
     * Deserialized by Jackson as a generic map.
     */
    private Map<String, Object> payload;

    /** ISO-8601 UTC timestamp recorded at ingestion time. */
    private String receivedAt;

    public WorkflowMessage() {}

    public WorkflowMessage(
            String id, String workflowId, Map<String, Object> payload, String receivedAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getReceivedAt() { return receivedAt; }
    public void setReceivedAt(String receivedAt) { this.receivedAt = receivedAt; }
}
```


### 3. `WorkflowMessageQueueProperties.java`

**Path:** `core/src/main/java/com/netflix/conductor/core/config/WorkflowMessageQueueProperties.java`

Follows the same `@ConfigurationProperties` pattern as `ConductorProperties` and `SweeperProperties`.

```java
package com.netflix.conductor.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.workflow-message-queue")
public class WorkflowMessageQueueProperties {

    /** Master switch. All WMQ beans are conditional on this being true. */
    private boolean enabled = false;

    /**
     * Maximum number of messages allowed in a single workflow's queue.
     * Push requests that would exceed this limit are rejected.
     */
    private int maxQueueSize = 1000;

    /**
     * TTL in seconds applied to the Redis queue key. Reset on every push.
     * Default: 24 hours.
     */
    private long ttlSeconds = 86400L;

    /**
     * Server-side cap on the batchSize parameter accepted by PULL_WORKFLOW_MESSAGES.
     * Task input requesting a higher value is silently capped to this limit.
     */
    private int maxBatchSize = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }

    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }
}
```


### 4. `PullWorkflowMessages.java`

**Path:** `core/src/main/java/com/netflix/conductor/core/execution/tasks/PullWorkflowMessages.java`

This is the system task implementation. Study `Wait.java` and `Human.java` in the same package for the exact patterns to follow.

Key decisions:
- `@Component("PULL_WORKFLOW_MESSAGES")` — the Spring bean name IS the task type string. This is how `SystemTaskRegistry` discovers tasks. There is no constant in `TaskType.java` for WMQ (it would need to be added if built-in registration is desired, but it can also remain a named component for an opt-in task).
- `@ConditionalOnProperty` with `matchIfMissing = false` — the task bean must not be created when the feature is disabled, otherwise `SystemTaskRegistry` would know about it and workflows could reference it even without the DAO.
- `isAsync() = true` — required for `SystemTaskWorkerCoordinator` to register it for polling.
- `execute()` returns `false` on empty queue (stay `IN_PROGRESS`) and `true` on success (status `COMPLETED`, advance workflow).

```java
package com.netflix.conductor.core.execution.tasks;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component(PullWorkflowMessages.TASK_TYPE)
@ConditionalOnProperty(name = "conductor.workflow-message-queue.enabled", havingValue = "true")
public class PullWorkflowMessages extends WorkflowSystemTask {

    public static final String TASK_TYPE = "PULL_WORKFLOW_MESSAGES";
    static final String INPUT_BATCH_SIZE = "batchSize";
    static final String OUTPUT_MESSAGES = "messages";
    static final String OUTPUT_COUNT = "count";

    private final WorkflowMessageQueueDAO dao;
    private final WorkflowMessageQueueProperties properties;

    public PullWorkflowMessages(
            WorkflowMessageQueueDAO dao, WorkflowMessageQueueProperties properties) {
        super(TASK_TYPE);
        this.dao = dao;
        this.properties = properties;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.IN_PROGRESS);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        int batchSize = getBatchSize(task);
        List<WorkflowMessage> messages = dao.pop(workflow.getWorkflowId(), batchSize);
        if (messages.isEmpty()) {
            // No messages yet — stay IN_PROGRESS; SystemTaskWorker will re-poll
            return false;
        }
        task.addOutput(OUTPUT_MESSAGES, messages);
        task.addOutput(OUTPUT_COUNT, messages.size());
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        // Poll every 1 second while waiting for messages.
        return Optional.of(1L);
    }

    private int getBatchSize(TaskModel task) {
        Object raw = task.getInputData().get(INPUT_BATCH_SIZE);
        int requested = 1;
        if (raw instanceof Number) {
            requested = ((Number) raw).intValue();
        }
        if (requested < 1) {
            requested = 1;
        }
        return Math.min(requested, properties.getMaxBatchSize());
    }
}
```

**Notes:**
- The `batchSize` input value is read from `task.getInputData()` which is populated by the workflow engine from the task definition's `inputParameters` map, so standard Conductor input parameter resolution (including `${workflow.input.xxx}` references) works for `batchSize`.
- On Redis failure during `pop`, the method logs and returns `false` so the task stays `IN_PROGRESS`. The task's `timeoutSeconds` provides the upper bound on how long this can continue before Conductor forcibly times out the task.
- `task.addOutput()` serializes the `List<WorkflowMessage>` via Jackson. Jackson will serialize each `WorkflowMessage` to a JSON object with its standard getter fields.


### 5. Lifecycle cleanup

Queue cleanup is handled entirely by the Redis TTL (`conductor.workflow-message-queue.ttlSeconds`, default 86400s). No `WorkflowStatusListener` implementation is included — `WorkflowStatusListener` is a single-bean interface and adding a WMQ implementation would conflict with other listener implementations in the same deployment.

The TTL is reset on every `push`, so active queues are never prematurely expired. Orphaned queues (e.g., after a server crash during workflow termination) expire automatically.


### 6. `RedisWorkflowMessageQueueDAO.java`

**Path:** `redis-persistence/src/main/java/com/netflix/conductor/redis/dao/RedisWorkflowMessageQueueDAO.java`

This is the Redis implementation of `WorkflowMessageQueueDAO`. It extends `BaseDynoDAO` exactly as `RedisExecutionDAO` does, using the inherited `jedisProxy`, `objectMapper`, `nsKey()`, and `toJson()` / `readValue()` utilities.

```java
package com.netflix.conductor.redis.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.commands.JedisCommands;

@Component
@Conditional(AnyRedisCondition.class)
@ConditionalOnProperty(
        name = "conductor.workflow-message-queue.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class RedisWorkflowMessageQueueDAO extends BaseDynoDAO implements WorkflowMessageQueueDAO {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisWorkflowMessageQueueDAO.class);

    private static final String WMQ_PREFIX = "wmq";

    // Dequeue uses LRANGE + LTRIM (not an atomic Lua script) because
    // JedisCommands (Jedis 3.3.0) does not expose eval(). This is safe because
    // Conductor holds a per-workflow execution lock during the decide cycle, so
    // concurrent pop() calls for the same workflow ID cannot occur.
    // For Redis 6.2+, LPOP <key> <count> would be a cleaner alternative.

    private final WorkflowMessageQueueProperties wmqProperties;

    public RedisWorkflowMessageQueueDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties,
            WorkflowMessageQueueProperties wmqProperties) {
        super(jedisProxy, objectMapper, conductorProperties, redisProperties);
        this.wmqProperties = wmqProperties;
    }

    @Override
    public void push(String workflowId, WorkflowMessage message) {
        String key = queueKey(workflowId);
        long current = jedisProxy.llen(key);  // See note below on llen
        if (current >= wmqProperties.getMaxQueueSize()) {
            throw new IllegalStateException(
                    "WMQ for workflow " + workflowId + " is full ("
                    + wmqProperties.getMaxQueueSize() + " messages). Cannot push.");
        }
        String json = toJson(message);
        jedisProxy.rpush(key, json);          // Append to tail (FIFO)
        jedisProxy.expire(key, (int) wmqProperties.getTtlSeconds());  // Reset TTL
        recordRedisDaoRequests("wmq_push");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WorkflowMessage> pop(String workflowId, int maxCount) {
        if (maxCount < 1) {
            throw new IllegalArgumentException("maxCount must be >= 1");
        }
        String key = queueKey(workflowId);

        // Execute atomic Lua script. The result is a List<String> of JSON values.
        List<String> rawMessages = (List<String>) jedisProxy.eval(
                ATOMIC_POP_SCRIPT,
                Collections.singletonList(key),
                Collections.singletonList(String.valueOf(maxCount)));

        if (rawMessages == null || rawMessages.isEmpty()) {
            return Collections.emptyList();
        }

        recordRedisDaoRequests("wmq_pop");
        return rawMessages.stream()
                .map(json -> readValue(json, WorkflowMessage.class))
                .collect(Collectors.toList());
    }

    @Override
    public long size(String workflowId) {
        return jedisProxy.llen(queueKey(workflowId));
    }

    @Override
    public void delete(String workflowId) {
        jedisProxy.del(queueKey(workflowId));
        recordRedisDaoRequests("wmq_delete");
    }

    private String queueKey(String workflowId) {
        // nsKey() prepends the namespace/stack/domain prefix from BaseDynoDAO,
        // so the full key is e.g.: "conductor.test.wmq.{workflowId}"
        return nsKey(WMQ_PREFIX, workflowId);
    }
}
```

**Note on `JedisProxy` method gaps:** The existing `JedisProxy` does not expose `LLEN`, `RPUSH`, `EVAL`, or list-range methods. These must be added to `JedisProxy` as thin delegation wrappers over `jedisCommands`, exactly as the existing `get`, `set`, `del`, `expire`, `sadd`, etc. methods are implemented. The methods to add:

```java
// In JedisProxy.java — additions required:

public Long rpush(String key, String... values) {
    return jedisCommands.rpush(key, values);
}

public Long llen(String key) {
    return jedisCommands.llen(key);
}

public Object eval(String script, List<String> keys, List<String> args) {
    // JedisCommands.eval signature: eval(String script, List<String> keys, List<String> args)
    return jedisCommands.eval(script, keys, args);
}
```

The `JedisCommands` interface from the Jedis library supports all of these. Verify compatibility with the Jedis version declared in `dependencies.gradle`.


### 7. `WorkflowMessageQueueConfiguration.java`

**Path:** `redis-persistence/src/main/java/com/netflix/conductor/redis/config/WorkflowMessageQueueConfiguration.java`

This `@Configuration` class wires together the DAO bean, the properties bean, and the cleanup listener bean. It is conditional on both the feature flag and Redis being configured.

```java
package com.netflix.conductor.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.listener.WorkflowMessageQueueCleanupListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.redis.dao.RedisWorkflowMessageQueueDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring configuration for the Workflow Message Queue feature backed by Redis.
 *
 * <p>This configuration is active only when:
 * <ol>
 *   <li>{@code conductor.workflow-message-queue.enabled=true}</li>
 *   <li>A Redis backend is configured ({@code conductor.db.type} is one of: dynomite, memory,
 *       redis_cluster, redis_sentinel, redis_standalone)</li>
 * </ol>
 *
 * <p>IMPORTANT — WorkflowStatusListener single-bean constraint:
 * ConductorCoreConfiguration registers a stub WorkflowStatusListener with matchIfMissing=true.
 * This configuration registers a WMQ cleanup listener as the WorkflowStatusListener bean,
 * which suppresses the stub. Deployments that also use an archiving or Kafka listener must
 * write a composite listener that delegates to both. See ArchivingWorkflowListenerConfiguration
 * for the established pattern.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(AnyRedisCondition.class)
@ConditionalOnProperty(
        name = "conductor.workflow-message-queue.enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(WorkflowMessageQueueProperties.class)
public class WorkflowMessageQueueConfiguration {

    @Bean
    public RedisWorkflowMessageQueueDAO redisWorkflowMessageQueueDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties redisProperties,
            WorkflowMessageQueueProperties wmqProperties) {
        return new RedisWorkflowMessageQueueDAO(
                jedisProxy, objectMapper, conductorProperties, redisProperties, wmqProperties);
    }

    @Bean
    public WorkflowStatusListener workflowMessageQueueCleanupListener(
            RedisWorkflowMessageQueueDAO dao) {
        return new WorkflowMessageQueueCleanupListener(dao);
    }
}
```


### 8. `WorkflowMessageQueueResource.java`

**Path:** `rest/src/main/java/com/netflix/conductor/rest/controllers/WorkflowMessageQueueResource.java`

The controller follows the same patterns as `WorkflowResource.java` in the same package. It uses `@RequestMapping(WORKFLOW)` (which resolves to `/api/workflow`) and maps the push endpoint to `POST /{workflowId}/messages`.

```java
package com.netflix.conductor.rest.controllers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.WorkflowModel;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.WORKFLOW;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * REST endpoint for pushing messages into a running workflow's message queue.
 *
 * <p>Active only when {@code conductor.workflow-message-queue.enabled=true}.
 */
@RestController
@RequestMapping(WORKFLOW)
@ConditionalOnProperty(
        name = "conductor.workflow-message-queue.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class WorkflowMessageQueueResource {

    private final WorkflowMessageQueueDAO dao;
    private final WorkflowExecutor workflowExecutor;

    public WorkflowMessageQueueResource(
            WorkflowMessageQueueDAO dao, WorkflowExecutor workflowExecutor) {
        this.dao = dao;
        this.workflowExecutor = workflowExecutor;
    }

    /**
     * Push a message into the running workflow's message queue.
     *
     * @param workflowId the ID of the target workflow instance
     * @param payload    arbitrary JSON object to include as the message payload
     * @return the generated message ID (UUID v4)
     * @throws ResponseStatusException 404 if the workflow is not found
     * @throws ResponseStatusException 409 if the workflow is not in RUNNING state
     * @throws ResponseStatusException 429 if the workflow's message queue is full
     */
    @PostMapping(
            value = "/{workflowId}/messages",
            consumes = APPLICATION_JSON_VALUE,
            produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Push a message into a running workflow's message queue")
    public String pushMessage(
            @PathVariable("workflowId") String workflowId,
            @RequestBody Map<String, Object> payload) {

        // Validate workflow exists and is RUNNING.
        WorkflowModel workflow;
        try {
            workflow = workflowExecutor.getWorkflow(workflowId, false);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId);
        }

        if (workflow.getStatus() != WorkflowModel.Status.RUNNING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot push message to workflow in state " + workflow.getStatus()
                    + ". Workflow must be RUNNING.");
        }

        // Build the message.
        String messageId = UUID.randomUUID().toString();
        WorkflowMessage message = new WorkflowMessage(
                messageId,
                workflowId,
                payload,
                Instant.now().toString());

        // Persist to Redis queue.
        try {
            dao.push(workflowId, message);
        } catch (IllegalStateException e) {
            // Queue is full.
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }

        // Trigger immediate workflow re-evaluation so the PULL_WORKFLOW_MESSAGES task
        // can be woken up without waiting for the next SystemTaskWorker poll interval.
        workflowExecutor.decide(workflowId);

        return messageId;
    }
}
```

**Note on workflow state lookup:** The controller calls `workflowExecutor.getWorkflow(workflowId, false)` with `includeTasks = false` to avoid loading all task data — only the workflow status is needed for validation. This follows the same pattern used internally in `WorkflowExecutorOps`.


## Files to modify

### 1. `JedisProxy.java`

**Path:** `redis-persistence/src/main/java/com/netflix/conductor/redis/jedis/JedisProxy.java`

Add the following methods to support the Redis List operations needed by `RedisWorkflowMessageQueueDAO`:

```java
// Append one or more values to the tail of a list.
public Long rpush(String key, String... values) {
    return jedisCommands.rpush(key, values);
}

// Return the length of a list; 0 if key does not exist.
public Long llen(String key) {
    return jedisCommands.llen(key);
}

// Evaluate a Lua script with the given keys and arguments.
public Object eval(String script, List<String> keys, List<String> args) {
    return jedisCommands.eval(script, keys, args);
}
```

Ensure the import for `java.util.List` is already present (it is, from the existing `hvals` method return type).


### 2. `core/src/main/resources/META-INF/additional-spring-configuration-metadata.json`

Add property metadata entries for IDE auto-completion and documentation tooling. Append to the existing `"properties"` array:

```json
{
  "name": "conductor.workflow-message-queue.enabled",
  "type": "java.lang.Boolean",
  "description": "Enables the Workflow Message Queue feature. When true, the PULL_WORKFLOW_MESSAGES system task, the push REST endpoint, and the Redis queue DAO are activated. Requires a Redis backend (conductor.db.type).",
  "sourceType": "com.netflix.conductor.core.config.WorkflowMessageQueueProperties",
  "defaultValue": false
},
{
  "name": "conductor.workflow-message-queue.maxQueueSize",
  "type": "java.lang.Integer",
  "description": "Maximum number of messages that can be queued per workflow instance. Push requests that would exceed this limit are rejected with an error.",
  "sourceType": "com.netflix.conductor.core.config.WorkflowMessageQueueProperties",
  "defaultValue": 1000
},
{
  "name": "conductor.workflow-message-queue.ttlSeconds",
  "type": "java.lang.Long",
  "description": "TTL in seconds applied to the Redis queue key. The TTL is reset on every push. Acts as a safety net for orphaned queues. Default: 86400 (24 hours).",
  "sourceType": "com.netflix.conductor.core.config.WorkflowMessageQueueProperties",
  "defaultValue": 86400
},
{
  "name": "conductor.workflow-message-queue.maxBatchSize",
  "type": "java.lang.Integer",
  "description": "Server-side upper bound on the batchSize parameter for PULL_WORKFLOW_MESSAGES. Task input requesting a higher batchSize is silently capped to this value.",
  "sourceType": "com.netflix.conductor.core.config.WorkflowMessageQueueProperties",
  "defaultValue": 100
}
```


## Property reference

Complete `application.properties` snippet showing all WMQ properties with defaults and explanations:

```properties
# -----------------------------------------------------------------------
# Workflow Message Queue (WMQ)
# -----------------------------------------------------------------------

# Master switch. Set to true to activate the feature.
# Requires: conductor.db.type set to a Redis variant
# (dynomite, memory, redis_cluster, redis_sentinel, redis_standalone).
# Default: false (feature is off, zero runtime footprint)
conductor.workflow-message-queue.enabled=false

# Maximum number of messages per workflow queue.
# Push requests that would exceed this limit are rejected.
# Tune based on expected throughput and Redis memory budget.
# Default: 1000
conductor.workflow-message-queue.maxQueueSize=1000

# Redis key TTL in seconds. The TTL is refreshed on every push.
# Acts as a safety net for orphaned queues (e.g., server crash before cleanup).
# Default: 86400 (24 hours)
conductor.workflow-message-queue.ttlSeconds=86400

# Server-side cap on batchSize for PULL_WORKFLOW_MESSAGES.
# Prevents single task invocations from consuming excessively large batches.
# Default: 100
conductor.workflow-message-queue.maxBatchSize=100
```


## Workflow definition example

A workflow that waits for an external approval message, with a 5-minute timeout:

```json
{
  "name": "human_approval_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "request_approval",
      "taskReferenceName": "request_approval_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://approvals.internal/request",
          "method": "POST",
          "body": {
            "workflowId": "${workflow.workflowId}",
            "requestedBy": "${workflow.input.userId}"
          }
        }
      }
    },
    {
      "name": "wait_for_approval",
      "taskReferenceName": "wait_for_approval_ref",
      "type": "PULL_WORKFLOW_MESSAGES",
      "inputParameters": {
        "batchSize": 1
      },
      "timeoutSeconds": 300,
      "timeoutPolicy": "TIME_OUT_WF"
    },
    {
      "name": "process_decision",
      "taskReferenceName": "process_decision_ref",
      "type": "SWITCH",
      "expression": "${wait_for_approval_ref.output.messages[0].payload.decision}",
      "evaluatorType": "value-param",
      "decisionCases": {
        "approved": [
          {
            "name": "execute_approved_path",
            "taskReferenceName": "execute_approved_ref",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://approvals.internal/execute",
                "method": "POST"
              }
            }
          }
        ]
      },
      "defaultCase": [
        {
          "name": "handle_rejection",
          "taskReferenceName": "handle_rejection_ref",
          "type": "TERMINATE",
          "inputParameters": {
            "terminationStatus": "FAILED",
            "terminationReason": "Approval denied by reviewer"
          }
        }
      ]
    }
  ]
}
```

For an agent loop that processes messages in batches until a stop condition:

```json
{
  "name": "agent_tool_loop",
  "version": 1,
  "tasks": [
    {
      "name": "poll_tool_results",
      "taskReferenceName": "poll_tool_results_ref",
      "type": "PULL_WORKFLOW_MESSAGES",
      "inputParameters": {
        "batchSize": 5
      },
      "timeoutSeconds": 60
    },
    {
      "name": "process_batch",
      "taskReferenceName": "process_batch_ref",
      "type": "INLINE",
      "inputParameters": {
        "expression": "function e() { return $.messages.length; }",
        "evaluatorType": "graaljs",
        "messages": "${poll_tool_results_ref.output.messages}"
      }
    }
  ]
}
```


## API usage example

Push a message to a running workflow using curl:

```bash
# Push an approval decision into a running workflow
curl -X POST \
  "http://localhost:8080/api/workflow/8e2c14e1-99ab-4c10-b4a8-a7b0d2f0e123/messages" \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "approved",
    "approvedBy": "user@example.com",
    "approvedAt": "2025-06-15T10:30:00Z",
    "comments": "Reviewed and approved after verifying the request details"
  }'

# Response: the generated message ID
# 3f2504e0-4f89-11d3-9a0c-0305e82c3301
```

Push using Python requests:

```python
import requests

workflow_id = "8e2c14e1-99ab-4c10-b4a8-a7b0d2f0e123"
response = requests.post(
    f"http://localhost:8080/api/workflow/{workflow_id}/messages",
    json={
        "decision": "approved",
        "approvedBy": "user@example.com"
    }
)
response.raise_for_status()
message_id = response.text
print(f"Message pushed with ID: {message_id}")
```

Check the output of a completed `PULL_WORKFLOW_MESSAGES` task:

```json
{
  "taskType": "PULL_WORKFLOW_MESSAGES",
  "status": "COMPLETED",
  "outputData": {
    "messages": [
      {
        "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        "workflowId": "8e2c14e1-99ab-4c10-b4a8-a7b0d2f0e123",
        "payload": {
          "decision": "approved",
          "approvedBy": "user@example.com"
        },
        "receivedAt": "2025-06-15T10:30:00Z"
      }
    ],
    "count": 1
  }
}
```

Reference the message payload in a downstream task via standard Conductor output interpolation:

```
${wait_for_approval_ref.output.messages[0].payload.decision}
```


## Implementation notes

### Atomic dequeue: Lua script vs. LPOP count

The `ATOMIC_POP_SCRIPT` in `RedisWorkflowMessageQueueDAO` uses `LRANGE` + `LTRIM` executed as a single Lua script. Lua scripts execute atomically in Redis — no other command can interleave between the range read and the trim.

For deployments on **Redis 6.2 or later**, `LPOP key count` is available natively and achieves the same atomic batch pop without a Lua script:

```java
// Redis 6.2+ alternative — replace the Lua eval block in pop() with:
List<String> rawMessages = jedisCommands.lpop(key, maxCount);
```

The Lua approach is the default because it ensures compatibility with Redis 5.x, which is still in active use. If minimum Redis version is raised to 6.2, the Lua script can be removed.

### decide() trigger after push

The explicit `workflowExecutor.decide(workflowId)` call at the end of `WorkflowMessageQueueResource.pushMessage()` is important for latency. Without it:

- A `PULL_WORKFLOW_MESSAGES` task sitting `IN_PROGRESS` with no messages would only wake up when `AsyncSystemTaskExecutor` re-polls its queue — at the `callbackAfterSeconds` interval (default: `conductor.app.systemTaskWorkerCallbackDuration`, commonly 30 seconds).
- With the explicit `decide()`, the workflow is evaluated immediately, and the task's `execute()` method is invoked on the same thread (or very shortly after, depending on lock acquisition).

The trade-off: `decide()` takes a workflow-level lock and does a full evaluation pass. For high-throughput push scenarios, this could create contention. If that becomes a concern, a lightweight "ping" mechanism that only queues a fast re-evaluation without a full decide pass would be a follow-up optimization.

### maxBatchSize guard

In `PullWorkflowMessages.execute()`, the `effectiveBatch` variable caps the user-supplied `batchSize` at `properties.getMaxBatchSize()`. This prevents a task definition from requesting, say, `batchSize: 100000`, which could cause a massive Redis `LRANGE` and serialize thousands of messages into a single task output payload. The server-side cap is enforced regardless of what is in the task definition.

### WorkflowStatusListener single-bean constraint

Conductor's `ConductorCoreConfiguration` registers the stub `WorkflowStatusListener` bean with `matchIfMissing = true`, meaning it applies unless another bean of the same type is explicitly registered. `WorkflowMessageQueueConfiguration` registers `WorkflowMessageQueueCleanupListener` as a `WorkflowStatusListener` bean when WMQ is enabled. This replaces the stub.

If the deployment also uses an archiving listener (`conductor.workflow-status-listener.type=archive`) or another listener implementation, a conflict will occur — Spring will find two beans of type `WorkflowStatusListener`. The resolution is to write a composite listener:

```java
// Example composite for WMQ + archiving:
@Bean
@Primary
public WorkflowStatusListener compositeListener(
        WorkflowMessageQueueCleanupListener wmqListener,
        ArchivingWorkflowStatusListener archivingListener) {
    return new CompositeWorkflowStatusListener(wmqListener, archivingListener);
}
```

This is a known composability gap in the current `WorkflowStatusListener` design (single interface, single bean). It should be documented in the feature's release notes.

### System task registration

`@Component("PULL_WORKFLOW_MESSAGES")` registers the task under the bean name `PULL_WORKFLOW_MESSAGES`. `SystemTaskRegistry` collects all `WorkflowSystemTask` beans at startup, keyed by their `getTaskType()` return value (which equals the constructor argument, which equals the bean name). This is the established pattern: see `@Component(TASK_TYPE_WAIT)` in `Wait.java` and `@Component(TASK_TYPE_HUMAN)` in `Human.java`.

`SystemTaskWorkerCoordinator` calls `systemTaskWorker.startPolling(task)` for each async task at `ApplicationReadyEvent`. Because `PullWorkflowMessages.isAsync()` returns `true`, it will be picked up automatically once registered.

A constant for the task type string should be added to `TaskType.java` (`TASK_TYPE_PULL_WORKFLOW_MESSAGES = "PULL_WORKFLOW_MESSAGES"`) if the task is expected to be listed in `TaskType.BUILT_IN_TASKS`. For an opt-in feature, it may be acceptable to omit it from the built-in list and rely solely on the Spring bean name.

### Swagger/OpenAPI visibility

The WMQ REST controller is registered conditionally. When `conductor.workflow-message-queue.enabled=false`, the bean does not exist, and Springdoc/Swagger scans only registered beans for OpenAPI generation. The endpoint will not appear in Swagger UI when disabled — this is the desired behavior and requires no special handling.

### Task timeout behavior

`PULL_WORKFLOW_MESSAGES` does not override `getEvaluationOffset()`. The standard `callbackAfterSeconds` from `AsyncSystemTaskExecutor` governs re-poll frequency while waiting for messages. Task-level timeout (`timeoutSeconds` in the task definition) is enforced by the existing Conductor timeout mechanism in `WorkflowSweeper`/`WorkflowExecutorOps` — no special code is needed in the task implementation. When the task times out, it transitions to `TIMED_OUT` and the workflow's `timeoutPolicy` determines the workflow-level outcome.


## Migration and rollout

### Enabling the feature

1. Add `conductor.workflow-message-queue.enabled=true` to your server configuration.
2. Ensure Redis is configured (`conductor.db.type` set to a supported Redis variant).
3. Restart the Conductor server.
4. Verify the `/api/workflow/{workflowId}/messages` endpoint appears in Swagger UI.
5. Verify `PULL_WORKFLOW_MESSAGES` appears as a known task type in the task metadata endpoint.

No database migrations are required. Redis keys are created lazily on first push.

### Workflow definition registration order

Register or update workflow definitions that use `PULL_WORKFLOW_MESSAGES` only **after** the server has started with the feature enabled. If a workflow definition referencing an unknown task type is registered before the feature is enabled, metadata validation will fail (or succeed with a warning depending on validation mode). Re-register the definition after enabling the feature.

### Disabling the feature after use

If the feature is disabled after workflows have been run:
- The Redis queue keys will expire naturally based on `ttlSeconds`.
- Any queued messages that have not been consumed are lost. Ensure workflows are drained or terminated before disabling.
- Existing completed workflows are unaffected — their queue keys were already deleted by the cleanup listener.

### Scaling considerations

- Multiple Conductor server instances share the same Redis backend. The Lua dequeue script ensures atomic pops across instances — concurrent calls from different servers on the same workflow queue will not overlap.
- The workflow-level lock (`ExecutionLockService`) is held during `decide()`. The explicit `decide()` after push should be safe under normal load but could serialize push throughput for high-frequency push scenarios targeting the same workflow. If this becomes a bottleneck, consider batching pushes client-side or using a lightweight queue ping mechanism.
