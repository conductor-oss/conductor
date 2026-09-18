---
description: Write, validate, and register versioned Conductor workflow definitions with the CLI, API, or UI.
---

# Create or update workflows

A workflow definition is a versioned JSON document. It declares the workflow's name, its inputs and outputs, and the tasks it runs. This page covers writing that document, validating it, and registering it with the server.

## Prerequisites

- A reachable Conductor server and configured CLI.
- A task definition and polling worker for every `SIMPLE` task.

## 1. Write the definition

A minimal definition names the workflow, lists its tasks, and maps its inputs and outputs:

```json
{
  "name": "order_flow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["orderId"],
  "tasks": [
    {
      "name": "process_order",
      "taskReferenceName": "process_order_ref",
      "type": "SIMPLE",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}"
      }
    }
  ],
  "outputParameters": {
    "status": "${process_order_ref.output.status}"
  }
}
```

Save it as `workflow.json`. A few rules to follow:

- Give every task a unique, descriptive `taskReferenceName`. Other tasks reference its output through that name.
- Prefer a [built-in task](../Tasks/choosing-tasks.md) when one covers the operation. A `SIMPLE` task needs a registered task definition and a polling worker, or it stays queued at runtime.
- Keep `outputParameters` stable across versions, because callers depend on them.

The [workflow definition reference](../../../documentation/configuration/workflowdef/index.md) documents every field.

## 2. Validate before registration

```bash
curl -i -X POST 'http://localhost:8080/api/metadata/workflow/validate' \
  -H 'Content-Type: application/json' \
  --data-binary @workflow.json
```

Success is an empty `200 OK` response. Validation checks the definition, not worker availability or external connectivity.

## 3. Register the definition

```bash
conductor workflow create workflow.json
```

Success is a registered name and version visible through:

```bash
conductor workflow get <workflow-name>
```

The REST equivalents are `POST /api/metadata/workflow` for create and `PUT /api/metadata/workflow` for an update body containing an array of definitions. See the [Metadata API](../../../documentation/api/metadata.md) for both endpoints.

## 4. Verify SIMPLE task dependencies

List registered task definitions and compare them with every workflow task whose `type` is `SIMPLE`:

```bash
conductor taskDef list
```

Then verify that a worker polls each exact task type. Registration alone does not start a worker.

## 5. Test and run

Use [Validate and test workflows](testing-workflows.md) to mock branches through `/api/workflow/test`, then run one real execution against test dependencies.

## Update and version safely

<a id="updating-workflows"></a>

Use a new version when inputs, outputs, task order, or failure semantics change in a way callers can observe. Register the new version, update callers deliberately, and leave the previous version available while existing callers or executions need it. See [Managing Workflow Versions](versioning-workflows.md).

## Create in the UI

<a id="ui-alternative"></a>

1. In the left navigation, open **Definitions** and select **Workflow**.
2. Select **Define workflow** in the top right. The editor opens with an empty Start-to-End graph.
3. Under **Workflow Details**, enter a unique name and a description.
4. Add tasks either visually or as JSON:
    - Select the **+** node on the canvas to insert a task, then configure it in the **Task** panel.
    - Or open the **Code** tab and paste a complete JSON definition.
5. Select **Save**. Resolve any warnings the editor reports first.

To change an existing workflow, open it from **Definitions** and then **Workflow**, edit it, and save. Use the CLI/API flow in automation so the checked-in definition remains the source of truth.

## Limitations

- Definition validation does not verify task worker deployment, credentials, broker topics, or HTTP reachability.
- Updating the same version in place makes rollout and rollback harder to reason about.
- Large input/output payloads belong in external storage; carry references in the workflow.

Next, [start the workflow](starting-workflows.md) and inspect the returned execution.

<a id="using-conductor-ui"></a>
<a id="using-the-cli"></a>
<a id="using-apis"></a>
<a id="using-sdks"></a>
