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
package io.conductor.e2e.workflow;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;

import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EndTimeIssueTests {

    @DisplayName("${task_ref.endTime} should be replaced correctly")
    @Test
    @SneakyThrows
    void endTimeIsReplacedCorrectly() {
        final var start = System.currentTimeMillis();
        var metadataClient = ApiUtil.METADATA_CLIENT;
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;
        var taskClient = ApiUtil.TASK_CLIENT;

        var mapper = new ObjectMapperProvider().getObjectMapper();
        var workflowDef =
                mapper.readValue(
                        getResourceAsString("metadata/end_time_workflow.json"), WorkflowDef.class);
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowDef.getName());
        startWorkflowRequest.setVersion(workflowDef.getVersion());
        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var task =
                                    taskClient.pollTask("end_time_simple", "end-time-worker", null);
                            assertNotNull(task);
                            assertEquals(workflowId, task.getWorkflowInstanceId());
                            assertEquals("simple_ref", task.getReferenceTaskName());
                            assertEquals(Task.Status.IN_PROGRESS, task.getStatus());

                            var result = new TaskResult(task);
                            result.setStatus(TaskResult.Status.COMPLETED);
                            taskClient.updateTask(result);
                        });

        for (int i = 2; i <= 3; i++) {
            var n = i;
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                var taskType = "end_time_simple_" + n;
                                var task = taskClient.pollTask(taskType, "end-time-worker", null);
                                assertNotNull(task);
                                assertEquals(workflowId, task.getWorkflowInstanceId());
                                assertEquals(Task.Status.IN_PROGRESS, task.getStatus());

                                assertNotNull(task.getInputData().get("endTime"));
                                var endTime =
                                        Long.parseLong(
                                                task.getInputData().get("endTime").toString());
                                if (endTime < start) {
                                    // we want to throw an Exception to break the untilAsserted
                                    // block
                                    throw new RuntimeException(
                                            String.format(
                                                    "Invalid value for ${simple_ref.endTime}:%d in task %s",
                                                    endTime, taskType));
                                }

                                var result = new TaskResult(task);
                                result.setStatus(TaskResult.Status.COMPLETED);
                                taskClient.updateTask(result);
                            });
        }
    }
}
