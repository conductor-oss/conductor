---
hide:
  - navigation
  - toc
description: Conductor is an open source workflow engine and durable execution platform for workflow orchestration, microservice orchestration, and AI agent orchestration. Self-hosted, Apache 2.0 licensed. 14+ native LLM providers, MCP tool calling, and built-in vector database support. Build distributed workflows with saga pattern compensation, at-least-once task delivery, human-in-the-loop approval, and polyglot workers. The workflow automation platform for teams that need LLM orchestration and durable execution at scale.
---

<div class="home-wrapper">

<div class="hero">
  <div class="hero-badge">Apache 2.0 Licensed &middot; Originally created at Netflix</div>
  <h1 class="hero-title">Code breaks. Infrastructure fails.<br/><span class="hero-highlight">Your workflows don't.</span></h1>
  <p class="hero-subtitle">Crash-proof workflows and AI agents that finish what they start &mdash; powered by durable execution at Netflix scale.</p>
  <p class="hero-differentiators">No SDK restrictions. No non-determinism bugs. No cloud lock-in.</p>
  <div class="hero-actions">
    <a href="quickstart/index.html" class="btn-primary">Get Started<span class="btn-arrow">&rarr;</span></a>
    <a href="https://github.com/conductor-oss/conductor" class="repo-link" id="hero-repo-link">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
      <span>conductor-oss/conductor</span>
      <span class="repo-stats" id="repo-stats"></span>
    </a>
    <script>
      fetch("https://api.github.com/repos/conductor-oss/conductor")
        .then(function(r){return r.json()})
        .then(function(d){
          var el=document.getElementById("repo-stats");
          if(el&&d.stargazers_count){
            var s=d.stargazers_count>=1000?(d.stargazers_count/1000).toFixed(1)+"k":d.stargazers_count;
            var f=d.forks_count>=1000?(d.forks_count/1000).toFixed(1)+"k":d.forks_count;
            el.innerHTML='<span class="repo-stat">&#9733; '+s+'</span><span class="repo-stat"><svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor"><path d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"/></svg> '+f+'</span>';
          }
        }).catch(function(){});
    </script>
  </div>
  <div class="hero-install"><code>$ npm install -g @conductor-oss/conductor-cli</code></div>
  <div class="hero-ai-card">
    <div class="hero-ai-header">
      <div class="hero-ai-icon">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a4 4 0 0 0-4 4c0 2 1.5 3 1.5 5h5c0-2 1.5-3 1.5-5a4 4 0 0 0-4-4z"/><line x1="10" y1="17" x2="14" y2="17"/><line x1="10" y1="20" x2="14" y2="20"/><line x1="11" y1="23" x2="13" y2="23"/></svg>
      </div>
      <h3>Build with AI Agents</h3>
    </div>
    <div class="hero-ai-body">
      <div class="hero-ai-item">
        <a href="devguide/how-tos/conductor-skills.html" class="hero-ai-link" title="Conductor Skills for AI agent orchestration">Conductor Skills &rarr;</a>
        <span class="hero-ai-sub">Install Conductor Skills for your AI Agent</span>
      </div>
      <div class="hero-ai-item">
        <a href="devguide/ai/index.html" class="hero-ai-link" title="AI Cookbook — LLM orchestration, MCP tools, and durable agents">AI Cookbook &rarr;</a>
        <span class="hero-ai-sub">14+ LLM providers, MCP tool calling, human-in-the-loop, and durable agent execution.</span>
      </div>
    </div>
  </div>
</div>

<div class="value-strip">
  <div class="value-item"><div class="value-metric">Guaranteed at-least-once</div><div class="value-label">Task Delivery</div></div>
  <div class="value-divider"></div>
  <div class="value-item"><div class="value-metric">Any language</div><div class="value-label">Worker Support</div></div>
  <div class="value-divider"></div>
  <div class="value-item"><div class="value-metric">Millions</div><div class="value-label">Concurrent Workflows</div></div>
  <div class="value-divider"></div>
  <div class="value-item"><div class="value-metric">Billions of workflows</div><div class="value-label">Internet Scale Execution</div></div>
</div>

<div class="logo-wall hero-logos">
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

