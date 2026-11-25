---
description: "The Conductor OSS repositories — server, SDKs, CLI, and skills — and which one owns the code you want to change."
---

# Repositories

Conductor is split across several repositories under the [conductor-oss](https://github.com/conductor-oss) organisation. Knowing which one owns your change saves a redirected pull request.

## Server and docs { .wide-first-col }

| Repository | Contains |
|---|---|
| [conductor-oss/conductor](https://github.com/conductor-oss/conductor) | The server: core engine, system tasks, persistence modules, REST and gRPC APIs, UI, and this documentation site under `docs/` |

Almost everything server-side lives here, including the persistence and queue backends (`postgres-persistence`, `mysql-persistence`, `redis-persistence`, `cassandra-persistence`, `sqlite-persistence`), the storage modules, and the AI/agent modules.

Documentation lives in the same repo as the code it describes, which is deliberate: a change to an endpoint and the change to its docs page belong in one pull request.

## Client SDKs

Each language SDK is its own repository with its own release cadence.

| Language | Repository | Docs |
|---|---|---|
| Java | [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk) | [Java SDK](../../documentation/clientsdks/java-sdk.md) |
| Python | [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk) | [Python SDK](../../documentation/clientsdks/python-sdk.md) |
| JavaScript | [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk) | [JavaScript SDK](../../documentation/clientsdks/js-sdk.md) |
| Go | [conductor-oss/go-sdk](https://github.com/conductor-oss/go-sdk) | [Go SDK](../../documentation/clientsdks/go-sdk.md) |
| C# | [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk) | [C# SDK](../../documentation/clientsdks/csharp-sdk.md) |
| Ruby | [conductor-oss/ruby-sdk](https://github.com/conductor-oss/ruby-sdk) | [Ruby SDK](../../documentation/clientsdks/ruby-sdk.md) |
| Rust | [conductor-oss/rust-sdk](https://github.com/conductor-oss/rust-sdk) | [Rust SDK](../../documentation/clientsdks/rust-sdk.md) |
| Clojure | [conductor-oss/clojure-sdk](https://github.com/conductor-oss/clojure-sdk) | — |

A change to how a worker polls, retries, or serialises payloads belongs in the SDK repo. A change to what the server accepts belongs in the server repo. Anything that alters the wire contract needs both, and the server change should merge first so the SDK has something to talk to.

## Tooling { .wide-first-col }

| Repository | Contains |
|---|---|
| [conductor-oss/conductor-cli](https://github.com/conductor-oss/conductor-cli) | The `conductor` CLI — workflow and task management, agents, scheduling, local server control |
| [conductor-oss/conductor-skills](https://github.com/conductor-oss/conductor-skills) | Skills for coding agents working with Conductor |

## Which repo owns my change?

| What you are changing | Repository |
|---|---|
| Engine behaviour, a system task, an operator | `conductor` |
| A REST or gRPC endpoint | `conductor` |
| A persistence or queue backend | `conductor` |
| A documentation page | `conductor`, under `docs/` |
| The UI | `conductor`, under `ui/` |
| Worker polling, retries, client-side transfer | the SDK repo for that language |
| A CLI command or flag | `conductor-cli` |
| The wire contract between client and server | `conductor` first, then each SDK |

## Related pages

- [Contribute overview](index.md)
- [Contribution Guide](../contributing.md)
- [Best Practices](best-practices.md)
