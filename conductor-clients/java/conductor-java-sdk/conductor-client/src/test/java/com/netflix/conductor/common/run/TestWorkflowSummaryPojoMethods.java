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
package com.netflix.conductor.common.run;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.run.Workflow.WorkflowStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestWorkflowSummaryPojoMethods {

    private WorkflowSummary workflowSummary;
    private static final String WORKFLOW_TYPE = "TestWorkflow";
    private static final int VERSION = 1;
    private static final String WORKFLOW_ID = "test-workflow-id";
    private static final String CORRELATION_ID = "test-correlation-id";
    private static final String START_TIME = "2023-01-01T00:00:00.000Z";
    private static final String UPDATE_TIME = "2023-01-01T01:00:00.000Z";
    private static final String END_TIME = "2023-01-01T02:00:00.000Z";
    private static final WorkflowStatus STATUS = WorkflowStatus.COMPLETED;
    private static final String INPUT = "{\"key\":\"value\"}";
    private static final String OUTPUT = "{\"result\":\"success\"}";
    private static final String REASON_FOR_INCOMPLETION = "Test reason";
    private static final long EXECUTION_TIME = 7200000;
    private static final String EVENT = "TEST_EVENT";
    private static final String FAILED_REFERENCE_TASK_NAMES = "task1,task2";
    private static final Set<String> FAILED_TASK_NAMES = new HashSet<>(Arrays.asList("task1", "task2"));
    private static final String EXTERNAL_INPUT_PATH = "/input/path";
    private static final String EXTERNAL_OUTPUT_PATH = "/output/path";
    private static final int PRIORITY = 10;
    private static final String CREATED_BY = "test-user";

    @BeforeEach
    void setUp() {
        workflowSummary = new WorkflowSummary();
        workflowSummary.setWorkflowType(WORKFLOW_TYPE);
        workflowSummary.setVersion(VERSION);
        workflowSummary.setWorkflowId(WORKFLOW_ID);
        workflowSummary.setCorrelationId(CORRELATION_ID);
        workflowSummary.setStartTime(START_TIME);
        workflowSummary.setUpdateTime(UPDATE_TIME);
        workflowSummary.setEndTime(END_TIME);
        workflowSummary.setStatus(STATUS);
        workflowSummary.setInput(INPUT);
        workflowSummary.setOutput(OUTPUT);
        workflowSummary.setReasonForIncompletion(REASON_FOR_INCOMPLETION);
        workflowSummary.setExecutionTime(EXECUTION_TIME);
        workflowSummary.setEvent(EVENT);
        workflowSummary.setFailedReferenceTaskNames(FAILED_REFERENCE_TASK_NAMES);
        workflowSummary.setFailedTaskNames(FAILED_TASK_NAMES);
        workflowSummary.setExternalInputPayloadStoragePath(EXTERNAL_INPUT_PATH);
        workflowSummary.setExternalOutputPayloadStoragePath(EXTERNAL_OUTPUT_PATH);
        workflowSummary.setPriority(PRIORITY);
        workflowSummary.setCreatedBy(CREATED_BY);
    }

    @Test
    void testDefaultConstructor() {
        WorkflowSummary ws = new WorkflowSummary();
        assertNotNull(ws);
    }

    @Test
    void testWorkflowConstructor() {
        // Create a mock Workflow
        Workflow workflow = mock(Workflow.class);

        // Set up the mock to return values
        when(workflow.getWorkflowName()).thenReturn(WORKFLOW_TYPE);
        when(workflow.getWorkflowVersion()).thenReturn(VERSION);
        when(workflow.getWorkflowId()).thenReturn(WORKFLOW_ID);
        when(workflow.getCorrelationId()).thenReturn(CORRELATION_ID);
        when(workflow.getCreateTime()).thenReturn(new Date().getTime());
        when(workflow.getEndTime()).thenReturn(new Date().getTime() + 1000);
        when(workflow.getStartTime()).thenReturn(new Date().getTime() - 1000);
        when(workflow.getUpdateTime()).thenReturn(new Date().getTime());
        when(workflow.getStatus()).thenReturn(STATUS);
        when(workflow.getReasonForIncompletion()).thenReturn(REASON_FOR_INCOMPLETION);
        when(workflow.getEvent()).thenReturn(EVENT);
        when(workflow.getFailedTaskNames()).thenReturn(FAILED_TASK_NAMES);
        when(workflow.getExternalInputPayloadStoragePath()).thenReturn(EXTERNAL_INPUT_PATH);
        when(workflow.getExternalOutputPayloadStoragePath()).thenReturn(EXTERNAL_OUTPUT_PATH);
        when(workflow.getPriority()).thenReturn(PRIORITY);

        // Create a WorkflowSummary from the mock
        WorkflowSummary summary = new WorkflowSummary(workflow);

        // Verify the values were properly set
        assertEquals(WORKFLOW_TYPE, summary.getWorkflowType());
        assertEquals(VERSION, summary.getVersion());
        assertEquals(WORKFLOW_ID, summary.getWorkflowId());
        assertEquals(CORRELATION_ID, summary.getCorrelationId());
        assertNotNull(summary.getStartTime());
        assertNotNull(summary.getUpdateTime());
        assertNotNull(summary.getEndTime());
        assertEquals(STATUS, summary.getStatus());
        assertNotNull(summary.getInput());
        assertNotNull(summary.getOutput());
        assertEquals(REASON_FOR_INCOMPLETION, summary.getReasonForIncompletion());
        assertTrue(summary.getExecutionTime() >= 0);
        assertEquals(EVENT, summary.getEvent());
        assertNotNull(summary.getFailedReferenceTaskNames());
        assertEquals(FAILED_TASK_NAMES, summary.getFailedTaskNames());
        assertEquals(EXTERNAL_INPUT_PATH, summary.getExternalInputPayloadStoragePath());
        assertEquals(EXTERNAL_OUTPUT_PATH, summary.getExternalOutputPayloadStoragePath());
        assertEquals(PRIORITY, summary.getPriority());
    }

    @Test
    void testGettersAndSetters() {
        assertEquals(WORKFLOW_TYPE, workflowSummary.getWorkflowType());
        assertEquals(VERSION, workflowSummary.getVersion());
        assertEquals(WORKFLOW_ID, workflowSummary.getWorkflowId());
        assertEquals(CORRELATION_ID, workflowSummary.getCorrelationId());
        assertEquals(START_TIME, workflowSummary.getStartTime());
        assertEquals(UPDATE_TIME, workflowSummary.getUpdateTime());
        assertEquals(END_TIME, workflowSummary.getEndTime());
        assertEquals(STATUS, workflowSummary.getStatus());
        assertEquals(INPUT, workflowSummary.getInput());
        assertEquals(OUTPUT, workflowSummary.getOutput());
        assertEquals(REASON_FOR_INCOMPLETION, workflowSummary.getReasonForIncompletion());
        assertEquals(EXECUTION_TIME, workflowSummary.getExecutionTime());
        assertEquals(EVENT, workflowSummary.getEvent());
        assertEquals(FAILED_REFERENCE_TASK_NAMES, workflowSummary.getFailedReferenceTaskNames());
        assertEquals(FAILED_TASK_NAMES, workflowSummary.getFailedTaskNames());
        assertEquals(EXTERNAL_INPUT_PATH, workflowSummary.getExternalInputPayloadStoragePath());
        assertEquals(EXTERNAL_OUTPUT_PATH, workflowSummary.getExternalOutputPayloadStoragePath());
        assertEquals(PRIORITY, workflowSummary.getPriority());
        assertEquals(CREATED_BY, workflowSummary.getCreatedBy());
    }

    @Test
    void testInputOutputSize() {
        assertEquals(INPUT.length(), workflowSummary.getInputSize());
        assertEquals(OUTPUT.length(), workflowSummary.getOutputSize());

        // Test with null values
        WorkflowSummary ws = new WorkflowSummary();
        assertEquals(0, ws.getInputSize());
        assertEquals(0, ws.getOutputSize());
    }

    @Test
    void testEqualsAndHashCode() {
        // Same values
        WorkflowSummary ws1 = createWorkflowSummary();
        WorkflowSummary ws2 = createWorkflowSummary();

        // Verify equals and hashCode
        assertEquals(ws1, ws2);
        assertEquals(ws1.hashCode(), ws2.hashCode());

        // Same object reference
        assertEquals(ws1, ws1);

        // Different types
        assertNotEquals(ws1, "Not a WorkflowSummary");

        // Null
        assertNotEquals(ws1, null);

        // Test with different values for fields used in equals/hashCode
        WorkflowSummary different;

        // Different workflow type
        different = createWorkflowSummary();
        different.setWorkflowType("DifferentType");
        assertNotEquals(ws1, different);

        // Different version
        different = createWorkflowSummary();
        different.setVersion(VERSION + 1);
        assertNotEquals(ws1, different);

        // Different workflow ID
        different = createWorkflowSummary();
        different.setWorkflowId("different-id");
        assertNotEquals(ws1, different);

        // Different correlation ID
        different = createWorkflowSummary();
        different.setCorrelationId("different-correlation");
        assertNotEquals(ws1, different);

        // Different start time
        different = createWorkflowSummary();
        different.setStartTime("2023-02-01T00:00:00.000Z");
        assertNotEquals(ws1, different);

        // Different update time
        different = createWorkflowSummary();
        different.setUpdateTime("2023-02-01T01:00:00.000Z");
        assertNotEquals(ws1, different);

        // Different end time
        different = createWorkflowSummary();
        different.setEndTime("2023-02-01T02:00:00.000Z");
        assertNotEquals(ws1, different);

        // Different status
        different = createWorkflowSummary();
        different.setStatus(WorkflowStatus.FAILED);
        assertNotEquals(ws1, different);

        // Different reason for incompletion
        different = createWorkflowSummary();
        different.setReasonForIncompletion("Different reason");
        assertNotEquals(ws1, different);

        // Different execution time
        different = createWorkflowSummary();
        different.setExecutionTime(EXECUTION_TIME + 1000);
        assertNotEquals(ws1, different);

        // Different event
        different = createWorkflowSummary();
        different.setEvent("DIFFERENT_EVENT");
        assertNotEquals(ws1, different);

        // Different priority
        different = createWorkflowSummary();
        different.setPriority(PRIORITY + 1);
        assertNotEquals(ws1, different);

        // Different created by
        different = createWorkflowSummary();
        different.setCreatedBy("different-user");
        assertNotEquals(ws1, different);
    }

    private WorkflowSummary createWorkflowSummary() {
        WorkflowSummary ws = new WorkflowSummary();
        ws.setWorkflowType(WORKFLOW_TYPE);
        ws.setVersion(VERSION);
        ws.setWorkflowId(WORKFLOW_ID);
        ws.setCorrelationId(CORRELATION_ID);
        ws.setStartTime(START_TIME);
        ws.setUpdateTime(UPDATE_TIME);
        ws.setEndTime(END_TIME);
        ws.setStatus(STATUS);
        ws.setReasonForIncompletion(REASON_FOR_INCOMPLETION);
        ws.setExecutionTime(EXECUTION_TIME);
        ws.setEvent(EVENT);
        ws.setPriority(PRIORITY);
        ws.setCreatedBy(CREATED_BY);
        return ws;
    }
}