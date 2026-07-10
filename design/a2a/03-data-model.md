# 3. Core Data Model

> Field names and enum values below use the **v0.3.x JSON-RPC model** (lowercase enums,
> `kind` discriminators) — what the broad SDK ecosystem implements. v1.0 (ProtoJSON) renames
> several of these; deltas are flagged inline and collected in [06-versioning.md](06-versioning.md).
> Types are shown as TypeScript-ish interfaces for readability; the canonical source is
> `specification/a2a.proto`.

## 3.0 The object graph at a glance

```
AgentCard ── advertises ──► AgentSkill[]            (discovery)
   │
   └─ capabilities: AgentCapabilities

contextId  ── groups ──►  Task, Task, Task …          (a conversation/session)
   Task
    ├─ status:    TaskStatus { state, message?, timestamp? }
    ├─ history:   Message[]      (the turns of this task)
    └─ artifacts: Artifact[]     (the outputs of this task)

Message ── made of ──► Part[]   (Part = TextPart | FilePart | DataPart)
Artifact ── made of ──► Part[]
```

- **contextId** is the conversation/session. It groups one or more related **Tasks**.
- A **Task** is one stateful job inside a context. Its `history` holds that job's turns; its
  `artifacts` holds its outputs.
- **Messages** and **Artifacts** are both built from typed **Parts**.

## 3.1 AgentCard — the discovery unit

A JSON document describing "an agent's identity, capabilities, endpoint, skills, and
authentication requirements." It is how a client finds and selects a remote agent.

### Hosting & discovery
- **Well-known URI** (RFC 8615): `https://{domain}/.well-known/agent.json` (**v0.2.5**) →
  `…/.well-known/agent-card.json` (**v0.3.0+**).
- **Three discovery mechanisms:** (1) Well-Known URI on the agent's domain; (2) curated
  **catalogs / registries** (enterprise, public, or domain-specific); (3) **direct
  configuration** (client is pre-given the card URL or content).

### Fields (v0.3.x)
```ts
interface AgentCard {
  protocolVersion: string;          // A2A version the card conforms to (e.g. "0.3.0")
  name: string;                     // human-readable agent name
  description: string;              // human-readable description
  url: string;                      // base endpoint URL for the preferred transport
  preferredTransport: string;       // transport at `url`; defaults to "JSONRPC". REQUIRED in v0.3.0
  additionalInterfaces?: AgentInterface[]; // other (url, transport) pairs supported
  iconUrl?: string;
  provider?: AgentProvider;         // the org providing the agent
  version: string;                  // agent/implementation version (provider-defined)
  documentationUrl?: string;
  capabilities: AgentCapabilities;  // optional protocol features supported
  securitySchemes?: { [name: string]: SecurityScheme };  // OpenAPI-style auth schemes
  security?: { [name: string]: string[] }[];             // security requirements (scheme → scopes)
  defaultInputModes: string[];      // default accepted input MIME types (e.g. "text/plain")
  defaultOutputModes: string[];     // default produced output MIME types
  skills: AgentSkill[];             // the capabilities the agent offers
  supportsAuthenticatedExtendedCard?: boolean;  // serves a richer card to authed clients
  signatures?: AgentCardSignature[];            // JWS signatures over the card (v0.3.0+)
}
```

Notes:
- `defaultInputModes` / `defaultOutputModes` are **MIME types** (`"text/plain"`,
  `"application/json"`, `"image/png"`), applied to every skill unless a skill overrides them.
- `securitySchemes` (a map) + `security` (a requirements array) mirror **OpenAPI** security
  definitions. See [05-security.md](05-security.md).
- `preferredTransport` is documented as **REQUIRED** from v0.3.0 (was optional-looking in
  v0.2.5).

### Supporting types
```ts
interface AgentProvider { organization: string; url: string; }

interface AgentInterface {              // a (URL, transport) the agent is reachable at
  url: string;
  transport: string;                    // a TransportProtocol value: "JSONRPC" | "GRPC" | "HTTP+JSON"
}

interface AgentCardSignature {          // JWS over the card, for integrity/authenticity
  protected: string;                    // base64url JWS protected header (RFC 7515)
  signature: string;                    // base64url signature
  header?: object;                      // optional unprotected JWS header
}
```

