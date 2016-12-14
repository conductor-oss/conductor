# Workflow Definition
A typical workflow has the following structure:

```json
{
  "name": "workflow_name",
  "description": "Description of workflow",
  "version": 1,
  "tasks": [
    {
      "name": "name_of_task",
      "taskReferenceName": "ref_name_unique_within_blueprint",
      "inputParameters": {
        "movieId": "${workflow.input.movieId}",
        "url": "${workflow.input.fileLocation}"
      },
      "type": "SIMPLE",
      ... (any other task specific parameters)
    },
    {}
    ...
  ],
  "outputParameters": {
    "encoded_url": "${encode.output.location}"
  }
}
```

A sample encoding flow

```json
{
  "name": "workflow_name",
  "description": "Description of workflow",
  "version": 1,
  "tasks": [
    {
      "name": "store_in_s3",
      "taskReferenceName": "t1",
      "inputParameters": {
        "movieId": "${workflow.input.movieId}",
        "url": "${workflow.input.fileLocation}"
      },
      "type": "SIMPLE"
    },
    {
      "name": "encode",
      "taskReferenceName": "encode",
      "inputParameters": {
        "recipe": "${workflow.input.recipe}",
        "location": "${t1.output.s3Location}"
      },
      "type": "SIMPLE"
    }
  ],
  "outputParameters": {
    "encoded_url": "${encode.output.location}"
  }
}
```
In the above definition, tasks are series of steps that are executed in sequence.

Below is the list of all the parameters used to define a workflow blueprint:

``` json
{
	"name": "Workflow Name. Used for all the operations",
	"description": "Human readable description",
	"version": 1, //defines the version of workflow blueprint. Useful for versioning
	"inputParameters": "List of required inputs to workflow",
	"outputParameters": "Map of key - value used to determine the output of the workflow",
	"failureWorkflow": "Name of another workflow to be started if this workflow fails",
	"tasks": "list of tasks that makes up this blueprint"
}
```

# Task Definition
Workflow tasks are defined within the ```tasks``` block in the blueprint.  
Each task represent either a system task (executed by the engine and used for control flow) or a user task which is executed on a remote system and communicates with the conductor server using REST APIs.

Tasks are defined using a JSON DSL.  Conductor maintain a registry of all the tasks.

``` json
{
  "name": "encode_task",
  "retryCount": 3,
  "timeoutSeconds": 1200,
  "inputKeys": [
    "sourceRequestId",
    "qcElementType"
  ],
  "outputKeys": [
    "state",
    "skipped",
    "result"
  ],
  "timeoutPolicy": "TIME_OUT_WF",
  "retryLogic": "FIXED",
  "retryDelaySeconds": 600,
  "responseTimeoutSeconds": 3600
}
```

* ```retryCount``` No. of retries to attempt when a task is marked as failure.
* ```timeoutPolicy``` Can be either of the following:
	* RETRY : Retries the task again
	* TIME_OUT_WF : Workflow is marked as TIMED_OUT and terminated
	* ALERT_ONLY : Registers a counter (task_timeout)
* ```retryLogic```
	* FIXED : Reschedule the task afer the ```retryDelaySeconds```
	* EXPONENTIAL_BACKOFF : reschedule after ```retryDelaySeconds  * attempNo```
* ```responseTimeoutSeconds``` if greater than 0, the task is rescheduled if not updated with a status after this time.  Useful when the worker polls for the task but fails to complete due to errors/network failure.
* ```inputKeys``` Set of inputs that task expects
* ```outputKeys``` Set of output that task produces upon execution
