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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkflowStatusPojoMethods {

    @Test
    void testConstructor() {
        WorkflowStatus status = new WorkflowStatus();
        assertNotNull(status);
        assertNull(status.getCorrelationId());
        assertNull(status.getOutput());
        assertNull(status.getStatus());
        assertNull(status.getVariables());
        assertNull(status.getWorkflowId());
    }

    @Test
    void testCorrelationId() {
        WorkflowStatus status = new WorkflowStatus();

        // Test setter
        status.setCorrelationId("test-correlation-id");
        assertEquals("test-correlation-id", status.getCorrelationId());

        // Test builder pattern
        WorkflowStatus statusFromBuilder = new WorkflowStatus().correlationId("builder-correlation-id");
        assertEquals("builder-correlation-id", statusFromBuilder.getCorrelationId());
    }

    @Test
    void testOutput() {
        WorkflowStatus status = new WorkflowStatus();

        // Test setter
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");
        status.setOutput(output);
        assertEquals(output, status.getOutput());

        // Test builder pattern
        Map<String, Object> builderOutput = new HashMap<>();
        builderOutput.put("key2", "value2");
        WorkflowStatus statusFromBuilder = new WorkflowStatus().output(builderOutput);
        assertEquals(builderOutput, statusFromBuilder.getOutput());

        // Test putOutputItem
        WorkflowStatus statusWithPut = new WorkflowStatus();
        statusWithPut.putOutputItem("key3", "value3");
        Map<String, Object> expectedOutput = new HashMap<>();
        expectedOutput.put("key3", "value3");
        assertEquals(expectedOutput, statusWithPut.getOutput());

        // Test putOutputItem with existing output
        statusWithPut.putOutputItem("key4", "value4");
        expectedOutput.put("key4", "value4");
        assertEquals(expectedOutput, statusWithPut.getOutput());
    }

    @Test
    void testStatus() {
        WorkflowStatus status = new WorkflowStatus();

        // Test setter
        status.setStatus(WorkflowStatus.StatusEnum.RUNNING);
        assertEquals(WorkflowStatus.StatusEnum.RUNNING, status.getStatus());

        // Test builder pattern
        WorkflowStatus statusFromBuilder = new WorkflowStatus().status(WorkflowStatus.StatusEnum.COMPLETED);
        assertEquals(WorkflowStatus.StatusEnum.COMPLETED, statusFromBuilder.getStatus());
    }

    @Test
    void testVariables() {
        WorkflowStatus status = new WorkflowStatus();

        // Test setter
        Map<String, Object> variables = new HashMap<>();
        variables.put("var1", "val1");
        status.setVariables(variables);
        assertEquals(variables, status.getVariables());

        // Test builder pattern
        Map<String, Object> builderVariables = new HashMap<>();
        builderVariables.put("var2", "val2");
        WorkflowStatus statusFromBuilder = new WorkflowStatus().variables(builderVariables);
        assertEquals(builderVariables, statusFromBuilder.getVariables());

        // Test putVariablesItem
        WorkflowStatus statusWithPut = new WorkflowStatus();
        statusWithPut.putVariablesItem("var3", "val3");
        Map<String, Object> expectedVariables = new HashMap<>();
        expectedVariables.put("var3", "val3");
        assertEquals(expectedVariables, statusWithPut.getVariables());

        // Test putVariablesItem with existing variables
        statusWithPut.putVariablesItem("var4", "val4");
        expectedVariables.put("var4", "val4");
        assertEquals(expectedVariables, statusWithPut.getVariables());
    }

    @Test
    void testWorkflowId() {
        WorkflowStatus status = new WorkflowStatus();

        // Test setter
        status.setWorkflowId("test-workflow-id");
        assertEquals("test-workflow-id", status.getWorkflowId());

        // Test builder pattern
        WorkflowStatus statusFromBuilder = new WorkflowStatus().workflowId("builder-workflow-id");
        assertEquals("builder-workflow-id", statusFromBuilder.getWorkflowId());
    }

    @Test
    void testStatusEnum() {
        // Test getValue
        assertEquals("RUNNING", WorkflowStatus.StatusEnum.RUNNING.getValue());
        assertEquals("COMPLETED", WorkflowStatus.StatusEnum.COMPLETED.getValue());
        assertEquals("FAILED", WorkflowStatus.StatusEnum.FAILED.getValue());
        assertEquals("TIMED_OUT", WorkflowStatus.StatusEnum.TIMED_OUT.getValue());
        assertEquals("TERMINATED", WorkflowStatus.StatusEnum.TERMINATED.getValue());
        assertEquals("PAUSED", WorkflowStatus.StatusEnum.PAUSED.getValue());

        // Test toString
        assertEquals("RUNNING", WorkflowStatus.StatusEnum.RUNNING.toString());
        assertEquals("COMPLETED", WorkflowStatus.StatusEnum.COMPLETED.toString());

        // Test fromValue
        assertEquals(WorkflowStatus.StatusEnum.RUNNING, WorkflowStatus.StatusEnum.fromValue("RUNNING"));
        assertEquals(WorkflowStatus.StatusEnum.COMPLETED, WorkflowStatus.StatusEnum.fromValue("COMPLETED"));
        assertNull(WorkflowStatus.StatusEnum.fromValue("INVALID"));
    }

    @Test
    void testEqualsAndHashCode() {
        WorkflowStatus status1 = new WorkflowStatus()
                .correlationId("correlation1")
                .workflowId("workflow1")
                .status(WorkflowStatus.StatusEnum.RUNNING);

        WorkflowStatus status2 = new WorkflowStatus()
                .correlationId("correlation1")
                .workflowId("workflow1")
                .status(WorkflowStatus.StatusEnum.RUNNING);

        WorkflowStatus status3 = new WorkflowStatus()
                .correlationId("correlation2")
                .workflowId("workflow1")
                .status(WorkflowStatus.StatusEnum.RUNNING);

        // Test equals
        assertEquals(status1, status2);
        assertNotEquals(status1, status3);

        // Test hashCode
        assertEquals(status1.hashCode(), status2.hashCode());
        assertNotEquals(status1.hashCode(), status3.hashCode());
    }

    @Test
    void testToString() {
        WorkflowStatus status = new WorkflowStatus()
                .correlationId("correlation1")
                .workflowId("workflow1")
                .status(WorkflowStatus.StatusEnum.RUNNING);

        String toString = status.toString();

        // Just verify it contains expected fields without being too rigid on format
        assertTrue(toString.contains("correlationId"));
        assertTrue(toString.contains("correlation1"));
        assertTrue(toString.contains("workflowId"));
        assertTrue(toString.contains("workflow1"));
        assertTrue(toString.contains("RUNNING"));
    }
}