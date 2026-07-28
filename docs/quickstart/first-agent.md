---
description: Run your first Conductor Agent and verify its durable execution with the SDK language you choose.
---

# Run your first Conductor Agent

**Audience:** developers authoring a new Conductor Agent with Python, Java, TypeScript/JavaScript, or C#.

**Outcome:** a completed agent run that is compiled to and executed as a Conductor workflow.

Conductor Agents are available in Python, Java, TypeScript/JavaScript, and C#. Choose a language below to see its complete install and first-run steps.

For an existing framework agent, such as LangChain, use [framework agent quickstarts](framework-agents.md). For a declarative LLM and tool workflow, start from [Agents & AI](../devguide/ai/index.md).

## Prerequisites

Complete [Connect to Conductor](connect.md), including the hosted model integration or local provider API-key setup required by the selected model. You also need the runtime or SDK tooling for the language you select.

## Run your first workflow

If your work starts with services, APIs, timers, or workers, begin with [Run your first workflow](first-workflow.md). It creates and inspects a durable two-step workflow without requiring an agent or model-provider credential.

## Language-specific quickstart

`AGENTSPAN_SERVER_URL` and its access credentials are configured in [Connect to Conductor](connect.md). Keep provider credentials in the environment or secret system used by the agent workers; do not put them in workflow input.

<div class="agent-language-picker" markdown="1">
  <label for="agent-language-select">Language</label>
  <select id="agent-language-select" aria-describedby="agent-language-help">
    <option value="python" selected>Python</option>
    <option value="java">Java</option>
    <option value="typescript">TypeScript / JavaScript</option>
    <option value="csharp">C#</option>
  </select>
  <p id="agent-language-help">Choose a language to reveal its install and runnable first-agent steps.</p>

  <section class="agent-language-guide" data-agent-language="python" markdown="1">

<p class="agent-language-guide__heading" role="heading" aria-level="3">1. Install Python support</p>

```bash
pip install conductor-python
```

<p class="agent-language-guide__heading" role="heading" aria-level="3">2. Save and run an agent</p>

Save this as `hello.py`:

```python
from conductor.ai.agents import Agent, AgentRuntime

agent = Agent(
    name="greeter",
    model="openai/gpt-4o-mini",
    instructions="You are a friendly assistant. Keep responses brief.",
)

with AgentRuntime() as runtime:
    result = runtime.run(agent, "Say hello and share a fun Python fact.")
    result.print_result()
```

```bash
python hello.py
```

See the [Python agent guide](https://github.com/conductor-oss/python-sdk/tree/main/docs/agents) for more examples.

  </section>

  <section class="agent-language-guide" data-agent-language="java" hidden markdown="1">

<p class="agent-language-guide__heading" role="heading" aria-level="3">1. Install Java support</p>

Gradle:

```groovy
dependencies {
    implementation 'org.conductoross:conductor-client-ai:VERSION'
}
```

Maven:

```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client-ai</artifactId>
    <version>VERSION</version>
</dependency>
```

<p class="agent-language-guide__heading" role="heading" aria-level="3">2. Define and run an agent</p>

```java
import org.conductoross.conductor.ai.Agent;
import org.conductoross.conductor.ai.AgentRuntime;
import org.conductoross.conductor.ai.model.AgentResult;

Agent agent = Agent.builder()
    .name("java_greeter")
    .model("openai/gpt-4o-mini")
    .instructions("You are friendly and concise.")
    .build();

try (AgentRuntime runtime = new AgentRuntime()) {
    AgentResult result = runtime.run(agent, "Share a fun Java fact.");
    result.printResult();
}
```

Run the class with your Gradle or Maven application task. See the [Java agent guide](https://github.com/conductor-oss/java-sdk/tree/main/docs/agents) for project setup and runnable examples.

  </section>

  <section class="agent-language-guide" data-agent-language="typescript" hidden markdown="1">

<p class="agent-language-guide__heading" role="heading" aria-level="3">1. Install TypeScript / JavaScript support</p>

```bash
npm install @io-orkes/conductor-javascript
```

<p class="agent-language-guide__heading" role="heading" aria-level="3">2. Save and run an agent</p>

Save this as `my-agent.ts`:

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

See the [TypeScript agent guide](https://github.com/conductor-oss/javascript-sdk/tree/main/docs/agents) for more examples.

  </section>

  <section class="agent-language-guide" data-agent-language="csharp" hidden markdown="1">

<p class="agent-language-guide__heading" role="heading" aria-level="3">1. Install C# support</p>

```bash
dotnet add package conductor-ai
```

<p class="agent-language-guide__heading" role="heading" aria-level="3">2. Define and run an agent</p>

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

```bash
dotnet run
```

See the [C# agent guide](https://github.com/conductor-oss/csharp-sdk/tree/main/docs/agents) for project setup and runnable examples.

  </section>
</div>

<script>
  (function () {
    var select = document.getElementById("agent-language-select");
    var guides = document.querySelectorAll("[data-agent-language]");

    function showGuide() {
      guides.forEach(function (guide) {
        guide.hidden = guide.dataset.agentLanguage !== select.value;
      });
    }

    select.addEventListener("change", showGuide);
  })();
</script>

## 3. Verify and recover

In the Conductor UI, locate the execution created by the run. Verify its terminal status and inspect its task timeline, inputs, and output. If the run cannot reach the model, first confirm the server URL and provider credential in the worker environment; then inspect the failed task in the execution before retrying.

## Add your agent to a workflow

After deploying an agent, a workflow can invoke it as an `AGENT` task alongside ordinary API calls, retrieval, approval, retries, branches, and parallel work. The workflow owns the durable business process; the agent owns the model-driven decision or action inside it.

```json
{
  "name": "ask_agent",
  "taskReferenceName": "ask_agent_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "name": "greeter",
    "prompt": "Summarize this workflow context: ${fetch_context.output.response.body}",
    "pollIntervalSeconds": 5
  }
}
```

The task records the agent execution ID, state, text, and structured output, so operators can inspect the parent workflow and the agent run together. See the [complete workflow-plus-agent example](../devguide/ai/first-ai-agent.md) or the [`AGENT` task integration guide](../devguide/ai/conductor-agents.md#use-a-deployed-agent-in-a-workflow).

## What you built

Each language uses the same durable execution model: the runtime compiles and runs the agent as a Conductor workflow, preserving an inspectable execution record. A later design can add approval, waits, retries, composition, and operational recovery without moving the agent logic into one long-lived process.

## Next production step

Continue with the [production agent architecture](../devguide/ai/production-agent-architecture.md). It covers governance, evaluation, deployment, composition, recovery, and operations.
