---
description: Write, run, and verify your first Conductor workflow and worker in Python, Java, TypeScript/JavaScript, C#, or Rust.
---

# Your First Workflow & Worker

**Outcome:** a `greetings` workflow that queues a `greet` task and returns `Hello Conductor` from a worker.

**Time:** about 5 minutes.

Complete [Connect to Conductor](connect.md) first. This guide uses the SDK connection variables configured there: `CONDUCTOR_SERVER_URL`, plus `CONDUCTOR_AUTH_KEY` and `CONDUCTOR_AUTH_SECRET` when your server requires them.

## How a worker runs

In this quickstart you build two things: a **workflow** named `greetings` — the durable definition that Conductor executes — and a **worker** — a function in your code that performs one task inside it.

The workflow has a single task of type `SIMPLE`, which means the work is done by your code rather than by one of Conductor's built-in tasks. Every `SIMPLE` task has a task type — here, `greet`. When a running workflow reaches that task, Conductor places it on a queue for that task type. Your worker polls the `greet` queue, runs your business logic, and reports back `COMPLETED` or `FAILED`. Conductor durably persists the result, then advances the workflow to its next task.

Two rules follow from this design:

- The task type must match exactly between the workflow definition and the worker — otherwise the task sits on a queue that nothing polls.
- Workers run as ordinary processes in your own infrastructure and deploy and scale independently of the Conductor server. Conductor guarantees at-least-once delivery, meaning the same task can be delivered again after a failure or timeout — so write workers to be idempotent, where running the same task twice produces the same result.

<svg viewBox="0 0 760 290" role="img" aria-label="Diagram: Conductor queues the greet task by task type; your worker polls the queue, runs business logic, and reports the result back; Conductor persists it and advances the workflow" style="max-width:760px;width:100%;height:auto;display:block;margin:18px auto;font-family:inherit;">
  <g fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <!-- Conductor server box (hand-drawn) -->
    <path d="M24 42 q110 -5 218 2 q6 1 5 9 l-2 160 q0 8 -8 8 q-104 5 -206 -2 q-8 -1 -8 -9 l-1 -159 q0 -8 2 -9 z"/>
    <!-- workflow pill -->
    <path d="M46 84 q80 -3 158 1 q5 0 5 6 l-1 22 q0 6 -6 6 q-76 3 -152 -1 q-6 0 -6 -6 l0 -22 q0 -6 2 -6 z"/>
    <!-- task pill -->
    <path d="M46 138 q80 -4 158 1 q5 0 5 6 l-1 22 q0 6 -6 6 q-76 3 -152 -1 q-6 0 -6 -6 l1 -22 q0 -6 1 -6 z"/>
    <!-- queue: three stacked slots -->
    <path d="M318 96 q60 -3 118 1 q5 1 5 6 l-1 18 q0 5 -6 5 q-56 2 -112 -1 q-5 0 -5 -5 l0 -19 q0 -5 1 -5 z"/>
    <path d="M322 130 q58 -3 112 1 q5 1 5 6 l-1 18 q0 5 -6 5 q-54 2 -106 -1 q-5 0 -5 -5 l0 -19 q0 -5 1 -5 z"/>
    <path d="M326 164 q54 -3 104 1 q5 1 5 6 l-1 18 q0 5 -6 5 q-50 2 -98 -1 q-5 0 -5 -5 l0 -19 q0 -5 1 -5 z"/>
    <!-- worker box -->
    <path d="M520 60 q106 -5 212 2 q6 1 5 9 l-2 130 q0 8 -8 8 q-100 5 -200 -2 q-8 -1 -8 -9 l0 -129 q0 -8 1 -9 z"/>
  </g>
  <g fill="none" stroke="#1976d2" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <!-- server -> queue arrow -->
    <path d="M252 118 q30 -6 60 -1"/>
    <path d="M304 112 l9 5 -10 4"/>
    <!-- worker -> queue arrow (polls) -->
    <path d="M516 128 q-30 6 -62 2"/>
    <path d="M463 125 l-10 5 11 4"/>
    <!-- worker -> server return arrow (result) -->
    <path d="M560 212 q-180 52 -320 8"/>
    <path d="M250 226 l-11 -7 13 -3"/>
  </g>
  <g fill="currentColor" font-size="15">
    <text x="44" y="70" font-weight="700">Conductor server</text>
    <text x="58" y="103">greetings workflow</text>
    <text x="60" y="157">greet task (SIMPLE)</text>
    <text x="36" y="196" font-size="12.5" opacity="0.75">persists result,</text>
    <text x="36" y="211" font-size="12.5" opacity="0.75">advances workflow</text>
    <text x="344" y="86" font-weight="700">greet queue</text>
    <text x="540" y="88" font-weight="700">Your worker</text>
    <text x="540" y="118">greet(name)</text>
    <text x="540" y="140" font-size="12.5" opacity="0.75">your business logic</text>
  </g>
  <g fill="#1976d2" font-size="12.5">
    <text x="238" y="100">queues by</text>
    <text x="240" y="114">task type</text>
    <text x="466" y="112">polls</text>
    <text x="330" y="252">reports COMPLETED / FAILED</text>
  </g>