### Authenticated Extended Card
If `supportsAuthenticatedExtendedCard` is `true`, an authenticated client can GET a richer
card via `agent/getAuthenticatedExtendedCard` (v0.3.x) — it "may contain additional details
or skills not present in the public card." (v1.0 moves the flag to
`capabilities.extendedAgentCard`.)

## 3.2 AgentSkill — an advertised capability
```ts
interface AgentSkill {
  id: string;            // unique within this agent
  name: string;          // human-readable
  description: string;   // what the skill does
  tags: string[];        // keywords/categories for discoverability
  examples?: string[];   // example prompts / use cases
  inputModes?: string[]; // MIME types — overrides card defaults for this skill
  outputModes?: string[];// MIME types — overrides card defaults for this skill
}
```
> v1.0 adds a per-skill `security?: string[]`. Not present in v0.2.5/0.3.x.

## 3.3 AgentCapabilities — optional protocol features
```ts
interface AgentCapabilities {
  streaming?: boolean;              // supports SSE (message/stream, tasks/resubscribe)
  pushNotifications?: boolean;      // supports webhook push notifications
  stateTransitionHistory?: boolean; // exposes detailed status-change history
  extensions?: AgentExtension[];    // declared protocol extensions
}

interface AgentExtension {
  uri: string;                      // identifies the extension
  description?: string;
  required?: boolean;               // must a client understand it to interact?
  params?: { [k: string]: any };    // extension-specific config
}
```

## 3.4 Task — the stateful unit of work

Created by the server when a message requires stateful/long-running work.
```ts
interface Task {
  id: string;                       // unique task id (server-generated, e.g. UUID)
  contextId: string;                // groups related tasks/interactions
  status: TaskStatus;               // current state (+ optional message + timestamp)
  history?: Message[];              // the conversation turns of this task
  artifacts?: Artifact[];           // outputs produced by this task
  metadata?: Record<string, any>;
  kind: "task";                     // discriminator literal
}

interface TaskStatus {
  state: TaskState;                 // current lifecycle state (see 3.5)
  message?: Message;                // e.g. the agent's reply or its prompt for input
  timestamp?: string;              // ISO 8601
}
```

**Task ↔ contextId ↔ Message ↔ Artifact:**
- A Task **groups its messages** in `history` (ordered turns) and **its outputs** in `artifacts`.
- `contextId` **groups related Tasks** — "for maintaining context across multiple related
  tasks or interactions." Several Tasks in the same broader session share one `contextId`.
- Messages tie back via `Message.taskId` and can cite sibling tasks via
  `Message.referenceTaskIds`. Both Message and Task carry `contextId`.

## 3.5 TaskState — the lifecycle enum (v0.3.x: lowercase strings)

| Member | String value | Meaning | Class |
|---|---|---|---|
| Submitted | `"submitted"` | Acknowledged, not yet started | non-terminal |
| Working | `"working"` | Actively being processed | non-terminal |
| InputRequired | `"input-required"` | Agent needs **more user input** to proceed | **paused / resumable** |
| AuthRequired | `"auth-required"` | **Authentication required** to proceed | **paused / resumable** |
| Completed | `"completed"` | Finished successfully | **terminal** |
| Canceled | `"canceled"` | Canceled before completion | **terminal** |
| Failed | `"failed"` | Finished with an error | **terminal** |
| Rejected | `"rejected"` | Agent declined to perform the task | **terminal** |
| Unknown | `"unknown"` | Indeterminate state | (treat as non-actionable sentinel — *(inference)*) |

- **Terminal:** `completed`, `canceled`, `failed`, `rejected` — no further work on that task id.
- **Paused / resumable:** `input-required`, `auth-required` — the client resumes by sending
  another message with the **same `taskId`** (supplying the input/credentials), without losing
  prior work.
