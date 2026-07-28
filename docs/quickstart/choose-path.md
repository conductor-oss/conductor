---
description: Choose the fastest path to your first durable Conductor workflow or agent.
---

# Choose your Conductor path

<section class="integration-hero integration-hero--durable" aria-labelledby="choose-path-hero-title">
  <div class="integration-hero__identity" aria-hidden="true">
    <img class="integration-hero__logo integration-hero__logo--conductor" src="../img/logo.svg" alt="" />
    <span class="integration-hero__connector">→</span>
    <img class="integration-hero__logo" src="../assets/images/concepts/durable-checkpoint.svg" alt="" />
  </div>
  <p class="integration-hero__eyebrow">Your first durable result</p>
  <h2 id="choose-path-hero-title">Pick a path. Verify a result. Build for production.</h2>
  <p>Choose the authoring model that matches your work today. Every path ends with an inspectable execution and the same controls for retries, waits, recovery, and operations.</p>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="index.html">
      <span class="integration-action-card__title">Build a workflow</span>
      <span>Coordinate APIs, services, timers, and workers with durable control flow.</span>
    </a>
    <a class="integration-action-card" href="first-agent.html">
      <span class="integration-action-card__title">Build a Conductor Agent</span>
      <span>Author in Python, Java, TypeScript/JavaScript, or C# and run it through Conductor's durable runtime.</span>
    </a>
    <a class="integration-action-card" href="framework-agents.html">
      <span class="integration-action-card__title">Bring a framework agent</span>
      <span>Use LangChain, ADK, or another agent framework while Conductor owns durable execution.</span>
    </a>
  </div>
</section>

## Choose the authoring model

| If you want to… | Start here | First result |
| --- | --- | --- |
| Coordinate services, APIs, timers, or workers | [Run your first workflow](first-workflow.md) | A completed two-step workflow in the UI |
| Write a new Conductor Agent | [Run your first Conductor Agent](first-agent.md) | A completed durable agent run |
| Keep an existing OpenAI Agents, LangChain, LangGraph, or Claude Agent SDK agent | [Framework agent quickstarts](framework-agents.md) | Your framework agent running through Conductor |

## Before you begin

Complete [Connect to Conductor](connect.md) once. It covers the recommended Developer Edition connection, the CLI local-server path, and the credentials required for workflow and agent examples.

## What you will verify

Every path ends with a concrete result you can inspect:

- a workflow or agent execution has a terminal status;
- its inputs, outputs, and task timeline are visible in the Conductor UI; and
- you have a production next step for versioning, reliability, and operations.

## After your first result

- Workflow builders: continue with [best practices](../devguide/bestpractices.md).
- Agent builders: continue with the [production agent architecture](../devguide/ai/production-agent-architecture.md).
- Platform operators: use the [deployment guides](../devguide/running/deploy.md) to deploy, observe, and recover both kinds of execution.
