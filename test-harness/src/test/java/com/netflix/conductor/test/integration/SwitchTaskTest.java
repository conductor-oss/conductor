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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.test.base.AbstractSpecification;

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask;

import static org.junit.jupiter.api.Assertions.*;

class SwitchTaskTest extends AbstractSpecification {

    @Autowired Join joinTask;

    private static final String SWITCH_WF = "SwitchWorkflow";
    private static final String FORK_JOIN_SWITCH_WF = "ForkConditionalTest";
    private static final String COND_TASK_WF = "ConditionalTaskWF";
    private static final String SWITCH_NODEFAULT_WF = "SwitchWithNoDefaultCaseWF";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_switch_task_integration_test.json",
                "switch_and_fork_join_integration_test.json",
                "conditional_switch_task_workflow_integration_test.json",
                "switch_with_no_default_case_integration_test.json");
    }

    @Test
    @DisplayName("Test simple switch workflow")
    void testSimpleSwitchWorkflow() throws Exception {
        // given: workflow input with switch task
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1");
        input.put("param2", "p2");
        input.put("case", "c");

        // when: a switch workflow is started
        String workflowInstanceId = startWorkflow(SWITCH_WF, 1, "switch_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: the task 'integration_task_1' is polled and completed
        Task polledAndCompletedTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker");

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1);

        // and: verify that 'integration_task_1' is COMPLETED and workflow has progressed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());

        // when: the task 'integration_task_2' is polled and completed
        Task polledAndCompletedTask2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task1.integration.worker");

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2Try1);

        // and: verify that 'integration_task_2' is COMPLETED and workflow has progressed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("integration_task_2", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_20", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());

        // when: the task 'integration_task_20' is polled and completed
        Task polledAndCompletedTask20Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_20", "task1.integration.worker");

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask20Try1);

        // and: verify that 'integration_task_20' is COMPLETED and the workflow is done
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("integration_task_20", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
    }

    @Test
    @DisplayName("Test a workflow that has a switch task that leads to a fork join")
    void testSwitchTaskLeadingToForkJoin() throws Exception {
        // given: workflow input with switch task
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1");
        input.put("param2", "p2");
        input.put("case", "c");

        // when: a switch workflow is started
        String workflowInstanceId =
                startWorkflow(FORK_JOIN_SWITCH_WF, 1, "switch_forkjoin", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());
        assertEquals("FORK", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("integration_task_1", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());

        // when: tasks 'integration_task_1' and 'integration_task_10' are polled and completed
        String joinTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTaskByRefName("joinTask")
                        .getTaskId();
        Task polledAndCompletedTask1Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "task1.integration.worker");
        Task polledAndCompletedTask10Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_10", "task1.integration.worker");

        // then: verify that the tasks are completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask1Try1);
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask10Try1);

        // and: verify that 'integration_task_1' and 'integration_task_10' are COMPLETED
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("integration_task_10", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(
                List.of("t20", "t10"), workflow.getTasks().get(4).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(5).getStatus());

        // when: the task 'integration_task_2' is polled and completed
        Task polledAndCompletedTask2Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task1.integration.worker");

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask2Try1);

        // and: verify that 'integration_task_2' is COMPLETED and workflow has progressed
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(
                List.of("t20", "t10"), workflow.getTasks().get(4).getInputData().get("joinOn"));
        assertEquals(Task.Status.IN_PROGRESS, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(5).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(5).getStatus());
        assertEquals("integration_task_20", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(6).getStatus());

        // when: the task 'integration_task_20' is polled and completed
        Task polledAndCompletedTask20Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_20", "task1.integration.worker");

        // and: the workflow is evaluated
        sweep(workflowInstanceId);

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask20Try1);

        // when: JOIN task is polled and executed
        asyncSystemTaskExecutor.execute(joinTask, joinTaskId);

        // then: verify that JOIN is COMPLETED and the workflow is done
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
        assertEquals("JOIN", workflow.getTasks().get(4).getTaskType());
        assertEquals(
                List.of("t20", "t10"), workflow.getTasks().get(4).getInputData().get("joinOn"));
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(4).getStatus());
        assertEquals("integration_task_20", workflow.getTasks().get(6).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(6).getStatus());
    }

    @Test
    @DisplayName("Test default case condition execution of a conditional workflow")
    void testDefaultCaseConditionExecutionOfConditionalWorkflow() throws Exception {
        // given: input for a workflow to ensure that the default case is executed
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "xxx");
        input.put("param2", "two");

        // when: a conditional workflow is started
        String workflowInstanceId =
                startWorkflow(COND_TASK_WF, 1, "conditional_default", input, null);

        // then: verify that the workflow is running and the default condition case was executed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(
                List.of("xxx"), workflow.getTasks().get(0).getOutputData().get("evaluationResult"));
        assertEquals("integration_task_10", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: the task 'integration_task_10' is polled and completed
        Task polledAndCompletedTask10Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_10", "task1.integration.worker");

        // then: verify that the tasks are completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask10Try1);

        // and: verify that the workflow is in a completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("integration_task_10", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(
                List.of("null"),
                workflow.getTasks().get(2).getOutputData().get("evaluationResult"));
    }

    @ParameterizedTest(
            name = "Test case 'nested' and '{0}' condition execution of a conditional workflow")
    @MethodSource("provideNestedCaseParams")
    @DisplayName("Test case 'nested' and caseValue condition execution of a conditional workflow")
    void testNestedAndCaseValueConditionExecutionOfConditionalWorkflow(
            String caseValue,
            String expectedTaskName,
            String workflowCorrelationId,
            Task.Status endTaskStatus)
            throws Exception {
        // given: input for a workflow to ensure that 'nested' and caseValue switch tree is executed
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "nested");
        input.put("param2", caseValue);

        // when: a conditional workflow is started
        String workflowInstanceId =
                startWorkflow(COND_TASK_WF, 1, workflowCorrelationId, input, null);

        // then: verify workflow is running and 'nested' and caseValue condition case was executed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(
                List.of("nested"),
                workflow.getTasks().get(0).getOutputData().get("evaluationResult"));
        assertEquals("SWITCH", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals(
                List.of(caseValue),
                workflow.getTasks().get(1).getOutputData().get("evaluationResult"));
        assertEquals(expectedTaskName, workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(2).getStatus());

        // when: the expectedTaskName task is polled and completed
        Task polledAndCompletedTaskTry1 =
                workflowTestUtil.pollAndCompleteTask(expectedTaskName, "task.integration.worker");

        // then: verify that the tasks are completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTaskTry1);

        // and: verify the final workflow state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(expectedTaskName, workflow.getTasks().get(2).getTaskType());
        assertEquals(endTaskStatus, workflow.getTasks().get(2).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals(
                List.of("null"),
                workflow.getTasks().get(3).getOutputData().get("evaluationResult"));
    }

    static Stream<Arguments> provideNestedCaseParams() {
        return Stream.of(
                Arguments.of(
                        "two",
                        "integration_task_2",
                        "conditional_nested_two",
                        Task.Status.COMPLETED),
                Arguments.of(
                        "one",
                        "integration_task_1",
                        "conditional_nested_one",
                        Task.Status.COMPLETED));
    }

    @Test
    @DisplayName("Test 'three' case condition execution of a conditional workflow")
    void testThreeCaseConditionExecutionOfConditionalWorkflow() throws Exception {
        // given: input for a workflow to ensure the 'three' case is executed
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "three");
        input.put("param2", "two");
        input.put("finalCase", "notify");

        // when: a conditional workflow is started
        String workflowInstanceId =
                startWorkflow(COND_TASK_WF, 1, "conditional_three", input, null);

        // then: verify that the workflow is running and the 'three' condition case was executed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals(
                List.of("three"),
                workflow.getTasks().get(0).getOutputData().get("evaluationResult"));
        assertEquals("integration_task_3", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: the task 'integration_task_3' is polled and completed
        Task polledAndCompletedTask3Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_3", "task1.integration.worker");

        // then: verify that the tasks are completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask3Try1);

        // and: verify that the workflow is in a running state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("integration_task_3", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(
                List.of("notify"),
                workflow.getTasks().get(2).getOutputData().get("evaluationResult"));
        assertEquals("integration_task_4", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(3).getStatus());

        // when: the task 'integration_task_4' is polled and completed
        Task polledAndCompletedTask4Try1 =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_4", "task1.integration.worker");

        // then: verify that the tasks are completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTask4Try1);

        // and: verify that the workflow is in a completed state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("integration_task_3", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("SWITCH", workflow.getTasks().get(2).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals(
                List.of("notify"),
                workflow.getTasks().get(2).getOutputData().get("evaluationResult"));
        assertEquals("integration_task_4", workflow.getTasks().get(3).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
    }

    @Test
    @DisplayName("Test switch with no default case workflow")
    void testSwitchWithNoDefaultCaseWorkflow() throws Exception {
        // given: workflow input
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1");
        input.put("param2", "p2");

        // when: a switch workflow is started with no default case
        String workflowInstanceId =
                startWorkflow(SWITCH_NODEFAULT_WF, 1, "switch_no_default_workflow", input, null);

        // then: verify that the workflow is in running state
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(1).getStatus());

        // when: the task 'integration_task_2' is polled and completed
        Task polledAndCompletedTaskTry =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_2", "task1.integration.worker");

        // then: verify that the task is completed and acknowledged
        verifyPolledAndAcknowledgedTask(polledAndCompletedTaskTry);

        // and: verify that 'integration_task_2' is COMPLETED and the workflow is in COMPLETED state
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("SWITCH", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("integration_task_2", workflow.getTasks().get(1).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
    }
}
