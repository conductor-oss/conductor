## Installing

### Requirements

1. **Database**: [Dynomite](https://github.com/Netflix/dynomite)
2. **Indexing Backend**: [Elasticsearch 5.x](https://www.elastic.co)
2. **Servlet Container**: Tomcat, Jetty, or similar running JDK 1.8 or higher

There are 3 ways in which you can install Conductor:

#### 1. Build from source
To build from source, checkout the code from github and build server module using ```gradle build``` command. If you do not have gradle installed, you can run the command ```./gradlew build``` from the project root. This produces *conductor-server-all-VERSION.jar* in the folder *./server/build/libs/*

The jar can be executed using:
```shell
java -jar conductor-server-VERSION-all.jar
```

#### 2. Download pre-built binaries from jcenter or maven central
Use the following coordinates:

|group|artifact|version
|---|---|---|
|com.netflix.conductor|conductor-server-all|2.7.+|



#### 3. Use the pre-configured Docker image
To build the docker images for the conductor server and ui run the commands:
```shell
cd docker
docker-compose build
```

After the docker images are built, run the following command to start the containers:
```shell
docker-compose up
```

This will create a docker container network that consists of the following images: conductor:server, conductor:ui, [elasticsearch:5.6.8](https://hub.docker.com/_/elasticsearch/), and dynomite.

To view the UI, navigate to [localhost:5000](http://localhost:5000/), to view the Swagger docs, navigate to [localhost:8080](http://localhost:8080/).

## Configuration
Conductor server uses a property file based configuration.  The property file is passed to the Main class as a command line argument.

```shell
java -jar conductor-server-all-VERSION.jar [PATH TO PROPERTY FILE] [log4j.properties file path]
```
log4j.properties file path is optional and allows finer control over the logging (defaults to INFO level logging in the console).

#### Configuration Parameters
```properties

# Database persistence model.  Possible values are memory, redis, redis_cluster, redis_sentinel and dynomite.
# If omitted, the persistence used is memory
#
# memory : The data is stored in memory and lost when the server dies.  Useful for testing or demo
# redis : non-Dynomite based redis instance
# redis_cluster: AWS Elasticache Redis (cluster mode enabled).See [http://docs.aws.amazon.com/AmazonElastiCache/latest/UserGuide/Clusters.Create.CON.RedisCluster.html]
# redis_sentinel: Redis HA with Redis Sentinel. See [https://redis.io/topics/sentinel]
# dynomite : Dynomite cluster.  Use this for HA configuration.
db=dynomite

# Dynomite Cluster details.
# format is host:port:rack separated by semicolon
# for AWS Elasticache Redis (cluster mode enabled) the format is configuration_endpoint:port:us-east-1e. The region in this case does not matter
workflow.dynomite.cluster.hosts=host1:8102:us-east-1c;host2:8102:us-east-1d;host3:8102:us-east-1e

# If you are running using dynomite, also add the following line to the property 
# to set the rack/availability zone of the conductor server to be same as dynomite cluster config
EC2_AVAILABILTY_ZONE=us-east-1c

# Dynomite cluster name
workflow.dynomite.cluster.name=dyno_cluster_name

# Maximum connections to redis/dynomite
workflow.dynomite.connection.maxConnsPerHost=31

# Namespace for the keys stored in Dynomite/Redis
workflow.namespace.prefix=conductor

# Namespace prefix for the dyno queues
workflow.namespace.queue.prefix=conductor_queues

# No. of threads allocated to dyno-queues (optional)
queues.dynomite.threads=10

# Non-quorum port used to connect to local redis.  Used by dyno-queues.
# When using redis directly, set this to the same port as redis server.
# For Dynomite, this is 22122 by default or the local redis-server port used by Dynomite.
queues.dynomite.nonQuorum.port=22122

# Transport address to elasticsearch
# Specifying multiple node urls is not supported. specify one of the nodes' url, or a load balancer.
workflow.elasticsearch.url=localhost:9300

# Name of the elasticsearch cluster
workflow.elasticsearch.index.name=conductor

# Additional modules (optional)
conductor.additional.modules=class_extending_com.google.inject.AbstractModule

```

## High Availability Configuration

Conductor servers are stateless and can be deployed on multiple servers to handle scale and availability needs.  The scalability of the server is achieved by scaling the [Dynomite](https://github.com/Netflix/dynomite) cluster along with [dyno-queues](https://github.com/Netflix/dyno-queues) which is used for queues.

Clients connects to the server via HTTP load balancer or using Discovery (on NetflixOSS stack).

## Using Standalone Redis / ElastiCache

Conductor server can be used with a standlone Redis or ElastiCache server.  To configure the server, change the config to use the following:

```properties
db=redis

# For AWS Elasticache Redis (cluster mode enabled) the format is configuration_endpoint:port:us-east-1e.
# The region in this case does not matter
workflow.dynomite.cluster.hosts=server_address:server_port:us-east-1e
workflow.dynomite.connection.maxConnsPerHost=31

queues.dynomite.nonQuorum.port=server_port
```

## Setting up Zookeeper to enable Distributed Locking Service.

See [Technical Details](../technicaldetails/#maintaining-workflow-consistency-with-distributed-locking-and-fencing-tokens) for more details about this.

Locking Service is disabled by default. Enable this by setting:

```decider.locking.enabled: true```

Setup Zookeeper cluster connection string:

```zk.connection=1.2.3.4:2181,5.6.7.8:2181```

Optionally, configure the default timeouts:

```    
zk.sessionTimeoutMs
zk.connectionTimeoutMs
```

## Default Workflow Archiving Module Configuration

Conductor server does not perform automated workflow execution data cleaning by default. Archiving module (if enabled) 
removes all execution data from conductor persistence storage immediately upon workflow completion or termination, 
but keeps archived index data in elastic search. 

To benefit form archiving module you have to do the following:

### 1. Enable Archiving Module

Set property in server configuration.

```properties
# Comma-separated additional conductor modules
conductor.additional.modules=com.netflix.conductor.contribs.ArchivingWorkflowModule
```

### 2. Enable Workflow Status Listener

Archiving module is triggered only if workflow status listener is enabled on workflow definition level. To enable it 
you have to set `workflowStatusListenerEnabled` property to `true`. See sample workflow definition below:

```json
{
  "name": "e2e_approval_v4",
  "description": "Approval Process",
  "workflowStatusListenerEnabled": true, 
  "tasks": []
}
```