</svg>

## Language-specific quickstart

Choose a language to reveal one complete `greet` worker and the matching `greetings` workflow. The examples are adapted from the maintained SDK hello-world worker examples.

<div class="worker-language-picker" markdown="1">
  <label for="worker-language-select">Language</label>
  <select id="worker-language-select" aria-describedby="worker-language-help">
    <option value="python" selected>Python</option>
    <option value="java">Java</option>
    <option value="typescript">TypeScript / JavaScript</option>
    <option value="csharp">C#</option>
    <option value="rust">Rust</option>
  </select>
  <p id="worker-language-help">Choose a language to reveal its install, worker, workflow, and run steps.</p>

  <section class="worker-language-guide" data-worker-language="python" markdown="1">

<p class="worker-language-guide__heading" role="heading" aria-level="3">1. Install Python support</p>

```bash
pip install conductor-python
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">2. Save the worker and workflow app</p>

Save as `quickstart.py`:

```python
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.orkes_clients import OrkesClients
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.worker.worker_task import worker_task


@worker_task(task_definition_name="greet", register_task_def=True)
def greet(name: str) -> dict:
    return {"result": f"Hello {name}"}


def main():
    config = Configuration()
    clients = OrkesClients(configuration=config)
    executor = clients.get_workflow_executor()

    workflow = ConductorWorkflow(name="greetings", version=1, executor=executor)
    greet_task = greet(task_ref_name="greet_ref", name=workflow.input("name"))
    workflow >> greet_task
    workflow.output_parameters({"result": greet_task.output("result")})
    workflow.register(overwrite=True)

    with TaskHandler(configuration=config, scan_for_annotated_workers=True) as handler:
        handler.start_processes()
        run = executor.execute(name="greetings", version=1, workflow_input={"name": "Conductor"})
        print(run.output["result"])


if __name__ == "__main__":
    main()
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">3. Run and verify</p>

```bash
python quickstart.py
# Hello Conductor
```

See the [Python SDK guide](../documentation/clientsdks/python-sdk.md) for worker configuration and production patterns.

  </section>

  <section class="worker-language-guide" data-worker-language="java" hidden markdown="1">

<p class="worker-language-guide__heading" role="heading" aria-level="3">1. Install Java support</p>

Add the SDK dependency to your Gradle project:

```groovy
dependencies {
    implementation 'org.conductoross:conductor-client:5.0.1'
}
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">2. Save the worker and workflow app</p>

Save as `Main.java`:

```java
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import java.util.List;
import java.util.Map;

class GreetWorker implements Worker {
    @Override
    public String getTaskDefName() {
        return "greet";
    }

    @Override
    public TaskResult execute(Task task) {
        String name = (String) task.getInputData().get("name");
        TaskResult result = new TaskResult(task);
        result.setStatus(TaskResult.Status.COMPLETED);
        result.addOutputData("result", "Hello " + name);
        return result;
    }
}

public class Main {
    public static void main(String[] args) {
        String serverUrl = System.getenv().getOrDefault(
                "CONDUCTOR_SERVER_URL", "http://localhost:8080/api");
        ConductorClient client = ConductorClient.builder().basePath(serverUrl).build();
        WorkflowExecutor executor = new WorkflowExecutor(client);

        ConductorWorkflow workflow = new ConductorWorkflow<>(executor);
        workflow.setName("greetings");
        workflow.setVersion(1);
        SimpleTask greetTask = new SimpleTask("greet", "greet_ref");
        greetTask.input("name", "${workflow.input.name}");
        workflow.add(greetTask);
        workflow.registerWorkflow(true, true);

        TaskClient taskClient = new TaskClient(client);
        new TaskRunnerConfigurer.Builder(taskClient, List.of(new GreetWorker()))
                .withThreadCount(10)
                .build()
                .init();

        WorkflowClient workflowClient = new WorkflowClient(client);
        String workflowId = workflowClient.startWorkflow(
                "greetings", 1, "", Map.of("name", "Conductor"));
        System.out.println("Started workflow: " + workflowId);
    }
}
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">3. Run and verify</p>

