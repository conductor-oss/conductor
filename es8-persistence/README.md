# ES8 Persistence

This module provides Elasticsearch 8.x persistence for indexing workflows and tasks.

It uses the Elasticsearch Java API Client (`elasticsearch-java`) aligned with ES8.

## Index management (ES8 best practices)

This module uses composable index templates, write aliases, and an ILM policy:

- **ILM policy:** `conductor-default-ilm-policy`
- **Rollover conditions (hot phase):** `max_primary_shard_size=50gb`, `max_age=30d`
- **Write aliases:** `${indexPrefix}_workflow`, `${indexPrefix}_task`, `${indexPrefix}_task_log`,
  `${indexPrefix}_message`, `${indexPrefix}_event`
- **Initial indices:** `${alias}-000001` (created automatically when index management is enabled)
- **Refresh interval:** defaults to `30s` for all indices (configurable)

## Build and usage

### 1) Build-time module selection

When building the server, select the ES8 persistence module to avoid Lucene conflicts:

```sh
./gradlew :conductor-server:bootJar -PindexingBackend=elasticsearch8
```

`-PindexingBackend=es8` is also accepted.

### 2) Runtime configuration

Set the Elasticsearch major version to 8 in your configuration:

```properties
conductor.elasticsearch.version=8
```

All other `conductor.elasticsearch.*` properties are shared with the ES7 module.

### Configuration

(Default values shown below)

```properties
# A comma separated list of schema/host/port of the ES nodes to communicate with.
# Schema can be `http` or `https`. If schema is ignored then `http` transport will be used;
# Since ES deprecated TransportClient, conductor will use only the REST transport protocol.
conductor.elasticsearch.url=

# The name of the workflow and task index.
conductor.elasticsearch.indexPrefix=conductor

# Default refresh interval applied via the component template.
conductor.elasticsearch.indexRefreshInterval=30s

# Worker queue size used in executor service for async methods in IndexDao.
conductor.elasticsearch.asyncWorkerQueueSize=100

# Maximum thread pool size in executor service for async methods in IndexDao
conductor.elasticsearch.asyncMaxPoolSize=12

# Timeout (in seconds) for the in-memory to be flushed if not explicitly indexed
conductor.elasticsearch.asyncBufferFlushTimeout=10
```

### BASIC Authentication

If you need to pass user/password to connect to ES, add the following properties to your
config file:

```
conductor.elasticsearch.username=someusername
conductor.elasticsearch.password=somepassword
```
