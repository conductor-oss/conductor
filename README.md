![Conductor](docs/docs/img/conductor-vector-x.png)

## Conductor
Conductor is an _orchestration_ engine that runs in the cloud.

[![Build Status](https://travis-ci.org/Netflix/conductor.svg?branch=master)](https://travis-ci.org/Netflix/conductor)



## Documentation & Getting Started
[http://netflix.github.io/conductor/](http://netflix.github.io/conductor/)

[Getting Started](http://netflix.github.io/conductor/intro) guide.

## Get Conductor
Binaries are available from Maven Central and jcenter.

|Group|Artifact|Latest Stable Version|
|-----------|---------------|---------------------|
|com.netflix.conductor|conductor-*|1.5.+|

Below are the various artifacts published:

|Artifact|Description|
|-----------|---------------|
|conductor-common|Common models used by various conductor modules|
|conductor-core|Core Conductor module|
|conductor-redis-persistence|Persistence using Redis/Dynomite and Elasticsearch|
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
com.netflix.conductor.dao.RedisESWorkflowModule
```
## Database Requirements

* The default persistence used is [Dynomite](https://github.com/Netflix/dynomite)
* For queues, we are relying on [dyno-queues](https://github.com/Netflix/dyno-queues)
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (2.+)

## Other Requirements
* JDK 1.8+
* Servlet Container

## Get Support
Conductor is maintained by Content Platform Engineering team at Netflix.  Use github issue tracking for any support request.  

## LICENSE

Copyright (c) 2016 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
