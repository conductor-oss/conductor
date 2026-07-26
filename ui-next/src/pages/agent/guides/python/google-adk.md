## 1. Install and configure

```bash
python -m pip install 'conductor-python[adk]'
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=google_gemini/gemini-2.0-flash
```

## 2. Run the ADK agent

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

```bash
python adk_agent.py
```

[Google ADK examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/adk) · [Python agent documentation](https://github.com/conductor-oss/python-sdk/tree/main/docs/agents)
