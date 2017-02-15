Conductor tasks executed by remote workers communicates over HTTP endpoints to poll for the task and updates the status of the execution.

Conductor provides a framework to poll for tasks, manage the execution thread and update the status of the execution back to the server.  The framework provides libraries in Java and Python.  Other langugage support can be added by using the HTTP endpoints for task management.

### Java

1. Implement [Worker](https://github.com/Netflix/conductor/blob/dev/client/src/main/java/com/netflix/conductor/client/worker/Worker.java) interface to implement the task.
2. Use [WorkflowTaskCoordinator](https://github.com/Netflix/conductor/blob/dev/client/src/main/java/com/netflix/conductor/client/task/WorkflowTaskCoordinator.java) to register the worker(s) and initialize the polling loop. 

	* [Sample Worker Implementation](https://github.com/Netflix/conductor/blob/dev/client/src/test/java/com/netflix/conductor/client/sample/SampleWorker.java)
	* [Example](https://github.com/Netflix/conductor/blob/dev/client/src/test/java/com/netflix/conductor/client/sample/Main.java)

### Python
[https://github.com/Netflix/conductor/tree/dev/client/python](https://github.com/Netflix/conductor/tree/dev/client/python)

Follow the example as documented in the readme or take a look at [kitchensink_workers.py](https://github.com/Netflix/conductor/blob/dev/client/python/kitchensink_workers.py)

!!!warning
	Python client is under development is not production battle tested.  We encourage you to test it out and let us know the feedback.  Pull Requests with fixes or enhancements are welcomed!