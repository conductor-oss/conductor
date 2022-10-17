# Worker Definition

A worker is responsible for executing a task.  Operator and System tasks are handled by the Conductor server, while user 
defined tasks needs to have a worker created that awaits the work to be scheduled by the server for it to be executed.
Workers can be implemented in any language, and Conductor provides support for Java, Golang and Python worker framework that provides features such as 
polling threads, metrics and server communication that makes creating workers easy.

Each worker embodies Microservice design pattern and follows certain basic principles:

1. Workers are stateless and do not implement a workflow specific logic.  
2. Each worker executes a very specific task and produces well defined output given specific inputs.
3. Workers are meant to be idempotent (or should handle cases where the task that partially executed gets rescheduled due to timeouts etc.)
4. Workers do not implement the logic to handle retries etc, that is taken care by the Conductor server.
 
