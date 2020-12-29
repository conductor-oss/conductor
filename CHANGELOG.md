DynoQueueDAO - removed deprecated Constructors int getLongPollTimeoutInMS() - removed deprecated Worker method in client

workflow.sqs.event.queue.enabled workflow.amqp.event.queue.enabled workflow.nats.event.queue.enabled
workflow.nats_stream.event.queue.enabled

workflow.executor.service.max.threads=50(default)

workflow.events.default.queue.type=sqs (default)/amqp

(Fixed) workflow.listener.queue.prefix

workflow.status.listener.type=stub(default)/archive/queue_publisher

conductor.metrics-logger.enabled

HTTP task - removed OAuth support (Create a task for OAuth2 support)

Removed deprecated API - /queue/requeue from /tasks

Upgraded protobuf-java to 3.13.0 Upgraded grpc-protobuf to 1.33.+ Renamed DynoProxy to JedisProxy Removed support for
EmbeddedElasticSearch

Ignored a flaky test class - LocalOnlyLockTest. Test Harness module uses TestContainers for MySql,Postgres &
Elasticsearch

Modified properties in the `azureblob-storage` module:

| Old | New |
| --- | --- |
| workflow.external.payload.storage.azure_blob.connection_string | conductor.external-payload-storage.azureblob.connectionString |
| workflow.external.payload.storage.azure_blob.container_name | conductor.external-payload-storage.azureblob.containerName |
| workflow.external.payload.storage.azure_blob.endpoint | conductor.external-payload-storage.azureblob.endpoint |
| workflow.external.payload.storage.azure_blob.sas_token | conductor.external-payload-storage.azureblob.sasToken |
| workflow.external.payload.storage.azure_blob.signedurlexpirationseconds | conductor.external-payload-storage.azureblob.signedUrlExpirationSeconds |
| workflow.external.payload.storage.azure_blob.workflow_input_path | conductor.external-payload-storage.azureblob.workflowInputPath |
| workflow.external.payload.storage.azure_blob.workflow_output_path | conductor.external-payload-storage.azureblob.workflowOutputPath |
| workflow.external.payload.storage.azure_blob.task_input_path | conductor.external-payload-storage.azureblob.taskInputPath |
| workflow.external.payload.storage.azure_blob.task_output_path | conductor.external-payload-storage.azureblob.taskOutputPath |

Modified properties in the `cassandra-persistence` module:

| Old | New |
| --- | --- |
| workflow.cassandra.host | conductor.cassandra.hostAddress |
| workflow.cassandra.port | conductor.cassandra.port |
| workflow.cassandra.cluster | conductor.cassandra.cluster |
| workflow.cassandra.keyspace | conductor.cassandra.keyspace |
| workflow.cassandra.shard.size | conductor.cassandra.shardSize |
| workflow.cassandra.replication.strategy | conductor.cassandra.replicationStrategy |
| workflow.cassandra.replication.factor.key | conductor.cassandra.replicationFactorKey |
| workflow.cassandra.replication.factor.value | conductor.cassandra.replicationFactorValue |
| workflow.cassandra.read.consistency.level | conductor.cassandra.readConsistencyLevel |
| workflow.cassandra.write.consistency.level | conductor.cassandra.writeConsistencyLevel |
| conductor.taskdef.cache.refresh.time.seconds | conductor.cassandra.taskDefCacheRefreshTimeSecs |
| conductor.eventhandler.cache.refresh.time.seconds | conductor.cassandra.eventHandlerCacheRefreshTimeSecs |
| workflow.event.execution.persistence.ttl.seconds | conductor.cassandra.eventExecutionPersistenceTTLSecs |

Modified properties in the `contribs` module:

