---
description: "What an agent is in Conductor and how agent turns run as durable workflow tasks with tools, approvals, and a full execution history."
---

# Agents & AI

## What is an agent?

An agent is a program that uses an LLM to decide what to do next. Instead of following a fixed sequence of steps, it works in turns: the model reads the goal and the context so far, then proposes the next action. That action might be a tool call, a question for a person, or a final answer. The result of each action becomes context for the next turn, and the loop continues until the goal is met.

<section class="agent-runtime-hero" aria-label="The Conductor agent turn loop">
  <svg class="agent-runtime-hero__diagram" viewBox="0 0 520 312" role="img" aria-labelledby="agent-runtime-diagram-title agent-runtime-diagram-description">
    <title id="agent-runtime-diagram-title">The Conductor agent turn loop</title>
    <desc id="agent-runtime-diagram-description">An LLM proposes the next action. Conductor validates the proposal, persists state, and schedules the work. Workers, MCP tools, remote agents, and people execute it. Results are saved and start the next turn.</desc>
    <defs>
      <marker id="agent-runtime-arrow-runtime" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" class="agent-runtime-hero__arrowhead agent-runtime-hero__arrowhead--runtime" />
      </marker>
    </defs>
    <rect x="110" y="16" width="300" height="56" rx="12" class="agent-runtime-hero__brain-box" />
    <text x="260" y="39" text-anchor="middle" class="agent-runtime-hero__runtime-title agent-runtime-hero__runtime-title--brain">LLM decides the next step</text>
    <text x="260" y="59" text-anchor="middle" class="agent-runtime-hero__detail">proposes a tool call, a question, or an answer</text>
    <path d="M 260 72 V 92" class="agent-runtime-hero__arrow agent-runtime-hero__arrow--runtime" marker-end="url(#agent-runtime-arrow-runtime)" />
    <rect x="80" y="94" width="360" height="72" rx="14" class="agent-runtime-hero__runtime-box" />
    <text x="260" y="119" text-anchor="middle" class="agent-runtime-hero__runtime-title">Conductor</text>
    <text x="260" y="138" text-anchor="middle" class="agent-runtime-hero__runtime-detail">validates the proposal · applies approvals</text>
    <text x="260" y="155" text-anchor="middle" class="agent-runtime-hero__runtime-detail">persists state · schedules the work</text>
    <path d="M 260 166 V 188" class="agent-runtime-hero__arrow agent-runtime-hero__arrow--runtime" marker-end="url(#agent-runtime-arrow-runtime)" />
    <rect x="31" y="192" width="110" height="56" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="86" y="217" text-anchor="middle" class="agent-runtime-hero__label">Workers</text>
    <text x="86" y="234" text-anchor="middle" class="agent-runtime-hero__detail">your code</text>
    <rect x="147" y="192" width="110" height="56" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="202" y="217" text-anchor="middle" class="agent-runtime-hero__label">MCP tools</text>
    <text x="202" y="234" text-anchor="middle" class="agent-runtime-hero__detail">tools + data</text>
    <rect x="263" y="192" width="110" height="56" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="318" y="217" text-anchor="middle" class="agent-runtime-hero__label">Remote agents</text>
    <text x="318" y="234" text-anchor="middle" class="agent-runtime-hero__detail">A2A protocol</text>
    <rect x="379" y="192" width="110" height="56" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="434" y="217" text-anchor="middle" class="agent-runtime-hero__label">People</text>
    <text x="434" y="234" text-anchor="middle" class="agent-runtime-hero__detail">review + input</text>
    <path d="M 260 254 V 276 H 24 V 44 H 102" class="agent-runtime-hero__turn-loop" marker-end="url(#agent-runtime-arrow-runtime)" />
    <text x="272" y="296" text-anchor="middle" class="agent-runtime-hero__compile">results are saved and start the next turn</text>
  </svg>
</section>

In Conductor, that loop runs as a durable workflow. The model's proposal is data, not a command. Conductor validates it, applies any required approvals, and only then schedules the work. The work itself runs as ordinary tasks, using the same building blocks a workflow already has: your workers, MCP tools, remote agents, and people. Because every result is persisted before the next turn starts, a crash, deploy, or long wait never loses the agent's progress.

## Three ways to build

The paths are complementary. A production workflow can use native AI tasks, invoke a compiled Conductor Agent, and delegate specialist work to a remote A2A agent in the same durable graph.

