[Python SDK on GitHub](https://github.com/conductor-oss/python-sdk)

## 1. Install

```bash
python -m pip install 'conductor-python[agents]'
```

## 2. Configure

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 3. Save as `hello.py` and run

```python
from conductor.ai.agents import Agent, AgentRuntime

agent = Agent(
    name="greeter",
    model="openai/gpt-4o-mini",
    instructions="You are a friendly assistant. Keep responses brief.",
)

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Say hello and share a fun Python fact.")
    result.print_result()
```

```bash
python hello.py
```

The runtime compiles and runs the agent as a durable workflow. Return to [Agents](/agents) to open its definition.

[Complete Python agent examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents) · [Python agent documentation](https://github.com/conductor-oss/python-sdk/tree/main/docs/agents)
