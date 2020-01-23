![Conductor](docs/docs/img/conductor-vector-x.png)


## Conductor
Conductor is an _orchestration_ engine that runs in the cloud.



[![Download](https://api.bintray.com/packages/netflixoss/maven/conductor/images/download.svg)](https://bintray.com/netflixoss/maven/conductor/_latestVersion)
[![License](https://img.shields.io/github/license/Netflix/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Issues](https://img.shields.io/github/issues/Netflix/conductor.svg)](https://github.com/Netflix/conductor/issues)
[![NetflixOSS Lifecycle](https://img.shields.io/osslifecycle/Netflix/conductor.svg)]()

## Community
[![Gitter](https://badges.gitter.im/netflix-conductor/community.svg)](https://gitter.im/netflix-conductor/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge) Please feel free to join our Gitter for questions and interacting with the community.

[Access here other Conductor related projects made by the community!](/RELATED.md) - Backup tool, Cron like workflow starter, Docker containers...

## Builds
Conductor builds are run on Travis CI [here](https://travis-ci.org/Netflix/conductor).

| Branch |                                                     Build                                                     |                                                                 Coverage (coveralls.io)                                                                |                                                        Coverage (codecov.io)                                                       |
|:------:|:-------------------------------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------:|
| master | [![Build Status](https://travis-ci.org/Netflix/conductor.svg?branch=master)](https://travis-ci.org/Netflix/conductor) | [![Coverage Status](https://coveralls.io/repos/github/Netflix/conductor/badge.svg?branch=master)](https://coveralls.io/github/Netflix/conductor?branch=master) | [![codecov](https://codecov.io/gh/Netflix/conductor/branch/master/graph/badge.svg)](https://codecov.io/gh/Netflix/conductor/branch/master) |
| dev | [![Build Status](https://travis-ci.org/Netflix/conductor.svg?branch=dev)](https://travis-ci.org/Netflix/conductor) | [![Coverage Status](https://coveralls.io/repos/github/Netflix/conductor/badge.svg?branch=dev)](https://coveralls.io/github/Netflix/conductor?branch=dev) | [![codecov](https://codecov.io/gh/Netflix/conductor/branch/dev/graph/badge.svg)](https://codecov.io/gh/Netflix/conductor/branch/dev) |

## Documentation & Getting Started
[http://netflix.github.io/conductor/](http://netflix.github.io/conductor/)

[Getting Started](https://netflix.github.io/conductor/gettingstarted/basicconcepts/) guide.

## Get Conductor
Binaries are available from Maven Central and jcenter.

Below are the various artifacts published:

|Artifact|Description|
|-----------|---------------|
|conductor-common|Common models used by various conductor modules|
|conductor-core|Core Conductor module|
|conductor-redis-persistence|Persistence using Redis/Dynomite|
|conductor-es5-persistence|Indexing using Elasticsearch 5.X|
|conductor-jersey|Jersey JAX-RS resources for the core services|
|conductor-ui|node.js based UI for Conductor|
|conductor-contribs|Optional contrib package that holds extended workflow tasks and support for SQS|
|conductor-client|Java client for Conductor that includes helpers for running a worker tasks|
|conductor-server|Self contained Jetty server|
|conductor-test-harness|Used for building test harness and an in-memory kitchensink demo|

## Building
To build the server, use the following dependencies in your classpath:

* conductor-common
* conductor-core
* conductor-jersey
* conductor-redis-persistence (_unless using your own persistence module_)
* conductor-es5-persistence (_unless using your own index module_)
* conductor-contribs (_optional_)


### Deploying Jersey JAX-RS resources
Add the following packages to classpath scan:

```java
com.netflix.conductor.server.resources
com.netflix.workflow.contribs.queue
```
Conductor relies on the guice (4.0+) for the dependency injection.
Persistence has a guice module to wire up appropriate interfaces:

```java
com.netflix.conductor.dao.RedisWorkflowModule
```
## Database Requirements

* The default persistence used is [Dynomite](https://github.com/Netflix/dynomite)
* For queues, we are relying on [dyno-queues](https://github.com/Netflix/dyno-queues)
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (5.x)

## Other Requirements
* JDK 1.8+
* Servlet Container

## Get Support
Conductor is maintained by Media Workflow Infrastructure team at Netflix.  Use github issue tracking for any support request. 

## Contributions
Whether it is a small doc correction, bug fix or adding new module to support some crazy feature, contributions are highly appreciated. We just ask to follow standard oss guidelines. And to reiterate, please check with us before spending too much time, only to find later that someone else is already working on similar feature. 

`dev` branch is the current working branch, while `master` branch is current stable branch. Please send your PR's to `dev` branch, making sure that it builds on your local system successfully. Also, please make sure all the conflicts are resolved.

Feel free to create an issue with a label: question, with any questions or requests for help.

## License
Copyright 2018 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
