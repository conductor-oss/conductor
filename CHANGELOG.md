Conductor has been upgraded to use the SpringBoot framework and requires Java11 or above.  
#### NOTE: The java clients (conductor-client, conductor-client-spring, conductor-grpc-client) are still compiled using Java8 to ensure backward compatibility and smoother migration.

## Removals/Deprecations
- Removed support for EmbeddedElasticSearch
- Removed deprecated constructors in DynoQueueDAO
- Removed deprecated methods in the Worker interface
- Removed OAuth Support in HTTP task (Looking for contributions for OAuth/OAuth2.0)
- Removed deprecated fields and methods in the Workflow object
- Removed deprecated fields and methods in the Task object
- Removed deprecated fields and methods in the WorkflowTask object

Removed unused methods from QueueDAO:
- List pop(String, int, int, long)
- List pollMessages(String, int, int, long)

Removed APIs:
- GET /tasks/in_progress/{tasktype}
- GET /tasks/in_progress/{workflowId}/{taskRefName}
- POST /tasks/{taskId}/ack
- POST /tasks/queue/requeue
- DELETE /queue/{taskType}/{taskId}


- GET /event/queues
- GET /event/queues/providers


- void restart(String) in workflow client
- List getPendingTasksByType(String, String, Integer) in task client
- Task getPendingTaskForWorkflow(String, String) in task client
- boolean preAck(Task) in Worker
- int getPollCount() in Worker

## What's changed
Changes to configurations:

### `azureblob-storage` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.external.payload.storage.azure_blob.connection_string | conductor.external-payload-storage.azureblob.connectionString | null |
| workflow.external.payload.storage.azure_blob.container_name | conductor.external-payload-storage.azureblob.containerName | conductor-payloads |
| workflow.external.payload.storage.azure_blob.endpoint | conductor.external-payload-storage.azureblob.endpoint | null |
| workflow.external.payload.storage.azure_blob.sas_token | conductor.external-payload-storage.azureblob.sasToken | null |
| workflow.external.payload.storage.azure_blob.signedurlexpirationseconds | conductor.external-payload-storage.azureblob.signedUrlExpirationDuration | 5s |
| workflow.external.payload.storage.azure_blob.workflow_input_path | conductor.external-payload-storage.azureblob.workflowInputPath | workflow/input/ | 
| workflow.external.payload.storage.azure_blob.workflow_output_path | conductor.external-payload-storage.azureblob.workflowOutputPath | workflow/output/ |
| workflow.external.payload.storage.azure_blob.task_input_path | conductor.external-payload-storage.azureblob.taskInputPath | task/input/ |
| workflow.external.payload.storage.azure_blob.task_output_path | conductor.external-payload-storage.azureblob.taskOutputPath | task/output/ |

### `cassandra-persistence` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.cassandra.host | conductor.cassandra.hostAddress | 127.0.0.1 |
| workflow.cassandra.port | conductor.cassandra.port | 9142 |
| workflow.cassandra.cluster | conductor.cassandra.cluster | "" |
| workflow.cassandra.keyspace | conductor.cassandra.keyspace | conductor |
| workflow.cassandra.shard.size | conductor.cassandra.shardSize | 100 |
| workflow.cassandra.replication.strategy | conductor.cassandra.replicationStrategy | SimpleStrategy |
| workflow.cassandra.replication.factor.key | conductor.cassandra.replicationFactorKey | replication_factor |
| workflow.cassandra.replication.factor.value | conductor.cassandra.replicationFactorValue | 3 |
| workflow.cassandra.read.consistency.level | conductor.cassandra.readConsistencyLevel | LOCAL_QUORUM |
| workflow.cassandra.write.consistency.level | conductor.cassandra.writeConsistencyLevel | LOCAL_QUORUM |
| conductor.taskdef.cache.refresh.time.seconds | conductor.cassandra.taskDefCacheRefreshInterval | 60s |
| conductor.eventhandler.cache.refresh.time.seconds | conductor.cassandra.eventHandlerCacheRefreshInterval | 60s |
| workflow.event.execution.persistence.ttl.seconds | conductor.cassandra.eventExecutionPersistenceTTL | 0s |

