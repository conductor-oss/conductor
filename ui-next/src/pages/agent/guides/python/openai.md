## 1. Install and configure

```bash
python -m pip install 'conductor-python[openai-agents]'
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Change one import

```python
from conductor.ai import Runner
from agents import Agent

agent = Agent(
    name="haiku_agent",
    model="gpt-4o-mini",
    instructions="Answer only in haiku.",
)

result = Runner.run_sync(agent, "Write about durable execution.")
print(result.final_output)
print(result.execution_id)
```

```bash
python openai_agent.py
```

`Runner.run`, `Runner.run_sync`, and `Runner.run_streamed` accept existing OpenAI Agents SDK agents.

[OpenAI examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/openai) · [Framework bridge documentation](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#openai-agents-sdk)
