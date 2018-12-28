## Usage

### Using Elasticsearch as Index Data Source

While it is easy to plug-in any implementations of Indexing data source, Elasticsearch implementations are readily provided.
Configuring Elasticsearch to use with Conductor is as easy as setting up configuration parameters.

At the minimum, provide these options through VM or Config file:

`workflow.elasticsearch.url`
`workflow.elasticsearch.index.name`

### Database persistence model
Possible values are memory, redis, redis_cluster, redis_sentinel and dynomite.
If omitted, the persistence used is memory

memory : The data is stored in memory and lost when the server dies.  Useful for testing or demo
redis : non-Dynomite based redis instance
redis_cluster: AWS Elasticache Redis (cluster mode enabled).See [http://docs.aws.amazon.com/AmazonElastiCache/latest/UserGuide/Clusters.Create.CON.RedisCluster.html]
redis_sentinel: Redis HA with Redis Sentinel. See [https://redis.io/topics/sentinel]
dynomite : Dynomite cluster.  Use this for HA configuration.
`db=dynomite`

### Dynomite Cluster details
format is host:port:rack separated by semicolon
for AWS Elasticache Redis (cluster mode enabled) the format is configuration_endpoint:port:us-east-1e. The region in this case does not matter
`workflow.dynomite.cluster.hosts=host1:8102:us-east-1c;host2:8102:us-east-1d;host3:8102:us-east-1e`

### Dynomite cluster name
`workflow.dynomite.cluster.name=dyno_cluster_name`

### Maximum connections to redis/dynomite
`workflow.dynomite.connection.maxConnsPerHost=31`

### Namespace for the keys stored in Dynomite/Redis
`workflow.namespace.prefix=conductor`

### Namespace prefix for the dyno queues
`workflow.namespace.queue.prefix=conductor_queues`

### No. of threads allocated to dyno-queues (optional)
`queues.dynomite.threads=10`

### Non-quorum port used to connect to local redis.  Used by dyno-queues.
When using redis directly, set this to the same port as redis server
For Dynomite, this is 22122 by default or the local redis-server port used by Dynomite.
`queues.dynomite.nonQuorum.port=22122`

### Additional modules (optional)
`conductor.additional.modules=class_extending_com.google.inject.AbstractModule`


