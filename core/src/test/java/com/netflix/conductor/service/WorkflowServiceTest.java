package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkflowServiceTest {

    private WorkflowExecutor mockWorkflowExecutor;

    private ExecutionService mockExecutionService;

    private MetadataService mockMetadata;

    private Configuration mockConfig;

    private WorkflowService workflowService;

    @Before
    public void before() {
        this.mockWorkflowExecutor = Mockito.mock(WorkflowExecutor.class);
        this.mockExecutionService = Mockito.mock(ExecutionService.class);
        this.mockMetadata = Mockito.mock(MetadataService.class);
        this.mockConfig = Mockito.mock(Configuration.class);

        when(mockConfig.getIntProperty(anyString(), anyInt())).thenReturn(5_000);
        this.workflowService = new WorkflowService(this.mockWorkflowExecutor, this.mockExecutionService,
                this.mockMetadata, this.mockConfig);
    }

    @Test
    public void testStartWorkflow() throws Exception {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("w123");

        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        startWorkflowRequest.setInput(input);
        String workflowID = "w112";

        when(mockMetadata.getWorkflowDef(anyString(), anyInt())).thenReturn(workflowDef);
        when(mockWorkflowExecutor.startWorkflow(anyString(), anyInt(), anyString(),
                anyMapOf(String.class, Object.class), any(String.class),
                anyMapOf(String.class, String.class))).thenReturn(workflowID);
        assertEquals("w112", workflowService.startWorkflow(startWorkflowRequest));
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionStartWorkflowMessage() throws Exception {
        try {
            when(mockMetadata.getWorkflowDef(anyString(), anyInt())).thenReturn(null);

            StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
            startWorkflowRequest.setName("w123");
            startWorkflowRequest.setVersion(1);
            workflowService.startWorkflow(startWorkflowRequest);
        } catch (ApplicationException ex) {
            String message = "No such workflow found by name: w123, version: 1";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test
    public void testStartWorkflowParam() throws Exception {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);

        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        String workflowID = "w112";

        when(mockMetadata.getWorkflowDef(anyString(), anyInt())).thenReturn(workflowDef);
        when(mockWorkflowExecutor.startWorkflow(anyString(), anyInt(), anyString(),
                anyMapOf(String.class, Object.class), any(String.class))).thenReturn(workflowID);
        assertEquals("w112", workflowService.startWorkflow("test", 1, "c123", input));
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionStartWorkflowMessageParam() throws Exception {
        try {
            when(mockMetadata.getWorkflowDef(anyString(), anyInt())).thenReturn(null);

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

    @Test
    public void testGetWorklfowsSingleCorrelationId() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> workflowArrayList = new ArrayList<Workflow>() {{
            add(workflow);
        }};

        when(mockExecutionService.getWorkflowInstances(anyString(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(workflowArrayList);
        assertEquals(workflowArrayList, workflowService.getWorkflows("test", "c123",
                true, true));
    }

    @Test
    public void testGetWorklfowsMultipleCorrelationId() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> workflowArrayList = new ArrayList<Workflow>() {{
            add(workflow);
        }};

        List<String> correlationIdList = new ArrayList<String>() {{
            add("c123");
        }};

        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        workflowMap.put("c123", workflowArrayList);

        when(mockExecutionService.getWorkflowInstances(anyString(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(workflowArrayList);
        assertEquals(workflowMap, workflowService.getWorkflows("test", true,
                true, correlationIdList));
    }

    @Test
    public void testGetExecutionStatus() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        when(mockExecutionService.getExecutionStatus(anyString(), anyBoolean())).thenReturn(workflow);
        assertEquals(workflow, workflowService.getExecutionStatus("w123", true));
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionGetExecutionStatus() throws Exception {
        try {
            when(mockExecutionService.getExecutionStatus(anyString(), anyBoolean())).thenReturn(null);
            workflowService.getExecutionStatus("w123", true);
        } catch (ApplicationException ex) {
            String message = "Workflow with Id: w123 not found.";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test
    public void testDeleteWorkflow() throws Exception {
        workflowService.deleteWorkflow("w123", true);
        verify(mockExecutionService, times(1)).removeWorkflow(anyString(), anyBoolean());
    }

    @Test(expected = ApplicationException.class)
    public void testInvalidDeleteWorkflow() throws Exception {
        try {
            workflowService.deleteWorkflow(null, true);
        } catch (ApplicationException ex) {
            String message = "WorkflowId cannot be null or empty.";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test(expected = ApplicationException.class)
    public void testInvalidWorkflowNameGetRunningWorkflows() throws Exception {
        try {
            workflowService.getRunningWorkflows(null, 123, null, null);
        } catch (ApplicationException ex) {
            String message = "Workflow name cannot be null or empty.";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test
    public void testGetRunningWorkflowsTime() throws Exception{
        workflowService.getRunningWorkflows("test", 1, 100L, 120L);
        verify(mockWorkflowExecutor, times(1)).getWorkflows(anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    public void testGetRunningWorkflows() throws Exception{
        workflowService.getRunningWorkflows("test", 1, null, null);
        verify(mockWorkflowExecutor, times(1)).getRunningWorkflowIds(anyString());
    }

    @Test
    public void testDecideWorkflow() throws Exception {
        workflowService.decideWorkflow("test");
        verify(mockWorkflowExecutor, times(1)).decide(anyString());
    }

    @Test
    public void testPauseWorkflow() throws Exception{
        workflowService.pauseWorkflow("test");
        verify(mockWorkflowExecutor, times(1)).pauseWorkflow(anyString());
    }

    @Test
    public void testResumeWorkflow() throws Exception {
        workflowService.resumeWorkflow("test");
        verify(mockWorkflowExecutor, times(1)).resumeWorkflow(anyString());
    }

    @Test
    public void testSkipTaskFromWorkflow() throws Exception {
        workflowService.skipTaskFromWorkflow("test", "testTask", null);
        verify(mockWorkflowExecutor, times(1)).skipTaskFromWorkflow(anyString(), anyString(),
                any(SkipTaskRequest.class));
    }

    @Test
    public void testRerunWorkflow() throws Exception {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        workflowService.rerunWorkflow("test", request);
        verify(mockWorkflowExecutor, times(1)).rerun(any(RerunWorkflowRequest.class));
    }

    @Test
    public void testRerunWorkflowReturnWorkflowId() throws Exception {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        String workflowId = "w123";
        when(mockWorkflowExecutor.rerun(any(RerunWorkflowRequest.class))).thenReturn(workflowId);
        assertEquals(workflowId, workflowService.rerunWorkflow("test", request));
    }

    @Test
    public void testRestartWorkflow() throws Exception {
        workflowService.restartWorkflow("w123");
        verify(mockWorkflowExecutor, times(1)).rewind(anyString());
    }

    @Test
    public void testRetryWorkflow() throws Exception {
        workflowService.retryWorkflow("w123");
        verify(mockWorkflowExecutor, times(1)).retry(anyString());
    }

    @Test
    public void testResetWorkflow() throws Exception{
        workflowService.resetWorkflow("w123");
        verify(mockWorkflowExecutor, times(1)).resetCallbacksForInProgressTasks(anyString());
    }

    @Test
    public void testTerminateWorkflow() throws Exception {
        workflowService.terminateWorkflow("w123", "test");
        verify(mockWorkflowExecutor, times(1)).terminateWorkflow(anyString(), anyString());
    }

    @Test
    public void testSearchWorkflows() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        WorkflowSummary workflowSummary = new WorkflowSummary(workflow);
        List<WorkflowSummary> listOfWorkflowSummary = new ArrayList<WorkflowSummary>() {{
            add(workflowSummary);
        }};
        SearchResult<WorkflowSummary> searchResult = new SearchResult<WorkflowSummary>(100, listOfWorkflowSummary);

        when(mockExecutionService.search(anyString(), anyString(), anyInt(), anyInt(), anyListOf(String.class))).thenReturn(searchResult);
        assertEquals(searchResult, workflowService.searchWorkflows(0,100,"asc", "*", "*"));
    }

    @Test(expected = ApplicationException.class)
    public void testInvalidSizeSearchWorkflows() throws Exception {
        try {
            workflowService.searchWorkflows(0,6000,"asc", "*", "*");
        } catch (ApplicationException ex) {
            String message = "Cannot return more than 5000 workflows. Please use pagination.";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("IllegalArgumentException did not throw!");
    }

    @Test
    public void searchWorkflowsByTasks() {
        workflowService.searchWorkflowsByTasks(0,100,"asc", "*", "*");
        verify(mockExecutionService, times(1)).searchWorkflowByTasks(anyString(), anyString(), anyInt(), anyInt(), anyListOf(String.class));
    }
}