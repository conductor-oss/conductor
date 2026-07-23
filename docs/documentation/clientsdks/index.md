---
description: "Conductor SDKs for Java, Python, Go, JavaScript, C#, Ruby, and Rust — build workers, workflows, and durable agents in your language of choice."
---

# SDKs

Build Conductor workers and define workflows as code in your language of choice. Every SDK provides task polling, workflow management, and full API coverage for this open source workflow orchestration engine — so you can focus on business logic while Conductor handles retries, state, and orchestration.

<section class="agent-concepts-callout" aria-labelledby="sdk-agents-title">
  <p class="agent-concepts-kicker">Build and run durable agents</p>
  <h2 id="sdk-agents-title">Bring your agent framework to Conductor</h2>
  <p>Supported SDK bridges compile framework-native agents into durable, inspectable Conductor graphs. Run them interactively with <code>run</code>, or <code>deploy</code> and <code>serve</code> them for production, where larger workflows can invoke them as reusable <code>AGENT</code> tasks.</p>
  <div class="agent-framework-strip" aria-label="Supported agent frameworks">
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/openai.svg" alt="" />OpenAI Agents</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/google-adk.svg" alt="" />Google ADK</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/langchain.svg" alt="" />LangChain / LangChain4j</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/langgraph.svg" alt="" />LangGraph / LangGraph4j</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/vercel.svg" alt="" />Vercel AI SDK</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/concepts/ai-agent.svg" alt="" />Native Conductor Agents</span>
  </div>
  <p><a href="../../devguide/ai/agent-framework-recipes.html">Explore framework agent recipes →</a></p>
</section>

<div class="sdk-grid">
<a href="java-sdk.html" class="sdk-card"><div class="sdk-icon sdk-java" aria-hidden="true"></div><div class="sdk-info"><h3>Java</h3><p>Spring Boot integration, annotation-based workers, thread management, and testing framework.</p></div><span class="sdk-arrow">→</span></a>
<a href="python-sdk.html" class="sdk-card"><div class="sdk-icon sdk-python" aria-hidden="true"></div><div class="sdk-info"><h3>Python</h3><p>Decorator-based task definitions, async support, and workflow management.</p></div><span class="sdk-arrow">→</span></a>
<a href="go-sdk.html" class="sdk-card"><div class="sdk-icon sdk-go" aria-hidden="true"></div><div class="sdk-info"><h3>Go</h3><p>Type-safe task definitions, struct-based I/O, and concurrent worker execution.</p></div><span class="sdk-arrow">→</span></a>
<a href="js-sdk.html" class="sdk-card"><div class="sdk-icon sdk-js" aria-hidden="true"></div><div class="sdk-info"><h3>JavaScript / TypeScript</h3><p>Full TypeScript support, Promise-based APIs, and workflow management.</p></div><span class="sdk-arrow">→</span></a>
<a href="csharp-sdk.html" class="sdk-card"><div class="sdk-icon sdk-csharp" aria-hidden="true"></div><div class="sdk-info"><h3>C# / .NET</h3><p>Dependency injection, async/await patterns, and NuGet packages.</p></div><span class="sdk-arrow">→</span></a>
<a href="ruby-sdk.html" class="sdk-card"><div class="sdk-icon sdk-ruby" aria-hidden="true"></div><div class="sdk-info"><h3>Ruby</h3><p>Idiomatic Ruby task definitions and workflow management.</p></div><span class="sdk-arrow">→</span></a>
<a href="rust-sdk.html" class="sdk-card"><div class="sdk-icon sdk-rust" aria-hidden="true"></div><div class="sdk-info"><h3>Rust</h3><p>Type-safe task definitions, async runtime support, and zero-cost abstractions.</p></div><span class="sdk-arrow">→</span></a>
</div>

All SDKs are open source and hosted at [github.com/conductor-oss](https://github.com/conductor-oss). Contributions are welcome.
