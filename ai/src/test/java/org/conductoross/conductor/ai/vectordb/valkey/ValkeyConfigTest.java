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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValkeyConfig: default values, validation at construction, and the VectorDBConfig
 * contract (get() throws, get(name) constructs).
 */
class ValkeyConfigTest {

    @Test
    void defaults_areCorrect() {
        ValkeyConfig config = new ValkeyConfig();
        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
        assertEquals(0, config.getDatabase());
        assertFalse(config.getUseTls());
        assertEquals(256, config.getDimensions());
        assertEquals("cosine", config.getDistanceMetric());
        assertEquals("hnsw", config.getIndexingMethod());
        assertEquals("conductor", config.getKeyPrefix());
        assertEquals(2000, config.getRequestTimeoutMs());
    }

    @Test
    void get_throwsUnsupported() {
        ValkeyConfig config = new ValkeyConfig();
        assertThrows(UnsupportedOperationException.class, config::get);
    }

    @Test
    void getWithName_invalidMetric_throwsAtConstruction() {
        ValkeyConfig config = new ValkeyConfig();
        config.setDistanceMetric("manhattan");
        assertThrows(IllegalArgumentException.class, () -> config.get("test"));
    }

    @Test
    void getWithName_invalidAlgorithm_throwsAtConstruction() {
        ValkeyConfig config = new ValkeyConfig();
        config.setIndexingMethod("annoy");
        assertThrows(IllegalArgumentException.class, () -> config.get("test"));
    }

    @Test
    void passwordIsExcludedFromToString() {
        ValkeyConfig config = new ValkeyConfig();
        config.setPassword("s3cret");
        String toString = config.toString();
        assertFalse(toString.contains("s3cret"), "Password leaked in toString(): " + toString);
    }

    @Test
    void resolveDistanceMetric_usesLocaleRoot() {
        // Turkish locale would map "IP" to "\u0131p" which would not match "ip"
        // Using Locale.ROOT ensures this works regardless of JVM locale
        assertEquals(
                glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.IP,
                ValkeyConfig.resolveDistanceMetric("IP"));
    }

    @Test
    void resolveIndexingMethod_usesLocaleRoot() {
        assertEquals("hnsw", ValkeyConfig.resolveIndexingMethod("HNSW"));
        assertEquals("flat", ValkeyConfig.resolveIndexingMethod("FLAT"));
    }
}
