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
package org.conductoross.conductor.postgres.dao;

import java.util.Map;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.postgres.config.PostgresConfiguration;

import static org.conductoross.conductor.service.webhook.WebhookTaskHashing.computeHash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@TestPropertySource(
        properties = {
            "conductor.db.type=postgres",
            "spring.flyway.clean-disabled=false",
            "conductor.app.workflow.name-validation.enabled=true",
            "spring.flyway.ignore-migration-patterns=*:missing"
        })
@SpringBootTest
public class PostgresWebhookTaskServiceTest {

    @Autowired private PostgresWebhookTaskService service;
    @Autowired private Flyway flyway;

    @Before
    public void before() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    public void put_and_get_matchByHash() {
        TaskModel task = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));

        service.put(task, 1);

        String hash =
                computeHash(
                        "wf-a",
                        1,
                        TaskUtils.removeIterationFromTaskRefName("wait_ref"),
                        Map.of("event", "push"));
        assertEquals(Set.of("task-1"), service.get(hash));
    }

    @Test
    public void put_missingMatchesField_throws() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-x");
        task.setInputData(Map.of()); // no "matches"

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
        assertTrue(ids.contains("task-1"));
        assertTrue(ids.contains("task-2"));
        assertEquals(2, ids.size());
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
        service.put(task, 1); // second put must not throw on PK conflict

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
