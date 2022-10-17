# Starting Workflows

Workflow executions can be started by using the following API:

```http
POST /api/workflow/{name}
```

`{name}` is the placeholder for workflow name. The POST API body is your workflow input parameters which can be empty if
there are none.

### Using Client SDKs

Conductor offers client SDKs for popular languages which has library methods that can be used to make this API call.
Refer to the SDK documentation to configure a client in your selected language to invoke workflow executions.

### Example using curl

```bash
curl 'https://localhost:8080/api/workflow/sample_workflow' \
  -H 'accept: text/plain' \
  -H 'content-type: application/json' \
  --data-raw '{"service":"fedex"}'
```

In this example we are specifying one input param called `service` with a value of `fedex` and the name of the workflow
is `sample_workflow`

### Example using node fetch

```javascript
fetch("https://localhost:8080/api/workflow/sample_workflow", {
    "headers": {
        "accept": "text/plain",
        "content-type": "application/json",
    },
    "body": "{\"service\":\"fedex\"}",
    "method": "POST",
});
```


