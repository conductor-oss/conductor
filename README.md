
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
1. [What is Conductor?](#what-is-conductor)
    * [Key benefits](#key-benefits)
    * [Features](#features)
2. [Getting Started with Conductor](#getting-started-with-conductor)
    * [Requirements](#requirements)
    * [Quick Start guide](#quick-start-guide)
    * [Create your first workflow](#create-your-first-workflow)
3. [Documentation](#documentation)
4. [Database Specifications](#database-specifications)
5. [Build from source](#build-from-source)
6. [Contributing](#contributing)
7. [Community](#community)
8. [Conductor OSS Roadmap](#conductor-oss-roadmap)
9. [License](#license)

# What is Conductor?
Conductor (or [Netflix Conductor](https://netflixtechblog.com/netflix-conductor-a-microservices-orchestrator-2e8d4771bf40)) is a microservices orchestration engine for distributed and asynchronous workflows. It empowers developers to create workflows that define interactions between services, databases, and other external systems.

Conductor is designed to enable flexible, resilient, and scalable workflows. It allows you to compose services into complex workflows without coupling them tightly, simplifying orchestration across cloud-native applications and enterprise systems alike.

## Key benefits
* **Resilience and Error Handling:** Conductor enables automatic retries and fallback mechanisms.
* **Scalability:** Built to scale with complex workflows in high-traffic environments.
* **Observability:** Provides monitoring and debugging capabilities for workflows.
* **Ease of Integration:** Seamlessly integrates with microservices, external APIs, and legacy systems.

## Features
* **Workflow as code:** Define workflows in JSON and manage them with versioning.
* **Rich task types:** Includes task types like HTTP, JSON, Lambda, Sub Workflow, and Event tasks, allowing for flexible workflow definitions.
* **Dynamic workflow management:** Workflows can evolve independently of the underlying services.
* **Built-in UI:** A customizable UI is available to monitor and manage workflows.
* **Flexible persistence and queue options:** Use Redis, MySQL, Postgres, and more.
- - - 
# Getting Started with Conductor

## Quick Start with Docker

```shell
git clone https://github.com/conductor-oss/conductor
cd conductor
docker compose -f docker/docker-compose.yaml up
```

Access the UI at http://localhost:8080

## Requirements for Source Build
* Install Docker Desktop ([Mac](https://docs.docker.com/desktop/setup/install/mac-install/), [Windows/PC](https://docs.docker.com/desktop/setup/install/windows-install/), [Linux](https://docs.docker.com/desktop/setup/install/linux/))
* Install Java (JDK) 21 or newer
* Node 18 for the UI to build

### Clone the repo
```shell
git clone https://github.com/conductor-oss/conductor
```

### Change to new Conductor directory

```shell
cd conductor
```

### (Optional) Install Git Hook for Auto-formatting

To automatically format code before commits:

```shell
ln -s ../../hooks/pre-commit .git/hooks/pre-commit
```

### Start with Docker Compose (_recommended for local deployment_)

```shell
docker compose -f docker/docker-compose.yaml up
```

## Create your first workflow

### With the UI:
* http://localhost:8127

### Or the REST API with your preferred HTTP client:
* http://localhost:8080

# Documentation
Check-out the [Conductor docs](https://github.com/conductor-oss/conductor/tree/main/docs) for additional details
- - - 
# Database Specifications
* **Default persistence**: Redis (standalone) for DB and queues
  * **Alternative peristence**:  Postgres, MySQL
* **Default indexing backend**: [Elasticsearch](https://www.elastic.co/) 7.x
  * **Alternative indexing backends**: [OpenSearch](https://opensearch.org/) 2.x, Postgres, Sqlite

## Configuration for various database backends

| Backend              | Configuration                                                                             |
|----------------------|-------------------------------------------------------------------------------------------|
| Redis + ES7 (default)         | [config-redis.properties](docker/server/config/config-redis.properties)                   |
| Redis + OS   | [config-redis-os.properties](docker/server/config/config-redis-os.properties)             |
| Postgres             | [config-postgres.properties](docker/server/config/config-postgres.properties)             |
| Postgres + ES7       | [config-postgres-es7.properties](docker/server/config/config-postgres-es7.properties)     |
| MySQL + ES7          | [config-mysql.properties](docker/server/config/config-mysql.properties)                   |


# Build from source
Build from source and deploy Conductor as a standalone Java application. Configure databases, queues, and environment settings as needed. Follow the **[Building Conductor From Source](docs/devguide/running/source.md)** guide included in this repo. 

## Available SDKs
Conductor provides several SDKs for interacting with the API and creating custom clients:

* [**Java SDK:**](https://github.com/conductor-oss/java-sdk) Fully featured for building and executing workflows in Java.
* [**Python SDK:**](https://github.com/conductor-oss/python-sdk) Python library for creating and managing workflows.
* [**Javascript SDK:**](https://github.com/conductor-oss/javascript-sdk) For integrating Conductor workflows with Javascript/Typescript-based services.
* [**Go SDK:**](https://github.com/conductor-oss/go-sdk) For integrating Conductor workflows with Go-based services.
* [**C# SDK:**](https://github.com/conductor-oss/csharp-sdk)The conductor-csharp repository provides the client SDKs to build task workers in C#

Each SDK is maintained as part of the Conductor project, providing examples and comprehensive API documentation.
- - - 
# Contributing

We welcome contributions from everyone!

- **Report Issues:** Found a bug or have a feature request? Open an [issue on GitHub](https://github.com/conductor-oss/conductor/issues).
- **Contribute code:** Check out our [Contribution Guide](CONTRIBUTING.md), and explore our [Good first issues](https://github.com/conductor-oss/conductor/labels/good%20first%20issue) for beginner-friendly tasks to tackle first.
- **Contribute to our Docs:** Contribute edits or updates to keep our [documentation](https://github.com/conductor-oss/conductor/tree/main/docs) in great shape for the community.
- **Build a Conductor SDK:** Need an [SDK](https://github.com/conductor-sdk) not available for Conductor today? Build your own using the [Swagger API](http://localhost:8080) included with your local deployment. 

- - - 
# Community
**[Join the Conductor Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)** channel for community discussions and support. 

## Contributors

<a href="https://github.com/conductor-oss/conductor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=conductor-oss/conductor" />
</a>

- - - 
# Conductor OSS Roadmap
[See the roadmap for the Conductor](ROADMAP.md)
If you would like to participate in the roadmap and development, [please reach out](https://forms.gle/P2i1xHrxPQLrjzTB7).


- - -
# License
Conductor is licensed under the [Apache 2.0 License ©](LICENSE)
