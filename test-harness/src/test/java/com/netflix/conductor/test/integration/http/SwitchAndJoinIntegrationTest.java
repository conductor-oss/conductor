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
package com.netflix.conductor.test.integration.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

// Enable system task workers to allow Join tasks to be processed asynchronously
@TestPropertySource(properties = "conductor.system-task-workers.enabled=true")
public class SwitchAndJoinIntegrationTest extends TestHarnessAbstractHttpEndToEndTest {

    @Before
    public void init() {
        apiRoot = String.format("http://localhost:%d/api/", port);

        taskClient = new TaskClient();
        taskClient.setRootURI(apiRoot);

        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);

        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);

        eventClient = new EventClient();
        eventClient.setRootURI(apiRoot);
    }

    /**
     * Tests nested Switch task execution.
     *
     * <p>Workflow Diagram:
     *
     * <pre>
     * [Start]
     *    |
     *    v
     * [OuterSwitch] --caseB--> [InnerSwitch] --caseA--> [SimpleTask]
     *    |                          |                        |
     *    v                          v                        v
     * (default)                 (default)                 [End]
     * </pre>
     *
     * <p>Verifies: - Nested Switch tasks are correctly scheduled and executed - Inner Switch
     * receives correct case input from workflow - Simple task inside nested Switch is polled and
     * completed
     */
    @Test
    public void testNestedSwitch() {
        String taskName = "simple_task_nested_switch";
        createAndRegisterTaskDefinitions(taskName, 1);

        WorkflowDef def = new WorkflowDef();
        def.setName("nested_switch_workflow");
        def.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);
        def.setVersion(1);

        // Inner Switch
        WorkflowTask innerSwitch = new WorkflowTask();
        innerSwitch.setTaskReferenceName("inner_switch");
        innerSwitch.setWorkflowTaskType(TaskType.SWITCH);
        innerSwitch.setEvaluatorType("value-param");
        innerSwitch.setExpression("switch_case_param");
        innerSwitch.setName("inner_switch_task");
        innerSwitch.getInputParameters().put("switch_case_param", "caseA");

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setName("simple_task_nested_switch0");
        simpleTask.setTaskReferenceName("simple_node");
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        Map<String, List<WorkflowTask>> innerDecisionCases = new HashMap<>();
        innerDecisionCases.put("caseA", Collections.singletonList(simpleTask));
        innerSwitch.setDecisionCases(innerDecisionCases);

        // Outer Switch
        WorkflowTask outerSwitch = new WorkflowTask();
        outerSwitch.setTaskReferenceName("outer_switch");
        outerSwitch.setWorkflowTaskType(TaskType.SWITCH);
        outerSwitch.setEvaluatorType("value-param");
        outerSwitch.setExpression("switch_case_param");
        outerSwitch.setName("outer_switch_task");
        outerSwitch.getInputParameters().put("switch_case_param", "caseB");

        Map<String, List<WorkflowTask>> outerDecisionCases = new HashMap<>();
        outerDecisionCases.put("caseB", Collections.singletonList(innerSwitch));
        outerSwitch.setDecisionCases(outerDecisionCases);

        def.getTasks().add(outerSwitch);

        metadataClient.registerWorkflowDef(def);

        String workflowId = startWorkflow(def.getName(), def);
        assertNotNull(workflowId);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Poll for the simple task (it should be scheduled if resolving works)
        List<Task> polled =
                taskClient.batchPollTasksByTaskType(
                        "simple_task_nested_switch0", "test_worker", 1, 100);
        assertNotNull(polled);
        assertEquals(1, polled.size());

        Task task = polled.get(0);
        task.setStatus(Task.Status.COMPLETED);
        taskClient.updateTask(new TaskResult(task));

        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    /**
     * Tests Fork/Join with a nested Switch task in one of the branches.
     *
     * <p>Workflow Diagram:
     *
     * <pre>
     *                    [Start]
     *                       |
     *                       v
     *                    [Fork]
     *                   /      \
     *                  v        v
     *    [branch1_simple]    [branch2_switch]
     *         |                    |
     *         |              (evaluates to caseX)
     *         |                    |
     *         |                    v
     *         |         [branch2_simple_inside_switch]
     *         |                    |
     *          \                  /
     *           \                /
     *            v              v
     *               [Join]
     *                 |
     *                 v
     *               [End]
     * </pre>
     *
     * <p>Verifies: - Fork correctly schedules parallel branches - Switch task in branch2 correctly
     * routes to caseX - Join task uses polymorphic resolution to wait for: - branch1_simple (direct
     * reference) - branch2_switch -> resolved to branch2_simple_inside_switch - Workflow completes
     * when both branch tasks complete
     */
    @Test
    public void testForkJoinWithNestedSwitch() throws InterruptedException {
        String taskName = "fork_join_switch_task";
        createAndRegisterTaskDefinitions(taskName, 2);

        WorkflowDef def = new WorkflowDef();
        def.setName("fork_join_nested_switch_workflow");
        def.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);
        def.setVersion(1);

        // Branch 1: Simple Task
        WorkflowTask branch1Task = new WorkflowTask();
        branch1Task.setName("fork_join_switch_task0");
        branch1Task.setTaskReferenceName("branch1_simple");
        branch1Task.setWorkflowTaskType(TaskType.SIMPLE);

        // Branch 2: Switch -> Simple Task
        WorkflowTask branch2InnerTask = new WorkflowTask();
        branch2InnerTask.setName("fork_join_switch_task1");
        branch2InnerTask.setTaskReferenceName("branch2_simple_inside_switch");
        branch2InnerTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask branch2Switch = new WorkflowTask();
        branch2Switch.setName("branch2_switch_task");
        branch2Switch.setTaskReferenceName("branch2_switch");
        branch2Switch.setWorkflowTaskType(TaskType.SWITCH);
        branch2Switch.setEvaluatorType("value-param");
        branch2Switch.setExpression("switch_case_param");
        branch2Switch.getInputParameters().put("switch_case_param", "caseX");

        Map<String, List<WorkflowTask>> branch2DecisionCases = new HashMap<>();
        branch2DecisionCases.put("caseX", Collections.singletonList(branch2InnerTask));
        branch2Switch.setDecisionCases(branch2DecisionCases);

        // Fork Task
        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setName("fork_task");
        forkTask.setTaskReferenceName("my_fork");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.getForkTasks().add(Collections.singletonList(branch1Task));
        forkTask.getForkTasks().add(Collections.singletonList(branch2Switch));

        // Join Task - waits on branch1_simple and branch2_switch
        // The Join will resolve branch2_switch to its terminal task
        // (branch2_simple_inside_switch)
        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setName("join_task");
        joinTask.setTaskReferenceName("my_join");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of("branch1_simple", "branch2_switch"));

        def.getTasks().add(forkTask);
        def.getTasks().add(joinTask);

        metadataClient.registerWorkflowDef(def);

        String workflowId = startWorkflow(def.getName(), def);
        assertNotNull(workflowId);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Poll and complete branch1_simple task
        List<Task> polledBranch1 =
                taskClient.batchPollTasksByTaskType(
                        "fork_join_switch_task0", "test_worker", 1, 100);
        assertNotNull(polledBranch1);
        assertEquals(1, polledBranch1.size());
        Task task1 = polledBranch1.get(0);
        task1.setStatus(Task.Status.COMPLETED);
        taskClient.updateTask(new TaskResult(task1));

        // Workflow should still be RUNNING because branch2 is not done
        workflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());

        // Poll and complete branch2_simple_inside_switch task (inside the Switch)
        List<Task> polledBranch2 =
                taskClient.batchPollTasksByTaskType(
                        "fork_join_switch_task1", "test_worker", 1, 100);
        assertNotNull(polledBranch2);
        assertEquals(1, polledBranch2.size());
        Task task2 = polledBranch2.get(0);
        task2.setStatus(Task.Status.COMPLETED);
        taskClient.updateTask(new TaskResult(task2));

        // Wait for workflow to complete (async reconciliation)
        int maxRetries = 20;
        for (int i = 0; i < maxRetries; i++) {
            workflow = workflowClient.getWorkflow(workflowId, true);
            if (workflow.getStatus() == Workflow.WorkflowStatus.COMPLETED) {
                break;
            }
            Thread.sleep(500);
        }

        // Now the workflow should be COMPLETED
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());

        // Verify that Join task is completed
        Task joinTaskResult =
                workflow.getTasks().stream()
                        .filter(t -> "my_join".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElse(null);
        assertNotNull(joinTaskResult);
        assertEquals(Task.Status.COMPLETED, joinTaskResult.getStatus());
    }
}
