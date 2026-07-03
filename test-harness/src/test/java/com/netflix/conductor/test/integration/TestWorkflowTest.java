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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowTestRequest;
import com.netflix.conductor.service.WorkflowTestService;
import com.netflix.conductor.test.base.AbstractSpecification;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkflowTest extends AbstractSpecification {

    @Autowired WorkflowTestService workflowTestService;

    @Test
    @DisplayName("Run Workflow Test with simple tasks")
    void runWorkflowTestWithSimpleTasks() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("var", "var_test_value");

        WorkflowTestRequest request = new WorkflowTestRequest();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_workflow");
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("owner@example.com");

        WorkflowTask task1 = new WorkflowTask();
        task1.setType(TaskType.TASK_TYPE_SIMPLE);
        task1.setName("task1");
        task1.setTaskReferenceName("task1");

        WorkflowTask task2 = new WorkflowTask();
        task2.setType(TaskType.TASK_TYPE_SIMPLE);
        task2.setName("task2");
        task2.setTaskReferenceName("task2");

        workflowDef.getTasks().add(task1);
        workflowDef.getTasks().add(task2);

        request.setName(workflowDef.getName());
        request.setVersion(workflowDef.getVersion());

        List<WorkflowTestRequest.TaskMock> task1Executions = new LinkedList<>();
        task1Executions.add(
                new WorkflowTestRequest.TaskMock(
                        TaskResult.Status.COMPLETED, Map.of("key", "value")));

        request.getTaskRefToMockOutput().put("task1", task1Executions);

        request.setWorkflowDef(workflowDef);

        // when: Start the workflow which has the set variable task
        Workflow workflow = workflowTestService.testWorkflow(request);

        // then: verify that the simple task is scheduled
        Workflow executionStatus =
                workflowExecutionService.getExecutionStatus(workflow.getWorkflowId(), true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, executionStatus.getStatus());
        assertEquals(2, executionStatus.getTasks().size());
        assertEquals("task1", executionStatus.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, executionStatus.getTasks().get(0).getStatus());
        assertEquals("value", executionStatus.getTasks().get(0).getOutputData().get("key"));
        assertEquals("task2", executionStatus.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, executionStatus.getTasks().get(1).getStatus());
    }

    @Test
    @DisplayName("Run Workflow Test with decision task")
    void runWorkflowTestWithDecisionTask() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("var", "var_test_value");

        WorkflowTestRequest request = new WorkflowTestRequest();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_workflow");
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("owner@example.com");

        WorkflowTask task1 = new WorkflowTask();
        task1.setType(TaskType.TASK_TYPE_SIMPLE);
        task1.setName("task1");
        task1.setTaskReferenceName("task1");

        WorkflowTask decision = new WorkflowTask();
        decision.setType(TaskType.TASK_TYPE_SWITCH);
        decision.setName("switch");
        decision.setTaskReferenceName("switch");
        decision.setEvaluatorType("value-param");
        decision.setExpression("switchCaseValue");
        decision.getInputParameters().put("switchCaseValue", "${workflow.input.case}");

        WorkflowTask d1 = new WorkflowTask();
        d1.setType(TaskType.TASK_TYPE_SIMPLE);
        d1.setName("task1");
        d1.setTaskReferenceName("d1");

        WorkflowTask d2 = new WorkflowTask();
        d2.setType(TaskType.TASK_TYPE_SIMPLE);
        d2.setName("task2");
        d2.setTaskReferenceName("d2");

        decision.getDecisionCases().put("a", Arrays.asList(d1));
        decision.getDecisionCases().put("b", Arrays.asList(d2));

        workflowDef.getTasks().add(task1);
        workflowDef.getTasks().add(decision);

        request.setName(workflowDef.getName());
        request.setVersion(workflowDef.getVersion());

        List<WorkflowTestRequest.TaskMock> task1Executions = new LinkedList<>();
        task1Executions.add(
                new WorkflowTestRequest.TaskMock(
                        TaskResult.Status.COMPLETED, Map.of("key", "value")));

        request.getTaskRefToMockOutput().put("task1", task1Executions);

        request.setWorkflowDef(workflowDef);
        request.setInput(Map.of("case", "b"));

        // when: Start the workflow which has the set variable task
        Workflow workflow = workflowTestService.testWorkflow(request);

        // then: verify that the simple task is scheduled
        Workflow executionStatus =
                workflowExecutionService.getExecutionStatus(workflow.getWorkflowId(), true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, executionStatus.getStatus());
        assertEquals(3, executionStatus.getTasks().size());
        assertEquals("task1", executionStatus.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, executionStatus.getTasks().get(0).getStatus());
        assertEquals("value", executionStatus.getTasks().get(0).getOutputData().get("key"));
        assertEquals("SWITCH", executionStatus.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, executionStatus.getTasks().get(1).getStatus());
        assertEquals("task2", executionStatus.getTasks().get(2).getTaskType());
        assertEquals("d2", executionStatus.getTasks().get(2).getReferenceTaskName());
        assertEquals(Task.Status.SCHEDULED, executionStatus.getTasks().get(2).getStatus());
    }
}
