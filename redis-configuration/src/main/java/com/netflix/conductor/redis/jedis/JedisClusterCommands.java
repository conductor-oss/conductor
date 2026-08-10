/*
 * Copyright 2020 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redis.jedis;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import redis.clients.jedis.ClusterPipeline;
import redis.clients.jedis.Connection;
import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Response;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;
import redis.clients.jedis.util.SafeEncoder;

public class JedisClusterCommands implements JedisCommands {

    private final JedisCluster jedisCluster;

    public JedisClusterCommands(redis.clients.jedis.JedisCluster jedisCluster) {
        this.jedisCluster = jedisCluster;
    }

    @Override
    public String set(String key, String value) {
        return jedisCluster.set(key, value);
    }

    @Override
    public String set(String key, String value, SetParams params) {
        return jedisCluster.set(key, value, params);
    }

    public String set(byte[] key, byte[] value, SetParams params) {
        return jedisCluster.set(key, value, params);
    }

    @Override
    public String get(String key) {
        return jedisCluster.get(key);
    }

    @Override
    public Boolean exists(String key) {
        return jedisCluster.exists(key);
    }

    public ClusterPipeline pipe(String key) {
        return jedisCluster.pipelined();
    }

    @Override
    public Long persist(String key) {
        return jedisCluster.persist(key);
    }

    @Override
    public Long expire(String key, long seconds) {
        return jedisCluster.expire(key, seconds);
    }

    @Override
    public Long ttl(String key) {
        return jedisCluster.ttl(key);
    }

    @Override
    public Long setnx(String key, String value) {
        return jedisCluster.setnx(key, value);
    }

    @Override
    public Long incrBy(String key, long integer) {
        return jedisCluster.incrBy(key, integer);
    }

    @Override
    public Long hset(String key, String field, String value) {
        return jedisCluster.hset(key, field, value);
    }

    @Override
    public Long hset(String key, Map<String, String> hash) {
        return jedisCluster.hset(key, hash);
    }

    @Override
    public String hget(String key, String field) {
        return jedisCluster.hget(key, field);
    }

    @Override
    public Long hsetnx(String key, String field, String value) {
        return jedisCluster.hsetnx(key, field, value);
    }

    @Override
    public Long hincrBy(String key, String field, long value) {
        return jedisCluster.hincrBy(key, field, value);
    }

    @Override
    public Boolean hexists(String key, String field) {
        return jedisCluster.hexists(key, field);
    }

    @Override
    public Long hdel(String key, String... field) {
        return jedisCluster.hdel(key, field);
    }

    @Override
    public Long hlen(String key) {
        return jedisCluster.hlen(key);
    }

    @Override
    public List<String> hvals(String key) {
        return jedisCluster.hvals(key);
    }

    @Override
    public Long sadd(String key, String... member) {
        return jedisCluster.sadd(key, member);
    }

    @Override
    public Set<String> smembers(String key) {
        return jedisCluster.smembers(key);
    }

    @Override
    public Long srem(String key, String... member) {
        return jedisCluster.srem(key, member);
    }

    @Override
    public Long scard(String key) {
        return jedisCluster.scard(key);
    }

    @Override
    public Boolean sismember(String key, String member) {
        return jedisCluster.sismember(key, member);
    }

    @Override
    public Long zadd(String key, double score, String member) {
        return jedisCluster.zadd(key, score, member);
    }

    @Override
    public Long zadd(String key, Map<String, Double> scores) {
        return jedisCluster.zadd(key, scores);
    }

    @Override
    public Long zadd(String key, double score, String member, ZAddParams params) {
        return jedisCluster.zadd(key, score, member, params);
    }

    @Override
    public List<String> zrange(String key, long start, long end) {
        return jedisCluster.zrange(key, start, end);
    }

    @Override
    public Long zrem(String key, String... member) {
        return jedisCluster.zrem(key, member);
    }

    @Override
    public List<Tuple> zrangeWithScores(String key, long start, long end) {
        return jedisCluster.zrangeWithScores(key, start, end);
    }

    @Override
    public Long zcard(String key) {
        return jedisCluster.zcard(key);
    }

    @Override
    public Long zcount(String key, double min, double max) {
        return jedisCluster.zcount(key, min, max);
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max) {
        return jedisCluster.zrangeByScore(key, min, max);
    }

    @Override
    public Double zscore(String key, String messageId) {
        return jedisCluster.zscore(key, messageId);
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        return jedisCluster.zrangeByScore(key, min, max, offset, count);
    }

    @Override
    public List<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
        return jedisCluster.zrangeByScoreWithScores(key, min, max);
    }

    @Override
    public Long zremrangeByScore(String key, String start, String end) {
        return jedisCluster.zremrangeByScore(key, start, end);
    }

    @Override
    public Long del(String key) {
        return jedisCluster.del(key);
    }

    @Override
    public Long llen(String key) {
        return jedisCluster.llen(key);
    }

    @Override
    public Long rpush(String key, String... values) {
        return jedisCluster.rpush(key, values);
    }

    @Override
    public List<String> lrange(String key, long start, long end) {
        return jedisCluster.lrange(key, start, end);
    }

    @Override
    public String ltrim(String key, long start, long end) {
        return jedisCluster.ltrim(key, start, end);
    }

    @Override
    public ScanResult<Entry<String, String>> hscan(String key, String cursor) {
        return jedisCluster.hscan(key, cursor);
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(
            String key, String cursor, ScanParams params) {
        ScanResult<Map.Entry<byte[], byte[]>> scanResult =
                jedisCluster.hscan(key.getBytes(), cursor.getBytes(), params);
        List<Map.Entry<String, String>> results =
                scanResult.getResult().stream()
                        .map(
                                entry ->
                                        new AbstractMap.SimpleEntry<>(
                                                new String(entry.getKey()),
                                                new String(entry.getValue())))
                        .collect(Collectors.toList());
        return new ScanResult<>(scanResult.getCursorAsBytes(), results);
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor) {
        return jedisCluster.sscan(key, cursor);
    }

    @Override
    public ScanResult<String> scan(String keyPrefix, String cursor, int count) {
        return new ScanResult<String>(SafeEncoder.encode("0"), new ArrayList<>());
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        ScanResult<byte[]> scanResult =
                jedisCluster.sscan(key.getBytes(), cursor.getBytes(), params);
        List<String> results =
                scanResult.getResult().stream().map(String::new).collect(Collectors.toList());
        return new ScanResult<>(scanResult.getCursorAsBytes(), results);
    }

    @Override
    public ScanResult<Tuple> zscan(String key, String cursor) {
        return jedisCluster.zscan(key, cursor);
    }

    // new methods

    @Override
    public String set(byte[] key, byte[] value) {
        return jedisCluster.set(key, value);
    }

    public void mset(byte[]... keyvalues) {
        try (ClusterPipeline pipeline = jedisCluster.pipelined()) {
            for (int i = 0; i < keyvalues.length; i += 2) {
                pipeline.set(keyvalues[i], keyvalues[i + 1]);
            }
            pipeline.sync();
        }
    }

    @Override
    public byte[] getBytes(byte[] key) {
        return jedisCluster.get(key);
    }

    @Override
    public List<String> mget(String[] keys) {
        List<Response<String>> responses = new ArrayList<>(keys.length);
        try (ClusterPipeline pipeline = jedisCluster.pipelined()) {
            for (String key : keys) {
                responses.add(pipeline.get(key));
            }
            pipeline.sync();
        }
        List<String> values = new ArrayList<>(keys.length);
        for (Response<String> response : responses) {
            String value = response.get();
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    @Override
    public List<byte[]> mgetBytes(byte[]... keys) {
        List<Response<byte[]>> responses = new ArrayList<>(keys.length);
        try (ClusterPipeline pipeline = jedisCluster.pipelined()) {
            for (byte[] key : keys) {
                responses.add(pipeline.get(key));
            }
            pipeline.sync();
        }
        List<byte[]> values = new ArrayList<>(keys.length);
        for (Response<byte[]> response : responses) {
            byte[] value = response.get();
            if (value != null && value.length > 0) {
                values.add(value);
            }
        }
        return values;
    }

    @Override
    public Object evalsha(String sha1, List<String> keys, List<String> args) {
        return jedisCluster.evalsha(sha1, keys, args);
    }

    @Override
    public Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
        return jedisCluster.evalsha(sha1, keys, args);
    }

    @Override
    public byte[] scriptLoad(byte[] script, byte[] sampleKey) {
        return jedisCluster.scriptLoad(script, sampleKey);
    }

    @Override
    public String info(String command) {
        ConnectionPool pool = jedisCluster.getClusterNodes().values().stream().findAny().get();
        try (Connection connection = pool.getResource()) {
            return connection.executeCommand(Protocol.Command.INFO).toString();
        }
    }

    @Override
    public int waitReplicas(String key, int replicas, long timeoutInMillis) {
        Long replicated = jedisCluster.waitReplicas(key, replicas, timeoutInMillis);
        if (replicated == null) {
            return 0;
        }
        return replicated.intValue();
    }

    @Override
    public String ping() {
        return jedisCluster.ping();
    }
}
