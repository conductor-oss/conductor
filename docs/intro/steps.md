**Steps required for a new workflow to be registered and get executed:**

1. Define task definitions used by the workflow.
2. Create the workflow definition
3. Create task worker(s) that polls for scheduled tasks at regular interval
	
	```
	GET /tasks/poll/batch/{taskType}
	```
	
	Update task status:
	
	```
	POST /tasks
	{
		... //json payload with task status
	}
	```
	
4. Use the REST endpoint to trigger the workflow execution

	```
	POST /workflow/{name}
	{
		... //json payload as workflow input
	}
	```
