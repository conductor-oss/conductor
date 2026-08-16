# 4. Protocol Mechanics — Transports, Methods, Streaming, Push, Errors

> Method strings use the **v0.3.x JSON-RPC** wire names (e.g. `message/send`), which live
> implementers still emit even when advertising newer versions. The PascalCase names in
> parentheses are the abstract-operation / gRPC service method names.

## 4.1 Transports

A2A defines **three transport bindings**, treated as equal normative bindings. JSON-RPC 2.0
over HTTP is the de-facto default.

1. **JSON-RPC 2.0 over HTTP(S)** — single POST endpoint; requests/responses are JSON-RPC 2.0
   envelopes. Default when `preferredTransport` is unspecified.
2. **gRPC over HTTP/2** — protobuf service `A2AService` (`SendMessage`, `GetTask`, …). The
   `specification/a2a.proto` file is the authoritative normative definition of all protocol
   objects; the REST mapping is baked in via `google.api.http` annotations.
3. **HTTP+JSON / REST** — RESTful resource-style binding.

### TransportProtocol enum
`"JSONRPC"`, `"GRPC"`, `"HTTP+JSON"` (v1.0 also allows custom URI binding identifiers).

### Declaring transports on the Agent Card
- **v0.3.x:** `url` + `preferredTransport` (defaults to `"JSONRPC"`) + `additionalInterfaces[]`
  (each an `AgentInterface { url, transport }`). Lets one agent expose the same functionality
  over several transports/endpoints.
- **v1.0:** `supportedInterfaces[]` (ordered; first entry = preferred).

### Negotiation & functional equivalence
A client should "select the first supported transport" in the card's preference order and use
that transport's URL. When an agent supports multiple transports, all of them **MUST**:
- provide the same set of operations and capabilities;
- return semantically equivalent results for the same requests;
- map errors consistently using protocol-specific codes;
- support the same authentication schemes declared in the Agent Card.

A *Method Mapping Reference* and *Error Code Mappings* in the spec keep the bindings aligned.

## 4.2 RPC methods

JSON-RPC envelope: `{ "jsonrpc": "2.0", "id": …, "method": "<string>", "params": {…} }`.

| Method (JSON-RPC) | gRPC op | Params | Result |
|---|---|---|---|
| `message/send` | SendMessage | `MessageSendParams` | **`Message` \| `Task`** |
| `message/stream` | SendStreamingMessage | `MessageSendParams` | SSE stream of `Message` \| `Task` \| `TaskStatusUpdateEvent` \| `TaskArtifactUpdateEvent` |
| `tasks/get` | GetTask | `TaskQueryParams` | `Task` |
| `tasks/cancel` | CancelTask | `TaskIdParams` | `Task` (updated) |
| `tasks/resubscribe` | SubscribeToTask | `TaskIdParams` | SSE stream (same union as `message/stream`) |
| `tasks/pushNotificationConfig/set` | CreateTaskPushNotificationConfig | `TaskPushNotificationConfig` | `TaskPushNotificationConfig` |
| `tasks/pushNotificationConfig/get` | GetTaskPushNotificationConfig | `GetTaskPushNotificationConfigParams` | `TaskPushNotificationConfig` |
| `tasks/pushNotificationConfig/list` | ListTaskPushNotificationConfigs | `ListTaskPushNotificationConfigParams` | `TaskPushNotificationConfig[]` |
| `tasks/pushNotificationConfig/delete` | DeleteTaskPushNotificationConfig | `DeleteTaskPushNotificationConfigParams` | void / confirmation |
| `agent/getAuthenticatedExtendedCard` | GetExtendedAgentCard | (none) | `AgentCard` |
| `tasks/list` *(v1.0 only)* | ListTasks | `ListTasksRequest` | `ListTasksResponse` (`tasks[]`, `nextPageToken`, …) |

### Method detail
- **`message/send`** — the primary operation. Client sends a `Message`; the agent returns
  **either a `Task`** (when it tracks stateful/long-running work) **or a direct `Message`**
  (immediate reply). Blocking vs non-blocking is controlled by `configuration.blocking`.
- **`message/stream`** — same params, but the agent streams incremental updates over SSE.
  Requires `capabilities.streaming: true`. See §4.3.
- **`tasks/get`** — fetch a task's current status, artifacts, and (optionally) history.
  `historyLength` caps how many recent messages come back.
- **`tasks/cancel`** — request termination; **success is not guaranteed**. Returns the updated
  `Task`; errors with `TaskNotCancelableError` if the task can't be canceled in its state.
- **`tasks/resubscribe`** — reconnect an SSE stream to an existing task after a dropped
  connection.
- **`tasks/pushNotificationConfig/*`** — manage webhook configs bound to a task (§4.4).
- **`agent/getAuthenticatedExtendedCard`** — no params; after auth, returns a richer
  `AgentCard`. Errors with `AuthenticatedExtendedCardNotConfiguredError` if none configured.

