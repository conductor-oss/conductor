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
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.redis.testutil.FixedPortContainer;

import com.google.common.util.concurrent.Uninterruptibles;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JedisClusterCommands} batch operations (mget, mgetBytes, mset) using
 * a real Redis Cluster via TestContainers. Keys are spread across shards by design.
 */
public class JedisClusterCommandsBatchTest {

    static int[] ports = new int[] {7000, 7001, 7002, 7003, 7004, 7005};

    private static FixedPortContainer redis =
            new FixedPortContainer(DockerImageName.parse("orkesio/redis-cluster"));

    static {
        redis.exposePort(7000, 7000);
        redis.exposePort(7001, 7001);
        redis.exposePort(7002, 7002);
        redis.exposePort(7003, 7003);
        redis.exposePort(7004, 7004);
        redis.exposePort(7005, 7005);
    }

    private static JedisClusterCommands commands;
    private static JedisCluster jedisCluster;

    @BeforeAll
    public static void setUp() {
        redis.withStartupTimeout(Duration.ofSeconds(120)).start();
        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);

        Set<HostAndPort> hostAndPorts = new HashSet<>();
        for (int port : ports) {
            hostAndPorts.add(new HostAndPort("localhost", port));
        }

        jedisCluster = new JedisCluster(hostAndPorts);
        commands = new JedisClusterCommands(jedisCluster);
    }

    @Test
    void mget_withKeysAcrossShards_returnsAllValues() {
        int keyCount = 2000;
        String[] keys = new String[keyCount];
        Map<String, String> expected = new HashMap<>();

        for (int i = 0; i < keyCount; i++) {
            // UUID keys ensure distribution across different hash slots
            keys[i] = "mget-test:" + UUID.randomUUID();
            expected.put(keys[i], "value-" + i);
            jedisCluster.set(keys[i], "value-" + i);
        }

        List<String> results = commands.mget(keys);

        assertEquals(keyCount, results.size());
        // All expected values should be present (order may differ due to null filtering)
        Set<String> resultSet = new HashSet<>(results);
        for (String value : expected.values()) {
            assertTrue(resultSet.contains(value), "Missing value: " + value);
        }
    }

    @Test
    void mget_withMissingKeys_filtersNulls() {
        int existingCount = 1000;
        int missingCount = 500;
        String[] keys = new String[existingCount + missingCount];

        for (int i = 0; i < existingCount; i++) {
            keys[i] = "mget-exists:" + UUID.randomUUID();
            jedisCluster.set(keys[i], "val-" + i);
        }
        for (int i = 0; i < missingCount; i++) {
            keys[existingCount + i] = "mget-missing:" + UUID.randomUUID();
        }

        List<String> results = commands.mget(keys);

        assertEquals(existingCount, results.size());
    }

    @Test
    void mgetBytes_withKeysAcrossShards_returnsAllValues() {
        int keyCount = 2000;
        byte[][] keys = new byte[keyCount][];
        Set<String> expectedValues = new HashSet<>();

        for (int i = 0; i < keyCount; i++) {
            String keyStr = "mgetb-test:" + UUID.randomUUID();
            String valStr = "bytes-value-" + i;
            keys[i] = keyStr.getBytes(StandardCharsets.UTF_8);
            expectedValues.add(valStr);
            jedisCluster.set(keys[i], valStr.getBytes(StandardCharsets.UTF_8));
        }

        List<byte[]> results = commands.mgetBytes(keys);

        assertEquals(keyCount, results.size());
        Set<String> resultSet = new HashSet<>();
        for (byte[] r : results) {
            resultSet.add(new String(r, StandardCharsets.UTF_8));
        }
        assertEquals(expectedValues, resultSet);
    }

    @Test
    void mgetBytes_withMissingKeys_filtersNullsAndEmpty() {
        int existingCount = 1000;
        int missingCount = 500;
        byte[][] keys = new byte[existingCount + missingCount][];

        for (int i = 0; i < existingCount; i++) {
            String keyStr = "mgetb-exists:" + UUID.randomUUID();
            keys[i] = keyStr.getBytes(StandardCharsets.UTF_8);
            jedisCluster.set(keys[i], ("val-" + i).getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < missingCount; i++) {
            keys[existingCount + i] =
                    ("mgetb-missing:" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        }

        List<byte[]> results = commands.mgetBytes(keys);

        assertEquals(existingCount, results.size());
    }

    @Test
    void mset_withKeysAcrossShards_writesAllValues() {
        int keyCount = 2000;
        byte[][] keyvalues = new byte[keyCount * 2][];
        byte[][] keys = new byte[keyCount][];

        for (int i = 0; i < keyCount; i++) {
            String keyStr = "mset-test:" + UUID.randomUUID();
            String valStr = "mset-value-" + i;
            keys[i] = keyStr.getBytes(StandardCharsets.UTF_8);
            keyvalues[i * 2] = keys[i];
            keyvalues[i * 2 + 1] = valStr.getBytes(StandardCharsets.UTF_8);
        }

        commands.mset(keyvalues);

        // Verify all values were written by reading them back
        for (int i = 0; i < keyCount; i++) {
            byte[] value = jedisCluster.get(keys[i]);
            assertNotNull(
                    value,
                    "Key " + new String(keys[i], StandardCharsets.UTF_8) + " was not written");
            String expected = "mset-value-" + i;
            assertEquals(expected, new String(value, StandardCharsets.UTF_8));
        }
    }

    @Test
    void mset_then_mgetBytes_roundtrip() {
        int keyCount = 5000;
        byte[][] keyvalues = new byte[keyCount * 2][];
        byte[][] keys = new byte[keyCount][];
        Set<String> expectedValues = new HashSet<>();

        for (int i = 0; i < keyCount; i++) {
            String keyStr = "roundtrip:" + UUID.randomUUID();
            String valStr = "rt-value-" + i;
            keys[i] = keyStr.getBytes(StandardCharsets.UTF_8);
            keyvalues[i * 2] = keys[i];
            keyvalues[i * 2 + 1] = valStr.getBytes(StandardCharsets.UTF_8);
            expectedValues.add(valStr);
        }

        commands.mset(keyvalues);
        List<byte[]> results = commands.mgetBytes(keys);

        assertEquals(keyCount, results.size());
        Set<String> resultSet = new HashSet<>();
        for (byte[] r : results) {
            resultSet.add(new String(r, StandardCharsets.UTF_8));
        }
        assertEquals(expectedValues, resultSet);
    }
}
