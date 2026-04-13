/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.RegistrationUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraaljsTests {
    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    List<String> workflowNames = new ArrayList<>();
    List<String> taskNames = new ArrayList<>();

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
    }

    @Test
    public void testInfiniteExecution()
            throws ExecutionException, InterruptedException, TimeoutException {
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String taskName1 = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String taskName2 = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        // Register workflow
        RegistrationUtil.registerWorkflowDef(workflowName, taskName1, taskName2, metadataClient);
        System.out.println("testInfiniteExecution: " + workflowName);
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        workflowDef
                .getTasks()
                .get(0)
                .setInputParameters(
                        Map.of(
                                "evaluatorType",
                                "graaljs",
                                "expression",
                                "function e() { while(true){} }; e();"));
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
        workflowNames.add(workflowName);
        taskNames.add(taskName1);
        taskNames.add(taskName2);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);

        // Wait for workflow to get failed since inline task will failed
        // With the new decider change, the decider might background decision on sweeper
        await().atMost(60, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
                        });
    }

    @After
    public void cleanUp() {
        for (String workflowName : workflowNames) {
            try {
                metadataClient.unregisterWorkflowDef(workflowName, 1);
            } catch (Exception e) {
            }
        }
        for (String taskName : taskNames) {
            try {
                metadataClient.unregisterTaskDef(taskName);
            } catch (Exception e) {
            }
        }
    }
}
