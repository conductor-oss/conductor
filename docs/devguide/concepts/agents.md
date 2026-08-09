---
description: "Choose between declarative AI workflows, SDK-authored Conductor Agents, and remote A2A agents, then run each durably with Conductor."
---

# Agent Concepts

<section class="agent-concepts-hero" aria-labelledby="agent-concepts-hero-title">
  <div class="agent-concepts-hero__content">
    <p class="agent-concepts-hero__eyebrow">A practical decision guide</p>
    <h2 id="agent-concepts-hero-title">Author in the place that fits. Run it durably in Conductor.</h2>
    <p>Choose a native workflow when the graph is the application, compile SDK or framework code when the agent already lives in code, or call an A2A service when the agent is independently deployed.</p>
  </div>
  <svg class="agent-concepts-hero__diagram" viewBox="0 0 760 330" role="img" aria-labelledby="agent-concepts-diagram-title agent-concepts-diagram-desc">
    <title id="agent-concepts-diagram-title">Three agent paths through Conductor</title>
    <desc id="agent-concepts-diagram-desc">Declarative workflow definitions, SDK or framework-authored agents, and remote A2A agents each feed through Conductor. The first uses native tasks, the second compiles to a deployed graph, and the third uses a durable AGENT task handoff.</desc>
    <defs>
      <marker id="agent-concepts-arrow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto"><path class="agent-concepts-hero__arrowhead" d="M 0 0 L 8 4 L 0 8 z" /></marker>
    </defs>
    <text class="agent-concepts-hero__lane" x="18" y="34">AUTHOR</text>
    <text class="agent-concepts-hero__lane" x="471" y="34">DURABLE EXECUTION</text>

    <rect class="agent-concepts-hero__source agent-concepts-hero__source--workflow" x="18" y="54" width="248" height="62" rx="10" />
    <text class="agent-concepts-hero__title" x="34" y="80">Declarative workflow definition</text>
    <text class="agent-concepts-hero__detail" x="34" y="101">LLM, MCP, and control-flow tasks</text>
    <path class="agent-concepts-hero__arrow" d="M 266 85 H 402" marker-end="url(#agent-concepts-arrow)" />

    <rect class="agent-concepts-hero__source agent-concepts-hero__source--sdk" x="18" y="135" width="248" height="62" rx="10" />
    <text class="agent-concepts-hero__title" x="34" y="161">SDK or framework-authored agent</text>
    <text class="agent-concepts-hero__detail" x="34" y="182">Compile and deploy a Conductor Agent graph</text>
    <path class="agent-concepts-hero__arrow" d="M 266 166 H 402" marker-end="url(#agent-concepts-arrow)" />

    <rect class="agent-concepts-hero__source agent-concepts-hero__source--a2a" x="18" y="216" width="248" height="62" rx="10" />
    <text class="agent-concepts-hero__title" x="34" y="242">Remote A2A agent</text>
    <text class="agent-concepts-hero__detail" x="34" y="263">External service behind Agent2Agent</text>
    <path class="agent-concepts-hero__arrow" d="M 266 247 H 402" marker-end="url(#agent-concepts-arrow)" />

    <rect class="agent-concepts-hero__conductor" x="405" y="54" width="210" height="224" rx="14" />
    <text class="agent-concepts-hero__conductor-title" x="510" y="96" text-anchor="middle">Conductor</text>
    <text class="agent-concepts-hero__conductor-detail" x="510" y="124" text-anchor="middle">Native workflow tasks</text>
    <text class="agent-concepts-hero__conductor-detail" x="510" y="153" text-anchor="middle">Compiled agent graph</text>
    <text class="agent-concepts-hero__conductor-detail" x="510" y="182" text-anchor="middle">Durable AGENT handoff</text>
    <path class="agent-concepts-hero__rule" d="M 433 200 H 587" />
    <text class="agent-concepts-hero__conductor-detail" x="510" y="226" text-anchor="middle">state · retries · waits</text>
    <text class="agent-concepts-hero__conductor-detail" x="510" y="248" text-anchor="middle">cancellation · records</text>

    <rect class="agent-concepts-hero__outcome" x="620" y="120" width="122" height="92" rx="10" />
    <path class="agent-concepts-hero__arrow agent-concepts-hero__arrow--out" d="M 615 166 H 638" marker-end="url(#agent-concepts-arrow)" />
    <text class="agent-concepts-hero__title" x="681" y="153" text-anchor="middle">Business</text>
    <text class="agent-concepts-hero__title" x="681" y="171" text-anchor="middle">process</text>
    <text class="agent-concepts-hero__detail" x="681" y="193" text-anchor="middle">ordinary tasks + agents</text>
  </svg>
