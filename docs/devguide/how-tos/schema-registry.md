---
description: "Store JSON Schemas on the Conductor server under a name and version, so many definitions can reference one contract instead of each embedding its own copy."
---

# Schema Registry

A schema attached to a definition travels inside that definition — see [Input/Output Schema Validation](schema-validation.md). That works, and it stops working the moment two definitions need the same contract: you now have two copies that drift.

The schema registry is the server-side store that fixes this. A schema is saved once under a name and a version, and definitions reference it. It is also what populates the input- and output-schema pickers in the UI.

## The API

Six endpoints under `/api/schema`. The path, method and parameters match the contract the Conductor SDKs were written against, so an existing schema client needs no change to talk to this server.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/schema?newVersion=false` | Save one or more schemas |
| `GET` | `/api/schema?short=false` | List every version of every schema |
| `GET` | `/api/schema/{name}` | Read the highest version registered under a name |
| `GET` | `/api/schema/{name}/{version}` | Read one version |
| `DELETE` | `/api/schema/{name}` | Remove every version under a name |
| `DELETE` | `/api/schema/{name}/{version}` | Remove one version |

### Saving

The body is a **list**, and `POST` returns `200` with no body.

```shell
curl -X POST "$CONDUCTOR_SERVER_URL/schema" \
  -H 'Content-Type: application/json' \
  -d '[{
    "name": "customerInput",
    "version": 1,
    "type": "JSON",
    "data": {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "properties": { "customerId": { "type": "string" } },
      "required": ["customerId"]
    }
  }]'
```

A bare object is accepted too, and treated as a one-element list. Several SDK clients post one, so this is not a shorthand you need to avoid.

### Reading

```shell
curl "$CONDUCTOR_SERVER_URL/schema/customerInput"
```

```json
{
    "createTime": 1788197572423,
    "updateTime": 0,
    "name": "customerInput",
    "version": 1,
    "type": "JSON",
    "data": {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "properties": {
            "customerId": {
                "type": "string"
            }
        },
        "required": [
            "customerId"
        ]
    }
}
```

`GET /api/schema/{name}/{version}` reads one version instead of the latest. Both return `404` when nothing is registered, which is how you tell a missing schema from an empty one:

```json
{"status":404,"message":"No such schema found by name customerInput","instance":"5f0694ae4d22","retryable":false}
```

### Listing

`GET /api/schema` returns every version of every schema, bodies included. `?short=true` returns names and versions only — this is what a picker asks for, so that opening a dropdown does not transfer every schema document on the server:

```shell
curl "$CONDUCTOR_SERVER_URL/schema?short=true"
```

```json
[
    {
        "createTime": 0,
        "updateTime": 0,
        "name": "customerInput",
        "version": 1
    },
    {
        "createTime": 0,
        "updateTime": 0,
        "name": "customerInput",
        "version": 2
    }
]
```

The zeroed timestamps are a placeholder, not a real creation date — the short listing omits them along with the schema body. Read the full record if you need them.

## Versioning

A schema is addressed by name **and** version; the pair is unique. `version` defaults to `1`.

There are two ways to save, and the difference matters:

| `newVersion` | Effect |
|---|---|
| `false` (default) | Overwrites whatever is stored at the version in the payload |
| `true` | Stores at one past the highest version currently registered under that name |

Use `newVersion=true` to evolve a contract. Definitions pinned to an older version keep resolving to the schema they were written against:

```shell
curl -X POST "$CONDUCTOR_SERVER_URL/schema?newVersion=true" \
  -H 'Content-Type: application/json' \
  -d '[{ "name": "customerInput", "type": "JSON", "data": { "...": "..." } }]'
