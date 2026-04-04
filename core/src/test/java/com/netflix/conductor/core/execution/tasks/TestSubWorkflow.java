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
package com.netflix.conductor.core.execution.tasks;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class TestSubWorkflow {

    private static final String PARENT_WORKFLOW_ID = "parent-workflow";
    private static final String PARENT_TASK_ID = "task_1";
    private static final String RESERVED_SUB_WORKFLOW_ID = "reserved-sub-workflow";

    private WorkflowExecutor workflowExecutor;
    private SubWorkflow subWorkflow;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setup() {
        workflowExecutor = mock(WorkflowExecutor.class);
        subWorkflow = new SubWorkflow(objectMapper);
    }

    @Test
    public void testStartSubWorkflow() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        Map<String, Object> inputData = inputData("UnitWorkFlow", 3);
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId(RESERVED_SUB_WORKFLOW_ID);
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 3, inputData, null, null);
        mockSubWorkflowLaunch(workflowInstance, task, startWorkflowInput, subWorkflowInstance);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertEquals(RESERVED_SUB_WORKFLOW_ID, task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertNull(task.getReasonForIncompletion());
        assertFalse(task.getOutputData().containsKey("subWorkflowLaunchError"));
    }

    @Test
    public void testStartSubWorkflowQueueFailure() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        task.setStatus(TaskModel.Status.SCHEDULED);
        Map<String, Object> inputData = inputData("UnitWorkFlow", 3);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 3, inputData, null, null);

        when(workflowExecutor.reserveSubWorkflowId(PARENT_WORKFLOW_ID, PARENT_TASK_ID))
                .thenReturn(RESERVED_SUB_WORKFLOW_ID);
        when(workflowExecutor.startWorkflowIdempotent(startWorkflowInput))
                .thenThrow(new TransientException("QueueDAO failure"));

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertNull(task.getSubWorkflowId());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals(
                "Transient error starting sub workflow UnitWorkFlow: QueueDAO failure",
                task.getReasonForIncompletion());
        assertEquals("QueueDAO failure", task.getOutputData().get("subWorkflowLaunchError"));
    }

    @Test
    public void testStartSubWorkflowStartError() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        task.setStatus(TaskModel.Status.SCHEDULED);
        Map<String, Object> inputData = inputData("UnitWorkFlow", 3);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 3, inputData, null, null);

        when(workflowExecutor.reserveSubWorkflowId(PARENT_WORKFLOW_ID, PARENT_TASK_ID))
                .thenReturn(RESERVED_SUB_WORKFLOW_ID);
        when(workflowExecutor.startWorkflowIdempotent(startWorkflowInput))
                .thenThrow(new NonTransientException("non transient failure"));

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertNull(task.getSubWorkflowId());
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("non transient failure", task.getReasonForIncompletion());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    public void testStartSubWorkflowWithEmptyWorkflowInputUsesTaskInput() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        Map<String, Object> inputData = inputData("UnitWorkFlow", 3);
        inputData.put("workflowInput", new HashMap<>());
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId(RESERVED_SUB_WORKFLOW_ID);
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 3, inputData, null, null);
        mockSubWorkflowLaunch(workflowInstance, task, startWorkflowInput, subWorkflowInstance);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertEquals(RESERVED_SUB_WORKFLOW_ID, task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowWithWorkflowInput() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        Map<String, Object> inputData = inputData("UnitWorkFlow", 3);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("test", "value");
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId(RESERVED_SUB_WORKFLOW_ID);
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 3, workflowInput, null, null);
        mockSubWorkflowLaunch(workflowInstance, task, startWorkflowInput, subWorkflowInstance);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertEquals(RESERVED_SUB_WORKFLOW_ID, task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowTaskToDomain() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "unittest");

        Map<String, Object> inputData = inputData("UnitWorkFlow", 2);
        inputData.put("subWorkflowTaskToDomain", taskToDomain);
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId(RESERVED_SUB_WORKFLOW_ID);
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 2, inputData, taskToDomain, null);
        mockSubWorkflowLaunch(workflowInstance, task, startWorkflowInput, subWorkflowInstance);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertEquals(RESERVED_SUB_WORKFLOW_ID, task.getSubWorkflowId());
    }

    @Test
    public void testExecuteSubWorkflowWithoutId() {
        WorkflowModel workflowInstance = newParentWorkflow();

        TaskModel task = newTask();
        task.setOutputData(new HashMap<>());
        task.setInputData(inputData("UnitWorkFlow", 2));

        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
    }

    @Test
    public void testExecuteScheduledSubWorkflowWithoutIdRetriesStart() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        task.setStatus(TaskModel.Status.SCHEDULED);
        Map<String, Object> inputData = inputData("UnitWorkFlow", 2);
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId(RESERVED_SUB_WORKFLOW_ID);
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance, task, "UnitWorkFlow", 2, inputData, null, null);
        mockSubWorkflowLaunch(workflowInstance, task, startWorkflowInput, subWorkflowInstance);

        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(RESERVED_SUB_WORKFLOW_ID, task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertNull(task.getReasonForIncompletion());
        assertFalse(task.getOutputData().containsKey("subWorkflowLaunchError"));
    }

    @Test
    public void testExecuteWorkflowStatus() {
        WorkflowModel workflowInstance = newParentWorkflow();
        WorkflowModel subWorkflowInstance = new WorkflowModel();
        TaskModel task = newTask();
        task.setSubWorkflowId("sub-workflow-id");
        task.setOutputData(new HashMap<>());
        task.setInputData(inputData("UnitWorkFlow", 2));

        when(workflowExecutor.getWorkflow("sub-workflow-id", false))
                .thenReturn(subWorkflowInstance);

        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);
        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertNull(task.getStatus());
        assertNull(task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(WorkflowModel.Status.PAUSED);
        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertNull(task.getStatus());
        assertNull(task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(WorkflowModel.Status.COMPLETED);
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNull(task.getReasonForIncompletion());

        subWorkflowInstance.setStatus(WorkflowModel.Status.FAILED);
        subWorkflowInstance.setReasonForIncompletion("unit1");
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("unit1"));

        subWorkflowInstance.setStatus(WorkflowModel.Status.TIMED_OUT);
        subWorkflowInstance.setReasonForIncompletion("unit2");
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(TaskModel.Status.TIMED_OUT, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("unit2"));

        subWorkflowInstance.setStatus(WorkflowModel.Status.TERMINATED);
        subWorkflowInstance.setReasonForIncompletion("unit3");
        assertTrue(subWorkflow.execute(workflowInstance, task, workflowExecutor));
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains("unit3"));
    }

    @Test
    public void testCancelWithWorkflowId() {
        WorkflowModel workflowInstance = newParentWorkflow();
        WorkflowModel subWorkflowInstance = new WorkflowModel();
        TaskModel task = newTask();
        task.setSubWorkflowId("sub-workflow-id");
        task.setInputData(inputData("UnitWorkFlow", 2));

        when(workflowExecutor.getWorkflow("sub-workflow-id", true)).thenReturn(subWorkflowInstance);

        workflowInstance.setStatus(WorkflowModel.Status.TIMED_OUT);
        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(WorkflowModel.Status.TERMINATED, subWorkflowInstance.getStatus());
    }

    @Test
    public void testCancelWithoutWorkflowId() {
        WorkflowModel workflowInstance = newParentWorkflow();
        WorkflowModel subWorkflowInstance = new WorkflowModel();
        TaskModel task = newTask();
        task.setInputData(inputData("UnitWorkFlow", 2));

        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(WorkflowModel.Status.RUNNING, subWorkflowInstance.getStatus());
    }

    @Test
    public void testIsAsync() {
        assertFalse(subWorkflow.isAsync());
    }

    @Test
    public void testStartSubWorkflowWithSubWorkflowDefinition() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();

        WorkflowDef subWorkflowDef = new WorkflowDef();
        subWorkflowDef.setName("subWorkflow_1");

        Map<String, Object> inputData = inputData("UnitWorkFlow", 2);
        inputData.put("subWorkflowDefinition", subWorkflowDef);
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId(RESERVED_SUB_WORKFLOW_ID);
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance,
                        task,
                        "subWorkflow_1",
                        2,
                        inputData,
                        null,
                        subWorkflowDef);
        mockSubWorkflowLaunch(workflowInstance, task, startWorkflowInput, subWorkflowInstance);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        assertEquals(RESERVED_SUB_WORKFLOW_ID, task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowReusesExistingTaskSubWorkflowId() {
        WorkflowModel workflowInstance = newParentWorkflow();
        TaskModel task = newTask();
        task.setSubWorkflowId("existing-sub-workflow");
        Map<String, Object> inputData = inputData("UnitWorkFlow", 2);
        task.setInputData(inputData);

        WorkflowModel subWorkflowInstance = new WorkflowModel();
        subWorkflowInstance.setWorkflowId("existing-sub-workflow");
        subWorkflowInstance.setStatus(WorkflowModel.Status.RUNNING);

        StartWorkflowInput startWorkflowInput =
                expectedStartWorkflowInput(
                        workflowInstance,
                        task,
                        "UnitWorkFlow",
                        2,
                        inputData,
                        null,
                        null,
                        "existing-sub-workflow");

        when(workflowExecutor.startWorkflowIdempotent(startWorkflowInput))
                .thenReturn("existing-sub-workflow");
        when(workflowExecutor.getWorkflow("existing-sub-workflow", false))
                .thenReturn(subWorkflowInstance);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        verify(workflowExecutor).startWorkflowIdempotent(startWorkflowInput);
        assertEquals("existing-sub-workflow", task.getSubWorkflowId());
    }

    private WorkflowModel newParentWorkflow() {
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowId(PARENT_WORKFLOW_ID);
        workflowInstance.setWorkflowDefinition(new WorkflowDef());
        return workflowInstance;
    }

    private TaskModel newTask() {
        TaskModel task = new TaskModel();
        task.setTaskId(PARENT_TASK_ID);
        task.setOutputData(new HashMap<>());
        return task;
    }

    private Map<String, Object> inputData(String subWorkflowName, int subWorkflowVersion) {
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", subWorkflowName);
        inputData.put("subWorkflowVersion", subWorkflowVersion);
        return inputData;
    }

    private void mockSubWorkflowLaunch(
            WorkflowModel workflowInstance,
            TaskModel task,
            StartWorkflowInput startWorkflowInput,
            WorkflowModel subWorkflowInstance) {
        when(workflowExecutor.reserveSubWorkflowId(PARENT_WORKFLOW_ID, PARENT_TASK_ID))
                .thenReturn(RESERVED_SUB_WORKFLOW_ID);
        when(workflowExecutor.startWorkflowIdempotent(startWorkflowInput))
                .thenReturn(RESERVED_SUB_WORKFLOW_ID);
        when(workflowExecutor.getWorkflow(RESERVED_SUB_WORKFLOW_ID, false))
                .thenReturn(subWorkflowInstance);
    }

    private StartWorkflowInput expectedStartWorkflowInput(
            WorkflowModel workflowInstance,
            TaskModel task,
            String subWorkflowName,
            int subWorkflowVersion,
            Map<String, Object> workflowInput,
            Map<String, String> taskToDomain,
            WorkflowDef workflowDef) {
        return expectedStartWorkflowInput(
                workflowInstance,
                task,
                subWorkflowName,
                subWorkflowVersion,
                workflowInput,
                taskToDomain,
                workflowDef,
                RESERVED_SUB_WORKFLOW_ID);
    }

    private StartWorkflowInput expectedStartWorkflowInput(
            WorkflowModel workflowInstance,
            TaskModel task,
            String subWorkflowName,
            int subWorkflowVersion,
            Map<String, Object> workflowInput,
            Map<String, String> taskToDomain,
            WorkflowDef workflowDef,
            String workflowId) {
        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setWorkflowDefinition(workflowDef);
        startWorkflowInput.setName(subWorkflowName);
        startWorkflowInput.setVersion(subWorkflowVersion);
        startWorkflowInput.setWorkflowInput(workflowInput);
        startWorkflowInput.setCorrelationId(workflowInstance.getCorrelationId());
        startWorkflowInput.setParentWorkflowId(workflowInstance.getWorkflowId());
        startWorkflowInput.setParentWorkflowTaskId(task.getTaskId());
        startWorkflowInput.setTaskToDomain(
                taskToDomain == null ? workflowInstance.getTaskToDomain() : taskToDomain);
        startWorkflowInput.setWorkflowId(workflowId);
        return startWorkflowInput;
    }
}
