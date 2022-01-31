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
package com.netflix.conductor.contribs.listener;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.contribs.listener.archive.ArchivingWorkflowStatusListener;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.model.WorkflowModel;

import static org.mockito.Mockito.*;

/** @author pavel.halabala */
public class ArchivingWorkflowStatusListenerTest {

    WorkflowModel workflow;
    ExecutionDAOFacade executionDAOFacade;
    ArchivingWorkflowStatusListener listener;

    @Before
    public void before() {
        workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        def.setName("name1");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId(UUID.randomUUID().toString());

        executionDAOFacade = Mockito.mock(ExecutionDAOFacade.class);
        listener = new ArchivingWorkflowStatusListener(executionDAOFacade);
    }

    @Test
    public void testArchiveOnWorkflowCompleted() {
        listener.onWorkflowCompleted(workflow);
        verify(executionDAOFacade, times(1)).removeWorkflow(workflow.getWorkflowId(), true);
        verifyNoMoreInteractions(executionDAOFacade);
    }

    @Test
    public void testArchiveOnWorkflowTerminated() {
        listener.onWorkflowTerminated(workflow);
        verify(executionDAOFacade, times(1)).removeWorkflow(workflow.getWorkflowId(), true);
        verifyNoMoreInteractions(executionDAOFacade);
    }
}
