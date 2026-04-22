/*
 * Copyright 2022 Conductor Authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JedisStandalone} and {@link UnifiedJedisCommands} — both
 * implementations of {@link JedisCommands} tested against a real Redis instance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JedisCommandsIntegrationTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:6.2.6-alpine"))
                    .withExposedPorts(6379);

    private JedisPool jedisPool;
    private JedisStandalone jedisStandalone;
    private UnifiedJedisCommands unifiedJedisCommands;

    @BeforeAll
    void setUp() {
        redis.start();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMinIdle(2);
        poolConfig.setMaxTotal(10);
        jedisPool = new JedisPool(poolConfig, redis.getHost(), redis.getFirstMappedPort());
        jedisStandalone = new JedisStandalone(jedisPool);

        GenericObjectPoolConfig<Connection> unifiedPoolConfig = new GenericObjectPoolConfig<>();
        unifiedPoolConfig.setMinIdle(2);
        unifiedPoolConfig.setMaxTotal(10);
        JedisPooled pooled =
                new JedisPooled(unifiedPoolConfig, redis.getHost(), redis.getFirstMappedPort());
        unifiedJedisCommands = new UnifiedJedisCommands(pooled);
    }

    @BeforeEach
    void flushDb() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    Stream<JedisCommands> implementations() {
        return Stream.of(jedisStandalone, unifiedJedisCommands);
    }

    // --- String operations ---

    @ParameterizedTest
    @MethodSource("implementations")
    void setAndGet(JedisCommands cmd) {
        assertEquals("OK", cmd.set("k1", "v1"));
        assertEquals("v1", cmd.get("k1"));
        assertNull(cmd.get("nonexistent"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void setWithParams(JedisCommands cmd) {
        SetParams params = SetParams.setParams().ex(60).nx();
        assertEquals("OK", cmd.set("sp1", "val", params));
        assertEquals("val", cmd.get("sp1"));
        // NX should fail on second set
        assertNull(cmd.set("sp1", "val2", params));
        assertEquals("val", cmd.get("sp1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void setAndGetBytes(JedisCommands cmd) {
        byte[] key = "bk1".getBytes(StandardCharsets.UTF_8);
        byte[] value = "bv1".getBytes(StandardCharsets.UTF_8);
        assertEquals("OK", cmd.set(key, value));
        assertArrayEquals(value, cmd.getBytes(key));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void setBytesWithParams(JedisCommands cmd) {
        byte[] key = "bsp1".getBytes(StandardCharsets.UTF_8);
        byte[] value = "bval".getBytes(StandardCharsets.UTF_8);
        SetParams params = SetParams.setParams().ex(60);
        assertEquals("OK", cmd.set(key, value, params));
        assertArrayEquals(value, cmd.getBytes(key));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void setnx(JedisCommands cmd) {
        assertEquals(1L, cmd.setnx("nx1", "first"));
        assertEquals(0L, cmd.setnx("nx1", "second"));
        assertEquals("first", cmd.get("nx1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void incrBy(JedisCommands cmd) {
        cmd.set("counter", "10");
        assertEquals(15L, cmd.incrBy("counter", 5));
        assertEquals(10L, cmd.incrBy("counter", -5));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void mget(JedisCommands cmd) {
        cmd.set("mg1", "a");
        cmd.set("mg2", "b");
        cmd.set("mg3", "c");

        List<String> result = cmd.mget(new String[] {"mg1", "mg2", "mg3"});
        assertEquals(3, result.size());
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void msetAndMgetBytes(JedisCommands cmd) {
        byte[] k1 = "mbk1".getBytes(StandardCharsets.UTF_8);
        byte[] v1 = "mbv1".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "mbk2".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "mbv2".getBytes(StandardCharsets.UTF_8);

        cmd.mset(k1, v1, k2, v2);

        List<byte[]> results = cmd.mgetBytes(k1, k2);
        assertEquals(2, results.size());
    }

    // --- Key operations ---

    @ParameterizedTest
    @MethodSource("implementations")
    void existsAndDel(JedisCommands cmd) {
        assertFalse(cmd.exists("ex1"));
        cmd.set("ex1", "val");
        assertTrue(cmd.exists("ex1"));
        assertEquals(1L, cmd.del("ex1"));
        assertFalse(cmd.exists("ex1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void expireAndTtl(JedisCommands cmd) {
        cmd.set("ttl1", "val");
        assertEquals(-1L, cmd.ttl("ttl1")); // no expiry
        cmd.expire("ttl1", 100);
        long ttl = cmd.ttl("ttl1");
        assertTrue(ttl > 0 && ttl <= 100);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void persist(JedisCommands cmd) {
        cmd.set("p1", "val");
        cmd.expire("p1", 100);
        assertTrue(cmd.ttl("p1") > 0);
        cmd.persist("p1");
        assertEquals(-1L, cmd.ttl("p1"));
    }

    // --- Hash operations ---

    @ParameterizedTest
    @MethodSource("implementations")
    void hsetAndHget(JedisCommands cmd) {
        cmd.hset("h1", "f1", "v1");
        assertEquals("v1", cmd.hget("h1", "f1"));
        assertNull(cmd.hget("h1", "missing"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hsetMap(JedisCommands cmd) {
        Map<String, String> hash = new HashMap<>();
        hash.put("a", "1");
        hash.put("b", "2");
        hash.put("c", "3");
        cmd.hset("hm1", hash);
        assertEquals("1", cmd.hget("hm1", "a"));
        assertEquals("2", cmd.hget("hm1", "b"));
        assertEquals("3", cmd.hget("hm1", "c"));
        assertEquals(3L, cmd.hlen("hm1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hsetnx(JedisCommands cmd) {
        assertEquals(1L, cmd.hsetnx("hn1", "f1", "first"));
        assertEquals(0L, cmd.hsetnx("hn1", "f1", "second"));
        assertEquals("first", cmd.hget("hn1", "f1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hincrBy(JedisCommands cmd) {
        cmd.hset("hi1", "count", "10");
        assertEquals(13L, cmd.hincrBy("hi1", "count", 3));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hexistsAndHdel(JedisCommands cmd) {
        cmd.hset("hd1", "f1", "v1");
        assertTrue(cmd.hexists("hd1", "f1"));
        assertEquals(1L, cmd.hdel("hd1", "f1"));
        assertFalse(cmd.hexists("hd1", "f1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hvals(JedisCommands cmd) {
        cmd.hset("hv1", "a", "1");
        cmd.hset("hv1", "b", "2");
        List<String> vals = cmd.hvals("hv1");
        assertEquals(2, vals.size());
        assertTrue(vals.contains("1"));
        assertTrue(vals.contains("2"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hscan(JedisCommands cmd) {
        for (int i = 0; i < 100; i++) {
            cmd.hset("hs1", "field-" + i, "value-" + i);
        }
        Map<String, String> all = new HashMap<>();
        String cursor = "0";
        do {
            ScanResult<Map.Entry<String, String>> result = cmd.hscan("hs1", cursor);
            cursor = result.getCursor();
            for (Map.Entry<String, String> e : result.getResult()) {
                all.put(e.getKey(), e.getValue());
            }
        } while (!"0".equals(cursor));

        assertEquals(100, all.size());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void hscanWithParams(JedisCommands cmd) {
        for (int i = 0; i < 50; i++) {
            cmd.hset("hsp1", "match-" + i, "v" + i);
            cmd.hset("hsp1", "other-" + i, "x" + i);
        }
        Map<String, String> matched = new HashMap<>();
        String cursor = "0";
        ScanParams params = new ScanParams().match("match-*").count(100);
        do {
            ScanResult<Map.Entry<String, String>> result = cmd.hscan("hsp1", cursor, params);
            cursor = result.getCursor();
            for (Map.Entry<String, String> e : result.getResult()) {
                matched.put(e.getKey(), e.getValue());
            }
        } while (!"0".equals(cursor));

        assertEquals(50, matched.size());
        for (String key : matched.keySet()) {
            assertTrue(key.startsWith("match-"));
        }
    }

    // --- Set operations ---

    @ParameterizedTest
    @MethodSource("implementations")
    void saddAndSmembers(JedisCommands cmd) {
        cmd.sadd("s1", "a", "b", "c");
        Set<String> members = cmd.smembers("s1");
        assertEquals(Set.of("a", "b", "c"), members);
        assertEquals(3L, cmd.scard("s1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void sremAndSismember(JedisCommands cmd) {
        cmd.sadd("sr1", "a", "b", "c");
        assertTrue(cmd.sismember("sr1", "b"));
        cmd.srem("sr1", "b");
        assertFalse(cmd.sismember("sr1", "b"));
        assertEquals(2L, cmd.scard("sr1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void sscan(JedisCommands cmd) {
        for (int i = 0; i < 200; i++) {
            cmd.sadd("ss1", "member-" + i);
        }
        Set<String> all = new java.util.HashSet<>();
        String cursor = "0";
        do {
            ScanResult<String> result = cmd.sscan("ss1", cursor);
            cursor = result.getCursor();
            all.addAll(result.getResult());
        } while (!"0".equals(cursor));

        assertEquals(200, all.size());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void sscanWithParams(JedisCommands cmd) {
        for (int i = 0; i < 100; i++) {
            cmd.sadd("ssp1", "member-" + i);
        }
        Set<String> all = new java.util.HashSet<>();
        String cursor = "0";
        ScanParams params = new ScanParams().count(10);
        do {
            ScanResult<String> result = cmd.sscan("ssp1", cursor, params);
            cursor = result.getCursor();
            all.addAll(result.getResult());
        } while (!"0".equals(cursor));

        assertEquals(100, all.size());
    }

    // --- Sorted set operations ---

    @ParameterizedTest
    @MethodSource("implementations")
    void zaddAndZrange(JedisCommands cmd) {
        cmd.zadd("z1", 1.0, "a");
        cmd.zadd("z1", 2.0, "b");
        cmd.zadd("z1", 3.0, "c");

        List<String> range = cmd.zrange("z1", 0, -1);
        assertEquals(List.of("a", "b", "c"), range);
        assertEquals(3L, cmd.zcard("z1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zaddMap(JedisCommands cmd) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("x", 10.0);
        scores.put("y", 20.0);
        scores.put("z", 30.0);
        cmd.zadd("zm1", scores);

        assertEquals(3L, cmd.zcard("zm1"));
        assertEquals(10.0, cmd.zscore("zm1", "x"));
        assertEquals(20.0, cmd.zscore("zm1", "y"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zaddWithParams(JedisCommands cmd) {
        cmd.zadd("zp1", 1.0, "a");
        // NX: only add, don't update
        ZAddParams nxParams = ZAddParams.zAddParams().nx();
        cmd.zadd("zp1", 99.0, "a", nxParams);
        assertEquals(1.0, cmd.zscore("zp1", "a")); // unchanged
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zrangeByScore(JedisCommands cmd) {
        cmd.zadd("zs1", 1.0, "a");
        cmd.zadd("zs1", 2.0, "b");
        cmd.zadd("zs1", 3.0, "c");
        cmd.zadd("zs1", 4.0, "d");
        cmd.zadd("zs1", 5.0, "e");

        List<String> result = cmd.zrangeByScore("zs1", 2.0, 4.0);
        assertEquals(List.of("b", "c", "d"), result);

        List<String> paged = cmd.zrangeByScore("zs1", 1.0, 5.0, 1, 2);
        assertEquals(List.of("b", "c"), paged);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zrangeWithScores(JedisCommands cmd) {
        cmd.zadd("zws1", 1.5, "a");
        cmd.zadd("zws1", 2.5, "b");

        List<Tuple> tuples = cmd.zrangeWithScores("zws1", 0, -1);
        assertEquals(2, tuples.size());
        assertEquals("a", tuples.get(0).getElement());
        assertEquals(1.5, tuples.get(0).getScore());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zrangeByScoreWithScores(JedisCommands cmd) {
        cmd.zadd("zbsws1", 1.0, "a");
        cmd.zadd("zbsws1", 2.0, "b");
        cmd.zadd("zbsws1", 3.0, "c");

        List<Tuple> tuples = cmd.zrangeByScoreWithScores("zbsws1", 1.5, 3.0);
        assertEquals(2, tuples.size());
        assertEquals("b", tuples.get(0).getElement());
        assertEquals("c", tuples.get(1).getElement());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zrem(JedisCommands cmd) {
        cmd.zadd("zr1", 1.0, "a");
        cmd.zadd("zr1", 2.0, "b");
        cmd.zadd("zr1", 3.0, "c");

        assertEquals(2L, cmd.zrem("zr1", "a", "c"));
        assertEquals(1L, cmd.zcard("zr1"));
        assertEquals(List.of("b"), cmd.zrange("zr1", 0, -1));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zcountAndZscore(JedisCommands cmd) {
        cmd.zadd("zc1", 1.0, "a");
        cmd.zadd("zc1", 2.0, "b");
        cmd.zadd("zc1", 3.0, "c");
        cmd.zadd("zc1", 4.0, "d");

        assertEquals(2L, cmd.zcount("zc1", 2.0, 3.0));
        assertEquals(3.0, cmd.zscore("zc1", "c"));
        assertNull(cmd.zscore("zc1", "missing"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zremrangeByScore(JedisCommands cmd) {
        cmd.zadd("zrr1", 1.0, "a");
        cmd.zadd("zrr1", 2.0, "b");
        cmd.zadd("zrr1", 3.0, "c");
        cmd.zadd("zrr1", 4.0, "d");

        assertEquals(2L, cmd.zremrangeByScore("zrr1", "2", "3"));
        assertEquals(2L, cmd.zcard("zrr1"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void zscan(JedisCommands cmd) {
        for (int i = 0; i < 100; i++) {
            cmd.zadd("zsc1", i, "m-" + i);
        }
        Set<String> all = new java.util.HashSet<>();
        String cursor = "0";
        do {
            ScanResult<Tuple> result = cmd.zscan("zsc1", cursor);
            cursor = result.getCursor();
            for (Tuple t : result.getResult()) {
                all.add(t.getElement());
            }
        } while (!"0".equals(cursor));

        assertEquals(100, all.size());
    }

    // --- SCAN ---

    @ParameterizedTest
    @MethodSource("implementations")
    void scan(JedisCommands cmd) {
        for (int i = 0; i < 50; i++) {
            cmd.set("scan-prefix:" + i, "v" + i);
        }
        // Also set some keys that shouldn't match
        for (int i = 0; i < 20; i++) {
            cmd.set("other:" + i, "x" + i);
        }

        Set<String> found = new java.util.HashSet<>();
        String cursor = "0";
        do {
            ScanResult<String> result = cmd.scan("scan-prefix:*", cursor, 100);
            cursor = result.getCursor();
            found.addAll(result.getResult());
        } while (!"0".equals(cursor));

        assertEquals(50, found.size());
        for (String key : found) {
            assertTrue(key.startsWith("scan-prefix:"));
        }
    }

    // --- Script / Info / Ping ---

    @ParameterizedTest
    @MethodSource("implementations")
    void ping(JedisCommands cmd) {
        assertEquals("PONG", cmd.ping());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void info(JedisCommands cmd) {
        String info = cmd.info("server");
        assertNotNull(info);
        assertTrue(info.contains("redis_version"));
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void evalsha(JedisCommands cmd) {
        // Load a simple script that returns the value of a key
        byte[] script = "return redis.call('get', KEYS[1])".getBytes(StandardCharsets.UTF_8);
        byte[] sampleKey = "evaltest".getBytes(StandardCharsets.UTF_8);
        byte[] sha = cmd.scriptLoad(script, sampleKey);
        assertNotNull(sha);

        cmd.set("evaltest", "hello");
        Object result =
                cmd.evalsha(
                        new String(sha, StandardCharsets.UTF_8), List.of("evaltest"), List.of());
        assertEquals("hello", result);
    }
}
