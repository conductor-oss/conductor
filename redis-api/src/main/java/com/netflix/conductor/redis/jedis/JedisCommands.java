/*
 * Copyright 2026 Conductor Authors.
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

/** Common interface for sharded and non-sharded Jedis */
public interface JedisCommands {

    String set(String key, String value);

    String set(String key, String value, SetParams params);

    String set(byte[] key, byte[] value, SetParams params);

    String get(String key);

    Boolean exists(String key);

    Long persist(String key);

    Long expire(String key, long seconds);

    Long ttl(String key);

    Long setnx(String key, String value);

    Long incrBy(String key, long increment);

    Long hset(String key, String field, String value);

    Long hset(String key, Map<String, String> hash);

    String hget(String key, String field);

    Long hsetnx(String key, String field, String value);

    Long hincrBy(String key, String field, long value);

    Boolean hexists(String key, String field);

    Long hdel(String key, String... field);

    Long hlen(String key);

    List<String> hvals(String key);

    Long sadd(String key, String... member);

    Set<String> smembers(String key);

    Long srem(String key, String... member);

    Long scard(String key);

    Boolean sismember(String key, String member);

    Long zadd(String key, double score, String member);

    Long zadd(String key, Map<String, Double> scores);

    Long zadd(String key, double score, String member, ZAddParams params);

    List<String> zrange(String key, long start, long stop);

    Long zrem(String key, String... members);

    List<Tuple> zrangeWithScores(String key, long start, long stop);

    Long zcard(String key);

    Long zcount(String key, double min, double max);

    Double zscore(String key, String member);

    List<String> zrangeByScore(String key, double min, double max);

    List<String> zrangeByScore(String key, double min, double max, int offset, int count);

    List<Tuple> zrangeByScoreWithScores(String key, double min, double max);

    Long zremrangeByScore(String key, String min, String max);

    Long del(String key);

    Long llen(String key);

    Long rpush(String key, String... values);

    List<String> lrange(String key, long start, long end);

    String ltrim(String key, long start, long end);

    ScanResult<Map.Entry<String, String>> hscan(String key, String cursor);

    ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params);

    ScanResult<String> sscan(String key, String cursor);

    ScanResult<String> scan(String key, String cursor, int count);

    ScanResult<Tuple> zscan(String key, String cursor);

    ScanResult<String> sscan(String key, String cursor, ScanParams params);

    String set(byte[] key, byte[] value);

    void mset(byte[]... keyvalues);

    byte[] getBytes(byte[] key);

    List<String> mget(String[] keys);

    List<byte[]> mgetBytes(byte[]... keys);

    Object evalsha(final String sha1, final List<String> keys, final List<String> args);

    Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args);

    byte[] scriptLoad(byte[] script, byte[] sampleKey);

    String info(String command);

    int waitReplicas(String key, int replicas, long timeoutInMillis);

    String ping();
}
