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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class WaitForWebhookTaskTest {

    private WebhookTaskDAO webhookTaskDAO;
    private WaitForWebhookTask task;
    private WorkflowModel workflow;

    @Before
    public void setUp() {
        webhookTaskDAO = mock(WebhookTaskDAO.class);
        task = new WaitForWebhookTask(webhookTaskDAO);
        workflow = new WorkflowModel();
    }

    @Test
    public void start_setsTaskInProgress() {
        TaskModel taskModel = new TaskModel();
        taskModel.setInputData(new HashMap<>());

        task.start(workflow, taskModel, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, taskModel.getStatus());
    }

    @Test
    public void execute_returnsFalse() {
        TaskModel taskModel = new TaskModel();
        taskModel.setInputData(new HashMap<>());

        boolean result = task.execute(workflow, taskModel, null);

        assertFalse(result);
    }

    @Test
    public void cancel_setsTaskCanceled() {
        TaskModel taskModel = new TaskModel();
        taskModel.setInputData(new HashMap<>());

        task.cancel(workflow, taskModel, null);

        assertEquals(TaskModel.Status.CANCELED, taskModel.getStatus());
    }

    @Test
    public void cancel_removesFromDAOWhenHashPresent() {
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task-123");
        taskModel.setInputData(Map.of("__webhookHash", "abc123"));

        task.cancel(workflow, taskModel, null);

        verify(webhookTaskDAO).remove("abc123", "task-123");
    }

    @Test
    public void cancel_doesNotCallDAOWhenNoHash() {
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task-456");
        taskModel.setInputData(new HashMap<>());

        // should not throw
        task.cancel(workflow, taskModel, null);

        assertEquals(TaskModel.Status.CANCELED, taskModel.getStatus());
    }

    @Test
    public void complete_setsTaskCompleted() {
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task-789");
        taskModel.setOutputData(new HashMap<>());

        task.complete(taskModel, Map.of("event", "payment.completed"), "hash-xyz");

        assertEquals(TaskModel.Status.COMPLETED, taskModel.getStatus());
    }

    @Test
    public void complete_storesPayloadInOutput() {
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task-789");
        taskModel.setOutputData(new HashMap<>());
        Map<String, Object> payload = Map.of("orderId", "ORD-001", "amount", 99.99);

        task.complete(taskModel, payload, "hash-xyz");

        assertEquals(payload, taskModel.getOutputData().get("payload"));
    }

    @Test
    public void complete_removesFromDAO() {
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("task-789");
        taskModel.setOutputData(new HashMap<>());

        task.complete(taskModel, Map.of(), "hash-xyz");

        verify(webhookTaskDAO).remove("hash-xyz", "task-789");
    }

    @Test
    public void isAsync_returnsTrue() {
        assertTrue(task.isAsync());
    }
}
