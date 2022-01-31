/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.dal.ModelMapper;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.dao.QueueDAO;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class ExecutionServiceTest {

    @Mock private WorkflowExecutor workflowExecutor;
    @Mock private ModelMapper modelMapper;
    @Mock private ExecutionDAOFacade executionDAOFacade;
    @Mock private QueueDAO queueDAO;
    @Mock private ConductorProperties conductorProperties;
    @Mock private ExternalPayloadStorage externalPayloadStorage;
    @Mock private SystemTaskRegistry systemTaskRegistry;

    private ExecutionService executionService;

    private Workflow workflow1;
    private Workflow workflow2;
    private Task taskWorkflow1;
    private Task taskWorkflow2;
    private final List<String> sort = Collections.singletonList("Sort");

    @Before
    public void setup() {
        when(conductorProperties.getTaskExecutionPostponeDuration())
                .thenReturn(Duration.ofSeconds(60));
        executionService =
                new ExecutionService(
                        workflowExecutor,
                        modelMapper,
                        executionDAOFacade,
                        queueDAO,
                        conductorProperties,
                        externalPayloadStorage,
                        systemTaskRegistry);
        WorkflowDef workflowDef = new WorkflowDef();
        workflow1 = new Workflow();
        workflow1.setWorkflowId("wf1");
        workflow1.setWorkflowDefinition(workflowDef);
        workflow2 = new Workflow();
        workflow2.setWorkflowId("wf2");
        workflow2.setWorkflowDefinition(workflowDef);
        taskWorkflow1 = new Task();
        taskWorkflow1.setTaskId("task1");
        taskWorkflow1.setWorkflowInstanceId("wf1");
        taskWorkflow2 = new Task();
        taskWorkflow2.setTaskId("task2");
        taskWorkflow2.setWorkflowInstanceId("wf2");
    }

    @Test
    public void workflowSearchTest() {
        when(executionDAOFacade.searchWorkflows("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        workflow1.getWorkflowId(), workflow2.getWorkflowId())));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<WorkflowSummary> searchResult =
                executionService.search("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(2, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
        assertEquals(workflow2.getWorkflowId(), searchResult.getResults().get(1).getWorkflowId());
    }

    @Test
    public void workflowSearchExceptionTest() {
        when(executionDAOFacade.searchWorkflows("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        workflow1.getWorkflowId(), workflow2.getWorkflowId())));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenThrow(new RuntimeException());
        SearchResult<WorkflowSummary> searchResult =
                executionService.search("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(1, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
    }

    @Test
    public void workflowSearchV2Test() {
        when(executionDAOFacade.searchWorkflows("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        workflow1.getWorkflowId(), workflow2.getWorkflowId())));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<Workflow> searchResult = executionService.searchV2("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(Arrays.asList(workflow1, workflow2), searchResult.getResults());
    }

    @Test
    public void workflowSearchV2ExceptionTest() {
        when(executionDAOFacade.searchWorkflows("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        workflow1.getWorkflowId(), workflow2.getWorkflowId())));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenThrow(new RuntimeException());
        SearchResult<Workflow> searchResult = executionService.searchV2("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow1), searchResult.getResults());
    }

    @Test
    public void workflowSearchByTasksTest() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId())).thenReturn(taskWorkflow2);
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<WorkflowSummary> searchResult =
                executionService.searchWorkflowByTasks("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(2, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
        assertEquals(workflow2.getWorkflowId(), searchResult.getResults().get(1).getWorkflowId());
    }

    @Test
    public void workflowSearchByTasksExceptionTest() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId()))
                .thenThrow(new RuntimeException());
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        SearchResult<WorkflowSummary> searchResult =
                executionService.searchWorkflowByTasks("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(1, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
    }

    @Test
    public void workflowSearchByTasksV2Test() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId())).thenReturn(taskWorkflow2);
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<Workflow> searchResult =
                executionService.searchWorkflowByTasksV2("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(Arrays.asList(workflow1, workflow2), searchResult.getResults());
    }

    @Test
    public void workflowSearchByTasksV2ExceptionTest() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId()))
                .thenThrow(new RuntimeException());
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        SearchResult<Workflow> searchResult =
                executionService.searchWorkflowByTasksV2("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow1), searchResult.getResults());
    }

    @Test
    public void TaskSearchTest() {
        List<String> taskList = Arrays.asList(taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId());
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(new SearchResult<>(2, taskList));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId())).thenReturn(taskWorkflow2);
        SearchResult<TaskSummary> searchResult =
                executionService.getSearchTasks("query", "*", 0, 2, "Sort");
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(2, searchResult.getResults().size());
        assertEquals(taskWorkflow1.getTaskId(), searchResult.getResults().get(0).getTaskId());
        assertEquals(taskWorkflow2.getTaskId(), searchResult.getResults().get(1).getTaskId());
    }

    @Test
    public void TaskSearchExceptionTest() {
        List<String> taskList = Arrays.asList(taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId());
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(new SearchResult<>(2, taskList));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId()))
                .thenThrow(new RuntimeException());
        SearchResult<TaskSummary> searchResult =
                executionService.getSearchTasks("query", "*", 0, 2, "Sort");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(1, searchResult.getResults().size());
        assertEquals(taskWorkflow1.getTaskId(), searchResult.getResults().get(0).getTaskId());
    }

    @Test
    public void TaskSearchV2Test() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId())).thenReturn(taskWorkflow2);
        SearchResult<Task> searchResult =
                executionService.getSearchTasksV2("query", "*", 0, 2, "Sort");
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(Arrays.asList(taskWorkflow1, taskWorkflow2), searchResult.getResults());
    }

    @Test
    public void TaskSearchV2ExceptionTest() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId()))
                .thenThrow(new RuntimeException());
        SearchResult<Task> searchResult =
                executionService.getSearchTasksV2("query", "*", 0, 2, "Sort");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(taskWorkflow1), searchResult.getResults());
    }
}
