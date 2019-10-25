/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dyno;

import com.google.inject.Singleton;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.dyno.jedis.DynoJedisClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.params.ZAddParams;

/**
 * @author Viren
 * Proxy for the Dynomite client
 */
@Singleton
public class DynoProxy {

    private static Logger logger = LoggerFactory.getLogger(DynoProxy.class);

    protected DiscoveryClient discoveryClient;

    protected JedisCommands dynoClient;

    @Inject
    public DynoProxy(JedisCommands dynoClient) {
        this.dynoClient = dynoClient;
    }

    /**
     * @deprecated The preferred method of construction for this use case is via DynoProxyDiscoveryProvider.
     */
    @Deprecated
    public DynoProxy(DiscoveryClient discoveryClient, Configuration config) throws DynoException, InterruptedException, ExecutionException {
        this.discoveryClient = discoveryClient;
        String cluster = config.getProperty("workflow.dynomite.cluster", null);
        String applicationName = config.getAppId();
        this.dynoClient = new DynoJedisClient.Builder()
                .withApplicationName(applicationName)
                .withDynomiteClusterName(cluster)
                .withDiscoveryClient(discoveryClient)
                .build();
    }

    public Set<String> zrange(String key, long start, long end) {
        return dynoClient.zrange(key, start, end);
    }

    public Set<Tuple> zrangeByScoreWithScores(String key, double maxScore, int count) {
        return dynoClient.zrangeByScoreWithScores(key, 0, maxScore, 0, count);
    }

    public Set<String> zrangeByScore(String key, double maxScore, int count) {
        return dynoClient.zrangeByScore(key, 0, maxScore, 0, count);
    }

    public Set<String> zrangeByScore(String key, double minScore, double maxScore, int count) {
        return dynoClient.zrangeByScore(key, minScore, maxScore, 0, count);
    }

    public ScanResult<Tuple> zscan(String key, int cursor) {
        return dynoClient.zscan(key, "" + cursor);
    }

    public String get(String key) {
        return dynoClient.get(key);
    }

    public Long zcard(String key) {
        return dynoClient.zcard(key);
    }

    public Long del(String key) {
        return dynoClient.del(key);
    }

    public Long zrem(String key, String member) {
        return dynoClient.zrem(key, member);
    }

    public long zremrangeByScore(String key, String start, String end) { return dynoClient.zremrangeByScore(key, start, end);}

    public long zcount(String key, double min, double max) { return dynoClient.zcount(key, min, max);}

    public String set(String key, String value) {
        String retVal = dynoClient.set(key, value);
        return retVal;
    }

    public Long setnx(String key, String value) {
        Long added = dynoClient.setnx(key, value);
        return added;
    }

    public Long zadd(String key, double score, String member) {
        Long retVal = dynoClient.zadd(key, score, member);
        return retVal;
    }

    public Long zaddnx(String key, double score, String member) {
        ZAddParams params = ZAddParams.zAddParams().nx();
        Long retVal = dynoClient.zadd(key, score, member, params);
        return retVal;
    }

    public Long hset(String key, String field, String value) {
        Long retVal = dynoClient.hset(key, field, value);
        return retVal;
    }

    public Long hsetnx(String key, String field, String value) {
        Long retVal = dynoClient.hsetnx(key, field, value);
        return retVal;
    }

    public Long hlen(String key) {
        Long retVal = dynoClient.hlen(key);
        return retVal;
    }

    public String hget(String key, String field) {
        return dynoClient.hget(key, field);
    }

    public Optional<String> optionalHget(String key, String field) {
        return Optional.ofNullable(dynoClient.hget(key, field));
    }

    public Map<String, String> hscan(String key, int count) {
        Map<String, String> m = new HashMap<>();
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> scanResult = dynoClient.hscan(key, "" + cursor);
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
        JedisCommands dyno = dynoClient;
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> scanResult = dyno.hscan(key, "" + cursor);
            cursor = Integer.parseInt(scanResult.getCursor());
            for (Entry<String, String> r : scanResult.getResult()) {
                m.put(r.getKey(), r.getValue());
            }
        } while (cursor > 0);

        return m;
    }

    public List<String> hvals(String key) {
        logger.trace("hvals {}", key);
        return dynoClient.hvals(key);
    }

    public Set<String> hkeys(String key) {
        logger.trace("hkeys {}", key);
        JedisCommands client = dynoClient;
        Set<String> keys = new HashSet<>();
        int cursor = 0;
        do {
            ScanResult<Entry<String, String>> sr = client.hscan(key, "" + cursor);
            cursor = Integer.parseInt(sr.getCursor());
            List<Entry<String, String>> result = sr.getResult();
            for (Entry<String, String> e : result) {
                keys.add(e.getKey());
            }
        } while (cursor > 0);

        return keys;
    }

    public Long hdel(String key, String... fields) {
        logger.trace("hdel {} {}", key, fields[0]);
        return dynoClient.hdel(key, fields);
    }

    public Long expire(String key, int seconds) {
        return dynoClient.expire(key, seconds);
    }

    public Boolean hexists(String key, String field) {
        return dynoClient.hexists(key, field);
    }

    public Long sadd(String key, String value) {
        logger.trace("sadd {} {}", key, value);
        Long retVal = dynoClient.sadd(key, value);
        return retVal;
    }

    public Long srem(String key, String member) {
        logger.trace("srem {} {}", key, member);
        Long retVal = dynoClient.srem(key, member);
        return retVal;
    }

    public boolean sismember(String key, String member) {
        return dynoClient.sismember(key, member);
    }

    public Set<String> smembers(String key) {
        logger.trace("smembers {}", key);
        JedisCommands client = dynoClient;
        Set<String> r = new HashSet<>();
        int cursor = 0;
        ScanParams sp = new ScanParams();
        sp.count(50);

        do {
            ScanResult<String> scanResult = client.sscan(key, "" + cursor, sp);
            cursor = Integer.parseInt(scanResult.getCursor());
            r.addAll(scanResult.getResult());

        } while (cursor > 0);

        return r;

    }

    public Long scard(String key) {
        return dynoClient.scard(key);
    }

}
