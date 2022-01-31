/*
 * Copyright 2022 Netflix, Inc.
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
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(3),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        any()))
                .thenReturn(workflowId);

        when(workflowExecutor.getWorkflow(anyString(), eq(false))).thenReturn(workflow);

        workflow.setStatus(WorkflowModel.Status.RUNNING);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());

        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());

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

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(3),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        any()))
                .thenThrow(
                        new ApplicationException(
                                ApplicationException.Code.BACKEND_ERROR, "QueueDAO failure"));

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

        String failureReason = "non transient failure";
        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(3),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        any()))
                .thenThrow(
                        new ApplicationException(
                                ApplicationException.Code.INTERNAL_ERROR, failureReason));

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

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);

        Map<String, Object> workflowInput = new HashMap<>();
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(3),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        any()))
                .thenReturn("workflow_1");

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

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 3);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("test", "value");
        inputData.put("workflowInput", workflowInput);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(3),
                        eq(workflowInput),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        any()))
                .thenReturn("workflow_1");

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

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowTaskToDomain", taskToDomain);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(2),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        eq(taskToDomain)))
                .thenReturn("workflow_1");

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

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(2),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        eq(null)))
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

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(2),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        eq(taskToDomain)))
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

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(2),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        eq(null)))
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

        when(workflowExecutor.startWorkflow(
                        eq("UnitWorkFlow"),
                        eq(2),
                        eq(inputData),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        eq(null),
                        eq(null)))
                .thenReturn("workflow_1");
        when(workflowExecutor.getWorkflow(eq("sub-workflow-id"), eq(false)))
                .thenReturn(subWorkflowInstance);

        subWorkflow.cancel(workflowInstance, task, workflowExecutor);

        assertEquals(WorkflowModel.Status.RUNNING, subWorkflowInstance.getStatus());
    }

    @Test
    public void testIsAsync() {
        assertTrue(subWorkflow.isAsync());
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

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowName", "UnitWorkFlow");
        inputData.put("subWorkflowVersion", 2);
        inputData.put("subWorkflowDefinition", subWorkflowDef);
        task.setInputData(inputData);

        when(workflowExecutor.startWorkflow(
                        eq(subWorkflowDef),
                        eq(inputData),
                        eq(null),
                        any(),
                        eq(0),
                        any(),
                        any(),
                        eq(null),
                        any()))
                .thenReturn("workflow_1");

        subWorkflow.start(workflowInstance, task, workflowExecutor);
        assertEquals("workflow_1", task.getSubWorkflowId());
    }
}
