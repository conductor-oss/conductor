/*
 * Copyright 2024 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.netflix.conductor.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminServiceImplReindexTest {

    @Mock private ConductorProperties properties;
    @Mock private ExecutionService executionService;
    @Mock private QueueDAO queueDAO;
    @Mock private ExecutionDAO executionDAO;
    @Mock private IndexDAO indexDAO;

    private AdminServiceImpl adminService;

    @Before
    public void setUp() {
        adminService =
                new AdminServiceImpl(
                        properties,
                        executionService,
                        queueDAO,
                        executionDAO,
                        indexDAO,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
    }

    private void waitForReindex() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            String state = (String) adminService.getReindexStatus().get("state");
            if ("COMPLETED".equals(state) || "FAILED".equals(state)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Reindex did not complete within 5 seconds");
    }

    private WorkflowModel stubWorkflow() {
        WorkflowModel wf = mock(WorkflowModel.class);
        WorkflowDef def = new WorkflowDef();
        def.setName("test-workflow");
        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        when(wf.toWorkflow()).thenReturn(workflow);
        when(wf.getTasks()).thenReturn(Collections.emptyList());
        return wf;
    }

    @Test
    public void testIdleToCompleted() throws Exception {
        WorkflowModel wf = stubWorkflow();
        when(executionDAO.getWorkflowCount()).thenReturn(2L);
        when(executionDAO.getAllWorkflowIds(0, 100)).thenReturn(Arrays.asList("wf1", "wf2"));
        when(executionDAO.getAllWorkflowIds(100, 100)).thenReturn(Collections.emptyList());
        when(executionDAO.getWorkflow("wf1", true)).thenReturn(wf);
        when(executionDAO.getWorkflow("wf2", true)).thenReturn(wf);

        Map<String, Object> result = adminService.startReindex();
        assertEquals("STARTED", result.get("state"));

        waitForReindex();

        Map<String, Object> status = adminService.getReindexStatus();
        assertEquals("COMPLETED", status.get("state"));
        assertEquals(2L, status.get("processed"));
        assertEquals(0L, status.get("errors"));
        verify(indexDAO, times(2)).indexWorkflow(any());
    }

    @Test
    public void testCompletedToRunningResetsCounters() throws Exception {
        WorkflowModel wf = stubWorkflow();
        when(executionDAO.getWorkflowCount()).thenReturn(1L);
        when(executionDAO.getAllWorkflowIds(0, 100)).thenReturn(Collections.singletonList("wf1"));
        when(executionDAO.getAllWorkflowIds(100, 100)).thenReturn(Collections.emptyList());
        when(executionDAO.getWorkflow("wf1", true)).thenReturn(wf);

        adminService.startReindex();
        waitForReindex();
        assertEquals("COMPLETED", adminService.getReindexStatus().get("state"));

        Map<String, Object> result = adminService.startReindex();
        assertEquals("STARTED", result.get("state"));
        waitForReindex();

        Map<String, Object> status = adminService.getReindexStatus();
        assertEquals("COMPLETED", status.get("state"));
        assertEquals(1L, status.get("processed"));
        assertEquals(0L, status.get("errors"));
    }

    @Test
    public void testDoublePostReturnsAlreadyRunning() {
        when(executionDAO.getWorkflowCount()).thenReturn(0L);
        // make background thread block so state stays RUNNING during second call
        when(executionDAO.getAllWorkflowIds(anyInt(), anyInt()))
                .thenAnswer(
                        inv -> {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return Collections.emptyList();
                        });

        adminService.startReindex();

        Map<String, Object> result = adminService.startReindex();
        assertEquals("ALREADY_RUNNING", result.get("state"));
    }

    @Test
    public void testUnsupportedBackend() {
        when(executionDAO.getWorkflowCount())
                .thenThrow(new UnsupportedOperationException("not supported"));

        Map<String, Object> result = adminService.startReindex();
        assertEquals("UNSUPPORTED", result.get("state"));
    }

    @Test
    public void testPaginationCallsCorrectOffsets() throws Exception {
        List<String> batch1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) batch1.add("wf" + i);
        WorkflowModel wf = stubWorkflow();

        when(executionDAO.getWorkflowCount()).thenReturn(100L);
        when(executionDAO.getAllWorkflowIds(0, 100)).thenReturn(batch1);
        when(executionDAO.getAllWorkflowIds(100, 100)).thenReturn(Collections.emptyList());
        when(executionDAO.getWorkflow(anyString(), eq(true))).thenReturn(wf);

        adminService.startReindex();
        waitForReindex();

        verify(executionDAO).getAllWorkflowIds(0, 100);
        verify(executionDAO).getAllWorkflowIds(100, 100);
        verify(indexDAO, times(100)).indexWorkflow(any());
    }
}
