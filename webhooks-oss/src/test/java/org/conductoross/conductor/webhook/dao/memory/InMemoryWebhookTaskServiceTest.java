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
package org.conductoross.conductor.webhook.dao.memory;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryWebhookTaskServiceTest {

    private InMemoryWebhookTaskService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryWebhookTaskService();
    }

    @Test
    void put_and_get_matchByHash() {
        TaskModel task = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));

        service.put(task, 1);

        // Same matches map produces same hash on retrieval
        assertThat(service.get("wf-a;1;wait_ref;push")).containsExactly("task-1");
    }

    @Test
    void put_missingMatchesField_throws() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setInputData(Map.of()); // no "matches"

        assertThatThrownBy(() -> service.put(task, 1))
                .isInstanceOf(NonTransientException.class)
                .hasMessageContaining("matches");
    }

    @Test
    void multiple_tasks_sameHash_bucketed() {
        TaskModel t1 = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        TaskModel t2 = newTask("task-2", "wf-a", "wait_ref", Map.of("event", "push"));

        service.put(t1, 1);
        service.put(t2, 1);

        assertThat(service.get("wf-a;1;wait_ref;push"))
                .containsExactlyInAnyOrder("task-1", "task-2");
    }

    @Test
    void remove_evictsOneTaskAndKeepsBucketIfNonEmpty() {
        TaskModel t1 = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        TaskModel t2 = newTask("task-2", "wf-a", "wait_ref", Map.of("event", "push"));
        service.put(t1, 1);
        service.put(t2, 1);

        service.remove("wf-a;1;wait_ref;push", "task-1");

        assertThat(service.get("wf-a;1;wait_ref;push")).containsExactly("task-2");
    }

    @Test
    void remove_lastTask_dropsBucket() {
        TaskModel t1 = newTask("task-1", "wf-a", "wait_ref", Map.of("event", "push"));
        service.put(t1, 1);

        service.remove("wf-a;1;wait_ref;push", "task-1");

        assertThat(service.get("wf-a;1;wait_ref;push")).isEmpty();
    }

    @Test
    void get_unknownHash_returnsEmpty() {
        assertThat(service.get("never-seen")).isEmpty();
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