### `contribs` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.archival.ttl.seconds | conductor.workflow-status-listener.archival.ttlDuration | 0s |
| workflow.archival.delay.queue.worker.thread.count | conductor.workflow-status-listener.archival.delayQueueWorkerThreadCount | 5 |
| workflow.archival.delay.seconds | conductor.workflow-status-listener.archival.delaySeconds | 60 |
|  |  |
| workflowstatuslistener.publisher.success.queue | conductor.workflow-status-listener.queue-publisher.successQueue | _callbackSuccessQueue |
| workflowstatuslistener.publisher.failure.queue | conductor.workflow-status-listener.queue-publisher.failureQueue | _callbackFailureQueue |
|  |  |  |
| com.netflix.conductor.contribs.metrics.LoggingMetricsModule.reportPeriodSeconds | conductor.metrics-logger.reportInterval | 30s |
|  |  |  |
| workflow.event.queues.amqp.batchSize | conductor.event-queues.amqp.batchSize | 1 |
| workflow.event.queues.amqp.pollTimeInMs | conductor.event-queues.amqp.pollTimeDuration | 100ms |
| workflow.event.queues.amqp.hosts | conductor.event-queues.amqp.hosts | localhost |
| workflow.event.queues.amqp.username | conductor.event-queues.amqp.username | guest |
| workflow.event.queues.amqp.password | conductor.event-queues.amqp.password | guest |
| workflow.event.queues.amqp.virtualHost | conductor.event-queues.amqp.virtualHost | / |
| workflow.event.queues.amqp.port | conductor.event-queues.amqp.port.port | 5672 |
| workflow.event.queues.amqp.connectionTimeout | conductor.event-queues.amqp.connectionTimeout | 60000ms |
| workflow.event.queues.amqp.useNio | conductor.event-queues.amqp.useNio | false |
| workflow.event.queues.amqp.durable | conductor.event-queues.amqp.durable | true |
| workflow.event.queues.amqp.exclusive | conductor.event-queues.amqp.exclusive | false |
| workflow.event.queues.amqp.autoDelete | conductor.event-queues.amqp.autoDelete | false |
| workflow.event.queues.amqp.contentType | conductor.event-queues.amqp.contentType | application/json |
| workflow.event.queues.amqp.contentEncoding | conductor.event-queues.amqp.contentEncoding | UTF-8 |
| workflow.event.queues.amqp.amqp_exchange | conductor.event-queues.amqp.exchangeType | topic |
| workflow.event.queues.amqp.deliveryMode | conductor.event-queues.amqp.deliveryMode | 2 |
| workflow.listener.queue.useExchange | conductor.event-queues.amqp.useExchange | true |
| workflow.listener.queue.prefix | conductor.event-queues.amqp.listenerQueuePrefix | "" |
|  |  |  |
| io.nats.streaming.clusterId | conductor.event-queues.nats-stream.clusterId | test-cluster |
| io.nats.streaming.durableName | conductor.event-queues.nats-stream.durableName | null |
| io.nats.streaming.url | conductor.event-queues.nats-stream.url | nats://localhost:4222 |
|  |  |  |
| workflow.event.queues.sqs.batchSize | conductor.event-queues.sqs.batchSize | 1 |
| workflow.event.queues.sqs.pollTimeInMS | conductor.event-queues.sqs.pollTimeDuration | 100ms |
| workflow.event.queues.sqs.visibilityTimeoutInSeconds | conductor.event-queues.sqs.visibilityTimeout | 60s |
| workflow.listener.queue.prefix | conductor.event-queues.sqs.listenerQueuePrefix | "" |
| workflow.listener.queue.authorizedAccounts | conductor.event-queues.sqs.authorizedAccounts | "" |
|  |  |  |
| workflow.external.payload.storage.s3.bucket | conductor.external-payload-storage.s3.bucketName | conductor_payloads |
| workflow.external.payload.storage.s3.signedurlexpirationseconds | conductor.external-payload-storage.s3.signedUrlExpirationDuration | 5s |
| workflow.external.payload.storage.s3.region | conductor.external-payload-storage.s3.region | us-east-1 |
|  |  |  |
| http.task.read.timeout | conductor.tasks.http.readTimeout | 150ms |
| http.task.connect.timeout | conductor.tasks.http.connectTimeout | 100ms |
|  |  |  |
| kafka.publish.request.timeout.ms | conductor.tasks.kafka-publish.requestTimeout | 100ms |
| kafka.publish.max.block.ms | conductor.tasks.kafka-publish.maxBlock | 500ms |
| kafka.publish.producer.cache.size | conductor.tasks.kafka-publish.cacheSize | 10 |
| kafka.publish.producer.cache.time.ms | conductor.tasks.kafka-publish.cacheTime | 120000ms |

