---
hide:
  - navigation
  - toc
description: Conductor is an open-source platform for building production-grade AI agents and workflows. Open sourced by Netflix Engineering — cloud agnostic, language agnostic, and deployment agnostic.
---

<div class="home-wrapper">

<div class="hero">
  <div class="hero-badge">Apache 2.0 Licensed &middot; Originally created at Netflix</div>
  <h1 class="hero-title">Build production-grade<br/>AI Agents and Workflows</h1>
  <p class="hero-subtitle">Open sourced by Netflix Engineering, built for high performance and scale. Cloud Agnostic, Language Agnostic and Deployment Agnostic.</p>
  <p class="hero-subtitle">Build workflows and agents natively, or bring your own agents from any framework including <strong>LangGraph</strong>, <strong>LangChain</strong>, <strong>Google ADK</strong>, and the <strong>OpenAI SDK</strong>. Run them all on one durable engine.</p>
  <div class="hero-actions">
    <a href="quickstart/index.html" class="btn-primary">Get Started<span class="btn-arrow">&rarr;</span></a>
    <a href="https://github.com/conductor-oss/conductor" class="repo-link" id="hero-repo-link">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
      <span>View on Github</span>
      <span class="repo-stats" id="repo-stats"></span>
    </a>
    <script>
      fetch("https://api.github.com/repos/conductor-oss/conductor")
        .then(function(r){return r.json()})
        .then(function(d){
          var el=document.getElementById("repo-stats");
          if(el&&d.stargazers_count){
            var s=d.stargazers_count>=1000?(d.stargazers_count/1000).toFixed(1)+"k":d.stargazers_count;
            el.innerHTML='<span class="repo-stat">&#9733; '+s+'</span>';
          }
        }).catch(function(){});
    </script>
  </div>
  <p class="home-skills-line">Using an AI coding agent? Install <a href="devguide/how-tos/conductor-skills.html">Conductor Skills</a>.</p>
</div>

<div class="home-section home-section--alt">
  <div class="section-header-inline">
    <h2>Code in any language</h2>
    <p class="home-section-sub">Conductor orchestrates across languages and services instead of forcing workflow logic into a single application runtime</p>
  </div>
  <div class="home-sdk-grid">
    <a class="home-sdk-card" href="documentation/clientsdks/python-sdk.html">
      <img src="https://orkes.io/content/img/Python_logo.svg" alt="" />
      <span class="home-sdk-card__meta"><strong>Python</strong><span>conductor-oss/python-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
    <a class="home-sdk-card" href="documentation/clientsdks/java-sdk.html">
      <img src="https://orkes.io/content/img/java.svg" alt="" />
      <span class="home-sdk-card__meta"><strong>Java</strong><span>conductor-oss/java-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
    <a class="home-sdk-card" href="documentation/clientsdks/js-sdk.html">
      <img src="https://orkes.io/content/img/JavaScript_logo_2.svg" alt="" />
      <span class="home-sdk-card__meta"><strong>TypeScript</strong><span>conductor-oss/javascript-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
    <a class="home-sdk-card" href="documentation/clientsdks/csharp-sdk.html">
      <img src="https://orkes.io/content/img/csharp.png" alt="" />
      <span class="home-sdk-card__meta"><strong>.NET</strong><span>conductor-oss/csharp-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
    <a class="home-sdk-card" href="documentation/clientsdks/go-sdk.html">
      <img src="https://orkes.io/content/img/Go_Logo_Blue.svg" alt="" />
      <span class="home-sdk-card__meta"><strong>Go</strong><span>conductor-oss/go-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
    <a class="home-sdk-card" href="documentation/clientsdks/ruby-sdk.html">
      <img src="https://upload.wikimedia.org/wikipedia/commons/7/73/Ruby_logo.svg" alt="" />
      <span class="home-sdk-card__meta"><strong>Ruby</strong><span>conductor-oss/ruby-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
    <a class="home-sdk-card" href="documentation/clientsdks/rust-sdk.html">
      <img src="https://upload.wikimedia.org/wikipedia/commons/d/d5/Rust_programming_language_black_logo.svg" alt="" />
      <span class="home-sdk-card__meta"><strong>Rust</strong><span>conductor-oss/rust-sdk</span></span>
      <span class="home-sdk-card__arrow">&rarr;</span>
    </a>
  </div>
