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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

/** Do NOT use it for the cluster commands */
public class UnifiedJedisCommands implements JedisCommands {

    private final UnifiedJedis unifiedJedis;

    public UnifiedJedisCommands(UnifiedJedis unifiedJedis) {
        this.unifiedJedis = unifiedJedis;
    }

    private <R> R executeInJedis(Function<UnifiedJedis, R> function) {
        return function.apply(unifiedJedis);
    }

    @Override
    public String set(String key, String value) {
        return executeInJedis(jedis -> jedis.set(key, value));
    }

    @Override
    public String set(String key, String value, SetParams params) {
        return executeInJedis(jedis -> jedis.set(key, value, params));
    }

    @Override
    public String set(byte[] key, byte[] value, SetParams params) {
        return executeInJedis(jedis -> jedis.set(key, value, params));
    }

    @Override
    public String get(String key) {
        return executeInJedis(jedis -> jedis.get(key));
    }

    public List<String> mget(String... keys) {
        return executeInJedis(jedis -> jedis.mget(keys));
    }

    @Override
    public List<byte[]> mgetBytes(byte[]... keys) {
        return executeInJedis(jedis -> jedis.mget(keys));
    }

    @Override
    public Boolean exists(String key) {
        return executeInJedis(jedis -> jedis.exists(key));
    }

    @Override
    public Long persist(String key) {
        return executeInJedis(jedis -> jedis.persist(key));
    }

    @Override
    public Long expire(String key, long seconds) {
        return executeInJedis(jedis -> jedis.expire(key, seconds));
    }

    @Override
    public Long ttl(String key) {
        return executeInJedis(jedis -> jedis.ttl(key));
    }

    @Override
    public Long setnx(String key, String value) {
        return executeInJedis(jedis -> jedis.setnx(key, value));
    }

    @Override
    public Long incrBy(String key, long increment) {
        return executeInJedis(jedis -> jedis.incrBy(key, increment));
    }

    @Override
    public Long hset(String key, String field, String value) {
        return executeInJedis(jedis -> jedis.hset(key, field, value));
    }

    @Override
    public Long hset(String key, Map<String, String> hash) {
        return executeInJedis(jedis -> jedis.hset(key, hash));
    }

    @Override
    public String hget(String key, String field) {
        return executeInJedis(jedis -> jedis.hget(key, field));
    }

    @Override
    public Long hsetnx(String key, String field, String value) {
        return executeInJedis(jedis -> jedis.hsetnx(key, field, value));
    }

    @Override
    public Long hincrBy(String key, String field, long value) {
        return executeInJedis(jedis -> jedis.hincrBy(key, field, value));
    }

    @Override
    public Boolean hexists(String key, String field) {
        return executeInJedis(jedis -> jedis.hexists(key, field));
    }

    @Override
    public Long hdel(String key, String... field) {
        return executeInJedis(jedis -> jedis.hdel(key, field));
    }

    @Override
    public Long hlen(String key) {
        return executeInJedis(jedis -> jedis.hlen(key));
    }

    @Override
    public List<String> hvals(String key) {
        return executeInJedis(jedis -> jedis.hvals(key));
    }

    @Override
    public Long sadd(String key, String... member) {
        return executeInJedis(jedis -> jedis.sadd(key, member));
    }

    @Override
    public Set<String> smembers(String key) {
        return executeInJedis(jedis -> jedis.smembers(key));
    }

    @Override
    public Long srem(String key, String... member) {
        return executeInJedis(jedis -> jedis.srem(key, member));
    }

    @Override
    public Long scard(String key) {
        return executeInJedis(jedis -> jedis.scard(key));
    }

    @Override
    public Boolean sismember(String key, String member) {
        return executeInJedis(jedis -> jedis.sismember(key, member));
    }

    @Override
    public Long zadd(String key, double score, String member) {
        return executeInJedis(jedis -> jedis.zadd(key, score, member));
    }

    @Override
    public Long zadd(String key, Map<String, Double> scores) {
        return executeInJedis(jedis -> jedis.zadd(key, scores));
    }

    @Override
    public Long zadd(String key, double score, String member, ZAddParams params) {
        return executeInJedis(jedis -> jedis.zadd(key, score, member, params));
    }

    @Override
    public List<String> zrange(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zrange(key, start, stop));
    }

    @Override
    public Long zrem(String key, String... members) {
        return executeInJedis(jedis -> jedis.zrem(key, members));
    }

    @Override
    public List<Tuple> zrangeWithScores(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zrangeWithScores(key, start, stop));
    }

    @Override
    public Long zcard(String key) {
        return executeInJedis(jedis -> jedis.zcard(key));
    }

    @Override
    public Long zcount(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zcount(key, min, max));
    }

    @Override
    public Double zscore(String key, String member) {
        return executeInJedis(jedis -> jedis.zscore(key, member));
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zrangeByScore(key, min, max));
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrangeByScore(key, min, max, offset, count));
    }

    @Override
    public List<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zrangeByScoreWithScores(key, min, max));
    }

    @Override
    public Long zremrangeByScore(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zremrangeByScore(key, min, max));
    }

    @Override
    public Long del(String key) {
        return executeInJedis(jedis -> jedis.del(key));
    }

    @Override
    public Long llen(String key) {
        return executeInJedis(jedis -> jedis.llen(key));
    }

    @Override
    public Long rpush(String key, String... values) {
        return executeInJedis(jedis -> jedis.rpush(key, values));
    }

    @Override
    public List<String> lrange(String key, long start, long end) {
        return executeInJedis(jedis -> jedis.lrange(key, start, end));
    }

    @Override
    public String ltrim(String key, long start, long end) {
        return executeInJedis(jedis -> jedis.ltrim(key, start, end));
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
        return executeInJedis(jedis -> jedis.hscan(key, cursor));
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(
            String key, String cursor, ScanParams params) {
        return executeInJedis(jedis -> jedis.hscan(key, cursor, params));
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor) {
        return executeInJedis(jedis -> jedis.sscan(key, cursor));
    }

    @Override
    public ScanResult<String> scan(String keyPrefix, String cursor, int count) {
        final ScanParams params = new ScanParams().count(count).match(keyPrefix);
        return executeInJedis(jedis -> jedis.scan(cursor, params));
    }

    @Override
    public ScanResult<Tuple> zscan(String key, String cursor) {
        return executeInJedis(jedis -> jedis.zscan(key, cursor));
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        return executeInJedis(jedis -> jedis.sscan(key, cursor, params));
    }

    @Override
    public String set(byte[] key, byte[] value) {
        return executeInJedis(jedis -> jedis.set(key, value));
    }

    @Override
    public void mset(byte[]... keyvalues) {
        executeInJedis(jedis -> jedis.mset(keyvalues));
    }

    @Override
    public byte[] getBytes(byte[] key) {
        return executeInJedis(jedis -> jedis.get(key));
    }

    @Override
    public Object evalsha(String sha1, List<String> keys, List<String> args) {
        return unifiedJedis.evalsha(sha1, keys, args);
    }

    @Override
    public Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
        return unifiedJedis.evalsha(sha1, keys, args);
    }

    @Override
    public byte[] scriptLoad(byte[] script, byte[] sampleKey) {
        return unifiedJedis.scriptLoad(script, sampleKey);
    }

    @Override
    public String info(String command) {
        return unifiedJedis.info(command);
    }

    @Override
    public int waitReplicas(String key, int replicas, long timeoutInMillis) {
        // Nothing to replicate
        return replicas;
    }

    @Override
    public String ping() {
        return unifiedJedis.ping();
    }
}