<div class="features-section">
  <div class="section-header-inline">
    <h2>Built for workflows that can't afford to fail.</h2>
  </div>
  <div class="features-grid">
    <div class="feature-card feature-accent">
      <div class="feature-tag">Core</div>
      <h3>Durable execution by default</h3>
      <p>Workflow state is persisted at every step. Survive server restarts, worker crashes, and network failures. Durable execution with at-least-once task delivery, configurable retries, timeouts, and compensation flows. Build durable agents that never lose progress.</p>
      <a href="architecture/durable-execution.html" class="feature-link">Failure semantics &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">JSON superpower</div>
      <h3>JSON native &mdash; deterministic by default</h3>
      <p>JSON definitions separate orchestration from implementation &mdash; no side effects, no hidden state, every run is deterministic. Generate workflows at runtime with LLMs, modify per-execution, and use dynamic forks, dynamic tasks, and dynamic sub-workflows for more flexibility than code-based engines. Code via SDKs when you need it.</p>
      <a href="architecture/json-native.html" class="feature-link">Why JSON wins &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Primitives</div>
      <h3>Replay, Restart, Pause, Resume</h3>
      <p>Pause workflows on time, external signals, webhooks, or human approval. Resume safely after minutes, hours, or days. Replay any workflow from the beginning, from a specific task, or retry just the failed step &mdash; even months later. Full execution history is always preserved.</p>
      <a href="architecture/durable-execution.html#replay-and-recovery" class="feature-link">How it works &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">AI</div>
      <h3>AI agent orchestration &amp; LLM orchestration</h3>
      <p>Orchestrate AI agents with 14+ native LLM providers (Anthropic, OpenAI, Gemini, Bedrock, Mistral, and more), MCP tool calling, function calling, human-in-the-loop approval, and structured output. Built-in vector database support (Pinecone, pgvector, MongoDB Atlas) for RAG pipelines.</p>
      <a href="devguide/ai/index.html" class="feature-link">AI Cookbook &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Workers</div>
      <h3>Polyglot workers</h3>
      <p>Write task workers in any language. Workers poll for tasks, execute your logic, and report results&mdash;run them anywhere.</p>
      <div class="lang-logos">
        <a href="https://github.com/conductor-oss/java-sdk" title="Java"><img src="https://orkes.io/content/img/java.svg" alt="Java"></a>
        <a href="https://github.com/conductor-oss/python-sdk" title="Python"><img src="https://orkes.io/content/img/Python_logo.svg" alt="Python"></a>
        <a href="https://github.com/conductor-oss/go-sdk" title="Go"><img src="https://orkes.io/content/img/Go_Logo_Blue.svg" alt="Go"></a>
        <a href="https://github.com/conductor-oss/csharp-sdk" title="C#"><img src="https://orkes.io/content/img/csharp.png" alt="C#"></a>
        <a href="https://github.com/conductor-oss/javascript-sdk" title="JavaScript"><img src="https://orkes.io/content/img/JavaScript_logo_2.svg" alt="JavaScript"></a>
        <a href="https://github.com/conductor-oss/ruby-sdk" title="Ruby"><img src="https://upload.wikimedia.org/wikipedia/commons/7/73/Ruby_logo.svg" alt="Ruby"></a>
        <a href="https://github.com/conductor-oss/rust-sdk" title="Rust"><img src="https://upload.wikimedia.org/wikipedia/commons/d/d5/Rust_programming_language_black_logo.svg" alt="Rust"></a>
      </div>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Reliability</div>
      <h3>Saga pattern &amp; compensation</h3>
      <p>Model distributed transactions as sagas. When a step fails, Conductor automatically runs undo logic in reverse order&mdash;no manual intervention.</p>
      <a href="devguide/how-tos/Workflows/handling-errors.html" class="feature-link">Error handling &rarr;</a>
    </div>
  </div>
</div>

<div class="arch-section">
  <div class="section-header-inline">
    <h2>Understand the engine.</h2>
  </div>
  <div class="arch-grid">
    <a href="architecture/durable-execution.html" class="arch-card">
      <div class="arch-number">01</div>
      <h3>Durable Execution</h3>
      <p>What persists, what gets retried, failure matrix, and state transitions.</p>
    </a>
    <a href="devguide/ai/index.html" class="arch-card">
      <div class="arch-number">02</div>
      <h3>AI Cookbook</h3>
      <p>LLM tasks, tool calls, human approval, dynamic workflows, MCP tools.</p>
    </a>
    <a href="architecture/json-native.html" class="arch-card">
      <div class="arch-number">03</div>
      <h3>JSON + Code Native</h3>
      <p>Runtime generation, versioning, dynamic definitions, API/SDK parity.</p>
    </a>
    <a href="devguide/architecture/index.html" class="arch-card">
      <div class="arch-number">04</div>
      <h3>System Architecture</h3>
      <p>Worker-task queues, persistence, polling, distributed consistency.</p>
    </a>
  </div>
