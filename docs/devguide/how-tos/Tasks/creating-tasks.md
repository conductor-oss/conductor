---
description: "Create and update task definitions in Conductor to configure timeouts, retries, rate limits, and input templates for worker and system tasks."
---

# Creating / Updating Task Definitions

A [task definition](../../../documentation/configuration/taskdef.md) specifies a task's general implementation details:

- Timeout policy
- Retry logic
- Rate limit and execution limit
- Input/output keys
- Input template

This definition applies to all instances of the task across workflows.

You can create task definitions using the Conductor UI, CLI, or APIs for the following scenarios:

- **Worker tasks**: all worker tasks (`SIMPLE`) must be registered to the Conductor server as a task definition before they can execute in a workflow.
- **System tasks**: system tasks don't require a task definition, but you can create one with the same name to customize retry, timeout, and rate limit behavior.

## Using Conductor UI

With the UI, you can create or update task definitions visually.

### Creating task definitions

**To create a task definition:**

1. In the left navigation, open **Definitions** and select **Task**.
2. Select **Define task**.
3. Configure the task in the **Task** form, or open the **Code** tab to edit the JSON directly. Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for the full parameters.
4. Select **Save**.

### Updating task definitions

**To update a task definition:**

1. In the left navigation, open **Definitions** and select **Task**, then select the task definition to be updated.
2. Modify the task in the **Task** form or the **Code** tab. Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for the full parameters.
3. Select **Save**.

## Using the CLI

Save your task definition to a JSON file and run:

```bash
conductor task create taskdef.json
```

The file can contain a single task definition object or an array of them. To update an existing definition, edit the file and run:

```bash
conductor task update taskdef.json
```

Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for a reference guide on the full parameters.

## Using APIs

Refer to [Task Definitions](../../../documentation/configuration/taskdef.md) for a reference guide on the full parameters.

### Creating task definitions

You can also create task definitions using the Create Task Definition API (`POST /api/metadata/taskdefs`). The API accepts an array of task definitions, allowing you to create them in bulk.

??? note "Example using cURL"
    ```shell
    curl 'http://localhost:8080/api/metadata/taskdefs' \
      -H 'accept: */*' \
      -H 'content-type: application/json' \
      --data-raw '[{"name":"sample_task_name_1","description":"This is a sample task for demo","responseTimeoutSeconds":10,"timeoutSeconds":30,"inputKeys":[],"outputKeys":[],"timeoutPolicy":"TIME_OUT_WF","retryCount":3,"retryLogic":"FIXED","retryDelaySeconds":5,"inputTemplate":{},"rateLimitPerFrequency":0,"rateLimitFrequencyInSeconds":1}]'
    ```


### Updating task definitions

You can update task definitions using the Update Task Definition API (`PUT /api/metadata/taskdefs`). This API can only be used to update a single task definition at a time.

??? note "Example using cURL"
    ```shell
    curl 'http://localhost:8080/api/metadata/taskdefs' \
      -X 'PUT' \
      -H 'accept: */*' \
      -H 'content-type: application/json' \
      --data-raw '{"name":"sample_task_name_1","description":"This is a sample task for demo","responseTimeoutSeconds":10,"timeoutSeconds":30,"inputKeys":[],"outputKeys":[],"timeoutPolicy":"TIME_OUT_WF","retryCount":3,"retryLogic":"FIXED","retryDelaySeconds":5,"inputTemplate":{},"rateLimitPerFrequency":0,"rateLimitFrequencyInSeconds":1}'
    ```


## Using SDKs

Every [client SDK](../../../documentation/clientsdks/index.md) includes metadata-client methods that call the same create and update endpoints. Use them when task registration belongs in your application or deployment code rather than in a manual step.

## Reusing tasks

Once a task is defined in Conductor, it can be reused numerous times:

- **In the same workflow** — use the same task with different task reference names.
- **Across workflows** — any workflow can reference any registered task definition.

When reusing tasks in a multi-tenant system, all work assigned to a task goes into the same queue by default. If a noisy neighbor causes polling delays, you can scale up the number of workers or use [task-to-domain](../../../documentation/api/taskdomains.md) to route task load into separate queues.
