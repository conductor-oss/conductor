# 7. Ecosystem, SDKs & Samples

All verified against the live `github.com/a2aproject` org and the spec site.

## 7.1 Governance & licensing
- **Owner:** the **Linux Foundation**. The org description: *"Agent2Agent (A2A) Project —
  Donated to the Linux Foundation by Google."*
- **License:** **Apache 2.0** across the spec repo and every SDK.
- **Governance:** an **8-seat Technical Steering Committee** (`GOVERNANCE.md`), one per
  company — Google, Microsoft, Cisco, AWS, Salesforce, ServiceNow, SAP, IBM Research. Roles
  escalate Contributors → Maintainers by TSC vote; a `.gitvote.yml` bot runs TSC votes.
- IBM's **ACP** protocol merged into A2A under the Linux Foundation (LF AI & Data umbrella —
  *inference*).

## 7.2 The canonical spec is Protobuf, not JSON

Key finding for anyone implementing:

- The **source of truth is `specification/a2a.proto`** — a single proto3 file, package
  **`lf.a2a.v1`**, defining the `A2AService` gRPC service (`SendMessage`,
  `SendStreamingMessage`, `GetTask`, …) with `google.api.http` REST annotations baked in.
- The **JSON Schema (`a2a.json`) is a *generated, non-normative artifact*** derived from the
  proto (JSON Schema 2020-12, produced by `scripts/proto_to_json_schema.sh` via bufbuild's
  `protoc-gen-jsonschema`). It is intentionally **not committed** — *"Do NOT edit `a2a.json`
  manually. Update the proto instead."*
- Practical consequence: when in doubt about a field, **read the `.proto`**, not the JSON.

> Correction to a common assumption: there is **no** committed `specification/json/a2a.json`
> or `specification/grpc/a2a.proto`. The proto lives at `specification/a2a.proto`;
> `specification/json/` holds only a README explaining the generated schema.

Main repo (`a2aproject/A2A`) also contains: `docs/` (MkDocs site source — `specification.md`,
`definitions.md`, `topics/`, `tutorials/`, `partners.md`, `announcing-1.0.md`,
`whats-new-v1.md`, `llms.txt`), `adrs/` (architecture decision records), and the usual
governance/meta files.

## 7.3 Official SDKs

All under `a2aproject`, all Apache 2.0:

| Language | Repo | Package | Notes |
|---|---|---|---|
| **Python** | `a2a-python` | PyPI `a2a-sdk` | Most mature; implements v1.0 with a v0.3 compat mode; all 3 transports (client + server) |
| **JS/TS** | `a2a-js` | npm `@a2a-js/sdk` | Express + gRPC integrations |
| **Java** | `a2a-java` | Maven | Official |
| **.NET/C#** | `a2a-dotnet` | NuGet `A2A` | ASP.NET Core, SSE streaming, card discovery; .NET 8+ |
| **Go** | `a2a-go` | `github.com/a2aproject/a2a-go/v2` | Run apps as A2A servers |
| **Rust** | `a2a-rs` | — | Present in org; less prominent |

Tooling repos: **`a2a-inspector`** (validation tools), **`a2a-tck`** (Technical Compatibility
Kit / conformance suite), **`a2a-itk`** (integration testing kit), **`a2a-gateway`** (bridges
A2A agents to other channels), plus experimental binding/auth extensions.

## 7.4 SDK API shape (Python, and mirrored in JS)

A consistent cross-language design:

**Server side:**
- **`AgentExecutor`** — the abstract base you implement. Two async methods: `execute(context,
  event_queue)` and `cancel(context, event_queue)`. Your agent logic lives here.
- **`RequestHandler`** → concrete **`DefaultRequestHandler`** (constructed with `agent_card`,
  `task_store`, `agent_executor`). Also `GrpcHandler`, `LegacyRequestHandler`.
- **`TaskStore`** — async persistence (`save`/`get`/`list`/`delete`). Implementations:
  **`InMemoryTaskStore`**, **`DatabaseTaskStore`** (Postgres/MySQL/SQLite), `CopyingTaskStore`.
- **App wiring (v1.0):** the old wrapper apps (`A2AStarletteApplication`,
  `A2AFastApiApplication`, `A2ARESTFastApiApplication`) were **removed**; you now compose route
  factories (`create_jsonrpc_routes()`, `create_rest_routes()`, `create_agent_card_routes()`)
  into a `Starlette`/`FastAPI` app and run with `uvicorn`.

**Client side:**
- **`A2ACardResolver`** — fetches a remote agent's Agent Card from its well-known URL
  (discovery). Still present in v1.0.
- **v1.0 client:** **`ClientFactory`** + **`ClientConfig`** → a transport-abstracted
  **`Client`**, with interceptors (`AuthInterceptor`) and credential services. The old
  single-transport **`A2AClient`** is no longer a top-level export (lives in the v0.3 compat
  layer / older samples).

