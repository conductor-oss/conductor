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

import org.junit.Test;

import static org.junit.Assert.*;

public class TaskRateLimitOverrideTest {

    @Test
    public void testDefaultState() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // By default, both values should be null (not set)
        assertNull(
                "Default rateLimitPerFrequency should be null",
                override.getRateLimitPerFrequency());
        assertNull(
                "Default rateLimitFrequencyInSeconds should be null",
                override.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testPositiveValues() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // Set positive values
        override.setRateLimitPerFrequency(100);
        override.setRateLimitFrequencyInSeconds(60);

        // Verify values are set correctly
        assertEquals(Integer.valueOf(100), override.getRateLimitPerFrequency());
        assertEquals(Integer.valueOf(60), override.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testZeroValues() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // Zero is a valid value (no rate limiting)
        override.setRateLimitPerFrequency(0);
        override.setRateLimitFrequencyInSeconds(0);

        // Verify values are set correctly
        assertEquals(Integer.valueOf(0), override.getRateLimitPerFrequency());
        assertEquals(Integer.valueOf(0), override.getRateLimitFrequencyInSeconds());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeRateLimitPerFrequency() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // Should throw IllegalArgumentException
        override.setRateLimitPerFrequency(-10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeRateLimitFrequencyInSeconds() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // Should throw IllegalArgumentException
        override.setRateLimitFrequencyInSeconds(-5);
    }

    @Test
    public void testNullHandling() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // Set values first
        override.setRateLimitPerFrequency(100);
        override.setRateLimitFrequencyInSeconds(60);

        // Then set to null (unset)
        override.setRateLimitPerFrequency(null);
        override.setRateLimitFrequencyInSeconds(null);

        // Verify values are null again
        assertNull(
                "rateLimitPerFrequency should be null after setting to null",
                override.getRateLimitPerFrequency());
        assertNull(
                "rateLimitFrequencyInSeconds should be null after setting to null",
                override.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testFluentAPI() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride()
                        .withRateLimitPerFrequency(200)
                        .withRateLimitFrequencyInSeconds(30);

        // Verify fluent API set values correctly
        assertEquals(Integer.valueOf(200), override.getRateLimitPerFrequency());
        assertEquals(Integer.valueOf(30), override.getRateLimitFrequencyInSeconds());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFluentAPIWithNegativeValue() {
        // Should throw IllegalArgumentException
        new StartWorkflowRequest.TaskRateLimitOverride().withRateLimitPerFrequency(-50);
    }

    @Test
    public void testSetUnsetState() {
        StartWorkflowRequest.TaskRateLimitOverride override =
                new StartWorkflowRequest.TaskRateLimitOverride();

        // Initially both should be null
        assertNull(override.getRateLimitPerFrequency());
        assertNull(override.getRateLimitFrequencyInSeconds());

        // Set only one value
        override.setRateLimitPerFrequency(100);

        // One should be set, one should be null
        assertEquals(Integer.valueOf(100), override.getRateLimitPerFrequency());
        assertNull(override.getRateLimitFrequencyInSeconds());

        // Set the other value
        override.setRateLimitFrequencyInSeconds(60);

        // Both should be set
        assertEquals(Integer.valueOf(100), override.getRateLimitPerFrequency());
        assertEquals(Integer.valueOf(60), override.getRateLimitFrequencyInSeconds());

        // Unset one value
        override.setRateLimitPerFrequency(null);

        // One should be null, one should be set
        assertNull(override.getRateLimitPerFrequency());
        assertEquals(Integer.valueOf(60), override.getRateLimitFrequencyInSeconds());
    }
}
