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
import java.util.List;
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

    @Test
    public void testSearchByWorkflowIdWithDoubleQuotes() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        registerWorkflowDef(workflowName, taskName, metadataClient);

        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);
        String workflowId = workflowClient.startWorkflow(startReq);

        // Search by workflowId with double quotes
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            SearchResult<WorkflowSummary> result =
                                    workflowClient.search("workflowId=\"" + workflowId + "\"");
                            assertEquals(
                                    1,
                                    result.getResults().size(),
                                    "Search by workflowId with double quotes should return 1 result");
                            assertEquals(workflowId, result.getResults().get(0).getWorkflowId());
                        });

        workflowClient.terminateWorkflow(workflowId, "test cleanup");
        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    @Test
    public void testSearchByWorkflowIdWithSingleQuotes() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        registerWorkflowDef(workflowName, taskName, metadataClient);

        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);
        String workflowId = workflowClient.startWorkflow(startReq);

        // Search by workflowId with single quotes — this was broken before the fix
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            SearchResult<WorkflowSummary> result =
                                    workflowClient.search("workflowId='" + workflowId + "'");
                            assertEquals(
                                    1,
                                    result.getResults().size(),
                                    "Search by workflowId with single quotes should return 1 result");
                            assertEquals(workflowId, result.getResults().get(0).getWorkflowId());
                        });

        workflowClient.terminateWorkflow(workflowId, "test cleanup");
        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    @Test
    public void testSearchByWorkflowTypeWithSingleQuotes() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        registerWorkflowDef(workflowName, taskName, metadataClient);

        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);
        workflowClient.startWorkflow(startReq);
        workflowClient.startWorkflow(startReq);

        // Search by workflowType with single quotes
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            SearchResult<WorkflowSummary> result =
                                    workflowClient.search("workflowType='" + workflowName + "'");
                            assertEquals(
                                    2,
                                    result.getResults().size(),
                                    "Search by workflowType with single quotes should return 2 results");
                        });

        // Cleanup
        SearchResult<WorkflowSummary> all =
                workflowClient.search("workflowType='" + workflowName + "'");
        all.getResults()
                .forEach(
                        wf -> workflowClient.terminateWorkflow(wf.getWorkflowId(), "test cleanup"));
        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    @Test
    public void testSearchWithMultipleConditionsAndSingleQuotes() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String correlationId = "corr-" + RandomStringUtils.randomAlphanumeric(10);

        registerWorkflowDef(workflowName, taskName, metadataClient);

        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);
        startReq.setCorrelationId(correlationId);
        String workflowId = workflowClient.startWorkflow(startReq);

        // Start another workflow without the correlationId to ensure filtering works
        StartWorkflowRequest otherReq = new StartWorkflowRequest();
        otherReq.setName(workflowName);
        otherReq.setVersion(1);
        String otherWorkflowId = workflowClient.startWorkflow(otherReq);

        // Search with multiple single-quoted conditions — mimics getWorkflowsByCorrelationId
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            SearchResult<WorkflowSummary> result =
                                    workflowClient.search(
                                            "correlationId='"
                                                    + correlationId
                                                    + "' AND workflowType='"
                                                    + workflowName
                                                    + "'");
                            assertEquals(
                                    1,
                                    result.getResults().size(),
                                    "Multi-condition search with single quotes should return 1 result");
                            assertEquals(workflowId, result.getResults().get(0).getWorkflowId());
                        });

        workflowClient.terminateWorkflow(workflowId, "test cleanup");
        workflowClient.terminateWorkflow(otherWorkflowId, "test cleanup");
        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    @Test
    public void testSearchWithPaginationAndSort() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
        String taskName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();
        String workflowName = RandomStringUtils.randomAlphanumeric(5).toUpperCase();

        registerWorkflowDef(workflowName, taskName, metadataClient);

        // Start 3 workflows
        for (int i = 0; i < 3; i++) {
            StartWorkflowRequest startReq = new StartWorkflowRequest();
            startReq.setName(workflowName);
            startReq.setVersion(1);
            workflowClient.startWorkflow(startReq);
        }

        // Wait for all 3 to be indexed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            SearchResult<WorkflowSummary> result =
                                    workflowClient.search("workflowType='" + workflowName + "'");
                            assertEquals(3, result.getResults().size());
                        });

        // Search with pagination: page size 2, starting at 0
        SearchResult<WorkflowSummary> page1 =
                workflowClient.search(
                        0, 2, "workflowId:ASC", "*", "workflowType='" + workflowName + "'");
        assertEquals(3, page1.getTotalHits(), "Total hits should be 3");
        assertEquals(2, page1.getResults().size(), "Page 1 should have 2 results");

        // Page 2
        SearchResult<WorkflowSummary> page2 =
                workflowClient.search(
                        2, 2, "workflowId:ASC", "*", "workflowType='" + workflowName + "'");
        assertEquals(3, page2.getTotalHits(), "Total hits should still be 3");
        assertEquals(1, page2.getResults().size(), "Page 2 should have 1 result");

        // Verify no overlap between pages
        List<String> page1Ids =
                page1.getResults().stream().map(WorkflowSummary::getWorkflowId).toList();
        List<String> page2Ids =
                page2.getResults().stream().map(WorkflowSummary::getWorkflowId).toList();
        page2Ids.forEach(
                id ->
                        assertFalse(
                                page1Ids.contains(id),
                                "Pages should not have overlapping results"));

        // Cleanup
        SearchResult<WorkflowSummary> all =
                workflowClient.search("workflowType='" + workflowName + "'");
        all.getResults()
                .forEach(
                        wf -> workflowClient.terminateWorkflow(wf.getWorkflowId(), "test cleanup"));
        metadataClient.unregisterWorkflowDef(workflowName, 1);
    }

    private void registerWorkflowDef(
            String workflowName, String taskName, MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName(taskName);
        workflowTask.setName(taskName);
        workflowTask.setTaskDefinition(taskDef);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTasks(Arrays.asList(workflowTask));
        metadataClient1.updateWorkflowDefs(java.util.List.of(workflowDef));
        metadataClient1.registerTaskDefs(Arrays.asList(taskDef));
    }
}
