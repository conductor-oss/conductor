## 1. Install

```bash
npm install @io-orkes/conductor-javascript
```

## 2. Configure

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 3. Save as `my-agent.ts` and run

```typescript
import { Agent, AgentRuntime } from "@io-orkes/conductor-javascript/agents";

const agent = new Agent({
  name: "greeter",
  model: "openai/gpt-4o-mini",
  instructions: "You are friendly and concise.",
});

const runtime = new AgentRuntime();
try {
  const result = await runtime.run(agent, "Share a fun TypeScript fact.");
  result.printResult();
} finally {
  await runtime.shutdown();
}
```

```bash
npx tsx my-agent.ts
```

Return to [Agents](/agents) to open the compiled definition.

[Complete TypeScript examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents) · [TypeScript agent documentation](https://github.com/conductor-oss/javascript-sdk/tree/main/docs/agents)
