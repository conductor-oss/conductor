# 2. A2A vs MCP — Complementary, Not Competing

This is the single most-asked question about A2A, so it gets its own doc.

## 2.1 The distinction

| | **MCP (Model Context Protocol)** | **A2A (Agent2Agent)** |
|---|---|---|
| Connects an agent to… | its **tools, resources, structured I/O** | **other agents**, as peers |
| Direction | "vertical" — agent → tooling *(inference: community framing, not spec wording)* | "horizontal" — agent → agent *(inference)* |
| The other side is… | a primitive: "well-defined, structured inputs and outputs," "specific, often stateless, functions" (APIs, DBs, calculators) | an autonomous system that "reason[s], plan[s], use[s] multiple tools, maintain[s] state over longer interactions" |
| One-liner | agents **using** capabilities | agents **partnering** on tasks |
| Author | Anthropic | Google → Linux Foundation |

The official summarizing sentence, verbatim:

> "A2A is about agents *partnering* on tasks, while MCP is more about agents *using* capabilities."

And on the relationship:

> "A2A is an open protocol that complements Anthropic's Model Context Protocol (MCP), which
> provides helpful tools and context to agents."

> "Both the MCP and A2A protocols are essential for building complex AI systems, and they
> address distinct but highly complementary needs."

The official A2A-vs-MCP page is even titled **"Complementary Protocols for Agentic Systems."**

> **Note on "vertical/horizontal":** the spec does **not** use those words. They're an
> accurate community shorthand (MCP = vertical/agent-to-tool, A2A = horizontal/agent-to-agent),
> but treat them as popularized framing, not a quote. The docs' own framing is
> "partnering" vs "using."

## 2.2 How they're used together

> "An agentic application might primarily use A2A to communicate with other agents. Each
> individual agent internally uses MCP to interact with its specific tools and resources."

So the layering is:

```
        ┌─────────────────────────────────────────────┐
        │  Agent A                                      │
        │   ── A2A ──►  Agent B  ── A2A ──►  Agent C     │   (peers collaborating)
        │     │                    │                    │
        │   MCP│                  MCP│                  │
        │     ▼                    ▼                    │
        │  tools/DBs/APIs       tools/DBs/APIs          │   (each agent's own tooling)
        └─────────────────────────────────────────────┘
```

A2A is the connective tissue **between** agents; MCP is how each agent drives **its own**
tools internally. The two never compete for the same job.

## 2.3 The canonical analogy — the auto repair shop

The official docs illustrate the split with an autonomous-AI auto-repair shop:

1. A customer contacts a **Shop Manager** agent. The Manager uses **A2A** for "a multi-turn
   diagnostic conversation" — *"send me a picture of the left wheel," "I notice a fluid leak,
   how long has this been happening?"* This is agent-to-agent dialogue.

2. The Manager delegates to **Mechanic** agents. A Mechanic uses **MCP** "to interact with
   its specialized tools" — a Vehicle Diagnostic Scanner, a Repair Manual Database, a
   Platform Lift (*"raise platform by 2 meters," "engage"*). These are structured, tool-style
   operations.

3. When a part is needed, the Mechanic uses **A2A** "to communicate with a 'Parts Supplier'
   agent to order a part." Back to agent-to-agent collaboration.

> Within one workflow: **A2A** connects Manager ↔ Mechanic ↔ Parts Supplier; **MCP** is how
> each agent operates its own tools.

## 2.4 The "opaque agent" concept

A2A is deliberately designed so agents collaborate as **black boxes**. They interact "as
peers" through standardized messages and task structures **without exposing internal
mechanics, tools, memory, or state**. Design principle #1 says it directly: agents collaborate
"even when they don't share memory, tools and context."

This is the philosophical line between the two protocols:

- **MCP exposes a tool's interface** so an agent can call it precisely (structured schema in,
  structured result out).
- **A2A hides the agent's interior** and exposes only a capability surface (skills) plus a
  conversational/task interface. You ask a remote agent to *accomplish something*; you don't
  reach into how it does it.

## 2.5 Two illustrative splits (secondary sources)

These come from third-party explainers, not the spec, but they're clean mental models:

- **Loan approval** — MCP handles preprocessing (credit-score APIs, transaction history, OCR
  doc validation); A2A orchestrates a `RiskAssessmentAgent`, a `ComplianceAgent`, and a
  `DisbursementAgent`. Mixed MCP + A2A.
- **IT helpdesk** — a client agent routes a ticket across Hardware-Diagnostic →
  Software-Rollback → Device-Replacement agents, all opaque and communicating asynchronously
  via Tasks. "Pure A2A" — no structured tools involved, all coordination is agent-to-agent.

## 2.6 Why this matters for Conductor

Conductor's `ai/` module already makes Conductor an **MCP client** (`CALL_MCP_TOOL`,
`LIST_MCP_TOOLS`, `MCP` task types) and an LLM orchestrator (`LLM_CHAT_COMPLETE`,
`LLM_TEXT_COMPLETE`, embeddings/RAG, multimodal). That covers the "agent uses tools" half.
A2A is the other half — the peer layer. The implications of adding it are explored in
[08-conductor-implications.md](08-conductor-implications.md).
