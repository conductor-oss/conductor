## 1. Install and configure

```bash
npm install @io-orkes/conductor-javascript @langchain/langgraph @langchain/openai @langchain/core
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Run the graph

```typescript
import { createReactAgent } from "@langchain/langgraph/prebuilt";
import { ChatOpenAI } from "@langchain/openai";
import { AgentRuntime } from "@io-orkes/conductor-javascript/agents";

const graph = createReactAgent({
  llm: new ChatOpenAI({ model: "gpt-4o-mini", temperature: 0 }),
  tools: [],
  name: "langgraph_assistant",
});

const runtime = new AgentRuntime();
try {
  const result = await runtime.run(graph, "What makes execution durable?");
  result.printResult();
} finally {
  await runtime.shutdown();
}
```

```bash
npx tsx langgraph-agent.ts
```

[LangGraph examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/langgraph) · [Framework bridge documentation](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/agents/framework-agents.md#langgraph)
