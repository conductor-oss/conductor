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

Enforcement is decided entirely by the definition. There is no server property to set and nothing to restart. A payload is checked when both of these hold:

1. the definition's own `enforceSchema` is `true`;
2. a schema is actually attached at that point.

`enforceSchema` defaults to `false`, which is what keeps a schema attached for documentation from rejecting work. Attaching a schema changes nothing about how a definition executes until you also set that flag on the same definition — so enforcement arrives one definition at a time, as you edit each one, rather than all at once across a deployment.

The corollary is that setting `enforceSchema` takes effect on the next execution of that definition. Set it on a definition whose schema you have not checked against real traffic and that definition starts rejecting payloads immediately, so treat it as the change it is: register the schema, confirm it matches what callers actually send, then turn the flag on.

## When validation runs

| Point | Effect on failure |
|---|---|
| Workflow input | The workflow does not start, and nothing is created |
| Task input | The task fails terminally, before the worker sees it |
| Task output | The task fails terminally after the worker returns |
| Workflow output | The workflow fails at completion instead of completing |

A workflow-input failure is reported to the caller: the start request is rejected with `400` and the validation message in the body, and no execution is created. The other three happen inside a running execution, so the validation message becomes the `reasonForIncompletion` on the task or the workflow — visible in the UI and the API, without reading server logs.

Both task failures are **terminal**, not retriable. An input that violates a schema violates it identically on the next attempt, and an output the definition refuses is the same shape whenever the task is run again — so in neither case does a retry do anything but spend the task's retry budget on the same outcome. The workflow-level failures end the execution, so retrying does not arise.

Input validation is the valuable one: it rejects the execution before any task has run, so there is nothing to compensate for. Output validation catches a worker returning the wrong shape, which otherwise surfaces as a downstream failure far from its cause.

## Limits, and what happens at them

**An externalized output is not checked.** A worker that returns its output through external payload storage hands the server a storage path rather than the payload, so there is nothing in hand to validate and the check is skipped. Task input, workflow input and workflow output are unaffected; so is a task whose output is small enough to travel inline.

**Some system task output is checked, and some is not.** A synchronous system task that finishes inside its `execute(...)` step — `INLINE`, `SET_VARIABLE` and the like — has its output validated in the decider, and fails terminally like any other task. Two kinds are not covered: an asynchronous system task such as `HTTP` or `SUB_WORKFLOW`, and a synchronous one that completes during scheduling instead. For those, an `outputSchema` on the task definition is stored and never enforced. That and the externalized output above pass quietly, and so does the unresolvable reference described below; the non-`JSON` and typeless schemas below are the ones that fail loudly instead. Every system task's *input* is validated at scheduling like any other task's, and workflow input and workflow output are unaffected.

**A schema that is not `JSON` is refused, not skipped.** An `AVRO` or `PROTOBUF` schema is accepted at registration and returned unchanged, but this server has no validator for it — so rather than let the payload through unchecked, it fails the execution and says why. A definition that both attaches one and opts into enforcement will start failing when you turn enforcement on.

**A schema carrying no `type`, or only an `externalRef`, is refused too.** Neither names a document this server can check against — nothing dereferences `externalRef` — so both fail the execution and say so.

**A reference the registry does not hold stops enforcing, quietly.** A schema attached by name and version is looked up in the [Schema Registry](schema-registry.md); if nothing is registered under that name and version there is no document to validate against, so the payload goes through unchecked rather than failing. The miss increments the `schema_registry_miss` counter, tagged with the schema name — that counter is the only signal, so watch it if you rely on enforcement. A registered document the validator cannot read or use behaves the same way and is logged. Both are errors in the definition rather than in the payload, which is why neither is charged to the caller; the cost is that a reference pointing at nothing enforces nothing.

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
