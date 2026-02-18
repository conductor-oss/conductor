# OpenSearch 2.x Persistence

This module provides OpenSearch 2.x persistence for indexing workflows and tasks in Conductor.

## Overview

The `os-persistence-v2` module targets OpenSearch 2.x clusters (2.0 through 2.18+). It uses the
`opensearch-java 2.18.0` client with dependency shading to prevent classpath conflicts when the
server is deployed alongside the `os-persistence-v3` module.

## Configuration

Set the following properties to enable OpenSearch 2.x indexing:

```properties
conductor.indexing.enabled=true
conductor.indexing.type=opensearch2

# URL of the OpenSearch cluster (comma-separated for multiple nodes)
conductor.opensearch.url=http://localhost:9200

# Index prefix (default: conductor)
conductor.opensearch.indexPrefix=conductor
```

### All Configuration Properties

| Property | Default | Description |
|---|---|---|
| `conductor.opensearch.url` | `localhost:9201` | Comma-separated list of OpenSearch node URLs. Supports `http://` and `https://` schemes. |
| `conductor.opensearch.indexPrefix` | `conductor` | Prefix used when creating indices. |
| `conductor.opensearch.clusterHealthColor` | `green` | Cluster health color to wait for before starting (`green`, `yellow`). |
| `conductor.opensearch.indexBatchSize` | `1` | Number of documents per batch when async indexing is enabled. |
| `conductor.opensearch.asyncWorkerQueueSize` | `100` | Size of the async indexing task queue. |
| `conductor.opensearch.asyncMaxPoolSize` | `12` | Maximum threads in the async indexing pool. |
| `conductor.opensearch.asyncBufferFlushTimeout` | `10s` | How long async buffers are held before being flushed. |
| `conductor.opensearch.indexShardCount` | `5` | Number of shards per index. |
| `conductor.opensearch.indexReplicasCount` | `0` | Number of replicas per index. |
| `conductor.opensearch.taskLogResultLimit` | `10` | Maximum task log entries returned per query. |
| `conductor.opensearch.restClientConnectionRequestTimeout` | `-1` | Connection request timeout in ms (`-1` = unlimited). |
| `conductor.opensearch.autoIndexManagementEnabled` | `true` | Whether Conductor creates and manages indices automatically. |
| `conductor.opensearch.username` | _(none)_ | Username for basic authentication. |
| `conductor.opensearch.password` | _(none)_ | Password for basic authentication. |

### Basic Authentication

To connect to a secured OpenSearch cluster:

```properties
conductor.opensearch.username=myuser
conductor.opensearch.password=mypassword
```

### Single-Node / Development Clusters

A single-node cluster cannot achieve `green` health because replica shards have nowhere to be
assigned. Set:

```properties
conductor.opensearch.clusterHealthColor=yellow
conductor.opensearch.indexReplicasCount=0
```

### External Index Management

If you manage OpenSearch indices externally (e.g., via ILM policies or Terraform):

```properties
conductor.opensearch.autoIndexManagementEnabled=false
```

## Migration from Legacy `opensearch` Type

If you previously used `conductor.indexing.type=opensearch`, update to `opensearch2`:

```properties
# Before
conductor.indexing.type=opensearch
conductor.elasticsearch.url=http://localhost:9200

# After
conductor.indexing.type=opensearch2
conductor.opensearch.url=http://localhost:9200
```

The `conductor.elasticsearch.*` namespace is still accepted for backward compatibility but is
deprecated. A warning is logged at startup when legacy properties are detected.

## Docker Compose

```shell
docker compose -f docker/docker-compose-redis-os2.yaml up
```

This starts Conductor, Redis, and OpenSearch 2.18.0.

## Dependency Isolation

OpenSearch 2.x and 3.x use identical Java package names (`org.opensearch.client.*`). This module
uses the [Shadow plugin](https://github.com/johnrengelman/shadow) to relocate all OpenSearch client
classes to an isolated namespace:

```
org.opensearch.client → org.conductoross.conductor.os2.shaded.opensearch.client
```

This allows both `os-persistence-v2` and `os-persistence-v3` to coexist on the same classpath
without conflicts.

## See Also

- [os-persistence-v3](../os-persistence-v3/README.md) — for OpenSearch 3.x clusters
- [OpenSearch configuration guide](../docs/documentation/advanced/opensearch.md)
- [Issue #678](https://github.com/conductor-oss/conductor/issues/678) — OpenSearch improvement epic
