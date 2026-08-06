---
description: Get started with Conductor — your first durable workflow or agent in minutes, with your AI coding agent or your SDK.
---

# Get started with Conductor

Every path below ends the same way: a durable execution you can inspect — inputs, outputs, task timeline, and recovery controls — in the Conductor UI. Most take about five minutes.

<section class="integration-hero integration-hero--durable" aria-labelledby="get-started-hero-title">
  <div class="integration-hero__identity" aria-hidden="true">
    <img class="integration-hero__logo integration-hero__logo--conductor" src="../img/logo.svg" alt="" />
    <span class="integration-hero__connector">→</span>
    <img class="integration-hero__logo" src="../assets/images/concepts/durable-checkpoint.svg" alt="" />
  </div>
  <p class="integration-hero__eyebrow">Your first durable result</p>
  <h2 id="get-started-hero-title">Pick a path. Verify a result. Build for production.</h2>
  <p>Build with the AI coding agent you already use, or author directly with an SDK. Every path ends with an inspectable execution and the same controls for retries, waits, recovery, and operations.</p>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="../devguide/how-tos/conductor-skills.html">
      <span class="integration-action-card__title">Build with your AI agent</span>
      <span>Install Conductor Skills and let Claude Code, Cursor, Copilot, or your agent of choice build and operate workflows for you.</span>
    </a>
    <a class="integration-action-card" href="first-worker.html">
      <span class="integration-action-card__title">Build with the SDK</span>
      <span>Author a workflow, worker, or agent in Python, Java, TypeScript/JavaScript, C#, or Rust and run it durably.</span>
    </a>
    <a class="integration-action-card" href="framework-agents.html">
      <span class="integration-action-card__title">Bring a framework agent</span>
      <span>Keep your OpenAI Agents, LangChain, LangGraph, or ADK agent; Conductor owns durable execution.</span>
    </a>
  </div>
</section>

## Choose your path

| If you want to… | Start here | First result |
| --- | --- | --- |
| Build with the AI coding agent you already use | [Build with your AI agent](../devguide/how-tos/conductor-skills.md) | Your agent creates, runs, and monitors a workflow from a prompt |
| Write a workflow and worker in your language | [Your first workflow & worker](first-worker.md) | `Hello Conductor` from your own code, durably executed |
| Write a new Conductor Agent | [Your first agent](first-agent.md) | A completed durable agent run |
| Keep an existing framework agent | [Bring your framework agent](framework-agents.md) | Your framework agent running through Conductor |
| Register and run a workflow with no code | [Run a workflow from JSON](first-workflow.md) | A completed two-step workflow in the UI |

## Before you begin

Complete [Connect to Conductor](connect.md) once. It covers the recommended Developer Edition connection, the CLI local-server path, and the credentials the quickstarts assume.

## After your first result

- Explore [Design Patterns](../devguide/cookbook/index.md) — every entry is a complete, runnable example.
- Building agents? Continue with the [production agent architecture](../devguide/ai/production-agent-architecture.md).
- Operating the platform? Use the [deployment guides](../devguide/running/deploy.md).
