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
package com.netflix.conductor.core.events;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.Action.Type;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.events.EventHandler.TaskDetails;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.operation.StartWorkflowOperation;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class TestSimpleActionProcessor {

    private WorkflowExecutor workflowExecutor;
    private ExternalPayloadStorageUtils externalPayloadStorageUtils;
    private SimpleActionProcessor actionProcessor;
    private StartWorkflowOperation startWorkflowOperation;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setup() {
        externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);

        workflowExecutor = mock(WorkflowExecutor.class);
        startWorkflowOperation = mock(StartWorkflowOperation.class);

        actionProcessor =
                new SimpleActionProcessor(
                        workflowExecutor,
                        new ParametersUtils(objectMapper),
                        new JsonUtils(objectMapper),
                        startWorkflowOperation);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testStartWorkflow_correlationId() throws Exception {
        StartWorkflow startWorkflow = new StartWorkflow();
        startWorkflow.setName("testWorkflow");
        startWorkflow.getInput().put("testInput", "${testId}");
        startWorkflow.setCorrelationId("${correlationId}");

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "dev");
        startWorkflow.setTaskToDomain(taskToDomain);

        Action action = new Action();
        action.setAction(Type.start_workflow);
        action.setStart_workflow(startWorkflow);

        Object payload =
                objectMapper.readValue(
                        "{\"correlationId\":\"test-id\", \"testId\":\"test_1\"}", Object.class);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testWorkflow");
        workflowDef.setVersion(1);

        when(startWorkflowOperation.execute(any())).thenReturn("workflow_1");

        Map<String, Object> output =
                actionProcessor.execute(action, payload, "testEvent", "testMessage");

        assertNotNull(output);
        assertEquals("workflow_1", output.get("workflowId"));

        ArgumentCaptor<StartWorkflowInput> startWorkflowInputArgumentCaptor =
                ArgumentCaptor.forClass(StartWorkflowInput.class);

        verify(startWorkflowOperation).execute(startWorkflowInputArgumentCaptor.capture());
        StartWorkflowInput capturedValue = startWorkflowInputArgumentCaptor.getValue();

        assertEquals("test_1", capturedValue.getWorkflowInput().get("testInput"));
        assertEquals("test-id", capturedValue.getCorrelationId());
        assertEquals(
                "testMessage", capturedValue.getWorkflowInput().get("conductor.event.messageId"));
        assertEquals("testEvent", capturedValue.getWorkflowInput().get("conductor.event.name"));
        assertEquals(taskToDomain, capturedValue.getTaskToDomain());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testStartWorkflow() throws Exception {
        StartWorkflow startWorkflow = new StartWorkflow();
        startWorkflow.setName("testWorkflow");
        startWorkflow.getInput().put("testInput", "${testId}");

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "dev");
        startWorkflow.setTaskToDomain(taskToDomain);

        Action action = new Action();
        action.setAction(Type.start_workflow);
        action.setStart_workflow(startWorkflow);

        Object payload = objectMapper.readValue("{\"testId\":\"test_1\"}", Object.class);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testWorkflow");
        workflowDef.setVersion(1);

        when(startWorkflowOperation.execute(any())).thenReturn("workflow_1");

        Map<String, Object> output =
                actionProcessor.execute(action, payload, "testEvent", "testMessage");

        assertNotNull(output);
        assertEquals("workflow_1", output.get("workflowId"));

        ArgumentCaptor<StartWorkflowInput> startWorkflowInputArgumentCaptor =
                ArgumentCaptor.forClass(StartWorkflowInput.class);

        verify(startWorkflowOperation).execute(startWorkflowInputArgumentCaptor.capture());
        StartWorkflowInput capturedArgument = startWorkflowInputArgumentCaptor.getValue();
        assertEquals("test_1", capturedArgument.getWorkflowInput().get("testInput"));
        assertNull(capturedArgument.getCorrelationId());
        assertEquals(
                "testMessage",
                capturedArgument.getWorkflowInput().get("conductor.event.messageId"));
        assertEquals("testEvent", capturedArgument.getWorkflowInput().get("conductor.event.name"));
        assertEquals(taskToDomain, capturedArgument.getTaskToDomain());
    }

    @Test
    public void testCompleteTask() throws Exception {
        TaskDetails taskDetails = new TaskDetails();
        taskDetails.setWorkflowId("${workflowId}");
        taskDetails.setTaskRefName("testTask");
        taskDetails.getOutput().put("someNEKey", "${Message.someNEKey}");
        taskDetails.getOutput().put("someKey", "${Message.someKey}");
        taskDetails.getOutput().put("someNullKey", "${Message.someNullKey}");

        Action action = new Action();
        action.setAction(Type.complete_task);
        action.setComplete_task(taskDetails);

        String payloadJson =
                "{\"workflowId\":\"workflow_1\",\"Message\":{\"someKey\":\"someData\",\"someNullKey\":null}}";
        Object payload = objectMapper.readValue(payloadJson, Object.class);

        TaskModel task = new TaskModel();
        task.setReferenceTaskName("testTask");
        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(task);

        when(workflowExecutor.getWorkflow(eq("workflow_1"), anyBoolean())).thenReturn(workflow);
        doNothing().when(externalPayloadStorageUtils).verifyAndUpload(any(), any());

        actionProcessor.execute(action, payload, "testEvent", "testMessage");

        ArgumentCaptor<TaskResult> argumentCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(argumentCaptor.capture());
        assertEquals(Status.COMPLETED, argumentCaptor.getValue().getStatus());
        assertEquals(
                "testMessage",
                argumentCaptor.getValue().getOutputData().get("conductor.event.messageId"));
        assertEquals(
                "testEvent", argumentCaptor.getValue().getOutputData().get("conductor.event.name"));
        assertEquals("workflow_1", argumentCaptor.getValue().getOutputData().get("workflowId"));
        assertEquals("testTask", argumentCaptor.getValue().getOutputData().get("taskRefName"));
        assertEquals("someData", argumentCaptor.getValue().getOutputData().get("someKey"));
        // Assert values not in message are evaluated to null
        assertTrue("testTask", argumentCaptor.getValue().getOutputData().containsKey("someNEKey"));
        // Assert null values from message are kept
        assertTrue(
                "testTask", argumentCaptor.getValue().getOutputData().containsKey("someNullKey"));
        assertNull("testTask", argumentCaptor.getValue().getOutputData().get("someNullKey"));
    }

    @Test
    public void testCompleteLoopOverTask() throws Exception {
        TaskDetails taskDetails = new TaskDetails();
        taskDetails.setWorkflowId("${workflowId}");
        taskDetails.setTaskRefName("testTask");
        taskDetails.getOutput().put("someNEKey", "${Message.someNEKey}");
        taskDetails.getOutput().put("someKey", "${Message.someKey}");
        taskDetails.getOutput().put("someNullKey", "${Message.someNullKey}");

        Action action = new Action();
        action.setAction(Type.complete_task);
        action.setComplete_task(taskDetails);

        String payloadJson =
                "{\"workflowId\":\"workflow_1\",  \"taskRefName\":\"testTask\", \"Message\":{\"someKey\":\"someData\",\"someNullKey\":null}}";
        Object payload = objectMapper.readValue(payloadJson, Object.class);

        TaskModel task = new TaskModel();
        task.setIteration(1);
        task.setReferenceTaskName("testTask__1");
        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().add(task);

        when(workflowExecutor.getWorkflow(eq("workflow_1"), anyBoolean())).thenReturn(workflow);
        doNothing().when(externalPayloadStorageUtils).verifyAndUpload(any(), any());

        actionProcessor.execute(action, payload, "testEvent", "testMessage");

        ArgumentCaptor<TaskResult> argumentCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(argumentCaptor.capture());
        assertEquals(Status.COMPLETED, argumentCaptor.getValue().getStatus());
        assertEquals(
                "testMessage",
                argumentCaptor.getValue().getOutputData().get("conductor.event.messageId"));
        assertEquals(
                "testEvent", argumentCaptor.getValue().getOutputData().get("conductor.event.name"));
        assertEquals("workflow_1", argumentCaptor.getValue().getOutputData().get("workflowId"));
        assertEquals("testTask", argumentCaptor.getValue().getOutputData().get("taskRefName"));
        assertEquals("someData", argumentCaptor.getValue().getOutputData().get("someKey"));
        // Assert values not in message are evaluated to null
        assertTrue("testTask", argumentCaptor.getValue().getOutputData().containsKey("someNEKey"));
        // Assert null values from message are kept
        assertTrue(
                "testTask", argumentCaptor.getValue().getOutputData().containsKey("someNullKey"));
        assertNull("testTask", argumentCaptor.getValue().getOutputData().get("someNullKey"));
    }

    @Test
    public void testCompleteTaskByTaskId() throws Exception {
        TaskDetails taskDetails = new TaskDetails();
        taskDetails.setWorkflowId("${workflowId}");
        taskDetails.setTaskId("${taskId}");

        Action action = new Action();
        action.setAction(Type.complete_task);
        action.setComplete_task(taskDetails);

        Object payload =
                objectMapper.readValue(
                        "{\"workflowId\":\"workflow_1\", \"taskId\":\"task_1\"}", Object.class);

        TaskModel task = new TaskModel();
        task.setTaskId("task_1");
        task.setReferenceTaskName("testTask");

        when(workflowExecutor.getTask(eq("task_1"))).thenReturn(task);
        doNothing().when(externalPayloadStorageUtils).verifyAndUpload(any(), any());

        actionProcessor.execute(action, payload, "testEvent", "testMessage");

        ArgumentCaptor<TaskResult> argumentCaptor = ArgumentCaptor.forClass(TaskResult.class);
        verify(workflowExecutor).updateTask(argumentCaptor.capture());
        assertEquals(Status.COMPLETED, argumentCaptor.getValue().getStatus());
        assertEquals(
                "testMessage",
                argumentCaptor.getValue().getOutputData().get("conductor.event.messageId"));
        assertEquals(
                "testEvent", argumentCaptor.getValue().getOutputData().get("conductor.event.name"));
        assertEquals("workflow_1", argumentCaptor.getValue().getOutputData().get("workflowId"));
        assertEquals("task_1", argumentCaptor.getValue().getOutputData().get("taskId"));
    }
}
