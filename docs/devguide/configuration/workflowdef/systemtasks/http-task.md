---
description: "Configure HTTP tasks in Conductor to call remote APIs and services. Supports GET, POST, PUT, DELETE methods with headers, body, and timeout options."
---

# HTTP Task

```json
"type" : "HTTP"
```

The HTTP task (`HTTP`) is useful for make calls to remote services exposed over HTTP/HTTPS. It supports various HTTP methods, headers, body content, and other configurations needed for interacting with APIs or remote services.

The data returned in the HTTP call can be referenced in subsequent tasks as inputs, enabling you to chain multiple tasks or HTTP calls to create complex flows without writing any additional code.


## Task parameters

The HTTP request parameters can be specified directly in `inputParameters` or nested inside `inputParameters.http_request`. Both forms are supported — the flat form is simpler for most use cases.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| uri | String        | The URI for the HTTP service. Supports dynamic references like `${workflow.input.url}`.                                  | Required. |
| method            | String           | The HTTP method. Supported methods: `GET`, `PUT`, `POST`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`, `TRACE`.                                       | Required. |
| accept            | String           | The accept header required by the server. Default: `application/json`.                                     | Optional. |
| contentType       | String           | The content type for the request. Default: `application/json`.                                                              | Optional. |
| headers           | Map[String, Any] | A map of additional HTTP headers to be sent along with the request. See [Sending headers](#sending-headers) below.                | Optional. |
| body              | Map[String, Any]            | The request body.                                          | Required for POST, PUT, or PATCH methods. |
| asyncComplete     | Boolean          | Whether the task is completed asynchronously. Default: `false`. When `true`, the task stays `IN_PROGRESS` until an external event marks it as complete. | Optional. |
| connectionTimeOut | Integer          | The connection timeout in milliseconds. Default: 100. Set to 0 for no timeout.                       | Optional. |
| readTimeOut       | Integer          | Read timeout in milliseconds. Default: 150. Set to 0 for no timeout.                       | Optional. |

## Configuration JSON

Here is the task configuration for an HTTP task. Note that parameters are specified directly in `inputParameters`:

```json
{
  "name": "http",
  "taskReferenceName": "http_ref",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://api.example.com/data",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer ${workflow.input.api_token}",
      "X-Request-Id": "${workflow.correlationId}"
    },
    "body": {
      "key": "value"
    }
  }
}
```

!!! note "Legacy `http_request` form"
    The nested `inputParameters.http_request` form is still supported for backward compatibility:
    ```json
    "inputParameters": {
      "http_request": {
        "uri": "https://api.example.com/data",
        "method": "POST",
        "body": { "key": "value" }
      }
    }
    ```
    Both forms work identically. The flat form (shown above) is recommended for new workflows.

## Sending headers

Use the `headers` parameter to send custom HTTP headers, including authentication:

### Bearer token authentication

```json
{
  "name": "call_api",
  "taskReferenceName": "call_api_ref",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://api.example.com/protected/resource",
    "method": "GET",
    "headers": {
      "Authorization": "Bearer ${workflow.input.access_token}"
    }
  }
}
```

### API key authentication

```json
{
  "name": "call_api",
  "taskReferenceName": "call_api_ref",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://api.example.com/data",
    "method": "GET",
    "headers": {
      "X-API-Key": "${workflow.input.api_key}"
    }
  }
}
```

### Basic authentication

```json
{
  "name": "call_api",
  "taskReferenceName": "call_api_ref",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://api.example.com/data",
    "method": "GET",
    "headers": {
      "Authorization": "Basic ${workflow.input.basic_auth_token}"
    }
  }
}
```

### Multiple custom headers

```json
{
  "name": "call_api",
  "taskReferenceName": "call_api_ref",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://api.example.com/data",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer ${workflow.input.token}",
      "X-Correlation-Id": "${workflow.correlationId}",
      "X-Request-Source": "conductor",
      "Accept-Language": "en-US"
    },
    "body": {
      "data": "${workflow.input.payload}"
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
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://jsonplaceholder.typicode.com/posts/${workflow.input.queryid}",
    "method": "GET"
  }
}
```

### POST Method

```json
{
  "name": "http_post_example",
  "taskReferenceName": "post_example",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://jsonplaceholder.typicode.com/posts/",
    "method": "POST",
    "body": {
      "title": "${get_example.output.response.body.title}",
      "userId": "${get_example.output.response.body.userId}",
      "action": "doSomething"
    }
  }
}
```

### PUT Method
```json
{
  "name": "http_put_example",
  "taskReferenceName": "put_example",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://jsonplaceholder.typicode.com/posts/1",
    "method": "PUT",
    "body": {
      "title": "${get_example.output.response.body.title}",
      "userId": "${get_example.output.response.body.userId}",
      "action": "doSomethingDifferent"
    }
  }
}
```

### DELETE Method
```json
{
  "name": "DELETE Example",
  "taskReferenceName": "delete_example",
  "type": "HTTP",
  "inputParameters": {
    "uri": "https://jsonplaceholder.typicode.com/posts/1",
    "method": "DELETE"
  }
}
```
