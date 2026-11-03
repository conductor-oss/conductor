---
description: Start Conductor workflow executions with the CLI, REST API, Java, Python, TypeScript, or Go.
---

# Start workflows

Starting a workflow creates a durable execution and returns a workflow ID. Preserve that ID: it is the primary key for status, tasks, logs, and recovery.

## Prerequisites

- The workflow definition is registered.
- Every `SIMPLE` task has a task definition and a running worker.
- The CLI or selected SDK is configured for the same server.

## Start with the CLI

Use asynchronous start for long-running work:

```bash
conductor workflow start -w sample_workflow -i '{"service":"fedex"}'
```

Pin a version and attach a business correlation ID when repeatability and lookup matter:

```bash
conductor workflow start -w sample_workflow --version 2 \
  --correlation order-123 -i '{"service":"fedex"}'
```

For a bounded test, `--sync` waits for the execution result:

```bash
conductor workflow start -w sample_workflow -i '{"service":"fedex"}' --sync
```

Success is a returned workflow ID for an asynchronous start, or a workflow result with the expected status for a synchronous start.

## Start with REST

`POST /api/workflow/{name}` accepts the workflow input map directly and returns the workflow ID as text.

```bash
curl -sS -X POST 'http://localhost:8080/api/workflow/sample_workflow' \
  -H 'Content-Type: application/json' \
  --data '{"service":"fedex"}'
```

Use `POST /api/workflow` with a `StartWorkflowRequest` when you need fields such as `version`, `correlationId`, `priority`, or `taskToDomain`. Use `POST /api/workflow/execute/{name}/{version}` only when the caller should wait synchronously. The [Start Workflow API](../../../documentation/api/startworkflow.md) owns the complete request and response reference.

## Start with an SDK

These examples show the start call after client configuration. Use the SDK reference linked below each tab for dependency and authentication setup.

=== "Java"

    ```java
    StartWorkflowRequest request = new StartWorkflowRequest();
    request.setName("sample_workflow");
    request.setVersion(2);
    request.setCorrelationId("order-123");
    request.setInput(Map.of("service", "fedex"));

    String workflowId = clients.getWorkflowClient().startWorkflow(request);
    ```

    See the [Java SDK](../../../documentation/clientsdks/java-sdk.md).

=== "Python"

    ```python
    from conductor.client.http.models import StartWorkflowRequest

    request = StartWorkflowRequest(
        name="sample_workflow",
        version=2,
        correlation_id="order-123",
        input={"service": "fedex"},
    )
    workflow_id = executor.start_workflow(request)
    ```

    See the [Python SDK](../../../documentation/clientsdks/python-sdk.md).

=== "TypeScript"

    ```typescript
    const workflowId = await workflowClient.startWorkflow({
      name: "sample_workflow",
      version: 2,
      correlationId: "order-123",
      input: { service: "fedex" },
    });
    ```

    See the [JavaScript and TypeScript SDK](../../../documentation/clientsdks/js-sdk.md).

=== "Go"

    ```go
    workflowID, err := workflowExecutor.StartWorkflow(&model.StartWorkflowRequest{
        Name:          "sample_workflow",
        Version:       2,
        CorrelationId: "order-123",
        Input: map[string]string{
            "service": "fedex",
        },
    })
    if err != nil {
        return err
    }
    ```

    See the [Go SDK](../../../documentation/clientsdks/go-sdk.md).

## Inspect the execution

```bash
conductor workflow get-execution <workflow-id> -c
```

Confirm the workflow name and version, input, current status, and each task status. Submission alone is not proof that a worker or integration completed.

## Limitations

- Synchronous execution keeps the client waiting and is a poor fit for human tasks, timers, and long-running workers.
- Omitting `version` selects the server's latest registered version; pin it when callers require repeatable behavior.
- A correlation ID helps lookup but is not necessarily unique and is not a substitute for the workflow ID.

Next, learn how to [view executions](viewing-workflow-executions.md) or [choose an automatic trigger](choosing-a-trigger.md).

<a id="using-conductor-ui"></a>
<a id="using-the-cli"></a>
<a id="using-apis"></a>
<a id="using-sdks"></a>
