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
package com.netflix.conductor.core.execution.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.model.TaskModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JoinTest {

    @Test
    public void testSynchronousJoinModeEvaluationOffset() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);

        TaskModel task = new TaskModel();
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("joinMode", "SYNC");
        task.setInputData(inputData);
        task.setPollCount(100);

        // Synchronous mode should always return 0 offset
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());

        // Even with high poll count, SYNC mode returns 0
        task.setPollCount(500);
        offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());
    }

    @Test
    public void testAsynchronousJoinModeEvaluationOffset() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);

        TaskModel task = new TaskModel();
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("joinMode", "ASYNC");
        task.setInputData(inputData);

        // Low poll count should return 0
        task.setPollCount(100);
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());

        // High poll count should use exponential backoff
        task.setPollCount(250);
        offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertTrue(offset.get() > 0L);
    }

    @Test
    public void testDefaultAsyncBehavior() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);

        TaskModel task = new TaskModel();
        Map<String, Object> inputData = new HashMap<>();
        // No joinMode specified - should default to async behavior
        task.setInputData(inputData);

        // Low poll count should return 0
        task.setPollCount(100);
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());

        // High poll count should use exponential backoff (default async behavior)
        task.setPollCount(250);
        offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertTrue(offset.get() > 0L);
    }

    @Test
    public void testJoinModeCaseInsensitive() {
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskPostponeThreshold()).thenReturn(200);

        Join join = new Join(properties);

        TaskModel task = new TaskModel();
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("joinMode", "sync"); // lowercase
        task.setInputData(inputData);
        task.setPollCount(500);

        // Should still be treated as SYNC mode
        Optional<Long> offset = join.getEvaluationOffset(task, 10000L);
        assertTrue(offset.isPresent());
        assertEquals(0L, offset.get().longValue());
    }

    @Test
    public void testIsAsync() {
        ConductorProperties properties = mock(ConductorProperties.class);
        Join join = new Join(properties);

        // isAsync should always return true
        assertTrue(join.isAsync());
    }
}
