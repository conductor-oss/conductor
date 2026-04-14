# Conductor OSS Roadmap

<!--
  machine-readable: true
  format: markdown-tables
  last_reviewed: 2026-04-12
  statuses: [Done, Partial, Planned]
-->

> **Status key:** `Done` = shipped and available, `Partial` = partially implemented, `Planned` = not yet started.

## System Tasks and Operators

| Status | Item | Details |
|--------|------|---------|
| Done | LLM text completion | `LLM_TEXT_COMPLETE` system task in `ai/` module |
| Done | LLM chat completion with memory | `LLM_CHAT_COMPLETE` system task in `ai/` module |
| Done | LLM embedding generation | `LLM_GENERATE_EMBEDDINGS` system task in `ai/` module |
| Done | Improved While loop (DO_WHILE) | `keepLastN` iteration cleanup, `items` list iteration, `loopIndex`/`loopItem` injection |
| Done | Python scripting for INLINE task | `PythonEvaluator` via GraalVM polyglot alongside JavaScript |
| Done | Database task | System task for relational and NoSQL database operations |
| Planned | HTTP task polling | Polling/retry support for the HTTP system task |
| Planned | For..Each operator | Parallel and sequential iteration operator |
| Planned | Try..Catch operator | Task-level error handling without failing the workflow |

## APIs

| Status | Item | Details |
|--------|------|---------|
| Done | Synchronous workflow execution | `POST /api/workflow/execute/{name}/{version}` with configurable wait time and `waitUntilTaskRef` |
| Done | Update tasks synchronously | `POST /tasks/{workflowId}/{taskRefName}/{status}/sync` returns updated workflow |
| Planned | Update workflow variables | API to update workflow variables during execution |

## Type Safety

| Status     | Item | Details |
|------------|------|---------|
| Done       | JSON Schema validation | `JsonSchemaValidator` validates workflow input and task I/O against JSON schemas |
| Planned    | Protobuf support | Type-safe workflows and workers via protobuf definitions |
| Incubating | Code generation for workers | Scaffold worker code from schema definitions using the CLI |

## CLI

| Status | Item | Details |
|--------|------|---------|
| Done | Conductor CLI | Go-based CLI in `conductor-cli/` repo. Manages metadata, workflow executions, and server lifecycle |


## Testing and Debugging

| Status | Item | Details |
|--------|------|---------|
| Planned | Workflow debugger | Full debugger experience: breakpoints, step-through, variable inspection, rewind to previous task, attach to running execution, remote task debugging via SDKs |

## Maintenance

| Status | Item | Details |
|--------|------|---------|
| Done | Deprecate Elasticsearch 6 | ES6 module replaced with deprecation stub |
| Done | Elasticsearch 7 and 8 support | `es7-persistence` and `es8-persistence` modules (ES8 uses Java API client) |
| Done | JOIN task performance | `FORK_JOIN` synchronous join mode (`joinMode=SYNC`) reduces polling overhead |
