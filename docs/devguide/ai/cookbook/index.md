---
description: Production-ready Conductor AI workflow starters for knowledge, tools, agents, approvals, and delivery.
---

# AI Cookbook

<section class="ai-cookbook-hero" aria-label="AI Cookbook overview">
  <div class="ai-cookbook-hero__content">
    <p>Each recipe on this page is a complete, runnable AI workflow. Register the definition, run it, then swap in your own models, tools, and data. The recipes are built the way you would run them in production: loops have limits, tool access is allowlisted, risky steps wait for human approval, and every run records what happened.</p>
  </div>
  <svg class="ai-cookbook-hero__diagram" viewBox="0 70 540 300" role="img" aria-labelledby="ai-cookbook-diagram-title ai-cookbook-diagram-description">
    <title id="ai-cookbook-diagram-title">AI Cookbook production starter model</title>
    <desc id="ai-cookbook-diagram-description">Two recipe categories feed a durable production starter. The starter connects model and tools through a policy control and produces an inspectable outcome.</desc>
    <defs>
      <marker id="ai-cookbook-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
        <path d="M 0 0 L 10 5 L 0 10 z" class="ai-cookbook-hero__arrowhead" />
      </marker>
      <marker id="ai-cookbook-arrow-control" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
        <path d="M 0 0 L 10 5 L 0 10 z" class="ai-cookbook-hero__arrowhead ai-cookbook-hero__arrowhead--control" />
      </marker>
    </defs>
    <rect x="20" y="82" width="220" height="72" rx="10" class="ai-cookbook-hero__family ai-cookbook-hero__family--workflows" />
    <text x="40" y="108" class="ai-cookbook-hero__label">Agentic Workflows</text>
    <text x="40" y="126" class="ai-cookbook-hero__detail">the graph decides what runs</text>
    <text x="40" y="142" class="ai-cookbook-hero__detail">LLM · MCP · agents · humans</text>
    <rect x="20" y="174" width="220" height="72" rx="10" class="ai-cookbook-hero__family ai-cookbook-hero__family--agents" />
    <text x="40" y="200" class="ai-cookbook-hero__label">AI Agents</text>
    <text x="40" y="218" class="ai-cookbook-hero__detail">the agent owns its own loop</text>
    <text x="40" y="234" class="ai-cookbook-hero__detail">SDK · guardrails · memory</text>
    <path d="M240 118 H282 M240 210 H282" class="ai-cookbook-hero__arrow" marker-end="url(#ai-cookbook-arrow)" />
    <rect x="290" y="88" width="228" height="152" rx="15" class="ai-cookbook-hero__starter" />
    <text x="404" y="120" text-anchor="middle" class="ai-cookbook-hero__starter-title">Production starter</text>
    <rect x="314" y="138" width="180" height="31" rx="7" class="ai-cookbook-hero__model" />
    <text x="404" y="158" text-anchor="middle" class="ai-cookbook-hero__label">Model / tools / agents</text>
    <path d="M404 169 V191" class="ai-cookbook-hero__arrow ai-cookbook-hero__arrow--control" marker-end="url(#ai-cookbook-arrow-control)" />
    <rect x="314" y="197" width="180" height="27" rx="7" class="ai-cookbook-hero__policy" />
    <text x="404" y="215" text-anchor="middle" class="ai-cookbook-hero__policy-text">policy · approval · limits</text>
    <path d="M404 240 V270" class="ai-cookbook-hero__arrow ai-cookbook-hero__arrow--control" marker-end="url(#ai-cookbook-arrow-control)" />
    <rect x="290" y="281" width="228" height="78" rx="13" class="ai-cookbook-hero__outcome" />
    <path d="M326 318 l10 10 22 -25" class="ai-cookbook-hero__check" />
    <text x="432" y="313" text-anchor="middle" class="ai-cookbook-hero__label">Inspectable outcome</text>
    <text x="432" y="332" text-anchor="middle" class="ai-cookbook-hero__detail">evidence · state · media reference</text>
  </svg>
</section>

## Agentic Workflows

