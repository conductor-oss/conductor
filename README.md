
<picture>
  <!-- Dark mode logo -->
  <source srcset="https://github.com/user-attachments/assets/104b3a67-6013-4622-8075-a45da3a9e726" media="(prefers-color-scheme: dark)">
  <!-- Light mode logo -->
  <img src="https://assets.conductor-oss.org/logo.png" alt="Logo">
</picture>


<h1 align="center" style="border-bottom: none">
    Conductor - Internet scale Workflow Engine
</h1>


[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor?style=social)](https://github.com/conductor-oss/conductor/stargazers)
[![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://github.com/conductor-oss/conductor/releases)
[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Conductor Slack](https://img.shields.io/badge/Slack-Join%20the%20Community-blueviolet?logo=slack)](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)
[![Conductor OSS](https://img.shields.io/badge/Conductor%20OSS-Visit%20Site-blue)](https://conductor-oss.org)

#### Orchestrating distributed systems means wrestling with failures, retries, and state recovery. Conductor handles all of that so you don't have to.

Conductor is an open-source, durable workflow engine built at [Netflix](https://netflixtechblog.com/netflix-conductor-a-microservices-orchestrator-2e8d4771bf40) for orchestrating microservices, AI agents, and durable workflows at internet scale. Trusted in production at Netflix, Tesla, LinkedIn, and J.P. Morgan. Actively maintained by [Orkes](https://orkes.io) and a growing [community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA).

[![conductor_oss_getting_started](https://github.com/user-attachments/assets/6153aa58-8ad1-4ec5-93d1-38ba1b83e3f4)](https://youtu.be/4azDdDlx27M)

---

# Get Running in 60 Seconds

**Prerequisites:** [Node.js](https://nodejs.org/) v16+ and Java 21+ must be installed.

```shell
npm install -g @conductor-oss/conductor-cli
conductor server start
```

Open [http://localhost:8080](http://localhost:8080) — your server is running with the built-in UI.

**Run your first workflow:**

```shell
# Create a workflow that calls an API and parses the response — no workers needed
curl -s https://raw.githubusercontent.com/conductor-oss/conductor/main/docs/quickstart/workflow.json -o workflow.json
conductor workflow create workflow.json
```

> **Note:** Running this command twice will return an error on the second call — the workflow already exists. This is expected behavior. Use `conductor workflow update` to modify an existing workflow.

```shell
conductor workflow start -w hello_workflow --sync
```

See the [Quickstart guide](https://docs.conductor-oss.org/quickstart/) for the full walkthrough, including writing workers and replaying workflows.

**Docker Image for Conductor:**

```shell
docker run -p 8080:8080 conductoross/conductor:latest # replace latest with the published version to pin to a specific version
```

All CLI commands have equivalent cURL/API calls. See the [Quickstart](https://docs.conductor-oss.org/quickstart/) for details.


---

# Why Conductor is the workflow engine of choice for developers

| | |
|---|---|
| **Durable execution** | Every step is persisted. Survives crashes, restarts, and network failures with configurable retries and timeouts. |
| **Deterministic by design** | Orchestration is separated from business logic — determinism is architectural, not developer discipline. Workers run any code; the workflow graph stays deterministic by construction. |
| **AI agent orchestration** | 14+ native LLM providers, MCP tool calling, function calling, human-in-the-loop approval, and vector databases for RAG. |
| **Dynamic at runtime** | Dynamic forks, tasks, and sub-workflows resolved at runtime. LLMs generate JSON workflow definitions and Conductor executes them immediately. |
| **Full replayability** | Restart from the beginning, rerun from any task, or retry just the failed step — on any workflow, at any time. |
| **Internet scale** | Battle-tested at Netflix, Tesla, LinkedIn, and J.P. Morgan. Scales horizontally to billions of workflow executions. |
| **Polyglot workers** | Workers in Java, Python, Go, JavaScript, C#, Ruby, or Rust. Workers poll, execute, and report — run them anywhere. |
| **Self-hosted, no lock-in** | Apache 2.0. 5 persistence backends, 6 message brokers. Runs anywhere Docker or a JVM runs. |

# Ship Agents, Not Framework Code

Conductor workers are plain code — any language, any library, any I/O. No determinism constraints, no SDK ritual. The orchestration layer is declarative and machine-readable, so LLMs generate and compose workflows natively. If an agent crashes at iteration 12, it resumes from iteration 12.

**An autonomous think-act agent in Conductor:** discover tools via MCP, reason with an LLM, call the chosen tool, repeat until done.

```json
{
  "name": "autonomous_agent",
  "description": "Agent that loops until the task is complete",
  "version": 1,
  "tasks": [
    {
      "name": "discover_tools",
      "taskReferenceName": "discover",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}"
      }
    },
    {
      "name": "agent_loop",
      "taskReferenceName": "loop",
      "type": "DO_WHILE",
      "loopCondition": "if ($.loop['think'].output.result.done == true) { false; } else { true; }",
      "loopOver": [
        {
          "name": "think",
          "taskReferenceName": "think",
          "type": "LLM_CHAT_COMPLETE",
          "inputParameters": {
            "llmProvider": "openai",
            "model": "gpt-4o-mini",
            "messages": [
              {
                "role": "system",
                "message": "You are an autonomous agent. Available tools: ${discover.output.tools}. Previous results: ${loop.output.results}. Respond with JSON: {\"action\": \"tool_name\", \"arguments\": {}, \"done\": false} or {\"answer\": \"final answer\", \"done\": true}."
              },
              { "role": "user", "message": "${workflow.input.task}" }
            ]
          }
        },
        {
          "name": "act",
          "taskReferenceName": "act",
          "type": "SWITCH",
          "expression": "$.think.output.result.done ? 'done' : 'call_tool'",
          "decisionCases": {
            "call_tool": [
              {
                "name": "execute_tool",
                "taskReferenceName": "tool_call",
                "type": "CALL_MCP_TOOL",
                "inputParameters": {
                  "mcpServer": "${workflow.input.mcpServerUrl}",
                  "method": "${think.output.result.action}",
                  "arguments": "${think.output.result.arguments}"
                }
              }
            ]
          }
        }
      ]
    }
  ]
}
```

Every step is durably persisted — no framework, no SDK lock-in. Code-first engines force your code to be deterministic so the framework can replay it. Conductor makes the engine deterministic — so your code doesn't have to be.

See the [Build Your First AI Agent](https://docs.conductor-oss.org/devguide/ai/first-ai-agent.html) guide for the full walkthrough.

---

## Conductor Skills for AI Coding Assistants

**[Conductor Skills](https://github.com/conductor-oss/conductor-skills)** let AI coding assistants (Claude Code, Gemini CLI, and others) create, manage, and deploy Conductor workflows directly from your terminal.

### Claude
```shell
# Install Skills for Claude Code
/plugin marketplace add conductor-oss/conductor-skills
/plugin install conductor@conductor-skills
```

### Install for all detected agents

One command to auto-detect every supported agent on your system and install globally where possible. Re-run anytime — it only installs for newly detected agents.

**macOS / Linux**
```bash
curl -sSL https://conductor-oss.github.io/conductor-skills/install.sh | bash -s -- --all
```

**Windows (PowerShell) / (cmd)**
```powershell
# powershell
irm https://conductor-oss.github.io/conductor-skills/install.ps1 -OutFile install.ps1; .\install.ps1 -All

# cmd
powershell -c "irm https://conductor-oss.github.io/conductor-skills/install.ps1 -OutFile install.ps1; .\install.ps1 -All"
```

---

# SDKs

| Language | Repository | Install |
|----------|------------|---------|
| ☕ Java | [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk) | [Maven Central](https://mvnrepository.com/artifact/org.conductoross/conductor-client) |
| 🐍 Python | [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk) | `pip install conductor-python` |
| 🟨 JavaScript | [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk) | `npm install @io-orkes/conductor-javascript` |
| 🐹 Go | [conductor-oss/go-sdk](https://github.com/conductor-oss/go-sdk) | `go get github.com/conductor-sdk/conductor-go` |
| 🟣 C# | [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk) | `dotnet add package conductor-csharp` |
| 💎 Ruby | [conductor-oss/ruby-sdk](https://github.com/conductor-oss/ruby-sdk) | *(incubating)* |
| 🦀 Rust | [conductor-oss/rust-sdk](https://github.com/conductor-oss/rust-sdk) | *(incubating)* |

---

# Documentation & Community

- **[Documentation](https://conductor-oss.org)** — Architecture, guides, API reference, and cookbook recipes.
- **[Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)** — Community discussions and support.
- **[Community Forum](https://community.orkes.io/)** — Ask questions and share patterns.

---

<details>
<summary><strong>Backend Configuration</strong></summary>

| Backend | Configuration |
|---------|---------------|
| Redis + ES7 (default) | [config-redis.properties](docker/server/config/config-redis.properties) |
| Redis + ES8 | [config-redis-es8.properties](docker/server/config/config-redis-es8.properties) |
| Redis + OpenSearch | [config-redis-os.properties](docker/server/config/config-redis-os.properties) |
| Postgres | [config-postgres.properties](docker/server/config/config-postgres.properties) |
| Postgres + ES7 | [config-postgres-es7.properties](docker/server/config/config-postgres-es7.properties) |
| MySQL + ES7 | [config-mysql.properties](docker/server/config/config-mysql.properties) |

</details>

---

# Build From Source

<details>
<summary><strong>Requirements and instructions</strong></summary>

**Requirements:** Docker Desktop, Java (JDK) 21+, Node 18 (for UI)

```shell
git clone https://github.com/conductor-oss/conductor
cd conductor
./gradlew build

# (optional) Build UI
# ./build_ui.sh

# Start local server
cd server
../gradlew bootRun
```

See the [full build guide](docs/devguide/running/source.md) for details.
</details>

---

# FAQ

<details>
<summary><strong>Is this the same as Netflix Conductor?</strong></summary>

Yes. Conductor OSS is the continuation of the original [Netflix Conductor](https://github.com/Netflix/conductor) repository after Netflix contributed the project to the open-source foundation.
</details>

<details>
<summary><strong>Is Conductor open source?</strong></summary>

Yes. Conductor is a fully open-source workflow engine licensed under Apache 2.0. You can self-host on your own infrastructure with 5 persistence backends and 6 message brokers.
</details>

<details>
<summary><strong>Is this project actively maintained?</strong></summary>

Yes. [Orkes](https://orkes.io) is the primary maintainer and offers an enterprise SaaS platform for Conductor across all major cloud providers.
</details>

<details>
<summary><strong>Can Conductor scale to handle my workload?</strong></summary>

Yes. Built at Netflix, battle-tested at internet scale. Conductor scales horizontally across multiple server instances to handle billions of workflow executions.
</details>

<details>
<summary><strong>Does Conductor support durable execution?</strong></summary>

Yes. Conductor pioneered durable execution patterns, ensuring workflows and durable agents complete reliably despite infrastructure failures or crashes. Every step is persisted and recoverable.
</details>

<details>
<summary><strong>Can I replay a workflow after it completes or fails?</strong></summary>

Yes. Conductor preserves full execution history indefinitely. You can restart from the beginning, rerun from a specific task, or retry just the failed step — via API or UI.
</details>

<details>
<summary><strong>Can Conductor orchestrate AI agents and LLMs?</strong></summary>

Yes. Conductor provides native integration with 14+ LLM providers (Anthropic, OpenAI, Gemini, Bedrock, and more), MCP tool calling, function calling, human-in-the-loop approval, and vector database integration for RAG.
</details>

<details>
<summary><strong>Why does Conductor separate orchestration from code?</strong></summary>

Coupling orchestration logic with business logic forces developers to maintain determinism constraints manually — no direct I/O, no system time, no randomness in workflow definitions. Conductor eliminates this entire class of bugs by making the orchestration layer deterministic by construction. Workers are plain code with zero framework constraints — write them in any language, use any library, call any API.
</details>

<details>
<summary><strong>Isn't writing workflows as code more powerful than JSON?</strong></summary>

It depends on what you mean by "powerful." In code-first engines, the workflow definition and your business logic live in the same runtime — which means the engine must replay your code to recover state. That forces determinism constraints on your business logic: no direct I/O, no system time, no threads, no randomness. Conductor separates these concerns. The orchestration graph is declarative (JSON), so it's deterministic by construction. Your workers are plain code with zero constraints — use any language, any library, call any API. You get the full power of code where it matters (business logic) without the framework tax where it doesn't (orchestration).
</details>

<details>
<summary><strong>Can JSON workflows handle complex logic like branching, loops, and error handling?</strong></summary>

Yes. Conductor supports `SWITCH` (conditional branching), `DO_WHILE` (loops with configurable iteration cleanup), `FORK_JOIN` (parallel execution with dynamic fanout), `SUB_WORKFLOW` (composition), and `DYNAMIC` tasks resolved at runtime. These are composable — you can nest loops inside branches inside forks. For error handling, every task supports configurable retries, timeouts, and optional/compensating tasks. The declarative model doesn't limit complexity — it makes complexity visible and debuggable.
</details>

<details>
<summary><strong>How does Conductor handle workflow versioning?</strong></summary>

Workflow definitions are versioned by number. Running executions continue on the version they started with — deploying a new version never breaks in-flight workflows. There's no replay compatibility problem because Conductor doesn't replay your code. The orchestration graph is the source of truth, and each execution is pinned to its definition version. Update orchestration logic without redeploying workers and without worrying about breaking running workflows.
</details>

<details>
<summary><strong>What about developer experience — IDE support, type checking, debugging?</strong></summary>

Conductor provides a built-in visual UI for designing, running, and debugging workflows. Every execution is fully observable: you can inspect the input, output, timing, and retry history of every task. For type safety, Conductor validates workflow inputs and task I/O against JSON Schema. Workers are plain code in your language of choice — you get full IDE support, type checking, and debugging for your business logic. The orchestration layer is visible in the UI, not hidden inside a framework.
</details>

<details>
<summary><strong>Can Conductor handle long-running workflows (days, weeks, months)?</strong></summary>

Yes. Conductor is designed for long-running workflows. Executions are fully persisted — a workflow can pause for months waiting for a human approval, an external signal, or a scheduled timer, and resume exactly where it left off. There's no in-memory state to lose. This is the same mechanism that makes AI agent loops durable: if iteration 12 waits for a human review for three weeks, iteration 13 picks up right where it left off.
</details>

<details>
<summary><strong>Don't I lose flexibility by not having orchestration in code?</strong></summary>

You gain flexibility. Because workflows are JSON, LLMs can generate and modify them at runtime — no compile/deploy cycle. Dynamic forks let you fan out to a variable number of parallel tasks determined at runtime. Dynamic sub-workflows let one workflow compose others by name. And because workers are decoupled from orchestration, you can update the workflow graph or swap worker implementations independently. Code-first engines couple these together, so changing orchestration means redeploying and re-versioning your code.
</details>

<details>
<summary><strong>How does Conductor compare to other workflow engines?</strong></summary>

Conductor is an open-source workflow engine with native LLM task types for 14+ providers, built-in MCP integration, durable execution, full replayability, and 7 language SDKs. Unlike code-first engines, Conductor separates orchestration from business logic — determinism is an architectural guarantee, not a developer constraint. Your workers are plain code with zero framework rules. The orchestration layer is declarative, so it's observable, versionable, and composable by LLMs. Battle-tested at Netflix, Tesla, LinkedIn, and J.P. Morgan.
</details>

<details>
<summary><strong>Is Orkes Conductor compatible with Conductor OSS?</strong></summary>

100% compatible. Orkes Conductor is built on top of Conductor OSS with full API and workflow compatibility.
</details>

---

# Contributing

We welcome contributions from everyone!

- **Report Issues:** Open an [issue on GitHub](https://github.com/conductor-oss/conductor/issues).
- **Contribute code:** Check out our [Contribution Guide](CONTRIBUTING.md) and [good first issues](https://github.com/conductor-oss/conductor/labels/good%20first%20issue).
- **Improve docs:** Help keep our [documentation](https://github.com/conductor-oss/conductor/tree/main/docs) great.

## Contributors

<a href="https://github.com/conductor-oss/conductor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=conductor-oss/conductor" />
</a>

---

# Roadmap

[See the Conductor OSS Roadmap](ROADMAP.md). Want to participate? [Reach out](https://forms.gle/P2i1xHrxPQLrjzTB7).

# License

Conductor is licensed under the [Apache 2.0 License](LICENSE).
