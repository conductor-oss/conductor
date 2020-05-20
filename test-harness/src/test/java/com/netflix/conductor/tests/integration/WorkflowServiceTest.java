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
package com.netflix.conductor.tests.integration;

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.tests.utils.TestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.workflow.TaskType.SUB_WORKFLOW;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.RUNNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(TestRunner.class)
public class WorkflowServiceTest extends AbstractWorkflowServiceTest {
    private static final String WF_WITH_INLINE_SUB_WF = "WorkflowWithInlineSubWorkflow";
    private static final String WF_WITH_SUB_WF_WITH_INLINE_SUB_WF = "WorkflowWithSubWorkflowWithInlineSubWorkflow";
    private static final String WF_WITH_INLINE_SUB_WF_WITH_INLINE_SUB_WF = "WorkflowWithInlineSubWorkflowWithInlineSubWorkflow";

    @Override
    String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName, int version,
                                        String correlationId, Map<String, Object> input, String event,
                                        Map<String, String> taskToDomain) {
        return workflowExecutor.startWorkflow(workflowName, version,
            correlationId, input, null, event, taskToDomain);
    }

    @Test
    public void testSubWorkflowWithInlineWorkflowDefinition() {
        registerWorkflow(createWorkflowWithInlineSubWorkflow());
        metadataService.getWorkflowDef(WF_WITH_INLINE_SUB_WF, 2);

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param 1 value");
        input.put("param3", "param 3 value");
        String wfId = startOrLoadWorkflowExecution(WF_WITH_INLINE_SUB_WF, WF_WITH_INLINE_SUB_WF,
            2, "test", input, null, null);
        assertNotNull(wfId);

        validateWorkflowWithInlineSubWorkflowExecution(wfId);
    }

    @Test
    public void testWorkflowWithSubWorkflowWithInlineSubWorkflow() {
        createWorkflowWithSubWorkflowWithInlineSubWorkflow();
        metadataService.getWorkflowDef(WF_WITH_SUB_WF_WITH_INLINE_SUB_WF, 1);

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "parent param 1 value");
        input.put("param3", "parent param 3 value");

        String wfId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF_WITH_INLINE_SUB_WF,
            WF_WITH_SUB_WF_WITH_INLINE_SUB_WF, 1, "test", input, null, null);
        assertNotNull(wfId);

        validateWorkflowWithSubWorkflowWithInlineSubWorkflowExecution(wfId);
    }

    @Test
    public void testWorkflowWithInlineSubWorkflowWithInlineSubWorkflow() {
        createWorkflowWithInlineSubWorkflowWithInlineSubWorkflow();
        metadataService.getWorkflowDef(WF_WITH_INLINE_SUB_WF_WITH_INLINE_SUB_WF, 1);

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "parent param 1 value");
        input.put("param3", "parent param 3 value");

        String wfId = startOrLoadWorkflowExecution(WF_WITH_INLINE_SUB_WF_WITH_INLINE_SUB_WF,
            WF_WITH_INLINE_SUB_WF_WITH_INLINE_SUB_WF, 1, "test", input, null, null);
        assertNotNull(wfId);

        validateWorkflowWithSubWorkflowWithInlineSubWorkflowExecution(wfId);
    }

    private WorkflowDef createInlineSubWorkflow() {
        // create inline subworkflow
        WorkflowDef subWorkflowDef = new WorkflowDef();
        subWorkflowDef.setName("inline_sw_1");
        subWorkflowDef.setDescription(subWorkflowDef.getName());
        subWorkflowDef.setVersion(3);
        subWorkflowDef.setSchemaVersion(2);
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${isw_t1.output.uuid}");
        subWorkflowDef.setOutputParameters(outputParameters);

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        wft1.setTaskReferenceName("isw_t1");
        subWorkflowDef.setTasks(Collections.singletonList(wft1));

        return subWorkflowDef;
    }

    private void registerWorkflow(WorkflowDef def) {
        metadataService.updateWorkflowDef(Collections.singletonList(def));
    }

    private WorkflowDef createWorkflowWithInlineSubWorkflow() {
        WorkflowDef subWorkflowDef = createInlineSubWorkflow();

        WorkflowTask subWfTask = new WorkflowTask();
        subWfTask.setName("subWorkflowTask");
        subWfTask.setType(SUB_WORKFLOW.name());
        SubWorkflowParams swp = new SubWorkflowParams();
        swp.setName("does-not-existing-wf");
        swp.setWorkflowDefinition(subWorkflowDef);
        subWfTask.setSubWorkflowParam(swp);
        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("test", "test value");
        inputParam.put("param1", "sub workflow input param1");
        inputParam.put("param2", subWorkflowDef.getVersion());
        subWfTask.setInputParameters(inputParam);
        subWfTask.setTaskReferenceName("sw1");

        WorkflowDef main = new WorkflowDef();
        main.setVersion(2);
        main.setSchemaVersion(2);
        main.setInputParameters(Arrays.asList("param1", "param2"));
        main.setName(WF_WITH_INLINE_SUB_WF);
        main.getTasks().add(subWfTask);

        return main;
    }

    private void createWorkflowWithSubWorkflowWithInlineSubWorkflow() {
        registerWorkflow(createWorkflowWithInlineSubWorkflow());

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("subWorkflowTask");
        workflowTask.setType(SUB_WORKFLOW.name());
        SubWorkflowParams swp = new SubWorkflowParams();
        swp.setName(WF_WITH_INLINE_SUB_WF);
        workflowTask.setSubWorkflowParam(swp);
        Map<String, Object> input = new HashMap<>();
        input.put("test", "test value");
        input.put("param1", "sub workflow task input param1");
        input.put("param2", 21);
        workflowTask.setInputParameters(input);
        workflowTask.setTaskReferenceName("sw2");

        WorkflowDef main = new WorkflowDef();
        main.setSchemaVersion(2);
        main.setInputParameters(Arrays.asList("param1", "param2"));
        main.setName(WF_WITH_SUB_WF_WITH_INLINE_SUB_WF);
        main.getTasks().add(workflowTask);

        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${sw2.output.o2}");
        main.setOutputParameters(outputParameters);

        metadataService.updateWorkflowDef(Collections.singletonList(main));
    }

    private void createWorkflowWithInlineSubWorkflowWithInlineSubWorkflow() {
        WorkflowDef subWorkflowDef = createWorkflowWithInlineSubWorkflow();

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("subWorkflowTask");
        workflowTask.setType(SUB_WORKFLOW.name());
        SubWorkflowParams swp = new SubWorkflowParams();
        swp.setName("dummy-name");
        swp.setWorkflowDef(subWorkflowDef);
        workflowTask.setSubWorkflowParam(swp);
        Map<String, Object> input = new HashMap<>();
        input.put("test", "test value");
        input.put("param1", "sub workflow task input param1");
        input.put("param2", 21);
        workflowTask.setInputParameters(input);
        workflowTask.setTaskReferenceName("sw2");

        WorkflowDef main = new WorkflowDef();
        main.setSchemaVersion(2);
        main.setInputParameters(Arrays.asList("param1", "param2"));
        main.setName(WF_WITH_INLINE_SUB_WF_WITH_INLINE_SUB_WF);
        main.getTasks().add(workflowTask);

        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${sw2.output.o2}");
        main.setOutputParameters(outputParameters);

        metadataService.updateWorkflowDef(Collections.singletonList(main));
    }

    private void validateWorkflowWithInlineSubWorkflowExecution(String wfId) {
        Workflow workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(RUNNING, workflow.getStatus());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("sw1").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());

        Task subWorkflowTask = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(subWorkflowTask);
        assertNotNull(subWorkflowTask.getOutputData());
        assertNotNull(subWorkflowTask.getInputData());
        assertNotNull("Output: " + subWorkflowTask.getSubWorkflowId() + ", status: " + subWorkflowTask.getStatus(), subWorkflowTask.getSubWorkflowId());
        assertTrue(subWorkflowTask.getInputData().containsKey("workflowInput"));
        assertEquals(3, ((Map<String, Object>) subWorkflowTask.getInputData().get("workflowInput")).get("param2"));
        assertEquals("inline_sw_1", subWorkflowTask.getInputData().get("subWorkflowName"));
        assertEquals(3, subWorkflowTask.getInputData().get("subWorkflowVersion"));
        assertEquals(IN_PROGRESS, subWorkflowTask.getStatus());

        String subWorkflowId = subWorkflowTask.getSubWorkflowId();
        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(wfId, workflow.getParentWorkflowId());
        assertEquals(RUNNING, workflow.getStatus());

        Task simpleTask = workflowExecutionService.poll("junit_task_1", "test");
        String uuid = UUID.nameUUIDFromBytes("hello".getBytes()).toString();
        simpleTask.getOutputData().put("uuid", uuid);
        simpleTask.setStatus(COMPLETED);
        workflowExecutionService.updateTask(simpleTask);

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals("inline_sw_1", workflow.getWorkflowName());
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().containsKey("o1"));
        assertTrue(workflow.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", workflow.getOutput().get("o1"));
        assertEquals(uuid, workflow.getOutput().get("o2"));

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals("sub workflow input param1", workflow.getOutput().get("o1"));
        assertEquals(uuid, workflow.getOutput().get("o2"));

        subWorkflowTask = workflow.getTaskByRefName("sw1");
        assertEquals(COMPLETED, subWorkflowTask.getStatus());
        assertEquals("sub workflow input param1", subWorkflowTask.getOutputData().get("o1"));
        assertEquals(uuid, subWorkflowTask.getOutputData().get("o2"));
    }

    private void validateWorkflowWithSubWorkflowWithInlineSubWorkflowExecution(String wfId) {
        Workflow workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(RUNNING, workflow.getStatus());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("sw2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());

        Task subWorkflowTask = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(subWorkflowTask);
        assertNotNull(subWorkflowTask.getOutputData());
        assertNotNull(subWorkflowTask.getInputData());
        assertNotNull("Output: " + subWorkflowTask.getSubWorkflowId() + ", status: " + subWorkflowTask.getStatus(), subWorkflowTask.getSubWorkflowId());
        assertTrue(subWorkflowTask.getInputData().containsKey("workflowInput"));
        assertEquals(21, ((Map<String, Object>) subWorkflowTask.getInputData().get("workflowInput")).get("param2"));
        assertEquals(WF_WITH_INLINE_SUB_WF, subWorkflowTask.getInputData().get("subWorkflowName"));
        assertEquals(2, subWorkflowTask.getInputData().get("subWorkflowVersion"));
        assertEquals(IN_PROGRESS, subWorkflowTask.getStatus());

        String subWorkflowId = subWorkflowTask.getSubWorkflowId();
        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(wfId, workflow.getParentWorkflowId());

        validateWorkflowWithInlineSubWorkflowExecution(subWorkflowId);

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals("parent param 1 value", workflow.getOutput().get("o1"));
        String uuid = UUID.nameUUIDFromBytes("hello".getBytes()).toString();
        assertEquals(uuid, workflow.getOutput().get("o2"));

        subWorkflowTask = workflow.getTaskByRefName("sw2");
        assertEquals(COMPLETED, subWorkflowTask.getStatus());
        assertEquals("sub workflow input param1", subWorkflowTask.getOutputData().get("o1"));
        assertEquals(uuid, subWorkflowTask.getOutputData().get("o2"));
    }
}
