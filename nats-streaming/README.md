# Event Queue
## Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact | Description |
| ----------- | ----------- | 
| conductor-amqp | Support for integration with AMQP  |
| conductor-nats | Support for integration with NATS  |

## Modules
### AMQP
https://www.amqp.org/

Provides ability to publish and consume messages from AMQP compatible message broker.

#### Configuration
(Default values shown below)
```properties
conductor.event-queues.amqp.enabled=true
conductor.event-queues.amqp.hosts=localhost
conductor.event-queues.amqp.port=5672
conductor.event-queues.amqp.username=guest
conductor.event-queues.amqp.password=guest
conductor.event-queues.amqp.virtualhost=/
conductor.event-queues.amqp.useSslProtocol=false
#milliseconds
conductor.event-queues.amqp.connectionTimeout=60000
conductor.event-queues.amqp.useExchange=true
conductor.event-queues.amqp.listenerQueuePrefix=
```
For advanced configurations, see [AMQPEventQueueProperties](amqp/src/main/java/com/netflix/conductor/contribs/queue/amqp/config/AMQPEventQueueProperties.java)

### NATS
https://nats.io/

Provides ability to publish and consume messages from NATS queues
#### Configuration
(Default values shown below)
```properties
conductor.event-queues.nats.enabled=true
conductor.event-queues.nats-stream.clusterId=test-cluster
conductor.event-queues.nats-stream.durableName=
conductor.event-queues.nats-stream.url=nats://localhost:4222
conductor.event-queues.nats-stream.listenerQueuePrefix=
```
