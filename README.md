
<picture>
  <!-- Dark mode logo -->
  <source srcset="https://github.com/user-attachments/assets/104b3a67-6013-4622-8075-a45da3a9e726" media="(prefers-color-scheme: dark)">
  <!-- Light mode logo -->
  <img src="https://assets.conductor-oss.org/logo.png" alt="Logo">
</picture>


<h1 align="center" style="border-bottom: none">
    Conductor - Scalable Workflow Orchestration
</h1>


[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor?style=social)](https://github.com/conductor-oss/conductor/stargazers)
[![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://github.com/conductor-oss/conductor/releases)
[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Conductor Slack](https://img.shields.io/badge/Slack-Join%20the%20Community-blueviolet?logo=slack)](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)
[![Conductor OSS](https://img.shields.io/badge/Conductor%20OSS-Visit%20Site-blue)](https://conductor-oss.org)




Conductor is an open-source orchestration engine built at Netflix to help developers manage microservices and event-driven workflows. Today, it’s actively maintained by the team at [Orkes](https://orkes.io) and a growing [community of contributors](https://orkes-conductor.slack.com/join/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA#/shared-invite/email).


[![conductor_oss_getting_started](https://github.com/user-attachments/assets/6153aa58-8ad1-4ec5-93d1-38ba1b83e3f4)](https://youtu.be/4azDdDlx27M)

- - - 
# Table of Contents
<!-- TOC -->
* [Table of Contents](#table-of-contents)
* [What is Conductor?](#what-is-conductor)
  * [Key Features](#key-features)
  * [Use Cases](#use-cases)
* [Getting Started with Conductor](#getting-started-with-conductor)
    * [All Options](#all-options)
* [Conductor SDKs](#conductor-sdks)
* [Documentation](#documentation)
* [Community / I Need Help](#community--i-need-help)
* [Backend Configuration options](#backend-configuration-options)
* [Build from source](#build-from-source)
    * [Requirements for Source Build](#requirements-for-source-build)
    * [Clone the repo](#clone-the-repo)
* [Frequently Asked Questions](#frequently-asked-questions)
* [Contributing](#contributing)
  * [Contributors](#contributors)
* [Conductor OSS Roadmap](#conductor-oss-roadmap)
* [License](#license)
<!-- TOC -->

# What is Conductor?
Conductor (or [Netflix Conductor](https://netflixtechblog.com/netflix-conductor-a-microservices-orchestrator-2e8d4771bf40)) is a durable orchestration engine for workflows and agents. 
It empowers developers to create workflows that define interactions between services, agents databases, and other external systems providing durability of execution against underlying infrastructure failures.

Conductor is designed to enable flexible, resilient, and scalable workflows. 
It allows you to compose services into complex workflows without coupling them tightly, simplifying orchestration across cloud-native applications and enterprise systems alike.

Conductor OSS is the continuation original [Netflix Conductor Repository](https://github.com/Netflix/conductor) afer Netflix contributed the source to the OSS foundation.

## Key Features
* **Durable Execution** Workflows are guaranteed to complete even when there are temporary failures in the system.
* **Resilience and Error Handling:** Conductor enables automatic retries and fallback mechanisms.
* **Scalability:** Built to scale with complex workflows in high-traffic environments.
* **Observability:** Provides monitoring and debugging capabilities for workflows.
* **Ease of Integration:** Seamlessly integrates with microservices, agents, LLMs, external APIs, and legacy systems.
* **Built-in UI:** A customizable UI is available to monitor and manage workflows.
* **Flexible persistence:** Use Redis, MySQL, Postgres, and more.

## Use Cases
* **Microservices orchestration** Orchestrate very complex microservices flows both _synchronously_ and _asynchronously_. 
* **Durable code execution**, tasks in the workflow are durable with at-least once delivery semantics offered by the queues
* **Agentic workflows** Conductor workflows can be fully dynamic, LLMs can plan and design workflows that can be executed by Conductor server at runtime.  No compile, deploy cycle required.
* **Agentic RAG** Easy to build RAG pipelines with LLM and Vector DB integrations 

- - - 
# Getting Started with Conductor

**One-liner for macOS / Linux**
```bash
curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh
```

**One-liner for Windows PowerShell:**
```powershell
irm https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.ps1 | iex
```

### All Options

| Operating System | Interactive | With Custom Port |
|------------------|-------------|------------------|
| macOS / Linux | `./conductor_server.sh` | `./conductor_server.sh 9090` |
| Windows (CMD) | `conductor_server.bat` | `conductor_server.bat 9090` |
| Windows (PowerShell) | `.\conductor_server.ps1` | `.\conductor_server.ps1 9090` |

Each script will:
1. Download the Conductor server JAR (if not already present)
2. Start the server with `java -jar`

Set `CONDUCTOR_HOME` to specify where the JAR is stored (defaults to current directory).

**Or run with Docker:**

```shell
docker run -p 8080:8080 conductoross/conductor:latest
```

Access the UI at http://localhost:8080

# Conductor SDKs

| Language | Repository | Installation |
|----------|------------|--------------|
| ☕ Java | [conductor-oss/java-sdk](https://github.com/conductor-oss/java-sdk) | [Maven Central](https://mvnrepository.com/artifact/org.conductoross/conductor-client) |
| 🐍 Python | [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk) | `pip install conductor-python` |
| 🟨 JavaScript | [conductor-oss/javascript-sdk](https://github.com/conductor-oss/javascript-sdk) | `npm install @io-orkes/conductor-javascript` |
| 🐹 Go | [conductor-oss/go-sdk](https://github.com/conductor-oss/go-sdk) | `go get github.com/conductor-sdk/conductor-go` |
| 🟣 C# | [conductor-oss/csharp-sdk](https://github.com/conductor-oss/csharp-sdk) | `dotnet add package conductor-csharp` |
| 🦀 Rust | [conductor-oss/rust-sdk](https://github.com/conductor-oss/rust-sdk) | *(incubating - build locally)* |

# Documentation
Check-out the [Conductor docs](https://github.com/conductor-oss/conductor/tree/main/docs) for additional details

# Community / I Need Help
* **[Join the Conductor Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)** channel for community discussions and support.
* Post your question at: https://community.orkes.io/

# Backend Configuration options
Conductor is quite flexible and works with the majority of the popular databases and search systems.
Use this table as a reference for various backend configurations.  

| Backend              | Configuration                                                                             |
|----------------------|-------------------------------------------------------------------------------------------|
| Redis + ES7 (default)         | [config-redis.properties](docker/server/config/config-redis.properties)                   |
| Redis + OS   | [config-redis-os.properties](docker/server/config/config-redis-os.properties)             |
| Postgres             | [config-postgres.properties](docker/server/config/config-postgres.properties)             |
| Postgres + ES7       | [config-postgres-es7.properties](docker/server/config/config-postgres-es7.properties)     |
| MySQL + ES7          | [config-mysql.properties](docker/server/config/config-mysql.properties)                   |


# Build from source
Build from source and deploy Conductor as a standalone Java application. Configure databases, queues, and environment settings as needed. Follow the **[Building Conductor From Source](docs/devguide/running/source.md)** guide included in this repo. 

### Requirements for Source Build
* Install Docker Desktop ([Mac](https://docs.docker.com/desktop/setup/install/mac-install/), [Windows/PC](https://docs.docker.com/desktop/setup/install/windows-install/), [Linux](https://docs.docker.com/desktop/setup/install/linux/))
* Install Java (JDK) 21 or newer
* Node 18 for the UI to build

### Clone the repo
```shell
git clone https://github.com/conductor-oss/conductor
cd conductor
./gradlw build

# (optional) Build UI
# ./build_ui.sh

# once the build is completed, you can start local server
cd server
../gradlew bootRun
```

- - -
# Frequently Asked Questions

<details>
<summary><strong>Is this the same as Netflix Conductor?</strong></summary>

Yes. Conductor OSS is the continuation of the original [Netflix Conductor](https://github.com/Netflix/conductor) repository after Netflix contributed the project to the open-source foundation.
</details>

<details>
<summary><strong>Is this project actively maintained?</strong></summary>

Yes. [Orkes](https://orkes.io) is the primary maintainer of this repository and offers an enterprise SaaS platform for Conductor across all major cloud providers.
</details>

<details>
<summary><strong>Can Conductor scale to handle my workload?</strong></summary>

Absolutely. Conductor was built at Netflix to handle massive scale and has been battle-tested in production environments processing millions of workflows. It scales horizontally to meet virtually any demand.
</details>

<details>
<summary><strong>Does Conductor support durable code execution?</strong></summary>

Yes. Conductor pioneered durable execution patterns, ensuring workflows complete reliably even in the face of infrastructure failures, process crashes, or network issues.
</details>

<details>
<summary><strong>Are workflows always asynchronous?</strong></summary>

No. While Conductor excels at asynchronous orchestration, it also supports synchronous workflow execution when immediate results are required.
</details>

<details>
<summary><strong>Do I need to use a Conductor-specific framework?</strong></summary>

Not at all. Conductor is language and framework agnostic. Use your preferred language and framework—our [SDKs](#conductor-sdks) provide native integration for Java, Python, JavaScript, Go, C#, and more.
</details>

<details>
<summary><strong>Is Conductor a low-code/no-code platform?</strong></summary>

No. Conductor is designed for developers who write code. While workflows can be defined in JSON, the power of Conductor comes from building workers and tasks in your preferred programming language using our SDKs.
</details>

<details>
<summary><strong>Can Conductor handle complex workflows?</strong></summary>

Conductor was specifically designed for complex orchestration. It supports advanced patterns including nested loops, dynamic branching, sub-workflows, and workflows with thousands of tasks—complexity that other orchestration tools struggle to handle.
</details>

<details>
<summary><strong>Is Netflix Conductor abandoned?</strong></summary>

No. The original Netflix repository has transitioned to Conductor OSS, which is the new home for the project. Active development and maintenance continues here.
</details>

<details>
<summary><strong>Is Orkes Conductor compatible with Conductor OSS?</strong></summary>

100% compatible. Orkes Conductor is built on top of Conductor OSS, ensuring full compatibility between the open-source version and the enterprise offering.
</details>

- - -


# Contributing

We welcome contributions from everyone!

- **Report Issues:** Found a bug or have a feature request? Open an [issue on GitHub](https://github.com/conductor-oss/conductor/issues).
- **Contribute code:** Check out our [Contribution Guide](CONTRIBUTING.md), and explore our [Good first issues](https://github.com/conductor-oss/conductor/labels/good%20first%20issue) for beginner-friendly tasks to tackle first.
- **Contribute to our Docs:** Contribute edits or updates to keep our [documentation](https://github.com/conductor-oss/conductor/tree/main/docs) in great shape for the community.
- **Build a Conductor SDK:** Need an [SDK](https://github.com/conductor-sdk) not available for Conductor today? Build your own using the [Swagger API](http://localhost:8080) included with your local deployment.


## Contributors

<a href="https://github.com/conductor-oss/conductor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=conductor-oss/conductor" />
</a>

- - - 
# Conductor OSS Roadmap
[See the roadmap for the Conductor](ROADMAP.md)
If you would like to participate in the roadmap and development, [please reach out](https://forms.gle/P2i1xHrxPQLrjzTB7).


# License
Conductor is licensed under the [Apache 2.0 License ©](LICENSE)
