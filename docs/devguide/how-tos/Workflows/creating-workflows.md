---
description: Validate, create, and update versioned Conductor workflow definitions.
---

# Create or update workflows

A workflow definition is a versioned contract. Build it from registered task types, validate it, and register a new version for breaking changes.

## Prerequisites

- A reachable Conductor server and configured CLI.
- A definition that follows the [workflow definition reference](../../../documentation/configuration/workflowdef/index.md).
- A task definition and polling worker for every `SIMPLE` task.

## 1. Define the contract

Specify `name`, `version`, `schemaVersion: 2`, input names, tasks, and stable `outputParameters`. Give every task a unique, descriptive `taskReferenceName`. Prefer a [built-in task](../Tasks/choosing-tasks.md) when it represents the operation.

Save the result as `workflow.json`. A `SIMPLE` task whose task definition is missing, or whose worker is not polling, remains queued at runtime.

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

The REST equivalents are `POST /api/metadata/workflow` for create and `PUT /api/metadata/workflow` for an update body containing an array of definitions. The [Metadata API](../../../documentation/api/metadata.md) owns the endpoint contract.

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

Use a new version when inputs, outputs, task order, or failure semantics change in a way callers can observe. Register the new version, update callers deliberately, and leave the previous version available while existing callers or executions need it. See [Version and roll out](versioning-workflows.md).

## UI alternative

In **Definitions**, create or open a workflow, edit its JSON or graph, and save it. Keep automatic version increment enabled for production changes. Use the CLI/API flow in automation so the checked-in definition remains the source of truth.

## Limitations

- Definition validation does not verify task worker deployment, credentials, broker topics, or HTTP reachability.
- Updating the same version in place makes rollout and rollback harder to reason about.
- Large input/output payloads belong in external storage; carry references in the workflow.

Next, [start the workflow](starting-workflows.md) and inspect the returned execution.

<a id="using-conductor-ui"></a>
<a id="using-the-cli"></a>
<a id="using-apis"></a>
<a id="using-sdks"></a>