### Key param types
```ts
interface MessageSendParams {
  message: Message;                       // required
  configuration?: MessageSendConfiguration;
  metadata?: Record<string, any>;
}
interface MessageSendConfiguration {
  acceptedOutputModes?: string[];         // accepted output MIME types
  historyLength?: number;                 // how much history to include in the returned Task
  pushNotificationConfig?: PushNotificationConfig; // register a webhook inline with the send
  blocking?: boolean;                     // true ⇒ hold response until terminal/paused (inference)
}
interface TaskQueryParams { id: string; historyLength?: number; metadata?: Record<string,any>; }
interface TaskIdParams    { id: string; metadata?: Record<string,any>; }
```

## 4.3 Streaming (Server-Sent Events)

- **Activation:** `capabilities.streaming: true`; initiated by `message/stream` (or resumed by
  `tasks/resubscribe`).
- **HTTP mechanics:** the server responds `200 OK` with `Content-Type: text/event-stream` and
  holds the connection open. Each SSE event's `data:` field carries a JSON-RPC 2.0 response
  whose `result` is one of: `Message`, `Task`, `TaskStatusUpdateEvent`, `TaskArtifactUpdateEvent`.
- **Artifact streaming:** `TaskArtifactUpdateEvent.append` lets the agent stream an artifact in
  chunks; `lastChunk: true` marks the final chunk.
- **Stream closure:** when a task hits a terminal or interrupted state (completed, failed,
  canceled, rejected, or input-required) the server closes the stream; the final status event
  carries `final: true`.
- **Resubscription:** on disconnect, the client calls `tasks/resubscribe` with `TaskIdParams`
  to resume the event stream for the same task.

## 4.4 Push notifications (webhooks)

For very long-running or disconnected scenarios (mobile, serverless, hours/days-long tasks).
Requires `capabilities.pushNotifications: true`.

- The client registers a `PushNotificationConfig` (webhook `url`, optional `token`, optional
  `authentication`) — via `tasks/pushNotificationConfig/set` or inline in
  `MessageSendConfiguration.pushNotificationConfig`.
- The agent **POSTs** notifications to that webhook as state changes occur (payload mirrors the
  streaming event shapes — may contain task/message/statusUpdate/artifactUpdate).
- On receipt, the client typically calls `tasks/get` to pull the full updated `Task`.
- Errors with `PushNotificationNotSupportedError` if unsupported.

### Streaming vs push — when to use which
| Use **SSE streaming** | Use **push notifications** |
|---|---|
| Real-time progress, low latency | Very long tasks (minutes → days) |
| Client can hold a persistent connection | Clients that can't hold connections (mobile/serverless) |
| Want every incremental update | Only need notification on significant state changes |

Webhook security (SSRF protection, signing/JWKS verification) is covered in
[05-security.md](05-security.md).

## 4.5 Error handling

JSON-RPC error object `{ "code": int, "message": string, "data"?: any }`. Protocol-level
errors return HTTP 200 with a JSON-RPC error body.

**Standard JSON-RPC 2.0 codes:**
| Code | Name | Meaning |
|---|---|---|
| -32700 | JSONParseError | Invalid JSON |
| -32600 | InvalidRequestError | Not a valid Request object |
| -32601 | MethodNotFoundError | Method doesn't exist / unavailable |
| -32602 | InvalidParamsError | Invalid parameters |
| -32603 | InternalError | Internal error |

**A2A-specific codes (server range -32000…-32099):**
| Code | Name | Meaning |
|---|---|---|
| -32001 | TaskNotFoundError | Task id not found / no longer accessible |
| -32002 | TaskNotCancelableError | Task can't be canceled in its current state |
| -32003 | PushNotificationNotSupportedError | Server doesn't support push notifications |
| -32004 | UnsupportedOperationError | Operation not supported |
| -32005 | ContentTypeNotSupportedError | Supplied/requested MIME type not supported |
| -32006 | InvalidAgentResponseError | Agent produced a malformed response |
| -32007 | AuthenticatedExtendedCardNotConfiguredError | Extended card requested but none configured |

> v1.0 adds (numbers *inferred*, likely -32008/-32009): `ExtensionSupportRequiredError`,
> `VersionNotSupportedError`. Vendor runtimes (e.g. AWS Bedrock AgentCore) may map their own
> non-spec codes — don't treat those as canonical.

## 4.6 "Life of a task" — a concrete walkthrough

A typical long-running interaction:

1. **Discover.** Client fetches the remote agent's Agent Card from
   `/.well-known/agent-card.json`, picks a transport, and checks `capabilities` / `skills`.
2. **Send.** Client calls `message/send` with a user `Message`. Because the work is
   non-trivial, the agent returns a `Task` in state `submitted` (then `working`).
3. **Track.** Client either:
   - **polls** `tasks/get`, or
   - opened `message/stream` instead and receives `TaskStatusUpdateEvent` /
     `TaskArtifactUpdateEvent` over SSE, or
   - registered a **webhook** and gets POSTed on state changes.
4. **Clarify (optional).** Agent moves the task to `input-required` and emits a status
   `message` asking a question. Client sends another `message/send` with the **same `taskId`**;
   the task returns to `working`.
5. **Produce.** Agent emits `Artifact`(s) (possibly streamed in chunks with
   `append`/`lastChunk`).
6. **Finish.** Task reaches a terminal state (`completed` / `failed` / `canceled` /
   `rejected`); the final streaming event has `final: true` and the stream closes.

Cancellation can be requested any time via `tasks/cancel` (best-effort).