</div>

<div class="home-section">
  <div class="section-header-inline">
    <h2>Getting Started</h2>
    <p class="home-section-sub">Start with the core OSS paths or explore Orkes Conductor Developer Edition</p>
  </div>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="quickstart/connect.html">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4.5 16.5c-1.5 1.3-2 5-2 5s3.7-.5 5-2c.7-.8.7-2 0-2.8-.8-.7-2.2-.7-3 .8Z"/><path d="m12 15-3-3a22 22 0 0 1 2-3.9A12.7 12.7 0 0 1 21.5 2.5c0 2.7-.8 7.5-5.6 10.5a22.4 22.4 0 0 1-3.9 2Z"/><path d="M9 12H4s.5-3 2-4c1.6-1.1 5 0 5 0"/><path d="M12 15v5s3-.5 4-2c1.1-1.6 0-5 0-5"/></svg></span>
      <span class="integration-action-card__title">Deploy Conductor in minutes</span>
      <span>Install conductor and quickly deploy your first agents and workflows.</span>
      <span class="home-card-cta">Set up Conductor &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://github.com/conductor-oss/conductor/releases">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg></span>
      <span class="integration-action-card__title">Download Conductor OSS</span>
      <span>Download and install the latest release from the official GitHub repo.</span>
      <span class="home-card-cta">Install Conductor &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://developer.orkescloud.com/">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19a4.5 4.5 0 1 0-.4-9A7 7 0 1 0 4 14.9"/><path d="M12 12v9"/><path d="m8 17 4-4 4 4"/></svg></span>
      <span class="integration-action-card__title">Free Orkes Conductor Developer Edition</span>
      <span>Get started with a free hosted version of Conductor.</span>
      <span class="home-card-cta">Start for free &rarr;</span>
    </a>
    <a class="integration-action-card" href="quickstart/index.html">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg></span>
      <span class="integration-action-card__title">Quickstart guide</span>
      <span>Run conductor locally, register workflows and agents, and execute it end-to-end.</span>
      <span class="home-card-cta">Start here &rarr;</span>
    </a>
    <a class="integration-action-card" href="devguide/running/deploy.html">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14a9 3 0 0 0 18 0V5"/><path d="M3 12a9 3 0 0 0 18 0"/></svg></span>
      <span class="integration-action-card__title">Self-hosting</span>
      <span>Deploy Conductor OSS with Docker, shared persistence, and production-ready topology.</span>
      <span class="home-card-cta">Deploy OSS &rarr;</span>
    </a>
    <a class="integration-action-card" href="devguide/cookbook/index.html">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg></span>
      <span class="integration-action-card__title">Cookbook</span>
      <span>Reference patterns for microservices, timers, event-driven workflows, and AI orchestration.</span>
      <span class="home-card-cta">Browse recipes &rarr;</span>
    </a>
  </div>
</div>

<div class="home-section home-section--alt">
  <div class="section-header-inline">
    <h2>Conductor Resources</h2>
    <p class="home-section-sub">Core documentation, architecture, recipes, and direct answers to the common durability and comparison questions</p>
  </div>
  <div class="integration-action-grid integration-action-grid--four">
    <a class="integration-action-card" href="quickstart/index.html">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg></span>
      <span class="integration-action-card__title">Documentation</span>
      <span>Documentation curated to enable developers to start building using Conductor.</span>
      <span class="home-card-cta">Read docs &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://orkes.io/blog">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg></span>
      <span class="integration-action-card__title">Blogs</span>
      <span>Explore technical use cases, community posts, product updates and more.</span>
      <span class="home-card-cta">Read blogs &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://orkes.io/customers">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg></span>
      <span class="integration-action-card__title">Case Studies</span>
      <span>Explore inspiring stories and use cases of how companies have used Conductor to transform their business operations.</span>
      <span class="home-card-cta">Read case studies &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://www.youtube.com/@orkesio">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><polygon points="10 8 16 12 10 16 10 8"/><rect x="2" y="4" width="20" height="16" rx="3"/></svg></span>
      <span class="integration-action-card__title">Videos</span>
      <span>Quickly learn key functions and capabilities of Conductor.</span>
      <span class="home-card-cta">Watch videos &rarr;</span>
    </a>
  </div>
