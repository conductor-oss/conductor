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
package com.netflix.conductor.test.integration.http.functional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

/**
 * Functional tests that exercise the full Conductor stack with real Redis and Elasticsearch.
 *
 * <p>These mirror the e2e module tests but run inside the test-harness with embedded Spring Boot
 * and TestContainers, eliminating the need for Docker Compose.
 */
public class WorkflowFunctionalTest extends FunctionalTestBase {

    @Test
    public void testSimpleWorkflowExecution() {
        // Register task definitions
        List<TaskDef> taskDefs =
                Arrays.asList(createTaskDef("func_simple_t1"), createTaskDef("func_simple_t2"));
        metadataClient.registerTaskDefs(taskDefs);

        // Create and register workflow: t1 -> t2
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("func_test_simple_workflow");
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail(OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(120);
        workflowDef.getTasks().add(createSimpleWorkflowTask("func_simple_t1", "t1_ref"));
        workflowDef.getTasks().add(createSimpleWorkflowTask("func_simple_t2", "t2_ref"));
        metadataClient.registerWorkflowDef(workflowDef);

        // Start workflow
        StartWorkflowRequest request =
                new StartWorkflowRequest().withName("func_test_simple_workflow");
        String workflowId = workflowClient.startWorkflow(request);
        assertNotNull(workflowId);

        // Verify workflow is running with first task scheduled
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // Poll and complete task 1
        pollAndCompleteTask("func_simple_t1", Map.of("output1", "value1"));

        // Wait for task 2 to be scheduled
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(2, wf.getTasks().size());
                            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
                            assertEquals(Task.Status.SCHEDULED, wf.getTasks().get(1).getStatus());
                        });

        // Poll and complete task 2
        pollAndCompleteTask("func_simple_t2", Map.of("output2", "value2"));

