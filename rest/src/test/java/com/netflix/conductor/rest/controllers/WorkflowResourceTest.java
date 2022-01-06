/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.rest.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkflowResourceTest {

    @Mock private WorkflowService mockWorkflowService;

    private WorkflowResource workflowResource;

    @Before
    public void before() {
        this.mockWorkflowService = mock(WorkflowService.class);
        this.workflowResource = new WorkflowResource(this.mockWorkflowService);
    }

    @Test
    public void testStartWorkflow() {
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName("w123");
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        startWorkflowRequest.setInput(input);
        String workflowID = "w112";
        when(mockWorkflowService.startWorkflow(any(StartWorkflowRequest.class)))
                .thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow(startWorkflowRequest));
    }

    @Test
    public void testStartWorkflowParam() {
        Map<String, Object> input = new HashMap<>();
        input.put("1", "abc");
        String workflowID = "w112";
        when(mockWorkflowService.startWorkflow(
                        anyString(), anyInt(), anyString(), anyInt(), anyMap()))
                .thenReturn(workflowID);
        assertEquals("w112", workflowResource.startWorkflow("test1", 1, "c123", 0, input));
    }

    @Test
    public void getWorkflows() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("123");
        ArrayList<Workflow> listOfWorkflows =
                new ArrayList<>() {
                    {
                        add(workflow);
                    }
                };
        when(mockWorkflowService.getWorkflows(anyString(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(listOfWorkflows);
        assertEquals(listOfWorkflows, workflowResource.getWorkflows("test1", "123", true, true));
    }

    @Test
    public void testGetWorklfowsMultipleCorrelationId() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        List<Workflow> workflowArrayList =
                new ArrayList<>() {
                    {
                        add(workflow);
                    }
                };

        List<String> correlationIdList =
                new ArrayList<>() {
                    {
                        add("c123");
                    }
                };

        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        workflowMap.put("c123", workflowArrayList);

        when(mockWorkflowService.getWorkflows(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(workflowMap);
        assertEquals(
                workflowMap, workflowResource.getWorkflows("test", true, true, correlationIdList));
    }

    @Test
    public void testGetExecutionStatus() {
        Workflow workflow = new Workflow();
        workflow.setCorrelationId("c123");

        when(mockWorkflowService.getExecutionStatus(anyString(), anyBoolean()))
                .thenReturn(workflow);
        assertEquals(workflow, workflowResource.getExecutionStatus("w123", true));
    }

    @Test
    public void testDelete() {
        workflowResource.delete("w123", true);
        verify(mockWorkflowService, times(1)).deleteWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testGetRunningWorkflow() {
        List<String> listOfWorklfows =
                new ArrayList<>() {
                    {
                        add("w123");
                    }
                };
        when(mockWorkflowService.getRunningWorkflows(anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(listOfWorklfows);
        assertEquals(listOfWorklfows, workflowResource.getRunningWorkflow("w123", 1, 12L, 13L));
    }

    @Test
    public void testDecide() {
        workflowResource.decide("w123");
        verify(mockWorkflowService, times(1)).decideWorkflow(anyString());
    }

    @Test
    public void testPauseWorkflow() {
        workflowResource.pauseWorkflow("w123");
        verify(mockWorkflowService, times(1)).pauseWorkflow(anyString());
    }

    @Test
    public void testResumeWorkflow() {
        workflowResource.resumeWorkflow("test");
        verify(mockWorkflowService, times(1)).resumeWorkflow(anyString());
    }

    @Test
    public void testSkipTaskFromWorkflow() {
        workflowResource.skipTaskFromWorkflow("test", "testTask", null);
        verify(mockWorkflowService, times(1))
                .skipTaskFromWorkflow(anyString(), anyString(), isNull());
    }

    @Test
    public void testRerun() {
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        workflowResource.rerun("test", request);
        verify(mockWorkflowService, times(1))
                .rerunWorkflow(anyString(), any(RerunWorkflowRequest.class));
    }

    @Test
    public void restart() {
        workflowResource.restart("w123", false);
        verify(mockWorkflowService, times(1)).restartWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testRetry() {
        workflowResource.retry("w123", false);
        verify(mockWorkflowService, times(1)).retryWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void testResetWorkflow() {
        workflowResource.resetWorkflow("w123");
        verify(mockWorkflowService, times(1)).resetWorkflow(anyString());
    }

    @Test
    public void testTerminate() {
        workflowResource.terminate("w123", "test");
        verify(mockWorkflowService, times(1)).terminateWorkflow(anyString(), anyString());
    }

    @Test
    public void testSearch() {
        workflowResource.search(0, 100, "asc", "*", "*");
        verify(mockWorkflowService, times(1))
                .searchWorkflows(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    public void testSearchV2() {
        workflowResource.searchV2(0, 100, "asc", "*", "*");
        verify(mockWorkflowService).searchWorkflowsV2(0, 100, "asc", "*", "*");
    }

    @Test
    public void testSearchWorkflowsByTasks() {
        workflowResource.searchWorkflowsByTasks(0, 100, "asc", "*", "*");
        verify(mockWorkflowService, times(1))
                .searchWorkflowsByTasks(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    public void testSearchWorkflowsByTasksV2() {
        workflowResource.searchWorkflowsByTasksV2(0, 100, "asc", "*", "*");
        verify(mockWorkflowService).searchWorkflowsByTasksV2(0, 100, "asc", "*", "*");
    }

    @Test
    public void testGetExternalStorageLocation() {
        workflowResource.getExternalStorageLocation("path", "operation", "payloadType");
        verify(mockWorkflowService).getExternalStorageLocation("path", "operation", "payloadType");
    }
}
