---
description: "Use Python to run existing OpenAI Agents, LangChain, LangGraph, or Google ADK agents as durable Conductor Agents."
---

# Framework Agent Quickstarts

**Bring your agent. Make it durable.** Keep the framework object and authoring API you already use; the Conductor Python SDK compiles it into an inspectable, retryable Conductor graph.

For a new agent that does not need another framework, start with [Run Your First Conductor Agent](first-agent.md).

## Before you start

Start a local server with the [workflow quickstart](index.md#start-conductor), then configure the Python SDK:

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
```

Use `run` while you are iterating interactively. In production, use `deploy` to register the compiled graph and `serve` to run its workers. The SDK repositories are the source of truth for framework packages and runnable code.

## OpenAI Agents

Use the Python bridge as a near drop-in replacement for the OpenAI Agents runner:

```python
from conductor.ai import Runner
from agents import Agent

agent = Agent(name="assistant", instructions="Reply concisely.")
result = Runner.run_sync(agent, "Explain durable execution in one sentence.")
print(result.final_output)
```

Follow the maintained [Python bridge guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#openai-agents-sdk) and [runnable OpenAI Agents examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/openai).

## LangChain

Create the agent with LangChain, then pass the object to `AgentRuntime.run(...)`. The maintained [LangChain bridge snippet](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#langchain) is the supported starting point; it stays aligned with the current LangChain API.

## LangGraph

Compile your LangGraph state graph, then pass that graph to `AgentRuntime.run(...)`. Start from the maintained [LangGraph bridge guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#langgraph) and [runnable Python examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/langgraph).

## Google ADK

Pass a standard Google ADK `Agent` to `AgentRuntime.run(...)`. Use the maintained [Google ADK bridge guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#google-adk) and [runnable Python examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/adk).

## Choose the production path

Every bridge follows the same lifecycle:

```text
framework-native agent → plan → deploy → serve → invoke from a workflow
```

See [Framework Agent Recipes](../devguide/ai/agent-framework-recipes.md) for the broader support matrix, including Java and TypeScript paths, and [Conductor Agents](../devguide/ai/conductor-agents.md) for using a deployed agent as an `AGENT` task.