| Old | New |
| --- | --- |
| workflow.archival.ttl.seconds | conductor.workflow-status-listener.archival.ttlSeconds |
| workflow.archival.delay.queue.worker.thread.count | conductor.workflow-status-listener.archival.delayQueueWorkerThreadCount |
| workflow.archival.delay.seconds | conductor.workflow-status-listener.archival.delaySeconds |
|  |  |
| workflowstatuslistener.publisher.success.queue | conductor.workflow-status-listener.queue-publisher.successQueue |
| workflowstatuslistener.publisher.failure.queue | conductor.workflow-status-listener.queue-publisher.failureQueue |
|  |  |
| com.netflix.conductor.contribs.metrics.LoggingMetricsModule.reportPeriodSeconds | conductor.metrics-logger.reportPeriodSeconds |
|  |  |
| workflow.event.queues.amqp.batchSize | conductor.event-queues.amqp.batchSize |
| workflow.event.queues.amqp.pollTimeInMs | conductor.event-queues.amqp.pollTimeMs |
| workflow.event.queues.amqp.hosts | conductor.event-queues.amqp.hosts |
| workflow.event.queues.amqp.username | conductor.event-queues.amqp.username |
| workflow.event.queues.amqp.password | conductor.event-queues.amqp.password |
| workflow.event.queues.amqp.virtualHost | conductor.event-queues.amqp.virtualHost |
| workflow.event.queues.amqp.port | conductor.event-queues.amqp.port.port |
| workflow.event.queues.amqp.connectionTimeout | conductor.event-queues.amqp.connectionTimeout |
| workflow.event.queues.amqp.useNio | conductor.event-queues.amqp.useNio |
| workflow.event.queues.amqp.durable | conductor.event-queues.amqp.durable |
| workflow.event.queues.amqp.exclusive | conductor.event-queues.amqp.exclusive |
| workflow.event.queues.amqp.autoDelete | conductor.event-queues.amqp.autoDelete |
| workflow.event.queues.amqp.contentType | conductor.event-queues.amqp.contentType |
| workflow.event.queues.amqp.contentEncoding | conductor.event-queues.amqp.contentEncoding |
| workflow.event.queues.amqp.amqp_exchange | conductor.event-queues.amqp.exchangeType |
| workflow.event.queues.amqp.deliveryMode | conductor.event-queues.amqp.deliveryMode |
| workflow.listener.queue.useExchange | conductor.event-queues.amqp.useExchange |
| workflow.listener.queue.prefix | conductor.event-queues.amqp.listenerQueuePrefix |
|  |  |
| io.nats.streaming.clusterId | conductor.event-queues.nats-stream.clusterId |
| io.nats.streaming.durableName | conductor.event-queues.nats-stream.durableName |
| io.nats.streaming.url | conductor.event-queues.nats-stream.url |
|  |  |
| workflow.event.queues.sqs.batchSize | conductor.event-queues.sqs.batchSize |
| workflow.event.queues.sqs.pollTimeInMS | conductor.event-queues.sqs.pollTimeMs |
| workflow.event.queues.sqs.visibilityTimeoutInSeconds | conductor.event-queues.sqs.visibilityTimeoutSeconds |
| workflow.listener.queue.prefix | conductor.event-queues.sqs.listenerQueuePrefix |
| workflow.listener.queue.authorizedAccounts | conductor.event-queues.sqs.authorizedAccounts |
|  |  |
| workflow.external.payload.storage.s3.bucket | conductor.external-payload-storage.s3 |
| workflow.external.payload.storage.s3.signedurlexpirationseconds | conductor.external-payload-storage.s3 |
| workflow.external.payload.storage.s3.region | conductor.external-payload-storage.s3 |
|  |  |
| http.task.read.timeout | conductor.tasks.http.readTimeout |
| http.task.connect.timeout | conductor.tasks.http.connectTimeout |
|  |  |
| kafka.publish.request.timeout.ms | conductor.tasks.kafka-publish.requestTimeoutMs |
| kafka.publish.max.block.ms | conductor.tasks.kafka-publish.maxBlockMs |
| kafka.publish.producer.cache.size | conductor.tasks.kafka-publish.cacheSize |
| kafka.publish.producer.cache.time.ms | conductor.tasks.kafka-publish.cacheTimeMs |

Modified properties in the `core` module:

