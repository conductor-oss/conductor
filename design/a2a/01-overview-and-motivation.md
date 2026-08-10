# 1. Overview & Motivation

## 1.1 The problem A2A solves

Enterprises are deploying a growing number of autonomous agents, but those agents live in
silos — built by different vendors, on different frameworks (LangGraph, CrewAI, ADK,
Semantic Kernel, …), over different data systems and applications. They cannot collaborate.
Google's framing of the goal:

> "To maximize the benefits from agentic AI, it is critical for these agents to be able to
> collaborate in a dynamic, multi-agent ecosystem across siloed data systems and applications."

The payoff is cross-framework, cross-vendor interoperability:

> "Enabling agents to interoperate with each other, even if they were built by different
> vendors or in a different framework, will increase autonomy and multiply productivity
> gains, while lowering long-term costs."

A2A is the missing **vendor-neutral interoperability layer** between agents — a common
"language" so an agent can find another agent, understand what it can do, hand it work, and
get results back, **without either side exposing its internals**.

The headline vision is literally the title of the launch blog: **"A new era of Agent
Interoperability."**

## 1.2 The five design principles (from the launch announcement)

1. **Embrace agentic capabilities.**
   > "A2A focuses on enabling agents to collaborate in their natural, unstructured
   > modalities, even when they don't share memory, tools and context."

   This is the **opaque-agent** stance — agents cooperate as peers without merging internal
   state. They exchange messages and tasks, not memory dumps or tool handles.

2. **Build on existing standards.**
   > "The protocol is built on top of existing, popular standards including HTTP, SSE,
   > JSON-RPC."

   Easier to drop into IT stacks businesses already run; works with existing API gateways,
   auth, observability.

3. **Secure by default.**
   > "A2A is designed to support enterprise-grade authentication and authorization, with
   > parity to OpenAPI's authentication schemes at launch."

   See [05-security.md](05-security.md).

4. **Support for long-running tasks.**
   > "We designed A2A to be flexible and support scenarios where it excels at completing
   > everything from quick tasks to deep research that may take hours and or even days when
   > humans are in the loop."

   Hence the stateful Task lifecycle, streaming, and push notifications.

5. **Modality agnostic.**
   > "The agentic world isn't limited to just text, which is why we've designed A2A to
   > support various modalities, including audio and video streaming."

   Content travels as typed **Parts** (text / file / structured data) with MIME types.

> The official spec site re-expresses these as: **Simplicity** (HTTP/JSON-RPC/SSE),
> **Enterprise Readiness** (auth, security, tracing), **Asynchronous** (long-running tasks),
> **Modality Independent**, and **Opaque Execution**. Same ideas, different labels.

## 1.3 Core actors and terminology

| Term | Definition (from the key-concepts page) |
|---|---|
| **User** | "The end user, which can be a human operator or an automated service." |
| **A2A Client (Client Agent)** | "An application, service, or another AI agent that acts on behalf of the user. The client initiates communication." Responsible for formulating and communicating tasks. |
| **A2A Server (Remote Agent)** | "An AI agent or an agentic system that exposes an HTTP endpoint implementing the A2A protocol. It receives requests, processes tasks, and returns results or status updates." Responsible for acting on tasks. |
| **Agent Card** | Standardized JSON descriptor used for **capability discovery** — lets a client "identify the best agent that can perform a task." |
| **Opaque agents** | Agents collaborate as black boxes — no shared memory, tools, context, or internal logic. |
| **Task** | A structured, stateful unit of work with a lifecycle. |
| **Message** | A conversational turn between client and remote agent. |
| **Artifact** | An immutable output produced by the remote agent, composed of Parts. |

The relationship is asymmetric per interaction but symmetric in principle: any agent can be
a client in one exchange and a remote agent in another. An "agent" in A2A is anything that
can speak the protocol over HTTP — it need not be an LLM.

## 1.4 Real-world examples cited at launch

- **Candidate sourcing / hiring** (the marquee example): a hiring manager tasks their agent,
  through one unified interface, to find candidates matching a job spec. That agent
  "interacts with other specialized agents to source potential candidates." The user reviews
  suggestions and directs the agent to "schedule further interviews"; afterward "another
  agent can be engaged to facilitate background checks." One orchestrating agent coordinating
  sourcing → interview-scheduling → background-check agents across HR systems.

- **Purchasing concierge** (the hands-on codelab): a single concierge agent talks to multiple
  independent seller agents (e.g. a burger agent and a pizza agent) to fulfill an order. The
  seller agents are deliberately built on *different* frameworks to prove interoperability.
  Detailed in [07-ecosystem-and-samples.md](07-ecosystem-and-samples.md).

- Secondary illustrative examples (loan approval, IT helpdesk routing) show the A2A + MCP
  split and "pure A2A" orchestration respectively — see [02-a2a-vs-mcp.md](02-a2a-vs-mcp.md).

## 1.5 Governance & timeline

- **April 9, 2025** — Google **announces A2A** ("A new era of Agent Interoperability") with
  50+ launch partners (Atlassian, Box, Cohere, Intuit, LangChain, MongoDB, PayPal, Salesforce,
  SAP, ServiceNow, Workday, plus the big consultancies).

- **June 23–24, 2025** — at Open Source Summit North America, Google **donates A2A to the
  Linux Foundation**, forming the vendor-neutral **Agent2Agent project**. Founding members
  alongside Google: **AWS, Cisco, Microsoft, Salesforce, SAP, ServiceNow** (+100 companies).

- **v1.0** — announced as "the first stable, production-ready version," governed by an
  **8-seat Technical Steering Committee** (Google, Microsoft, Cisco, AWS, Salesforce,
  ServiceNow, SAP, IBM Research).

- **License:** Apache 2.0. **Org:** `github.com/a2aproject`. **Canonical spec:** the
  protobuf file `specification/a2a.proto` (package `lf.a2a.v1` — "lf" = Linux Foundation).

More on governance, the canonical proto, and the SDK ecosystem in
[07-ecosystem-and-samples.md](07-ecosystem-and-samples.md).
