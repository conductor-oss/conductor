# Netflix Conductor Client C# SDK

`conductor-csharp` repository provides the client SDKs to build Task Workers and Clients in C#

The code for the C# SDk is available on [Github](https://github.com/conductor-sdk/conductor-csharp). Please feel free to file PRs, issues, etc. there.


## Quick Start
1. [Get Secrets](#Get-Secrets)
2. [Write workers](#Write-workers)
3. [Run workers](#Run-workers)
4. [Worker Configurations](#Worker-Configurations)

### Dependencies
`conductor-csharp` packages are published to nugget package manager.  You can find the latest releases [here](https://www.nuget.org/packages/conductor-csharp/).

### Write workers  

```
 internal class MyWorkflowTask : IWorkflowTask
    {
        public MyWorkflowTask(){}

        public string TaskType => "test_ctask";
        public int? Priority => null;

        public async Task<TaskResult> Execute(Conductor.Client.Models.Task task, CancellationToken token)
        {
           Dictionary<string, object> newOutput = new Dictionary<string, object>();
           newOutput.Add("output", "1");
           return task.Completed(task.OutputData.MergeValues(newOutput));
        }
    }

 internal class MyWorkflowTask2 : IWorkflowTask
    {
        public MyWorkflowTask2(){}

        public string TaskType => "test_ctask2";
        public int? Priority => null;

        public async Task<TaskResult> Execute(Conductor.Client.Models.Task task, CancellationToken token)
        {
           Dictionary<string, object> newOutput = new Dictionary<string, object>();
           //Reuse the existing code written in C#
           newOutput.Add("output", "success");
           return task.Completed(task.OutputData.MergeValues(newOutput));
        }
    }
```

### Run workers

```
using System;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Generic;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Conductor.Client.Models;
using Conductor.Client.Extensions;
using Conductor.Client.Interfaces;

using Task = System.Threading.Tasks.Task;
using Conductor.Client;
using System.Collections.Concurrent;

namespace TestOrkesSDK
{
    class Program
    {
        static void Main(string[] args)
        {
            new HostBuilder()
                 .ConfigureServices((ctx, services) =>
                 {
                    // First argument is optional headers which client wasnt to pass.
                     Configuration configuration = new Configuration(new ConcurrentDictionary<string, string>(), 
                         "KEY",
                         "SECRET");
                     services.AddConductorWorker(configuration);
                     services.AddConductorWorkflowTask<MyWorkflowTask>();
                     services.AddHostedService<WorkflowsWorkerService>();
                 })
                 .ConfigureLogging(logging =>
                 {
                     logging.SetMinimumLevel(LogLevel.Debug);
                     logging.AddConsole();
                 })
                 .RunConsoleAsync();
            Console.ReadLine();
        }
    }

    internal class MyWorkflowTask : IWorkflowTask
    {
        public MyWorkflowTask() { }

        public string TaskType => "my_ctask";
        public int? Priority => null;

        public async Task<TaskResult> Execute(Conductor.Client.Models.Task task, CancellationToken token)
        {
            Dictionary<string, object> newOutput = new Dictionary<string, object>();
            newOutput.Add("output", 1);
            return task.Completed(task.OutputData.MergeValues(newOutput));
        }
    }
}
```

####Note:
Replace KEY and SECRET by obtaining a new key and secret from [Orkes Playground](https://play.orkes.io/)

See [Generating Access Keys for Programmatic Access](https://orkes.io/content/docs/getting-started/concepts/access-control#access-keys) for details./

```
    internal class WorkflowsWorkerService : BackgroundService
    {
        private readonly IWorkflowTaskCoordinator workflowTaskCoordinator;
        private readonly IEnumerable<IWorkflowTask> workflowTasks;

        public WorkflowsWorkerService(
            IWorkflowTaskCoordinator workflowTaskCoordinator,
            IEnumerable<IWorkflowTask> workflowTasks
        )
        {
            this.workflowTaskCoordinator = workflowTaskCoordinator;
            this.workflowTasks = workflowTasks;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            foreach (var worker in workflowTasks)
            {
                workflowTaskCoordinator.RegisterWorker(worker);
            }
            // start all the workers so that it can poll for the tasks
            await workflowTaskCoordinator.Start();
        }
    }
```

### Worker Configurations
Worker configuration is handled via Configuration object passed when initializing TaskHandler.
```
Configuration configuration = 
    new Configuration(new ConcurrentDictionary<string, string>(), "KEY", "SECRET", "https://play.orkes.io/");
```

### Registering and starting the workflow using SDK.

Below is the code snippet that shows how to register a simple workflow and start execution:

```
IDictionary<string, string> optionalHeaders = new ConcurrentDictionary<string, string>();
Configuration configuration = new Configuration(optionalHeaders, "keyId", "keySecret");

//Create task definition
MetadataResourceApi metadataResourceApi = new MetadataResourceApi(configuration);
TaskDef taskDef = new TaskDef(name: "test_task");
taskDef.OwnerEmail = "test@test.com";
metadataResourceApi.RegisterTaskDef(new List<TaskDef>() { taskDef});

//Create workflow definition
WorkflowDef workflowDef = new WorkflowDef();
workflowDef.Name = "test_workflow";
workflowDef.OwnerEmail = "test@test.com";
workflowDef.SchemaVersion = 2;

WorkflowTask workflowTask = new WorkflowTask();
workflowTask.Type = "HTTP";
workflowTask.Name = "test_"; //Same as registered task definition.
IDictionary<string, string> requestParams = new Dictionary<string, string>();
requestParams.Add("uri", "https://www.google.com"); //adding a key/value using the Add() method
requestParams.Add("method", "GET");
Dictionary<string, object> request = new Dictionary<string, object>();
request.Add("http_request", requestParams);
workflowTask.InputParameters = request;
workflowDef.Tasks = new List<WorkflowTask>() { workflowTask };
//Run a workflow
WorkflowResourceApi workflowResourceApi = new WorkflowResourceApi(configuration);
Dictionary<string, Object> input = new Dictionary<string, Object>();
//Fill the input map which workflow consumes.
workflowResourceApi.StartWorkflow("test_workflow", input, 1);
Console.ReadLine();
```
Please see [Conductor.Api](https://github.com/conductor-sdk/conductor-csharp/tree/main/Api) for the APIs.