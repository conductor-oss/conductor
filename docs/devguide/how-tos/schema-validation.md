---
description: "Attach JSON Schemas to workflow and task definitions so malformed input is rejected at the boundary instead of failing several tasks later."
---

# Input/Output Schema Validation

A schema is a contract on the shape of data crossing a boundary. Without one, a missing field is discovered by whatever task first dereferences it — usually several tasks in, as a `NullPointerException` in a worker or a silently-null `${...}` expression. With one, and with [enforcement turned on](#turning-enforcement-on), the execution is rejected at the boundary, before any side effect.

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

Two fields are easy to confuse. `schemaVersion` is unrelated to any of this — it is the workflow definition *format* version and should be `2`. `enforceSchema` is the per-definition switch; on `WorkflowDef` it defaults to `true`, so once enforcement is turned on at the server (see below) a definition that declares an `inputSchema` is validated unless you explicitly set it to `false`. On `TaskDef` it defaults to `false`, so a task opts in.

Register it the usual way — schemas travel inside the definition, so there is no separate step:

```shell
curl -X PUT "$CONDUCTOR_SERVER_URL/metadata/workflow" \
  -H 'Content-Type: application/json' \
  -d '[ ... definition above ... ]'
```

## Turning enforcement on

**Validation is off until you turn it on**, and a server upgrade does not turn it on for you. Attaching a schema to a definition therefore changes nothing about how that definition executes — the schema is documentation, and the pickers in the UI read it — until an operator sets:

| Property | Default | Meaning |
|---|---|---|
| `conductor.app.schema-validation.enabled` | `false` | Whether the engine validates payloads against the schemas attached to definitions |

That switch is necessary but not sufficient. A payload is checked only when all three of these hold:

1. `conductor.app.schema-validation.enabled` is `true` on the server;
2. the definition's own `enforceSchema` is `true`;
3. a schema is actually attached at that point.

Enable it deliberately, and on a deployment where you already know which definitions carry schemas: turning it on makes every one of those definitions start rejecting payloads that do not match.

## When validation runs

| Point | Effect on failure |
|---|---|
| Workflow input | The workflow does not start, and nothing is created |
| Task input | The task fails terminally, before the worker sees it |
| Task output | The task fails after the worker returns |
| Workflow output | The workflow fails at completion instead of completing |

A workflow-input failure is reported to the caller: the start request is rejected with `400` and the validation message in the body, and no execution is created. The other three happen inside a running execution, so the validation message becomes the `reasonForIncompletion` on the task or the workflow — visible in the UI and the API, without reading server logs.

Task-input failure is the one that differs: it is a **terminal** failure, not a retriable one. A payload that violates a schema violates it identically on the next attempt, so retrying would only spend the task's retry budget re-submitting the same input. The other three fail normally.

Input validation is the valuable one: it rejects the execution before any task has run, so there is nothing to compensate for. Output validation catches a worker returning the wrong shape, which otherwise surfaces as a downstream failure far from its cause.

## Limits, and what happens at them

**An externalized output is not checked.** A worker that returns its output through external payload storage hands the server a storage path rather than the payload, so there is nothing in hand to validate and the check is skipped. Task input, workflow input and workflow output are unaffected; so is a task whose output is small enough to travel inline.

**System task output is not checked — silently.** Only a task completed by a worker reporting through the task-update API is validated on output. A system task the server runs itself — `HTTP`, `SUB_WORKFLOW`, `EVENT`, `INLINE` and the rest — completes through a path that has no output-validation point, so an `outputSchema` on one of those task definitions is stored and never enforced. That and the externalized output above are the two gaps that pass quietly; the two below do not. A system task's *input* is validated at scheduling like any other task's, and workflow input and workflow output are unaffected.

**A schema that is not `JSON` is refused, not skipped.** An `AVRO` or `PROTOBUF` schema is accepted at registration and returned unchanged, but this server has no validator for it — so rather than let the payload through unchecked, it fails the execution and says why. A definition that both attaches one and opts into enforcement will start failing when you turn enforcement on.

**A schema this server cannot resolve is also refused.** A schema attached by name and version is looked up in the [Schema Registry](schema-registry.md); if nothing is registered under that name and version, the execution fails naming the schema it could not find. The same goes for a schema carrying no `type`, and for one carrying only an `externalRef` — nothing dereferences that field.

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
