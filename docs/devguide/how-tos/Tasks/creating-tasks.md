# Creating / Updating Task Definitions

A [task definition](../../../documentation/configuration/taskdef.md) specifies a task’s general implementation details:

- Timeout policy
- Retry logic
- Rate limit and execution limit
- Input/output keys
- Input template

This definition applies to all instances of the task across workflows.

You can create task definitions using the Conductor UI or APIs for the following scenarios:

- **Worker tasks**—All Worker tasks (`SIMPLE`) must be registered to the Conductor server as a task definition before it can execute in a workflow.
- **System tasks**—All system tasks can be [extended with a task definition](extending-system-tasks.md) if required.

## Using Conductor UI

With the UI, you can create or update task definitions visually.

### Creating task definitions

**To create a task definition:**

1. In [**Executions** > **Tasks**](http://localhost:8127/taskDefs), select **+ New Task Definition**.
2. Configure the task definition JSON. Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for the full parameters.
3. Select **Save** > **Save**.

### Updating task definitions

**To update a task definition:**

1. In [**Executions** > **Tasks**](http://localhost:8127/taskDefs), select the task definition to be updated.
2. Modify the task definition JSON. Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for the full parameters.
3. Select **Save** > **Save**.

## Using APIs

Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for a reference guide on the full parameters.

### Creating task definitions

You can create task definitions using the Create Task Definition API (`POST api/metadata/taskdefs`). The API accepts an array of task definitions, allowing you to create them in bulk.

#### Example using cURL

```shell
curl '{{ server_host }}/api/metadata/taskdefs' \
  -H 'accept: */*' \
  -H 'content-type: application/json' \
  --data-raw '[{"createdBy":"user","name":"sample_task_name_1","description":"This is a sample task for demo","responseTimeoutSeconds":10,"timeoutSeconds":30,"inputKeys":[],"outputKeys":[],"timeoutPolicy":"TIME_OUT_WF","retryCount":3,"retryLogic":"FIXED","retryDelaySeconds":5,"inputTemplate":{},"rateLimitPerFrequency":0,"rateLimitFrequencyInSeconds":1}]'
```


### Updating task definitions

You can update task definitions using the Update Task Definition API (`PUT api/metadata/taskdefs`). This API can only be used to update a single task definition at a time.

#### Example using cURL

```shell
curl '{{ server_host }}/api/metadata/taskdefs' \
  -X 'PUT' \
  -H 'accept: */*' \
  -H 'content-type: application/json' \
  --data-raw '{"createdBy":"user","name":"sample_task_name_1","description":"This is a sample task for demo","responseTimeoutSeconds":10,"timeoutSeconds":30,"inputKeys":[],"outputKeys":[],"timeoutPolicy":"TIME_OUT_WF","retryCount":3,"retryLogic":"FIXED","retryDelaySeconds":5,"inputTemplate":{},"rateLimitPerFrequency":0,"rateLimitFrequencyInSeconds":1}'
```


## Using SDKs

Conductor offers client SDKs for popular languages which have library methods for making the API call. Refer to the SDK documentation to configure a client in your selected language to create or update task definitions.

Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for a reference guide on the full parameters.

### Creating task definitions - Example using JavaScript

In this example, the JavaScript Fetch API is used to create the task definition `sample_task_name_1`.

```javascript
fetch("{{ server_host }}/api/metadata/taskdefs", {
    "headers": {
        "accept": "*/*",
        "content-type": "application/json",
    },
    "body": "[{\"createdBy\":\"user\",\"name\":\"sample_task_name_1\",\"description\":\"This is a sample task for demo\",\"responseTimeoutSeconds\":10,\"timeoutSeconds\":30,\"inputKeys\":[],\"outputKeys\":[],\"timeoutPolicy\":\"TIME_OUT_WF\",\"retryCount\":3,\"retryLogic\":\"FIXED\",\"retryDelaySeconds\":5,\"inputTemplate\":{},\"rateLimitPerFrequency\":0,\"rateLimitFrequencyInSeconds\":1}]",
    "method": "POST"
});
```


### Updating task definitions - Example using JavaScript

In this example, the JavaScript Fetch API is used to update the task definition `sample_task_name_1`.

```javascript
fetch("{{ server_host }}/api/metadata/taskdefs", {
    "headers": {
        "accept": "*/*",
        "content-type": "application/json",
    },
    "body": "{\"createdBy\":\"user\",\"name\":\"sample_task_name_1\",\"description\":\"This is a sample task for demo\",\"responseTimeoutSeconds\":10,\"timeoutSeconds\":30,\"inputKeys\":[],\"outputKeys\":[],\"timeoutPolicy\":\"TIME_OUT_WF\",\"retryCount\":3,\"retryLogic\":\"FIXED\",\"retryDelaySeconds\":5,\"inputTemplate\":{},\"rateLimitPerFrequency\":0,\"rateLimitFrequencyInSeconds\":1}",
    "method": "PUT"
});
```