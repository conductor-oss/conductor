---
description: Route a customer request to one of several approved workflows with an LLM and a dynamic SUB_WORKFLOW.
---

# Dynamic Workflows with AI

Use an LLM to select the best workflow for a user's request while keeping execution durable. The LLM sees a catalog of workflow names and descriptions, returns one selection as JSON, and a dynamic `SUB_WORKFLOW` runs that selected, registered workflow.

The catalog is intentional: a dynamic `SUB_WORKFLOW` can start only a workflow definition registered under the selected name. Keep the workflow names in the prompt aligned with the child workflows registered in Conductor; an invented name fails before any child workflow starts.

## Example: route a customer request

This router can choose one of three registered workflows. The complete runnable fixtures are in [`ai/examples/36-ai-workflow-routing.json`](https://github.com/conductor-oss/conductor/blob/main/ai/examples/36-ai-workflow-routing.json) and its paired `36a`–`36c` child workflows.

| Workflow | Description |
|---|---|
| `ai_route_support_ticket` | Use for product defects, access problems, and troubleshooting requests. |
| `ai_route_refund_request` | Use for returns, refunds, and duplicate-charge requests. |
| `ai_route_sales_lead` | Use for pricing, procurement, and enterprise sales requests. |

```json
{
  "name": "ai_workflow_router",
  "description": "Select an approved workflow for a customer request",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["request"],
  "tasks": [
    {
      "name": "select_workflow",
      "taskReferenceName": "select_workflow",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "message": "You route customer requests to approved workflows. Choose exactly one workflow from this json catalog and return valid json only. Catalog: [{\"workflow\":\"ai_route_support_ticket\",\"description\":\"Product defects, access problems, and troubleshooting.\"},{\"workflow\":\"ai_route_refund_request\",\"description\":\"Returns, refunds, and duplicate charges.\"},{\"workflow\":\"ai_route_sales_lead\",\"description\":\"Pricing, procurement, and enterprise sales.\"}]"
          },
          {
            "role": "user",
            "message": "Customer request: ${workflow.input.request}. Return valid json with workflow and reason."
          }
        ],
        "temperature": 0,
        "maxTokens": 120,
        "jsonOutput": true
      }
    },
    {
      "name": "run_selected_workflow",
      "taskReferenceName": "run_selected_workflow",
      "type": "SUB_WORKFLOW",
      "inputParameters": {
        "request": "${workflow.input.request}",
        "routingReason": "${select_workflow.output.result.reason}"
      },
      "subWorkflowParam": {
        "name": "${select_workflow.output.result.workflow}",
        "version": 1
      }
    }
  ],
  "outputParameters": {
    "selectedWorkflow": "${select_workflow.output.result.workflow}",
    "routingReason": "${select_workflow.output.result.reason}",
    "subWorkflowId": "${run_selected_workflow.output.subWorkflowId}",
    "subWorkflowOutput": "${run_selected_workflow.output}"
  }
}
```

## Register the router and its approved destinations

Register each destination workflow before registering or starting the router. For a local end-to-end trial, these minimal destinations make each branch visible without calling an external system:

```json
{
  "name": "ai_route_support_ticket",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["request", "routingReason"],
  "tasks": [{"name": "record_ticket", "taskReferenceName": "record_ticket", "type": "NOOP"}]
}
```

Create equivalent placeholder definitions named `ai_route_refund_request` and `ai_route_sales_lead`, then register all four definitions:

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' -H 'Content-Type: application/json' -d @ai_route_support_ticket.json
curl -X POST 'http://localhost:8080/api/metadata/workflow' -H 'Content-Type: application/json' -d @ai_route_refund_request.json
curl -X POST 'http://localhost:8080/api/metadata/workflow' -H 'Content-Type: application/json' -d @ai_route_sales_lead.json
curl -X POST 'http://localhost:8080/api/metadata/workflow' -H 'Content-Type: application/json' -d @ai_workflow_router.json
```

Start the router:

```shell
curl -X POST 'http://localhost:8080/api/workflow/ai_workflow_router' \
  -H 'Content-Type: application/json' \
  -d '{"request":"I was charged twice for an order I returned."}'
```

The router records the selected workflow, the model's routing reason, and the child workflow ID in its output. `SUB_WORKFLOW` waits for the selected child to complete; the child output is available on `${run_selected_workflow.output}`.

## Adapt the catalog safely

To add a route, update both places together:

1. Add the workflow name and description to the LLM's catalog.
2. Register version `1` of a workflow whose name exactly matches the catalog entry.

The sub-workflow name is resolved at runtime from the LLM output. A name not present in the metadata registry cannot start a child workflow.

## Related recipes

- [AI Cookbook](../ai/cookbook/index.md) — production starters for chat, RAG, MCP agents, and native AI tasks.
- [Dynamic workflows as code](dynamic-workflows.md) — build workflow definitions in Python when the graph itself must be generated.
