![Conductor](docs/docs/img/conductor-vector-x.png)


## Conductor
Conductor is a _workflow orchestration_ engine that runs in the cloud.

[![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)
[![CI](https://github.com/Netflix/conductor/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Netflix/conductor/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/Netflix/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![NetflixOSS Lifecycle](https://img.shields.io/osslifecycle/Netflix/conductor.svg)]()

## Releases
The latest version is [![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)

[2.31.8](https://github.com/Netflix/conductor/releases/tag/v2.31.8) is the **final** release of `2.31` branch. As of Feb 2022, `1.x` & `2.x` versions are no longer supported.

## Getting Started - Building & Running Conductor
### Docker
The easiest way to get started is with Docker containers. Please follow the instructions [here](https://github.com/Netflix/conductor/tree/main/docker). The server and UI can also be built from source separately.

### Conductor Server From Source
Conductor Server is a [Spring Boot](https://spring.io/projects/spring-boot) project and follows all applicable conventions. First, ensure that Java JDK 11+ is installed.

#### Development
The server can be started locally by running `./gradlew bootRun` from the project root. This will start up Conductor with an in-memory persistence and queue implementation. It is not recommended for production use but can come in handy for quickly evaluating what Conductor's all about. For actual production use-cases, please use one of the supported persistence and queue implementations.

You can verify the development server is up by navigating to `http://localhost:8080` in a browser.

#### Production Build
Running `./gradlew build` from the project root builds the project into the `/build` directory. Note that Docker is a requirement for tests to run, and thus a requirement to build even if you are building
outside of a Docker container. If you do not have Docker installed you can run `./gradlew build -x test` to skip tests.


#### Pre-built JAR
A [pre-built](https://artifacts.netflix.net/netflixoss/com/netflix/conductor/conductor-server/) executable jar is available that can be downloaded and run using:
 
`java -jar conductor-server-*-boot.jar`

### Conductor UI from Source

The UI is a standard `create-react-app` React Single Page Application (SPA). To get started, with Node 14 and `yarn` installed, first run `yarn install` from within the `/ui` directory to retrieve package dependencies.

There is no need to "build" the project unless you require compiled assets to host on a production web server. If the latter is true, the project can be built with the command `yarn build`.

To run the UI on the bundled development server, run `yarn run start`. Navigate your browser to `http://localhost:5000`. The server must already be running on port 8080. 


## Documentation
[Documentation](http://netflix.github.io/conductor/)  
[Roadmap](https://github.com/Netflix/conductor/wiki/Roadmap)  
[Getting Started Guide](https://netflix.github.io/conductor/gettingstarted/basicconcepts/).

## Published Artifacts
Binaries are available from [Netflix OSS Maven](https://artifacts.netflix.net/netflixoss/com/netflix/conductor/) repository, or the [Maven Central Repository](https://search.maven.org/search?q=g:com.netflix.conductor).

| Artifact | Description |
| ----------- | --------------- |
| conductor-common | Common models used by various conductor modules |
| conductor-core | Core Conductor module |
| conductor-redis-persistence | Persistence and queue using Redis/Dynomite |
| conductor-cassandra-persistence | Persistence using Cassandra |
| conductor-mysql-persistence | Persistence and queue using MySQL |
| conductor-postgres-persistence | Persistence and queue using Postgres |
| conductor-es6-persistence | Indexing using Elasticsearch 6.X |
| conductor-rest | Spring MVC resources for the core services |
| conductor-ui | node.js based UI for Conductor |
| conductor-contribs | Optional contrib package that holds extended workflow tasks and support for SQS, AMQP, etc|
| conductor-client | Java client for Conductor that includes helpers for running worker tasks |
| conductor-client-spring | Client starter kit for Spring |
| conductor-server | Spring Boot Web Application |
| conductor-azureblob-storage | External payload storage implementation using AzureBlob |
| conductor-redis-lock | Workflow execution lock implementation using Redis |
| conductor-zookeeper-lock | Workflow execution lock implementation using Zookeeper |
| conductor-grpc | Protobuf models used by the server and client |
| conductor-grpc-client | gRPC server Application |
| conductor-grpc-server | gRPC client to interact with the gRPC server |
| conductor-test-harness | Integration and regression tests |

## Database Requirements

* The default persistence used is [Dynomite](https://github.com/Netflix/dynomite)
* For queues, we are relying on [dyno-queues](https://github.com/Netflix/dyno-queues)
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (6.x)

## Other Requirements
* JDK 11+
* UI requires Node 14 to build. Earlier Node versions may work but is untested.

## Community
[Discussion Forum](https://github.com/Netflix/conductor/discussions) Please use the forum for questions and discussing ideas and join the community.

[Access here other Conductor related projects made by the community!](/RELATED.md) - Backup tool, Cron like workflow starter, Docker containers...

## Get Support
Conductor is maintained by Media Workflow Infrastructure team at Netflix.  Use github issue tracking for filing issues and [Discussion Forum](https://github.com/Netflix/conductor/discussions) for any other questions, ideas or support requests. 

## Contributions
Whether it is a small documentation correction, bug fix or new features, contributions are highly appreciated. We just ask to follow standard oss guidelines. [Discussion Forum](https://github.com/Netflix/conductor/discussions) is a good place to ask questions, discuss new features and explore ideas. Please check with us before spending too much time, only to find later that someone else is already working on a similar feature.

`main` branch is the current working branch. Please send your PR's to `main` branch, making sure that it builds on your local system successfully. Also, please make sure all the conflicts are resolved.

## License
Copyright 2022 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
