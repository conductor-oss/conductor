# ES7 Persistence

This module provides ES7 persistence when indexing workflows and tasks.

## ES Breaking changes

From ES6 to ES7 there were significant breaking changes which affected ES7-persistence module implementation.
* Mapping type deprecation
* Templates API
* TransportClient deprecation

More information can be found here: https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html


## Build

In order to use the ES7, you must change the following files from ES6 to ES7:


https://github.com/Netflix/conductor/blob/master/server/build.gradle
https://github.com/Netflix/conductor/blob/master/settings.gradle
https://github.com/Netflix/conductor/blob/master/test-harness/build.gradle

In files:
- /server/build.gradle
- /settings.gradle

change module inclusion from 'es6-persistence' to 'es7-persistence'


In file:
 
- /test-harness/build.gradle

change org.elasticsearch:elasticsearch:${revElasticSearch6} dependency version from ${revElasticSearch6} to ${revElasticSearch7}


Also you need to recreate dependencies.lock files with ES7 dependencies. To do that delete all dependencies.lock files and then run: 

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
Schema can be `http` or `https`. If schema is ignored then `http` transport will be used;
Since ES deprecated TransportClient, conductor will use only the  REST transport protocol.
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


**In-memory ES with REST transport**

```
workflow.elasticsearch.instanceType=MEMORY
workflow.elasticsearch.url=http://localhost:9300
```

**ES with REST transport**

```
workflow.elasticsearch.instanceType=EXTERNAL
workflow.elasticsearch.url=http://127.0.0.1:9200
```

### BASIC Authentication
If you need to pass user/password to connect to ES, add the following properties to your config file
* workflow.elasticsearch.basic.auth.username
* workflow.elasticsearch.basic.auth.password

Example
```
workflow.elasticsearch.basic.auth.username=someusername
workflow.elasticsearch.basic.auth.password=somepassword
```
