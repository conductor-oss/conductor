## Task Domains
Task domains helps support task development. The idea is same “task definition” can be implemented in different “domains”. A domain is some arbitrary name that the developer controls. So when the workflow is started, the caller can specify, out of all the tasks in the workflow, which tasks need to run in a specific domain, this domain is then used to poll for task on the client side to execute it.  

As an example if a workflow (WF1) has 3 tasks T1, T2, T3. The workflow is deployed and working fine, which means there are T2 workers polling and executing. If you modify T2 and run it locally there is no guarantee that your modified T2 worker will get the task that you are looking for as it coming from the general T2 queue. “Task Domain” feature solves this problem by splitting the T2 queue by domains, so when the app polls for task T2 in a specific domain, it get the correct task.

When starting a workflow multiple domains can be specified as a fall backs, for example "domain1,domain2". Conductor keeps track of last polling time for each task, so in this case it checks if the there are any active workers for "domain1" then the task is put in "domain1", if not then the same check is done for the next domain in sequence "domain2" and so on.

If no workers are active for the domains provided:

- If `NO_DOMAIN` is provided as last token in list of domains, then no domain is set.
- Else, task will be added to last inactive domain in list of domains, hoping that workers would soon be available for that domain.

Also, a `*` token can be used to apply domains for all tasks. This can be overridden by providing task specific mappings along with `*`. 

For example, the below configuration:

```json
"taskToDomain": {
  "*": "mydomain",
  "some_task_x":"NO_DOMAIN",
  "some_task_y": "someDomain, NO_DOMAIN",
  "some_task_z": "someInactiveDomain1, someInactiveDomain2"
}
```

- puts `some_task_x` in default queue (no domain).
- puts `some_task_y` in `someDomain` domain, if available or in default otherwise.
- puts `some_task_z` in `someInactiveDomain2`, even though workers are not available yet.
- and puts all other tasks in `mydomain` (even if workers are not available).


<b>Note</b> that this "fall back" type domain strings can only be used when starting the workflow, when polling from the client only one domain is used. Also, `NO_DOMAIN` token should be used last.

## How to use Task Domains
### Change the poll call
The poll call must now specify the domain. 

#### Java Client
If you are using the java client then a simple property change will force  TaskRunnerConfigurer to pass the domain to the poller.
```
	conductor.worker.T2.domain=mydomain //Task T2 needs to poll for domain "mydomain"
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
	
	// Other options ...
	// taskToDomain.put("*", "mydomain, NO_DOMAIN")
	// taskToDomain.put("T2", "mydomain, fallbackDomain1, fallbackDomain2")
	
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
  "correlatonId": "corr1"
  "input": {
	"wf_input1": "one"
  },
  "taskToDomain": {
	"*": "mydomain",
	"some_task_x":"NO_DOMAIN",
    "some_task_y": "someDomain, NO_DOMAIN"
  }
}

```

