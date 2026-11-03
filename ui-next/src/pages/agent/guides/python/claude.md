## 1. Install and configure

```bash
python -m pip install 'conductor-python[claude]'
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
```

## 2. Run the agent

```python
from conductor.ai.agents import Agent, AgentRuntime, ClaudeCode

agent = Agent(
    name="claude_code_assistant",
    model=ClaudeCode("sonnet"),
    instructions="Inspect the repository and answer concisely.",
    tools=["Read", "Glob", "Grep"],
)

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Summarize this repository.")
    result.print_result()
```

```bash
python claude_agent.py
```

[Claude Agent SDK examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/claude_agent_sdk) · [Framework bridge documentation](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#claude-agent-sdk)
