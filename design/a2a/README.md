# A2A (Agent2Agent) Protocol — Design Notes

> A working understanding of the A2A protocol, derived from the official spec
> (`a2a-protocol.org`), the `a2aproject` GitHub org, Google's launch material, and
> the purchasing-concierge codelab. These notes exist to inform how Conductor might
> interoperate with A2A. Where a statement is an inference rather than spec text, it
> is marked **(inference)**.
>
> Researched: 2026-06-18. A2A is moving fast — re-verify field/method names against
> the version you target before implementing (see [06-versioning.md](06-versioning.md)).

## What A2A is, in one paragraph

A2A is an open, vendor-neutral protocol that lets **independent AI agents discover one
another and collaborate as peers** over standard web transports (HTTP + JSON-RPC 2.0,
gRPC, or HTTP+JSON). An agent publishes a machine-readable **Agent Card** describing its
identity, skills, supported transports, and authentication. A **client agent** finds a
**remote agent**, sends it a **Message**, and the remote agent either replies inline or
opens a stateful **Task** that progresses through a defined lifecycle, emits **Artifacts**
(outputs), and can stream updates or call back via webhooks for long-running work. Crucially,
agents stay **opaque** to each other — they do not share memory, tools, or internal logic;
they cooperate only through the standardized message/task surface. A2A was announced by
Google in April 2025 and donated to the **Linux Foundation** in June 2025; it reached
**v1.0** with an 8-company technical steering committee.

## The one thing to remember: A2A vs MCP

They are **complementary**, not competing:

- **MCP** connects an agent **down to its tools** — APIs, databases, functions (agent → tooling).
- **A2A** connects an agent **across to other agents** — as collaborating peers (agent → agent).

> "A2A is about agents *partnering* on tasks, while MCP is more about agents *using* capabilities." — official docs

A typical agent uses **MCP internally** to drive its own tools and **A2A externally** to
collaborate with other agents. See [02-a2a-vs-mcp.md](02-a2a-vs-mcp.md).

## Heads-up: two live spec generations

This is the biggest practical gotcha, so it is called out everywhere in these notes.

| | **v0.2.x / v0.3.x** (de-facto standard today) | **v1.0** (`/latest/` on the site) |
|---|---|---|
| Model | JSON-RPC-first | Protobuf-first (`a2a.proto`, ProtoJSON) |
| Enums | lowercase strings (`"input-required"`) | `SCREAMING_SNAKE_CASE` (`TASK_STATE_INPUT_REQUIRED`) |
| Roles | `"user"` / `"agent"` | `ROLE_USER` / `ROLE_AGENT` |
| Polymorphism | `kind` discriminator field | JSON member / wrapper based (no `kind`) |
| Well-known path | `/.well-known/agent.json` | `/.well-known/agent-card.json` |
| Transport on card | `preferredTransport` + `additionalInterfaces[]` | `supportedInterfaces[]` |

Most of these notes lead with the **v0.3.x JSON-RPC model** (what the broad SDK
ecosystem still implements) and flag v1.0 deltas. Pin your target version explicitly.
Full breakdown in [06-versioning.md](06-versioning.md).

## Reading order

| # | Doc | What's in it |
|---|---|---|
| 1 | [01-overview-and-motivation.md](01-overview-and-motivation.md) | The problem, the vision, the 5 design principles, governance & timeline, core actors |
| 2 | [02-a2a-vs-mcp.md](02-a2a-vs-mcp.md) | The complementary relationship, the auto-repair-shop analogy, opaque agents |
| 3 | [03-data-model.md](03-data-model.md) | Agent Card, Task & lifecycle, Message, Part, Artifact, events, push config |
| 4 | [04-protocol-mechanics.md](04-protocol-mechanics.md) | Transports, RPC methods, streaming (SSE), push notifications, error codes, "life of a task" |
| 5 | [05-security.md](05-security.md) | Secure-by-default, security schemes, header-based identity, extended card, webhook security |
| 6 | [06-versioning.md](06-versioning.md) | v0.2.5 → v0.3.0 → v1.0, what changed, which to target |
| 7 | [07-ecosystem-and-samples.md](07-ecosystem-and-samples.md) | Linux Foundation governance, canonical proto spec, official SDKs, samples, use cases |
| 8 | [08-conductor-implications.md](08-conductor-implications.md) | **Analysis:** how A2A maps onto Conductor (workflows-as-agents, A2A client task, lifecycle mapping) |
| 9 | [09-durable-a2a.md](09-durable-a2a.md) | **Durability:** what "durable A2A" must mean for the claim to hold — durability properties, the exactly-once boundary, the mechanisms (deterministic messageId, liveness guards, push backstop), and proof obligations |
| 10 | [10-a2a-server.md](10-a2a-server.md) | **A2A server (Direction A):** exposing Conductor workflows as A2A agents — one agent per workflow, opt-in, status mapping, idempotent-start durability, auth |

Docs 1–7 describe A2A as it exists. Docs 8–10 are repo-specific design/implementation (the
Conductor A2A **client** in 8–9 and the **server** in 10).

## Glossary (quick)

| Term | Meaning |
|---|---|
| **Client agent** | Initiates communication; formulates and sends tasks on behalf of a user |
| **Remote agent** (A2A server) | Exposes an A2A HTTP endpoint; receives requests, runs tasks, returns results |
| **Agent Card** | JSON descriptor of an agent's identity, skills, transports, and auth — the discovery unit |
| **Skill** | A discrete advertised capability of an agent |
| **Task** | A stateful unit of work with a unique id and a defined lifecycle |
| **contextId** | Server-generated id that groups related tasks/turns into one conversation/session |
| **Message** | One turn of communication (role `user` or `agent`), made of Parts |
| **Part** | Atomic content unit: `TextPart`, `FilePart`, or `DataPart` |
| **Artifact** | A tangible output produced by a task, made of Parts |
| **Opaque agent** | An agent treated as a black box — no shared memory/tools/state |
