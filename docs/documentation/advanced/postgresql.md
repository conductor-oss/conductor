# PostgreSQL

By default conductor runs with an in-memory Redis mock. However, you
can run Conductor against PostgreSQL which provides workflow management, queues and indexing.
There are a number of configuration options that enable you to use more or less of PostgreSQL functionality for your needs.
It has the benefit of requiring fewer moving parts for the infrastructure, but does not scale as well to handle high volumes of workflows.
You should benchmark Conductor with Postgres against your specific workload to be sure.


## Configuration

To enable the basic use of PostgreSQL to manage workflow metadata, set the following property:

```properties
conductor.db.type=postgres
spring.datasource.url=jdbc:postgresql://postgres:5432/conductor
spring.datasource.username=conductor
spring.datasource.password=password
# optional
conductor.postgres.schema=public
```

To also use PostgreSQL for queues, you can set:

```properties
conductor.queue.type=postgres
```

You can also use PostgreSQL to index workflows, configure this as follows:

```properties
conductor.indexing.enabled=true
conductor.indexing.type=postgres
conductor.elasticsearch.version=0
```

By default, Conductor writes the latest poll for tasks to the database so that it can be used to determine which tasks and domains are active. This creates a lot of database traffic.
To avoid some of this traffic you can configure the PollDataDAO with a write buffer so that it only flushes every x milliseconds. If you keep this value around 5s then there should be no impact on behaviour. Conductor uses a default duration of 10s to determine whether a queue for a domain is active or not (also configurable using `conductor.app.activeWorkerLastPollTimeout`) so this will ensure that there is plenty of time for the data to get to the database to be shared by other instances:

```properties
# Flush the data every 5 seconds
conductor.postgres.pollDataFlushInterval=5000
```

You can also configure a duration when the cached poll data will be considered stale. This means that the PollDataDAO will try to use the cached data, but if it is older than the configured period, it will check against the database. There is no downside to setting this as if this Conductor node already can confirm that the queue is active then there's no need to go to the database. If the record in the cache is out of date, then we still go to the database to check.

```properties
# Data older than 5 seconds is considered stale
conductor.postgres.pollDataCacheValidityPeriod=5000
```
