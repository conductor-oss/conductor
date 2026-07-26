## 1. Install and configure

```bash
python -m pip install 'conductor-python[langchain]'
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Run the agent

```python
from conductor.ai.agents import AgentRuntime
from langchain.agents import create_agent

agent = create_agent(
    "openai:gpt-4o-mini",
    tools=[],
    system_prompt="You are a concise assistant.",
)

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Explain durable execution in one sentence.")
    result.print_result()
```

```bash
python langchain_agent.py
```

[LangChain bridge documentation](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#langchain) · [Python agent examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents)
