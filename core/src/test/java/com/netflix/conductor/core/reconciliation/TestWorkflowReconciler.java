/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.reconciliation;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestWorkflowReconciler {

    @InjectMocks private WorkflowReconciler workflowReconciler;

    @Mock private QueueDAO queueDAO;

    @Mock private ExecutionDAO executionDAO;

    @Mock private ConductorProperties conductorProperties;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(conductorProperties.getSweeperThreadCount()).thenReturn(5);
        when(conductorProperties.getSweeperWorkflowPollTimeout())
                .thenReturn(java.time.Duration.ofMillis(1000));
        workflowReconciler.start();
    }

    @Test
    public void testReconcilePendingWorkflows_NoWorkflowsRunning() {
        when(executionDAO.getRunningWorkflowIds()).thenReturn(Collections.emptyList());
        workflowReconciler.reconcileRunningWorkflowsAndDeciderQueue();
        verify(queueDAO, never()).push(anyString(), anyList());
    }

    @Test
    public void testReconcilePendingWorkflows_AllWorkflowsInQueue() {
        List<String> runningWorkflowIds = Arrays.asList("wf1", "wf2");
        Set<String> workflowIdsInQueue = new HashSet<>(Arrays.asList("wf1", "wf2"));

        when(executionDAO.getRunningWorkflowIds()).thenReturn(runningWorkflowIds);
        when(queueDAO.getMessages(DECIDER_QUEUE))
                .thenReturn(
                        workflowIdsInQueue.stream()
                                .map(id -> new Message(id, null, null, 0))
                                .collect(Collectors.toList()));

        workflowReconciler.reconcileRunningWorkflowsAndDeciderQueue();
        verify(queueDAO, never()).push(eq(DECIDER_QUEUE), anyList());
    }

    @Test
    public void testReconcilePendingWorkflows_WorkflowsMissingFromQueue() {
        List<String> runningWorkflowIds = Arrays.asList("wf1", "wf2", "wf3");
        Set<String> workflowIdsInQueue = new HashSet<>(Arrays.asList("wf1", "wf3"));

        when(executionDAO.getRunningWorkflowIds()).thenReturn(runningWorkflowIds);
        when(queueDAO.getMessages(DECIDER_QUEUE))
                .thenReturn(
                        workflowIdsInQueue.stream()
                                .map(id -> new Message(id, null, null, 0))
                                .collect(Collectors.toList()));

        workflowReconciler.reconcileRunningWorkflowsAndDeciderQueue();

        ArgumentCaptor<List<Message>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(queueDAO).push(eq(DECIDER_QUEUE), argumentCaptor.capture());
        List<Message> capturedMessages = argumentCaptor.getValue();
        assertEquals(
                "Expected to push missing workflow back into queue", 1, capturedMessages.size());
        assertEquals("wf2", capturedMessages.get(0).getId());
    }
}