| Old | New |
| --- | --- |
| environment | _removed_ |
| STACK | conductor.app.stack |
| APP_ID | conductor.app.appId |
| workflow.executor.service.max.threads | conductor.app.executorServiceMaxThreadCount |
| decider.sweep.frequency.seconds | conductor.app.sweepFrequencySeconds |
| decider.sweep.disable | conductor.app.sweepDisabled |
| workflow.sweeper.thread.count | conductor.app.sweeperThreadCount |
| workflow.event.processor.thread.count | conductor.app.eventProcessorThreadCount |
| workflow.event.message.indexing.enabled | conductor.app.eventMessageIndexingEnabled |
| workflow.event.execution.indexing.enabled | conductor.app.eventExecutionIndexingEnabled |
| workflow.decider.locking.enabled | conductor.app.workflowExecutionLockEnabled |
| workflow.locking.lease.time.ms | conductor.app.lockLeaseTimeMs |
| workflow.locking.time.to.try.ms | conductor.app.lockTimeToTryMs |
| tasks.active.worker.lastpoll | conductor.app.activeWorkerLastPollSecs |
| task.queue.message.postponeSeconds | conductor.app.taskExecutionPostponeSeconds |
| workflow.taskExecLog.indexing.enabled | conductor.app.taskExecLogIndexingEnabled |
| async.indexing.enabled | conductor.app.asyncIndexingEnabled |
| workflow.system.task.worker.thread.count | conductor.app.systemTaskWorkerThreadCount |
| workflow.system.task.worker.callback.seconds | conductor.app.systemTaskWorkerCallbackSeconds |
| workflow.system.task.worker.poll.interval | conductor.app.systemTaskWorkerPollInterval |
| workflow.system.task.worker.executionNameSpace | conductor.app.systemTaskWorkerExecutionNamespace |
| workflow.isolated.system.task.worker.thread.count | conductor.app.isolatedSystemTaskWorkerThreadCount |
| workflow.system.task.queue.pollCount | conductor.app.systemTaskMaxPollCount |
| conductor.disable.async.workers | conductor.app.systemTaskWorkersDisabled |
| async.update.short.workflow.duration.seconds | conductor.app.asyncUpdateShortRunningWorkflowDuration |
| async.update.delay.seconds | conductor.app.asyncUpdateDelay |
| workflow.owner.email.mandatory | conductor.app.ownerEmailMandatory |
| workflow.repairservice.enabled | conductor.app.workflowRepairServiceEnabled |
| workflow.event.queue.scheduler.poll.thread.count | conductor.app.eventSchedulerPollThreadCount |
| workflow.dyno.queues.pollingInterval | conductor.app.eventQueuePollIntervalMs |
| workflow.dyno.queues.pollCount | conductor.app.eventQueuePollCount |
| workflow.dyno.queues.longPollTimeout | conductor.app.eventQueueLongPollTimeout |
| conductor.workflow.input.payload.threshold.kb | conductor.app.workflowInputPayloadSizeThresholdKB |
| conductor.max.workflow.input.payload.threshold.kb | conductor.app.maxWorkflowInputPayloadSizeThresholdKB |
| conductor.workflow.output.payload.threshold.kb | conductor.app.workflowOutputPayloadSizeThresholdKB |
| conductor.max.workflow.output.payload.threshold.kb | conductor.app.maxWorkflowOutputPayloadSizeThresholdKB |
| conductor.task.input.payload.threshold.kb | conductor.app.taskInputPayloadSizeThresholdKB |
| conductor.max.task.input.payload.threshold.kb | conductor.app.maxTaskInputPayloadSizeThresholdKB |
| conductor.task.output.payload.threshold.kb | conductor.app.taskOutputPayloadSizeThresholdKB |
| conductor.max.task.output.payload.threshold.kb | conductor.app.maxTaskOutputPayloadSizeThresholdKB |
| conductor.max.workflow.variables.payload.threshold.kb | conductor.app.maxWorkflowVariablesPayloadSizeThresholdKB |
|  |  |
| workflow.isolated.system.task.enable | conductor.app.isolatedSystemTaskEnabled |
| workflow.isolated.system.task.poll.time.secs | conductor.app.isolatedSystemTaskQueuePollIntervalSecs |
|  |  |
| workflow.task.pending.time.threshold.minutes | conductor.app.taskPendingTimeThresholdMins |
|  |  |
| workflow.monitor.metadata.refresh.counter | conductor.workflow-monitor.metadataRefreshInterval |
| workflow.monitor.stats.freq.seconds | conductor.workflow-monitor.statsFrequencySeconds |

