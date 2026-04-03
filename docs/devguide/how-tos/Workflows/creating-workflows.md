# Creating / Updating Workflows

You can create and update workflows using the Conductor UI, APIs, or SDKs. These workflows can be versioned, which is useful for [a variety of cases](versioning-workflows.md#when-to-version-workflows).

If your workflow definition contains any new tasks, you must also register the task definitions to Conductor before running the workflow.

## Using Conductor UI

With the UI, you can create or update workflow definitions visually.

### Creating workflows

**To create a workflow definition:**

1. In **[Definitions](http://localhost:8127/workflowDefs)**, select **+ New Workflow Definition**.
2. Configure the workflow definition JSON. Refer to [Workflow Definition](../../../documentation/configuration/workflowdef/index.md) for the reference guide on the full parameters.
3. Select **Save** > **Save**.

### Updating workflows

**To update a workflow definition:**

1. In **[Definitions](http://localhost:8127/workflowDefs**)**, select the workflow to be updated.
2. Modify the workflow definition JSON. Refer to [Workflow Definition](../../../documentation/configuration/workflowdef/index.md) for the reference guide on the full parameters.
3. Select **Save**. The workflow version will automatically increment by 1.
4. (Optional) Clear the **Automatically set version** checkbox to save the updated workflow definition without creating a new version.
5. Select **Save** again to confirm.


## Using APIs

You can create or update workflow definitions using the Update Workflow Definition API (`PUT api/metadata/workflow`). 

Refer to [Workflow Definition](../../../documentation/configuration/workflowdef/index.md) for the reference guide on the full parameters.

### Example using cURL 

```shell
curl '{{ server_host }}/api/metadata/workflow' \
  -X 'PUT' \
  -H 'accept: */*' \
  -H 'content-type: application/json' \
  --data-raw '[{"name":"sample_workflow","description":"shipping","version":1,"tasks":[{"name":"ship_via","taskReferenceName":"ship_via","type":"SIMPLE","inputParameters":{"service":"${workflow.input.service}"}}],"inputParameters":["service"],"outputParameters":{},"schemaVersion":2, "ownerEmail": "example@email.com"}]'
```

## Using SDKs

Conductor offers client SDKs for popular languages which have library methods for making the API call. Refer to the SDK documentation to configure a client in your selected language to invoke workflow executions.

Refer to [Workflow Definition](../../../documentation/configuration/workflowdef/index.md) for the reference guide on the full parameters.

### Example using JavaScript

In this example, the JavaScript Fetch API is used to create the workflow `sample_workflow`.

```javascript
fetch("{{ server_host }}/api/metadata/workflow", {
  "headers": {
    "accept": "*/*",
    "content-type": "application/json"
  },
  "body": "[{\"name\":\"sample_workflow\",\"description\":\"shipping\",\"version\":1,\"tasks\":[{\"name\":\"ship_via\",\"taskReferenceName\":\"ship_via\",\"type\":\"SIMPLE\",\"inputParameters\":{\"service\":\"${workflow.input.service}\"}}],\"inputParameters\":[\"service\"],\"outputParameters\":{},\"schemaVersion\":2,\"ownerEmail\": \"example@email.com\"}]",
  "method": "PUT"
});
```