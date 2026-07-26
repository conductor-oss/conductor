## 1. Install and configure

```bash
npm install @io-orkes/conductor-javascript @langchain/core @langchain/openai
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 2. Hand your executor to Conductor

```typescript
import { AgentRuntime } from "@io-orkes/conductor-javascript/agents";

// Keep the AgentExecutor your application already creates.
const executor = existingLangChainAgentExecutor;

const runtime = new AgentRuntime();
try {
  const result = await runtime.run(executor, "Summarize the latest release.");
  result.printResult();
} finally {
  await runtime.shutdown();
}
```

If automatic introspection is ambiguous, use `createAgentExecutor` from `@io-orkes/conductor-javascript/agents/langchain` when constructing the executor.

[LangChain bridge documentation](https://github.com/conductor-oss/javascript-sdk/blob/main/docs/agents/framework-agents.md#langchain) · [TypeScript agent examples](https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agents)