### `core` module:

| Old | New | Default |
| --- | --- | --- |
| environment | _removed_ |  |
| STACK | conductor.app.stack | test |
| APP_ID | conductor.app.appId | conductor |
| workflow.executor.service.max.threads | conductor.app.executorServiceMaxThreadCount | 50 |
| decider.sweep.frequency.seconds | conductor.app.sweepFrequency | 30s |
| workflow.sweeper.thread.count | conductor.app.sweeperThreadCount | 5 |
| - | conductor.app.sweeperWorkflowPollTimeout | 2000ms |
| workflow.event.processor.thread.count | conductor.app.eventProcessorThreadCount | 2 |
| workflow.event.message.indexing.enabled | conductor.app.eventMessageIndexingEnabled | true |
| workflow.event.execution.indexing.enabled | conductor.app.eventExecutionIndexingEnabled | true |
| workflow.decider.locking.enabled | conductor.app.workflowExecutionLockEnabled | false |
| workflow.locking.lease.time.ms | conductor.app.lockLeaseTime | 60000ms |
| workflow.locking.time.to.try.ms | conductor.app.lockTimeToTry | 500ms |
| tasks.active.worker.lastpoll | conductor.app.activeWorkerLastPollTimeout | 10s |
| task.queue.message.postponeSeconds | conductor.app.taskExecutionPostponeDuration | 60s |
| workflow.taskExecLog.indexing.enabled | conductor.app.taskExecLogIndexingEnabled | true |
| async.indexing.enabled | conductor.app.asyncIndexingEnabled | false |
| workflow.system.task.worker.thread.count | conductor.app.systemTaskWorkerThreadCount | # available processors * 2 |
| workflow.system.task.worker.callback.seconds | conductor.app.systemTaskWorkerCallbackDuration | 30s |
| workflow.system.task.worker.poll.interval | conductor.app.systemTaskWorkerPollInterval | 50s |
| workflow.system.task.worker.executionNameSpace | conductor.app.systemTaskWorkerExecutionNamespace | "" |
| workflow.isolated.system.task.worker.thread.count | conductor.app.isolatedSystemTaskWorkerThreadCount | 1 |
| workflow.system.task.queue.pollCount | conductor.app.systemTaskMaxPollCount | 1 |
| async.update.short.workflow.duration.seconds | conductor.app.asyncUpdateShortRunningWorkflowDuration | 30s |
| async.update.delay.seconds | conductor.app.asyncUpdateDelay | 60s |
| summary.input.output.json.serialization.enabled | conductor.app.summary-input-output-json-serialization.enabled | false |
| workflow.owner.email.mandatory | conductor.app.ownerEmailMandatory | true |
| workflow.repairservice.enabled | conductor.app.workflowRepairServiceEnabled | false |
| workflow.event.queue.scheduler.poll.thread.count | conductor.app.eventSchedulerPollThreadCount | # CPU cores |
| workflow.dyno.queues.pollingInterval | conductor.app.eventQueuePollInterval | 100ms |
| workflow.dyno.queues.pollCount | conductor.app.eventQueuePollCount | 10 |
| workflow.dyno.queues.longPollTimeout | conductor.app.eventQueueLongPollTimeout | 1000ms |
| conductor.workflow.input.payload.threshold.kb | conductor.app.workflowInputPayloadSizeThreshold | 5120KB |
| conductor.max.workflow.input.payload.threshold.kb | conductor.app.maxWorkflowInputPayloadSizeThreshold | 10240KB |
| conductor.workflow.output.payload.threshold.kb | conductor.app.workflowOutputPayloadSizeThreshold | 5120KB |
| conductor.max.workflow.output.payload.threshold.kb | conductor.app.maxWorkflowOutputPayloadSizeThreshold | 10240KB |
| conductor.task.input.payload.threshold.kb | conductor.app.taskInputPayloadSizeThreshold | 3072KB |
| conductor.max.task.input.payload.threshold.kb | conductor.app.maxTaskInputPayloadSizeThreshold | 10240KB |
| conductor.task.output.payload.threshold.kb | conductor.app.taskOutputPayloadSizeThreshold | 3072KB |
| conductor.max.task.output.payload.threshold.kb | conductor.app.maxTaskOutputPayloadSizeThreshold | 10240KB |
| conductor.max.workflow.variables.payload.threshold.kb | conductor.app.maxWorkflowVariablesPayloadSizeThreshold | 256KB |
|  |  |  |
| workflow.isolated.system.task.enable | conductor.app.isolatedSystemTaskEnabled | false |
| workflow.isolated.system.task.poll.time.secs | conductor.app.isolatedSystemTaskQueuePollInterval | 10s |
|  |  |  |
| workflow.task.pending.time.threshold.minutes | conductor.app.taskPendingTimeThreshold |  60m |
|  |  |  |
| workflow.monitor.metadata.refresh.counter | conductor.workflow-monitor.metadataRefreshInterval | 10 |
| workflow.monitor.stats.freq.seconds | conductor.workflow-monitor.statsFrequency | 60s |

