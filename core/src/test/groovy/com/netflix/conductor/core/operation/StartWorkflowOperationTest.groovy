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
package com.netflix.conductor.core.operation

import org.junit.Test

import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.core.execution.StartWorkflowInput
import com.netflix.conductor.model.WorkflowModel

import spock.lang.Specification


class StartWorkflowOperationTest extends Specification {
    def "Execute"() {

    }

//    public void testStartWorkflow() {
//        WorkflowDef def = new WorkflowDef();
//        def.setName("test");
//        WorkflowModel workflow = new WorkflowModel();
//        workflow.setWorkflowDefinition(def);
//
//        Map<String, Object> workflowInput = new HashMap<>();
//
//        when(executionLockService.acquireLock(anyString())).thenReturn(true);
//        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
//
//        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
//        startWorkflowInput.setWorkflowDefinition(def);
//        startWorkflowInput.setWorkflowInput(workflowInput);
//
//        workflowExecutor.startWorkflow(startWorkflowInput);
//
//        verify(executionDAOFacade, times(1)).createWorkflow(any(WorkflowModel.class));
//    }
}
