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
package org.conductoross.conductor.ai.vectordb.valkey;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.conductoross.conductor.ai.vectordb.VectorDB;
import org.conductoross.conductor.ai.vectordb.VectorDBInstanceConfig;
import org.conductoross.conductor.ai.vectordb.VectorDBInstanceConfig.VectorDBInstance;
import org.junit.jupiter.api.Test;

import glide.api.GlideClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests that the Valkey type aliases ("valkey", "valkeyvectordb") are correctly wired in
 * VectorDBInstanceConfig and route to ValkeyVectorDB instances. Uses a test-specific ValkeyConfig
 * subclass to inject a mock client, proving the routing actually produces a ValkeyVectorDB.
 */
class ValkeyInstanceConfigTest {

    /**
     * A ValkeyConfig subclass that overrides get(String) to return a ValkeyVectorDB with a mocked
     * client, proving the routing path exercises the ValkeyConfig.get(name) method.
     */
    static class TestValkeyConfig extends ValkeyConfig {
        private final ValkeyVectorDB mockDb;

        TestValkeyConfig(ValkeyVectorDB mockDb) {
            this.mockDb = mockDb;
        }

        @Override
        public ValkeyVectorDB get(String name) {
            return mockDb;
        }
    }

    @Test
    void valkeyAlias_routesToValkeyVectorDB() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyConfig realConfig = new ValkeyConfig();
        realConfig.setDimensions(4);
        ValkeyVectorDB expectedDb = new ValkeyVectorDB("test-valkey", realConfig, mockClient);

        TestValkeyConfig testConfig = new TestValkeyConfig(expectedDb);

        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstance instance = new VectorDBInstance();
        instance.setName("test-valkey");
        instance.setType("valkey");
        instance.setValkey(testConfig);
        instanceConfig.setInstances(List.of(instance));

        Map<String, VectorDB> result = instanceConfig.getVectorDBInstances();

        // Assert the routed instance IS the expected ValkeyVectorDB
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("test-valkey"));
        assertSame(expectedDb, result.get("test-valkey"));
        assertEquals("valkey", result.get("test-valkey").getType());
    }

    @Test
    void valkeyvectordbAlias_routesToValkeyVectorDB() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyConfig realConfig = new ValkeyConfig();
        realConfig.setDimensions(4);
        ValkeyVectorDB expectedDb = new ValkeyVectorDB("test-vdb", realConfig, mockClient);

        TestValkeyConfig testConfig = new TestValkeyConfig(expectedDb);

        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstance instance = new VectorDBInstance();
        instance.setName("test-vdb");
        instance.setType("valkeyvectordb");
        instance.setValkey(testConfig);
        instanceConfig.setInstances(List.of(instance));

        Map<String, VectorDB> result = instanceConfig.getVectorDBInstances();

        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("test-vdb"));
        assertSame(expectedDb, result.get("test-vdb"));
    }

    @Test
    void valkeyAlias_missingConfig_returnsEmpty() {
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstance instance = new VectorDBInstance();
        instance.setName("test-valkey");
        instance.setType("valkey");
        instance.setValkey(null); // Missing config path
        instanceConfig.setInstances(List.of(instance));

        Map<String, VectorDB> result = instanceConfig.getVectorDBInstances();
        // Should be empty because config is null (error logged)
        assertTrue(result.isEmpty());
    }

    @Test
    void unknownType_returnsEmpty() {
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstance instance = new VectorDBInstance();
        instance.setName("bad");
        instance.setType("valkeyy"); // typo - unknown type
        instanceConfig.setInstances(List.of(instance));

        Map<String, VectorDB> result = instanceConfig.getVectorDBInstances();
        assertTrue(result.isEmpty());
    }

    /**
     * A ValkeyConfig subclass whose get(String) always throws, simulating a construction failure.
     */
    static class ThrowingValkeyConfig extends ValkeyConfig {
        @Override
        public ValkeyVectorDB get(String name) {
            throw new IllegalArgumentException("invalid distanceMetric for instance " + name);
        }
    }

    @Test
    void instanceConstructionFailure_throwsAggregateException() {
        // An instance whose construction throws must fail startup loudly, not
        // disappear silently the way a missing-config or unknown-type instance does.
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstance instance = new VectorDBInstance();
        instance.setName("bad-valkey");
        instance.setType("valkey");
        instance.setValkey(new ThrowingValkeyConfig());
        instanceConfig.setInstances(List.of(instance));

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, instanceConfig::getVectorDBInstances);
        assertTrue(ex.getMessage().contains("bad-valkey"));
        assertEquals(1, ex.getSuppressed().length);
        assertInstanceOf(IllegalArgumentException.class, ex.getSuppressed()[0]);
    }

    @Test
    void multipleInstanceFailures_areAllReportedInAggregateException() {
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();

        VectorDBInstance first = new VectorDBInstance();
        first.setName("bad-valkey-1");
        first.setType("valkey");
        first.setValkey(new ThrowingValkeyConfig());

        VectorDBInstance second = new VectorDBInstance();
        second.setName("bad-valkey-2");
        second.setType("valkey");
        second.setValkey(new ThrowingValkeyConfig());

        instanceConfig.setInstances(List.of(first, second));

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, instanceConfig::getVectorDBInstances);
        assertTrue(ex.getMessage().contains("bad-valkey-1"));
        assertTrue(ex.getMessage().contains("bad-valkey-2"));
        assertEquals(2, ex.getSuppressed().length);
    }

    /**
     * A ValkeyConfig subclass whose get(String) throws a plain RuntimeException, simulating a
     * connectivity failure (e.g. an unreachable server) rather than a configuration error.
     */
    static class ConnectionFailingValkeyConfig extends ValkeyConfig {
        @Override
        public ValkeyVectorDB get(String name) {
            throw new RuntimeException("Connection refused");
        }
    }

    @Test
    void connectionFailure_isLoggedAndSkipped_doesNotAbortStartup() {
        // Unlike a configuration error (IllegalArgumentException/IllegalStateException), a runtime
        // connectivity failure must not take down the whole server -- it should behave like the
        // other vector DB backends, which tolerate an unreachable instance at boot.
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstance instance = new VectorDBInstance();
        instance.setName("unreachable-valkey");
        instance.setType("valkey");
        instance.setValkey(new ConnectionFailingValkeyConfig());
        instanceConfig.setInstances(List.of(instance));

        Map<String, VectorDB> result = assertDoesNotThrow(instanceConfig::getVectorDBInstances);
        assertTrue(result.isEmpty());
    }

    @Test
    void successfulInstanceIsClosed_whenLaterInstanceFails() throws ExecutionException {
        // Without closing already-created instances before the aggregate throw, a successfully
        // opened GLIDE client would leak: VectorDBProvider is never constructed to close it later.
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyConfig successConfig = new ValkeyConfig();
        successConfig.setDimensions(4);
        ValkeyVectorDB successDb = new ValkeyVectorDB("good-valkey", successConfig, mockClient);
        TestValkeyConfig testConfig = new TestValkeyConfig(successDb);

        VectorDBInstance good = new VectorDBInstance();
        good.setName("good-valkey");
        good.setType("valkey");
        good.setValkey(testConfig);

        VectorDBInstance bad = new VectorDBInstance();
        bad.setName("bad-valkey");
        bad.setType("valkey");
        bad.setValkey(new ThrowingValkeyConfig());

        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        instanceConfig.setInstances(List.of(good, bad));

        assertThrows(IllegalStateException.class, instanceConfig::getVectorDBInstances);

        verify(mockClient).close();
    }
}
