---
hide:
  - navigation
  - toc
description: Conductor is an open-source durable code execution engine and agentic workflow engine. Build distributed workflows with saga pattern compensation, at-least-once task delivery, human-in-the-loop approval, and polyglot workers. The workflow engine for teams that need durable execution at scale.
---

<div class="home-wrapper">

<div class="hero">
  <div class="hero-grid">
    <div class="hero-text">
      <div class="hero-eyebrow">Open Source Durable Execution Engine</div>
      <h1 class="hero-title">Durable execution<br/>for JSON + code native workflows<br/>and AI&nbsp;agents.</h1>
      <p class="hero-subtitle">Model long-running workflows as JSON. Pause on time, signals, webhooks, or human input. Resume safely after failures. Expose any workflow as an API or MCP tool.</p>
      <div class="hero-actions">
        <a href="quickstart/" class="btn-primary">Get Started<span class="btn-arrow">&rarr;</span></a>
        <a href="https://github.com/conductor-oss/conductor" class="btn-ghost">View on GitHub</a>
      </div>
    </div>
    <div class="hero-terminal">
      <div class="terminal-chrome">
        <span class="terminal-dot"></span>
        <span class="terminal-dot"></span>
        <span class="terminal-dot"></span>
        <span class="terminal-title">terminal</span>
      </div>
      <div class="terminal-body">
        <div class="terminal-line"><span class="terminal-prompt">$</span> npm install -g @conductor-oss/conductor-cli</div>
        <div class="terminal-line dim">added 142 packages in 8s</div>
        <div class="terminal-line"><span class="terminal-prompt">$</span> conductor server start</div>
        <div class="terminal-line dim">&#9632; Conductor server running on http://localhost:8080</div>
        <div class="terminal-line dim">&#9632; UI available at http://localhost:8127</div>
        <div class="terminal-line"><span class="terminal-prompt">$</span> conductor workflow start -w kitchensink</div>
        <div class="terminal-line success">"e3b0c442-98fc-1c14-b39f-4c5e8a21d0f7"</div>
      </div>
    </div>
  </div>
</div>

<div class="logo-wall">
  <p class="logo-wall-label">Trusted by engineering teams at</p>
  <div class="logo-marquee">
    <div class="logo-track">
      <span class="logo-name">Netflix</span>
      <span class="logo-name">Tesla</span>
      <span class="logo-name">LinkedIn</span>
      <span class="logo-name">JP Morgan</span>
      <span class="logo-name">Freshworks</span>
      <span class="logo-name">American Express</span>
      <span class="logo-name">Redfin</span>
      <span class="logo-name">VMware</span>
      <span class="logo-name">Coupang</span>
      <span class="logo-name">Swiggy</span>
      <span class="logo-name">Netflix</span>
      <span class="logo-name">Tesla</span>
      <span class="logo-name">LinkedIn</span>
      <span class="logo-name">JP Morgan</span>
      <span class="logo-name">Freshworks</span>
      <span class="logo-name">American Express</span>
      <span class="logo-name">Redfin</span>
      <span class="logo-name">VMware</span>
      <span class="logo-name">Coupang</span>
      <span class="logo-name">Swiggy</span>
    </div>
  </div>
</div>

<div class="value-strip">
  <div class="value-item">
    <span class="value-metric">Guaranteed at-least-once</span>
    <span class="value-label">task delivery</span>
  </div>
  <div class="value-divider"></div>
  <div class="value-item">
    <span class="value-metric">Any language</span>
    <span class="value-label">worker support</span>
  </div>
  <div class="value-divider"></div>
  <div class="value-item">
    <span class="value-metric">Millions</span>
    <span class="value-label">concurrent workflows</span>
  </div>
  <div class="value-divider"></div>
  <div class="value-item">
    <span class="value-metric">Zero downtime</span>
    <span class="value-label">version upgrades</span>
  </div>
</div>

