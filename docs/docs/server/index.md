# Instaling

### Requirements

1. **Database**: [Dynomite](https://github.com/Netflix/dynomite)
2. **Indexing Backend**: [Elasticsearch 2.x](https://www.elastic.co)
2. **Servlet Container**: Tomcat, Jetty, or similar running JDK 1.8 or higher

There are 3 ways in which you can install Conductor:

#### 1. Build from source
To build from source, checkout the code from github and build server module using ```gradle build``` command.  This should produce a conductor-server-all-VERSION.jar.
The jar can be executed using:
```shell
java -jar conductor-server-all-VERSION.jar
```
 
#### 2. Download pre-built binaries from jcenter or maven central
Use the following coordiates:

|group|artifact|version
|---|---|---|
|com.netflix.conductor|conductor-server-all|1.6.+|

#### 3. Use the pre-configured Docker image
Coming soon...

# Configuration
Conductor server uses a property file based configuration.  The property file is passed to the Main class as a command line argument.

```shell
java -jar conductor-server-all-VERSION.jar [PATH TO PROPERTY FILE] [log4j.properties file path]
```
log4j.properties file path is optional and allows finer control over the logging (defaults to INFO level logging in the console).

### Configuration Parameters
```properties

# Database persistence model.  Possible values are memory, redis, and dynomite.
# If ommitted, the persistence used is memory
#
# memory : The data is stored in memory and lost when the server dies.  Useful for testing or demo
# redis : non-Dynomite based redis instance
# dynomite : Dynomite cluster.  Use this for HA configuration.
db=dynomite

# Dynomite Cluster details.
# format is host:port:rack separated by semicolon  
workflow.dynomite.cluster.hosts=host1:8102:us-east-1c;host2:8102:us-east-1d;host3:8102:us-east-1e

# Dynomite cluster name
workflow.dynomite.cluster.name=dyno_cluster_name

# Namespace for the keys stored in Dynomite/Redis 
workflow.namespace.prefix=conductor

# Namespace prefix for the dyno queues
workflow.namespace.queue.prefix=conductor_queues

# No. of threads allocated to dyno-queues (optional)
queues.dynomite.threads=10

# Non-quorum port used to connect to local redis.  Used by dyno-queues.
# When using redis directly, set this to the same port as redis server
# For Dynomite, this is 22122 by default or the local redis-server port used by Dynomite.
queues.dynomite.nonQuorum.port=22122

# Transport address to elasticsearch
workflow.elasticsearch.url=localhost:9300

# Name of the elasticsearch cluster
workflow.elasticsearch.index.name=conductor

# Additional modules (optional)
conductor.additional.modules=class_extending_com.google.inject.AbstractModule

```
# High Availability Configuration

Conductor servers are stateless and can be deployed on multiple servers to handle scale and availability needs.  The scalability of the server is achieved by scaling the [Dynomite](https://github.com/Netflix/dynomite) cluster along with [dyno-queues](https://github.com/Netflix/dyno-queues) which is used for queues.

Clients connects to the server via HTTP load balancer or using Discovery (on NetflixOSS stack).