<div class="agent-overview-grid agent-overview-grid--three">
  <a class="agent-overview-card agent-overview-card--link" href="llm-orchestration.html">
    <span class="agent-overview-card__kicker">Direct composition</span>
    <strong>Declarative AI workflows</strong>
    <span>Compose LLM, MCP, vector-search, control-flow, wait, and human tasks directly. Choose this when the workflow definition should expose the complete orchestration.</span>
  </a>
  <a class="agent-overview-card agent-overview-card--link" href="conductor-agents.html">
    <span class="agent-overview-card__kicker">Compiled graphs</span>
    <strong>Conductor Agents</strong>
    <span>Author with a Conductor SDK or bring a supported framework agent. Compile it into an inspectable graph, deploy it, and reuse it as an <code>AGENT</code> task.</span>
  </a>
  <a class="agent-overview-card agent-overview-card--link" href="a2a-integration.html">
    <span class="agent-overview-card__kicker">Remote delegation</span>
    <strong>A2A agents</strong>
    <span>Invoke an agent running behind the Agent2Agent protocol through a durable <code>AGENT</code> task. Conductor manages the handoff without compiling that agent locally.</span>
  </a>
</div>

## Operating principles

Adaptive behavior stays manageable when the execution contract is explicit. These principles apply across all three authoring paths.

<div class="agent-overview-grid agent-overview-grid--principles">
  <div class="agent-overview-card">
    <span class="agent-overview-card__number">01</span>
    <strong>Model output is a proposal</strong>
    <span>Plans and tool arguments must pass schema validation, policy, guardrails, and approval before they become executable work.</span>
  </div>
  <div class="agent-overview-card">
    <span class="agent-overview-card__number">02</span>
    <strong>State belongs in the workflow</strong>
    <span>Progress, waits, decisions, and results live in durable execution state—not only in the memory of an agent process.</span>
  </div>
  <div class="agent-overview-card">
    <span class="agent-overview-card__number">03</span>
    <strong>Side effects cross task boundaries</strong>
    <span>Workers and system tasks perform approved actions through bounded, observable interfaces with defined retries and timeouts.</span>
  </div>
  <div class="agent-overview-card">
    <span class="agent-overview-card__number">04</span>
    <strong>Every turn is governable</strong>
    <span>Proposals, policy outcomes, approvals, inputs, outputs, retries, timing, and terminal state remain inspectable and recoverable.</span>
  </div>
</div>

## What you gain

Conductor applies the same durable execution model to adaptive agents and ordinary distributed workflows.

<div class="agent-overview-grid agent-overview-grid--outcomes">
  <div class="agent-overview-card"><strong>Durable execution</strong><span>Resume from persisted progress across crashes, deploys, retries, and long waits.</span></div>
  <div class="agent-overview-card"><strong>Policy and guardrails</strong><span>Validate model proposals and constrain tools, inputs, fan-out, time, and cost before execution.</span></div>
  <div class="agent-overview-card"><strong>Turn-by-turn observability</strong><span>Inspect the durable record of decisions, policy outcomes, task data, timing, and failures.</span></div>
  <div class="agent-overview-card"><strong>Human control</strong><span>Pause without losing state, collect review or input, then resume the same execution.</span></div>
  <div class="agent-overview-card"><strong>Framework and protocol interoperability</strong><span>Use supported framework bridges, MCP tools, and remote A2A agents behind stable workflow boundaries.</span></div>
  <div class="agent-overview-card"><strong>Ordinary workflow composition</strong><span>Place agents beside APIs, workers, branching, schedules, notifications, and compensation logic.</span></div>
</div>

## Where to start

Choose the boundary that matches what you are building, then deepen only the part of the platform you need.

<div class="agent-overview-grid agent-overview-grid--three agent-overview-grid--next">
  <div class="agent-overview-card">
    <span class="agent-overview-card__kicker">Build</span>
    <strong>Choose an authoring path</strong>
    <span>Compare the three agent models, then learn the native model and retrieval tasks available to declarative workflows.</span>
    <span class="agent-overview-card__links"><a href="../concepts/agents.html">Agent concepts</a><a href="llm-orchestration.html">LLM orchestration</a></span>
  </div>
  <div class="agent-overview-card">
    <span class="agent-overview-card__kicker">Integrate</span>
    <strong>Bring agents into a durable graph</strong>
    <span>Compile SDK or framework-authored agents locally, or invoke independently deployed agents through A2A.</span>
    <span class="agent-overview-card__links"><a href="conductor-agents.html">Conductor Agents</a><a href="a2a-integration.html">A2A integration</a></span>
  </div>
  <div class="agent-overview-card">
    <span class="agent-overview-card__kicker">Operate</span>
    <strong>Design for production</strong>
    <span>Apply the reference architecture, then move through governance, evaluation, deployment, recovery, and operations.</span>
    <span class="agent-overview-card__links"><a href="production-agent-architecture.html">Production architecture</a><a href="production-path.html">Production path</a></span>
  </div>
</div>
