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
package io.conductor.e2e.task;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HTTPTaskTests {

    @Test
    @Disabled(
            "Requires httpbin-server internal service (http://httpbin-server:8081) not configured in conductor-oss e2e docker setup")
    public void HTTPAsyncCompleteTest() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        TaskClient taskClient = ApiUtil.TASK_CLIENT;
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setVersion(1);
        startWorkflowRequest.setName("http_async_complete");
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().pollInterval(5, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(1, workflow.getTasks().size());
                            assertEquals(
                                    Task.Status.SCHEDULED,
                                    workflow.getTasks().getFirst().getStatus());
                            assertNotNull(
                                    workflow.getTasks()
                                            .getFirst()
                                            .getOutputData()
                                            .getOrDefault("response", null));
                        });
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        TaskResult taskResult = new TaskResult(workflow.getTasks().getFirst());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(taskResult);
        await().pollInterval(5, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .untilAsserted(
                        () -> {
                            Workflow workflowCompleted =
                                    workflowClient.getWorkflow(workflowId, false);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    workflowCompleted.getStatus());
                        });
        workflow = workflowClient.getWorkflow(workflowId, true);
        Map<String, Object> response =
                (Map<String, Object>)
                        workflow.getTasks()
                                .getFirst()
                                .getOutputData()
                                .getOrDefault("response", Map.of());
        assertNotNull(response);
        assertEquals(200, response.get("statusCode"));
    }
}
