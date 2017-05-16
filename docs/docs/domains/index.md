## Task Domains
Task domains helps support task development. The idea is same “task definition” can be implemented in different “domains”. A domain some arbitrary name that the developer controls. So when the workflow is started, the caller can specify, out of all the tasks in the workflow, which tasks need to run in a specific domain, this domain is then used to poll for task on the client side to execute it.

As an example if a workflow (WF1) has 3 tasks T1, T2, T3. The workflow is deployed and working fine, which means there are T2 workers polling and executing. If you modify T2 and run it locally there is no guarantee that your modified T2 worker will get the task that you are looking for as it coming from the general T2 queue. “Task Domain” feature solves this problem by splitting the T2 queue by domains, so when the app polls for task T2 in a specific domain, it get the correct task.

## How to use Task Domains
### Change the poll call
The poll call must now specify the domain. 

#### Java Client
If you are using the java client then a simple property change will force  WorkflowTaskCoordinator to pass the domain to the poller.
```
	conductor.worker.T2.domain=mydomain // Task T2 needs to poll 
```
#### REST call
`GET /tasks/poll/batch/T2?workerid=myworker&domain=mydomain`
`GET /tasks/poll/T2?workerid=myworker&domain=mydomain`

### Change the start workflow call
When starting the workflow, make sure the task to domain mapping is passes

#### Java Client
```
	Map<String, Object> input = new HashMap<>();
	input.put("wf_input1", "one”);

	Map<String, String> taskToDomain = new HashMap<>();
	taskToDomain.put("T2", "mydomain");
	
	StartWorkflowRequest swr = new StartWorkflowRequest();
	swr.withName(“myWorkflow”)
		.withCorrelationId(“corr1”)
		.withVersion(1)
		.withInput(input)
		.withTaskToDomain(taskToDomain);
	
	wfclient.startWorkflow(swr);

```

#### REST call
`POST /workflow`

```json
{
  "name": "myWorkflow",
  "version": 1,
  “correlatond”: “corr1”
  "input": {
	"wf_input1": "one"
  },
  "taskToDomain": {
	"T2": "mydomain"
  }
}

```

