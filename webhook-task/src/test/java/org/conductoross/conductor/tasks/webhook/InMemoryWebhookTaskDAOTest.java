/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InMemoryWebhookTaskDAOTest {

    private InMemoryWebhookTaskDAO dao;

    @Before
    public void setUp() {
        dao = new InMemoryWebhookTaskDAO();
    }

    @Test
    public void get_returnsEmptyListForUnknownHash() {
        List<String> result = dao.get("nonexistent-hash");
        assertTrue(result.isEmpty());
    }

    @Test
    public void put_thenGet_returnsTaskId() {
        dao.put("hash-1", "task-a");

        List<String> result = dao.get("hash-1");

        assertEquals(1, result.size());
        assertTrue(result.contains("task-a"));
    }

    @Test
    public void put_multipleTasksUnderSameHash() {
        dao.put("hash-1", "task-a");
        dao.put("hash-1", "task-b");
        dao.put("hash-1", "task-c");

        List<String> result = dao.get("hash-1");

        assertEquals(3, result.size());
        assertTrue(result.contains("task-a"));
        assertTrue(result.contains("task-b"));
        assertTrue(result.contains("task-c"));
    }

    @Test
    public void put_differentHashesAreIndependent() {
        dao.put("hash-1", "task-a");
        dao.put("hash-2", "task-b");

        assertTrue(dao.get("hash-1").contains("task-a"));
        assertFalse(dao.get("hash-1").contains("task-b"));
        assertTrue(dao.get("hash-2").contains("task-b"));
        assertFalse(dao.get("hash-2").contains("task-a"));
    }

    @Test
    public void remove_deregistersTaskId() {
        dao.put("hash-1", "task-a");
        dao.put("hash-1", "task-b");

        dao.remove("hash-1", "task-a");

        List<String> result = dao.get("hash-1");
        assertFalse(result.contains("task-a"));
        assertTrue(result.contains("task-b"));
    }

    @Test
    public void remove_lastTaskCleansUpHash() {
        dao.put("hash-1", "task-a");
        dao.remove("hash-1", "task-a");

        assertTrue(dao.get("hash-1").isEmpty());
    }

    @Test
    public void remove_unknownHashIsNoOp() {
        // should not throw
        dao.remove("nonexistent", "task-a");
    }

    @Test
    public void remove_unknownTaskIdUnderKnownHashIsNoOp() {
        dao.put("hash-1", "task-a");

        dao.remove("hash-1", "task-z");

        assertTrue(dao.get("hash-1").contains("task-a"));
    }

    @Test
    public void put_idempotent_duplicateTaskIdNotAddedTwice() {
        dao.put("hash-1", "task-a");
        dao.put("hash-1", "task-a");

        assertEquals(1, dao.get("hash-1").size());
    }
}
