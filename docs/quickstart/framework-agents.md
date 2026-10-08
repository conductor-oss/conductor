---
description: Run an existing OpenAI Agents, Google ADK, LangChain, or LangGraph agent through Conductor's durable runtime.
---

# Bring Your Framework Agent

**Outcome:** your framework agent runs through Conductor and produces an inspectable execution.

This page is for agents you have already built in another framework, such as OpenAI Agents, LangChain, LangGraph, or Google ADK. You keep the agent object your framework already defines, and the Conductor SDK compiles and runs it as a durable, inspectable Conductor execution. If you are starting from scratch instead, build a native agent with [Your First Agent](first-agent.md).

<section class="framework-hero" aria-labelledby="framework-quickstarts-title">
  <h2 id="framework-quickstarts-title">Bring your existing agent.</h2>
  <div class="framework-logo-grid framework-logo-grid--quickstart">
    <a class="framework-logo-card" href="#openai-agents-sdk" aria-label="OpenAI Agents SDK quickstart">
      <img class="framework-logo framework-logo--wide" src="../assets/images/frameworks/openai.svg" alt="" />
      <span>OpenAI Agents</span>
    </a>
    <a class="framework-logo-card" href="#langchain" aria-label="LangChain quickstart">
      <img class="framework-logo" src="../assets/images/frameworks/langchain.svg" alt="" />
      <span>LangChain</span>
    </a>
    <a class="framework-logo-card" href="#google-adk" aria-label="Google ADK quickstart">
      <img class="framework-logo" src="../assets/images/frameworks/google-adk.svg" alt="" />
      <span>Google ADK</span>
    </a>
    <a class="framework-logo-card" href="https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/vercel-ai" aria-label="Vercel AI SDK examples on GitHub">
      <img class="framework-logo" src="../assets/images/frameworks/vercel.svg" alt="" />
      <span>Vercel AI SDK</span>
    </a>
  </div>
</section>

## Prerequisites

First, complete [Connect to Conductor](connect.md) so the runtime can reach your server. Then make sure the server can call your model provider. On Developer Edition, add the provider as an [AI/LLM integration](https://orkes.io/content/category/integrations/ai-llm); on a local server, [export the provider API key](../devguide/ai/llm-orchestration.md#supported-llm-providers) before starting it. Each framework section below begins with its install command. Most examples use an OpenAI model, and the Google ADK example uses Gemini, so supply the matching credentials.

## OpenAI Agents SDK

Install the Conductor SDK with OpenAI Agents support:

```bash
pip install conductor-python
```

Save as `openai_agent.py`:

```python
from conductor.ai import Runner
from agents import Agent, function_tool

@function_tool
def get_weather(city: str) -> str:
    return f"72F and sunny in {city}"

agent = Agent(
    name="weather_assistant",
    model="gpt-4o-mini",
    tools=[get_weather],
    instructions="You are a helpful assistant.",
)

result = Runner.run_sync(agent, "What's the weather in NYC?")
print(result.final_output)
```

Run `python openai_agent.py`, then verify the output and execution in the UI. The only runner import changes: use `conductor.ai.Runner` rather than the framework runner.

## LangChain

Install the Conductor SDK with LangChain support:

```bash
pip install 'conductor-python[langchain]'
```

```python
from conductor.ai.agents import AgentRuntime
from langchain.agents import create_agent
from langchain_core.tools import tool

@tool
def check_token() -> str:
    """Check a token."""
    return "available"

agent = create_agent("openai:gpt-4o-mini", tools=[check_token],
                     system_prompt="You are a helpful assistant.")

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Is the token set?")
    result.print_result()
```

## LangGraph

Install the Conductor SDK with LangGraph support:

```bash
pip install 'conductor-python[langgraph]'
```

```python
import math
from conductor.ai.agents import AgentRuntime
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent

@tool
def calculate(expression: str) -> str:
    """Evaluate a limited math expression."""
    return str(eval(expression, {"__builtins__": {}}, {"sqrt": math.sqrt, "pi": math.pi}))

graph = create_react_agent(
    ChatOpenAI(model="gpt-4o-mini", temperature=0), tools=[calculate], name="math_agent"
)

with AgentRuntime() as runtime:
    result = runtime.run(graph, "What is sqrt(256) + 2**10?")
    result.print_result()
```

## Google ADK

Install the Conductor SDK with Google ADK support:

```bash
python -m pip install 'conductor-python[adk]'
```

```python
from conductor.ai.agents import AgentRuntime
from google.adk.agents import Agent

agent = Agent(
    name="adk_greeter",
    model="gemini-2.0-flash",
    instruction="You are friendly and concise.",
)

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Say hello and share an ML fact.")
    result.print_result()
```

Save the file as `adk_agent.py` and run `python adk_agent.py`.

## Verify and recover

For every framework, verify the printed result and find the corresponding execution in the Conductor UI. If it fails, first check the runtime server URL, framework package, and provider credentials; then inspect the failed task before retrying. Do not retry an agent action that may have performed an external side effect until its idempotency and recovery policy are clear.

## Next production step

**Next:** every entry in [Design Patterns → Agent Recipes](../devguide/ai/cookbook/index.md) is a complete, runnable example — handoffs, memory, guardrails, parallel agents, and more.

Use the [production agent architecture](../devguide/ai/production-agent-architecture.md) to add governance, evaluations, deployment, composition, and operations. The [Python SDK framework-agent guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md) remains the source for the current framework-agent API and support matrix.

## SDK examples

Use the maintained SDK examples for complete, runnable projects. A dash marks a pairing with no maintained example.

| Framework | Python | Java | TypeScript / JavaScript | C# |
|---|---|---|---|---|
| OpenAI Agents | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/openai) | [Examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/openai) | [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples) |
| Google ADK | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/adk) | [Examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/adk) | [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples) |
| LangChain | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents) | [LangChain4j examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents) | — |
| LangGraph | [Examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/langgraph) | [LangGraph4j examples](https://github.com/conductor-oss/java-sdk/tree/main/agent-examples) | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/langgraph) | — |
| Vercel AI SDK | — | — | [Examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/vercel-ai) | — |
