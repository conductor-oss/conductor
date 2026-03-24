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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.redis.config.AnyRedisConnectionCondition;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

/** Proxy for the {@link JedisCommands} object. */
@Component
@Conditional(AnyRedisConnectionCondition.class)
@Slf4j
public class JedisProxy {

    protected JedisCommands jedisCommands;

    @Autowired
    public JedisProxy(JedisCommands jedisCommands) {
        this.jedisCommands = jedisCommands;
        log.info("Initialized JedisProxy");
    }

    public List<String> zrange(String key, long start, long end) {
        return jedisCommands.zrange(key, start, end);
    }

    public List<Tuple> zrangeWithScores(String key, long start, long end) {
        return jedisCommands.zrangeWithScores(key, start, end);
    }

    public List<Tuple> zrangeByScoreWithScores(String key, double minScore, double maxScore) {
        return jedisCommands.zrangeByScoreWithScores(key, minScore, maxScore);
    }

    public List<String> zrangeByScore(String key, double maxScore, int count) {
        return jedisCommands.zrangeByScore(key, 0, maxScore, 0, count);
    }

    public List<String> zrangeByScore(String key, double minScore, double maxScore, int count) {
        return jedisCommands.zrangeByScore(key, minScore, maxScore, 0, count);
    }

    public List<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        return jedisCommands.zrangeByScore(key, min, max, offset, count);
    }

    public ScanResult<Tuple> zscan(String key, int cursor) {
        return jedisCommands.zscan(key, "" + cursor);
    }

    public String get(String key) {
        return jedisCommands.get(key);
    }

    public List<String> mget(String... keys) {
        return jedisCommands.mget(keys);
    }

    public List<byte[]> mget(byte[]... keys) {
        return jedisCommands.mgetBytes(keys);
    }

    public String mget(String key) {
        return jedisCommands.get(key);
    }

    public Long zcard(String key) {
        return jedisCommands.zcard(key);
    }

    public Long del(String key) {
        return jedisCommands.del(key);
    }

    public Long llen(String key) {
        return jedisCommands.llen(key);
    }

    public Long rpush(String key, String... values) {
        return jedisCommands.rpush(key, values);
    }

    public List<String> lrange(String key, long start, long end) {
        return jedisCommands.lrange(key, start, end);
    }

    public String ltrim(String key, long start, long end) {
        return jedisCommands.ltrim(key, start, end);
    }

    public Long zrem(String key, String member) {
        return jedisCommands.zrem(key, member);
    }

    public Long zrem(String key, String... members) {
        return jedisCommands.zrem(key, members);
    }

    public long zremrangeByScore(String key, String start, String end) {
        return jedisCommands.zremrangeByScore(key, start, end);
    }

    public long zcount(String key, double min, double max) {
        return jedisCommands.zcount(key, min, max);
    }

    public String set(String key, String value) {
        return jedisCommands.set(key, value);
    }

    public Long setnx(String key, String value) {
        return jedisCommands.setnx(key, value);
    }

    public String setWithExpiry(String key, String value, long ttlInSeconds) {
        SetParams params = SetParams.setParams().ex(ttlInSeconds);
        return jedisCommands.set(key, value, params);
    }

    public String setWithExpiry(String key, byte[] value, long ttlInSeconds) {
        SetParams params = SetParams.setParams().ex(ttlInSeconds);
        return jedisCommands.set(key.getBytes(StandardCharsets.UTF_8), value, params);
    }

    public String setWithExpiryInMilliIfNotExists(String key, String value, long ttlInMillis) {
        SetParams params = SetParams.setParams().px(ttlInMillis).nx();
        return jedisCommands.set(key, value, params);
    }

    public String setWithExpiryInMilliIfNotExists(String key, byte[] value, long ttlInMillis) {
        SetParams params = SetParams.setParams().px(ttlInMillis).nx();
        return jedisCommands.set(key.getBytes(StandardCharsets.UTF_8), value, params);
    }

    public Long ttl(String key) {
        return jedisCommands.ttl(key);
    }

    public Long zadd(String key, double score, String member) {
        return jedisCommands.zadd(key, score, member);
    }

    public Long zaddnx(String key, double score, String member) {
        ZAddParams params = ZAddParams.zAddParams().nx();
        return jedisCommands.zadd(key, score, member, params);
    }

    public boolean exists(String key) {
        return jedisCommands.exists(key);
    }

    public Long hset(String key, String field, String value) {
        return jedisCommands.hset(key, field, value);
    }

    public Long hset(String key, Map<String, String> fields) {
        return jedisCommands.hset(key, fields);
    }

    public Long hsetnx(String key, String field, String value) {
        return jedisCommands.hsetnx(key, field, value);
    }

    public Long hincrBy(String key, String field, long value) {
        return jedisCommands.hincrBy(key, field, value);
    }

    public Long hlen(String key) {
        return jedisCommands.hlen(key);
    }

    public String hget(String key, String field) {
        return jedisCommands.hget(key, field);
    }

    public Optional<String> optionalHget(String key, String field) {
        return Optional.ofNullable(jedisCommands.hget(key, field));
    }

    public Map<String, String> hscan(String key, int count) {
        Map<String, String> m = new HashMap<>();
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> scanResult = jedisCommands.hscan(key, "" + cursor);
            cursor = Integer.parseInt(scanResult.getCursor());
            for (Entry<String, String> r : scanResult.getResult()) {
                m.put(r.getKey(), r.getValue());
            }
            if (m.size() > count) {
                break;
            }
        } while (cursor > 0);

        return m;
    }

    public Map<String, String> hgetAll(String key) {
        Map<String, String> m = new HashMap<>();
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> scanResult = jedisCommands.hscan(key, "" + cursor);
            cursor = Integer.parseInt(scanResult.getCursor());
            for (Entry<String, String> r : scanResult.getResult()) {
                m.put(r.getKey(), r.getValue());
            }
        } while (cursor > 0);

        return m;
    }

    public Map<String, String> hgetAll(String key, String fieldMatch) {
        Map<String, String> m = new HashMap<>();
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> scanResult =
                    jedisCommands.hscan(
                            key, "" + cursor, new ScanParams().match(fieldMatch).count(1_000_000));
            cursor = Integer.parseInt(scanResult.getCursor());
            for (Entry<String, String> r : scanResult.getResult()) {
                m.put(r.getKey(), r.getValue());
            }
        } while (cursor > 0);

        return m;
    }

    public List<String> hvals(String key) {
        return jedisCommands.hvals(key);
    }

    public Set<String> hkeys(String key) {
        Set<String> keys = new HashSet<>();
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> sr = jedisCommands.hscan(key, "" + cursor);
            cursor = Integer.parseInt(sr.getCursor());
            List<Entry<String, String>> result = sr.getResult();
            for (Entry<String, String> e : result) {
                keys.add(e.getKey());
            }
        } while (cursor > 0);

        return keys;
    }

    public Long hdel(String key, String... fields) {
        return jedisCommands.hdel(key, fields);
    }

    public Long expire(String key, long seconds) {
        return jedisCommands.expire(key, seconds);
    }

    public Boolean hexists(String key, String field) {
        return jedisCommands.hexists(key, field);
    }

    public Long sadd(String key, String value) {
        return jedisCommands.sadd(key, value);
    }

    public Long sadd(String key, String... values) {
        return jedisCommands.sadd(key, values);
    }

    public Long srem(String key, String member) {
        return jedisCommands.srem(key, member);
    }

    public Long srem(String key, String... members) {
        return jedisCommands.srem(key, members);
    }

    public boolean sismember(String key, String member) {
        return jedisCommands.sismember(key, member);
    }

    public Set<String> smembers(String key) {
        Set<String> r = new HashSet<>();
        int cursor = 0;
        ScanParams sp = new ScanParams();
        sp.count(50);

        do {
            ScanResult<String> scanResult = jedisCommands.sscan(key, "" + cursor, sp);
            cursor = Integer.parseInt(scanResult.getCursor());
            r.addAll(scanResult.getResult());
        } while (cursor > 0);

        return r;
    }

    // Use the actual smembers command - use if the set is small enough
    public Set<String> smembers2(String key) {
        // collect metrics
        return Monitors.getTimer("jedis_members2").record(() -> jedisCommands.smembers(key));
    }

    public Long scard(String key) {
        return jedisCommands.scard(key);
    }

    public String set(String key, byte[] value) {
        return Monitors.getTimer("jedis_set_str_byte")
                .record(() -> jedisCommands.set(key.getBytes(StandardCharsets.UTF_8), value));
    }

    public void mset(Map<byte[], byte[]> keyValues) {
        byte[][] keyvaluebytes = new byte[keyValues.size() * 2][];
        int i = 0;
        for (Entry<byte[], byte[]> e : keyValues.entrySet()) {
            keyvaluebytes[i++] = e.getKey();
            keyvaluebytes[i++] = e.getValue();
        }
        Monitors.getTimer("jedis_mset_str_byte").record(() -> jedisCommands.mset(keyvaluebytes));
    }

    public void increment(String key, long value) {
        jedisCommands.incrBy(key, value);
    }

    public byte[] getBytes(String key) {
        // collect metrics
        return Monitors.getTimer("jedis_get_bytes")
                .record(() -> jedisCommands.getBytes(key.getBytes(StandardCharsets.UTF_8)));
    }

    public Object evalsha(final String sha1, final List<String> keys, final List<String> args) {
        return jedisCommands.evalsha(sha1, keys, args);
    }

    public Object evalsha(byte[] sha1, final List<byte[]> keys, final List<byte[]> args) {
        return jedisCommands.evalsha(sha1, keys, args);
    }

    public byte[] scriptLoad(byte[] script, byte[] sampleKey) {
        return jedisCommands.scriptLoad(script, sampleKey);
    }

    public String info(String command) {
        return jedisCommands.info(command);
    }

    public int waitReplicas(String key, int replicas, long timeoutInMillis) {
        return jedisCommands.waitReplicas(key, replicas, timeoutInMillis);
    }

    public String ping() {
        return jedisCommands.ping();
    }

    public List<String> scan(String keyPrefix, String cursor, int count) {
        Set<String> keys = new HashSet<>();
        ScanResult<String> sr = jedisCommands.scan(keyPrefix, "" + cursor, count);
        return sr.getResult();
    }

    public ScanResult<String> scanResult(String pattern, String cursor, int count) {
        return jedisCommands.scan(pattern, cursor, count);
    }

    public List<String> findAll(String keyPrefix) {
        List<String> keys = new ArrayList<>();
        int maxLoop = 1000;
        String cursor = "0";
        while (maxLoop-- > 0) {
            ScanResult<String> sr = jedisCommands.scan(keyPrefix, cursor, 100);
            if ("0".equals(sr.getCursor())) {
                keys.addAll(sr.getResult());
                break;
            } else {
                keys.addAll(sr.getResult());
                cursor = sr.getCursor();
            }
        }
        return keys;
    }

    public void persist(String key) {
        jedisCommands.persist(key);
    }
}
