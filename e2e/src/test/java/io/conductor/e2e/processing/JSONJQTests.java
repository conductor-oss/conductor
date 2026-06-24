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
package io.conductor.e2e.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.Commons;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JSONJQTests {
    @Test
    public void testJQOutputIsReachableWhenSyncSystemTaskIsNext() {

        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        String workflowName = RandomStringUtils.randomAlphanumeric(10).toUpperCase();

        var request = new StartWorkflowRequest();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail(Commons.OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(60);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        List<WorkflowTask> tasks = new ArrayList<>();

        WorkflowTask jqTask = new WorkflowTask();
        jqTask.setName("jqTaskName");
        jqTask.setTaskReferenceName("generate_operators_ref");
        jqTask.setInputParameters(Map.of("queryExpression", "{\"as\": \"+\", \"md\": \"/\"}"));
        jqTask.setType("JSON_JQ_TRANSFORM");

        WorkflowTask setVariableTask = new WorkflowTask();
        setVariableTask.setName("setvartaskname");
        setVariableTask.setTaskReferenceName("setvartaskname_ref");
        setVariableTask.setInputParameters(
                Map.of("name", "${generate_operators_ref.output.result.md}"));
        setVariableTask.setType("SET_VARIABLE");

        tasks.add(jqTask);
        tasks.add(setVariableTask);
        workflowDef.setTasks(tasks);
        request.setName(workflowName);
        request.setVersion(1);
        request.setWorkflowDef(workflowDef);

        List<String> workflowIds = new ArrayList<>();
        for (var i = 0; i < 40; ++i) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                workflowIds.add(workflowAdminClient.startWorkflow(request));
            } catch (Exception e) {

            }
        }
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            try {
                                workflowIds.forEach(
                                        id -> {
                                            var workflow =
                                                    workflowAdminClient.getWorkflow(id, true);
                                            assertEquals(
                                                    Workflow.WorkflowStatus.COMPLETED,
                                                    workflow.getStatus());
                                            assertEquals(
                                                    "/",
                                                    workflow.getTasks()
                                                            .get(1)
                                                            .getInputData()
                                                            .get("name"));
                                        });
                            } catch (Exception e) {

                            }
                        });
    }
}
