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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class ContextPropagationTest {

    @Test
    @SneakyThrows
    @DisplayName(
            "When tasks are scheduled in an org they should be available when polling in the same org")
    public void testScheduledTasksArePolled() {
        var metadataClient = ApiUtil.METADATA_CLIENT;
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;
        var taskClient = ApiUtil.TASK_CLIENT;

        var mapper = new ObjectMapperProvider().getObjectMapper();

        var workflowDef =
                mapper.readValue(
                        getResourceAsString("metadata/context_concurrency_issue.json"),
                        WorkflowDef.class);
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));

        var swr = new StartWorkflowRequest();
        swr.setName(workflowDef.getName());
        swr.setVersion(workflowDef.getVersion());
        var wfId = workflowClient.startWorkflow(swr);
        assertNotNull(wfId);

        var taskType = "concurrency_issue";
        var scheduledTasks = new ArrayList<String>();
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var workflow = workflowClient.getWorkflow(wfId, true);
                            var tasks =
                                    workflow.getTasks().stream()
                                            .filter(
                                                    t ->
                                                            t.getTaskDefName().equals(taskType)
                                                                    && t.getStatus()
                                                                            == Task.Status
                                                                                    .SCHEDULED)
                                            .map(Task::getTaskId)
                                            .toList();
                            assertEquals(8, tasks.size());
                            scheduledTasks.addAll(tasks);
                        });

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            var polled =
                                    taskClient.batchPollTasksByTaskType(taskType, "test", 10, 0);
                            assertNotNull(polled);
                            assertEquals(
                                    8,
                                    polled.size(),
                                    "Expected 8 to be polled but got " + polled.size());
                            assertTrue(
                                    polled.stream()
                                            .allMatch(t -> scheduledTasks.contains(t.getTaskId())));
                        });

        workflowClient.terminateWorkflow(wfId, "cleanup");
    }
}
