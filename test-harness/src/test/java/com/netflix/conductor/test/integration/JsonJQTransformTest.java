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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.test.base.AbstractSpecification;

import static org.junit.jupiter.api.Assertions.*;

class JsonJQTransformTest extends AbstractSpecification {

    private static final String JSON_JQ_TRANSFORM_WF = "test_json_jq_transform_wf";
    private static final String SEQUENTIAL_JSON_JQ_TRANSFORM_WF = "sequential_json_jq_transform_wf";
    private static final String JSON_JQ_TRANSFORM_RESULT_WF = "json_jq_transform_result_wf";

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows(
                "simple_json_jq_transform_integration_test.json",
                "sequential_json_jq_transform_integration_test.json",
                "json_jq_transform_result_integration_test.json");
    }

    /**
     * Given the following input JSON
     *
     * <pre>
     * {
     *   "in1": { "array": [ "a", "b" ] },
     *   "in2": { "array": [ "c", "d" ] }
     * }
     * </pre>
     *
     * expect the workflow task to transform to following result:
     *
     * <pre>
     * { out: [ "a", "b", "c", "d" ] }
     * </pre>
     */
    @Test
    @DisplayName("Test workflow with json jq transform task succeeds")
    void testWorkflowWithJsonJqTransformTaskSucceeds() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        Map<String, Object> in1 = new HashMap<>();
        in1.put("array", List.of("a", "b"));
        workflowInput.put("in1", in1);
        Map<String, Object> in2 = new HashMap<>();
        in2.put("array", List.of("c", "d"));
        workflowInput.put("in2", in2);

        // when: workflow which has the json jq transform task has started
        String workflowInstanceId = startWorkflow(JSON_JQ_TRANSFORM_WF, 1, "", workflowInput, null);

        // then: verify that the workflow and task are completed with expected output
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(0).getTaskType());
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("result"));
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("resultList"));
    }

    /**
     * Given the following input JSON
     *
     * <pre>
     * { "in1": "a", "in2": "b" }
     * </pre>
     *
     * using the same query from the success test, jq will try to get in1['array'] and fail since
     * 'in1' is a string.
     */
    @Test
    @DisplayName("Test workflow with json jq transform task fails")
    void testWorkflowWithJsonJqTransformTaskFails() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("in1", "a");
        workflowInput.put("in2", "b");

        // when: workflow which has the json jq transform task has started
        String workflowInstanceId = startWorkflow(JSON_JQ_TRANSFORM_WF, 1, "", workflowInput, null);

        // then: verify that the workflow and task failed with expected error
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(0).getTaskType());
        assertEquals(
                "Cannot index string with string \"array\"",
                workflow.getTasks().get(0).getReasonForIncompletion());
    }

    /**
     * Given an invalid input that causes failure, re-run the failed system task with valid input
     * and verify the workflow completes successfully.
     */
    @Test
    @DisplayName("Test rerun workflow with failed json jq transform task")
    void testRerunWorkflowWithFailedJsonJqTransformTask() {
        // given: invalid workflow input
        Map<String, Object> invalidInput = new HashMap<>();
        invalidInput.put("in1", "a");
        invalidInput.put("in2", "b");

        Map<String, Object> validInput = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> inputIn1 = new HashMap<>();
        inputIn1.put("array", List.of("a", "b"));
        input.put("in1", inputIn1);
        Map<String, Object> inputIn2 = new HashMap<>();
        inputIn2.put("array", List.of("c", "d"));
        input.put("in2", inputIn2);
        validInput.put("input", input);
        validInput.put("queryExpression", ".input as $_ | { out: ($_.in1.array + $_.in2.array) }");

        // when: workflow which has the json jq transform task started
        String workflowInstanceId = startWorkflow(JSON_JQ_TRANSFORM_WF, 1, "", invalidInput, null);

        // then: verify that the workflow and task failed with expected error
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(0).getTaskType());
        assertEquals(
                "Cannot index string with string \"array\"",
                workflow.getTasks().get(0).getReasonForIncompletion());

        // when: workflow which has the json jq transform task reran
        RerunWorkflowRequest reRunWorkflowRequest = new RerunWorkflowRequest();
        reRunWorkflowRequest.setReRunFromWorkflowId(workflowInstanceId);
        String reRunTaskId =
                workflowExecutionService
                        .getExecutionStatus(workflowInstanceId, true)
                        .getTasks()
                        .get(0)
                        .getTaskId();
        reRunWorkflowRequest.setReRunFromTaskId(reRunTaskId);
        reRunWorkflowRequest.setTaskInput(validInput);

        workflowExecutor.rerun(reRunWorkflowRequest);

        // then: verify that the workflow and task are completed with expected output
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(0).getTaskType());
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("result"));
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("resultList"));
    }

    @Test
    @DisplayName("Test json jq transform task with nested json object succeeds")
    void testJsonJqTransformTaskWithNestedJsonObjectSucceeds() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("method", "POST");
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Beary Beariston");
        body.put("title", "the Brown Bear");
        workflowInput.put("body", body);
        workflowInput.put(
                "requestTransform", "{name: (.body.name + \" you are \" + .body.title) }");
        workflowInput.put("responseTransform", "{result: \"reply: \" + .response.body.message}");

        // when: workflow which has the json jq transform task has started
        String workflowInstanceId =
                startWorkflow(SEQUENTIAL_JSON_JQ_TRANSFORM_WF, 1, "", workflowInput, null);

        // then: verify that the workflow and tasks are completed with expected output
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(0).getTaskType());
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("result"));
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("resultList"));

        @SuppressWarnings("unchecked")
        HashMap<String, Object> result1 =
                (HashMap<String, Object>) workflow.getTasks().get(0).getOutputData().get("result");
        assertEquals(workflowInput.get("method"), result1.get("method"));
        assertEquals(workflowInput.get("requestTransform"), result1.get("requestTransform"));
        assertEquals(workflowInput.get("responseTransform"), result1.get("responseTransform"));

        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(1).getTaskType());
        assertTrue(workflow.getTasks().get(1).getOutputData().containsKey("result"));
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("resultList"));

        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 =
                (HashMap<String, Object>) workflow.getTasks().get(1).getOutputData().get("result");
        assertEquals("Beary Beariston you are the Brown Bear", result2.get("name"));
    }

    @Test
    @DisplayName("Test json jq transform task with different json object results succeeds")
    void testJsonJqTransformTaskWithDifferentJsonObjectResultsSucceeds() {
        // given: workflow input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("requestedAction", "redeliver");

        // when: workflow which has the json jq transform task has started
        String workflowInstanceId =
                startWorkflow(JSON_JQ_TRANSFORM_RESULT_WF, 1, "", workflowInput, null);

        // then: verify that the workflow and tasks are completed with expected output
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());

        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(0).getTaskType());
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("result"));
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("resultList"));
        assertEquals("CREATE", workflow.getTasks().get(0).getOutputData().get("result"));

        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(1).getStatus());
        assertEquals("DECISION", workflow.getTasks().get(1).getTaskType());
        assertEquals("CREATE", workflow.getTasks().get(1).getInputData().get("case"));

        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(2).getStatus());
        assertEquals("JSON_JQ_TRANSFORM", workflow.getTasks().get(2).getTaskType());
        assertTrue(workflow.getTasks().get(2).getOutputData().containsKey("result"));
        assertTrue(workflow.getTasks().get(0).getOutputData().containsKey("resultList"));

        @SuppressWarnings("unchecked")
        List<String> result =
                (List<String>) workflow.getTasks().get(2).getOutputData().get("result");
        assertEquals(3, result.size());
        assertTrue(result.indexOf("redeliver") >= 0);

        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(3).getStatus());
        assertEquals("DECISION", workflow.getTasks().get(3).getTaskType());
        assertEquals("true", workflow.getTasks().get(3).getInputData().get("case"));
    }
}
