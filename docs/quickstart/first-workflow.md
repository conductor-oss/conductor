---
description: Run your first Conductor workflow locally, verify the result, and take the production path.
---

# Run your first workflow

**Audience:** developers orchestrating APIs, services, or workers.

**Outcome:** a completed, inspectable, two-step workflow—without writing a worker.

Choose [an agent path](choose-path.md) instead if your first result should be an LLM-powered agent.

## Prerequisites

Complete [Connect to Conductor](connect.md) and verify the connection before continuing. This workflow uses built-in HTTP and JSON tasks, so it does not require a model-provider API key.

## 1. Create a workflow

Save this as `workflow.json`. It calls a public test endpoint and transforms the response with two built-in system tasks, so no worker process is required.

```json
{
  "name": "hello_workflow",
  "description": "Fetch a test response and return a compact summary.",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "fetch_data",
      "taskReferenceName": "fetch_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://orkes-api-tester.orkesconductor.com/api",
          "method": "GET"
        }
      }
    },
    {
      "name": "summarize_response",
      "taskReferenceName": "summary_ref",
      "type": "JSON_JQ_TRANSFORM",
      "inputParameters": {
        "response": "${fetch_ref.output.response.body}",
        "queryExpression": "{host: .response.hostName, randomValue: .response.randomInt, summary: (\"Host \" + .response.hostName + \" responded with random value \" + (.response.randomInt|tostring))}"
      }
    }
  ],
  "outputParameters": {
    "summary": "${summary_ref.output.result.summary}",
    "host": "${summary_ref.output.result.host}",
    "randomValue": "${summary_ref.output.result.randomValue}"
  }
}
```

The [HTTP task](../documentation/configuration/workflowdef/systemtasks/http-task.md) performs the request. The [JSON JQ transform task](../documentation/configuration/workflowdef/systemtasks/json-jq-transform-task.md) shapes its JSON output.

## 2. Register, run, and verify

```bash
conductor workflow create workflow.json
conductor workflow start -w hello_workflow --sync
```

The synchronous start returns the workflow execution. Verify that its status is `COMPLETED` and its output has `summary`, `host`, and `randomValue`. In the UI, open the new execution and inspect the completed `fetch_ref` and `summary_ref` tasks.

Expected output values vary because the test endpoint is random, but the shape is:

```json
{
  "summary": "Host … responded with random value …",
  "host": "…",
  "randomValue": 123
}
```

## Recovery

- If the CLI cannot connect, return to [Connect to Conductor](connect.md) and verify the URL and credentials.
- If registration reports that the definition already exists, delete the local test definition or change its version before creating it again.
- If the HTTP task fails, inspect its response and retry with a new execution; the public test endpoint must be reachable from the server.

## Next production step

You now have a verified system-task workflow. To run your own business logic, continue with [Write Your First Worker](first-worker.md). Otherwise, use the [best practices](../devguide/bestpractices.md) to add contracts, workers, retries, tests, deployment, and operations.
