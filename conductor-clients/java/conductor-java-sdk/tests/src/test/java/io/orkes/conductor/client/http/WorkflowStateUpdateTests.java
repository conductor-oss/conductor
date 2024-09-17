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
package io.orkes.conductor.client.http;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;

import io.orkes.conductor.client.model.WorkflowRun;
import io.orkes.conductor.client.model.WorkflowStateUpdate;
import io.orkes.conductor.client.util.ClientTestUtil;

import lombok.SneakyThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class WorkflowStateUpdateTests {

    private static OrkesWorkflowClient workflowClient;

    @BeforeAll
    public static void init() {
        workflowClient = ClientTestUtil.getOrkesClients().getWorkflowClient();
    }

    @SneakyThrows
    public String startWorkflow() {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("sync_task_variable_updates");
        startWorkflowRequest.setVersion(1);
        var run = workflowClient.executeWorkflow(startWorkflowRequest, "wait_task_ref");
        return run.get(10, TimeUnit.SECONDS)
            .getWorkflowId();
    }

    @Test
    public void test() {
        String workflowId = startWorkflow();
        System.out.println(workflowId);

        TaskResult taskResult = new TaskResult();
        taskResult.setOutputData(Map.of("a", "b"));

        WorkflowStateUpdate request = new WorkflowStateUpdate();
        request.setTaskReferenceName("wait_task_ref");
        request.setTaskResult(taskResult);

        request.setVariables(Map.of("case", "case1"));

        WorkflowRun workflowRun = workflowClient.updateWorkflow(workflowId, List.of("wait_task_ref_1", "wait_task_ref_2"), 10, request);

        System.out.println(workflowRun);
        System.out.println(workflowRun.getStatus());
        System.out.println(workflowRun.getTasks()
            .stream()
            .map(task -> task.getReferenceTaskName() + ":" + task.getStatus())
            .collect(Collectors.toList()));

        request = new WorkflowStateUpdate();
        request.setTaskReferenceName("wait_task_ref_2");
        request.setTaskResult(taskResult);
        workflowRun = workflowClient.updateWorkflow(workflowId, List.of(), 10, request);

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflowRun.getStatus());
        Set<Task.Status> allTaskStatus = workflowRun.getTasks()
            .stream()
            .map(t -> t.getStatus())
            .collect(Collectors.toSet());
        assertEquals(1, allTaskStatus.size());
        assertEquals(Task.Status.COMPLETED, allTaskStatus.iterator().next());

        System.out.println(workflowRun.getStatus());
        System.out.println(workflowRun.getTasks()
            .stream()
            .map(task -> task.getReferenceTaskName() + ":" + task.getStatus())
            .collect(Collectors.toList()));

    }

    @Test
    public void testIdempotency() {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("sync_task_variable_updates");
        startWorkflowRequest.setVersion(1);
        String idempotencyKey = UUID.randomUUID().toString();
        startWorkflowRequest.setIdempotencyKey(idempotencyKey);
        startWorkflowRequest.setIdempotencyStrategy(IdempotencyStrategy.FAIL);
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        startWorkflowRequest.setIdempotencyStrategy(IdempotencyStrategy.RETURN_EXISTING);
        String workflowId2 = workflowClient.startWorkflow(startWorkflowRequest);
        assertEquals(workflowId, workflowId2);

        startWorkflowRequest.setIdempotencyStrategy(IdempotencyStrategy.FAIL);
        boolean conflict = false;
        try {
            workflowClient.startWorkflow(startWorkflowRequest);
        } catch (ConductorClientException ce) {
            conflict = ce.getStatusCode() == 409;
        }
        assertTrue(conflict);
    }
}
