# System Tasks

System tasks are built-in tasks that are general purpose and reusable. These tasks run on the Conductor servers and are executed by Conductor workers, allowing you to get started without having to write custom workers.

Here are the system tasks available in Conductor OSS: 

| System Task                  | Description                          |
| :-------------------- | :----------------------------------- |
| [Event](event-task.md)       | Publish events to an external eventing system (AMQP, SQS, Kafka, and so on).              |
| [HTTP](http-task.md)         | Invoke an HTTP(S) endpoint.                                 |
| [Human](human-task.md)       | Wait for an external trigger. |
| [Inline](inline-task.md)     | Execute lightweight JavaScript code inline.                           |
| [No Op](noop-task.md)            | Do nothing. |
| [JSON JQ Transform](json-jq-transform-task.md) | Use jq to transform JSON data. |
| [Kafka Publish](kafka-publish-task.md)  | Publish messages to Kafka.                                        |
| [Wait](wait-task.md)         | Wait until a certain time has passed.         |

The following tasks are deprecated:

- Lambda