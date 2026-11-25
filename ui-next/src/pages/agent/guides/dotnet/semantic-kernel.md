```bash
dotnet add package conductor-ai-semantic-kernel
```

Keep classes with `[KernelFunction]` methods and pass them to `SemanticKernelAgent.From`.

```csharp
using System.ComponentModel;
using Conductor.AI;
using Conductor.AI.SemanticKernel;
using Microsoft.SemanticKernel;

sealed class CalculatorPlugin
{
    [KernelFunction, Description("Add two integers")]
    public int Add(int a, int b) => a + b;
}

var agent = SemanticKernelAgent.From(
    name: "sk_calculator",
    model: "openai/gpt-4o-mini",
    instructions: "Use the calculator plugin.",
    new CalculatorPlugin());

await using var runtime = new AgentRuntime();
var result = await runtime.RunAsync(agent, "What is 17 plus 25?");
result.PrintResult();
```

Configure the server before running the agent:

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
```

[Semantic Kernel adapter documentation](https://github.com/conductor-oss/csharp-sdk/blob/main/docs/agents/framework-agents.md#semantic-kernel) · [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/Conductor.AI.Examples)