        // Verify workflow completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
                            assertEquals(2, wf.getTasks().size());
                        });
    }

    @Test
    public void testSetVariableTask() {
        // Register the simple task that follows SET_VARIABLE
        metadataClient.registerTaskDefs(Arrays.asList(createTaskDef("func_setvar_task")));

        // Create workflow: SET_VARIABLE -> SIMPLE
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("func_test_set_variable");
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail(OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(120);

        WorkflowTask setVarTask = new WorkflowTask();
        setVarTask.setName("set_var_ref");
        setVarTask.setTaskReferenceName("set_var_ref");
        setVarTask.setWorkflowTaskType(TaskType.SET_VARIABLE);
        Map<String, Object> setVarInput = new HashMap<>();
        setVarInput.put("myVar", "hello_functional_test");
        setVarTask.setInputParameters(setVarInput);

        workflowDef.getTasks().add(setVarTask);
        workflowDef.getTasks().add(createSimpleWorkflowTask("func_setvar_task", "simple_ref"));
        metadataClient.registerWorkflowDef(workflowDef);

        // Start workflow
        String workflowId =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest().withName("func_test_set_variable"));
        assertNotNull(workflowId);

        // SET_VARIABLE executes synchronously during decide(),
        // so the simple task should become scheduled immediately
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
                            assertTrue(wf.getTasks().size() >= 2);
                            assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
                        });

        // Verify the variable was set on the workflow
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals("hello_functional_test", workflow.getVariables().get("myVar"));

        // Complete the simple task
        pollAndCompleteTask("func_setvar_task", Map.of());

        // Verify workflow completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
                        });
    }

    @Test
    public void testSubWorkflowExecution() {
        // Register task for the child workflow
        metadataClient.registerTaskDefs(Arrays.asList(createTaskDef("func_child_task")));

        // Create and register child workflow
        WorkflowDef childDef = new WorkflowDef();
        childDef.setName("func_test_child_workflow");
        childDef.setVersion(1);
        childDef.setOwnerEmail(OWNER_EMAIL);
        childDef.setTimeoutSeconds(120);
        childDef.getTasks().add(createSimpleWorkflowTask("func_child_task", "child_task_ref"));
        metadataClient.registerWorkflowDef(childDef);

        // Create parent workflow with SUB_WORKFLOW task
        WorkflowDef parentDef = new WorkflowDef();
        parentDef.setName("func_test_parent_workflow");
        parentDef.setVersion(1);
        parentDef.setOwnerEmail(OWNER_EMAIL);
        parentDef.setTimeoutSeconds(120);

        WorkflowTask subWorkflowTask = new WorkflowTask();
        subWorkflowTask.setName("sub_workflow_ref");
        subWorkflowTask.setTaskReferenceName("sub_workflow_ref");
        subWorkflowTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subParams = new SubWorkflowParams();
        subParams.setName("func_test_child_workflow");
        subParams.setVersion(1);
        subWorkflowTask.setSubWorkflowParam(subParams);

        parentDef.getTasks().add(subWorkflowTask);
        metadataClient.registerWorkflowDef(parentDef);

        // Start parent workflow
        String parentId =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest().withName("func_test_parent_workflow"));
        assertNotNull(parentId);

        // Wait for the sub-workflow to be created (async system task worker handles this)
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow parent = workflowClient.getWorkflow(parentId, true);
                            assertEquals(WorkflowStatus.RUNNING, parent.getStatus());
                            assertFalse(parent.getTasks().isEmpty());
                            assertNotNull(
                                    "Sub-workflow ID should be set",
                                    parent.getTasks().get(0).getSubWorkflowId());
                        });

        // Get child workflow ID
        Workflow parent = workflowClient.getWorkflow(parentId, true);
        String childId = parent.getTasks().get(0).getSubWorkflowId();

        // Verify child is running
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow child = workflowClient.getWorkflow(childId, true);
                            assertEquals(WorkflowStatus.RUNNING, child.getStatus());
                            assertFalse(child.getTasks().isEmpty());
                        });

        // Poll and complete the child's task
        pollAndCompleteTask("func_child_task", Map.of("childOutput", "done"));

        // Verify child workflow completed
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow child = workflowClient.getWorkflow(childId, true);
                            assertEquals(WorkflowStatus.COMPLETED, child.getStatus());
                        });

        // Verify parent workflow completed (system task worker detects child completion)
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow p = workflowClient.getWorkflow(parentId, true);
                            assertEquals(WorkflowStatus.COMPLETED, p.getStatus());
                        });
    }

    @Test
    public void testSwitchTask() {
        // Register tasks for the two branches
        metadataClient.registerTaskDefs(
                Arrays.asList(
                        createTaskDef("func_switch_task_a"), createTaskDef("func_switch_task_b")));

        // Create workflow with SWITCH -> branch A or B
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("func_test_switch_workflow");
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail(OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(120);

        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setName("switch_ref");
        switchTask.setTaskReferenceName("switch_ref");
        switchTask.setWorkflowTaskType(TaskType.SWITCH);
        switchTask.setEvaluatorType("value-param");
        switchTask.setExpression("switchCaseValue");
        Map<String, Object> switchInput = new HashMap<>();
        switchInput.put("switchCaseValue", "${workflow.input.case}");
        switchTask.setInputParameters(switchInput);

        WorkflowTask taskA = createSimpleWorkflowTask("func_switch_task_a", "task_a_ref");
        WorkflowTask taskB = createSimpleWorkflowTask("func_switch_task_b", "task_b_ref");

        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("A", Arrays.asList(taskA));
        decisionCases.put("B", Arrays.asList(taskB));
        switchTask.setDecisionCases(decisionCases);

        workflowDef.getTasks().add(switchTask);
        metadataClient.registerWorkflowDef(workflowDef);

        // Start workflow with case=A
        Map<String, Object> input = new HashMap<>();
        input.put("case", "A");
        String workflowId =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest()
                                .withName("func_test_switch_workflow")
                                .withInput(input));
        assertNotNull(workflowId);

        // Verify SWITCH routed to branch A
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertTrue(wf.getTasks().size() >= 2);
                            assertEquals("task_a_ref", wf.getTasks().get(1).getReferenceTaskName());
                        });

        // Complete task A
        pollAndCompleteTask("func_switch_task_a", Map.of());

        // Verify workflow completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
                        });
    }

    @Test
    public void testWorkflowTerminateAndRestart() {
        // Register task definitions
        metadataClient.registerTaskDefs(Arrays.asList(createTaskDef("func_restart_t1")));

        // Create workflow
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("func_test_restart_workflow");
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail(OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(120);
        workflowDef.getTasks().add(createSimpleWorkflowTask("func_restart_t1", "t1_ref"));
        metadataClient.registerWorkflowDef(workflowDef);

        // Start workflow
        String workflowId =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest().withName("func_test_restart_workflow"));
        assertNotNull(workflowId);

        // Verify running
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());

        // Terminate
        workflowClient.terminateWorkflow(workflowId, "functional test termination");
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.TERMINATED, workflow.getStatus());

        // Restart
        workflowClient.restart(workflowId, false);
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        // Complete the task after restart
        pollAndCompleteTask("func_restart_t1", Map.of());

        // Verify completed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
                        });
    }
}