### `es6-persistence` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.elasticsearch.version | conductor.elasticsearch.version | 6 |
| workflow.elasticsearch.url | conductor.elasticsearch.url | localhost:9300 |
| workflow.elasticsearch.index.name | conductor.elasticsearch.indexPrefix | conductor |
| workflow.elasticsearch.tasklog.index.name | _removed_ |  |
| workflow.elasticsearch.cluster.health.color | conductor.elasticsearch.clusterHealthColor | green |
| workflow.elasticsearch.archive.search.batchSize | _removed_ |  |
| workflow.elasticsearch.index.batchSize | conductor.elasticsearch.indexBatchSize | 1 |
| workflow.elasticsearch.async.dao.worker.queue.size | conductor.elasticsearch.asyncWorkerQueueSize | 100 |
| workflow.elasticsearch.async.dao.max.pool.size | conductor.elasticsearch.asyncMaxPoolSize | 12 |
| workflow.elasticsearch.async.buffer.flush.timeout.seconds | conductor.elasticsearch.asyncBufferFlushTimeout | 10s |
| workflow.elasticsearch.index.shard.count | conductor.elasticsearch.indexShardCount | 5 |
| workflow.elasticsearch.index.replicas.count | conductor.elasticsearch.indexReplicasCount | 1 |
| tasklog.elasticsearch.query.size | conductor.elasticsearch.taskLogResultLimit | 10 |
| workflow.elasticsearch.rest.client.connectionRequestTimeout.milliseconds | conductor.elasticsearch.restClientConnectionRequestTimeout | -1 |
| workflow.elasticsearch.auto.index.management.enabled | conductor.elasticsearch.autoIndexManagementEnabled | true |
| workflow.elasticsearch.document.type.override | conductor.elasticsearch.documentTypeOverride | "" |

