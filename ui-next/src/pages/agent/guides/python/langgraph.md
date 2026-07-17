## 1. Install and configure

```bash
python -m pip install 'conductor-python[langgraph]' langchain-openai
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Run the graph

```python
from conductor.ai.agents import AgentRuntime
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent

graph = create_react_agent(
    ChatOpenAI(model="gpt-4o-mini", temperature=0),
    tools=[],
    name="langgraph_assistant",
)

with AgentRuntime() as runtime:
    result = runtime.run(graph, "What makes a workflow durable?")
    result.print_result()
```

```bash
python langgraph_agent.py
```

[LangGraph examples](https://github.com/conductor-oss/python-sdk/tree/main/examples/agents/langgraph) · [Framework bridge documentation](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/framework-agents.md#langgraph)
