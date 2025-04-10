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
package com.netflix.conductor.common.metadata.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskExecLogPojoMethods {

    @Test
    void testNoArgsConstructor() {
        TaskExecLog log = new TaskExecLog();
        assertNull(log.getLog());
        assertNull(log.getTaskId());
        assertEquals(0L, log.getCreatedTime());
    }

    @Test
    void testConstructorWithLog() {
        String logMessage = "Test log message";
        TaskExecLog log = new TaskExecLog(logMessage);

        assertEquals(logMessage, log.getLog());
        assertNull(log.getTaskId());
        assertTrue(log.getCreatedTime() > 0);
        // Verify creation time is close to current time
        long now = System.currentTimeMillis();
        assertTrue(now - log.getCreatedTime() < 1000);
    }

    @Test
    void testSetAndGetLog() {
        TaskExecLog log = new TaskExecLog();
        String logMessage = "Test log message";
        log.setLog(logMessage);
        assertEquals(logMessage, log.getLog());
    }

    @Test
    void testSetAndGetTaskId() {
        TaskExecLog log = new TaskExecLog();
        String taskId = "task-123";
        log.setTaskId(taskId);
        assertEquals(taskId, log.getTaskId());
    }

    @Test
    void testSetAndGetCreatedTime() {
        TaskExecLog log = new TaskExecLog();
        long createdTime = 1234567890L;
        log.setCreatedTime(createdTime);
        assertEquals(createdTime, log.getCreatedTime());
    }

    @Test
    void testEqualsWithSameObject() {
        TaskExecLog log = new TaskExecLog("test");
        log.setTaskId("task-123");
        log.setCreatedTime(1234567890L);

        assertTrue(log.equals(log));
    }

    @Test
    void testEqualsWithNull() {
        TaskExecLog log = new TaskExecLog("test");
        assertFalse(log.equals(null));
    }

    @Test
    void testEqualsWithDifferentClass() {
        TaskExecLog log = new TaskExecLog("test");
        assertFalse(log.equals("test"));
    }

    @Test
    void testEqualsWithEqualObjects() {
        TaskExecLog log1 = new TaskExecLog("test");
        log1.setTaskId("task-123");
        log1.setCreatedTime(1234567890L);

        TaskExecLog log2 = new TaskExecLog("test");
        log2.setTaskId("task-123");
        log2.setCreatedTime(1234567890L);

        assertTrue(log1.equals(log2));
        assertTrue(log2.equals(log1));
    }

    @Test
    void testEqualsWithDifferentLog() {
        TaskExecLog log1 = new TaskExecLog("test1");
        log1.setTaskId("task-123");
        log1.setCreatedTime(1234567890L);

        TaskExecLog log2 = new TaskExecLog("test2");
        log2.setTaskId("task-123");
        log2.setCreatedTime(1234567890L);

        assertFalse(log1.equals(log2));
    }

    @Test
    void testEqualsWithDifferentTaskId() {
        TaskExecLog log1 = new TaskExecLog("test");
        log1.setTaskId("task-123");
        log1.setCreatedTime(1234567890L);

        TaskExecLog log2 = new TaskExecLog("test");
        log2.setTaskId("task-456");
        log2.setCreatedTime(1234567890L);

        assertFalse(log1.equals(log2));
    }

    @Test
    void testEqualsWithDifferentCreatedTime() {
        TaskExecLog log1 = new TaskExecLog("test");
        log1.setTaskId("task-123");
        log1.setCreatedTime(1234567890L);

        TaskExecLog log2 = new TaskExecLog("test");
        log2.setTaskId("task-123");
        log2.setCreatedTime(9876543210L);

        assertFalse(log1.equals(log2));
    }

    @Test
    void testHashCode() {
        TaskExecLog log1 = new TaskExecLog("test");
        log1.setTaskId("task-123");
        log1.setCreatedTime(1234567890L);

        TaskExecLog log2 = new TaskExecLog("test");
        log2.setTaskId("task-123");
        log2.setCreatedTime(1234567890L);

        assertEquals(log1.hashCode(), log2.hashCode());

        // Changing a field should change the hash code
        log2.setTaskId("task-456");
        assertNotEquals(log1.hashCode(), log2.hashCode());
    }
}