### `es7-persistence` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.elasticsearch.version | conductor.elasticsearch.version | 7 |
| workflow.elasticsearch.url | conductor.elasticsearch.url | localhost:9300 |
| workflow.elasticsearch.index.name | conductor.elasticsearch.indexPrefix | conductor |
| workflow.elasticsearch.tasklog.index.name | _removed_ |  |
| workflow.elasticsearch.cluster.health.color | conductor.elasticsearch.clusterHealthColor | green |
| workflow.elasticsearch.archive.search.batchSize | _removed_ |  |
| workflow.elasticsearch.index.batchSize | conductor.elasticsearch.indexBatchSize | 1 |
| workflow.elasticsearch.async.dao.worker.queue.size | conductor.elasticsearch.asyncWorkerQueueSize | 100 |
| workflow.elasticsearch.async.dao.max.pool.size | conductor.elasticsearch.asyncMaxPoolSize | 12 |
| workflow.elasticsearch.async.buffer.flush.timeout.seconds | conductor.elasticsearch.asyncBufferFlushTimeout | 10s |
| workflow.elasticsearch.index.shard.count | conductor.elasticsearch.indexShardCount | 5 |
| workflow.elasticsearch.index.replicas.count | conductor.elasticsearch.indexReplicasCount | 1 |
| tasklog.elasticsearch.query.size | conductor.elasticsearch.taskLogResultLimit | 10 |
| workflow.elasticsearch.rest.client.connectionRequestTimeout.milliseconds | conductor.elasticsearch.restClientConnectionRequestTimeout | -1 |
| workflow.elasticsearch.auto.index.management.enabled | conductor.elasticsearch.autoIndexManagementEnabled | true |
| workflow.elasticsearch.document.type.override | conductor.elasticsearch.documentTypeOverride | "" |
| workflow.elasticsearch.basic.auth.username | conductor.elasticsearch.username | "" |
| workflow.elasticsearch.basic.auth.password | conductor.elasticsearch.password | "" |

### `grpc-server` module:

| Old | New | Default |
| --- | --- | --- |
| conductor.grpc.server.port | conductor.grpc-server.port | 8090 |
| conductor.grpc.server.reflectionEnabled | conductor.grpc-server.reflectionEnabled | true |

### `mysql-persistence` module (v3.0.0 - v3.0.5):

| Old | New | Default |
| --- | --- | --- |
| jdbc.url | conductor.mysql.jdbcUrl | jdbc:mysql://localhost:3306/conductor |
| jdbc.username | conductor.mysql.jdbcUsername | conductor |
| jdbc.password | conductor.mysql.jdbcPassword | password |
| flyway.enabled | conductor.mysql.flywayEnabled | true |
| flyway.table | conductor.mysql.flywayTable | null |
| conductor.mysql.connection.pool.size.max | conductor.mysql.connectionPoolMaxSize | -1 |
| conductor.mysql.connection.pool.idle.min | conductor.mysql.connectionPoolMinIdle | -1 |
| conductor.mysql.connection.lifetime.max | conductor.mysql.connectionMaxLifetime | 30m |
| conductor.mysql.connection.idle.timeout | conductor.mysql.connectionIdleTimeout | 10m |
| conductor.mysql.connection.timeout | conductor.mysql.connectionTimeout | 30s | 
| conductor.mysql.transaction.isolation.level | conductor.mysql.transactionIsolationLevel | "" |
| conductor.mysql.autocommit | conductor.mysql.autoCommit | false |
| conductor.taskdef.cache.refresh.time.seconds | conductor.mysql.taskDefCacheRefreshInterval | 60s |

### `mysql-persistence` module (v3.0.5+):

