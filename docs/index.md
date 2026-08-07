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

<div class="home-section">
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
      <span class="integration-action-card__title">Deploy Conductor in minutes</span>
      <span>Install conductor and quickly deploy your first agents and workflows.</span>
      <span class="home-card-cta">Set up Conductor &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://github.com/conductor-oss/conductor/releases">
      <span class="integration-action-card__title">Download Conductor OSS</span>
      <span>Download and install the latest release from the official GitHub repo.</span>
      <span class="home-card-cta">Install Conductor &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://developer.orkescloud.com/">
      <span class="integration-action-card__title">Free Orkes Conductor Developer Edition</span>
      <span>Get started with a free hosted version of Conductor.</span>
      <span class="home-card-cta">Start for free &rarr;</span>
    </a>
    <a class="integration-action-card" href="quickstart/index.html">
      <span class="integration-action-card__title">Quickstart guide</span>
      <span>Run conductor locally, register workflows and agents, and execute it end-to-end.</span>
      <span class="home-card-cta">Start here &rarr;</span>
    </a>
    <a class="integration-action-card" href="devguide/running/deploy.html">
      <span class="integration-action-card__title">Self-hosting</span>
      <span>Deploy Conductor OSS with Docker, shared persistence, and production-ready topology.</span>
      <span class="home-card-cta">Deploy OSS &rarr;</span>
    </a>
    <a class="integration-action-card" href="devguide/cookbook/index.html">
      <span class="integration-action-card__title">Cookbook</span>
      <span>Reference patterns for microservices, timers, event-driven workflows, and AI orchestration.</span>
      <span class="home-card-cta">Browse recipes &rarr;</span>
    </a>
  </div>
</div>

<div class="home-section">
  <div class="section-header-inline">
    <h2>Conductor Resources</h2>
    <p class="home-section-sub">Core documentation, architecture, recipes, and direct answers to the common durability and comparison questions</p>
  </div>
  <div class="integration-action-grid integration-action-grid--four">
    <a class="integration-action-card" href="quickstart/index.html">
      <span class="integration-action-card__title">Documentation</span>
      <span>Documentation curated to enable developers to start building using Conductor.</span>
      <span class="home-card-cta">Read docs &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://orkes.io/blog">
      <span class="integration-action-card__title">Blogs</span>
      <span>Explore technical use cases, community posts, product updates and more.</span>
      <span class="home-card-cta">Read blogs &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://orkes.io/customers">
      <span class="integration-action-card__title">Case Studies</span>
      <span>Explore inspiring stories and use cases of how companies have used Conductor to transform their business operations.</span>
      <span class="home-card-cta">Read case studies &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://www.youtube.com/@orkesio">
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
      <span class="integration-action-card__title">Community</span>
      <span>Join the public Slack community to ask questions and share resources.</span>
      <span class="home-card-cta">Join Slack &rarr;</span>
    </a>
    <a class="integration-action-card" href="resources/contribute/index.html">
      <span class="integration-action-card__title">Contributing</span>
      <span>Open pull requests, report issues, and review the project security and contribution policies.</span>
      <span class="home-card-cta">Contribute to Conductor &rarr;</span>
    </a>
    <a class="integration-action-card" href="https://orkes.io/events">
      <span class="integration-action-card__title">Events</span>
      <span>See us in action at an event or sign up for one of our upcoming livestreams.</span>
      <span class="home-card-cta">See upcoming events &rarr;</span>
    </a>
  </div>
</div>

</div>