<div class="features-section">
  <div class="section-header-inline">
    <span class="section-label">Capabilities</span>
    <h2>Built for workflows that can't afford to fail.</h2>
  </div>
  <div class="features-grid">
    <div class="feature-card feature-accent">
      <div class="feature-tag">Core</div>
      <h3>Durable by default</h3>
      <p>Workflow state is persisted at every step. Survive server restarts, worker crashes, and network failures. At-least-once task delivery with configurable retries, timeouts, and compensation flows.</p>
      <a href="architecture/durable-execution/" class="feature-link">Failure semantics &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Format</div>
      <h3>JSON + code native workflows</h3>
      <p>Define workflows as JSON&mdash;store, version, diff, and generate them programmatically. Create dynamic workflows at runtime via SDK, API, or UI without a compile/deploy cycle.</p>
      <a href="architecture/json-native/" class="feature-link">How it works &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Primitives</div>
      <h3>Wait, Signal, Pause, Resume, Retry</h3>
      <p>Pause workflows on time, external signals, webhooks, or human approval. Resume safely after minutes, hours, or days&mdash;even across deploys and version changes.</p>
    </div>
    <div class="feature-card">
      <div class="feature-tag">AI</div>
      <h3>Agent-ready orchestration</h3>
      <p>Orchestrate AI agents with LLM tasks, tool calls, human-in-the-loop approval, and structured output. Expose any workflow as an API endpoint or MCP tool.</p>
      <a href="architecture/agents/" class="feature-link">Agent patterns &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Workers</div>
      <h3>Polyglot workers</h3>
      <p>Write task workers in <a href="https://github.com/conductor-oss/java-sdk">Java</a>, <a href="https://github.com/conductor-oss/python-sdk">Python</a>, <a href="https://github.com/conductor-oss/go-sdk">Go</a>, <a href="https://github.com/conductor-oss/csharp-sdk">C#</a>, <a href="https://github.com/conductor-oss/javascript-sdk">JavaScript</a>, <a href="https://github.com/conductor-oss/ruby-sdk">Ruby</a>, or <a href="https://github.com/conductor-oss/rust-sdk">Rust</a>. Workers poll for tasks, execute your logic, and report results&mdash;run them anywhere.</p>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Reliability</div>
      <h3>Saga pattern &amp; compensation</h3>
      <p>Model distributed transactions as sagas with automatic compensation. When a step fails, Conductor runs your undo logic in reverse order&mdash;rolling back payments, releasing inventory, and cancelling reservations without manual intervention.</p>
      <a href="devguide/how-tos/Workflows/handling-errors/" class="feature-link">Error handling &rarr;</a>
    </div>
  </div>
</div>

<div class="arch-section">
  <div class="section-header-inline">
    <span class="section-label">Architecture</span>
    <h2>Understand the engine.</h2>
  </div>
  <div class="arch-grid">
    <a href="architecture/durable-execution/" class="arch-card">
      <div class="arch-number">01</div>
      <h3>Durable Execution</h3>
      <p>What persists, what gets retried, failure matrix, and state transitions.</p>
    </a>
    <a href="architecture/agents/" class="arch-card">
      <div class="arch-number">02</div>
      <h3>Agents &amp; AI</h3>
      <p>LLM tasks, tool calls, human approval, dynamic workflows, MCP tools.</p>
    </a>
    <a href="architecture/json-native/" class="arch-card">
      <div class="arch-number">03</div>
      <h3>JSON + Code Native</h3>
      <p>Runtime generation, versioning, dynamic definitions, API/SDK parity.</p>
    </a>
    <a href="devguide/architecture/" class="arch-card">
      <div class="arch-number">04</div>
      <h3>System Architecture</h3>
      <p>Worker-task queues, persistence, polling, distributed consistency.</p>
    </a>
  </div>
</div>

