---
hide:
  - navigation
  - toc
description: Conductor is an open source workflow engine and durable execution platform for workflow orchestration, microservice orchestration, and AI agent orchestration. Self-hosted, Apache 2.0 licensed. 14+ native LLM providers, MCP tool calling, and built-in vector database support. Build distributed workflows with saga pattern compensation, at-least-once task delivery, human-in-the-loop approval, and polyglot workers. The workflow automation platform for teams that need LLM orchestration and durable execution at scale.
---

<div class="home-wrapper">

<div class="hero">
  <h1 class="hero-title">The durable runtime <br/> for workflows and AI agents</h1>
  <div class="hero-columns">
    <div class="hero-left">
      <p class="hero-subtitle">Stop stitching together retries, state, and compensation by hand. Conductor gives your workflows a durable runtime.</p>
      <div class="hero-actions">
        <a href="quickstart/" class="btn-primary">Get Started<span class="btn-arrow">&rarr;</span></a>
        <a href="https://github.com/conductor-oss/conductor" class="repo-link">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
          <span>conductor-oss/conductor</span>
        </a>
      </div>
      <div class="hero-skills-cta">
        <a href="https://github.com/conductor-oss/conductor-skills" class="skills-link">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          <span>Install Conductor Skills for your AI agents</span>
          <span class="btn-arrow">&rarr;</span>
        </a>
      </div>
    </div>
    <div class="hero-right">
      <div class="hero-code-block">
        <div class="hero-code-label">Write workers in any language</div>
        <div class="hero-lang-tabs">
          <button class="hero-lang-tab active" data-lang="java">Java</button>
          <button class="hero-lang-tab" data-lang="python">Python</button>
          <button class="hero-lang-tab" data-lang="go">Go</button>
          <button class="hero-lang-tab" data-lang="js">JavaScript</button>
          <button class="hero-lang-tab" data-lang="csharp">C#</button>
        </div>
        <pre class="hero-code"><code id="hero-code-java" class="hero-code-panel active">@WorkerTask("charge_payment")
public ChargeResult charge(OrderInput input) {
    var chargeId = paymentService.charge(input.orderId, input.amount);
    return new ChargeResult(chargeId);
}</code><code id="hero-code-python" class="hero-code-panel">@worker_task(task_definition_name="charge_payment")
def charge_payment(order_id: str, amount: float) -&gt; dict:
    charge_id = payment_service.charge(order_id, amount)
    return {"chargeId": charge_id}</code><code id="hero-code-go" class="hero-code-panel">func ChargePayment(input *OrderInput) (*ChargeResult, error) {
    chargeId, err := paymentService.Charge(input.OrderId, input.Amount)
    if err != nil {
        return nil, err
    }
    return &amp;ChargeResult{ChargeId: chargeId}, nil
}</code><code id="hero-code-js" class="hero-code-panel">worker.register("charge_payment", async ({ orderId, amount }) =&gt; {
    const chargeId = await paymentService.charge(orderId, amount);
    return { chargeId };
});</code><code id="hero-code-csharp" class="hero-code-panel">[WorkerTask("charge_payment")]
public ChargeResult ChargePayment(OrderInput input) {
    var chargeId = _paymentService.Charge(input.OrderId, input.Amount);
    return new ChargeResult(chargeId);
}</code></pre>
      </div>
    </div>
  </div>
</div>

<script>
document.addEventListener("DOMContentLoaded", function() {
  document.querySelectorAll(".hero-lang-tab").forEach(function(tab) {
    tab.addEventListener("click", function() {
      document.querySelectorAll(".hero-lang-tab").forEach(function(t) { t.classList.remove("active"); });
      document.querySelectorAll(".hero-code-panel").forEach(function(p) { p.classList.remove("active"); });
      tab.classList.add("active");
      document.getElementById("hero-code-" + tab.dataset.lang).classList.add("active");
    });
  });
});
</script>

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
    <span class="value-metric">Billions of workflows</span>
    <span class="value-label">internet scale execution</span>
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
      <h3>Durable execution by default</h3>
      <p>Workflow state is persisted at every step. Survive server restarts, worker crashes, and network failures. Durable execution with at-least-once task delivery, configurable retries, timeouts, and compensation flows. Build durable agents that never lose progress.</p>
      <a href="architecture/durable-execution/" class="feature-link">Failure semantics &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Format</div>
      <h3>JSON native &mdash; deterministic by default</h3>
      <p>JSON definitions separate orchestration from implementation &mdash; no side effects, no hidden state, every run is deterministic. Generate workflows at runtime with LLMs, modify per-execution, and use dynamic forks, dynamic tasks, and dynamic sub-workflows for more flexibility than code-based engines. Code via SDKs when you need it.</p>
      <a href="architecture/json-native/" class="feature-link">Why JSON wins &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">Primitives</div>
      <h3>Pause, Resume, Replay, Restart</h3>
      <p>Pause workflows on time, external signals, webhooks, or human approval. Resume safely after minutes, hours, or days. Replay any workflow from the beginning, from a specific task, or retry just the failed step &mdash; even months later. Full execution history is always preserved.</p>
      <a href="architecture/durable-execution/#replay-and-recovery" class="feature-link">How it works &rarr;</a>
    </div>
    <div class="feature-card">
      <div class="feature-tag">AI</div>
      <h3>AI agent orchestration &amp; LLM orchestration</h3>
      <p>Orchestrate AI agents with 14+ native LLM providers (Anthropic, OpenAI, Gemini, Bedrock, Mistral, and more), MCP tool calling, function calling, human-in-the-loop approval, and structured output. Built-in vector database support (Pinecone, pgvector, MongoDB Atlas) for RAG pipelines.</p>
      <a href="devguide/ai/" class="feature-link">AI Cookbook &rarr;</a>
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
    <a href="devguide/ai/" class="arch-card">
      <div class="arch-number">02</div>
      <h3>AI Cookbook</h3>
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
      <summary>Is Conductor open source?</summary>
      <p>Yes. Conductor is a fully open source workflow engine, Apache 2.0 licensed. You can self-host it on your own infrastructure with no vendor lock-in. It supports 8+ persistence backends, 6 message brokers, and runs anywhere Docker runs.</p>
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
      <p>Conductor is the only open source workflow engine with native LLM task types for 14+ providers, built-in MCP integration, and vector database support. Combined with durable execution, 7+ language SDKs (Java, Python, Go, JavaScript, C#, Ruby, Rust), 6 message brokers, 8+ persistence backends, and battle-tested scale at Netflix, Tesla, LinkedIn, and JP Morgan, Conductor provides the most complete workflow orchestration platform available. Unlike Temporal, Step Functions, or Airflow, Conductor is fully self-hosted, supports both code-first and JSON workflow definitions, and provides native AI agent orchestration out of the box.</p>
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
      <a href="resources/contributing/" class="btn-ghost">Contributing guide</a>
    </div>
  </div>
</div>

</div>