</section>

## Choose your path

<div class="agent-concepts-paths">
  <article class="agent-concepts-path">
    <p class="agent-concepts-path__eyebrow">Build directly</p>
    <h3>Declarative AI workflow</h3>
    <dl>
      <dt>Author behavior</dt><dd>In a Conductor workflow definition.</dd>
      <dt>Conductor runs</dt><dd>The process itself: native LLM, MCP, `SWITCH`, `DO_WHILE`, `WAIT`, `HUMAN`, and other workflow tasks.</dd>
      <dt>Choose it when</dt><dd>Orchestration is the product and you want the complete control flow visible, versioned, and changed as a workflow.</dd>
    </dl>
    <a href="../ai/llm-orchestration.html">Explore LLM orchestration →</a>
  </article>
  <article class="agent-concepts-path">
    <p class="agent-concepts-path__eyebrow">Bring code</p>
    <h3>Conductor Agent</h3>
    <dl>
      <dt>Author behavior</dt><dd>In a Conductor SDK or supported framework, such as OpenAI Agents, Google ADK, LangChain, or LangGraph.</dd>
      <dt>Conductor runs</dt><dd>A compiled, deployed Conductor Agent graph that a parent workflow invokes through `AGENT`.</dd>
      <dt>Choose it when</dt><dd>Your team has agent logic in code or a framework and wants to keep that authoring surface while making its execution durable and inspectable.</dd>
    </dl>
    <a href="../ai/conductor-agents.html">Learn about Conductor Agents →</a>
  </article>
  <article class="agent-concepts-path">
    <p class="agent-concepts-path__eyebrow">Integrate remotely</p>
    <h3>Remote A2A agent</h3>
    <dl>
      <dt>Author behavior</dt><dd>In an independently deployed service that speaks the Agent2Agent protocol.</dd>
      <dt>Conductor runs</dt><dd>A durable `AGENT` task that hands work to the remote service and tracks its lifecycle.</dd>
      <dt>Choose it when</dt><dd>The agent is owned, deployed, or scaled separately and interoperability is more important than compiling it into a local graph.</dd>
    </dl>
    <a href="../ai/a2a-integration.html">Integrate an A2A agent →</a>
  </article>
</div>

## Share one operating model

A single parent workflow can combine ordinary tasks, native AI tasks, deployed Conductor Agents, and remote A2A calls in sequence or in parallel. Conductor owns the durable orchestration around every step: persisted workflow progress, retries, waits, cancellation, approvals and policy boundaries, and the execution record.

Visibility remains path-specific. Native tasks and compiled Conductor Agent graphs run within Conductor. For A2A, Conductor records and manages the durable handoff and lifecycle, while the remote agent's implementation remains remote.

For example, one workflow can validate a request, ask a deployed Conductor Agent to plan the work, send a specialized task to a remote A2A agent, wait for human approval, and then complete the workflow.

## Take the next step

<div class="agent-concepts-next-steps">
  <a class="agent-concepts-next-step" href="../ai/first-ai-agent.html"><strong>Build directly</strong><span>Create a native LLM, tool, and control-flow workflow.</span></a>
  <a class="agent-concepts-next-step" href="../../quickstart/first-agent.html"><strong>Bring existing agent code</strong><span>Run a first SDK-authored Conductor Agent, then deploy it for workflow reuse.</span></a>
  <a class="agent-concepts-next-step" href="../ai/a2a-integration.html"><strong>Integrate a remote agent</strong><span>Call or expose an A2A agent through a durable workflow boundary.</span></a>
</div>
