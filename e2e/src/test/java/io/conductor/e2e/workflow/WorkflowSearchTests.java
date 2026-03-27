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
package io.conductor.e2e.workflow;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.WorkflowSummary;

import io.conductor.e2e.util.ApiUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class WorkflowSearchTests {

    // NOTE: testWorkflowSearchPermissions was removed because it uses enterprise-only features:
    // TagObject, AuthorizationClient, SubjectRef, TargetRef, AuthorizationRequest,
    // UpsertGroupRequest
    // which are not available in conductor-oss.

    @Test
    public void testBasicWorkflowSearch() {

        WorkflowClient workflowAdminClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataAdminClient = ApiUtil.METADATA_CLIENT;
        String taskName1 = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName1 = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        // Run workflow search it should return 0 result
        AtomicReference<SearchResult<WorkflowSummary>> workflowSummarySearchResult =
                new AtomicReference<>(
                        workflowAdminClient.search("workflowType IN (" + workflowName1 + ")"));
        assertEquals(workflowSummarySearchResult.get().getResults().size(), 0);

        // Register workflow
        registerWorkflowDef(workflowName1, taskName1, metadataAdminClient);

        // Trigger two workflows
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName1);
        startWorkflowRequest.setVersion(1);

        workflowAdminClient.startWorkflow(startWorkflowRequest);
        workflowAdminClient.startWorkflow(startWorkflowRequest);
        await().pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            workflowSummarySearchResult.set(
                                    workflowAdminClient.search(
                                            "workflowType IN (" + workflowName1 + ")"));
                            assertEquals(2, workflowSummarySearchResult.get().getResults().size());
                        });

        // Register another workflow
        String taskName2 = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName2 = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        registerWorkflowDef(workflowName2, taskName2, metadataAdminClient);

        startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName2);
        startWorkflowRequest.setVersion(1);

        // Trigger workflow
        workflowAdminClient.startWorkflow(startWorkflowRequest);
        workflowAdminClient.startWorkflow(startWorkflowRequest);
        // In search result when only this workflow searched 2 results should come
        await().pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            workflowSummarySearchResult.set(
                                    workflowAdminClient.search(
                                            "workflowType IN (" + workflowName2 + ")"));
                            assertEquals(2, workflowSummarySearchResult.get().getResults().size());
                        });

        // In search result when both workflow searched then 4 results should come
        await().pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            workflowSummarySearchResult.set(
                                    workflowAdminClient.search(
                                            "workflowType IN ("
                                                    + workflowName1
                                                    + ","
                                                    + workflowName2
                                                    + ")"));
                            assertEquals(4, workflowSummarySearchResult.get().getResults().size());
                        });

        // Terminate all the workflows
        workflowSummarySearchResult
                .get()
                .getResults()
                .forEach(
                        workflowSummary ->
                                workflowAdminClient.terminateWorkflow(
                                        workflowSummary.getWorkflowId(), "test"));

        metadataAdminClient.unregisterWorkflowDef(workflowName1, 1);
        metadataAdminClient.unregisterWorkflowDef(workflowName2, 1);
    }

    private void registerWorkflowDef(
            String workflowName, String taskName, MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName(taskName);
        workflowTask.setName(taskName);
        workflowTask.setTaskDefinition(taskDef);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTasks(Arrays.asList(workflowTask));
        metadataClient1.updateWorkflowDefs(java.util.List.of(workflowDef));
        metadataClient1.registerTaskDefs(Arrays.asList(taskDef));
    }
}
