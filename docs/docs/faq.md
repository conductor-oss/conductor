

#### How do you schedule a task to be put in the queue after some time (e.g. 1 hour, 1 day etc.)

After polling for the task update the status of the task to `IN_PROGRESS` and set the `callbackAfterSeconds` value to the desired time.  The task will remain in the queue until the specified second before worker polling for it will receive it again.

If there is a timeout set for the task, and the `callbackAfterSeconds` exceeds the timeout value, it will result in task being TIMED_OUT.
	
#### How long can a workflow be in running state?  Can I have a workflow that keeps running for days or months?

Yes.  As long as the timeouts on the tasks are set to handle long running workflows, it will stay in running state.

#### My workflow fails to start with missing task error

Ensure all the tasks are registered via `/metadata/taskdefs` APIs.  Add any missing task definition (as reported in the error) and try again.

#### Where does my worker run?  How does conductor run my tasks?

Conductor does not run the workers.  When a task is scheduled, it is put into the queue maintained by Conductor.  Workers are required to poll for tasks using `/tasks/poll` API at periodic interval, execute the business logic for the task and report back the results using `POST /tasks` API call. 
Conductor, however will run [system tasks](/metadata/systask/) on the Conductor server.

#### How can I schedule workflows to run at a specific time?

Conductor does not provide any scheduling mechanism.  But you can use any of the available scheduling systems to make REST calls to Conductor to start a workflow.  Alternatively, publish a message to a supported eventing system like SQS to trigger a workflow.  
More details about [eventing](/events).

#### How do I setup Dynomite cluster?

Visit Dynomite's github page.  [https://github.com/Netflix/dynomite](https://github.com/Netflix/dynomite) to find details on setup and support mechanism.

#### Can I use conductor with Ruby / Go / Python?

Yes.  Workers can be written any language as long as they can poll and update the task results via HTTP endpoints.

Conductor provides frameworks for Java and Python to simplify the task of polling and updating the status back to Conductor server.

**Note:** Python client is currently in development and not battle tested for production use cases. 

#### How can I get help with Dynomite?

Visit Dynomite's github page.  [https://github.com/Netflix/dynomite](https://github.com/Netflix/dynomite) to find details on setup and support mechanism.

