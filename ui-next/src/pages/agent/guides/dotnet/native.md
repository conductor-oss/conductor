## 1. Add the SDK

```bash
dotnet add package conductor-ai --version 3.0.0-rc3
```

## 2. Configure

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export CONDUCTOR_AGENT_LLM_MODEL=openai/gpt-4o-mini
```

## 3. Run an agent

```csharp
using Conductor.AI;

var agent = new Agent("greeter")
{
    Model = "openai/gpt-4o-mini",
    Instructions = "You are friendly and concise.",
};

await using var runtime = new AgentRuntime();
var result = await runtime.RunAsync(agent, "Share a fun C# fact.");
result.PrintResult();
```

[Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples) · [Documentation](https://github.com/conductor-oss/csharp-sdk/tree/main/docs/agents)
