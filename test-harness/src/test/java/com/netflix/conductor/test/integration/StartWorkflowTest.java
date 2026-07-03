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
import java.util.UUID;
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
import com.netflix.conductor.core.execution.tasks.StartWorkflow;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.test.base.AbstractSpecification;
import com.netflix.conductor.test.utils.MockExternalPayloadStorage;

import static org.junit.jupiter.api.Assertions.*;

class StartWorkflowTest extends AbstractSpecification {

    @Autowired QueueDAO queueDAO;

    @Autowired StartWorkflow startWorkflowTask;

    @Autowired MockExternalPayloadStorage mockExternalPayloadStorage;

    private static final String WORKFLOW_THAT_STARTS_ANOTHER_WORKFLOW =
            "workflow_that_starts_another_workflow";

    private static final String workflowInputPath = UUID.randomUUID() + ".json";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "workflow_that_starts_another_workflow.json",
                "simple_workflow_1_integration_test.json");
        mockExternalPayloadStorage.upload(
                workflowInputPath,
                StartWorkflowTest.class.getResourceAsStream("/start_workflow_input.json"),
                0);
    }

    // ---------------------------------------------------------------------------
    // Parameterized: "start another workflow using #testCase.name"
    // ---------------------------------------------------------------------------

    static Stream<Arguments> provideStartAnotherWorkflowTestCases() {
        return Stream.of(
                Arguments.of(workflowName()),
                Arguments.of(workflowDef()),
                Arguments.of(workflowRequestWithExternalPayloadStorage()));
    }

    @ParameterizedTest(name = "start another workflow using {0}")
    @MethodSource("provideStartAnotherWorkflowTestCases")
    @DisplayName("start another workflow using testCase.name")
    void startAnotherWorkflow(TestCase testCase) {
        // setup: create the correlationId for the starter workflow
        String correlationId = UUID.randomUUID().toString();

        // when: starter workflow is started
        String workflowInstanceId =
                startWorkflow(
                        WORKFLOW_THAT_STARTS_ANOTHER_WORKFLOW,
                        1,
                        correlationId,
                        testCase.workflowInput,
                        testCase.workflowInputPath);

        // then: verify that the starter workflow is in RUNNING state
        Workflow starterStatus =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, starterStatus.getStatus());
        assertEquals(1, starterStatus.getTasks().size());
        assertEquals("START_WORKFLOW", starterStatus.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, starterStatus.getTasks().get(0).getStatus());

        // when: the START_WORKFLOW task is started
        List<String> polledTaskIds = queueDAO.pop("START_WORKFLOW", 1, 200);
        String startWorkflowTaskId = polledTaskIds.get(0);
        asyncSystemTaskExecutor.execute(startWorkflowTask, startWorkflowTaskId);

        // then: verify the START_WORKFLOW task and workflow are COMPLETED
        Workflow completedStatus =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, completedStatus.getStatus());
        assertEquals(1, completedStatus.getTasks().size());
        assertEquals("START_WORKFLOW", completedStatus.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, completedStatus.getTasks().get(0).getStatus());

        // when: the started workflow is retrieved
        Task startedTask = workflowExecutionService.getTask(startWorkflowTaskId);
        String startedWorkflowId = (String) startedTask.getOutputData().get("workflowId");

        // then: verify that the started workflow is RUNNING
        Workflow startedWorkflow =
                workflowExecutionService.getExecutionStatus(startedWorkflowId, false);
        assertEquals(Workflow.WorkflowStatus.RUNNING, startedWorkflow.getStatus());
        assertEquals(correlationId, startedWorkflow.getCorrelationId());
        // when the "starter" workflow is started with input from external payload storage,
        // it sends a large input to the "started" workflow — see start_workflow_input.json
        if (testCase.workflowInputPath != null) {
            assertNotNull(startedWorkflow.getExternalInputPayloadStoragePath());
        } else {
            assertNotNull(startedWorkflow.getInput());
        }
    }

    // ---------------------------------------------------------------------------
    // "start_workflow does not conform to StartWorkflowRequest"
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("start_workflow does not conform to StartWorkflowRequest")
    void startWorkflowDoesNotConformToStartWorkflowRequest() {
        // given: start_workflow that does not conform to StartWorkflowRequest
        Map<String, Object> startWorkflowParam = new HashMap<>();
        startWorkflowParam.put("param1", "value1");
        startWorkflowParam.put("param2", "value2");
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("start_workflow", startWorkflowParam);

        // when: starter workflow is started
        String workflowInstanceId =
                startWorkflow(WORKFLOW_THAT_STARTS_ANOTHER_WORKFLOW, 1, null, workflowInput, null);

        // then: verify that the starter workflow is in RUNNING state
        Workflow runningStatus =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, runningStatus.getStatus());
        assertEquals(1, runningStatus.getTasks().size());
        assertEquals("START_WORKFLOW", runningStatus.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, runningStatus.getTasks().get(0).getStatus());

        // when: the START_WORKFLOW task is started
        List<String> polledTaskIds = queueDAO.pop("START_WORKFLOW", 1, 200);
        String startWorkflowTaskId = polledTaskIds.get(0);
        asyncSystemTaskExecutor.execute(startWorkflowTask, startWorkflowTaskId);

        // then: verify the START_WORKFLOW task and workflow FAILED
        Workflow failedStatus =
                workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, failedStatus.getStatus());
        assertEquals(1, failedStatus.getTasks().size());
        assertEquals("START_WORKFLOW", failedStatus.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.FAILED, failedStatus.getTasks().get(0).getStatus());
        assertNotNull(failedStatus.getTasks().get(0).getReasonForIncompletion());
    }

    // ---------------------------------------------------------------------------
    // TestCase helper class and factory methods
    // ---------------------------------------------------------------------------

    /** Builds a TestCase for a StartWorkflowRequest with a WorkflowDef that contains two tasks. */
    static TestCase workflowDef() {
        Map<String, Object> task1 = new HashMap<>();
        task1.put("name", "integration_task_1");
        task1.put("taskReferenceName", "t1");
        task1.put("type", "SIMPLE");
        Map<String, Object> task1Input = new HashMap<>();
        task1Input.put("tp1", "${workflow.input.param1}");
        task1Input.put("tp2", "${workflow.input.param2}");
        task1Input.put("tp3", "${CPEWF_TASK_ID}");
        task1.put("inputParameters", task1Input);

        Map<String, Object> task2 = new HashMap<>();
        task2.put("name", "integration_task_2");
        task2.put("taskReferenceName", "t2");
        task2.put("type", "SIMPLE");
        Map<String, Object> task2Input = new HashMap<>();
        task2Input.put("tp1", "${workflow.input.param1}");
        task2Input.put("tp2", "${t1.output.op}");
        task2Input.put("tp3", "${CPEWF_TASK_ID}");
        task2.put("inputParameters", task2Input);

        Map<String, Object> wfDef = new HashMap<>();
        wfDef.put("name", "dynamic_wf");
        wfDef.put("version", 1);
        wfDef.put("tasks", List.of(task1, task2));
        wfDef.put("ownerEmail", "abc@abc.com");

        Map<String, Object> startWorkflow = new HashMap<>();
        startWorkflow.put("name", "dynamic_wf");
        startWorkflow.put("workflowDef", wfDef);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("startWorkflow", startWorkflow);

        return new TestCase("workflow definition", workflowInput, null);
    }

    /** Builds a TestCase for a StartWorkflowRequest with a workflow name. */
    static TestCase workflowName() {
        Map<String, Object> startWorkflow = new HashMap<>();
        startWorkflow.put("name", "integration_test_wf");
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "value1");
        input.put("param2", "value2");
        startWorkflow.put("input", input);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("startWorkflow", startWorkflow);

        return new TestCase("name and version", workflowInput, null);
    }

    /**
     * Builds a TestCase for a StartWorkflowRequest with a workflow name and input in external
     * payload storage.
     */
    static TestCase workflowRequestWithExternalPayloadStorage() {
        return new TestCase("name and version with external input", null, workflowInputPath);
    }

    static class TestCase {
        final String name;
        final Map<String, Object> workflowInput;
        final String workflowInputPath;

        TestCase(String name, Map<String, Object> workflowInput, String workflowInputPath) {
            this.name = name;
            this.workflowInput = workflowInput;
            this.workflowInputPath = workflowInputPath;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