```

```shell
curl "$CONDUCTOR_SERVER_URL/schema/customerInput"   # now version 2
curl "$CONDUCTOR_SERVER_URL/schema/customerInput/1" # still the original
```

Use the default to correct a version in place — a typo in a description, a field you meant to make optional. Anything referencing that version sees the correction, which is the point and also the risk.

Two simultaneous `newVersion=true` saves of the same name can overwrite each other. The server reads the highest version and saves one past it, with nothing between the two steps, so both writers can read the same maximum, land on the same version and leave only the later one stored. Concurrent registration under one name is last-writer-wins; serialize those saves if losing one would matter.

Deleting is version-aware in the same way. `DELETE /api/schema/{name}/{version}` removes one version and leaves the rest of the history; `DELETE /api/schema/{name}` removes all of it. Both return `404` when there was nothing to remove, so a delete that answers `200` has actually deleted something — worth knowing if you script cleanup that runs whether or not the schema is there.

## The management screen

The UI has a screen for the registry, so routine work does not need `curl`. Find it under **Definitions → Schemas**, at `/schemas`.

The list holds one row per schema rather than one per version: the name links to the editor, and the row carries the schema's type, its latest version, how many versions exist, and when it was created. Two actions sit on each row — **Clone**, which copies the contract under a new name starting again at version 1, and **Delete**, which removes the schema and every version of it.

Opening a schema shows its body in a JSON editor, with a version selector for its history. From there:

| Action | What it does |
|---|---|
| **Save** | Overwrites the version on screen. Anything referencing that version sees the change, so this one asks for confirmation first |
| **Save as new version** | Stores the edited body at a new version. The server allocates the number, so two people saving at once cannot collide |
| **Delete version** | Removes the version on screen and keeps the rest of the history |
| **Reset** | Discards local edits and reloads the stored version |
| **Download** | Saves the schema on screen as a `.json` file |

**New schema** opens the same editor on a JSON template. Saving it registers version 1.

The editor writes `JSON` schemas only. A stored `AVRO` or `PROTOBUF` schema opens read-only, with a note saying it is not validated by this server — the screen will not let you edit a schema whose type nothing here enforces. Replace one of those through the API.

The input- and output-schema pickers on the Simple Task, Yield Task, Workflow Properties and Task Definition forms read the same registry, and populate as soon as the server serves `/api/schema`. On the Simple Task, Yield Task and Workflow Properties forms, a picker naming a schema the registry does not hold is flagged, so a dangling reference shows up in the editor rather than at runtime. The Task Definition form does not flag one.

Give every JSON schema a `$schema` line, as the examples above do. Without one the server cannot tell which JSON Schema version to apply, and a definition enforcing that schema silently validates nothing. See [Input/Output Schema Validation](schema-validation.md).

## Server properties

The registry itself needs no configuration, and neither does enforcement: whether a definition's schema is enforced is decided by that definition's own `enforceSchema` flag, not by a server setting. See [Input/Output Schema Validation](schema-validation.md). The cache is the one thing configurable here, and it is off by default.

| Property | Default | Meaning |
|---|---|---|
| `conductor.app.schema-cache.ttl` | `0` | How long a read stays cached. Zero disables the cache; there is no separate on/off flag |
| `conductor.app.schema-cache.max-size` | `1000` | Maximum cached entries, counting by-version and latest-by-name lookups separately |

A non-zero `ttl` is also your staleness bound. Invalidation on save and delete reaches only the node that served the write, so on a multi-node deployment every other node keeps serving the old schema until the entry expires. Set it to something you would be comfortable waiting out after an edit.

## Storage

Schemas persist on MySQL, PostgreSQL, SQLite and Redis, in a `meta_schema_def` table (or, on Redis, a hash per schema name). The SQL backends create it through a migration of the registry's own, separate from the main Conductor migrations.

There is no Cassandra implementation. A server configured with `conductor.db.type=cassandra` fails at startup rather than accepting schema writes it cannot store.

## Limitations

Four things you cannot infer from the API:

**All three schema types are stored; only `JSON` is validated.** You can save an `AVRO` or `PROTOBUF` schema and read it back unchanged, but nothing on this server validates a payload against it, and the management screen shows it read-only for that reason.

**`createdBy` and `updatedBy` are never populated.** The API is unauthenticated, so there is no principal to attribute a write to, and the fields are absent from responses rather than empty. `createTime` and `updateTime` are set normally.

**The picker's inline edit and preview buttons are not shown.** In the schema pickers on the task, workflow and task-definition forms, the buttons that open a schema for editing or preview without leaving the form come from a UI plugin, and this build registers none. Selecting an existing schema works; creating and editing are done on the management screen or through this API.

**`externalRef` is stored and returned, and nothing resolves it.** If you save a schema carrying only an `externalRef`, you get that field back exactly as you sent it — the server does not fetch what it points at.

## Related pages

- [Input/Output Schema Validation](schema-validation.md) — attaching a schema to a definition
- [Task Definition reference](../../documentation/configuration/taskdef.md)
- [Workflow Definition reference](../../documentation/configuration/workflowdef/index.md)
