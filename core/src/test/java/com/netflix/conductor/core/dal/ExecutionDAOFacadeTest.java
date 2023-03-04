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
package com.netflix.conductor.core.dal;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.TestDeciderService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.*;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class ExecutionDAOFacadeTest {

    private ExecutionDAO executionDAO;
    private IndexDAO indexDAO;
    private ExecutionDAOFacade executionDAOFacade;
    private ExternalPayloadStorageUtils externalPayloadStorageUtils;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        executionDAO = mock(ExecutionDAO.class);
        QueueDAO queueDAO = mock(QueueDAO.class);
        indexDAO = mock(IndexDAO.class);
        externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        RateLimitingDAO rateLimitingDao = mock(RateLimitingDAO.class);
        ConcurrentExecutionLimitDAO concurrentExecutionLimitDAO =
                mock(ConcurrentExecutionLimitDAO.class);
        PollDataDAO pollDataDAO = mock(PollDataDAO.class);
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.isEventExecutionIndexingEnabled()).thenReturn(true);
        when(properties.isAsyncIndexingEnabled()).thenReturn(true);
        executionDAOFacade =
                new ExecutionDAOFacade(
                        executionDAO,
                        queueDAO,
                        indexDAO,
                        rateLimitingDao,
                        concurrentExecutionLimitDAO,
                        pollDataDAO,
                        objectMapper,
                        properties,
                        externalPayloadStorageUtils);
    }

    @Test
    public void testGetWorkflow() throws Exception {
        when(executionDAO.getWorkflow(any(), anyBoolean())).thenReturn(new WorkflowModel());
        Workflow workflow = executionDAOFacade.getWorkflow("workflowId", true);
        assertNotNull(workflow);
        verify(indexDAO, never()).get(any(), any());
    }

    @Test
    public void testGetWorkflowModel() throws Exception {
        when(executionDAO.getWorkflow(any(), anyBoolean())).thenReturn(new WorkflowModel());
        WorkflowModel workflowModel = executionDAOFacade.getWorkflowModel("workflowId", true);
        assertNotNull(workflowModel);
        verify(indexDAO, never()).get(any(), any());

        when(executionDAO.getWorkflow(any(), anyBoolean())).thenReturn(null);
        InputStream stream = ExecutionDAOFacadeTest.class.getResourceAsStream("/test.json");
        byte[] bytes = IOUtils.toByteArray(stream);
        String jsonString = new String(bytes);
        when(indexDAO.get(any(), any())).thenReturn(jsonString);
        workflowModel = executionDAOFacade.getWorkflowModel("wokflowId", true);
        assertNotNull(workflowModel);
        verify(indexDAO, times(1)).get(any(), any());
    }

    @Test
    public void testGetWorkflowsByCorrelationId() {
        when(executionDAO.canSearchAcrossWorkflows()).thenReturn(true);
        when(executionDAO.getWorkflowsByCorrelationId(any(), any(), anyBoolean()))
                .thenReturn(Collections.singletonList(new WorkflowModel()));
        List<Workflow> workflows =
                executionDAOFacade.getWorkflowsByCorrelationId(
                        "workflowName", "correlationId", true);

        assertNotNull(workflows);
        assertEquals(1, workflows.size());
        verify(indexDAO, never())
                .searchWorkflows(anyString(), anyString(), anyInt(), anyInt(), any());

        when(executionDAO.canSearchAcrossWorkflows()).thenReturn(false);
        List<String> workflowIds = new ArrayList<>();
        workflowIds.add("workflowId");
        SearchResult<String> searchResult = new SearchResult<>();
        searchResult.setResults(workflowIds);
        when(indexDAO.searchWorkflows(anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(searchResult);
        when(executionDAO.getWorkflow("workflowId", true)).thenReturn(new WorkflowModel());
        workflows =
                executionDAOFacade.getWorkflowsByCorrelationId(
                        "workflowName", "correlationId", true);
        assertNotNull(workflows);
        assertEquals(1, workflows.size());
    }

    @Test
    public void testRemoveWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflowId");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);

        TaskModel task = new TaskModel();
        task.setTaskId("taskId");
        workflow.setTasks(Collections.singletonList(task));

        when(executionDAO.getWorkflow(anyString(), anyBoolean())).thenReturn(workflow);
        executionDAOFacade.removeWorkflow("workflowId", false);
        verify(executionDAO, times(1)).removeWorkflow(anyString());
        verify(executionDAO, never()).removeTask(anyString());
        verify(indexDAO, never()).updateWorkflow(anyString(), any(), any());
        verify(indexDAO, never()).updateTask(anyString(), anyString(), any(), any());
        verify(indexDAO, times(1)).asyncRemoveWorkflow(anyString());
        verify(indexDAO, times(1)).asyncRemoveTask(anyString(), anyString());
    }

    @Test
    public void testArchiveWorkflow() throws Exception {
        InputStream stream = TestDeciderService.class.getResourceAsStream("/completed.json");
        WorkflowModel workflow = objectMapper.readValue(stream, WorkflowModel.class);

        when(executionDAO.getWorkflow(anyString(), anyBoolean())).thenReturn(workflow);
        executionDAOFacade.removeWorkflow("workflowId", true);
        verify(executionDAO, times(1)).removeWorkflow(anyString());
        verify(executionDAO, never()).removeTask(anyString());
        verify(indexDAO, times(1)).updateWorkflow(anyString(), any(), any());
        verify(indexDAO, times(15)).updateTask(anyString(), anyString(), any(), any());
        verify(indexDAO, never()).removeWorkflow(anyString());
        verify(indexDAO, never()).removeTask(anyString(), anyString());
    }

    @Test
    public void testAddEventExecution() {
        when(executionDAO.addEventExecution(any())).thenReturn(false);
        boolean added = executionDAOFacade.addEventExecution(new EventExecution());
        assertFalse(added);
        verify(indexDAO, never()).addEventExecution(any());

        when(executionDAO.addEventExecution(any())).thenReturn(true);
        added = executionDAOFacade.addEventExecution(new EventExecution());
        assertTrue(added);
        verify(indexDAO, times(1)).asyncAddEventExecution(any());
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testUpdateTaskThrowsTerminateWorkflowException() {
        TaskModel task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName("task1");

        doThrow(new TerminateWorkflowException("failed"))
                .when(externalPayloadStorageUtils)
                .verifyAndUpload(task, ExternalPayloadStorage.PayloadType.TASK_OUTPUT);

        executionDAOFacade.updateTask(task);
    }
}
