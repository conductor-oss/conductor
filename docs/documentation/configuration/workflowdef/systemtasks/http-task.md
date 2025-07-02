# HTTP Task

```json
"type" : "HTTP"
```

The `HTTP` task is useful when you have a requirements such as:

1. Making calls to another service that exposes an API via HTTP
2. Fetch any resource or data present on an endpoint

The `HTTP` task is moved to `COMPLETED` status once the remote service responds successfully.


## Use Cases

If we have a scenario where we need to make an HTTP call into another service, we can make use of HTTP tasks. You can
use the data returned from the HTTP call in your subsequent tasks as inputs. Using HTTP tasks you can avoid having to
write the code that talks to these services and instead let Conductor manage it directly. This can reduce the code you
have to maintain and allows for a lot of flexibility.

## Configuration

HTTP task is configured using the following key inside the `inputParameters`  of a task with type `HTTP`.

### inputParameters
| name         | type        | description                              |
| ------------ | ----------- | ---------------------------------------- |
| http_request | HttpRequest | JSON object (see [below](#http_request)) |

### http_request

| Name              | Type             | Description                                                                                                                                                                |
| ----------------- | ---------------- |----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| uri               | String           | URI for the service. Can be a partial when using vipAddress or includes the server address.                                                                                |
| method            | String           | HTTP method. GET, PUT, POST, DELETE, OPTIONS, HEAD                                                                                                                         |
| accept            | String           | Accept header. Default:  ```application/json```                                                                                                                            |
| contentType       | String           | Content Type - supported types are ```text/plain```, ```text/html```, and ```application/json``` (Default)                                                                 |
| headers           | Map[String, Any] | A map of additional http headers to be sent along with the request.                                                                                                        |
| body              | Map[]            | Request body                                                                                                                                                               |
| asyncComplete     | Boolean          | ```false``` to mark status COMPLETED upon execution ; ```true``` to keep it IN_PROGRESS, wait for an external event (via Conductor or SQS or EventHandler) to complete it. |
| connectionTimeOut | Integer          | Connection Time Out in milliseconds. If set to 0, equivalent to infinity. Default: 100.                                                                                    |
| readTimeOut       | Integer          | Read Time Out in milliseconds. If set to 0, equivalent to infinity. Default: 150.                                                                                          |
| accept4xxValues | Integer[] | Return codes to accept as valid when they are not 2xx.                                                                                                                              |

!!!tip Prevent 404 Errors
    If a remote service returing a 404 is a valid response that should not generate an error, the `accept4xxValues` parameter could be set to `[404]`. This will prevent the task from throwing an error when a 404 is returned from a remote service. This is useful for creating idempotent `get-or-create` type flows

!!!tip Asynchronous Requests
    In the case that remote service sends an asynchronous event to signal the completion of the request, consider setting the `asyncComplete` flag on the HTTP task to `true`. In this case, you will need
    to transition the HTTP task to COMPLETED manually.
    
!!!tip Authorization Header
    If the remote address that you are connecting to is a secure location, add the Authorization header with `Bearer <access_token>` to headers.

## Output

| name         | type             | description                                                                 |
| ------------ | ---------------- | --------------------------------------------------------------------------- |
| response     | Map              | JSON body containing the response if one is present                         |
| headers      | Map[String, Any] | Response Headers                                                            |
| statusCode   | Integer          | [Http Status Code](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes) |
| reasonPhrase | String           | Http Status Code's reason phrase                                            |

## Examples
### GET Method
We can use variables in our URI as show in the example below. 

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
Here we are using variables for our POST body which happens to be data from a previous task. This is an example of how you can **chain** HTTP calls to make complex flows happen without writing any additional code.

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

## Isolation Groups
Why are my HTTP tasks not getting picked up?

We might have too many HTTP tasks in the queue. There is a concept called Isolation Groups that you can rely on
for prioritizing certain HTTP tasks over others. 
   
