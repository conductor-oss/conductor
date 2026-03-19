/*
 * Copyright 2022 Orkes, Inc.
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

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class QueryProcessorTaskTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    static String workflowName = "query-processor-workflow-1";

    static String taskName = "query-processor-task-1";

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;

        // Start 100 workflows and search.
        // Register workflow
        registerWorkflowDef(workflowName, taskName, metadataClient);
        terminateExistingRunningWorkflows(workflowName);
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        int count = 100;
        for (int i = 0; i < count; i++) {
            startWorkflowRequest.setInput(Map.of("sequence", i+1));
            workflowClient.startWorkflow(startWorkflowRequest);
        }
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Check workflow search with name and default parameters")
    public void testSearchByWorkflowName() {

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setInput(Map.of("sequence", 101));
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        // Wait for 1 second to let sweeper run
        await().atMost(4, TimeUnit.SECONDS).pollInterval(1,TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            assertEquals(1, workflow.getTasks().size());
            assertNotNull( workflow.getTasks().get(0).getOutputData());
            assertNotNull( workflow.getTasks().get(0).getOutputData().get("result"));
            Map<String, Object> result = (Map<String, Object>) workflow.getTasks().get(0).getOutputData().get("result");
            assertNotNull(result);
            assertTrue(result.containsKey("count"));
            assertTrue(result.containsKey("totalHits"));
            assertEquals(result.get("count"), 10);
        });
    }

    @Test
    @DisplayName("Check workflow search with name and sort by start time ascending")
    public void testSearchByWorkflowNameSortByStartTime() {

        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        Map<String, Object> input  = workflowDef.getTasks().get(0).getInputParameters();
        input.put("sort", "startTime:ASC");
        metadataClient.registerWorkflowDef(workflowDef);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setInput(Map.of("sequence", 102));
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        // Wait for 1 second to let sweeper run
        await().atMost(5, TimeUnit.SECONDS).pollInterval(1,TimeUnit.SECONDS).untilAsserted(() -> {
            validateOrder(workflowId, true);
        });
    }

    void validateOrder(String workflowId, boolean isAscending) {
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(1, workflow.getTasks().size());
        assertNotNull( workflow.getTasks().get(0).getOutputData());
        assertNotNull( workflow.getTasks().get(0).getOutputData().get("result"));
        Map<String, Object> result = (Map<String, Object>) workflow.getTasks().get(0).getOutputData().get("result");
        assertNotNull(result);
        assertTrue(result.containsKey("count"));
        assertTrue(result.containsKey("totalHits"));
        assertEquals(result.get("count"), 10);
        List<Map<String, Object>> workflows = new ArrayList<>();
        workflows = (List<Map<String, Object>>) result.get("workflows");
        assertNotNull(workflows);
        List<Integer> sequences = new ArrayList<>();
        for (int i=0; i<workflows.size(); i++) {
            assertTrue(workflows.get(i).containsKey("input"));
            String taskInput = (String) workflows.get(i).get("input");
            sequences.add(Integer.valueOf(taskInput.replace("{sequence=", "").replace("}", "")));
        }
        for(int i=1; i<sequences.size(); i++) {
            if (isAscending) {
                assertTrue(sequences.get(i - 1) < sequences.get(i));
            } else {
                assertTrue(sequences.get(i - 1) > sequences.get(i));
            }
        }
    }

    @Test
    @DisplayName("Check workflow search with name and sort by start time descending")
    public void testRateLimitByWorkflowNameSortByStartTime2() {

        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        Map<String, Object> input  = workflowDef.getTasks().get(0).getInputParameters();
        input.put("sort", "startTime:DESC");
        metadataClient.registerWorkflowDef(workflowDef);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setInput(Map.of("sequence", 102));
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        // Wait for 1 second to let sweeper run
        await().atMost(5, TimeUnit.SECONDS).pollInterval(1,TimeUnit.SECONDS).untilAsserted(() -> {
            validateOrder(workflowId, false);
        });
    }

    @Test
    @Disabled
    @DisplayName("Check task search with name and sort by start time descending")
    public void testSearchByWorkflowNameSortByStartTime2() {

        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        Map<String, Object> input  = workflowDef.getTasks().get(0).getInputParameters();
        input.put("sort", "startTime:DESC");
        input.put("queryType", "TASK_API");
        metadataClient.registerWorkflowDef(workflowDef);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setInput(Map.of("sequence", 102));
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        // Wait for 1 second to let sweeper run
        await().atMost(50000, TimeUnit.SECONDS).pollInterval(1,TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
            assertEquals(1, workflow.getTasks().size());
            assertNotNull( workflow.getTasks().get(0).getOutputData());
            assertNotNull( workflow.getTasks().get(0).getOutputData().get("result"));
            Map<String, Object> result = (Map<String, Object>) workflow.getTasks().get(0).getOutputData().get("result");
            assertNotNull(result);
            assertTrue(result.containsKey("count"));
            assertTrue(result.containsKey("totalHits"));
            assertEquals(result.get("count"), 10);
            List<Map<String, Object>> tasks = new ArrayList<>();
            tasks = (List<Map<String, Object>>) result.get("tasks");
            assertNotNull(tasks);
            List<Integer> sequences = new ArrayList<>();
            for (int i=0; i<tasks.size(); i++) {
                assertTrue(tasks.get(i).containsKey("input"));
                String taskInput = (String) tasks.get(i).get("input");
                sequences.add(Integer.valueOf(taskInput.replace("{sequence=", "").replace("}", "")));
            }
            for(int i=1; i<sequences.size(); i++) {
                assertTrue(sequences.get(i - 1) > sequences.get(i));
            }
        });
    }

    @Test
    @DisplayName("Check workflow search with name and 20 count")
    public void testSearchByWorkflowNameWithCount20() {

        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        Map<String, Object> input  = workflowDef.getTasks().get(0).getInputParameters();
        input.put("count", 20);
        metadataClient.registerWorkflowDef(workflowDef);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setInput(Map.of("sequence", 102));
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        await().atMost(5, TimeUnit.SECONDS).pollInterval(1,TimeUnit.SECONDS).untilAsserted(() -> {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            assertEquals(1, workflow.getTasks().size());
            assertNotNull( workflow.getTasks().get(0).getOutputData());
            assertNotNull( workflow.getTasks().get(0).getOutputData().get("result"));
            Map<String, Object> result = (Map<String, Object>) workflow.getTasks().get(0).getOutputData().get("result");
            assertNotNull(result);
            assertTrue(result.containsKey("count"));
            assertTrue(result.containsKey("totalHits"));
            assertEquals(result.get("count"), 20);
            List<Map<String, Object>> workflows = new ArrayList<>();
            workflows = (List<Map<String, Object>>) result.get("workflows");
            assertNotNull(workflows);
            assertEquals(20, workflows.size());
        });
    }

    private static void registerWorkflowDef(String workflowName, String taskName, MetadataClient metadataClient) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@orkes.io");
        taskDef.setRetryCount(0);

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName);
        simpleTask.setName(taskName);
        simpleTask.setTaskDefinition(taskDef);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        simpleTask.setInputParameters(Map.of("sequence", "${workflow.input.sequence}", "queryType", "CONDUCTOR_API", "workflowNames", List.of(workflowName)));
        simpleTask.setType("QUERY_PROCESSOR");


        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to check query processor task.");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(simpleTask));
        metadataClient.registerWorkflowDef(workflowDef);
        metadataClient.registerTaskDefs(Arrays.asList(taskDef));
    }

    private static void terminateExistingRunningWorkflows(String workflowName) {
        //clean up first
            try {
            SearchResult<WorkflowSummary> found = workflowClient.search(0,10000, "", "*", "workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
            found.getResults().forEach(workflowSummary -> {

                    workflowClient.terminateWorkflow(workflowSummary.getWorkflowId(), "terminate - rate limiter test - " + workflowName);
                    System.out.println("Going to terminate " + workflowSummary.getWorkflowId());
            });
        } catch(Exception e){
                if (!(e instanceof ConductorClientException)) {
                    throw e;
                }
            }
    }
}
