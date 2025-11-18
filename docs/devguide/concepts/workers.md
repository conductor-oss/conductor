# Workers
A worker is responsible for executing a task in a workflow. Each type of worker implements the core functionality of each task, handling the logic as defined in its code.

System task workers are managed by Conductor within its JVM, while `SIMPLE` task workers are to be implemented by yourself. These workers can be implemented in any programming language of your choice (Python, Java, JavaScript, C#, Go, and Clojure) and hosted anywhere outside the Conductor environment.

!!! Note
    Conductor provides a set of worker frameworks in its SDKs. These frameworks come with comes with features like polling threads, metrics, and server communication, making it easy to create custom workers.

These workers communicate with the Conductor server via REST/gRPC, allowing them to poll for tasks and update the task status. Learn more in [Architecture](../architecture/index.md).