</div>

<div class="faq-section">
  <div class="section-header-inline">
    <h2>Frequently asked questions.</h2>
  </div>
  <div class="faq-grid">
    <details class="faq-item">
      <summary>How do I run Conductor with Docker?</summary>
      <p>Run <code>docker run -p 8080:8080 conductoross/conductor:latest</code> to start Conductor with all dependencies included. The server will be available at <code>http://localhost:8080</code>. For production deployments with external persistence, see the <a href="devguide/running/deploy.html">Docker deployment guide</a>.</p>
    </details>
    <details class="faq-item">
      <summary>Is Conductor open source?</summary>
      <p>Yes. Conductor is a fully open source workflow engine, Apache 2.0 licensed. You can self-host it on your own infrastructure with no vendor lock-in. It supports 5 persistence backends, 6 message brokers, and runs anywhere Docker runs.</p>
    </details>
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
      <summary>Does Conductor support durable execution?</summary>
      <p>Yes. Conductor pioneered durable execution patterns, ensuring workflows and durable agents complete reliably even in the face of infrastructure failures, process crashes, or network issues.</p>
    </details>
    <details class="faq-item">
      <summary>Can I replay a workflow after it completes or fails?</summary>
      <p>Yes. Conductor preserves full execution history indefinitely. You can restart from the beginning, rerun from any specific task, or retry just the failed step &mdash; even months later. Use the API (<code>/restart</code>, <code>/rerun</code>, <code>/retry</code>) or the UI.</p>
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
      <summary>Isn't JSON too limited for complex workflows?</summary>
      <p>The opposite. JSON separates orchestration from implementation, making every workflow deterministic by construction &mdash; no side effects, no hidden state. Dynamic forks, dynamic tasks, and dynamic sub-workflows let you build workflows that are more flexible than code-based engines. JSON is also AI-native: LLMs can generate and modify workflow definitions at runtime without a compile/deploy cycle. Code-based engines require redeployment for every change.</p>
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
    <details class="faq-item">
      <summary>Can Conductor orchestrate AI agents and LLMs?</summary>
      <p>Yes. Conductor provides AI agent orchestration and LLM orchestration as native capabilities. 14+ LLM providers (Anthropic, OpenAI, Azure OpenAI, Google Gemini, AWS Bedrock, Mistral, Cohere, HuggingFace, Ollama, and more), MCP tool calling and function calling (LIST_MCP_TOOLS, CALL_MCP_TOOL), vector database integration (Pinecone, pgvector, MongoDB Atlas) for RAG, and content generation (image, audio, video, PDF). All with the same durability guarantees as any other workflow task.</p>
    </details>
    <details class="faq-item">
      <summary>How does Conductor compare to other workflow engines?</summary>
      <p>Conductor is the only open source workflow engine with native LLM task types for 14+ providers, built-in MCP integration, and vector database support. Combined with durable execution, 7+ language SDKs (Java, Python, Go, JavaScript, C#, Ruby, Rust), 6 message brokers, 5 persistence backends, and battle-tested scale at Netflix, Tesla, LinkedIn, and JP Morgan, Conductor provides the most complete workflow orchestration platform available. Unlike Temporal, Step Functions, or Airflow, Conductor is fully self-hosted, supports both code-first and JSON workflow definitions, and provides native AI agent orchestration out of the box.</p>
    </details>
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

<div class="cta-section">
  <div class="cta-content">
    <h2>Open source workflow engine. Community driven.</h2>
    <p>Apache-2.0 licensed. Self-hosted, no vendor lock-in. Originally created at Netflix, now maintained by the community.</p>
    <div class="cta-actions">
      <a href="https://github.com/conductor-oss/conductor" class="btn-primary">Star on GitHub<span class="btn-arrow">&rarr;</span></a>
      <a href="resources/contributing.html" class="btn-ghost">Contributing guide</a>
    </div>
  </div>
</div>

</div>
