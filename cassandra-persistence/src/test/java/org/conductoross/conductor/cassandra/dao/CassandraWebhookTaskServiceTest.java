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
package org.conductoross.conductor.cassandra.dao;

import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.CassandraContainer;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.conductoross.conductor.service.webhook.WebhookTaskHashing.computeHash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CassandraWebhookTaskServiceTest {

    private static final String KEYSPACE = "conductor_test";

    @ClassRule
    public static final CassandraContainer<?> cassandra =
            new CassandraContainer<>("cassandra:3.11.2");

    private static Session session;
    private static ObjectMapper objectMapper;
    private CassandraWebhookTaskService service;

    @BeforeClass
    public static void setUpOnce() {
        session = cassandra.getCluster().newSession();
        session.execute(
                "CREATE KEYSPACE IF NOT EXISTS "
                        + KEYSPACE
                        + " WITH replication = {'class':'SimpleStrategy','replication_factor':1}");
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    @Before
    public void setUp() {
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".webhook_hash_to_taskid");
        CassandraProperties props = new CassandraProperties();
        props.setKeyspace(KEYSPACE);
        service = new CassandraWebhookTaskService(session, objectMapper, props);
    }

    @AfterClass
    public static void tearDown() {
        if (session != null) session.close();
    }

    @Test
    public void put_and_get_matchByHash() {
        TaskModel task = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        service.put(task, 1);
        String hash = computeHash("wf-a", 1, "wait_ref", Map.of("event", "push"));
        assertEquals(Set.of("task-1"), service.get(hash));
    }

    @Test
    public void put_missingMatchesField_throws() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-x");
        task.setInputData(Map.of());
        assertThrows(NonTransientException.class, () -> service.put(task, 1));
    }

    @Test
    public void multiple_tasks_sameHash_bucketed() {
        TaskModel t1 = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        TaskModel t2 = newTask("task-2", "wf-a", "wait_ref", Map.of("event", "push"));
        service.put(t1, 1);
        service.put(t2, 1);
        String hash = computeHash("wf-a", 1, "wait_ref", Map.of("event", "push"));
        Set<String> ids = service.get(hash);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("task-1"));
        assertTrue(ids.contains("task-2"));
    }

    @Test
    public void remove_evictsTaskAndLeavesOthers() {
        TaskModel t1 = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        TaskModel t2 = newTask("task-2", "wf-a", "wait_ref", Map.of("event", "push"));
        service.put(t1, 1);
        service.put(t2, 1);
        String hash = computeHash("wf-a", 1, "wait_ref", Map.of("event", "push"));
        service.remove(hash, "task-1");
        assertEquals(Set.of("task-2"), service.get(hash));
    }

    @Test
    public void put_isIdempotent() {
        TaskModel task = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        service.put(task, 1);
        service.put(task, 1);
        String hash = computeHash("wf-a", 1, "wait_ref", Map.of("event", "push"));
        assertEquals(Set.of("task-1"), service.get(hash));
    }

    @Test
    public void get_unknownHash_returnsEmpty() {
        assertTrue(service.get("never-seen").isEmpty());
    }

    private static TaskModel newTask(
            String taskId, String workflowType, String taskRef, Map<String, Object> matches) {
        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setWorkflowType(workflowType);
        task.setReferenceTaskName(taskRef);
        task.setInputData(Map.of("matches", matches));
        return task;
    }
}
