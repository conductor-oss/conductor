---
description: "Redis Backend — configure Redis Standalone, Cluster, or Sentinel as the database and queue backend for Conductor."
---
# Redis

Configure Redis as the database and queue backend by setting the properties below.

## `conductor.db.type` and `conductor.queue.type`

| Value                          | Description                                                                            |
|--------------------------------|----------------------------------------------------------------------------------------|
| redis_standalone               | Redis Standalone configuration.                                                        |
| redis_cluster                  | Redis Cluster configuration.                                                           |
| redis_sentinel                 | Redis Sentinel configuration.                                                          |

## `conductor.redis.hosts`

Expected format is `host:port:rack` separated by semicolon, e.g.: 

```properties
conductor.redis.hosts=host0:6379:us-east-1c;host1:6379:us-east-1c;host2:6379:us-east-1c
```

## `conductor.redis.database`
Redis database value other than default of 0 is supported in sentinel and standalone configurations. 
Redis cluster mode only uses database 0, and the configuration is ignored.

```properties
conductor.redis.database=1
```


## `conductor.redis.username`

[Redis ACL](https://redis.io/docs/latest/operate/oss_and_stack/management/security/acl/) using username and password authentication is now supported. 

The username property should be set as `conductor.redis.username`, e.g.:
```properties
conductor.redis.username=conductor
```
If not set, the client uses `default` as the username.

The password should be set as the 4th param of the first host `host:port:rack:password`, e.g.:

```properties
conductor.redis.hosts=host0:6379:us-east-1c:my_str0ng_pazz;host1:6379:us-east-1c;host2:6379:us-east-1c
```

**Notes**

- In a cluster, all nodes use the same username and password.
- In a sentinel configuration, sentinels and redis nodes use the same database index, username, and password.

## Valkey compatibility

Conductor's Redis backend (Jedis 6.0.0, Redisson 3.22.0) communicates using standard RESP commands only — no Redis modules (RedisSearch, RedisJSON, etc.) are used. This means [Valkey](https://valkey.io), the open-source, BSD-3-licensed fork of Redis, works as a drop-in replacement, including managed offerings such as AWS ElastiCache for Valkey and GCP Memorystore for Valkey.

No new configuration is required. Point `conductor.redis.hosts` at your Valkey endpoint(s); `conductor.db.type` stays `redis_standalone`, `redis_cluster`, or `redis_sentinel` — these values select the wire protocol Conductor speaks, not the specific product serving it.

Redisson, which backs Conductor's distributed locking, [officially supports Valkey 7.2.5 and above](https://github.com/redisson/redisson).