> The JS SDK mirrors this exactly: `@a2a-js/sdk/server` exports `AgentExecutor`,
> `DefaultRequestHandler`, `InMemoryTaskStore`; the client uses `ClientFactory`. The shared
> shape — **AgentExecutor + RequestHandler + TaskStore** on the server, **ClientFactory +
> CardResolver** on the client — is the portable mental model.

## 7.5 Samples (`a2aproject/a2a-samples`)

Organized by language (`samples/{python,js,java,go,dotnet}`) plus a `demo/` web app. Python is
the richest set — one A2A server per agent, deliberately spanning many frameworks to prove
interoperability:

- **Google ADK** — `adk_currency_agent`, `adk_expense_reimbursement` (multi-turn + webforms),
  `adk_facts` (Google Search grounding), `content_planner`, `birthday_planner_adk`
- **LangGraph** — the canonical **currency-conversion** agent (tools, multi-turn, streaming)
- **CrewAI** — image-generation agent (sends images over A2A)
- **LlamaIndex** — `llama_index_file_chat` (file upload/parse + chat, streaming)
- **AG2** — MCP-enabled agent exposed via A2A
- **Semantic Kernel** — travel agent
- **Marvin** — structured contact extraction
- **MindsDB** — query any DB/warehouse
- **Framework-free / MCP** — `a2a_mcp`, `a2a-mcp-without-framework`, `a2a_telemetry` (OTel)
- **`helloworld`** — the echo-bot quickstart
- **Multi-agent** — `airbnb_planner_multiagent` (a host/routing agent orchestrating an Airbnb
  agent + a weather agent over A2A)

**Flagship demo (`demo/`):** a **Mesop** web app where a Google ADK **Host Agent** orchestrates
multiple **Remote Agents**. Each remote agent is an `A2AClient` inside an ADK agent that
fetches the remote Agent Card and proxies calls over A2A. Renders text, thought bubbles, web
forms, and images.

JS samples: `coder`, `content-editor`, `movie-agent` (+ a `cli.ts`). Java: `agents`,
`custom_java_impl`, `koog`. Go: `client`/`server`/`models`. .NET: `BasicA2ADemo`,
`A2ACliDemo`, `A2ASemanticKernelDemo`.

## 7.6 The purchasing-concierge use case (codelab)

A end-to-end illustration of A2A's value (this lives in a **Google Cloud codelab**, not the
samples repo):

**Scenario:** a user talks only to a single **Purchasing Concierge** agent to order food. The
concierge fulfills nothing itself — it **discovers and delegates** to independent seller agents.

| Component | Role | Framework | Deployment |
|---|---|---|---|
| Purchasing Concierge | **A2A client** | Google **ADK** | Vertex AI Agent Engine |
| Burger Seller Agent | **A2A server** | **CrewAI** | Cloud Run |
| Pizza Seller Agent | **A2A server** | **LangGraph** | Cloud Run |

```
User → [Purchasing Concierge — A2A client / ADK]
            ├── A2A ─► [Burger Agent — CrewAI]
            └── A2A ─► [Pizza Agent — LangGraph]
```

**What it demonstrates:**
- **Discovery** — each seller publishes an Agent Card at its well-known path; the concierge
  uses `A2ACardResolver` to fetch/parse cards at init.
- **Sending tasks** — the concierge sends user intent via `message/send` (`SendMessageRequest`)
  with session context/metadata, holding a `RemoteAgentConnections` object per discovered agent.
- **Receiving artifacts** — sellers reply with `Artifact`s containing text `Part`s, which the
  concierge surfaces to the user.

The whole point is the **heterogeneous frameworks** (ADK ↔ CrewAI ↔ LangGraph) interoperating
purely through A2A — exactly the silo-breaking the protocol exists for.

> Component/class names in the codelab (`AgentExecutor`, `DefaultRequestHandler`,
> `InMemoryTaskStore`, `A2AStarletteApplication`) are **v0.x-era** SDK names — see §7.4 for the
> v1.0 renames.

## 7.7 Partners / adopters
`docs/partners.md` lists 100+ partners, each linking to their own A2A announcement. Beyond the
TSC companies (Google, Microsoft, AWS, Salesforce, ServiceNow, SAP, Cisco, IBM): Atlassian,
Box, Adobe, Autodesk, Bloomberg, Block, Boomi, Confluent, Datadog, DataRobot, DataStax,
Collibra, Cohere, AI21 Labs, Elastic, Glean, Harness, Deutsche Telekom, Alibaba Cloud, Auth0,
plus the major SIs (Accenture, Deloitte, Capgemini, Cognizant, HCLTech, EPAM, BCG, …).
