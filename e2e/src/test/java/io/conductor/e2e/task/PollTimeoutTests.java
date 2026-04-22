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

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;

import static io.conductor.e2e.util.TestUtil.getResourceAsString;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class PollTimeoutTests {

    private static final String TASK_NAME = "let_it_poll_timeout";

    @Test
    @Disabled(
            "Postgres-backed queue does not drain tasks from terminated workflows within the required 60s cleanup window; postgres queue cleanup is non-deterministic and can take several minutes")
    @SneakyThrows
    public void testPollTimeout() {
        var workflowClient = ApiUtil.WORKFLOW_CLIENT;
        var taskClient = ApiUtil.TASK_CLIENT;

        // Clean up any leftover workflows from previous runs
        var searchResult =
                workflowClient.search(
                        0,
                        1000,
                        "",
                        "*",
                        "workflowType IN (timed_out_task) AND status IN (RUNNING)");
        searchResult
                .getResults()
                .forEach(
                        w -> {
                            try {
                                workflowClient.terminateWorkflow(w.getWorkflowId(), "e2e cleanup");
                            } catch (Exception ignored) {
                            }
                        });
        // In conductor-oss with postgres queue, task cleanup after termination may take up to 60s
        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> taskClient.getQueueSizeForTask(TASK_NAME) == 0);

        var taskInQueue = taskClient.getQueueSizeForTask(TASK_NAME);
        assertEquals(0, taskInQueue, "Task queue size should be zero but it was " + taskInQueue);

        var mapper = new ObjectMapperProvider().getObjectMapper();
        var wf =
                mapper.readValue(
                        getResourceAsString("metadata/timed-out-tasks-not-removed.json"),
                        WorkflowDef.class);

        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wf.getName());
        startWorkflowRequest.setWorkflowDef(wf);

        var workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        assertNotNull(workflowId);
        await().untilAsserted(() -> assertTrue(taskClient.getQueueSizeForTask(TASK_NAME) > 0));
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3_000);
            workflowClient.runDecider(workflowId);
        }

        taskInQueue = taskClient.getQueueSizeForTask(TASK_NAME);
        assertEquals(0, taskInQueue, "Task queue size should be zero but it was " + taskInQueue);
    }
}
