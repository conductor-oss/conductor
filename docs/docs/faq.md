# Frequently asked Questions

### How do you schedule a task to be put in the queue after some time (e.g. 1 hour, 1 day etc.)

After polling for the task update the status of the task to `IN_PROGRESS` and set the `callbackAfterSeconds` value to the desired time.  The task will remain in the queue until the specified second before worker polling for it will receive it again.

If there is a timeout set for the task, and the `callbackAfterSeconds` exceeds the timeout value, it will result in task being TIMED_OUT.
	

### How long can a workflow be in running state?  Can I have a workflow that keeps running for days or months?

Yes.  As long as the timeouts on the tasks are set to handle long running workflows, it will stay in running state.


### My workflow fails to start with missing task error

Ensure all the tasks are registered via `/metadata/taskdefs` APIs.  Add any missing task definition (as reported in the error) and try again.


### Where does my worker run?  How does conductor run my tasks?

Conductor does not run the workers.  When a task is scheduled, it is put into the queue maintained by Conductor.  Workers are required to poll for tasks using `/tasks/poll` API at periodic interval, execute the business logic for the task and report back the results using `POST /tasks` API call. 
Conductor, however will run [system tasks](/configuration/systask.html) on the Conductor server.


### How can I schedule workflows to run at a specific time?

Netflix Conductor itself does not provide any scheduling mechanism.  But there is a community project [_Schedule Conductor Workflows_](https://github.com/jas34/scheduledwf) which provides workflow scheduling capability as a pluggable module as well as workflow server.
Other way is you can use any of the available scheduling systems to make REST calls to Conductor to start a workflow.  Alternatively, publish a message to a supported eventing system like SQS to trigger a workflow.  
More details about [eventing](/configuration/eventhandlers.html).


### How do I setup Dynomite cluster?

Visit Dynomite's [Github page](https://github.com/Netflix/dynomite) to find details on setup and support mechanism.


### Can I use conductor with Ruby / Go / Python?

Yes.  Workers can be written any language as long as they can poll and update the task results via HTTP endpoints.

Conductor provides frameworks for Java and Python to simplify the task of polling and updating the status back to Conductor server.

**Note:** Python and Go clients have been contributed by the community.


### How can I get help with Dynomite?

Visit Dynomite's [Github page](https://github.com/Netflix/dynomite) to find details on setup and support mechanism.


### My workflow is running and the task is SCHEDULED but it is not being processed.

Make sure that the worker is actively polling for this task. Navigate to the `Task Queues` tab on the Conductor UI and select your task name in the search box. Ensure that `Last Poll Time` for this task is current.

In Conductor 3.x, ```conductor.redis.availabilityZone``` defaults to ```us-east-1c```.  Ensure that this matches where your workers are, and that it also matches```conductor.redis.hosts```.

### How do I configure a notification when my workflow completes or fails?

When a workflow fails, you can configure a "failure workflow" to run using the```failureWorkflow``` parameter. By default, three parameters are passed:

* reason
* workflowId: use this to pull the details of the failed workflow.
* failureStatus

You can also use the Workflow Status Listener: 

* Set the workflowStatusListenerEnabled field in your workflow definition to true which enables [notifications](/configuration/workflowdef.html#workflow-notifications).
* Add a custom implementation of the Workflow Status Listener. Refer [this](/extend.html#workflow-status-listener).
* This notification can be implemented in such a way as to either send a notification to an external system or to send an event on the conductor queue to complete/fail another task in another workflow as described [here](/configuration/eventhandlers.html).

Refer to this [documentation](/configuration/workflowdef.html#workflow-notifications) to extend conductor to send out events/notifications upon workflow completion/failure. 




### I want my worker to stop polling and executing tasks when the process is being terminated. (Java client)

In a `PreDestroy` block within your application, call the `shutdown()` method on the `TaskRunnerConfigurer` instance that you have created to facilitate a graceful shutdown of your worker in case the process is being terminated.


### Can I exit early from a task without executing the configured automatic retries in the task definition?

Set the status to `FAILED_WITH_TERMINAL_ERROR` in the TaskResult object within your worker. This would mark the task as FAILED and fail the workflow without retrying the task as a fail-fast mechanism.
