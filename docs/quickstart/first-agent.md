---
description: "Run your first Conductor Agent with Python. Define an SDK agent, run it interactively, and see it compile into a durable Conductor graph."
---

# Run Your First Conductor Agent

Use this path when you want to author an agent in Python and have Conductor make its execution durable. Your SDK-created agent compiles into a Conductor graph, so its steps are inspectable, retryable, and ready to compose with workflows later.

For an existing OpenAI Agents, LangChain, LangGraph, or Google ADK object, use [Framework Agent Quickstarts](framework-agents.md) instead.

## 1. Start Conductor

Start a local Conductor server using the [workflow quickstart](index.md#start-conductor). The Python SDK connects to `http://localhost:8080/api` by default.

## 2. Install and configure the Python SDK

```bash
pip install 'conductor-python[agents]'
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export OPENAI_API_KEY=<your-openai-api-key>
```

`CONDUCTOR_SERVER_URL` points the SDK at your Conductor server. Configure the model provider credential on the Conductor server for production deployments, as described in the maintained SDK setup guides.

## 3. Define and run an agent

Save this as `hello_agent.py`:

```python
from conductor.ai.agents import Agent, AgentRuntime

agent = Agent(
    name="greeter",
    model="openai/gpt-4o-mini",
    instructions="You are a friendly assistant. Keep responses brief.",
)

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Say hello and tell me a fun fact about Python.")
    print(result.output)
```

Run it:

```bash
python hello_agent.py
```

`runtime.run()` creates the graph, starts the agent, and waits for its result. It is the right lifecycle for interactive development.

## What Conductor adds

The agent logic stays in Python, while Conductor owns the durable graph around it. That graph can use normal workflow capabilities such as retries, waits, human approval, fan-out/join, schedules, and cancellation.

## Next: deploy a reusable agent

For production, compile and register the agent with `deploy`, then keep its workers available with `serve`. The full, maintained lifecycle and runnable examples live in the [Python SDK agent getting-started guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/getting-started.md) and [deployment examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents).

To use the deployed graph as a step in a larger workflow, continue with [Conductor Agents](../devguide/ai/conductor-agents.md).
