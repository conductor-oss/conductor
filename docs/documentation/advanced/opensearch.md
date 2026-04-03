# OpenSearch

Conductor supports OpenSearch as an indexing backend for searching workflows and tasks via the UI.
Version-specific modules are provided for OpenSearch 2.x and 3.x.

## Quick Start

Choose the module that matches your OpenSearch cluster version and set `conductor.indexing.type`:

```properties
# For OpenSearch 2.x
conductor.indexing.enabled=true
conductor.indexing.type=opensearch2
conductor.opensearch.url=http://localhost:9200

# For OpenSearch 3.x
conductor.indexing.enabled=true
conductor.indexing.type=opensearch3
conductor.opensearch.url=http://localhost:9200
```

Conductor will create its indices on first startup and begin indexing workflows and tasks.

## Supported Versions

| Module | `conductor.indexing.type` | OpenSearch Version | Client Library |
|---|---|---|---|
| `os-persistence-v2` | `opensearch2` | 2.x (2.0 – 2.18+) | opensearch-java 2.18.0 |
| `os-persistence-v3` | `opensearch3` | 3.x (3.0+) | opensearch-java 3.0.0 |

OpenSearch 1.x is no longer supported. If you need 1.x support, see the
[archived os-persistence-v1 module](https://github.com/conductor-oss/conductor-os-persistence-v1).

## Configuration Reference

All OpenSearch configuration uses the `conductor.opensearch.*` namespace. Both the v2 and v3
modules share the same property names — only `conductor.indexing.type` differs.

### Connection

| Property | Default | Description |
|---|---|---|
| `conductor.opensearch.url` | `localhost:9201` | Comma-separated OpenSearch node URLs. HTTP and HTTPS are both supported. |
| `conductor.opensearch.username` | _(none)_ | Username for basic authentication. |
| `conductor.opensearch.password` | _(none)_ | Password for basic authentication. |

Multi-node example:

```properties
conductor.opensearch.url=http://os-node1:9200,http://os-node2:9200,http://os-node3:9200
```

### Index Management

| Property | Default | Description |
|---|---|---|
| `conductor.opensearch.indexPrefix` | `conductor` | Prefix for all Conductor-managed indices. |
| `conductor.opensearch.indexShardCount` | `5` | Primary shards per index. |
| `conductor.opensearch.indexReplicasCount` | `0` | Replica shards per index. |
| `conductor.opensearch.autoIndexManagementEnabled` | `true` | Whether Conductor creates and manages indices automatically. Set to `false` to manage indices externally. |
| `conductor.opensearch.clusterHealthColor` | `green` | Cluster health color Conductor waits for before starting. Use `yellow` for single-node clusters. |

### Performance Tuning

| Property | Default | Description |
|---|---|---|
| `conductor.opensearch.indexBatchSize` | `1` | Documents per batch in async mode. |
| `conductor.opensearch.asyncWorkerQueueSize` | `100` | Async indexing task queue depth. |
| `conductor.opensearch.asyncMaxPoolSize` | `12` | Maximum async indexing threads. |
| `conductor.opensearch.asyncBufferFlushTimeout` | `10s` | Maximum time an async buffer is held before flushing. |
| `conductor.opensearch.taskLogResultLimit` | `10` | Maximum task log entries returned per search. |
| `conductor.opensearch.restClientConnectionRequestTimeout` | `-1` | REST client connection request timeout in ms. `-1` means unlimited. |

## Example Configurations

### Development (single-node, no auth)

```properties
conductor.indexing.enabled=true
conductor.indexing.type=opensearch2
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor
conductor.opensearch.indexReplicasCount=0
conductor.opensearch.clusterHealthColor=yellow
```

### Production (multi-node, auth, OpenSearch 2.x)

```properties
conductor.indexing.enabled=true
conductor.indexing.type=opensearch2
conductor.opensearch.url=https://os-node1:9200,https://os-node2:9200,https://os-node3:9200
conductor.opensearch.username=conductor_user
conductor.opensearch.password=secure_password
conductor.opensearch.indexPrefix=conductor
conductor.opensearch.indexShardCount=5
conductor.opensearch.indexReplicasCount=1
conductor.opensearch.clusterHealthColor=green
conductor.opensearch.asyncWorkerQueueSize=500
conductor.opensearch.asyncMaxPoolSize=24
conductor.opensearch.indexBatchSize=10
```

### OpenSearch 3.x

```properties
conductor.indexing.enabled=true
conductor.indexing.type=opensearch3
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor
conductor.opensearch.indexReplicasCount=0
conductor.opensearch.clusterHealthColor=yellow
```

## Running with Docker Compose

Pre-built Docker Compose configurations are provided for both versions:

```shell
# OpenSearch 2.x
docker compose -f docker/docker-compose-redis-os2.yaml up

# OpenSearch 3.x
docker compose -f docker/docker-compose-redis-os3.yaml up
```

Both start Conductor, Redis, and the appropriate OpenSearch version.

## Migrating from the Legacy `opensearch` Type

The generic `conductor.indexing.type=opensearch` is deprecated. Starting the server with this
value will display an error message directing you to the new configuration.

**Before:**

```properties
conductor.indexing.type=opensearch
conductor.elasticsearch.url=http://localhost:9200
conductor.elasticsearch.indexName=conductor
```

**After:**

```properties
conductor.indexing.type=opensearch2   # or opensearch3
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor
```

The `conductor.elasticsearch.*` namespace is still accepted for backward compatibility. When
detected, those values are used and a deprecation warning is logged at startup. Migrate to
`conductor.opensearch.*` before the next major release.

### Legacy property mapping

| Legacy (`conductor.elasticsearch.*`) | New (`conductor.opensearch.*`) |
|---|---|
| `url` | `url` |
| `indexName` | `indexPrefix` |
| `clusterHealthColor` | `clusterHealthColor` |
| `indexBatchSize` | `indexBatchSize` |
| `asyncWorkerQueueSize` | `asyncWorkerQueueSize` |
| `asyncMaxPoolSize` | `asyncMaxPoolSize` |
| `indexShardCount` | `indexShardCount` |
| `indexReplicasCount` | `indexReplicasCount` |
| `taskLogResultLimit` | `taskLogResultLimit` |
| `username` | `username` |
| `password` | `password` |

## Disabling Indexing

To run Conductor without search indexing (disables workflow search in the UI):

```properties
conductor.indexing.enabled=false
```

## Troubleshooting

### Conductor fails to start: cluster health timeout

For single-node development clusters, set:

```properties
conductor.opensearch.clusterHealthColor=yellow
```

A single-node cluster cannot achieve `green` health because replica shards cannot be assigned.

### Conductor fails to start: `NoClassDefFoundError: org.opensearch.Version`

This error occurred with older `os-persistence` module versions and is resolved in the current
versioned modules. Ensure `conductor.indexing.type` is set to `opensearch2` or `opensearch3`.

### Configuration changes not taking effect in Docker

Config files are baked into the Docker image at build time. After changing `config-*.properties`:

```shell
docker compose -f docker/docker-compose-redis-os2.yaml build
docker compose -f docker/docker-compose-redis-os2.yaml up
```

Alternatively, mount the config file as a Docker volume to pick up changes without rebuilding.

## See Also

- [os-persistence-v2 README](../../os-persistence-v2/README.md)
- [os-persistence-v3 README](../../os-persistence-v3/README.md)
- [Issue #678](https://github.com/conductor-oss/conductor/issues/678) — OpenSearch improvement epic
- [OpenSearch documentation](https://opensearch.org/docs/latest/)
