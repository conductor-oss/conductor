/*
 * Copyright 2025 Conductor Authors.
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
package io.orkes.conductor.client.model;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkflowScheduleExecutionModelPojoMethods {

    @Test
    void testGettersAndSetters() {
        // Setup
        WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel();
        String executionId = "exec-123";
        Long executionTime = 1000L;
        String reason = "test reason";
        String scheduleName = "test schedule";
        Long scheduledTime = 2000L;
        String stackTrace = "test stack trace";
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        WorkflowScheduleExecutionModel.StateEnum state = WorkflowScheduleExecutionModel.StateEnum.EXECUTED;
        String workflowId = "workflow-123";
        String workflowName = "test workflow";

        // Execute
        model.setExecutionId(executionId);
        model.setExecutionTime(executionTime);
        model.setReason(reason);
        model.setScheduleName(scheduleName);
        model.setScheduledTime(scheduledTime);
        model.setStackTrace(stackTrace);
        model.setStartWorkflowRequest(startWorkflowRequest);
        model.setState(state);
        model.setWorkflowId(workflowId);
        model.setWorkflowName(workflowName);

        // Verify
        assertEquals(executionId, model.getExecutionId());
        assertEquals(executionTime, model.getExecutionTime());
        assertEquals(reason, model.getReason());
        assertEquals(scheduleName, model.getScheduleName());
        assertEquals(scheduledTime, model.getScheduledTime());
        assertEquals(stackTrace, model.getStackTrace());
        assertEquals(startWorkflowRequest, model.getStartWorkflowRequest());
        assertEquals(state, model.getState());
        assertEquals(workflowId, model.getWorkflowId());
        assertEquals(workflowName, model.getWorkflowName());
    }

    @Test
    void testBuilderStyleMethods() {
        // Setup
        String executionId = "exec-123";
        Long executionTime = 1000L;
        String reason = "test reason";
        String scheduleName = "test schedule";
        Long scheduledTime = 2000L;
        String stackTrace = "test stack trace";
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        WorkflowScheduleExecutionModel.StateEnum state = WorkflowScheduleExecutionModel.StateEnum.EXECUTED;
        String workflowId = "workflow-123";
        String workflowName = "test workflow";

        // Execute
        WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel()
                .executionId(executionId)
                .executionTime(executionTime)
                .reason(reason)
                .scheduleName(scheduleName)
                .scheduledTime(scheduledTime)
                .stackTrace(stackTrace)
                .startWorkflowRequest(startWorkflowRequest)
                .state(state)
                .workflowId(workflowId)
                .workflowName(workflowName);

        // Verify
        assertEquals(executionId, model.getExecutionId());
        assertEquals(executionTime, model.getExecutionTime());
        assertEquals(reason, model.getReason());
        assertEquals(scheduleName, model.getScheduleName());
        assertEquals(scheduledTime, model.getScheduledTime());
        assertEquals(stackTrace, model.getStackTrace());
        assertEquals(startWorkflowRequest, model.getStartWorkflowRequest());
        assertEquals(state, model.getState());
        assertEquals(workflowId, model.getWorkflowId());
        assertEquals(workflowName, model.getWorkflowName());
    }

    @Test
    void testEnumValues() {
        // Verify
        assertEquals("POLLED", WorkflowScheduleExecutionModel.StateEnum.POLLED.getValue());
        assertEquals("FAILED", WorkflowScheduleExecutionModel.StateEnum.FAILED.getValue());
        assertEquals("EXECUTED", WorkflowScheduleExecutionModel.StateEnum.EXECUTED.getValue());

        assertEquals("POLLED", WorkflowScheduleExecutionModel.StateEnum.POLLED.toString());
        assertEquals("FAILED", WorkflowScheduleExecutionModel.StateEnum.FAILED.toString());
        assertEquals("EXECUTED", WorkflowScheduleExecutionModel.StateEnum.EXECUTED.toString());

        assertEquals(WorkflowScheduleExecutionModel.StateEnum.POLLED,
                WorkflowScheduleExecutionModel.StateEnum.fromValue("POLLED"));
        assertEquals(WorkflowScheduleExecutionModel.StateEnum.FAILED,
                WorkflowScheduleExecutionModel.StateEnum.fromValue("FAILED"));
        assertEquals(WorkflowScheduleExecutionModel.StateEnum.EXECUTED,
                WorkflowScheduleExecutionModel.StateEnum.fromValue("EXECUTED"));
        assertNull(WorkflowScheduleExecutionModel.StateEnum.fromValue("INVALID"));
    }

    @Test
    void testEqualsAndHashCode() {
        // Setup
        WorkflowScheduleExecutionModel model1 = new WorkflowScheduleExecutionModel()
                .executionId("exec-123")
                .workflowName("test workflow");

        WorkflowScheduleExecutionModel model2 = new WorkflowScheduleExecutionModel()
                .executionId("exec-123")
                .workflowName("test workflow");

        WorkflowScheduleExecutionModel model3 = new WorkflowScheduleExecutionModel()
                .executionId("exec-456")
                .workflowName("different workflow");

        // Execute & Verify
        assertTrue(model1.equals(model2));
        assertEquals(model1.hashCode(), model2.hashCode());

        assertFalse(model1.equals(model3));
        assertNotEquals(model1.hashCode(), model3.hashCode());

        assertFalse(model1.equals(null));
        assertFalse(model1.equals("not a model"));
    }

    @Test
    void testToString() {
        // Setup
        WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel()
                .executionId("exec-123")
                .workflowName("test workflow");

        // Execute
        String result = model.toString();

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("exec-123"));
        assertTrue(result.contains("test workflow"));
    }
}