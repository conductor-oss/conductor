```bash
dotnet add package conductor-ai-openai
```

The adapter mirrors the OpenAI Agents authoring shape and routes the result through Conductor’s durable runtime.

```csharp
using Conductor.AI;
using Conductor.AI.OpenAI;

var agent = OpenAIAgent.Builder()
    .Name("openai_style_greeter")
    .Instructions("You are friendly and concise.")
    .Model("openai/gpt-4o-mini")
    .Build();

await using var runtime = new AgentRuntime();
var result = await runtime.RunAsync(agent, "Share a durable execution fact.");
result.PrintResult();
```

Configure the server before running the agent:

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
```

[OpenAI adapter documentation](https://github.com/conductor-oss/csharp-sdk/blob/main/docs/agents/framework-agents.md#openai-agents) · [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples)
