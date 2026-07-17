## 1. Install and configure

```bash
npm install @io-orkes/conductor-javascript @google/adk
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=google_gemini/gemini-2.5-flash
```

## 2. Run the agent

```typescript
import { LlmAgent } from "@google/adk";
import { AgentRuntime } from "@io-orkes/conductor-javascript/agents";

const agent = new LlmAgent({
  name: "adk_greeter",
  model: "gemini-2.5-flash",
  instruction: "You are friendly and concise.",
});

const runtime = new AgentRuntime();
try {
  const result = await runtime.run(agent, "Share a machine learning fact.");
  result.printResult();
} finally {
  await runtime.shutdown();
}
```

```bash
npx tsx adk-agent.ts
```

[Google ADK examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/adk) · [Framework bridge documentation](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/agents/framework-agents.md#google-adk)
