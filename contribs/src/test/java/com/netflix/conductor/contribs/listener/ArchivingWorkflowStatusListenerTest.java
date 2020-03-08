/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.contribs.listener;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * @author pavel.halabala
 */
public class ArchivingWorkflowStatusListenerTest {

    Workflow wf;
    ExecutionDAOFacade executionDAOFacade;
    ArchivingWorkflowStatusListener cut;

    @Before
    public void before() {
        wf = new Workflow();
        wf.setWorkflowId(UUID.randomUUID().toString());

        executionDAOFacade = Mockito.mock(ExecutionDAOFacade.class);
        cut = new ArchivingWorkflowStatusListener(executionDAOFacade);
    }

    @Test
    public void testArchiveOnWorkflowCompleted() {
        cut.onWorkflowCompleted(wf);
        verify(executionDAOFacade, times(1))
                .removeWorkflow(wf.getWorkflowId(), true);
        verifyNoMoreInteractions(executionDAOFacade);
    }

    @Test
    public void testArchiveOnWorkflowTerminated() {
        cut.onWorkflowTerminated(wf);
        verify(executionDAOFacade, times(1))
                .removeWorkflow(wf.getWorkflowId(), true);
        verifyNoMoreInteractions(executionDAOFacade);
    }
}
