![Conductor](docs/docs/img/logo.png)

# Conductor
Conductor is a platform created by Netflix to orchestrate workflows that span across microservices.

[![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)
[![CI](https://github.com/Netflix/conductor/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Netflix/conductor/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/Netflix/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![NetflixOSS Lifecycle](https://img.shields.io/osslifecycle/Netflix/conductor.svg)]()

## Workflow Creation in Code
Conductor supports creating workflows using JSON and Code.  
SDK support for creating workflows using code is available in multiple languages and can be found at https://github.com/conductor-sdk

## Documentation
[Main Documentation Site](http://conductor.netflix.com/)  

## Releases
The latest version is [![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)

[2.31.8](https://github.com/Netflix/conductor/releases/tag/v2.31.8) is the **final** release of `2.31` branch. As of Feb 2022, `1.x` & `2.x` versions are no longer supported.

## Community Contributions
The modules contributed by the community are housed at [conductor-community](https://github.com/Netflix/conductor-community). Compatible versions of the community modules are released simultaneously with releases of the main modules.

[Discussion Forum](https://github.com/Netflix/conductor/discussions): Please use the forum for questions and discussing ideas and join the community.

[List of Conductor community projects](/docs/docs/resources/related.md): Backup tool, Cron like workflow starter, Docker containers and more.

## Getting Started - Building & Running Conductor
###  Using Docker:
The easiest way to get started is with Docker containers. Please follow the instructions [here](https://conductor.netflix.com/gettingstarted/docker.html). 

###  From Source:
Conductor Server is a [Spring Boot](https://spring.io/projects/spring-boot) project and follows all applicable conventions. See instructions [here](http://conductor.netflix.com/gettingstarted/source.html).


## Published Artifacts
Binaries are available from [Netflix OSS Maven](https://artifacts.netflix.net/netflixoss/com/netflix/conductor/) repository, or the [Maven Central Repository](https://search.maven.org/search?q=g:com.netflix.conductor).

| Artifact                        | Description                                                                                     |
|---------------------------------|-------------------------------------------------------------------------------------------------|
| conductor-common                | Common models used by various conductor modules                                                 |
| conductor-core                  | Core Conductor module                                                                           |
| conductor-redis-persistence     | Persistence and queue using Redis/Dynomite                                                      |
| conductor-cassandra-persistence | Persistence using Cassandra                                                                     |
| conductor-es6-persistence       | Indexing using Elasticsearch 6.X                                                                |
| conductor-rest                  | Spring MVC resources for the core services                                                      |
| conductor-ui                    | node.js based UI for Conductor                                                                  |
| conductor-client                | Java client for Conductor that includes helpers for running worker tasks                        |
| conductor-client-spring         | Client starter kit for Spring                                                                   |
| conductor-server                | Spring Boot Web Application                                                                     |
| conductor-redis-lock            | Workflow execution lock implementation using Redis                                              |
| conductor-awss3-storage         | External payload storage implementation using AWS S3                                            |
| conductor-awssqs-event-queue    | Event queue implementation using AWS SQS                                                        |
| conductor-http-task             | Workflow system task implementation to send make requests                                       |
| conductor-json-jq-task          | Workflow system task implementation to evaluate JSON using [jq](https://stedolan.github.io/jq/) |
| conductor-grpc                  | Protobuf models used by the server and client                                                   |
| conductor-grpc-client           | gRPC client to interact with the gRPC server                                                    |
| conductor-grpc-server           | gRPC server Application                                                                         |
| conductor-test-harness          | Integration and regression tests                                                                |

## Database Requirements

* The default persistence used is [Dynomite](https://github.com/Netflix/dynomite)
* For queues, we are relying on [dyno-queues](https://github.com/Netflix/dyno-queues)
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (6.x)

## Other Requirements
* JDK 11+
* UI requires Node 14 to build. Earlier Node versions may work but is untested.

## Get Support
Conductor is maintained by Media Workflow Infrastructure team at Netflix.  Use Github issue tracking for filing issues and the [Discussion Forum](https://github.com/Netflix/conductor/discussions) for any other questions, ideas or support requests. 

## Contributions
Whether it is a small documentation correction, bug fix or a new feature, contributions are highly appreciated. We just ask you to follow standard OSS guidelines. The [Discussion Forum](https://github.com/Netflix/conductor/discussions) is a good place to ask questions, discuss new features and explore ideas. Please check with us before spending too much time, only to find out later that someone else is already working on a similar feature.

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
