# Redis Configuration Module

Redis connection and configuration layer for Conductor. Provides the `JedisCommands` abstraction, connection pooling, Spring auto-configuration, and pool monitoring. Supports three deployment topologies: standalone, cluster, and sentinel.

This module is a dependency of `redis-persistence` (DAOs) and `queues`. If you only need a Redis connection without the DAO layer, depend on this module directly.

## Deployment Modes

Set `conductor.db.type` to activate a Redis mode:

| Value | Mode | Configuration Class | Jedis Client |
|---|---|---|---|
| `redis_standalone` | Single node | `RedisStandaloneConfiguration` | `JedisPooled` |
| `redis_cluster` | Cluster (sharded) | `RedisClusterConfiguration` | `JedisCluster` |
| `redis_sentinel` | Sentinel (HA failover) | `RedisSentinelConfiguration` | `JedisSentineled` |

## Quick Start

### Standalone

```properties
conductor.db.type=redis_standalone
conductor.redis.hosts=localhost:6379:us-east-1c
```

### Cluster

```properties
conductor.db.type=redis_cluster
conductor.redis.hosts=node1:6379:us-east-1a;node2:6379:us-east-1b;node3:6379:us-east-1c
```

### Sentinel

```properties
conductor.db.type=redis_sentinel
conductor.redis.hosts=sentinel1:26379:us-east-1a;sentinel2:26379:us-east-1b;sentinel3:26379:us-east-1c
conductor.redis.sentinel-master-name=mymaster
```

## Host Format

The `conductor.redis.hosts` property uses a semicolon-separated format:

```
host:port:rack[:password]
```

- **host** - hostname or IP
- **port** - Redis port (6379 for data nodes, 26379 for sentinel nodes)
- **rack** - availability zone / rack identifier (e.g., `us-east-1a`)
- **password** - (optional) Redis AUTH password

Multiple hosts are separated by `;`:

```properties
conductor.redis.hosts=host1:6379:rack1:secret;host2:6379:rack2:secret
```

## Configuration Properties

All properties are prefixed with `conductor.redis.`.

### Connection

| Property | Description | Default |
|---|---|---|
| `hosts` | Host definitions (see format above) | *required* |
| `user` | Redis ACL username | none |
| `ssl` | Enable TLS | `false` |
| `ignore-ssl` | Trust all certificates (cluster mode only, for dev/test) | `false` |
| `database` | Redis database number (0-15, standalone/sentinel only) | `0` |
| `sentinel-master-name` | Sentinel master name (sentinel mode only) | `mymaster` |

### Connection Pool

| Property | Description | Default |
|---|---|---|
| `max-connections-per-host` | Maximum total connections in the pool | `10` |
| `max-idle-connections` | Maximum idle connections | `8` |
| `min-idle-connections` | Minimum idle connections maintained | `5` |
| `min-evictable-idle-time-millis` | Time before an idle connection can be evicted | `180000` |
| `time-between-eviction-runs-millis` | Interval between eviction runs | `60000` |
| `test-while-idle` | Validate idle connections | `true` |
| `fairness` | Use fair ordering for connection acquisition | `true` |
| `max-timeout-when-exhausted` | Max wait for a connection when pool is exhausted | `800ms` |

### Cluster-Specific

| Property | Description | Default |
|---|---|---|
| `max-total-retries-duration` | Maximum total retry duration for cluster operations | `10000ms` |

## Architecture

### `config` package

- **`RedisConfiguration`** - Abstract base. Creates the `UnifiedJedis` bean and runs a pool monitor thread that publishes connection metrics every 10 seconds.
- **`RedisStandaloneConfiguration`** - Creates a `JedisPooled` instance.
- **`RedisClusterConfiguration`** - Creates a `JedisCluster` instance. Supports `ignore-ssl` for trust-all TLS in dev environments.
- **`RedisSentinelConfiguration`** - Creates a `JedisSentineled` instance. Sentinel nodes reuse the configured auth and SSL settings so existing secured Sentinel deployments continue to work.
- **`RedisProperties`** - Spring Boot `@ConfigurationProperties` binding for all `conductor.redis.*` properties.
- **`ConfigurationHostSupplier`** - Parses the `hosts` string into `Host` objects.
- **`AnyRedisCondition`** - Spring condition that matches when `conductor.db.type` is any Redis variant.
- **`AnyRedisConnectionCondition`** - Matches when Redis is used for either `conductor.db.type` or `conductor.queue.type`.

### `jedis` package

- **`JedisCommands`** - Interface abstracting Redis operations across all deployment modes.
- **`UnifiedJedisCommands`** - Implementation backed by `UnifiedJedis` (used by standalone and sentinel modes).
- **`JedisClusterCommands`** - Implementation backed by `JedisCluster`. Batch operations (`mget`, `mgetBytes`, `mset`) use `ClusterPipeline` for cross-shard efficiency.
- **`JedisStandalone`** - Legacy implementation backed by `JedisPool`. Retained for test compatibility.
- **`OrkesJedisProxy`** - Spring-managed proxy over `JedisCommands`. Adds convenience methods (`hgetAll`, `findAll`, `setWithExpiry`, etc.), scan-based iteration, and metrics instrumentation.

## Pool Monitoring

All modes publish Redis connection pool metrics every 10 seconds via a daemon thread:

- `conductor_redis_connection_active` - active connections
- `conductor_redis_connection_waiting` - threads waiting for a connection
- `conductor_redis_connection_mean_borrow_wait_time` - average time to acquire a connection
- `conductor_redis_connection_max_borrow_wait_time` - worst-case connection acquisition time

The monitor is automatically shut down on Spring context close.