<div class="install-section">
  <div class="section-header-inline">
    <span class="section-label">Install</span>
    <h2>Multiple ways to run.</h2>
  </div>
  <div class="install-options">
    <div class="install-option">
      <h3>CLI <span class="install-rec">recommended</span></h3>
      <div class="install-cmd"><code><span class="install-comment"># Install CLI</span><br/>npm install -g @conductor-oss/conductor-cli<br/><span class="install-comment"># Start the server</span><br/>conductor server start</code></div>
    </div>
    <div class="install-option">
      <h3>Docker</h3>
      <div class="install-cmd"><code>docker run -p 8080:8080 conductoross/conductor:latest</code></div>
    </div>
  </div>
</div>

<div class="faq-section">
  <div class="section-header-inline">
    <span class="section-label">FAQ</span>
    <h2>Frequently asked questions.</h2>
  </div>
  <div class="faq-grid">
    <details class="faq-item">
      <summary>Is this the same as Netflix Conductor?</summary>
      <p>Yes. Conductor OSS is the continuation of the original Netflix Conductor repository after Netflix contributed the project to the open-source foundation.</p>
    </details>
    <details class="faq-item">
      <summary>Is this project actively maintained?</summary>
      <p>Yes. <a href="https://orkes.io">Orkes</a> is the primary maintainer of this repository and offers an enterprise SaaS platform for Conductor across all major cloud providers.</p>
    </details>
    <details class="faq-item">
      <summary>Can Conductor scale to handle my workload?</summary>
      <p>Conductor was built at Netflix to handle massive scale and has been battle-tested in production environments processing millions of workflows. It scales horizontally to meet virtually any demand.</p>
    </details>
    <details class="faq-item">
      <summary>Does Conductor support durable code execution?</summary>
      <p>Yes. Conductor pioneered durable execution patterns, ensuring workflows complete reliably even in the face of infrastructure failures, process crashes, or network issues.</p>
    </details>
    <details class="faq-item">
      <summary>Are workflows always asynchronous?</summary>
      <p>No. While Conductor excels at asynchronous orchestration, it also supports synchronous workflow execution when immediate results are required.</p>
    </details>
    <details class="faq-item">
      <summary>Do I need to use a Conductor-specific framework?</summary>
      <p>No. Conductor is language and framework agnostic. Use your preferred language and framework&mdash;SDKs provide native integration for Java, Python, JavaScript, Go, C#, and more.</p>
    </details>
    <details class="faq-item">
      <summary>Is Conductor a low-code/no-code platform?</summary>
      <p>No. Conductor is designed for developers who write code. While workflows can be defined in JSON, the power comes from building workers and tasks in your preferred programming language.</p>
    </details>
    <details class="faq-item">
      <summary>Can Conductor handle complex workflows?</summary>
      <p>Conductor was specifically designed for complex orchestration. It supports advanced patterns including nested loops, dynamic branching, sub-workflows, and workflows with thousands of tasks.</p>
    </details>
    <details class="faq-item">
      <summary>Is Netflix Conductor abandoned?</summary>
      <p>No. The original Netflix repository has transitioned to Conductor OSS, which is the new home for the project. Active development and maintenance continues here.</p>
    </details>
    <details class="faq-item">
      <summary>Is Orkes Conductor compatible with Conductor OSS?</summary>
      <p>100% compatible. Orkes Conductor is built on top of Conductor OSS, ensuring full compatibility between the open-source version and the enterprise offering.</p>
    </details>
  </div>
</div>

<div class="cta-section">
  <div class="cta-content">
    <h2>Open source. Community driven.</h2>
    <p>Apache-2.0 licensed. Originally created at Netflix, now maintained by the community.</p>
    <div class="cta-actions">
      <a href="https://github.com/conductor-oss/conductor" class="btn-primary">Star on GitHub<span class="btn-arrow">&rarr;</span></a>
      <a href="resources/contributing/" class="btn-ghost">Contributing guide</a>
    </div>
  </div>
</div>

</div>