</div>

<div class="home-section">
  <div class="section-header-inline">
    <h2>Join the Community</h2>
    <p class="home-section-sub">Discuss ideas, contribute in public, and track project activity across the open-source community</p>
  </div>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="https://join.slack.com/t/orkes-conductor/shared_invite/zt-3dpcskdyd-W895bJDm8psAV7viYG3jFA">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.4 8.4 0 0 1-8.5 8.4 8.6 8.6 0 0 1-3.9-.9L3 21l2-5.4a8.3 8.3 0 0 1-1-4A8.4 8.4 0 0 1 12.5 3a8.4 8.4 0 0 1 8.5 8.5Z"/></svg></span>
      <span class="integration-action-card__title">Community</span>
      <span>Join the public Slack community to ask questions and share resources.</span>
      <span class="home-card-cta">Join Slack &rarr;</span>
    </a>
    <a class="integration-action-card" href="resources/contribute/index.html">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M6 21V9a9 9 0 0 0 9 9"/></svg></span>
      <span class="integration-action-card__title">Contributing</span>
      <span>Open pull requests, report issues, and review the project security and contribution policies.</span>
      <span class="home-card-cta">Contribute to Conductor &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://orkes.io/events">
      <span class="home-card-icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></span>
      <span class="integration-action-card__title">Events</span>
      <span>See us in action at an event or sign up for one of our upcoming livestreams.</span>
      <span class="home-card-cta">See upcoming events &rarr;</span>
    </a>
  </div>
</div>

<div class="faq-section home-section home-section--alt">
  <div class="section-header-inline">
    <h2>Frequently asked questions.</h2>
  </div>
  <div class="faq-grid">
    <details class="faq-item">
      <summary>How do I run Conductor with Docker?</summary>
      <p>Run <code>docker run -p 8080:8080 conductoross/conductor:3.32.0-rc.23</code> to start Conductor with all dependencies included. The server will be available at <code>http://localhost:8080</code>. For production deployments with external persistence, see the <a href="devguide/running/deploy.html">Docker deployment guide</a>.</p>
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
      <p>Conductor servers and workers scale independently. Use task domains, concurrency limits, persistence configuration, and metrics to match throughput and isolation to your environment.</p>
    </details>
    <details class="faq-item">
      <summary>Does Conductor support durable execution?</summary>
      <p>Yes. Conductor persists workflow and task state, supports recovery after worker and infrastructure failure, and exposes retries, timeouts, pause, resume, and termination controls.</p>
    </details>
    <details class="faq-item">
      <summary>Can I replay a workflow after it completes or fails?</summary>
      <p>Conductor supports restart, rerun, and retry controls. Execution-history retention depends on configuration, and <code>keepLastN</code> intentionally removes older loop iterations.</p>
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
      <p>JSON keeps orchestration as machine-readable data while workers and built-in tasks perform business logic and side effects. Use validated runtime definitions, dynamic tasks, and dynamic forks when the path is selected at runtime.</p>
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
      <p>Yes. Conductor provides native LLM tasks, MCP tool discovery and calls, human approval, and vector workflows for RAG. See the maintained Agents &amp; AI documentation for provider and capability details.</p>
    </details>
    <details class="faq-item">
      <summary>What does Conductor provide for adaptive agents?</summary>
      <p>Conductor combines native AI and MCP tasks with durable loops, branches, fan-out, approval, retry, cancellation, and an inspectable execution history. Start with the governed adaptive graph.</p>
    </details>
  </div>
</div>

</div>
