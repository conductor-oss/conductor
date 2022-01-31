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
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.TestDeciderService;
import com.netflix.conductor.dao.*;
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
    private ModelMapper modelMapper;
    private ExecutionDAOFacade executionDAOFacade;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        executionDAO = mock(ExecutionDAO.class);
        QueueDAO queueDAO = mock(QueueDAO.class);
        indexDAO = mock(IndexDAO.class);
        modelMapper = mock(ModelMapper.class);
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
                        modelMapper,
                        objectMapper,
                        properties);
    }

    @Test
    public void testGetWorkflow() throws Exception {
        when(executionDAO.getWorkflow(any(), anyBoolean())).thenReturn(new WorkflowModel());
        when(modelMapper.getWorkflow(any(WorkflowModel.class))).thenReturn(new Workflow());
        Workflow workflow = executionDAOFacade.getWorkflow("workflowId", true);
        assertNotNull(workflow);
        verify(indexDAO, never()).get(any(), any());
    }

    @Test
    public void testGetWorkflowModel() throws Exception {
        when(executionDAO.getWorkflow(any(), anyBoolean())).thenReturn(new WorkflowModel());
        when(modelMapper.getFullCopy(any(WorkflowModel.class))).thenReturn(new WorkflowModel());
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
        when(modelMapper.getWorkflow(any(WorkflowModel.class))).thenReturn(new Workflow());
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
        when(modelMapper.getWorkflow(any(WorkflowModel.class))).thenReturn(new Workflow());
        workflows =
                executionDAOFacade.getWorkflowsByCorrelationId(
                        "workflowName", "correlationId", true);
        assertNotNull(workflows);
        assertEquals(1, workflows.size());
    }

    @Test
    public void testRemoveWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        when(executionDAO.getWorkflow(anyString(), anyBoolean())).thenReturn(workflow);
        executionDAOFacade.removeWorkflow("workflowId", false);
        verify(indexDAO, never()).updateWorkflow(any(), any(), any());
        verify(indexDAO, times(1)).asyncRemoveWorkflow(workflow.getWorkflowId());
    }

    @Test
    public void testArchiveWorkflow() throws Exception {
        InputStream stream = TestDeciderService.class.getResourceAsStream("/completed.json");
        WorkflowModel workflow = objectMapper.readValue(stream, WorkflowModel.class);

        when(executionDAO.getWorkflow(anyString(), anyBoolean())).thenReturn(workflow);
        executionDAOFacade.removeWorkflow("workflowId", true);
        verify(indexDAO, times(1)).updateWorkflow(any(), any(), any());
        verify(indexDAO, never()).removeWorkflow(any());
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
}