The workflow graph is the agent. A model reasons, but Conductor decides what actually executes: LLM, MCP, and agent tasks composed with `SWITCH`, `DO_WHILE`, `FORK_JOIN`, and `HUMAN`. The allowlist of possible actions lives in the definition, not in a prompt, so a model cannot widen its own blast radius.

Each of these carries the control that makes the pattern safe to run for real — a bounded loop, an enforced allowlist, an explicit refusal path, or a human gate.

| Recipe | Outcome | Built from |
|---|---|---|
| [RAG Agent](rag-agent.md) | Retrieve, grade whether the context can answer, retry, and refuse rather than answer ungrounded. | `DO_WHILE`, `LLM_SEARCH_INDEX` |
| [MCP Tool Calling](mcp-tool-calling.md) | Discover tools, shortlist them, and re-check the model's choice against that allowlist. | `LIST_MCP_TOOLS`, `CALL_MCP_TOOL`, `SWITCH` |
| [A2A Agent Orchestration](a2a-orchestration.md) | Delegate to two remote A2A agents in parallel, join, and synthesize. | `GET_AGENT_CARD`, `FORK_JOIN`, `AGENT` |
| [HITL Workflow](hitl-approval.md) | Draft an action, pause for a human, and send only on explicit approval. | `HUMAN`, `SWITCH`, `HTTP` |
| [LLM with Guardrails](llm-guardrails.md) | Fence a model call with a pattern screen, policy checks, and one bounded repair. | `INLINE`, `SWITCH`, `TERMINATE` |
| [Deep Research Agent](deep-research.md) | Decompose a goal, fan out searches, review coverage each round, render a PDF. | `DO_WHILE`, `FORK_JOIN_DYNAMIC`, `GENERATE_PDF` |
| [A2A Delegation](remote-a2a-delegation.md) | Hand a request to an agent someone else operates, over A2A. | `AGENT` (`a2a`) |

## AI Agents

An agent owns its own reasoning loop: it decides which tool to call and when it is done. You author it with a Conductor SDK in Python, TypeScript, Java, or C#, or bring one written in LangChain or Google ADK through the Conductor bridge. Conductor supplies what the loop cannot give itself — every tool call is a durable, individually retryable task, and approval and cancellation are boundaries the agent cannot skip.

| Recipe | Outcome | Built from |
|---|---|---|
| [Tool calling agent](agent-tool-calling.md) | Declare two tools and let the model choose between them. | SDK `Agent` + `@tool` |
| [Agent with guardrails](agent-guardrails.md) | Check the agent's own output and retry when a rule fails. | `RegexGuardrail`, `@guardrail` |
| [Multi-agent handoff](agent-handoff.md) | A supervisor delegates to the specialist that fits. | `Strategy.HANDOFF` |
| [Agent with memory](agent-memory.md) | Recall facts across sessions by relevance, not replay. | `SemanticMemory` |
| [Agent with CLI tools](agent-cli-tools.md) | Run real shell commands, restricted to an allowlist. | `cli_allowed_commands` |
| [Massively parallel agents](agent-scatter-gather.md) | Fan out to 100 sub-agents and synthesize the results. | `scatter_gather()` |
| [Conductor agent](reusable-conductor-agent.md) | Invoke a stable deployed capability from another workflow. | `AGENT` (`conductor`) |
| [LangChain investigator](langchain-entitlement-investigator.md) | Author with LangChain and invoke through the Conductor bridge. | `AGENT` (`conductor`) |
| [ADK triage](google-adk-order-triage.md) | Author with ADK and invoke through the Conductor bridge. | `AGENT` (`conductor`) |
| [Specialist review](parallel-specialist-review.md) | Collect independent reviews with durable fan-out and join. | `AGENT`, `FORK_JOIN`, `JOIN` |
| [Agent approval](human-approved-action.md) | Pause a deployed agent at its durable approval boundary. | `AGENT`, `SWITCH`, `HUMAN` |
| [Agent cancellation](conductor-agent-cancellation.md) | Propagate parent termination to a long-running deployed agent. | `AGENT`, `FORK_JOIN`, `TERMINATE` |

Every definition leans on Conductor's defaults for retries and timeouts, so the JSON stays readable — add explicit limits where a provider quota or blast radius demands them. Keep documents, media, and long evidence out of workflow payloads; pass object-storage or Files API references instead.