Modified properties in the `es6-persistence` module:

| Old | New |
| --- | --- |
| workflow.elasticsearch.version | conductor.elasticsearch.version |
| workflow.elasticsearch.url | conductor.elasticsearch.url |
| workflow.elasticsearch.index.name | conductor.elasticsearch.indexName |
| workflow.elasticsearch.tasklog.index.name | conductor.elasticsearch.taskLogIndexName |
| workflow.elasticsearch.cluster.health.color | conductor.elasticsearch.clusterHealthColor |
| workflow.elasticsearch.archive.search.batchSize | conductor.elasticsearch.archiveSearchBatchSize |
| workflow.elasticsearch.index.batchSize | conductor.elasticsearch.indexBatchSize |
| workflow.elasticsearch.async.dao.worker.queue.size | conductor.elasticsearch.asyncWorkerQueueSize |
| workflow.elasticsearch.async.dao.max.pool.size | conductor.elasticsearch.asyncMaxPoolSize |
| workflow.elasticsearch.async.buffer.flush.timeout.seconds | conductor.elasticsearch.asyncBufferFlushTimeoutSecs |
| workflow.elasticsearch.index.shard.count | conductor.elasticsearch.indexShardCount |
| workflow.elasticsearch.index.replicas.count | conductor.elasticsearch.indexReplicasCount |
| tasklog.elasticsearch.query.size | conductor.elasticsearch.taskLogResultLimit |
| workflow.elasticsearch.rest.client.connectionRequestTimeout.milliseconds | conductor.elasticsearch.restClientConnectionRequestTimeoutMs |
| workflow.elasticsearch.auto.index.management.enabled | conductor.elasticsearch.autoIndexManagementEnabled |
| workflow.elasticsearch.document.type.override | conductor.elasticsearch.documentTypeOverride |

Modified properties in the `grpc-server` module:

| Old | New |
| --- | --- |
| conductor.grpc.server.port | conductor.grpc-server.port |
| conductor.grpc.server.reflectionEnabled | conductor.grpc-server.reflectionEnabled |

Modified properties in the `mysql-persistence` module:

| Old | New |
| --- | --- |
| jdbc.url | conductor.mysql.jdbcUrl |
| jdbc.username | conductor.mysql.jdbcUsername |
| jdbc.password | conductor.mysql.jdbcPassword |
| flyway.enabled | conductor.mysql.flywayEnabled |
| flyway.table | conductor.mysql.flywayTable |
| conductor.mysql.connection.pool.size.max | conductor.mysql.connectionPoolMaxSize |
| conductor.mysql.connection.pool.idle.min | conductor.mysql.connectionPoolMinIdle |
| conductor.mysql.connection.lifetime.max | conductor.mysql.connectionMaxLifetime |
| conductor.mysql.connection.idle.timeout | conductor.mysql.connectionIdleTimeout |
| conductor.mysql.connection.timeout | conductor.mysql.connectionTimeout |
| conductor.mysql.transaction.isolation.level | conductor.mysql.transactionIsolationLevel |
| conductor.mysql.autocommit | conductor.mysql.autoCommit |
| conductor.taskdef.cache.refresh.time.seconds | conductor.mysql.taskDefCacheRefreshTimeSecs |

Modified properties in the `postgres-persistence` module:

