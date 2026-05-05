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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class TestSubWorkflow {

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
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);
        task.setInputData(inputData);

        String workflowId = "workflow_1";
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn(workflowId);
        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(workflow);

        // Each start() call must begin in SCHEDULED state; once started, the double-start guard
        // (status != SCHEDULED) prevents re-creation on a subsequent call with the same task.
        task.setStatus(TaskModel.Status.SCHEDULED);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());

        task.setSubWorkflowId(null); // reset to simulate a fresh retry attempt
        task.setStatus(TaskModel.Status.SCHEDULED);
        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());

        task.setSubWorkflowId(null); // reset to simulate a fresh retry attempt
        task.setStatus(TaskModel.Status.SCHEDULED);
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void testStartSubWorkflowQueueFailure() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(3);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(workflowInstance.getTaskToDomain());

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenThrow(new TransientException("QueueDAO failure"));
        // getWorkflow is not called when startWorkflow throws — no mock needed

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertNull("subWorkflowId should be null", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertTrue("Output data should be empty", task.getOutputData().isEmpty());
    }

    @Test
    public void testStartSubWorkflowStartError() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(3);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(workflowInstance.getTaskToDomain());

        String failureReason = "non transient failure";
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenThrow(new NonTransientException(failureReason));
        // getWorkflow is not called when startWorkflow throws — no mock needed

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertNull("subWorkflowId should be null", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(failureReason, task.getReasonForIncompletion());
        assertTrue("Output data should be empty", task.getOutputData().isEmpty());
    }

    @Test
    public void testStartSubWorkflowWithEmptyWorkflowInput() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);

        Map<String, Object> workflowInput = new HashMap<>();
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        WorkflowModel createdSubWorkflow = new WorkflowModel();
        createdSubWorkflow.setStatus(WorkflowModel.Status.RUNNING);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(createdSubWorkflow);

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowWithWorkflowInput() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("test", "value");
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        WorkflowModel createdSubWorkflow = new WorkflowModel();
        createdSubWorkflow.setStatus(WorkflowModel.Status.RUNNING);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(createdSubWorkflow);

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }

    @Test
    public void testStartSubWorkflowTaskToDomain() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);
        Map<String, String> taskToDomain =
                new HashMap<>() {
                    {
                        put("*", "unittest");
                    }
                };

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowTaskToDomain", taskToDomain);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(2);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(taskToDomain);

        WorkflowModel createdSubWorkflow = new WorkflowModel();
        createdSubWorkflow.setStatus(WorkflowModel.Status.RUNNING);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(createdSubWorkflow);

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }

    @Test
    public void testExecuteSubWorkflowWithoutId() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(2);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(workflowInstance.getTaskToDomain());

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");

        assertFalse(subWorkflow.execute(workflowInstance, task, workflowExecutor));
    }

    @Test
    public void testExecuteWorkflowStatus() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        WorkflowModel subWorkflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);
        Map<String, String> taskToDomain =
                new HashMap<>() {
                    {
                        put("*", "unittest");
                    }
                };

        TaskModel task = new TaskModel();
        Map<String, Object> outputData = new HashMap<>();
        task.setOutputData(outputData);
        task.setSubWorkflowId("sub-workflow-id");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowTaskToDomain", taskToDomain);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(2);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(taskToDomain);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(false)))
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
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        WorkflowModel subWorkflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        task.setSubWorkflowId("sub-workflow-id");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(2);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(workflowInstance.getTaskToDomain());

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(true)))
                .thenReturn(subWorkflowInstance);

        workflowInstance.setStatus(WorkflowModel.Status.TIMED_OUT);
        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(WorkflowModel.Status.TERMINATED, subWorkflowInstance.getStatus());
    }

    @Test
    public void testCancelWithoutWorkflowId() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        WorkflowModel subWorkflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        TaskModel task = new TaskModel();
        Map<String, Object> outputData = new HashMap<>();
        task.setOutputData(outputData);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        task.setInputData(inputData);

        StartWorkflowInput startWorkflowInput = new StartWorkflowInput();
        startWorkflowInput.setName("UnitWorkFlow");
        startWorkflowInput.setVersion(2);
        startWorkflowInput.setWorkflowInput(inputData);
        startWorkflowInput.setTaskToDomain(workflowInstance.getTaskToDomain());

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(false)))
                .thenReturn(subWorkflowInstance);

        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(WorkflowModel.Status.RUNNING, subWorkflowInstance.getStatus());
    }

    @Test
    public void testStartThrowsWhenSubWorkflowNameNullAndNoDefinitionSupplied() {
        // If neither subWorkflowName nor subWorkflowDefinition is provided the task is
        // misconfigured; start() must throw NonTransientException rather than NPE.
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(new WorkflowDef());

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setInputData(new HashMap<>()); // no subWorkflowName, no subWorkflowDefinition

        try {
            subWorkflow.start(workflowInstance, task, workflowExecutor);
            fail("Expected NonTransientException");
        } catch (NonTransientException e) {
            assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    public void testStartIsIdempotentWhenTaskAlreadyStarted() {
        // If start() is called again on a task that already moved past SCHEDULED (e.g. sweeper
        // retry before the first invocation persisted), the status-based guard must prevent
        // creating a duplicate sub-workflow.
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(new WorkflowDef());

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId("already-started-id");
        task.setStatus(TaskModel.Status.IN_PROGRESS); // task moved past SCHEDULED on first start

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 1);
        task.setInputData(inputData);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        // startWorkflow must NOT have been called a second time
        assertEquals("already-started-id", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    public void testStartCreatesNewSubWorkflowOnRetryEvenWhenOutputDataHasOldSubWorkflowId() {
        // Regression test: retried tasks inherit outputData from their failed predecessor,
        // so outputData may already contain "subWorkflowId" from the previous attempt.
        // The old guard (checking getSubWorkflowId()) would have incorrectly blocked this retry.
        // The new guard (checking status == SCHEDULED) must allow the retry through.
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(new WorkflowDef());

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("subWorkflowId", "old-sub-workflow-id"); // inherited from failed predecessor

        TaskModel task = new TaskModel();
        task.setOutputData(outputData);
        task.setStatus(TaskModel.Status.SCHEDULED); // retry: scheduler resets to SCHEDULED

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 1);
        task.setInputData(inputData);

        WorkflowModel createdSubWorkflow = new WorkflowModel();
        createdSubWorkflow.setStatus(WorkflowModel.Status.RUNNING);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("new-sub-workflow-id");
        when(workflowExecutor.getWorkflow(eq("new-sub-workflow-id"), eq(false)))
                .thenReturn(createdSubWorkflow);

        subWorkflow.start(workflowInstance, task, workflowExecutor);

        // A new sub-workflow must have been created, not blocked by the old outputData
        assertEquals("new-sub-workflow-id", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    public void testCancelIsNoOpWhenSubWorkflowNotFoundInStore() {
        // If the sub-workflow was deleted from the store (e.g. TTL expired) cancel() must
        // log a warning and return without throwing NullPointerException.
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(new WorkflowDef());
        workflowInstance.setStatus(WorkflowModel.Status.TERMINATED);

        TaskModel task = new TaskModel();
        task.setSubWorkflowId("deleted-sub-workflow-id");

        when(workflowExecutor.getWorkflow(eq("deleted-sub-workflow-id"), eq(true)))
                .thenReturn(null); // simulates a deleted or expired sub-workflow

        // Must not throw
        subWorkflow.cancel(workflowInstance, task, workflowExecutor);
    }

    @Test
    public void testExecuteReturnsFalseWhenSubWorkflowNotFoundInStore() {
        // If the sub-workflow was deleted from the store (e.g. TTL expired) execute() must
        // log a warning and return false without throwing NullPointerException.
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(new WorkflowDef());

        TaskModel task = new TaskModel();
        task.setSubWorkflowId("deleted-sub-workflow-id");

        when(workflowExecutor.getWorkflow(eq("deleted-sub-workflow-id"), eq(false)))
                .thenReturn(null); // simulates a deleted or expired sub-workflow

        boolean result = subWorkflow.execute(workflowInstance, task, workflowExecutor);
        assertFalse("execute() must return false when sub-workflow is not found", result);
    }

    @Test
    public void testIsAsync() {
        assertFalse(subWorkflow.isAsync());
    }

    @Test
    public void testStartSubWorkflowWithSubWorkflowDefinition() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);

        WorkflowDef subWorkflowDef = new WorkflowDef();
        subWorkflowDef.setName("subWorkflow_1");

        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        task.setStatus(TaskModel.Status.SCHEDULED);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowDefinition", subWorkflowDef);
        task.setInputData(inputData);

        WorkflowModel createdSubWorkflow = new WorkflowModel();
        createdSubWorkflow.setStatus(WorkflowModel.Status.RUNNING);

        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(createdSubWorkflow);

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }
}
