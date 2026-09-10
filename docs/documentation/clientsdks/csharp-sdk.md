---
description: "Build Conductor workers and clients in C#/.NET with the official generated SDK."
source_repo: "https://github.com/conductor-oss/csharp-sdk"
sdk_page: csharp
---

# C# SDK

## Install the SDK

```shell
dotnet add package conductor-csharp --version VERSION
```

## Configure a workflow client

The SDK quickstart configures the endpoint from the environment and uses the workflow executor API:

```csharp
using Conductor.Client;
using Conductor.Definition;
using Conductor.Definition.TaskType;
using Conductor.Executor;

var configuration = new Configuration {
    BasePath = Environment.GetEnvironmentVariable("CONDUCTOR_SERVER_URL")
        ?? "http://localhost:8080/api"
};

var workflow = new ConductorWorkflow()
    .WithName("greetings")
    .WithVersion(1);

var greetTask = new SimpleTask("greet", "greet_ref")
    .WithInput("name", workflow.Input("name"));
workflow.WithTask(greetTask);

var executor = new WorkflowExecutor(configuration);
executor.RegisterWorkflow(workflow, overwrite: true);
var workflowId = executor.StartWorkflow(new StartWorkflowRequest {
    Name = "greetings",
    Version = 1,
    Input = new Dictionary<string, object> { ["name"] = "Conductor" }
});
```

For Orkes authentication, the SDK exposes `Configuration.AuthenticationSettings`; create an `OrkesAuthenticationSettings` from `CONDUCTOR_AUTH_KEY` and `CONDUCTOR_AUTH_SECRET` before constructing clients. It does not do that environment mapping automatically. See the [upstream SDK README](https://github.com/conductor-oss/csharp-sdk#configurations) for its authentication and worker examples.
