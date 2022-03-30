---
sidebar_position: 13
---

# Kafka Publish Task
```json
"type" : "KAFKA_PUBLISH"
```

### Introduction

A Kafka Publish task is used to push messages to another microservice via Kafka.

### Configuration
The task expects an input parameter named ```kafka_request``` as part of the task's input with the following details:

| name             | description                                                                                                                                                                                                                                                                                                                  |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| bootStrapServers | bootStrapServers for connecting to given kafka.                                                                                                                                                                                                                                                                              |
| key              | Key to be published                                                                                                                                                                                                                                                                                                          |
| keySerializer    | Serializer used for serializing the key published to kafka.  One of the following can be set : <br/> 1. org.apache.kafka.common.serialization.IntegerSerializer<br/>2. org.apache.kafka.common.serialization.LongSerializer<br/>3. org.apache.kafka.common.serialization.StringSerializer. <br/>Default is String serializer |
| value            | Value published to kafka                                                                                                                                                                                                                                                                                                     |
| requestTimeoutMs | Request timeout while publishing to kafka. If this value is not given the value is read from the property `kafka.publish.request.timeout.ms`. If the property is not set the value defaults to 100 ms                                                                                                                        |
| maxBlockMs       | maxBlockMs while publishing to kafka. If this value is not given the value is read from the property `kafka.publish.max.block.ms`. If the property is not set the value defaults to 500 ms                                                                                                                                   |
| headers          | A map of additional kafka headers to be sent along with the request.                                                                                                                                                                                                                                                         |
| topic            | Topic to publish                                                                                                                                                                                                                                                                                                             |

### Examples

Sample Task 


```json
{
  "name": "call_kafka",
  "taskReferenceName": "call_kafka",
  "inputParameters": {
    "kafka_request": {
      "topic": "userTopic",
      "value": "Message to publish",
      "bootStrapServers": "localhost:9092",
      "headers": {
    "x-Auth":"Auth-key"    
      },
      "key": "123",
      "keySerializer": "org.apache.kafka.common.serialization.IntegerSerializer"
    }
  },
  "type": "KAFKA_PUBLISH"
}
```

The task expects an input parameter named `"kafka_request"` as part
of the task's input with the following details:

1. `"bootStrapServers"` - bootStrapServers for connecting to given kafka.
2. `"key"` - Key to be published.
3. `"keySerializer"` - Serializer used for serializing the key published to kafka. 
One of the following can be set :
a. org.apache.kafka.common.serialization.IntegerSerializer
b. org.apache.kafka.common.serialization.LongSerializer
c. org.apache.kafka.common.serialization.StringSerializer.
Default is String serializer.
4. `"value"` - Value published to kafka
5. `"requestTimeoutMs"` - Request timeout while publishing to kafka. 
If this value is not given the value is read from the property 
kafka.publish.request.timeout.ms. If the property is not set the value
defaults to 100 ms.
6. `"maxBlockMs"` - maxBlockMs while publishing to kafka. If this value is
not given the value is read from the property kafka.publish.max.block.ms.
If the property is not set the value defaults to 500 ms.
7. `"headers"` - A map of additional kafka headers to be sent along with
the request.
8. `"topic"` - Topic to publish.

The producer created in the kafka task is cached. By default
the cache size is 10 and expiry time is 120000 ms. To change the
defaults following can be modified 
kafka.publish.producer.cache.size,
kafka.publish.producer.cache.time.ms respectively.

#### Kafka Task Output

Task status transitions to `COMPLETED`.

The task is marked as `FAILED` if the message could not be published to
the Kafka queue.
