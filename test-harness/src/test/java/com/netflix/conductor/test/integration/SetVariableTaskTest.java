/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.test.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static org.junit.jupiter.api.Assertions.*;

class SetVariableTaskTest extends AbstractSpecification {

    private static final String SET_VARIABLE_WF = "test_set_variable_wf";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows("simple_set_variable_workflow_integration_test.json");
    }

    @Test
    @DisplayName("Test workflow with set variable task")
    void testWorkflowWithSetVariableTask() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("var", "var_test_value");

        // when: Start the workflow which has the set variable task
        String workflowInstanceId = startWorkflow(SET_VARIABLE_WF, 1, "", workflowInput, null);

        // then: verify that the task is completed and variables were set
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("SET_VARIABLE", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("[var:var_test_value]", workflow.getVariables().toString());
        assertEquals("[variables:[var:var_test_value]]", workflow.getOutput().toString());
    }

    @Test
    @DisplayName("Test workflow with set variable task passing variables payload size threshold")
    void testWorkflowWithSetVariableTaskPassingVariablesPayloadSizeThreshold() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        long maxThreshold = 2;
        workflowInput.put(
                "var",
                String.join(
                        "",
                        Collections.nCopies(1 + ((int) (maxThreshold * 1024 / 8)), "01234567")));

        // when: Start the workflow which has the set variable task
        String workflowInstanceId = startWorkflow(SET_VARIABLE_WF, 1, "", workflowInput, null);
        int EXTRA_HASHMAP_SIZE = 17;
        String expectedErrorMessage =
                String.format(
                        "The variables payload size: %d of workflow: %s is greater than the permissible limit: %d kilobytes",
                        EXTRA_HASHMAP_SIZE + maxThreshold * 1024 + 1,
                        workflowInstanceId,
                        maxThreshold);

        // then: verify that the task is failed and variables were not set
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("SET_VARIABLE", workflow.getTasks().get(0).getTaskType());
        assertEquals(
                Task.Status.FAILED_WITH_TERMINAL_ERROR, workflow.getTasks().get(0).getStatus());
        assertEquals(expectedErrorMessage, workflow.getTasks().get(0).getReasonForIncompletion());
        assertEquals("[:]", workflow.getVariables().toString());
        assertEquals("[variables:[:]]", workflow.getOutput().toString());
    }
}
