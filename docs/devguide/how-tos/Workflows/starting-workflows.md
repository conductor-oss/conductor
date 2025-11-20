# Starting Workflows

In Conductor, workflows can be started using the Conductor UI, APIs, or SDKs.

## Using Conductor UI

The Conductor UI is useful for sandbox testing before deploying the workflows to production using the APIs or SDKs.

**To start a workflow:**

1. Go to [Workbench](http://localhost:8127/workbench) in the Conductor UI.
2. Select the  **Workflow Name** and **Workflow version**.
3. If required, provide the workflow inputs in **Input (JSON)**.
4. (Optional) Specify the **Correlation ID** and **Task to Domain (JSON)** for the execution.
5. Select the ▶ icon (Execute Workflow) at the top to run the workflow.

Once the workflow has started, you can view the ongoing execution by selecting the Workflow ID hyperlink in the **Execution History** side panel on the right.

## Using APIs

You can start workflow executions using the Start Workflow API (`POST api/workflow/{name}`). `{name}` is the placeholder for the workflow name, and the request body contains the workflow inputs if any.

### Example using cURL

In this example, a cURL request is used to invoke the workflow `sample_workflow` with the input `service`  specified as `fedex`.

```bash
curl '{{ server_host }}/api/workflow/sample_workflow' \
  -H 'accept: text/plain' \
  -H 'content-type: application/json' \
  --data-raw '{"service":"fedex"}'
```

## Using SDKs

Conductor offers client SDKs for popular languages which have library methods for making the Start Workflow API call. Refer to the SDK documentation to configure a client in your selected language to invoke workflow executions.

### Example using JavaScript

In this example, the JavaScript Fetch API is used to invoke the workflow `sample_workflow` with the input `service`  specified as `fedex`.

```javascript
fetch("{{ server_host }}/api/workflow/sample_workflow", {
    "headers": {
        "accept": "text/plain",
        "content-type": "application/json",
    },
    "body": "{\"service\":\"fedex\"}",
    "method": "POST",
});
```


