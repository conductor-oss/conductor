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

import static org.junit.jupiter.api.Assertions.*;

public class TestTerminateWorkflowPojoMethods {

    @Test
    public void testNoArgsConstructor() {
        TerminateWorkflow terminateWorkflow = new TerminateWorkflow();
        assertNull(terminateWorkflow.getTerminationReason());
        assertNull(terminateWorkflow.getWorkflowId());
    }

    @Test
    public void testGetSetTerminationReason() {
        TerminateWorkflow terminateWorkflow = new TerminateWorkflow();
        assertNull(terminateWorkflow.getTerminationReason());

        String terminationReason = "Test termination reason";
        terminateWorkflow.setTerminationReason(terminationReason);
        assertEquals(terminationReason, terminateWorkflow.getTerminationReason());
    }

    @Test
    public void testGetSetWorkflowId() {
        TerminateWorkflow terminateWorkflow = new TerminateWorkflow();
        assertNull(terminateWorkflow.getWorkflowId());

        String workflowId = "workflow-123";
        terminateWorkflow.setWorkflowId(workflowId);
        assertEquals(workflowId, terminateWorkflow.getWorkflowId());
    }

    @Test
    public void testTerminationReasonBuilderMethod() {
        String terminationReason = "Test termination reason";
        TerminateWorkflow terminateWorkflow = new TerminateWorkflow().terminationReason(terminationReason);

        assertEquals(terminationReason, terminateWorkflow.getTerminationReason());
    }

    @Test
    public void testWorkflowIdBuilderMethod() {
        String workflowId = "workflow-123";
        TerminateWorkflow terminateWorkflow = new TerminateWorkflow().workflowId(workflowId);

        assertEquals(workflowId, terminateWorkflow.getWorkflowId());
    }

    @Test
    public void testChainedBuilderMethods() {
        String terminationReason = "Test termination reason";
        String workflowId = "workflow-123";

        TerminateWorkflow terminateWorkflow = new TerminateWorkflow()
                .terminationReason(terminationReason)
                .workflowId(workflowId);

        assertEquals(terminationReason, terminateWorkflow.getTerminationReason());
        assertEquals(workflowId, terminateWorkflow.getWorkflowId());
    }

    @Test
    public void testEqualsAndHashCode() {
        TerminateWorkflow tw1 = new TerminateWorkflow()
                .terminationReason("Test reason")
                .workflowId("workflow-123");

        TerminateWorkflow tw2 = new TerminateWorkflow()
                .terminationReason("Test reason")
                .workflowId("workflow-123");

        TerminateWorkflow tw3 = new TerminateWorkflow()
                .terminationReason("Different reason")
                .workflowId("workflow-123");

        TerminateWorkflow tw4 = new TerminateWorkflow()
                .terminationReason("Test reason")
                .workflowId("different-workflow-id");

        assertEquals(tw1, tw2);
        assertEquals(tw1.hashCode(), tw2.hashCode());

        assertNotEquals(tw1, tw3);
        assertNotEquals(tw1.hashCode(), tw3.hashCode());

        assertNotEquals(tw1, tw4);
        assertNotEquals(tw1.hashCode(), tw4.hashCode());
    }

    @Test
    public void testToString() {
        TerminateWorkflow terminateWorkflow = new TerminateWorkflow()
                .terminationReason("Test reason")
                .workflowId("workflow-123");

        String toString = terminateWorkflow.toString();

        assertTrue(toString.contains("terminationReason"));
        assertTrue(toString.contains("Test reason"));
        assertTrue(toString.contains("workflowId"));
        assertTrue(toString.contains("workflow-123"));
    }
}