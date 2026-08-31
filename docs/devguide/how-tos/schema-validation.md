---
description: "Attach JSON Schemas to workflow and task definitions so malformed input is rejected at the boundary instead of failing several tasks later."
---

# Input/Output Schema Validation

A schema is a contract on the shape of data crossing a boundary. Without one, a missing field is discovered by whatever task first dereferences it — usually several tasks in, as a `NullPointerException` in a worker or a silently-null `${...}` expression. With one, the execution is rejected at the boundary, before any side effect.

<!-- TODO: verify the enforcement behaviour below against a server once schema
     enforcement ships. The SchemaDef shape and the definition fields are taken
     from common/src/main/java/com/netflix/conductor/common/metadata/SchemaDef.java,
     WorkflowDef.java, and TaskDef.java. -->

## Where a schema attaches

| Attachment point | Scope | Fields |
|---|---|---|
| Workflow definition | The workflow's own input and output | `WorkflowDef.inputSchema` / `outputSchema` |
| Task definition | Every use of that task, in every workflow | `TaskDef.inputSchema` / `outputSchema` |

Put a schema on the task definition when the contract belongs to the task — every workflow calling `charge_payment` should agree on what a payment request looks like. Put it on the workflow definition when the contract belongs to the entry point, which is the case for anything triggered by an external caller.

## Schema shape

The schema is a `SchemaDef`, embedded in the definition:

```json
{
  "name": "customerInput",
  "version": 1,
  "type": "JSON",
  "data": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "properties": {
      "customerId": { "type": "string" },
      "tier": { "type": "string", "enum": ["standard", "premium"] }
    },
    "required": ["customerId"],
    "additionalProperties": false
  }
}
```

| Field | Meaning |
|---|---|
| `name` | Identifier for the schema |
| `version` | Defaults to `1`. Lets a schema evolve alongside the definition that uses it |
| `type` | `JSON`, `AVRO`, or `PROTOBUF` |
| `data` | The schema document itself |
| `externalRef` | A name for a schema held outside Conductor. Stored and returned unchanged; **nothing dereferences it**, so it is not an alternative to inline `data` |

## Attaching it to a workflow

```json
{
  "name": "order_fulfillment",
  "version": 1,
  "ownerEmail": "team@example.com",
  "schemaVersion": 2,
  "enforceSchema": true,
  "inputSchema": {
    "name": "customerInput",
    "version": 1,
    "type": "JSON",
    "data": {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "properties": { "customerId": { "type": "string" } },
      "required": ["customerId"],
      "additionalProperties": false
    }
  },
  "tasks": []
}
```

Two fields are easy to confuse. `schemaVersion` is unrelated to any of this — it is the workflow definition *format* version and should be `2`. `enforceSchema` is the switch that turns validation on; on `WorkflowDef` it defaults to `true`, so a definition that declares an `inputSchema` is validated unless you explicitly set it to `false`. On `TaskDef` it defaults to `false`.

Register it the usual way — schemas travel inside the definition, so there is no separate step:

```shell
curl -X PUT "$CONDUCTOR_SERVER_URL/metadata/workflow" \
  -H 'Content-Type: application/json' \
  -d '[ ... definition above ... ]'
```

## When validation runs

| Point | Effect on failure |
|---|---|
| Workflow input | The workflow does not start |
| Task input | The task fails before the worker sees it |
| Task output | The task fails after the worker returns |
| Workflow output | The workflow fails at completion |

Input validation is the valuable one: it rejects the execution before any task has run, so there is nothing to compensate for. Output validation catches a worker returning the wrong shape, which otherwise surfaces as a downstream failure far from its cause.

## Writing schemas that age well

`additionalProperties: false` deserves a moment's thought. It turns an unexpected field into a hard failure — right for a contract you own end to end, a nuisance for one where callers legitimately pass extra context. Leave it out unless you mean it.

Adding a field to `required` is a breaking change for every existing caller. Because a running execution keeps the definition version it started with, the safe path is the same as any other definition change: register a new version rather than editing the current one. See [Managing Workflow Versions](Workflows/versioning-workflows.md).

Keep the schema narrow. A schema that restates every optional field becomes something nobody updates, and a stale contract is worse than none. Validate the fields whose absence would actually break the workflow.

## Related pages

- [Schema Registry](schema-registry.md) — storing a schema on the server under a name and version
- [Task Definition reference](../../documentation/configuration/taskdef.md)
- [Workflow Definition reference](../../documentation/configuration/workflowdef/index.md)
- [Task Inputs](Tasks/task-inputs.md)
- [Managing Workflow Versions](Workflows/versioning-workflows.md)
- [CI/CD Integration](cicd-integration.md) — validating definitions before they reach production
