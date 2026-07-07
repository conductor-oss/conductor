# 6. Versioning — v0.2.5 → v0.3.0 → v1.0

The biggest practical gotcha in A2A right now is that **two materially different spec
generations are live at once**. The site's `/latest/` already points to **v1.0**, but the
broad SDK and deployed-agent ecosystem is still largely on **v0.3.x** (and plenty on v0.2.5).
They are **not interchangeable on the wire** — enum spellings, polymorphism, and several field
names differ. Pin your target version explicitly.

## 6.1 The two models

| Aspect | **v0.2.x / v0.3.x** (JSON-RPC-first) | **v1.0** (Protobuf-first / ProtoJSON) |
|---|---|---|
| Source of truth | JSON Schema / TS types | `specification/a2a.proto` (pkg `lf.a2a.v1`) — JSON schema is a *generated artifact* |
| `TaskState` | `"submitted"`, `"input-required"`, … | `TASK_STATE_SUBMITTED`, `TASK_STATE_INPUT_REQUIRED`, … (+`TASK_STATE_UNSPECIFIED`) |
| `Message.role` | `"user"` / `"agent"` | `ROLE_USER` / `ROLE_AGENT` (+`ROLE_UNSPECIFIED`) |
| Polymorphism | `kind` discriminator (`"task"`, `"text"`, `"status-update"`, …) | **no `kind`** — JSON-member / wrapper polymorphism (`{ "taskStatusUpdate": {…} }`) |
| `Part` file | `mimeType`; `FileWithBytes.bytes` / `FileWithUri.uri` | `mediaType`; file content via `raw` (bytes) / `url` members |
| Transport on card | `preferredTransport` + `additionalInterfaces[]` (each `{url, transport}`) | `supportedInterfaces[]` (each `{url, protocolBinding, protocolVersion}`; first = preferred) |
| Extended-card flag | `supportsAuthenticatedExtendedCard` (top-level) | `capabilities.extendedAgentCard` |
| `AgentCard.protocolVersion` | `"0.3.0"` | `"1.0"` (Major.Minor; patch doesn't affect compatibility) |
| Task listing | — | new `tasks/list` / `ListTasks` (paginated, with filters) |
| Per-skill security | — | `AgentSkill.security?: string[]` added |
| New errors | — | `ExtensionSupportRequiredError`, `VersionNotSupportedError` |

## 6.2 Well-known path changed
- **v0.2.5:** `https://{domain}/.well-known/agent.json`
- **v0.3.0+ / v1.0:** `https://{domain}/.well-known/agent-card.json`

A client that hard-codes the wrong path won't discover the agent.

## 6.3 JSON-RPC method strings persist across versions

Even in v1.0, the **JSON-RPC wire method strings stay slash-delimited** (`message/send`,
`tasks/get`, …). The PascalCase names (`SendMessage`, `GetTask`) are the abstract-operation /
gRPC service names. A live v1.0 implementer (AWS Bedrock AgentCore) still emits
`"message/send"` while advertising the protocol — confirming the slash strings are the
JSON-RPC transport's wire format. *(The v1.0 JSON-RPC binding section restating this could not
be fetched directly during research — treat the v1.0 slash-string continuation as well-supported
inference rather than a verbatim quote.)*

## 6.4 SDK reality
The official Python SDK (`a2a-sdk`) implements **v1.0** with a **v0.3 compatibility mode**
(`compat/v0_3/` shims), across all three transports. The v1.0 SDK also **renamed/removed
classes** (e.g. `A2AStarletteApplication` and the single-transport `A2AClient` are gone,
replaced by route factories + `ClientFactory`/`Client`). So "which version" affects not just
the wire format but the SDK API you code against. Details in
[07-ecosystem-and-samples.md](07-ecosystem-and-samples.md).

## 6.5 Recommendation for a new integration
- If you need **maximum interoperability today**, target **v0.3.x** wire semantics (lowercase
  enums, `kind`, `/.well-known/agent-card.json`) — most deployed agents and tutorials assume it.
- If you're building **green-field against current SDKs**, target **v1.0** and rely on the
  SDK's v0.3 compatibility mode for older peers.
- Either way, **read the AgentCard's `protocolVersion`** and adapt; don't assume.
