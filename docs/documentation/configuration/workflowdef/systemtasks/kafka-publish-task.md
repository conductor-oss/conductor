# Kafka Publish Task
```json
"type" : "KAFKA_PUBLISH"
```

The Kafka Publish task (`KAFKA_PUBLISH`) is used to push messages to another microservice via Kafka.

## Task parameters
The task expects a field named `kafka_request` as part of the task's `inputParameters`.

Use these parameters inside `inputParameters` in the Kafka Publish task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.kafka_request | KafkaRequest | JSON object containing the bootstrap server, message, and more. | Required. |
| inputParameters.kafka_request.bootStrapServers | String        | The bootstrap server for connecting to the Kafka cluster.             | Required.     |
| inputParameters.kafka_request.topic            | String        | The topic to publish the message to.                                  | Required.     |
| inputParameters.kafka_request.value            | Any        | The message to publish.                                         | Required.     |
| inputParameters.kafka_request.key              | String        | The Kafka message key. Messages with the same key will be sent to the same topic partition.             | Optional.     |
| inputParameters.kafka_request.keySerializer    | String (enum) | The serializer used for serializing the message key. The default is `StringSerializer`. Supported values: <ul><li>`org.apache.kafka.common.serialization.IntegerSerializer`</li> <li>`org.apache.kafka.common.serialization.LongSerializer`</li> <li>`org.apache.kafka.common.serialization.StringSerializer`</li></ul> | Optional.     |
| inputParameters.kafka_request.headers          | Map[String, Any]  | Any additional headers to be sent along with the Kafka message.                     | Optional.     |
| inputParameters.kafka_request.requestTimeoutMs | Integer     | The request timeout in milliseconds while awaiting a response.          | Optional.   |
| inputParameters.kafka_request.maxBlockMs       | Integer     | The maximum blocking time while publishing to Kafka.                  | Optional.   |

## JSON configuration

Here is the task configuration for a Kafka Publish task.

```json
{
  "name": "kafka",
  "taskReferenceName": "kafka_ref",
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

## Output

The task transitions to COMPLETED if the message has been successfully published to the Kafka queue, or marked as FAILED if the message could not be published.