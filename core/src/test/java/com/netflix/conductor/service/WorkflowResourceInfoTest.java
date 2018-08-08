package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.when;

public class WorkflowResourceInfoTest {


    private WorkflowExecutor mockWorkflowExecutor;

    private ExecutionService mockExecutionService;

    private MetadataService mockMetadata;

    private Configuration mockConfig;

    private WorkflowResourceInfo workflowResourceInfo;

    @Before
    public void before() {
        this.mockWorkflowExecutor = Mockito.mock(WorkflowExecutor.class);
        this.mockExecutionService = Mockito.mock(ExecutionService.class);
        this.mockMetadata = Mockito.mock(MetadataService.class);
        this.mockConfig = Mockito.mock(Configuration.class);
        this.workflowResourceInfo = new WorkflowResourceInfo(this.mockWorkflowExecutor, this.mockExecutionService,
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
        assertEquals("w112", workflowResourceInfo.startWorkflow(startWorkflowRequest));
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionStartWorkflowMessage() throws Exception {
        try
        {
            when(mockMetadata.getWorkflowDef(anyString(), anyInt())).thenReturn(null);

            StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
            startWorkflowRequest.setName("w123");
            startWorkflowRequest.setVersion(1);
            workflowResourceInfo.startWorkflow(startWorkflowRequest);
        }
        catch(ApplicationException ex)
        {
            String message = "No such workflow found by name=w123, version=1";
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
        assertEquals("w112", workflowResourceInfo.startWorkflow("test", 1, "c123", input));
    }

    @Test(expected = ApplicationException.class)
    public void testApplicationExceptionStartWorkflowMessageParam() throws Exception {
        try
        {
            when(mockMetadata.getWorkflowDef(anyString(), anyInt())).thenReturn(null);

            Map<String, Object> input = new HashMap<>();
            input.put("1", "abc");

            workflowResourceInfo.startWorkflow("test", 1, "c123", input);
        }
        catch(ApplicationException ex)
        {
            String message = "No such workflow found by name=test, version=1";
            assertEquals(message, ex.getMessage());
            throw ex;
        }
        fail("ApplicationException did not throw!");
    }

    @Test
    public void startWorkflow1() {
    }

    @Test
    public void getWorklfows() {
    }

    @Test
    public void getWorkflows() {
    }

    @Test
    public void getExecutionStatus() {
    }

    @Test
    public void deleteWorkflow() {
    }

    @Test
    public void getRunningWorkflows() {
    }

    @Test
    public void decideWorkflow() {
    }

    @Test
    public void pauseWorkflow() {
    }

    @Test
    public void resumeWorkflow() {
    }

    @Test
    public void skipTaskFromWorkflow() {
    }

    @Test
    public void rerunWorkflow() {
    }

    @Test
    public void restartWorkflow() {
    }

    @Test
    public void retryWorkflow() {
    }

    @Test
    public void resetWorkflow() {
    }

    @Test
    public void terminateWorkflow() {
    }

    @Test
    public void searchWorkflows() {
    }

    @Test
    public void searchWorkflowsByTasks() {
    }
}