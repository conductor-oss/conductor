---
description: "Conductor REST API reference — complete endpoint documentation for workflow orchestration including metadata, execution management, task polling, bulk operations, and event handlers."
---

# API Reference

Conductor exposes a full REST API for managing workflow definitions, executions, tasks, and events.

## Base URL

All API endpoints are relative to your Conductor server's base URL:

```
http://localhost:8080/api/
```

For example, to list all workflow definitions:

```shell
curl http://localhost:8080/api/metadata/workflow
```

If your Conductor server runs on a different host or port, replace `localhost:8080` accordingly.

## Authentication

Conductor OSS does not require authentication by default. All API endpoints are open. If you need to secure your Conductor instance, you can add authentication via a reverse proxy (e.g., Nginx, Envoy) or by implementing a custom security filter in Spring Boot.

## Content Type

All request and response bodies use JSON. Set the following headers on requests with a body:

```
Content-Type: application/json
```

A few endpoints return plain text (e.g., workflow ID on start). These are noted in their documentation.

## Common Response Codes

| Status Code | Description |
|---|---|
| `200 OK` | Request succeeded. Response body contains the result. |
| `204 No Content` | Request succeeded but there is no response body (e.g., poll with no tasks available). |
| `400 Bad Request` | Invalid request — check your request body or parameters. |
| `404 Not Found` | The requested resource (workflow, task, definition) does not exist. |
| `409 Conflict` | Conflict with current state (e.g., trying to resume a workflow that is not paused). |
| `500 Internal Server Error` | Server-side error. Check Conductor server logs. |

### Error Response Format

When an error occurs, the response body contains:

```json
{
  "status": 400,
  "message": "Workflow definition is not valid",
  "instance": "conductor-server",
  "retryable": false
}
```

## Quick Start

Register a workflow definition, start it, and check its status — all in three commands:

```shell
# 1. Register a workflow definition
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "hello_workflow",
    "version": 1,
    "tasks": [
      {
        "name": "hello_task",
        "taskReferenceName": "hello_ref",
        "type": "HTTP",
        "inputParameters": {
          "uri": "https://jsonplaceholder.typicode.com/posts/1",
          "method": "GET"
        }
      }
    ],
    "schemaVersion": 2
  }'

# 2. Start a workflow execution
WORKFLOW_ID=$(curl -s -X POST 'http://localhost:8080/api/workflow/hello_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}')
echo "Started workflow: $WORKFLOW_ID"

# 3. Check workflow status
curl "http://localhost:8080/api/workflow/$WORKFLOW_ID"
```

## API Sections

| Section | Base Path | Description |
|---|---|---|
| **[Metadata](metadata.md)** | `/api/metadata` | Register, update, validate, and delete workflow and task definitions |
| **[Start Workflow](startworkflow.md)** | `/api/workflow` | Start workflows asynchronously, synchronously, or with dynamic definitions |
| **[Workflow](workflow.md)** | `/api/workflow` | Manage executions: get status, pause, resume, retry, restart, terminate, search |
| **[Task](task.md)** | `/api/tasks` | Poll for tasks, update results, manage queues, view logs, search |
| **[Bulk Operations](bulk.md)** | `/api/workflow/bulk` | Pause, resume, restart, retry, terminate, or remove workflows in batch |
| **[Event Handlers](eventhandlers.md)** | `/api/event` | Create and manage event-driven workflow triggers |
| **[Task Domains](taskdomains.md)** | — | Route tasks to specific worker pools at runtime |

## Swagger UI

The Swagger UI at `http://localhost:8080/swagger-ui/index.html` provides an interactive API explorer where you can try endpoints directly from your browser.

## SDKs

For programmatic access, use one of the official [Conductor SDKs](../../documentation/clientsdks/index.md) which wrap these REST APIs with language-native interfaces for Java, Python, Go, JavaScript, C#, Ruby, and Rust.
