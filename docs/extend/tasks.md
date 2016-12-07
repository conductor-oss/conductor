Contrib module provides additional functionality and task that are executed on the workflow server.

# Wait Task
A wait task is implemented as a gate that remains in ```IN_PROGRESS``` state unless marked as ```COMPLETED``` or ```FAILED``` by an external trigger.
To use a wait task, set the task type as ```WAIT```

### Exernal Triggers for Wait Task

Task Resource endpoint can be used to update the status of a task to a terminate state. 

Contrib module provides SQS integration where an external system can place a message in a pre-configured queue that the server listens on.  As the messages arrive, they are marked as ```COMPLETED``` or ```FAILED```.  

#### SQS Queues
* SQS queues used by the server to update the task status can be retrieve using the following URL:

	```GET /queue```
	
* When updating the status of the task, the message needs to conform to the following spec:
	* 	Message has to be a valid JSON string.
	*  The message JSON should contain a key named ```externalId``` with the value being a JSONified string that contains the following keys:
		*  ```workflowId```: Id of the workflow
		*  ```taskRefName```: Task reference name that should be updated.
	*  Each queue represents a specific task status and tasks are marked accordingly.  e.g. message coming to a ```COMPLETED``` queue marks the task status as ```COMPLETED```.
	*  Tasks' output is updated with the message.
*  Example:

	```
	{
	  "some_key": "valuex",
	  "externalId": "{\"taskRefName\":\"TASK_REFERENCE_NAME\",\"workflowId\":\"WORKFLOW_ID\"}"
	}
	```

# HTTP
An HTTP task is used to make calls to another microservice over HTTP.
The task expects an input parameter named ```http_request``` as part of the task's input with the following details:

```
uri: URI for the service.  Can be a partial when using vipAddress or includes the server address.

method: HTTP method.  One of the GET, PUT, POST, DELETE, OPTIONS, HEAD
	
accept: Accpet header as required by server.

contentType: Content Type - supported types are text/plain, text/html and, application/json
	
headers: A map of additional http headers to be sent along with the request.

body:  Request body.

vipAddress: When using discovery based service URLs. A custom 
```
**Examples**

Task Input payload using vipAddress

```json
{
  "http_request": {
    "vipAddress": "examplevip-prod",
    "uri": "/",
    "method": "GET",
    "accept": "text/plain"
  }
}
```
Task Input using an absolute URL

```json
{
  "http_request": {
    "uri": "http://example.com/",
    "method": "GET",
    "accept": "text/plain"
  }
}
```

The task is marked as ```FAILED``` if the request cannot be completed or the remote server returns non successful status code.