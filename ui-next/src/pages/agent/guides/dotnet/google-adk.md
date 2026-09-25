```bash
dotnet add package conductor-ai-google-adk
```

```csharp
using Conductor.AI;
using Conductor.AI.GoogleADK;

var agent = GoogleADKAgent.Builder()
    .Name("adk_greeter")
    .Model("gemini-2.0-flash")
    .Instruction("You are friendly and concise.")
    .Build();

await using var runtime = new AgentRuntime();
var result = await runtime.RunAsync(agent, "Share a machine learning fact.");
result.PrintResult();
```

Configure the server before running the agent:

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
```

[Google ADK adapter documentation](https://github.com/conductor-oss/csharp-sdk/blob/main/docs/agents/framework-agents.md#google-adk) · [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples)
