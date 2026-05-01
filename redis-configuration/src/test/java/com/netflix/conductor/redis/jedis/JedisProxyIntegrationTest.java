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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.resps.Tuple;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JedisProxy} — validates scan-loop methods, convenience wrappers, and
 * byte-level operations against a real Redis.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JedisProxyIntegrationTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:6.2.6-alpine"))
                    .withExposedPorts(6379);

    private JedisPool jedisPool;
    private JedisProxy proxy;

    @BeforeAll
    void setUp() {
        redis.start();

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);
        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        proxy = new JedisProxy(new JedisStandalone(jedisPool));
    }

    @BeforeEach
    void flushDb() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    // --- String operations ---

    @Test
    void setAndGet() {
        proxy.set("k1", "v1");
        assertEquals("v1", proxy.get("k1"));
    }

    @Test
    void setWithExpiry() {
        proxy.setWithExpiry("ex1", "val", 60);
        assertEquals("val", proxy.get("ex1"));
        long ttl = proxy.ttl("ex1");
        assertTrue(ttl > 0 && ttl <= 60);
    }

    @Test
    void setWithExpiryBytes() {
        byte[] value = "byteVal".getBytes(StandardCharsets.UTF_8);
        proxy.setWithExpiry("exb1", value, 60);
        byte[] result = proxy.getBytes("exb1");
        assertArrayEquals(value, result);
    }

    @Test
    void setWithExpiryInMilliIfNotExists() {
        assertEquals("OK", proxy.setWithExpiryInMilliIfNotExists("nxm1", "first", 60000));
        assertNull(proxy.setWithExpiryInMilliIfNotExists("nxm1", "second", 60000));
        assertEquals("first", proxy.get("nxm1"));
    }

    @Test
    void setWithExpiryInMilliIfNotExists_bytes() {
        byte[] v1 = "first".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "second".getBytes(StandardCharsets.UTF_8);
        assertEquals("OK", proxy.setWithExpiryInMilliIfNotExists("nxmb1", v1, 60000));
        assertNull(proxy.setWithExpiryInMilliIfNotExists("nxmb1", v2, 60000));
        assertArrayEquals(v1, proxy.getBytes("nxmb1"));
    }

    @Test
    void setnx() {
        assertEquals(1L, proxy.setnx("snx1", "first"));
        assertEquals(0L, proxy.setnx("snx1", "second"));
        assertEquals("first", proxy.get("snx1"));
    }

    @Test
    void increment() {
        proxy.set("ctr", "10");
        proxy.increment("ctr", 5);
        assertEquals("15", proxy.get("ctr"));
    }

    @Test
    void setAndGetBytesViaStringKey() {
        byte[] value = new byte[] {1, 2, 3, 4, 5};
        proxy.set("bytes1", value);
        assertArrayEquals(value, proxy.getBytes("bytes1"));
    }

    @Test
    void msetAndMget() {
        Map<byte[], byte[]> keyValues = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            keyValues.put(
                    ("mk" + i).getBytes(StandardCharsets.UTF_8),
                    ("mv" + i).getBytes(StandardCharsets.UTF_8));
        }
        proxy.mset(keyValues);

        byte[][] keys = keyValues.keySet().toArray(new byte[0][]);
        List<byte[]> results = proxy.mget(keys);
        assertEquals(100, results.size());
    }

    @Test
    void mgetStrings() {
        proxy.set("ms1", "a");
        proxy.set("ms2", "b");
        proxy.set("ms3", "c");

        List<String> results = proxy.mget("ms1", "ms2", "ms3");
        assertEquals(3, results.size());
        assertTrue(results.contains("a"));
        assertTrue(results.contains("b"));
        assertTrue(results.contains("c"));
    }

    @Test
    void mgetSingleKey() {
        proxy.set("single1", "val");
        assertEquals("val", proxy.mget("single1"));
    }

    // --- Key operations ---

    @Test
    void existsAndDel() {
        assertFalse(proxy.exists("ex1"));
        proxy.set("ex1", "val");
        assertTrue(proxy.exists("ex1"));
        proxy.del("ex1");
        assertFalse(proxy.exists("ex1"));
    }

    @Test
    void expireAndPersist() {
        proxy.set("ep1", "val");
        proxy.expire("ep1", 100);
        assertTrue(proxy.ttl("ep1") > 0);
        proxy.persist("ep1");
        assertEquals(-1L, proxy.ttl("ep1"));
    }

    // --- Hash operations ---

    @Test
    void hsetAndHget() {
        proxy.hset("h1", "f1", "v1");
        assertEquals("v1", proxy.hget("h1", "f1"));
    }

    @Test
    void hsetMap() {
        Map<String, String> fields = new HashMap<>();
        fields.put("a", "1");
        fields.put("b", "2");
        fields.put("c", "3");
        proxy.hset("hm1", fields);
        assertEquals("1", proxy.hget("hm1", "a"));
        assertEquals(3L, proxy.hlen("hm1"));
    }

    @Test
    void optionalHget() {
        proxy.hset("oh1", "f1", "val");
        assertEquals(Optional.of("val"), proxy.optionalHget("oh1", "f1"));
        assertEquals(Optional.empty(), proxy.optionalHget("oh1", "missing"));
    }

    @Test
    void hsetnx() {
        assertEquals(1L, proxy.hsetnx("hn1", "f1", "first"));
        assertEquals(0L, proxy.hsetnx("hn1", "f1", "second"));
        assertEquals("first", proxy.hget("hn1", "f1"));
    }

    @Test
    void hincrBy() {
        proxy.hset("hi1", "count", "10");
        assertEquals(15L, proxy.hincrBy("hi1", "count", 5));
    }

    @Test
    void hexistsAndHdel() {
        proxy.hset("hd1", "f1", "v1");
        assertTrue(proxy.hexists("hd1", "f1"));
        proxy.hdel("hd1", "f1");
        assertFalse(proxy.hexists("hd1", "f1"));
    }

    @Test
    void hvals() {
        proxy.hset("hv1", "a", "1");
        proxy.hset("hv1", "b", "2");
        List<String> vals = proxy.hvals("hv1");
        assertEquals(2, vals.size());
        assertTrue(vals.contains("1"));
        assertTrue(vals.contains("2"));
    }

    // --- Hash scan-loop methods ---

    @Test
    void hgetAll_scansEntireHash() {
        int fieldCount = 1000;
        for (int i = 0; i < fieldCount; i++) {
            proxy.hset("hall1", "field-" + i, "value-" + i);
        }

        Map<String, String> all = proxy.hgetAll("hall1");
        assertEquals(fieldCount, all.size());
        for (int i = 0; i < fieldCount; i++) {
            assertEquals("value-" + i, all.get("field-" + i));
        }
    }

    @Test
    void hgetAll_withFieldMatch() {
        for (int i = 0; i < 500; i++) {
            proxy.hset("hallm1", "match-" + i, "v" + i);
            proxy.hset("hallm1", "other-" + i, "x" + i);
        }

        Map<String, String> matched = proxy.hgetAll("hallm1", "match-*");
        assertEquals(500, matched.size());
        for (String key : matched.keySet()) {
            assertTrue(key.startsWith("match-"));
        }
    }

    @Test
    void hgetAll_emptyHash() {
        Map<String, String> result = proxy.hgetAll("nonexistent-hash");
        assertTrue(result.isEmpty());
    }

    @Test
    void hscan_withCountLimit() {
        for (int i = 0; i < 200; i++) {
            proxy.hset("hsc1", "f" + i, "v" + i);
        }
        // hscan with count=50 should return at most ~50 entries
        Map<String, String> partial = proxy.hscan("hsc1", 50);
        assertTrue(partial.size() >= 50);
        assertTrue(partial.size() <= 200);
    }

    @Test
    void hkeys_scansAllKeys() {
        int fieldCount = 500;
        for (int i = 0; i < fieldCount; i++) {
            proxy.hset("hk1", "key-" + i, "val-" + i);
        }

        Set<String> keys = proxy.hkeys("hk1");
        assertEquals(fieldCount, keys.size());
        for (int i = 0; i < fieldCount; i++) {
            assertTrue(keys.contains("key-" + i));
        }
    }

    // --- Set operations ---

    @Test
    void saddAndSmembers() {
        proxy.sadd("s1", "a");
        proxy.sadd("s1", "b", "c");
        // smembers uses SSCAN internally
        Set<String> members = proxy.smembers("s1");
        assertEquals(Set.of("a", "b", "c"), members);
    }

    @Test
    void smembers_largeSet() {
        int size = 2000;
        for (int i = 0; i < size; i++) {
            proxy.sadd("sl1", "member-" + i);
        }
        Set<String> members = proxy.smembers("sl1");
        assertEquals(size, members.size());
    }

    @Test
    void smembers2() {
        proxy.sadd("s2m", "x", "y", "z");
        Set<String> members = proxy.smembers2("s2m");
        assertEquals(Set.of("x", "y", "z"), members);
    }

    @Test
    void sremAndSismember() {
        proxy.sadd("sr1", "a", "b", "c");
        assertTrue(proxy.sismember("sr1", "b"));
        proxy.srem("sr1", "b");
        assertFalse(proxy.sismember("sr1", "b"));
    }

    @Test
    void sremMultiple() {
        proxy.sadd("srm1", "a", "b", "c", "d");
        proxy.srem("srm1", "a", "b");
        assertEquals(2L, proxy.scard("srm1"));
    }

    // --- Sorted set operations ---

    @Test
    void zaddAndZrange() {
        proxy.zadd("z1", 1.0, "a");
        proxy.zadd("z1", 2.0, "b");
        proxy.zadd("z1", 3.0, "c");

        List<String> range = proxy.zrange("z1", 0, -1);
        assertEquals(List.of("a", "b", "c"), range);
    }

    @Test
    void zaddnx() {
        proxy.zadd("znx1", 1.0, "a");
        proxy.zaddnx("znx1", 99.0, "a"); // should not update
        List<Tuple> tuples = proxy.zrangeWithScores("znx1", 0, -1);
        assertEquals(1.0, tuples.get(0).getScore());
    }

    @Test
    void zrangeByScore_withCount() {
        for (int i = 0; i < 100; i++) {
            proxy.zadd("zbs1", i, "m-" + String.format("%03d", i));
        }

        List<String> top10 = proxy.zrangeByScore("zbs1", 50.0, 10);
        assertEquals(10, top10.size());

        List<String> range = proxy.zrangeByScore("zbs1", 20.0, 30.0, 5);
        assertEquals(5, range.size());
    }

    @Test
    void zrangeByScoreWithScores() {
        proxy.zadd("zbsws1", 1.0, "a");
        proxy.zadd("zbsws1", 2.0, "b");
        proxy.zadd("zbsws1", 3.0, "c");

        List<Tuple> tuples = proxy.zrangeByScoreWithScores("zbsws1", 1.5, 3.0);
        assertEquals(2, tuples.size());
    }

    @Test
    void zremAndZremrangeByScore() {
        proxy.zadd("zr1", 1.0, "a");
        proxy.zadd("zr1", 2.0, "b");
        proxy.zadd("zr1", 3.0, "c");

        proxy.zrem("zr1", "b");
        assertEquals(2L, proxy.zcard("zr1"));

        proxy.zadd("zrr1", 1.0, "a");
        proxy.zadd("zrr1", 2.0, "b");
        proxy.zadd("zrr1", 3.0, "c");
        assertEquals(2L, proxy.zremrangeByScore("zrr1", "1", "2"));
    }

    @Test
    void zcount() {
        proxy.zadd("zc1", 1.0, "a");
        proxy.zadd("zc1", 2.0, "b");
        proxy.zadd("zc1", 3.0, "c");
        assertEquals(2L, proxy.zcount("zc1", 1.5, 3.0));
    }

    @Test
    void zscan() {
        for (int i = 0; i < 100; i++) {
            proxy.zadd("zsc1", i, "m-" + i);
        }
        Set<String> all = new HashSet<>();
        var result = proxy.zscan("zsc1", 0);
        for (Tuple t : result.getResult()) {
            all.add(t.getElement());
        }
        assertFalse(all.isEmpty());
    }

    // --- SCAN / findAll ---

    @Test
    void scan_findsByPrefix() {
        for (int i = 0; i < 50; i++) {
            proxy.set("scanp:" + i, "v" + i);
        }
        for (int i = 0; i < 30; i++) {
            proxy.set("noise:" + i, "x" + i);
        }

        List<String> found = proxy.scan("scanp:*", "0", 100);
        for (String key : found) {
            assertTrue(key.startsWith("scanp:"));
        }
    }

    @Test
    void findAll_scansEntireKeyspace() {
        int keyCount = 500;
        for (int i = 0; i < keyCount; i++) {
            proxy.set("findall:" + i, "v" + i);
        }
        // Add noise
        for (int i = 0; i < 50; i++) {
            proxy.set("other:" + i, "x" + i);
        }

        List<String> found = proxy.findAll("findall:*");
        assertEquals(keyCount, found.size());
        for (String key : found) {
            assertTrue(key.startsWith("findall:"));
        }
    }

    @Test
    void findAll_emptyResult() {
        List<String> found = proxy.findAll("nonexistent-prefix:*");
        assertTrue(found.isEmpty());
    }

    // --- Script / Info / Ping ---

    @Test
    void evalsha() {
        byte[] script = "return redis.call('get', KEYS[1])".getBytes(StandardCharsets.UTF_8);
        byte[] sampleKey = "eval1".getBytes(StandardCharsets.UTF_8);
        byte[] sha = proxy.scriptLoad(script, sampleKey);

        proxy.set("eval1", "hello");
        Object result =
                proxy.evalsha(new String(sha, StandardCharsets.UTF_8), List.of("eval1"), List.of());
        assertEquals("hello", result);
    }

    @Test
    void ping() {
        assertEquals("PONG", proxy.ping());
    }

    @Test
    void info() {
        String info = proxy.info("server");
        assertNotNull(info);
        assertTrue(info.contains("redis_version"));
    }
}