| Old | New |
| --- | --- |
| jdbc.url | spring.datasource.url |
| jdbc.username | spring.datasource.username |
| jdbc.password | spring.datasource.password |
| flyway.enabled | spring.flyway.enabled |
| flyway.table | spring.flyway.table |
| conductor.mysql.connection.pool.size.max | spring.datasource.hikari.maximum-pool-size |
| conductor.mysql.connection.pool.idle.min | spring.datasource.hikari.minimum-idle |
| conductor.mysql.connection.lifetime.max | spring.datasource.hikari.max-lifetime |
| conductor.mysql.connection.idle.timeout | spring.datasource.hikari.idle-timeout |
| conductor.mysql.connection.timeout | spring.datasource.hikari.connection-timeout |
| conductor.mysql.transaction.isolation.level | spring.datasource.hikari.transaction-isolation |
| conductor.mysql.autocommit | spring.datasource.hikari.auto-commit |
| conductor.taskdef.cache.refresh.time.seconds | conductor.mysql.taskDefCacheRefreshInterval |

* for more properties and default values: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#application-properties.data.spring.datasource.hikari

### `postgres-persistence` module (v3.0.0 - v3.0.5):

| Old | New | Default |
| --- | --- | --- |
| jdbc.url | conductor.postgres.jdbcUrl | jdbc:postgresql://localhost:5432/conductor |
| jdbc.username | conductor.postgres.jdbcUsername | conductor |
| jdbc.password | conductor.postgres.jdbcPassword | password |
| flyway.enabled | conductor.postgres.flywayEnabled | true |
| flyway.table | conductor.postgres.flywayTable | null |
| conductor.postgres.connection.pool.size.max | conductor.postgres.connectionPoolMaxSize | -1 |
| conductor.postgres.connection.pool.idle.min | conductor.postgres.connectionPoolMinIdle | -1 |
| conductor.postgres.connection.lifetime.max | conductor.postgres.connectionMaxLifetime | 30m |
| conductor.postgres.connection.idle.timeout | conductor.postgres.connectionIdleTimeout | 10m |
| conductor.postgres.connection.timeout | conductor.postgres.connectionTimeout | 30s |
| conductor.postgres.transaction.isolation.level | conductor.postgres.transactionIsolationLevel | "" |
| conductor.postgres.autocommit | conductor.postgres.autoCommit | false |
| conductor.taskdef.cache.refresh.time.seconds | conductor.postgres.taskDefCacheRefreshInterval | 60s |

### `postgres-persistence` module (v3.0.5+):

| Old | New |
| --- | --- |
| jdbc.url | spring.datasource.url | 
| jdbc.username | spring.datasource.username |
| jdbc.password | spring.datasource.password |
| flyway.enabled | spring.flyway.enabled |
| flyway.table | spring.flyway.table |
| conductor.postgres.connection.pool.size.max | spring.datasource.hikari.maximum-pool-size |
| conductor.postgres.connection.pool.idle.min | spring.datasource.hikari.minimum-idle |
| conductor.postgres.connection.lifetime.max | spring.datasource.hikari.max-lifetime |
| conductor.postgres.connection.idle.timeout | spring.datasource.hikari.idle-timeout |
| conductor.postgres.connection.timeout | spring.datasource.hikari.connection-timeout |
| conductor.postgres.transaction.isolation.level | spring.datasource.hikari.transaction-isolation |
| conductor.postgres.autocommit | spring.datasource.hikari.auto-commit |
| conductor.taskdef.cache.refresh.time.seconds | conductor.postgres.taskDefCacheRefreshInterval |

* for more properties and default values: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#application-properties.data.spring.datasource.hikari

### `redis-lock` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.redis.locking.server.type | conductor.redis-lock.serverType | single |
| workflow.redis.locking.server.address | conductor.redis-lock.serverAddress | redis://127.0.0.1:6379 |
| workflow.redis.locking.server.password | conductor.redis-lock.serverPassword | null |
| workflow.redis.locking.server.master.name | conductor.redis-lock.serverMasterName | master |
| workflow.decider.locking.namespace | conductor.redis-lock.namespace | "" |
| workflow.decider.locking.exceptions.ignore | conductor.redis-lock.ignoreLockingExceptions | false |

