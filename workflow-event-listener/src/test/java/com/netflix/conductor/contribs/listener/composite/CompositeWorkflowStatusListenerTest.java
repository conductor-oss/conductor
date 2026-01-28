/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.contribs.listener.composite;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.WorkflowModel;

import static org.mockito.Mockito.*;

public class CompositeWorkflowStatusListenerTest {

    private WorkflowModel workflow;
    private WorkflowStatusListener listener1;
    private WorkflowStatusListener listener2;
    private WorkflowStatusListener listener3;
    private CompositeWorkflowStatusListener compositeListener;

    @Before
    public void setup() {
        workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId(UUID.randomUUID().toString());

        listener1 = Mockito.mock(WorkflowStatusListener.class);
        listener2 = Mockito.mock(WorkflowStatusListener.class);
        listener3 = Mockito.mock(WorkflowStatusListener.class);
    }

    @Test
    public void testAllDelegatesInvokedOnCompleted() {
        compositeListener =
                new CompositeWorkflowStatusListener(Arrays.asList(listener1, listener2, listener3));

        compositeListener.onWorkflowCompleted(workflow);

        verify(listener1, times(1)).onWorkflowCompleted(workflow);
        verify(listener2, times(1)).onWorkflowCompleted(workflow);
        verify(listener3, times(1)).onWorkflowCompleted(workflow);
    }

    @Test
    public void testAllDelegatesInvokedOnTerminated() {
        compositeListener =
                new CompositeWorkflowStatusListener(Arrays.asList(listener1, listener2));

        compositeListener.onWorkflowTerminated(workflow);

        verify(listener1, times(1)).onWorkflowTerminated(workflow);
        verify(listener2, times(1)).onWorkflowTerminated(workflow);
    }

    @Test
    public void testOneListenerFailureDoesNotAffectOthers() {
        // listener2 throws exception
        doThrow(new RuntimeException("Listener 2 failed"))
                .when(listener2)
                .onWorkflowCompleted(any());

        compositeListener =
                new CompositeWorkflowStatusListener(Arrays.asList(listener1, listener2, listener3));

        // Should not throw exception
        compositeListener.onWorkflowCompleted(workflow);

        // Verify listener1 and listener3 were still invoked
        verify(listener1, times(1)).onWorkflowCompleted(workflow);
        verify(listener3, times(1)).onWorkflowCompleted(workflow);
    }

    @Test
    public void testMultipleListenersFailureDoesNotStopExecution() {
        // Both listener1 and listener2 throw exceptions
        doThrow(new RuntimeException("Listener 1 failed")).when(listener1).onWorkflowStarted(any());
        doThrow(new RuntimeException("Listener 2 failed")).when(listener2).onWorkflowStarted(any());

        compositeListener =
                new CompositeWorkflowStatusListener(Arrays.asList(listener1, listener2, listener3));

        // Should not throw exception
        compositeListener.onWorkflowStarted(workflow);

        // Verify listener3 was still invoked
        verify(listener3, times(1)).onWorkflowStarted(workflow);
    }

    @Test
    public void testAllWorkflowEventsAreDelegated() {
        compositeListener =
                new CompositeWorkflowStatusListener(Collections.singletonList(listener1));

        compositeListener.onWorkflowStarted(workflow);
        compositeListener.onWorkflowCompleted(workflow);
        compositeListener.onWorkflowTerminated(workflow);
        compositeListener.onWorkflowFinalized(workflow);
        compositeListener.onWorkflowRestarted(workflow);
        compositeListener.onWorkflowRerun(workflow);
        compositeListener.onWorkflowPaused(workflow);
        compositeListener.onWorkflowResumed(workflow);
        compositeListener.onWorkflowRetried(workflow);

        verify(listener1, times(1)).onWorkflowStarted(workflow);
        verify(listener1, times(1)).onWorkflowCompleted(workflow);
        verify(listener1, times(1)).onWorkflowTerminated(workflow);
        verify(listener1, times(1)).onWorkflowFinalized(workflow);
        verify(listener1, times(1)).onWorkflowRestarted(workflow);
        verify(listener1, times(1)).onWorkflowRerun(workflow);
        verify(listener1, times(1)).onWorkflowPaused(workflow);
        verify(listener1, times(1)).onWorkflowResumed(workflow);
        verify(listener1, times(1)).onWorkflowRetried(workflow);
    }

    @Test
    public void testEmptyListenersList() {
        compositeListener = new CompositeWorkflowStatusListener(Collections.emptyList());

        // Should not throw - just does nothing
        compositeListener.onWorkflowCompleted(workflow);
        compositeListener.onWorkflowTerminated(workflow);
    }

    @Test
    public void testSingleListener() {
        compositeListener =
                new CompositeWorkflowStatusListener(Collections.singletonList(listener1));

        compositeListener.onWorkflowCompleted(workflow);

        verify(listener1, times(1)).onWorkflowCompleted(workflow);
        verifyNoInteractions(listener2, listener3);
    }
}