- v1.0 prefixes these: `TASK_STATE_SUBMITTED`, `…_WORKING`, `…_INPUT_REQUIRED`,
  `…_AUTH_REQUIRED`, `…_COMPLETED`, `…_CANCELED`, `…_FAILED`, `…_REJECTED`, plus
  `TASK_STATE_UNSPECIFIED` (the zero/unknown value).

## 3.6 Message — one turn of communication
```ts
interface Message {
  role: "user" | "agent";           // "user" = from client; "agent" = from remote agent
  parts: Part[];                    // the content
  messageId: string;                // unique id set by the creator
  taskId?: string;                  // the task this message relates to
  contextId?: string;               // the context this message belongs to
  metadata?: Record<string, any>;
  referenceTaskIds?: string[];      // other tasks cited as context
  extensions?: string[];            // URIs of extensions used in this message
  kind: "message";                  // discriminator literal
}
```
> v1.0: `ROLE_USER` / `ROLE_AGENT` (+ `ROLE_UNSPECIFIED`).

## 3.7 Part — the content union

The fundamental content container in Messages and Artifacts, discriminated by `kind`.
```ts
type Part = TextPart | FilePart | DataPart;

interface TextPart { kind: "text"; text: string; metadata?: Record<string, any>; }

interface FilePart {
  kind: "file";
  file: FileWithBytes | FileWithUri;   // exactly one variant
  metadata?: Record<string, any>;
}
interface FileWithBytes { name?: string; mimeType?: string; bytes: string; } // base64; no `uri`
interface FileWithUri   { name?: string; mimeType?: string; uri: string;   } // URL; no `bytes`

interface DataPart { kind: "data"; data: Record<string, any>; metadata?: Record<string, any>; }
```
The discriminator between the two file variants is `bytes` (inline base64) vs `uri`
(reference) — mutually exclusive.
> v1.0 drops `kind`, uses JSON members (`{ "text": … }`, `{ "file": { "fileWithUri" | "fileWithBytes": … } }`, `{ "data": … }`), and renames `mimeType` → `mediaType`.

## 3.8 Artifact — a task output
```ts
interface Artifact {
  artifactId: string;               // unique id
  name?: string;
  description?: string;
  parts: Part[];                    // the content
  metadata?: Record<string, any>;
  extensions?: string[];
}
```

## 3.9 Streaming events (over SSE)

Emitted when `capabilities.streaming` is true (via `message/stream` / `tasks/resubscribe`).
```ts
interface TaskStatusUpdateEvent {
  taskId: string;
  contextId: string;
  kind: "status-update";
  status: TaskStatus;
  final?: boolean;                  // true ⇒ last event; server closes the stream
  metadata?: Record<string, any>;
}

interface TaskArtifactUpdateEvent {
  taskId: string;
  contextId: string;
  kind: "artifact-update";
  artifact: Artifact;               // the artifact, or a chunk of it
  append?: boolean;                 // true ⇒ append parts to a previously-sent artifact
  lastChunk?: boolean;              // true ⇒ final chunk of this artifact
  metadata?: Record<string, any>;
}
```
> v1.0 wraps these instead of using `kind`: `{ "taskStatusUpdate": { … } }`,
> `{ "taskArtifactUpdate": { … } }`.

## 3.10 Push-notification objects
```ts
interface PushNotificationConfig {
  id?: string;                      // config id (a task can have several)
  url: string;                      // client webhook URL the server POSTs to
  token?: string;                   // client token echoed back for validation
  authentication?: PushNotificationAuthenticationInfo; // how the server auths TO the webhook
}

interface PushNotificationAuthenticationInfo {
  schemes: string[];                // e.g. ["Bearer"]
  credentials?: string;             // scheme-specific
}

interface TaskPushNotificationConfig {            // params/result of the pushNotificationConfig RPCs
  taskId: string;
  pushNotificationConfig: PushNotificationConfig;
}
```

## 3.11 `kind` discriminator quick reference (v0.3.x)

| Object | `kind` |
|---|---|
| Task | `"task"` |
| Message | `"message"` |
| TextPart | `"text"` |
| FilePart | `"file"` |
| DataPart | `"data"` |
| TaskStatusUpdateEvent | `"status-update"` |
| TaskArtifactUpdateEvent | `"artifact-update"` |
