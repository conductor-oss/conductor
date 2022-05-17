
# High Level Steps
Steps required for a new workflow to be registered and get executed

1. Define task definitions used by the workflow. 
2. Create the workflow definition
3. Create task worker(s) that polls for scheduled tasks at regular interval

### Trigger Workflow Execution

```
POST /workflow/{name}
{
	... //json payload as workflow input
}
```

### Polling for a task

```
GET /tasks/poll/batch/{taskType}
```
	
### Update task status
	
```
POST /tasks
{
	"outputData": {
        "encodeResult":"success",
        "location": "http://cdn.example.com/file/location.png"
        //any task specific output
     },
     "status": "COMPLETED"
}
```
