# Redis

By default conductor runs with an in-memory Redis mock. However, you
can change the configuration by setting the properties `conductor.db.type` and `conductor.redis.hosts`.

## `conductor.db.type`

| Value                          | Description                                                                            |
|--------------------------------|----------------------------------------------------------------------------------------|
| dynomite                       | Dynomite Cluster. Dynomite is a proxy layer that provides sharding and replication.    |
| memory                         | Uses an in-memory Redis mock. Should be used only for development and testing purposes.|
| redis_cluster                  | Redis Cluster configuration.                                                           |
| redis_sentinel                 | Redis Sentinel configuration.                                                          |
| redis_standalone               | Redis Standalone configuration.                                                        |



## `conductor.redis.hosts`

Expected format is `host:port:rack` separated by semicolon, e.g.: 

```properties
conductor.redis.hosts=host0:6379:us-east-1c;host1:6379:us-east-1c;host2:6379:us-east-1c
```

### Auth Support

Password authentication is supported. The password should be set as the 4th param of the first host `host:port:rack:password`, e.g.:

```properties
conductor.redis.hosts=host0:6379:us-east-1c:my_str0ng_pazz;host1:6379:us-east-1c;host2:6379:us-east-1c
```


**Notes**

- In a cluster, all nodes use the same password.
- In a sentinel configuration, sentinels and redis nodes use the same password.
