
<picture>
  <!-- Dark mode logo -->
  <source srcset="https://github.com/user-attachments/assets/104b3a67-6013-4622-8075-a45da3a9e726" media="(prefers-color-scheme: dark)">
  <!-- Light mode logo -->
  <img src="https://assets.conductor-oss.org/logo.png" alt="Logo">
</picture>


<h1 align="center" style="border-bottom: none">
    Scalable Workflow Orchestration
</h1>


[![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://GitHub.com/Netflix/conductor-oss/releases)
[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Conductor Slack](https://img.shields.io/badge/Slack-Join%20the%20Community-blueviolet?logo=slack)](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)
[![Community Forum](https://img.shields.io/badge/Discourse-Join%20the%20Community-blue?logo=discourse&logoColor=white)](https://community.orkes.io)
[![Conductor OSS](https://img.shields.io/badge/Conductor%20OSS-Visit%20Site-blue)](https://conductor-oss.org)
[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor?style=social)](https://github.com/conductor-oss/conductor/stargazers)



Conductor is a platform _originally_ created at **Netflix** to orchestrate microservices and events. [Conductor OSS](https://conductor-oss.org) is maintained by the team of developers at [Orkes.io](https://orkes.io/) along with members of the open source community.


[![conductor_oss_getting_started](https://github.com/user-attachments/assets/6153aa58-8ad1-4ec5-93d1-38ba1b83e3f4)](https://youtu.be/4azDdDlx27M)

- - - 
# Table of Contents
1. [What is Conductor?](#what-is-conductor)
    * [Key benefits](#key-benefits)
    * [Features](#features)
2. [Getting Started](#getting-started)
    * [Requirements](#requirements)
    * [Quick Start](#quick-start-guide)
    * [Create your first workflow](#create-your-first-workflow)
3. [Documentation](#documentation)
4. [Database Specifications](#database-specifications)
5. [Deployment Options](#deployment-options)
6. [Conductor Roadmap](#conductor-oss-roadmap)
7. [How to Contribute](#contributors)
8. [Additional Resources](#resources)
9. [Community & Support](#slack-community)

# What is Conductor?
Conductor (or [Netflix Conductor](https://netflixtechblog.com/netflix-conductor-a-microservices-orchestrator-2e8d4771bf40)) is a microservices orchestration engine for distributed and asynchronous workflows. It empowers developers to create workflows that define interactions between services, databases, and other external systems.

Conductor is designed to enable flexible, resilient, and scalable workflows. It allows you to compose services into complex workflows without coupling them tightly, simplifying orchestration across cloud-native applications and enterprise systems alike.

## Key benefits
* **Resilience and Error Handling:** Conductor enables automatic retries and fallback mechanisms.
* **Scalability:** Built to scale with complex workflows in high-traffic environments.
* **Observability:** Provides monitoring and debugging capabilities for workflows.
* **Ease of Integration:** Seamlessly integrates with microservices, external APIs, and legacy systems.

## Features
* **Workflow as Code:** Define workflows in JSON and manage them with versioning.
* **Rich Task Types:** Includes task types like HTTP, JSON, Lambda, Sub Workflow, and Event tasks, allowing for flexible workflow definitions.
* **Dynamic Workflow Management:** Workflows can evolve independently of the underlying services.
* **Built-in UI:** A customizable UI is available to monitor and manage workflows.
* **Flexible Persistence and Queue Options:** Use Redis, MySQL, Postgres, and more.
- - - 
# Getting Started

### Requirements
* Install Docker Desktop ([Mac](https://docs.docker.com/desktop/setup/install/mac-install/), [Windows/PC](https://docs.docker.com/desktop/setup/install/windows-install/), [Linux](https://docs.docker.com/desktop/setup/install/linux/))
* Install Java (JDK) 17 or newer
* Node 14 for the UI to build
  * _Earlier versions may work, but are untested_
  

## Quick Start Guide

#### Clone the repo

```shell
git clone https://github.com/conductor-oss/conductor
```

#### Change to new Conductor directory

```shell
cd conductor
```

#### Start with Docker Compose (_recommended for local deployment_)

```shell
docker compose -f docker/docker-compose.yaml up
```

#### Create your first workflow

##### To create a workflow, navigate to the UI:
* http://localhost:8127

##### Or use the REST API with your preferred HTTP client
* http://localhost:8080

# Documentation
Check-out the [Conductor OSS docs](https://github.com/conductor-oss/conductor/tree/main/docs) for additional details
- - - 
# Database Specifications
* The default persistence used is Redis
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (7.x)


### Configuration for various database backends

| Backend        | Configuration                                                                         |
|----------------|---------------------------------------------------------------------------------------|
| Redis + ES7    | [config-redis.properties](docker/server/config/config-redis.properties)               |
| Postgres       | [config-postgres.properties](docker/server/config/config-postgres.properties)         |
| Postgres + ES7 | [config-postgres-es7.properties](docker/server/config/config-postgres-es7.properties) |
| MySQL + ES7    | [config-mysql.properties](docker/server/config/config-mysql.properties)               |


# Deployment Options
In addition to the Docker Compose setup, Netflix Conductor supports several other deployment methods to suit various environments:

* **Docker:** Outlined above
* **Custom Deployment:** Build from source and deploy Conductor as a standalone Java application. Configure databases, queues, and environment settings as needed.

## Available SDKs
Conductor provides several SDKs for interacting with the API and creating custom clients:

* [**Java SDK:**](https://github.com/conductor-sdk/conductor-javascript) Fully featured for building and executing workflows in Java.
* [**Python SDK:**](https://github.com/conductor-sdk/conductor-python) Python library for creating and managing workflows.
* [**Go SDK:**](https://github.com/conductor-sdk/conductor-go) For integrating Conductor workflows with Go-based services.
* [**C# (C sharp) SDK:**](https://github.com/conductor-sdk/conductor-csharp)The conductor-csharp repository provides the client SDKs to build task workers in C#

Each SDK is maintained as part of the Conductor project, providing examples and comprehensive API documentation.

# Conductor OSS Roadmap
[See the roadmap for the Conductor](ROADMAP.md)
If you would like to participate in the roadmap and development, [please reach out](https://forms.gle/P2i1xHrxPQLrjzTB7).

# Documentation and Community
* **Official Documentation:** [Conductor documentation](https://docs.conductor-oss.org/index.html) contains detailed explanations of workflow concepts, API reference, and guides.
* **Conductor Slack:** [Join the Conductor Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA) channel for community discussions and support.
* **Orkes Community Discourse:** [Hosted by Orkes.io](https://community.orkes.io) on Discourse, you can engage the Conductor & Orkes community, ask questions, and contribute ideas. 
- - -
# License
Conductor is licensed under the Apache 2.0 License © [Conductor Open-source](https://conductor-oss.org/)
- - - 
# Contributing

We welcome contributions from everyone!

- **Report Issues:** Found a bug or have a feature request? Open an [issue on GitHub](https://github.com/conductor-oss/conductor/issues).
- **Contribute Code:** Check out our [Contribution Guide](https://github.com/conductor-oss/conductor/blob/main/CONTRIBUTING.md) for initial guidelines, and explore our [good first issues](https://github.com/conductor-oss/conductor/labels/good%20first%20issue) for beginner-friendly tasks to tackle first.
- **Build a Conductor SDK:** Need an SDK not included with Conductor? Build your own using the [Swagger API](http://localhost:8080) included with your local deployment. 
- **Contribute to our Docs:** Contribute edits or updates to keep our [documentation](https://github.com/conductor-oss/conductor/tree/main/docs) in great shape for the community.

## Contributors

<a href="https://github.com/conductor-oss/conductor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=conductor-oss/conductor" />
</a>
