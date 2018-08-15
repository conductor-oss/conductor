package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkflowResourceTest {

    @Mock
    private WorkflowService mockWorkflowService;

    private WorkflowResource workflowResource;

    @Before
    public void before() {
        this.mockWorkflowService = Mockito.mock(WorkflowService.class);
        this.workflowResource = new WorkflowResource(this.mockWorkflowService);
    }

    @Test
    public void testStartWorkflow() throws Exception {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("w123");
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        startWorkflowRequest.setInput(input);
        String workflowID = "w112";
        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class))).thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow(startWorkflowRequest));
    }

    @Test
    public void testStartWorkflowParam() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        String workflowID = "w112";
        when(mockWorkflowService.startWorkflow(anyString(), anyInt(), anyString(), anyMapOf(String.class, Object.class))).thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow("test1", 1, "c123", input));
    }

    @Test
    public void getWorkflows() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("123");
        ArrayList<Workflow> listOfWorkflows = new ArrayList<Workflow>() {{
            add(workflow);
        }};
        when(mockWorkflowService.getWorkflows(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(listOfWorkflows);
        assertEquals(listOfWorkflows, workflowResource.getWorkflows("test1", "123", true, true));
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

        when(mockWorkflowService.getWorkflows(anyString(), anyBoolean(), anyBoolean(), anyListOf(String.class)))
                .thenReturn(workflowMap);
        assertEquals(workflowMap, workflowResource.getWorkflows("test", true,
                true, correlationIdList));
    }

    @Test
    public void testGetExecutionStatus() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        when(mockWorkflowService.getExecutionStatus(anyString(), anyBoolean())).thenReturn(workflow);
        assertEquals(workflow, workflowResource.getExecutionStatus("w123", true));
    }

    @Test
    public void testDelete() throws Exception {
        workflowResource.delete("w123", true);
        verify(mockWorkflowService, times(1)).deleteWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testGetRunningWorkflow() throws Exception {
        List<String> listOfWorklfows = new ArrayList<String>() {{
            add("w123");
        }};
        when(mockWorkflowService.getRunningWorkflows(anyString(), anyInt(), anyLong(), anyLong())).thenReturn(listOfWorklfows);
        assertEquals(listOfWorklfows, workflowResource.getRunningWorkflow("w123", 1, 12L, 13L));
    }

    @Test
    public void testDecide() throws Exception {
        workflowResource.decide("w123");
        verify(mockWorkflowService, times(1)).decideWorkflow(anyString());
    }

    @Test
    public void testPauseWorkflow() throws Exception {
        workflowResource.pauseWorkflow("w123");
        verify(mockWorkflowService, times(1)).pauseWorkflow(anyString());
    }

    @Test
    public void testResumeWorkflow() throws Exception {
        workflowResource.resumeWorkflow("test");
        verify(mockWorkflowService, times(1)).resumeWorkflow(anyString());
    }

    @Test
    public void testSkipTaskFromWorkflow() throws Exception {
        workflowResource.skipTaskFromWorkflow("test", "testTask", null);
        verify(mockWorkflowService, times(1)).skipTaskFromWorkflow(anyString(), anyString(),
                any(SkipTaskRequest.class));
    }

    @Test
    public void testRerun() throws Exception {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        workflowResource.rerun("test", request);
        verify(mockWorkflowService, times(1)).rerunWorkflow(anyString(), any(RerunWorkflowRequest.class));
    }

    @Test
    public void restart() throws Exception {
        workflowResource.restart("w123");
        verify(mockWorkflowService, times(1)).restartWorkflow(anyString());
    }

    @Test
    public void testRetry() throws Exception {
        workflowResource.retry("w123");
        verify(mockWorkflowService, times(1)).retryWorkflow(anyString());

    }

    @Test
    public void testResetWorkflow() throws Exception {
        workflowResource.resetWorkflow("w123");
        verify(mockWorkflowService, times(1)).resetWorkflow(anyString());
    }

    @Test
    public void testTerminate() throws Exception {
        workflowResource.terminate("w123", "test");
        verify(mockWorkflowService, times(1)).terminateWorkflow(anyString(), anyString());
    }

    @Test
    public void testSearch() {
        workflowResource.search(0, 100, "asc", "*", "*");
        verify(mockWorkflowService, times(1)).searchWorkflows(anyInt(), anyInt(),
                anyString(), anyString(), anyString());
    }

    @Test
    public void testSearchWorkflowsByTasks() {
        workflowResource.searchWorkflowsByTasks(0, 100, "asc", "*", "*");
        verify(mockWorkflowService, times(1)).searchWorkflowsByTasks(anyInt(), anyInt(),
                anyString(), anyString(), anyString());
    }
}