### `redis-persistence` module:

| Old | New | Default |
| --- | --- | --- |
| EC2_REGION | conductor.redis.dataCenterRegion | us-east-1 |
| EC2_AVAILABILITY_ZONE | conductor.redis.availabilityZone | us-east-1c |
| workflow.dynomite.cluster | _removed_ |
| workflow.dynomite.cluster.name | conductor.redis.clusterName | "" |
| workflow.dynomite.cluster.hosts | conductor.redis.hosts | null |
| workflow.namespace.prefix | conductor.redis.workflowNamespacePrefix | null |
| workflow.namespace.queue.prefix | conductor.redis.queueNamespacePrefix | null |
| workflow.dyno.keyspace.domain | conductor.redis.keyspaceDomain | null |
| workflow.dynomite.connection.maxConnsPerHost | conductor.redis.maxConnectionsPerHost | 10 |
| workflow.dynomite.connection.max.retry.attempt | conductor.redis.maxRetryAttempts | 0 |
| workflow.dynomite.connection.max.timeout.exhausted.ms | conductor.redis.maxTimeoutWhenExhausted | 800ms |
| queues.dynomite.nonQuorum.port | conductor.redis.queuesNonQuorumPort | 22122 |
| workflow.dyno.queue.sharding.strategy | conductor.redis.queueShardingStrategy | roundRobin |
| conductor.taskdef.cache.refresh.time.seconds | conductor.redis.taskDefCacheRefreshInterval | 60s |
| workflow.event.execution.persistence.ttl.seconds | conductor.redis.eventExecutionPersistenceTTL | 60s |

### `zookeeper-lock` module:

| Old | New | Default |
| --- | --- | --- |
| workflow.zookeeper.lock.connection | conductor.zookeeper-lock.connectionString | localhost:2181 |
| workflow.zookeeper.lock.sessionTimeoutMs | conductor.zookeeper-lock.sessionTimeout | 60000ms |
| workflow.zookeeper.lock.connectionTimeoutMs | conductor.zookeeper-lock.connectionTimeout | 15000ms |
| workflow.decider.locking.namespace | conductor.zookeeper-lock.namespace | "" |

### Component configuration:

| Old | New | Default |
| --- | --- | --- |
| db | conductor.db.type | "" | 
| workflow.indexing.enabled | conductor.indexing.enabled | true |
| conductor.disable.async.workers | conductor.system-task-workers.enabled | true |
| decider.sweep.disable | conductor.workflow-reconciler.enabled | true |
| conductor.grpc.server.enabled | conductor.grpc-server.enabled | false |
| workflow.external.payload.storage | conductor.external-payload-storage.type | dummy |
| workflow.default.event.processor.enabled | conductor.default-event-processor.enabled | true |
| workflow.events.default.queue.type | conductor.default-event-queue.type | sqs |
| workflow.status.listener.type | conductor.workflow-status-listener.type | stub |
| - | conductor.task-status-listener.type | stub |
| workflow.decider.locking.server | conductor.workflow-execution-lock.type | noop_lock |
|  |  |  |
| workflow.default.event.queue.enabled | conductor.event-queues.default.enabled | true |
| workflow.sqs.event.queue.enabled | conductor.event-queues.sqs.enabled | false |
| workflow.amqp.event.queue.enabled | conductor.event-queues.amqp.enabled | false |
| workflow.nats.event.queue.enabled | conductor.event-queues.nats.enabled | false |
| workflow.nats_stream.event.queue.enabled | conductor.event-queues.nats-stream.enabled | false |
|  |  |  |
| - | conductor.metrics-logger.enabled | false |
| - | conductor.metrics-prometheus.enabled | false |
| - | conductor.metrics-datadog.enable | false |
| - | conductor.metrics-datadog.api-key | |

