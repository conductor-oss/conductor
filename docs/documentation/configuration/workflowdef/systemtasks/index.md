# System Tasks

System Tasks (Workers) are built-in tasks that are general purpose and re-usable. They run on the Conductor servers.
Such tasks allow you to get started without having to write custom workers.

| Task                  | Description                          | Use Case                                                                |
| :-------------------- | :----------------------------------- | :---------------------------------------------------------------------- |
| Event Publishing      | [Event Task](event-task.md)          | External eventing system integration. e.g. amqp, sqs, nats              |
| HTTP                  | [HTTP Task](http-task.md)            | Invoke any HTTP(S) endpoints                                            |
| Inline Code Execution | [Inline Task](inline-task.md)        | Execute arbitrary lightweight javascript code                           |
| JQ Transform          | [JQ Task](json-jq-transform-task.md) | Use [JQ](https://github.com/stedolan/jq) to transform task input/output |
| Kafka Publish         | [Kafka Task](kafka-publish-task.md)  | Publish messages to Kafka                                               |
| Wait                  | [Wait Task](wait-task.md)            | Block until resolved                                                    |
