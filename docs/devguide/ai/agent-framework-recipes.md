---
description: "Choose the supported Conductor bridge for an existing framework agent, then deploy it as a durable, reusable Conductor Agent."
---

# Framework Agent Bridges

<section class="framework-hero" aria-label="Framework bridges">
  <p>A <strong>bridge</strong> is the SDK adapter that lets Conductor run an agent authored in another framework, such as OpenAI Agents, LangChain, LangGraph, or Google ADK. You keep the agent object your framework defines, and the bridge compiles and runs it as a durable Conductor execution. This page is the reference for the bridges: which frameworks and languages are supported, how a bridged agent becomes a deployable Conductor Agent, and where the maintained examples live for each pairing.</p>
  <div class="framework-logo-grid">
    <a class="framework-logo-card" href="../../quickstart/framework-agents.html#openai-agents-sdk" aria-label="OpenAI Agents quickstart">
      <img class="framework-logo framework-logo--wide" src="../../assets/images/frameworks/openai.svg" alt="" />
      <span>OpenAI Agents</span>
    </a>
    <a class="framework-logo-card" href="../../quickstart/framework-agents.html#google-adk" aria-label="Google ADK quickstart">
      <img class="framework-logo" src="../../assets/images/frameworks/google-adk.svg" alt="" />
      <span>Google ADK</span>
    </a>
    <a class="framework-logo-card" href="../../quickstart/framework-agents.html#langchain" aria-label="LangChain quickstart">
      <img class="framework-logo" src="../../assets/images/frameworks/langchain.svg" alt="" />
      <span>LangChain</span>
    </a>
    <a class="framework-logo-card" href="../../quickstart/framework-agents.html#langgraph" aria-label="LangGraph quickstart">
      <img class="framework-logo" src="../../assets/images/frameworks/langgraph.svg" alt="" />
      <span>LangGraph</span>
    </a>
    <a class="framework-logo-card" href="https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/vercel-ai" aria-label="Vercel AI SDK examples on GitHub">
      <img class="framework-logo" src="../../assets/images/frameworks/vercel.svg" alt="" />
      <span>Vercel AI SDK</span>
    </a>
    <a class="framework-logo-card" href="../../quickstart/first-agent.html" aria-label="Conductor Agents quickstart">
      <img class="framework-logo framework-logo--wide" src="../../img/logo.svg" alt="" />
      <span>Conductor Agents</span>
    </a>
  </div>
</section>

## Choose your bridge

| Framework | Start here |
|---|---|
| OpenAI Agents | [Framework quickstart](../../quickstart/framework-agents.md#openai-agents-sdk) |
| Google ADK | [Google ADK quickstart](../../quickstart/framework-agents.md#google-adk) |
| LangChain / LangChain4j | [LangChain quickstart](../../quickstart/framework-agents.md#langchain) |
| LangGraph / LangGraph4j | [LangGraph quickstart](../../quickstart/framework-agents.md#langgraph) |
| Vercel AI SDK | [Maintained SDK examples](../../quickstart/framework-agents.md#sdk-examples) |
| Conductor Agents | [Run your first Conductor Agent](../../quickstart/first-agent.md) |

Each route keeps the framework-specific code, dependencies, and executable examples in the owning Conductor SDK. The bridge is the boundary: your framework remains the authoring surface, while Conductor provides durable execution around it.

## From framework object to workflow step

Every bridge follows the same production path:

1. **Author and run** the framework agent through its matching Conductor SDK bridge while iterating.
2. **Plan and deploy** the generated graph when the agent is ready to become a reusable production capability.
3. **Serve** any bridge workers required by that SDK route.
4. **Invoke** the deployed agent by name from a parent workflow with `AGENT` and `agentType: "conductor"`.

Use `run` for an interactive session. Use deploy plus serve when a durable business workflow must call a stable agent version. [Conductor Agents](conductor-agents.md) defines the deployed-agent contract, including invocation, waiting, resume, cancellation, and outputs.

## Why run a framework agent on Conductor

The framework keeps deciding *what* the agent does. Conductor changes *how it runs* — and you get this without rewriting the agent or adding infrastructure code to it.

**Durability with no code change.** The agent compiles into an ordinary Conductor workflow. Every LLM call, tool invocation, and handoff becomes a task with its own state. A process crash, deploy, or restart mid-run resumes from the last completed task instead of starting the conversation again. You write no checkpointing, no retry loop, no state store.

**Credentials stay out of the agent.** Declare `credentials=[...]` on a tool and the server injects the secret for the duration of that call. Nothing lands in the prompt, the agent definition, or your source tree — and the tool process is the only thing that ever sees it.

**Observability you did not instrument.** Because each step is a task, the UI shows the tool arguments, the returned payload, the guardrail verdict, the retry count, and the exact point of failure. No tracing library, no span plumbing.

**Bounded execution.** Timeouts, retry policy, rate limits, and concurrency limits are task-level settings that apply to a framework agent the same way they apply to any other workload.

**Composition with the rest of your system.** A framework agent is an `AGENT` task, so it sits beside HTTP calls, workers, `SWITCH` branches, human approvals, schedules, and compensation logic in one graph.

**Reuse by name.** Deploy once and any workflow, schedule, or service can invoke it — without importing your framework or its dependencies.

## Maintained SDK examples

Complete, runnable projects for each bridge. A dash marks a pairing with no maintained example.

| Framework | Python | Java | TypeScript / JavaScript | C# |
|---|---|---|---|---|
| OpenAI Agents | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/openai) | [Examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/openai) | [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples) |
| Google ADK | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/adk) | [Examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/adk) | [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples) |
| LangChain | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents) | [LangChain4j examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents) | — |
| LangGraph | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/langgraph) | [LangGraph4j examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/langgraph) | — |
| Vercel AI SDK | — | — | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/vercel-ai) | — |

## Next steps

- [Run a framework quickstart](../../quickstart/framework-agents.md) to execute an existing agent through Conductor.
- [Build an agentic workflow graph](first-ai-agent.md) to compose a deployed agent with direct Conductor tasks.
- [Apply guardrails](agent-guardrails.md) and [evaluate recorded behavior](agent-evals.md) before promotion.
- [Use A2A Integration](a2a-integration.md) when the agent is independently deployed and remains remote.
