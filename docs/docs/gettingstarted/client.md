# Using the Client
Conductor tasks that are executed by remote workers communicate over HTTP endpoints/gRPC to poll for the task and update the status of the execution.

## Client APIs
Conductor provides the following java clients to interact with the various APIs

| Client          | Usage                                                                     |
|-----------------|---------------------------------------------------------------------------|
| Metadata Client | Register / Update workflow and task definitions                           |
| Workflow Client | Start a new workflow / Get execution status of a workflow                 |
| Task Client     | Poll for task / Update task result after execution / Get status of a task |

## SDKs

* [Clojure](/how-tos/clojure-sdk.html)
* [C#](/how-tos/csharp-sdk.html)
* [Go](/how-tos/go-sdk.html)
* [Java](/how-tos/java-sdk.html)
* [Python](/how-tos/python-sdk.html)

The non-Java Conductor SDKs are hosted on a separate GitHub repository: [github.com/conductor-sdk](https://github.com/conductor-sdk).  Contributions from the community are encouraged!
