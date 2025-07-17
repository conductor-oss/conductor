# Workflow Event Listeners
Workflow Event listeners can be configured for the purpose in Conductor:
1. Remove and/or archive workflows from primary datasource (e.g. Redis) once the workflow reaches a terminal status.
2. Publish a message to a conductor queue as the workflows complete that can be used to trigger other workflows.
3. Publish workflow status changes to Kafka as it moves along its lifecycle.

## Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact | Description |
| ----------- | ----------- | 
| conductor-workflow-event-listener | Event Listeners for Conductor  |

## Backward Compatibility
Workflow event listeners are part of `conductor-contribs` binary as well - if you are already consuming contribs module as part of your build,
you do not need to add this as a separate dependency.
Core conductor-server also includes event listeners via contribs dependency.

## Configuration

### Workflow Archival
Set the following properties to archive the workflows as they complete.  
When archived, the workflow execution is removed from the primary DAO and pushed to index store (e.g. Elasticsearch)
```properties
conductor.workflow-status-listener.type=archive

#when non-zero, workflows are removed from the primary storage after the TTL expiry
conductor.workflow-status-listener.archival.ttlDuration=0

#number of threads for the background worker that processes the archival request
conductor.workflow-status-listener.archival.delayQueueWorkerThreadCount=5
```

### Queue publisher
Publish a summary of workflow [WorkflowSummary](https://github.com/conductor-oss/conductor/blob/main/common/src/main/java/com/netflix/conductor/common/run/WorkflowSummary.java) 
to a queue as the workflow gets completed.

```properties
conductor.workflow-status-listener.type=queue_publisher

#Queue for successful completion of a workflow
conductor.workflow-status-listener.queue-publisher.successQueue=_callbackSuccessQueue

#Queue for failed workflows
conductor.workflow-status-listener.queue-publisher.failureQueue=_callbackFailureQueue

#Queue for terminal state workflows (success or failed)
conductor.workflow-status-listener.queue-publisher.finalizeQueue=_callbackFinalizeQueue
```

### Kafka Publisher
Publish a summary of workflow WorkflowSummary to a Kafka topic(s) as a workflow moves through its lifecycle.

This publisher introduced some new events
- STARTED
- RERAN
- RETRIED
- PAUSED
- RESUMED
- RESTARTED
- COMPLETED (supported by queue_publisher)
- TERMINATED (supported by queue_publisher)
- FINALIZED (supported by queue_publisher)

Example of a default configuration:

```properties
conductor.workflow-status-listener.type=kafka

# Kafka Producer Configurations 
conductor.workflow-status-listener.kafka.producer[bootstrap.servers]=kafka:29092

# Serializers
conductor.workflow-status-listener.kafka.producer[key.serializer]=org.apache.kafka.common.serialization.StringSerializer
conductor.workflow-status-listener.kafka.producer[value.serializer]=org.apache.kafka.common.serialization.StringSerializer

# Reliability Settings
conductor.workflow-status-listener.kafka.producer[acks]=all
conductor.workflow-status-listener.kafka.producer[enable.idempotence]=true

# Retry sending messages if failure
conductor.workflow-status-listener.kafka.producer[retries]=5
conductor.workflow-status-listener.kafka.producer[retry.backoff.ms]=100

# Allow batching (default 0)
conductor.workflow-status-listener.kafka.producer[linger.ms]=10
conductor.workflow-status-listener.kafka.producer[batch.size]=65536
conductor.workflow-status-listener.kafka.producer[buffer.memory]=67108864

# Reduce network load
conductor.workflow-status-listener.kafka.producer[compression.type]=zstd

# Allow multiple in-flight messages (better throughput)
conductor.workflow-status-listener.kafka.producer[max.in.flight.requests.per.connection]=1

# Default Topic for All Workflow Status Events
conductor.workflow-status-listener.kafka.default-topic=workflow-status-events

```

For configuration it supports the Kafka Producer clients settings prefixed with `conductor.workflow-status-listener.kafka.producer`.

`conductor.workflow-status-listener.kafka.default-topic`  defines the default topic to use for all events.
Each event can also have its dedicated topic prefix the proeprty with `conductor.workflow-status-listener.kafka.event-topics.` followed by the event name in lowercase.

Example of using specific topics for the events:
```properties
# Custom Topics for Specific Events
conductor.workflow-status-listener.kafka.event-topics.completed=workflow-completed-events
conductor.workflow-status-listener.kafka.event-topics.terminated=workflow-terminated-events
conductor.workflow-status-listener.kafka.event-topics.started=workflow-started-events
```