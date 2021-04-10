![Conductor](docs/docs/img/conductor-vector-x.png)


## Conductor
Conductor is an _orchestration_ engine that runs in the cloud.



[![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)
[![CI](https://github.com/Netflix/conductor/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Netflix/conductor/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/Netflix/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![NetflixOSS Lifecycle](https://img.shields.io/osslifecycle/Netflix/conductor.svg)]()

## Community
[Discussion Forum](https://github.com/Netflix/conductor/discussions) Please use the forum for questions and discussing ideas and join the community.

[Access here other Conductor related projects made by the community!](/RELATED.md) - Backup tool, Cron like workflow starter, Docker containers...

## Builds
| Branch |                                                     Build                                                     |
|:------:|:-------------------------------------------------------------------------------------------------------------:|
| main | [![CI](https://github.com/Netflix/conductor/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Netflix/conductor/actions/workflows/ci.yml) |
| 2.31 | [![Build Status](https://travis-ci.com/Netflix/conductor.svg?branch=2.31)](https://travis-ci.com/Netflix/conductor) |

## Documentation & Getting Started
[http://netflix.github.io/conductor/](http://netflix.github.io/conductor/)

[Getting Started](https://netflix.github.io/conductor/gettingstarted/basicconcepts/) guide.

## Get Conductor
Binaries are available from Maven Central.

Below are the various artifacts published:

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
* JDK 1.8+

## Get Support
Conductor is maintained by Media Workflow Infrastructure team at Netflix.  Use github issue tracking for any support request. 

## Contributions
Whether it is a small doc correction, bug fix or adding new module to support some crazy feature, contributions are highly appreciated. We just ask to follow standard oss guidelines. To reiterate, please check with us before spending too much time, only to find later that someone else is already working on a similar feature. 

`main` branch is the current working branch, while `2.31` branch is the latest stable 2.x branch. Please send your PR's to `main` branch, making sure that it builds on your local system successfully. Also, please make sure all the conflicts are resolved.

Feel free to use the [discussion forum](https://github.com/Netflix/conductor/discussions) for asking questions.

## License
Copyright 2021 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
