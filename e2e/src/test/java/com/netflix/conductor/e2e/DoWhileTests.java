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
package com.netflix.conductor.e2e;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.e2e.util.ConductorClientUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class DoWhileTests {

    private static final String[] WORKFLOW_FILES = {
        "/metadata/do_while_stackoverflower.json",
    };
    private static final List<String> workflowIdsToTerminate = new ArrayList<>();
    private static WorkflowClient workflowClient;
    private static TaskClient taskClient;
    private static MetadataClient metadataClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<WorkflowDef> WORKFLOW_DEF = new TypeReference<>() {};

    @SneakyThrows
    @BeforeAll
    public static void beforeAll() {
        workflowClient = ConductorClientUtil.getWorkflowClient();
        taskClient = ConductorClientUtil.getTaskClient();
        metadataClient = ConductorClientUtil.getMetadataClient();

        for (String file : WORKFLOW_FILES) {
            InputStream resource = DoWhileTests.class.getResourceAsStream(file);
            assert resource != null;
            WorkflowDef workflowDef =
                    objectMapper.readValue(new InputStreamReader(resource), WORKFLOW_DEF);
            try {
                metadataClient.registerWorkflowDef(workflowDef);
                log.info("Registered workflow definition: {}", workflowDef.getName());
            } catch (Exception e) {
                log.warn(
                        "Could not register workflow {}: {}",
                        workflowDef.getName(),
                        e.getMessage());
            }
        }
    }

    @AfterAll
    public static void cleanup() {
        for (String id : workflowIdsToTerminate) {
            try {
                workflowClient.terminateWorkflow(
                        id, "Terminated by cleanup in " + DoWhileTests.class.getSimpleName());
            } catch (Exception e) {
                if (!e.getMessage().contains("cannot be terminated")) {
                    log.warn("Error while cleaning up workflow {}: {}", id, e.getMessage());
                }
            }
        }
    }

    @Test
    public void testStackoverflower() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("do_while_stackoverflower");
        request.setInput(Map.of("n", 999));
        String workflowId = workflowClient.startWorkflow(request);
        log.info("Started workflow {}", workflowId);
        workflowIdsToTerminate.add(workflowId);

        await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(workflow);
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    workflow.getStatus(),
                                    "Workflow should be completed");
                        });

        Workflow finalWorkflow = workflowClient.getWorkflow(workflowId, true);
        Task loopTask = finalWorkflow.getTaskByRefName("loop_ref");
        assertNotNull(loopTask, "loop task should exist");

        Object iteration = loopTask.getOutputData().get("iteration");
        assertNotNull(iteration, "iteration field should exist in loop task output");
        assertEquals(999, iteration, "loop should have iterated 999 times");

        log.info("Test completed successfully. Loop iterated {} times", iteration);
    }
}
