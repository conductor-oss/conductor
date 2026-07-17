## 1. Install and configure

```bash
npm install @io-orkes/conductor-javascript ai zod
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Run with the Vercel tool

```typescript
import { tool } from "ai";
import { z } from "zod";
import { Agent, AgentRuntime } from "@io-orkes/conductor-javascript/agents";

const weather = tool({
  description: "Get weather for a city",
  parameters: z.object({ city: z.string() }),
  execute: async ({ city }) => ({ city, condition: "Sunny" }),
});

const agent = new Agent({
  name: "vercel_weather",
  model: "openai/gpt-4o-mini",
  instructions: "Use the weather tool.",
  tools: [weather],
});

const runtime = new AgentRuntime();
try {
  const result = await runtime.run(agent, "Weather in San Francisco?");
  result.printResult();
} finally {
  await runtime.shutdown();
}
```

[Vercel AI examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents/vercel-ai) · [Framework bridge documentation](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/agents/framework-agents.md#vercel-ai-sdk)
