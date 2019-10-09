# ES6 Persistence

This module provides ES6 persistence when indexing workflows and tasks.

## Build

In order to use the ES6, you must change the following files from ES5 to ES6:


https://github.com/Netflix/conductor/blob/master/server/build.gradle
https://github.com/Netflix/conductor/blob/master/settings.gradle
https://github.com/Netflix/conductor/blob/master/test-harness/build.gradle

In files:
- /server/build.gradle
- /settings.gradle

change module inclusion from 'es5-persistence' to 'es6-persistence'


In file:
 
- /test-harness/build.gradle

change org.elasticsearch:elasticsearch:${revElasticSearch5} dependency version from ${revElasticSearch5} to ${revElasticSearch6}


Also you need to recreate dependencies.lock files with ES6 dependencies. To do that delete all dependencies.lock files and then run: 

```
./gradlew generateLock updateLock saveLock
```

## Usage

This module uses the following configuration options:

* `workflow.elasticsearch.instanceType` - This determines the type of ES instance we are using with conductor.
The two values are either `MEMORY` or `EXTERNAL`.
If `MEMORY`, then an embedded server will be run.
Default is `MEMORY`.
* `workflow.elasticsearch.url` - A comma separated list of schema/host/port of the ES nodes to communicate with.
Schema can be ignored when using `tcp` transport; otherwise, you must specify `http` or `https`.
If using the `http` or `https`, then conductor will use the REST transport protocol.
* `workflow.elasticsearch.index.name` - The name of the workflow and task index.
Defaults to `conductor`
* `workflow.elasticsearch.tasklog.index.name` - The name of the task log index.
Defaults to `task_log`
* `workflow.elasticsearch.async.dao.worker.queue.size` - Worker Queue size used in executor service for async methods in IndexDao 
Defaults to `100`
* `workflow.elasticsearch.async.dao.max.pool.size` - Maximum thread pool size in executor service for async methods in IndexDao        
Defaults to `12`
* `workflow.elasticsearch.async.buffer.flush.timeout.seconds` - Timeout (in seconds) for the in-memory to be flushed if not explicitly indexed
Defaults to `10`

### Embedded Configuration

If `workflow.elasticsearch.instanceType=MEMORY`, then you can configure the embedded server using the following configurations: 

* `workflow.elasticsearch.embedded.port` - The starting port of the embedded server.
This is the port used for the TCP transport.
It will also use this + 100 in order to setup the http transport.
Default is `9200`
* `workflow.elasticsearch.embedded.cluster.name` - The name of the embedded cluster name.
Default is `elasticsearch_test`
* `workflow.elasticsearch.embedded.host` - The host of the embedded server.
Default is `127.0.0.1`

### REST Transport

If you are using AWS ElasticSearch, you should use the `rest` transport as that's the only version transport that they support.
However, this module currently only works with open IAM, VPC version of ElasticSearch.
Eventually, we should create ES modules that can be loaded in to support authentication and request signing, but this currently does not support that.

### Example Configurations

**In-memory ES with TCP transport**

```
workflow.elasticsearch.instanceType=MEMORY
```

**In-memory ES with REST transport**

```
workflow.elasticsearch.instanceType=MEMORY
workflow.elasticsearch.url=http://localhost:9300
```

**ES with TCP transport**

```
workflow.elasticsearch.instanceType=EXTERNAL
workflow.elasticsearch.url=127.0.0.1:9300
```

**ES with REST transport**

```
workflow.elasticsearch.instanceType=EXTERNAL
workflow.elasticsearch.url=http://127.0.0.1:9200
```
