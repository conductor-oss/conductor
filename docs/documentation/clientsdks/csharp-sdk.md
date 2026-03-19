---
description: "Build Conductor workers in C#/.NET with dependency injection, workflow management, and task polling."
---

# C# SDK

!!! info "Source"
    GitHub: [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk) | Report issues and contribute on GitHub.

## ⭐ Conductor OSS
Show support for the Conductor OSS.  Please help spread the awareness by starring Conductor repo.

[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor.svg?style=social&label=Star&maxAge=)](https://GitHub.com/conductor-oss/conductor/)

   
### Setup Conductor C# Package​

```shell
dotnet add package conductor-csharp
```

## Configurations

### Authentication Settings (Optional)
Configure the authentication settings if your Conductor server requires authentication.
* keyId: Key for authentication.
* keySecret: Secret for the key.

```csharp
authenticationSettings: new OrkesAuthenticationSettings(
    KeyId: "key",
    KeySecret: "secret"
)
```

### Access Control Setup
See [Access Control](https://orkes.io/content/docs/getting-started/concepts/access-control) for more details on role-based access control with Conductor and generating API keys for your environment.

### Configure API Client
```csharp
using Conductor.Api;
using Conductor.Client;
using Conductor.Client.Authentication;

var configuration = new Configuration() {
    BasePath = basePath,
    AuthenticationSettings = new OrkesAuthenticationSettings("keyId", "keySecret")
};

var workflowClient = configuration.GetClient<WorkflowResourceApi>();

workflowClient.StartWorkflow(
    name: "test-sdk-csharp-workflow",
    body: new Dictionary<string, object>(),
    version: 1
)
```

### Next: [Create and run task workers](https://github.com/conductor-sdk/conductor-csharp/blob/main/docs/readme/workers.md)


## Examples

Browse all examples on GitHub: [conductor-oss/csharp-sdk/csharp-examples](https://github.com/conductor-oss/csharp-sdk/tree/main/csharp-examples)

| Example | Type |
|---|---|
| [Examples](https://github.com/conductor-oss/csharp-sdk/tree/main/csharp-examples/Examples) | directory |
| [Humantaskexamples](https://github.com/conductor-oss/csharp-sdk/blob/main/csharp-examples/HumanTaskExamples.cs) | file |
| [Program](https://github.com/conductor-oss/csharp-sdk/blob/main/csharp-examples/Program.cs) | file |
| [Runner](https://github.com/conductor-oss/csharp-sdk/blob/main/csharp-examples/Runner.cs) | file |
| [Testworker](https://github.com/conductor-oss/csharp-sdk/blob/main/csharp-examples/TestWorker.cs) | file |
| [Utils](https://github.com/conductor-oss/csharp-sdk/tree/main/csharp-examples/Utils) | directory |
| [Workflowexamples](https://github.com/conductor-oss/csharp-sdk/blob/main/csharp-examples/WorkFlowExamples.cs) | file |
