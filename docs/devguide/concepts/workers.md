# Workers
A worker is responsible for executing a task. Workers can be implemented in any language, and Conductor provides a polyglot set of worker frameworks that provide features such as polling threads, metrics and server communication that makes creating workers easy.

Each worker embodies the Microservice design pattern and follows certain basic principles:

1. Workers are stateless and do not implement a workflow specific logic.  
2. Each worker executes a very specific task and produces well defined output given specific inputs.
3. Workers are meant to be idempotent (or should handle cases where the task that partially executed gets rescheduled due to timeouts etc.)
4. Workers do not implement the logic to handle retries etc, that is taken care by the Conductor server.
 
Conductor maintains a registry of worker tasks.  A task MUST be registered before being used in a workflow. This can be done by creating and saving a **Task Definition**.