Run the class with your Gradle application task, then inspect the completed `greet_ref` task in the `greetings` execution. Its output is:

```text
Hello Conductor
```

See the [Java SDK guide](../documentation/clientsdks/java-sdk.md) for complete imports and worker configuration.

  </section>

  <section class="worker-language-guide" data-worker-language="typescript" hidden markdown="1">

<p class="worker-language-guide__heading" role="heading" aria-level="3">1. Install TypeScript / JavaScript support</p>

```bash
npm install @io-orkes/conductor-javascript
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">2. Save the worker and workflow app</p>

Save as `quickstart.ts`:

```typescript
import {
  OrkesClients,
  ConductorWorkflow,
  TaskHandler,
  worker,
  simpleTask,
} from "@io-orkes/conductor-javascript";
import type { Task } from "@io-orkes/conductor-javascript";

@worker({ taskDefName: "greet" })
async function greet(task: Task) {
  return {
    status: "COMPLETED" as const,
    outputData: { result: `Hello ${task.inputData.name}` },
  };
}

async function main() {
  const clients = await OrkesClients.from();
  const executor = clients.getWorkflowClient();
  const workflow = new ConductorWorkflow(executor, "greetings")
    .add(simpleTask("greet_ref", "greet", { name: "${workflow.input.name}" }))
    .outputParameters({ result: "${greet_ref.output.result}" });
  await workflow.register();

  const handler = new TaskHandler({ client: clients.getClient(), scanForDecorated: true });
  await handler.startWorkers();
  const run = await workflow.execute({ name: "Conductor" });
  console.log(run.output?.result);
  await handler.stopWorkers();
}

main();
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">3. Run and verify</p>

```bash
npx ts-node quickstart.ts
# Hello Conductor
```

See the [JavaScript SDK guide](../documentation/clientsdks/js-sdk.md) for TypeScript 5 decorators, worker health, and production configuration.

  </section>

  <section class="worker-language-guide" data-worker-language="csharp" hidden markdown="1">

<p class="worker-language-guide__heading" role="heading" aria-level="3">1. Install C# support</p>

```bash
dotnet add package conductor-csharp
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">2. Save and start the worker</p>

Save as `GreetWorker.cs`:

```csharp
using Conductor.Client.Extensions;
using Conductor.Client.Interfaces;
using Conductor.Client.Models;
using Conductor.Client.Worker;
using Task = Conductor.Client.Models.Task;

public class GreetWorker : IWorkflowTask
{
    public string TaskType => "greet";
    public WorkflowTaskExecutorConfiguration WorkerSettings { get; } = new();

    public async Task<TaskResult> Execute(Task task, CancellationToken token)
    {
        var result = task.Completed();
        result.OutputData = new Dictionary<string, object>
        {
            ["result"] = $"Hello {task.InputData["name"]}"
        };
        return await System.Threading.Tasks.Task.FromResult(result);
    }

    public TaskResult Execute(Task task) => throw new NotImplementedException();
}
```

Start the worker with the SDK's maintained worker-host pattern in `Program.cs`:

```csharp
using Conductor.Client;
using Conductor.Client.Authentication;
using Conductor.Client.Worker;
using Microsoft.Extensions.Logging;

var configuration = new Configuration
{
    BasePath = Environment.GetEnvironmentVariable("CONDUCTOR_SERVER_URL"),
    AuthenticationSettings = new OrkesAuthenticationSettings(
        Environment.GetEnvironmentVariable("CONDUCTOR_AUTH_KEY"),
        Environment.GetEnvironmentVariable("CONDUCTOR_AUTH_SECRET"))
};
var host = WorkflowTaskHost.CreateWorkerHost(
    configuration, LogLevel.Information, new GreetWorker());
await host.StartAsync(CancellationToken.None);
await Task.Delay(Timeout.Infinite);
```

In a second terminal, save this as `greetings.json`, then register and run it:

```json
{
  "name": "greetings",
  "description": "Return a greeting from a C# worker.",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [{
    "name": "greet",
    "taskReferenceName": "greet_ref",
    "type": "SIMPLE",
    "inputParameters": { "name": "${workflow.input.name}" }
  }],
  "outputParameters": { "result": "${greet_ref.output.result}" }
}
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">3. Run and verify</p>

