# Event Queue

## Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact | Description |
| ----------- | ----------- |
| conductor-kafka-event-queue | Support for integration with Kafka and consume events from it. |

## Modules

### Kafka

https://kafka.apache.org/

## kafka-event-queue

Provides ability to consume messages from Kafka.

## Usage

To use it in an event handler prefix the event with `kafka` followed by the topic.

Example:

```json
{
    "name": "kafka_test_event_handler",
    "event": "kafka:conductor-event",
    "actions": [
      {
        "action": "start_workflow",
        "start_workflow": {
          "name": "workflow_triggered_by_kafka",
          "input": {
            "payload": "${payload}"
          }
        },
        "expandInlineJSON": true
      }
    ],
    "active": true
}
```

The data from the kafka event has the format:

```json
{
    "key": "key-1",
    "headers": {
        "header-1": "value1"
    },
    "payload": {
        "first": "Marcelo",
        "middle": "Billie",
        "last": "Mertz"
    }
}
```

* `key` is the key field in Kafka message.
* `headers` is the headers in the kafka message.
* `payload` is the message of the Kafka message.

To access them in the event handler use for example `"${payload}"` to access the payload property, which contains the kafka message data.

## Configuration

To enable the queue use set the following to true.

```properties
conductor.event-queues.kafka.enabled=true
```

There are is a set of shared properties these are:

```properties
# If kafka should be used with event queues like SQS or AMPQ
conductor.default-event-queue.type=kafka

# the bootstrap server ot use. 
conductor.event-queues.kafka.bootstrap-servers=kafka:29092

# The dead letter queue to use for events that had some error.
conductor.event-queues.kafka.dlq-topic=conductor-dlq

# topic prefix combined with conductor.default-event-queue.type
conductor.event-queues.kafka.listener-queue-prefix=conductor_

# The polling duration. Start at 500ms and reduce based on how your environment behaves.
conductor.event-queues.kafka.poll-time-duration=500ms
```

There are 3 clients that should be configured, there is the Consumer, responsible to consuming messages, Publisher that publishes messages to Kafka and the Admin which handles admin operations.

The supported properties for the 3 clients are the ones included in `org.apache.kafka:kafka-clients:3.5.1` for each client type.

## Consumer properties

Example of consumer settings.

```properties
conductor.event-queues.kafka.consumer.client.id=consumer-client
conductor.event-queues.kafka.consumer.auto.offset.reset=earliest
conductor.event-queues.kafka.consumer.enable.auto.commit=false
conductor.event-queues.kafka.consumer.fetch.min.bytes=1
conductor.event-queues.kafka.consumer.max.poll.records=500
conductor.event-queues.kafka.consumer.group-id=conductor-group
```

## Producer properties

Example of producer settings.

```properties
conductor.event-queues.kafka.producer.client.id=producer-client
conductor.event-queues.kafka.producer.acks=all
conductor.event-queues.kafka.producer.retries=5
conductor.event-queues.kafka.producer.batch.size=16384
conductor.event-queues.kafka.producer.linger.ms=10
conductor.event-queues.kafka.producer.compression.type=gzip
```

## Admin properties

Example of admin settings.

```properties
conductor.event-queues.kafka.admin.client.id=admin-client
conductor.event-queues.kafka.admin.connections.max.idle.ms=10000
```
