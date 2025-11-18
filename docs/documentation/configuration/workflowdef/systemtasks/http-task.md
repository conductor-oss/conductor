# HTTP Task

```json
"type" : "HTTP"
```

The HTTP task (`HTTP`) is useful for make calls to remote services exposed over HTTP/HTTPS. It supports various HTTP methods, headers, body content, and other configurations needed for interacting with APIs or remote services.

The data returned in the HTTP call can be referenced in subsequent tasks as inputs, enabling you to chain multiple tasks or HTTP calls to create complex flows without writing any additional code.


## Task parameters

Use these parameters inside `inputParameters` in the HTTP task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.http_request | HttpRequest | JSON object containing the URI, method, and more. | Required. |
| inputParameters.http_request.uri | String        | The URI for the HTTP service. You can construct the URI using dynamic references, as shown in the [GET example](#get-method) below.                                  | Required. |
| inputParameters.http_request.method            | String           | The HTTP method. Supported methods: <ul><li>GET</li> <li>PUT</li> <li>POST</li> <li>PATCH</li><li>DELETE</li> <li>OPTIONS</li> <li>HEAD</li> <li>TRACE</li></ul>                                       | Required. |
| inputParameters.http_request.accept            | String           | The accept header required by the server. The default value is `application/json`. Supported values include: <ul><li>application/json</li> <li>application/xml</li> <li>application/pdf</li> <li>application/octet-stream</li> <li>application/x-www-form-urlencoded</li> <li>text/plain</li> <li>text/html</li> <li>text/xml</li> <li>image/jpeg</li> <li>image/png</li> <li>image/gif</li></ul>                                     | Optional. |
| inputParameters.http_request.contentType       | String           | The content type for the server. The default value is `application/json`. Supported values include: <ul><li>application/json</li> <li>text/plain</li> <li>text/html</li> </ul>                                                              | Optional. |
| inputParameters.http_request.headers           | Map[String, Any] | A map of additional HTTP headers to be sent along with the request. <br/><br/> **Tip:** If the remote address that you are connecting to is a secure location, add the Authorization header with `Bearer <access_token>` to `headers`.                | Optional. |
| inputParameters.http_request.body              | Map[String, Any]            | The request body.                                          | Required for POST, PUT, or PATCH methods.
| inputParameters.http_request.asyncComplete     | Boolean          | Whether the task is completed asynchronously. The default value is false. <ul><li>**false**—Task status is set to COMPLETED upon successful execution.</li> <li>**true**—Task status is kept as IN_PROGRESS until an external event (via Conductor or SQS or EventHandler) marks it as complete.</li></ul> <br/> **Tip:** If the remote service sends an asynchronous event to signal the completion of the request, consider setting `asyncComplete` to `true`. | Optional. |
| inputParameters.http_request.connectionTimeOut | Integer          | The connection timeout in milliseconds. The default is 100. <br/><br/> Set to 0 for no timeout.                       | Optional. |
| inputParameters.http_request.readTimeOut       | Integer          | Read timeout in milliseconds. The default is 150. <br/><br/> Set to 0 for no timeout.                       | Optional. |


## Configuration JSON
Here is the task configuration for an HTTP task.

```json
{
  "name": "http",
  "taskReferenceName": "http_ref",
  "type": "HTTP",
  "inputParameters": {
    "http_request": {
      "uri": "https://orkes-api-tester.orkesconductor.com/api",
      "method": "POST",
      "accept": "application/json",
      "contentType": "application/json",
      "encode": true,
      "headers": {
        "header-1": "${workflow.input.header-1}"
      },
      "body": {
        "key": "value"
      }
    }
  }
}
```

## Output

The HTTP task will return the following parameters.

| Name   | Type | Description                                                                                               |
| ------ | ---- | --------------------------------------------------------------------------------------------------------- |
| response     | Map[String, Any]              | The JSON body containing the request response, if available.                         |
| response.headers      | Map[String, Any] | The response headers.                                                            |
| response.statusCode   | Integer          | The [HTTP status code](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes) indicating the request outcome. |
| response.reasonPhrase | String           | The reason phrase associated with the HTTP status code.                                            |
| response.body | Map[String, Any] | The response body containing the data returned by the endpoint.

## Execution

The HTTP task is moved to COMPLETED status once the remote service responds successfully.

If your HTTP tasks are not getting picked up, you might have too many HTTP tasks in the task queue. Consider using Isolation Groups to prioritize certain HTTP tasks over others. 

## Examples

Here are some examples for using the HTTP task.


### GET Method

```json
{
  "name": "Get Example",
  "taskReferenceName": "get_example",
  "inputParameters": {
    "http_request": {
      "uri": "https://jsonplaceholder.typicode.com/posts/${workflow.input.queryid}",
      "method": "GET"
    }
  },
  "type": "HTTP"
}
```

### POST Method

```json
{
  "name": "http_post_example",
  "taskReferenceName": "post_example",
  "inputParameters": {
    "http_request": {
      "uri": "https://jsonplaceholder.typicode.com/posts/",
      "method": "POST",
      "body": {
        "title": "${get_example.output.response.body.title}",
        "userId": "${get_example.output.response.body.userId}",
        "action": "doSomething"
      }
    }
  },
  "type": "HTTP"
}
```

### PUT Method
```json
{
  "name": "http_put_example",
  "taskReferenceName": "put_example",
  "inputParameters": {
    "http_request": {
      "uri": "https://jsonplaceholder.typicode.com/posts/1",
      "method": "PUT",
      "body": {
        "title": "${get_example.output.response.body.title}",
        "userId": "${get_example.output.response.body.userId}",
        "action": "doSomethingDifferent"
      }
    }
  },
  "type": "HTTP"
}
```

### DELETE Method
```json
{
  "name": "DELETE Example",
  "taskReferenceName": "delete_example",
  "inputParameters": {
    "http_request": {
      "uri": "https://jsonplaceholder.typicode.com/posts/1",
      "method": "DELETE"
    }
  },
  "type": "HTTP"
}
```
   
