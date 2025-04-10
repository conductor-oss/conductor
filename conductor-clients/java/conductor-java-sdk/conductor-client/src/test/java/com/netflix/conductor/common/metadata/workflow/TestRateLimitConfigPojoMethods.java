/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestRateLimitConfigPojoMethods {

    @Test
    public void testDefaultConstructor() {
        RateLimitConfig rateLimitConfig = new RateLimitConfig();
        assertNotNull(rateLimitConfig);
        assertNull(rateLimitConfig.getRateLimitKey());
        assertEquals(0, rateLimitConfig.getConcurrentExecLimit());
    }

    @Test
    public void testSetAndGetRateLimitKey() {
        RateLimitConfig rateLimitConfig = new RateLimitConfig();

        // Test with null value
        rateLimitConfig.setRateLimitKey(null);
        assertNull(rateLimitConfig.getRateLimitKey());

        // Test with empty string
        rateLimitConfig.setRateLimitKey("");
        assertEquals("", rateLimitConfig.getRateLimitKey());

        // Test with non-empty string
        String testKey = "workflow.name";
        rateLimitConfig.setRateLimitKey(testKey);
        assertEquals(testKey, rateLimitConfig.getRateLimitKey());
    }

    @Test
    public void testSetAndGetConcurrentExecLimit() {
        RateLimitConfig rateLimitConfig = new RateLimitConfig();

        // Test with zero
        rateLimitConfig.setConcurrentExecLimit(0);
        assertEquals(0, rateLimitConfig.getConcurrentExecLimit());

        // Test with positive value
        rateLimitConfig.setConcurrentExecLimit(10);
        assertEquals(10, rateLimitConfig.getConcurrentExecLimit());

        // Test with negative value
        rateLimitConfig.setConcurrentExecLimit(-5);
        assertEquals(-5, rateLimitConfig.getConcurrentExecLimit());
    }

    @Test
    public void testObjectState() {
        RateLimitConfig rateLimitConfig = new RateLimitConfig();

        // Set values
        String testKey = "workflow.correlationId";
        int testLimit = 5;

        rateLimitConfig.setRateLimitKey(testKey);
        rateLimitConfig.setConcurrentExecLimit(testLimit);

        // Verify state is maintained
        assertEquals(testKey, rateLimitConfig.getRateLimitKey());
        assertEquals(testLimit, rateLimitConfig.getConcurrentExecLimit());
    }
}