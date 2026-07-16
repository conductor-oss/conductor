## 1. Install and configure

```bash
npm install @io-orkes/conductor-javascript @openai/agents
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Run the agent

```typescript
import { Agent, setTracingDisabled } from "@openai/agents";
import { AgentRuntime } from "@io-orkes/conductor-javascript/agents";

setTracingDisabled(true);
const agent = new Agent({
  name: "openai_greeter",
  model: "gpt-4o-mini",
  instructions: "You are friendly and concise.",
});

const runtime = new AgentRuntime();
try {
  const result = await runtime.run(agent, "Share a durable execution fact.");
  result.printResult();
} finally {
  await runtime.shutdown();
}
```

```bash
npx tsx openai-agent.ts
```

[OpenAI examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/openai) · [Framework bridge documentation](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/agents/framework-agents.md#openai-agents-sdk)