| Old | New |
| --- | --- |
| jdbc.url | conductor.postgres.jdbcUrl |
| jdbc.username | conductor.postgres.jdbcUsername |
| jdbc.password | conductor.postgres.jdbcPassword |
| flyway.enabled | conductor.postgres.flywayEnabled |
| flyway.table | conductor.postgres.flywayTable |
| conductor.postgres.connection.pool.size.max | conductor.postgres.connectionPoolMaxSize |
| conductor.postgres.connection.pool.idle.min | conductor.postgres.connectionPoolMinIdle |
| conductor.postgres.connection.lifetime.max | conductor.postgres.connectionMaxLifetime |
| conductor.postgres.connection.idle.timeout | conductor.postgres.connectionIdleTimeout |
| conductor.postgres.connection.timeout | conductor.postgres.connectionTimeout |
| conductor.postgres.transaction.isolation.level | conductor.postgres.transactionIsolationLevel |
| conductor.postgres.autocommit | conductor.postgres.autoCommit |
| conductor.taskdef.cache.refresh.time.seconds | conductor.postgres.taskDefCacheRefreshTimeSecs |

Modified properties in the `redis-lock` module:

| Old | New |
| --- | --- |
| workflow.redis.locking.server.type | conductor.redis-lock.serverType |
| workflow.redis.locking.server.address | conductor.redis-lock.serverAddress |
| workflow.redis.locking.server.password | conductor.redis-lock.serverPassword |
| workflow.redis.locking.server.master.name | conductor.redis-lock.serverMasterName |
| workflow.decider.locking.namespace | conductor.redis-lock.namespace |
| workflow.decider.locking.exceptions.ignore | conductor.redis-lock.ignoreLockingExceptions |

Modified properties in the `redis-persistence` module:

| Old | New |
| --- | --- |
| EC2_REGION | conductor.redis.dataCenterRegion |
| EC2_AVAILABILITY_ZONE | conductor.redis.availabilityZone |
| workflow.dynomite.cluster | _removed_ |
| workflow.dynomite.cluster.name | conductor.redis.clusterName |
| workflow.dynomite.cluster.hosts | conductor.redis.hosts |
| workflow.namespace.prefix | conductor.redis.workflowNamespacePrefix |
| workflow.namespace.queue.prefix | conductor.redis.queueNamespacePrefix |
| workflow.dyno.keyspace.domain | conductor.redis.keyspaceDomain |
| workflow.dynomite.connection.maxConnsPerHost | conductor.redis.maxConnectionsPerHost |
| queues.dynomite.nonQuorum.port | conductor.redis.queuesNonQuorumPort |
| workflow.dyno.queue.sharding.strategy | conductor.redis.queueShardingStrategy |
| conductor.taskdef.cache.refresh.time.seconds | conductor.redis.taskDefCacheRefreshTimeSecs |
| workflow.event.execution.persistence.ttl.seconds | conductor.redis.eventExecutionPersistenceTTLSecs |

Modified properties in the `zookeeper-lock` module:

| Old | New |
| --- | --- |
| workflow.zookeeper.lock.connection | conductor.zookeeper-lock.connectionString |
| workflow.zookeeper.lock.sessionTimeoutMs | conductor.zookeeper-lock.sessionTimeoutMs |
| workflow.zookeeper.lock.connectionTimeoutMs | conductor.zookeeper-lock.connectionTimeoutMs |
| workflow.decider.locking.namespace | conductor.zookeeper-lock.namespace |

Modified properties that are used for configuring components:

| Old | New |
| --- | --- |
| db | conductor.db.type |
| workflow.indexing.enabled | conductor.indexing.enabled |
| conductor.grpc.server.enabled | conductor.grpc-server.enabled |
| workflow.external.payload.storage | conductor.external-payload-storage.type |
| workflow.default.event.processor.enabled | conductor.default-event-processor.enabled |
| workflow.events.default.queue.type | conductor.default-event-queue.type |
| workflow.status.listener.type | conductor.workflow-status-listener.type |
| workflow.decider.locking.server | conductor.workflow-execution-lock.type |
|  |  |
| workflow.default.event.queue.enabled | conductor.event-queues.default.enabled |
| workflow.sqs.event.queue.enabled | conductor.event-queues.sqs.enabled |
| workflow.amqp.event.queue.enabled | conductor.event-queues.amqp.enabled |
| workflow.nats.event.queue.enabled | conductor.event-queues.nats.enabled |
| workflow.nats_stream.event.queue.enabled | conductor.event-queues.nats-stream.enabled |
