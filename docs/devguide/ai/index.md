---
description: Build and operate durable AI workflows, framework-authored Conductor Agents, and remote A2A agents with governed execution, observable turns, and human control.
---

# Agents & AI

<section class="agent-runtime-hero" aria-labelledby="agent-runtime-hero-title">
  <div class="agent-runtime-hero__content">
    <p class="agent-runtime-hero__eyebrow">Reasoning meets durable execution</p>
    <h2 id="agent-runtime-hero-title">Models propose. Conductor governs and executes.</h2>
    <p>Models and agent frameworks provide reasoning, planning, and tool selection. Conductor provides the production execution layer around them: durable state, bounded task execution, policy checks, approvals, retries, cancellation, and a complete record of every turn.</p>
    <p>The result is an agent system that can adapt at runtime without hiding control flow or entrusting side effects to an opaque model process.</p>
    <nav class="agent-runtime-hero__paths" aria-label="Agent authoring paths">
      <a href="llm-orchestration.html">Declarative workflows</a>
      <a href="conductor-agents.html">Conductor Agents</a>
      <a href="a2a-integration.html">A2A agents</a>
    </nav>
    <div class="agent-runtime-hero__legend" aria-label="Architecture legend">
      <span><i class="agent-runtime-hero__swatch agent-runtime-hero__swatch--authoring" aria-hidden="true"></i>Authoring</span>
      <span><i class="agent-runtime-hero__swatch agent-runtime-hero__swatch--brain" aria-hidden="true"></i>Brain</span>
      <span><i class="agent-runtime-hero__swatch agent-runtime-hero__swatch--runtime" aria-hidden="true"></i>Conductor</span>
      <span><i class="agent-runtime-hero__swatch agent-runtime-hero__swatch--hands" aria-hidden="true"></i>Hands</span>
    </div>
  </div>
  <svg class="agent-runtime-hero__diagram" viewBox="0 0 520 470" role="img" aria-labelledby="agent-runtime-diagram-title agent-runtime-diagram-description">
    <title id="agent-runtime-diagram-title">Conductor agent architecture</title>
    <desc id="agent-runtime-diagram-description">Native workflow definitions or SDK and framework agents provide the authoring layer. An LLM proposes plans, actions, and tool arguments. Conductor applies schemas, guardrails, policy, approvals, durable state, retries, waits, cancellation, and turn-level observability. Workers, MCP tools, APIs, data systems, remote A2A agents, and people execute approved actions. Results return through Conductor into durable state and the next model turn.</desc>
    <defs>
      <marker id="agent-runtime-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" class="agent-runtime-hero__arrowhead" />
      </marker>
      <marker id="agent-runtime-arrow-runtime" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" class="agent-runtime-hero__arrowhead agent-runtime-hero__arrowhead--runtime" />
      </marker>
    </defs>

    <text x="20" y="18" class="agent-runtime-hero__lane-label">AUTHORING</text>
    <rect x="20" y="28" width="480" height="72" rx="12" class="agent-runtime-hero__authoring-box" />
    <rect x="38" y="44" width="205" height="40" rx="8" class="agent-runtime-hero__authoring-card" />
    <text x="140" y="68" text-anchor="middle" class="agent-runtime-hero__label">Native workflow definitions</text>
    <rect x="277" y="44" width="205" height="40" rx="8" class="agent-runtime-hero__authoring-card" />
    <text x="379" y="61" text-anchor="middle" class="agent-runtime-hero__label">SDK / framework agents</text>
    <text x="379" y="75" text-anchor="middle" class="agent-runtime-hero__detail">compiled into workflow graphs</text>

    <path d="M 260 100 V 124" class="agent-runtime-hero__arrow" marker-end="url(#agent-runtime-arrow)" />
    <rect x="75" y="126" width="370" height="62" rx="12" class="agent-runtime-hero__brain-box" />
    <text x="260" y="149" text-anchor="middle" class="agent-runtime-hero__runtime-title agent-runtime-hero__runtime-title--brain">Brain · LLM reasoning</text>
    <text x="260" y="169" text-anchor="middle" class="agent-runtime-hero__detail">proposes plans · actions · tool arguments</text>

    <path d="M 260 188 V 210" class="agent-runtime-hero__arrow agent-runtime-hero__arrow--runtime" marker-end="url(#agent-runtime-arrow-runtime)" />
    <rect x="55" y="212" width="410" height="98" rx="14" class="agent-runtime-hero__runtime-box" />
    <text x="260" y="238" text-anchor="middle" class="agent-runtime-hero__runtime-title">Conductor platform</text>
    <text x="260" y="258" text-anchor="middle" class="agent-runtime-hero__runtime-detail">schemas · guardrails · policy · approvals</text>
    <text x="260" y="276" text-anchor="middle" class="agent-runtime-hero__runtime-detail">durable state · retries · waits · cancellation</text>
    <path d="M 127 286 H 393" class="agent-runtime-hero__runtime-rule" />
    <text x="260" y="301" text-anchor="middle" class="agent-runtime-hero__detail">turn record: proposals · outcomes · inputs · outputs · timing</text>

    <path d="M 260 310 V 342" class="agent-runtime-hero__arrow agent-runtime-hero__arrow--runtime" marker-end="url(#agent-runtime-arrow-runtime)" />
    <text x="20" y="340" class="agent-runtime-hero__lane-label">HANDS · APPROVED EXECUTION</text>
    <rect x="14" y="350" width="90" height="58" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="59" y="376" text-anchor="middle" class="agent-runtime-hero__label">Workers</text>
    <text x="59" y="393" text-anchor="middle" class="agent-runtime-hero__detail">services + jobs</text>
    <rect x="114" y="350" width="90" height="58" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="159" y="376" text-anchor="middle" class="agent-runtime-hero__label">MCP tools</text>
    <text x="159" y="393" text-anchor="middle" class="agent-runtime-hero__detail">tools + retrieval</text>
    <rect x="214" y="350" width="90" height="58" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="259" y="376" text-anchor="middle" class="agent-runtime-hero__label">APIs + data</text>
    <text x="259" y="393" text-anchor="middle" class="agent-runtime-hero__detail">side effects</text>
    <rect x="314" y="350" width="90" height="58" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="359" y="374" text-anchor="middle" class="agent-runtime-hero__label">Remote A2A</text>
    <text x="359" y="392" text-anchor="middle" class="agent-runtime-hero__detail">agents</text>
    <rect x="414" y="350" width="90" height="58" rx="9" class="agent-runtime-hero__hands-card" />
    <text x="459" y="376" text-anchor="middle" class="agent-runtime-hero__label">People</text>
    <text x="459" y="393" text-anchor="middle" class="agent-runtime-hero__detail">review + input</text>

    <path d="M 459 408 V 430 H 20 V 261 H 55" class="agent-runtime-hero__turn-loop" marker-end="url(#agent-runtime-arrow-runtime)" />
    <text x="260" y="449" text-anchor="middle" class="agent-runtime-hero__compile">results return through durable state</text>
    <path d="M 465 261 H 498 V 157 H 445" class="agent-runtime-hero__turn-loop" marker-end="url(#agent-runtime-arrow-runtime)" />
    <text x="506" y="212" text-anchor="middle" transform="rotate(-90 506 212)" class="agent-runtime-hero__compile">next model turn</text>
  </svg>
</section>

## How the architecture works

Authoring starts either with a native workflow definition or with SDK and framework code that compiles into a workflow graph. During a turn, the model—the brain—proposes a plan, an action, or tool arguments. That output becomes data entering Conductor, not an instruction that bypasses the runtime.

Conductor validates the proposal, applies policy and approvals, records durable state, and schedules only the accepted work. The hands—workers, MCP tools, APIs, data systems, remote A2A agents, and people—perform that work at explicit task boundaries. Their results return through Conductor, where they are persisted and become context for the next turn. A crash or long wait therefore interrupts infrastructure, not the logical execution history.

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