```bash
dotnet run
# In the second terminal:
conductor workflow create greetings.json
conductor workflow start -w greetings -i '{"name":"Conductor"}' --sync
# result: Hello Conductor
```

See the [C# SDK guide](../documentation/clientsdks/csharp-sdk.md) for the maintained examples and SDK reference.

  </section>

  <section class="worker-language-guide" data-worker-language="rust" hidden markdown="1">

<p class="worker-language-guide__heading" role="heading" aria-level="3">1. Create a Rust app and add the SDK</p>

```bash
cargo new greetings-worker
cd greetings-worker
```

In `Cargo.toml`, add the SDK and async runtime under `[dependencies]`:

```toml
[dependencies]
conductor = { version = "0.1", package = "conductor-sdk", features = ["macros"] }
conductor-macros = "0.1"
tokio = { version = "1", features = ["full"] }
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">2. Save the worker and workflow app</p>

Replace `src/main.rs` with:

```rust
use conductor::{
    client::ConductorClient,
    configuration::Configuration,
    models::{StartWorkflowRequest, WorkflowDef, WorkflowTask},
    worker::TaskHandler,
};
use conductor_macros::worker;

#[worker(name = "greet")]
async fn greet(name: String) -> String {
    format!("Hello {}", name)
}

fn greetings_workflow() -> WorkflowDef {
    WorkflowDef::new("greetings")
        .with_version(1)
        .with_task(
            WorkflowTask::simple("greet", "greet_ref")
                .with_input_param("name", "${workflow.input.name}"),
        )
        .with_output_param("result", "${greet_ref.output.result}")
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Reads CONDUCTOR_SERVER_URL and, when needed, CONDUCTOR_AUTH_* from the environment.
    let config = Configuration::default();
    let client = ConductorClient::new(config.clone())?;

    client
        .metadata_client()
        .register_or_update_workflow_def(&greetings_workflow(), true)
        .await?;

    let mut task_handler = TaskHandler::new(config.clone())?;
    task_handler.add_worker(greet_worker());
    task_handler.start().await?;

    let run = client
        .workflow_client()
        .execute_workflow(
            &StartWorkflowRequest::new("greetings")
                .with_version(1)
                .with_input_value("name", "Conductor"),
            std::time::Duration::from_secs(10),
        )
        .await?;

    println!("result: {:?}", run.output.get("result"));
    task_handler.stop().await?;
    Ok(())
}
```

<p class="worker-language-guide__heading" role="heading" aria-level="3">3. Run and verify</p>

```bash
cargo run
# result: Some("Hello Conductor")
```

See the maintained [Rust SDK quickstart](https://github.com/conductor-oss/rust-sdk#60-second-quickstart) for worker configuration, metrics, and production patterns.

  </section>
</div>

<script>
  (function () {
    var select = document.getElementById("worker-language-select");
    var guides = document.querySelectorAll("[data-worker-language]");

    function showGuide() {
      guides.forEach(function (guide) {
        guide.hidden = guide.dataset.workerLanguage !== select.value;
      });
    }

    select.addEventListener("change", showGuide);
  })();
</script>

## Verify and recover

In the Conductor UI, open the `greetings` execution and inspect the completed `greet_ref` task. Its output should include `result: Hello Conductor`.

- If `greet_ref` remains `SCHEDULED`, the worker is not polling the `greet` task type. Confirm the worker is running and that the worker task type is exactly `greet`.
- If workflow registration says the definition already exists, use a new version or update the local test definition before running it again.
- If `greet_ref` fails, inspect the task's input, output, and failure reason in the UI; fix the worker, restart it, and start a new execution.

## See durability happen

Durability means the execution outlives your process. The quickstart app exits after it prints, so no worker is running now. Start a new execution with only the CLI:

```bash
conductor workflow start -w greetings -i '{"name":"Conductor"}'
```

Open the execution in the UI: the workflow is `RUNNING` and `greet_ref` stays `SCHEDULED` — durably queued, not lost. Now run your quickstart app again. The moment the worker polls, the waiting task completes and the execution finishes with `result: Hello Conductor`.

## Keep learning

**Next:** [Run your first agent](first-agent.md) — the same durable execution model, applied to an LLM-powered agent.

Prefer no code? [Run a workflow from JSON](first-workflow.md) registers a two-step workflow with the CLI alone. The [SDKs landing page](../documentation/clientsdks/index.md) links to Go, Ruby, Rust, and the language-specific reference material and production guidance for every supported SDK.
