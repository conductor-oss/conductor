
<picture>
  <!-- Dark mode logo -->
  <source srcset="https://github.com/user-attachments/assets/104b3a67-6013-4622-8075-a45da3a9e726" media="(prefers-color-scheme: dark)">
  <!-- Light mode logo -->
  <img src="https://assets.conductor-oss.org/logo.png" alt="Logo">
</picture>


<h1 align="center" style="border-bottom: none">
    Conductor - {agentic, durable, scalable} Workflow Engine
</h1>


[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor?style=social)](https://github.com/conductor-oss/conductor/stargazers)
[![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://github.com/conductor-oss/conductor/releases)
[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Conductor Slack](https://img.shields.io/badge/Slack-Join%20the%20Community-blueviolet?logo=slack)](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)
[![Conductor OSS](https://img.shields.io/badge/Conductor%20OSS-Visit%20Site-blue)](https://conductor-oss.org)

Conductor is an open-source, durable workflow engine built at [Netflix](https://netflixtechblog.com/netflix-conductor-a-microservices-orchestrator-2e8d4771bf40) for orchestrating microservices, AI agents, and event-driven workflows at internet scale. Actively maintained by [Orkes](https://orkes.io) and a growing [community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA).

[![conductor_oss_getting_started](https://github.com/user-attachments/assets/6153aa58-8ad1-4ec5-93d1-38ba1b83e3f4)](https://youtu.be/4azDdDlx27M)

---

# Get Running in 60 Seconds

**Prerequisites:** [Node.js](https://nodejs.org/) v16+ and [Java](https://adoptium.net/) 21+ must be installed.

```shell
npm install -g @conductor-oss/conductor-cli
conductor server start
```

> **Note:** First start downloads ~600 MB (the server JAR). This is a one-time download.

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

<details>
<summary><strong>Prefer Docker?</strong></summary>

```shell
docker run -p 8080:8080 conductoross/conductor:latest
```

All CLI commands have equivalent cURL/API calls. See the [Quickstart](https://docs.conductor-oss.org/quickstart/) for details.
</details>

---

# Conductor Skills for AI Agents

**[Conductor Skills](https://github.com/conductor-oss/conductor-skills)** are pre-built, production-ready workflow packages that give your AI agents superpowers — retrieval, web search, document processing, and more. Install a skill, wire it into your agent, and ship.

```shell
# Install the skills CLI
npm install -g @conductor-oss/conductor-skills

# List available skills
conductor-skills list

# Install a skill
conductor-skills install <skill-name>
```

**[Browse available skills →](https://github.com/conductor-oss/conductor-skills)**

---

# Why Conductor

| | |
|---|---|
| **Durable execution** | Every step is persisted. Survive crashes, restarts, and network failures. At-least-once task delivery with configurable retries, timeouts, and compensation flows. |
| **Deterministic workflows** | JSON definitions separate orchestration from implementation — no side effects, no hidden state. Every run produces the same task graph. Replay any workflow months later. |
| **AI agent orchestration** | 14+ native LLM providers, MCP tool calling, function calling, human-in-the-loop approval, vector databases (Pinecone, pgvector, MongoDB Atlas) for RAG. |
| **Dynamic at runtime** | Dynamic forks, dynamic tasks, and dynamic sub-workflows — all resolved at runtime. LLMs can generate workflow definitions as JSON and Conductor executes them immediately. No compile/deploy cycle. |
| **Full replayability** | Restart from the beginning, rerun from any task, or retry just the failed step — on any workflow, at any time, indefinitely. |
| **Internet scale** | Battle-tested at Netflix, Tesla, LinkedIn, and JP Morgan. Scales horizontally to billions of workflow executions. |
| **Polyglot workers** | Write workers in Java, Python, Go, JavaScript, C#, Ruby, or Rust. Workers poll, execute, and report — run them anywhere. |
| **Self-hosted, no lock-in** | Apache 2.0 licensed. 8+ persistence backends, 6 message brokers. Runs anywhere Docker or a JVM runs. |

---

# SDKs

| Language | Repository | Install |
|----------|------------|---------|
| ☕ Java | [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk) | [Maven Central](https://mvnrepository.com/artifact/org.conductoross/conductor-client) |
| 🐍 Python | [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk) | `pip install conductor-python` |
| 🟨 JavaScript | [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk) | `npm install @io-orkes/conductor-javascript` |
| 🐹 Go | [conductor-oss/go-sdk](https://github.com/conductor-oss/go-sdk) | `go get github.com/conductor-sdk/conductor-go` |
| 🟣 C# | [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk) | `dotnet add package conductor-csharp` |
| 💎 Ruby | [conductor-oss/ruby-sdk](https://github.com/conductor-oss/ruby-sdk) | `gem install conductor_ruby` |
| 🦀 Rust | [conductor-oss/rust-sdk](https://github.com/conductor-oss/rust-sdk) | *(incubating)* |

---

# Documentation & Community

- **[Documentation](https://conductor-oss.org)** — Architecture, guides, API reference, and cookbook recipes.
- **[Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)** — Community discussions and support.
- **[Community Forum](https://community.orkes.io/)** — Ask questions and share patterns.

---

# Backend Configuration

| Backend | Configuration |
|---------|---------------|
| Redis + ES7 (default) | [config-redis.properties](docker/server/config/config-redis.properties) |
| Redis + OpenSearch | [config-redis-os.properties](docker/server/config/config-redis-os.properties) |
| Postgres | [config-postgres.properties](docker/server/config/config-postgres.properties) |
| Postgres + ES7 | [config-postgres-es7.properties](docker/server/config/config-postgres-es7.properties) |
| MySQL + ES7 | [config-mysql.properties](docker/server/config/config-mysql.properties) |

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
<summary><strong>Is this project actively maintained?</strong></summary>

Yes. [Orkes](https://orkes.io) is the primary maintainer and offers an enterprise SaaS platform for Conductor across all major cloud providers.
</details>

<details>
<summary><strong>Can Conductor scale to handle my workload?</strong></summary>

Yes. Built at Netflix, battle-tested at internet scale. Conductor scales horizontally across multiple server instances to handle billions of workflow executions.
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
