/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.redis.config.AnyRedisCondition;

import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.params.ZAddParams;

import static com.netflix.conductor.redis.config.RedisCommonConfiguration.DEFAULT_CLIENT_INJECTION_NAME;

/** Proxy for the {@link JedisCommands} object. */
@Component
@Conditional(AnyRedisCondition.class)
public class JedisProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisProxy.class);

    protected JedisCommands jedisCommands;

    public JedisProxy(@Qualifier(DEFAULT_CLIENT_INJECTION_NAME) JedisCommands jedisCommands) {
        this.jedisCommands = jedisCommands;
    }

    public Set<String> zrange(String key, long start, long end) {
        return jedisCommands.zrange(key, start, end);
    }

    public Set<Tuple> zrangeByScoreWithScores(String key, double maxScore, int count) {
        return jedisCommands.zrangeByScoreWithScores(key, 0, maxScore, 0, count);
    }

    public Set<String> zrangeByScore(String key, double maxScore, int count) {
        return jedisCommands.zrangeByScore(key, 0, maxScore, 0, count);
    }

    public Set<String> zrangeByScore(String key, double minScore, double maxScore, int count) {
        return jedisCommands.zrangeByScore(key, minScore, maxScore, 0, count);
    }

    public ScanResult<Tuple> zscan(String key, int cursor) {
        return jedisCommands.zscan(key, "" + cursor);
    }

    public String get(String key) {
        return jedisCommands.get(key);
    }

    public Long zcard(String key) {
        return jedisCommands.zcard(key);
    }

    public Long del(String key) {
        return jedisCommands.del(key);
    }

    public Long zrem(String key, String member) {
        return jedisCommands.zrem(key, member);
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

    public Long zadd(String key, double score, String member) {
        return jedisCommands.zadd(key, score, member);
    }

    public Long zaddnx(String key, double score, String member) {
        ZAddParams params = ZAddParams.zAddParams().nx();
        return jedisCommands.zadd(key, score, member, params);
    }

    public Long hset(String key, String field, String value) {
        return jedisCommands.hset(key, field, value);
    }

    public Long hsetnx(String key, String field, String value) {
        return jedisCommands.hsetnx(key, field, value);
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

    public List<String> hvals(String key) {
        LOGGER.trace("hvals {}", key);
        return jedisCommands.hvals(key);
    }

    public Set<String> hkeys(String key) {
        LOGGER.trace("hkeys {}", key);
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
        LOGGER.trace("hdel {} {}", key, fields[0]);
        return jedisCommands.hdel(key, fields);
    }

    public Long expire(String key, int seconds) {
        return jedisCommands.expire(key, seconds);
    }

    public Boolean hexists(String key, String field) {
        return jedisCommands.hexists(key, field);
    }

    public Long sadd(String key, String value) {
        LOGGER.trace("sadd {} {}", key, value);
        return jedisCommands.sadd(key, value);
    }

    public Long srem(String key, String member) {
        LOGGER.trace("srem {} {}", key, member);
        return jedisCommands.srem(key, member);
    }

    public boolean sismember(String key, String member) {
        return jedisCommands.sismember(key, member);
    }

    public Set<String> smembers(String key) {
        LOGGER.trace("smembers {}", key);
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

    public Long scard(String key) {
        return jedisCommands.scard(key);
    }
}
