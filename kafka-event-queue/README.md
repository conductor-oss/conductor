# Event Queue
## Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact | Description |
| ----------- | ----------- |
| conductor-kafka-event-queue | Support for integration with Kafka and consume events from it. |

## Modules
### Kafka
https://kafka.apache.org/

Provides ability to publish and consume messages from Kafka
#### Configuration
(Default values shown below)
```properties
conductor.event-queues.kafka.enabled=true
conductor.event-queues.kafka.bootstrap-servers=kafka:29092
conductor.event-queues.kafka.groupId=conductor-consumers
conductor.event-queues.kafka.topic=conductor-event
conductor.event-queues.kafka.dlqTopic=conductor-dlq
conductor.event-queues.kafka.pollTimeDuration=100
```
