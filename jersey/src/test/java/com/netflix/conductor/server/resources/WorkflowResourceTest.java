package com.netflix.conductor.server.resources.v1;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.WorkflowResourceInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkflowResourceTest {

    private WorkflowResourceInfo mockWorkflowResourceInfo;

    private WorkflowExecutor mockWorkflowExecutor;
    private ExecutionService mockExecutionService;
    private MetadataService mockMetadata;
    private Configuration mockConfig;

    private WorkflowResource workflowResource;

    @Before
    public void before() {
        this.mockWorkflowResourceInfo = Mockito.mock(WorkflowResourceInfo.class);
        this.mockWorkflowExecutor = Mockito.mock(WorkflowExecutor.class);
        this.mockExecutionService = Mockito.mock(ExecutionService.class);
        this.mockMetadata = Mockito.mock(MetadataService.class);
        this.mockConfig = Mockito.mock(Configuration.class);
        this.workflowResource = new WorkflowResource(this.mockWorkflowExecutor, this.mockExecutionService,
                this.mockMetadata, this.mockConfig);
    }

    @Test
    public void testStartWorkflow() throws Exception {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("w123");
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        startWorkflowRequest.setInput(input);
        String workflowID = "w112";
        when(mockWorkflowResourceInfo.startWorkflow(any(StartWorkflowRequest.class))).thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow(startWorkflowRequest));
    }

    @Test
    public void testStartWorkflowParam() throws Exception{
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        String workflowID = "w112";
        when(mockWorkflowResourceInfo.startWorkflow(anyString(), anyInt(), anyString(), anyMapOf(String.class, Object.class))).thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow("test1", 1, "c123", input));
    }

    @Test
    public void getWorkflows() throws Exception{
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("123");
        ArrayList<Workflow> listOfWorkflows = new ArrayList<Workflow>() {{
            add(workflow);
        }};
        when(mockWorkflowResourceInfo.getWorklfows(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(listOfWorkflows);
        assertEquals(listOfWorkflows, workflowResource.getWorkflows("test1", "123", true, true));
    }

    @Test
    public void getWorkflows1() {
    }

    @Test
    public void getExecutionStatus() {
    }

    @Test
    public void delete() {
    }

    @Test
    public void getRunningWorkflow() {
    }

    @Test
    public void testDecide() throws Exception {
        WorkflowResourceInfo mockWorkflowResourceInfo = Mockito.mock(WorkflowResourceInfo.class);
        workflowResource.decide("SIMPLE");
        verify(mockWorkflowResourceInfo, times(1)).decideWorkflow(anyString());
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
    public void rerun() {
    }

    @Test
    public void restart() {
    }

    @Test
    public void retry() {
    }

    @Test
    public void resetWorkflow() {
    }

    @Test
    public void terminate() {
    }

    @Test
    public void search() {
    }

    @Test
    public void searchWorkflowsByTasks() {
    }
}