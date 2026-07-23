---
description: "Learn how Conductor AI workflows, SDK-authored Conductor Agents, and A2A agents differ and compose."
---

# Agents

Conductor supports three complementary ways to build with agents.

## Declarative AI workflows

Build an agent directly from Conductor tasks such as LLM, MCP, `SWITCH`, `DO_WHILE`, `WAIT`, and `HUMAN`. Choose this path when the orchestration itself is the product and you want the complete graph in the workflow definition.

Start with [Build Your First Agentic Workflow Graph](../ai/first-ai-agent.md).

## Conductor Agents

Author the agent in an SDK, or bring an existing framework-native object. The SDK compiles that logic into a durable Conductor graph. The resulting graph is inspectable and retryable, and can become a reusable `AGENT` step inside a larger workflow alongside ordinary tasks, branching, fan-out/join, approvals, schedules, and cancellation.

Start with [Run Your First Conductor Agent](../../quickstart/first-agent.md) or [Framework Agent Quickstarts](../../quickstart/framework-agents.md).

## A2A agents

Use A2A when the agent runs remotely behind the Agent2Agent protocol. Conductor calls that remote service rather than compiling an SDK-authored agent into a local graph.

In an `AGENT` task, `agentType` selects **Conductor Agents** or **A2A**. It does not select OpenAI Agents, LangChain, LangGraph, or Google ADK; those are SDK bridge choices for Conductor Agents.

See [A2A Integration](../ai/a2a-integration.md) for the remote-agent path and [Conductor Agents](../ai/conductor-agents.md) for the compiled-graph path.
