# Managing Workflows via MCP

Conductor implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/), exposing its workflow engine
as an MCP server. Once enabled, any MCP-compatible client can start, inspect, pause, retry, and search workflows without
touching the REST API.

## Enabling

Add to your `application.properties`:

```properties
conductor.mcp-server.enabled=true
```

## Connecting a Client

The MCP server is available at `http://localhost:8080/sse` using SSE transport.

```json
{
  "mcpServers": {
    "conductor": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

This works with Claude Code, Cursor, and any MCP-compatible client.

## Available Tools

| Tool                          | Description                      | Parameters                                                                                                       |
|-------------------------------|----------------------------------|------------------------------------------------------------------------------------------------------------------|
| `startWorkflow`               | Start a new workflow execution   | `name` (required), `version`, `correlationId`, `priority` (0-99), `input`                                        |
| `getWorkflow`                 | Get workflow execution status    | `workflowId` (required), `includeTasks` (default: true)                                                          |
| `pauseWorkflow`               | Pause a running workflow         | `workflowId` (required)                                                                                          |
| `resumeWorkflow`              | Resume a paused workflow         | `workflowId` (required)                                                                                          |
| `terminateWorkflow`           | Terminate a running workflow     | `workflowId` (required), `reason`                                                                                |
| `restartWorkflow`             | Restart a completed workflow     | `workflowId` (required), `useLatestDefinitions` (default: false)                                                 |
| `retryWorkflow`               | Retry the last failed task       | `workflowId` (required), `resumeSubworkflowTasks` (default: false)                                               |
| `deleteWorkflow`              | Remove a workflow                | `workflowId` (required), `archiveWorkflow` (default: true)                                                       |
| `getRunningWorkflows`         | Get running workflow IDs by name | `workflowName` (required), `version` (default: 1)                                                                |
| `searchWorkflows`             | Search workflows                 | `query`, `freeText`, `start` (default: 0), `size` (default: 100)                                                 |
| `getWorkflowsByCorrelationId` | Get workflows by correlation ID  | `name` (required), `correlationId` (required), `includeClosed` (default: false), `includeTasks` (default: false) |
