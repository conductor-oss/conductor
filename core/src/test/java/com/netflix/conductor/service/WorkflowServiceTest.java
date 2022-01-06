/*
 * Copyright 2021 Netflix, Inc.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;

import static com.netflix.conductor.TestUtils.getConstraintViolationMessages;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
public class WorkflowServiceTest {

    @TestConfiguration
    static class TestWorkflowConfiguration {

        @Bean
        public WorkflowExecutor workflowExecutor() {
            return mock(WorkflowExecutor.class);
        }

        @Bean
        public ExecutionService executionService() {
            return mock(ExecutionService.class);
        }

        @Bean
        public MetadataService metadataService() {
            return mock(MetadataServiceImpl.class);
        }

        @Bean
        public WorkflowService workflowService(
                WorkflowExecutor workflowExecutor,
                ExecutionService executionService,
                MetadataService metadataService) {
            return new WorkflowServiceImpl(workflowExecutor, executionService, metadataService);
        }
    }

    @Autowired private WorkflowExecutor workflowExecutor;

    @Autowired private ExecutionService executionService;

    @Autowired private MetadataService metadataService;

    @Autowired private WorkflowService workflowService;

    @Test(expected = ConstraintViolationException.class)
    public void testStartWorkflowNull() {
        try {
            workflowService.startWorkflow(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("StartWorkflowRequest cannot be null"));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testStartWorkflowName() {
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("1", "abc");
            workflowService.startWorkflow(null, 1, "abc", input);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Workflow name cannot be null or empty"));
            throw ex;
        }
    }

    @Test
    public void testStartWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("test");
        startWorkflowRequest.setVersion(1);

        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        startWorkflowRequest.setInput(input);
        String workflowID = "w112";

        when(metadataService.getWorkflowDef("test", 1)).thenReturn(workflowDef);
        when(workflowExecutor.startWorkflow(
                        anyString(),
                        anyInt(),
                        isNull(),
                        anyInt(),
                        anyMap(),
                        isNull(),
                        isNull(),
                        anyMap()))
                .thenReturn(workflowID);
        assertEquals("w112", workflowService.startWorkflow(startWorkflowRequest));
    }

    @Test
    public void testStartWorkflowParam() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);

        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        String workflowID = "w112";

        when(metadataService.getWorkflowDef("test", 1)).thenReturn(workflowDef);
        when(workflowExecutor.startWorkflow(
                        anyString(), anyInt(), anyString(), anyInt(), anyMap(), isNull()))
                .thenReturn(workflowID);
        assertEquals("w112", workflowService.startWorkflow("test", 1, "c123", input));
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionStartWorkflowMessageParam() {
        try {
            when(metadataService.getWorkflowDef("test", 1)).thenReturn(null);

            Map<String, Object> input = new HashMap<>();
            input.put("1", "abc");

            workflowService.startWorkflow("test", 1, "c123", input);
        } catch (ApplicationException ex) {
            String message = "No such workflow found by name: test, version: 1";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetWorkflowsNoName() {
        try {
            workflowService.getWorkflows("", "c123", true, true);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Workflow name cannot be null or empty"));
            throw ex;
        }
    }

    @Test
    public void testGetWorklfowsSingleCorrelationId() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> workflowArrayList = Collections.singletonList(workflow);

        when(executionService.getWorkflowInstances(
                        anyString(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(workflowArrayList);
        assertEquals(workflowArrayList, workflowService.getWorkflows("test", "c123", true, true));
    }

    @Test
    public void testGetWorklfowsMultipleCorrelationId() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> workflowArrayList = Collections.singletonList(workflow);

        List<String> correlationIdList = Collections.singletonList("c123");

        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        workflowMap.put("c123", workflowArrayList);

        when(executionService.getWorkflowInstances(
                        anyString(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(workflowArrayList);
        assertEquals(
                workflowMap, workflowService.getWorkflows("test", true, true, correlationIdList));
    }

    @Test
    public void testGetExecutionStatus() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        when(executionService.getExecutionStatus(anyString(), anyBoolean())).thenReturn(workflow);
        assertEquals(workflow, workflowService.getExecutionStatus("w123", true));
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetExecutionStatusNoWorkflowId() {
        try {
            workflowService.getExecutionStatus("", true);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionGetExecutionStatus() {
        try {
            when(executionService.getExecutionStatus(anyString(), anyBoolean())).thenReturn(null);
            workflowService.getExecutionStatus("w123", true);
        } catch (ApplicationException ex) {
            String message = "Workflow with Id: w123 not found.";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test
    public void testDeleteWorkflow() {
        workflowService.deleteWorkflow("w123", true);
        verify(executionService, times(1)).removeWorkflow(anyString(), anyBoolean());
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInvalidDeleteWorkflow() {
        try {
            workflowService.deleteWorkflow(null, true);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInvalidPauseWorkflow() {
        try {
            workflowService.pauseWorkflow(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInvalidResumeWorkflow() {
        try {
            workflowService.resumeWorkflow(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInvalidSkipTaskFromWorkflow() {
        try {
            SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
            workflowService.skipTaskFromWorkflow(null, null, skipTaskRequest);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId name cannot be null or empty."));
            assertTrue(messages.contains("TaskReferenceName cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInvalidWorkflowNameGetRunningWorkflows() {
        try {
            workflowService.getRunningWorkflows(null, 123, null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Workflow name cannot be null or empty."));
            throw ex;
        }
    }

    @Test
    public void testGetRunningWorkflowsTime() {
        workflowService.getRunningWorkflows("test", 1, 100L, 120L);
        verify(workflowExecutor, times(1))
                .getWorkflows(anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    public void testGetRunningWorkflows() {
        workflowService.getRunningWorkflows("test", 1, null, null);
        verify(workflowExecutor, times(1)).getRunningWorkflowIds(anyString(), anyInt());
    }

    @Test
    public void testDecideWorkflow() {
        workflowService.decideWorkflow("test");
        verify(workflowExecutor, times(1)).decide(anyString());
    }

    @Test
    public void testPauseWorkflow() {
        workflowService.pauseWorkflow("test");
        verify(workflowExecutor, times(1)).pauseWorkflow(anyString());
    }

    @Test
    public void testResumeWorkflow() {
        workflowService.resumeWorkflow("test");
        verify(workflowExecutor, times(1)).resumeWorkflow(anyString());
    }

    @Test
    public void testSkipTaskFromWorkflow() {
        workflowService.skipTaskFromWorkflow("test", "testTask", null);
        verify(workflowExecutor, times(1)).skipTaskFromWorkflow(anyString(), anyString(), isNull());
    }

    @Test
    public void testRerunWorkflow() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        workflowService.rerunWorkflow("test", request);
        verify(workflowExecutor, times(1)).rerun(any(RerunWorkflowRequest.class));
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRerunWorkflowNull() {
        try {
            workflowService.rerunWorkflow(null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            assertTrue(messages.contains("RerunWorkflowRequest cannot be null."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRestartWorkflowNull() {
        try {
            workflowService.restartWorkflow(null, false);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRetryWorkflowNull() {
        try {
            workflowService.retryWorkflow(null, false);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testResetWorkflowNull() {
        try {
            workflowService.resetWorkflow(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testTerminateWorkflowNull() {
        try {
            workflowService.terminateWorkflow(null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            throw ex;
        }
    }

    @Test
    public void testRerunWorkflowReturnWorkflowId() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        String workflowId = "w123";
        when(workflowExecutor.rerun(any(RerunWorkflowRequest.class))).thenReturn(workflowId);
        assertEquals(workflowId, workflowService.rerunWorkflow("test", request));
    }

    @Test
    public void testRestartWorkflow() {
        workflowService.restartWorkflow("w123", false);
        verify(workflowExecutor, times(1)).restart(anyString(), anyBoolean());
    }

    @Test
    public void testRetryWorkflow() {
        workflowService.retryWorkflow("w123", false);
        verify(workflowExecutor, times(1)).retry(anyString(), anyBoolean());
    }

    @Test
    public void testResetWorkflow() {
        workflowService.resetWorkflow("w123");
        verify(workflowExecutor, times(1)).resetCallbacksForWorkflow(anyString());
    }

    @Test
    public void testTerminateWorkflow() {
        workflowService.terminateWorkflow("w123", "test");
        verify(workflowExecutor, times(1)).terminateWorkflow(anyString(), anyString());
    }

    @Test
    public void testSearchWorkflows() {
        Workflow workflow = new Workflow();
        WorkflowDef def = new WorkflowDef();
        def.setName("name");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        workflow.setCorrelationId("c123");

        WorkflowSummary workflowSummary = new WorkflowSummary(workflow);
        List<WorkflowSummary> listOfWorkflowSummary = Collections.singletonList(workflowSummary);

        SearchResult<WorkflowSummary> searchResult = new SearchResult<>(100, listOfWorkflowSummary);

        when(executionService.search("*", "*", 0, 100, Collections.singletonList("asc")))
                .thenReturn(searchResult);
        assertEquals(searchResult, workflowService.searchWorkflows(0, 100, "asc", "*", "*"));
        assertEquals(
                searchResult,
                workflowService.searchWorkflows(
                        0, 100, Collections.singletonList("asc"), "*", "*"));
    }

    @Test
    public void testSearchWorkflowsV2() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> listOfWorkflow = Collections.singletonList(workflow);
        SearchResult<Workflow> searchResult = new SearchResult<>(1, listOfWorkflow);

        when(executionService.searchV2("*", "*", 0, 100, Collections.singletonList("asc")))
                .thenReturn(searchResult);
        assertEquals(searchResult, workflowService.searchWorkflowsV2(0, 100, "asc", "*", "*"));
        assertEquals(
                searchResult,
                workflowService.searchWorkflowsV2(
                        0, 100, Collections.singletonList("asc"), "*", "*"));
    }

    @Test
    public void testInvalidSizeSearchWorkflows() {
        ConstraintViolationException ex =
                assertThrows(
                        ConstraintViolationException.class,
                        () -> workflowService.searchWorkflows(0, 6000, "asc", "*", "*"));
        assertEquals(1, ex.getConstraintViolations().size());
        Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
        assertTrue(
                messages.contains(
                        "Cannot return more than 5000 workflows. Please use pagination."));
    }

    @Test
    public void testInvalidSizeSearchWorkflowsV2() {
        ConstraintViolationException ex =
                assertThrows(
                        ConstraintViolationException.class,
                        () -> workflowService.searchWorkflowsV2(0, 6000, "asc", "*", "*"));
        assertEquals(1, ex.getConstraintViolations().size());
        Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
        assertTrue(
                messages.contains(
                        "Cannot return more than 5000 workflows. Please use pagination."));
    }

    @Test
    public void testSearchWorkflowsByTasks() {
        Workflow workflow = new Workflow();
        WorkflowDef def = new WorkflowDef();
        def.setName("name");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        workflow.setCorrelationId("c123");

        WorkflowSummary workflowSummary = new WorkflowSummary(workflow);
        List<WorkflowSummary> listOfWorkflowSummary = Collections.singletonList(workflowSummary);
        SearchResult<WorkflowSummary> searchResult = new SearchResult<>(100, listOfWorkflowSummary);

        when(executionService.searchWorkflowByTasks(
                        "*", "*", 0, 100, Collections.singletonList("asc")))
                .thenReturn(searchResult);
        assertEquals(searchResult, workflowService.searchWorkflowsByTasks(0, 100, "asc", "*", "*"));
        assertEquals(
                searchResult,
                workflowService.searchWorkflowsByTasks(
                        0, 100, Collections.singletonList("asc"), "*", "*"));
    }

    @Test
    public void testSearchWorkflowsByTasksV2() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> listOfWorkflow = Collections.singletonList(workflow);
        SearchResult<Workflow> searchResult = new SearchResult<>(1, listOfWorkflow);

        when(executionService.searchWorkflowByTasksV2(
                        "*", "*", 0, 100, Collections.singletonList("asc")))
                .thenReturn(searchResult);
        assertEquals(
                searchResult, workflowService.searchWorkflowsByTasksV2(0, 100, "asc", "*", "*"));
        assertEquals(
                searchResult,
                workflowService.searchWorkflowsByTasksV2(
                        0, 100, Collections.singletonList("asc"), "*", "*"));
    }
}
