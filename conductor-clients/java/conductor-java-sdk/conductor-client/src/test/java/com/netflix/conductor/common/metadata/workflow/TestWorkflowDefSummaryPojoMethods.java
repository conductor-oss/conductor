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
package com.netflix.conductor.common.metadata.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkflowDefSummaryPojoMethods {

    @Test
    void testGettersAndSetters() {
        WorkflowDefSummary summary = new WorkflowDefSummary();

        summary.setName("testWorkflow");
        assertEquals("testWorkflow", summary.getName());

        summary.setVersion(2);
        assertEquals(2, summary.getVersion());

        Long createTime = System.currentTimeMillis();
        summary.setCreateTime(createTime);
        assertEquals(createTime, summary.getCreateTime());
    }

    @Test
    void testEquals() {
        WorkflowDefSummary summary1 = new WorkflowDefSummary();
        summary1.setName("testWorkflow");
        summary1.setVersion(2);
        summary1.setCreateTime(1000L);

        WorkflowDefSummary summary2 = new WorkflowDefSummary();
        summary2.setName("testWorkflow");
        summary2.setVersion(2);
        summary2.setCreateTime(2000L);

        WorkflowDefSummary summary3 = new WorkflowDefSummary();
        summary3.setName("otherWorkflow");
        summary3.setVersion(2);

        WorkflowDefSummary summary4 = new WorkflowDefSummary();
        summary4.setName("testWorkflow");
        summary4.setVersion(3);

        // Test equality based on name and version (createTime is ignored in equals)
        assertEquals(summary1, summary2);
        assertNotEquals(summary1, summary3);
        assertNotEquals(summary1, summary4);

        // Test reflexivity
        assertEquals(summary1, summary1);

        // Test null and different class
        assertNotEquals(summary1, null);
        assertNotEquals(summary1, "string");
    }

    @Test
    void testHashCode() {
        WorkflowDefSummary summary1 = new WorkflowDefSummary();
        summary1.setName("testWorkflow");
        summary1.setVersion(2);
        summary1.setCreateTime(1000L);

        WorkflowDefSummary summary2 = new WorkflowDefSummary();
        summary2.setName("testWorkflow");
        summary2.setVersion(2);
        summary2.setCreateTime(2000L);

        WorkflowDefSummary summary3 = new WorkflowDefSummary();
        summary3.setName("otherWorkflow");
        summary3.setVersion(2);

        // Objects that are equal should have the same hashCode
        assertEquals(summary1.hashCode(), summary2.hashCode());

        // Different objects likely have different hashCodes
        assertNotEquals(summary1.hashCode(), summary3.hashCode());
    }

    @Test
    void testToString() {
        WorkflowDefSummary summary = new WorkflowDefSummary();
        summary.setName("testWorkflow");
        summary.setVersion(2);

        String expectedString = "WorkflowDef{name='testWorkflow, version=2}";
        assertEquals(expectedString, summary.toString());
    }

    @Test
    void testCompareTo() {
        WorkflowDefSummary summary1 = new WorkflowDefSummary();
        summary1.setName("aWorkflow");
        summary1.setVersion(2);

        WorkflowDefSummary summary2 = new WorkflowDefSummary();
        summary2.setName("bWorkflow");
        summary2.setVersion(1);

        WorkflowDefSummary summary3 = new WorkflowDefSummary();
        summary3.setName("aWorkflow");
        summary3.setVersion(1);

        WorkflowDefSummary summary4 = new WorkflowDefSummary();
        summary4.setName("aWorkflow");
        summary4.setVersion(3);

        // Compare by name first
        assertTrue(summary1.compareTo(summary2) < 0);
        assertTrue(summary2.compareTo(summary1) > 0);

        // If names are equal, compare by version
        assertTrue(summary1.compareTo(summary3) > 0);
        assertTrue(summary3.compareTo(summary1) < 0);

        assertTrue(summary1.compareTo(summary4) < 0);
        assertTrue(summary4.compareTo(summary1) > 0);

        // Equal objects should return 0
        WorkflowDefSummary summary5 = new WorkflowDefSummary();
        summary5.setName("aWorkflow");
        summary5.setVersion(2);

        assertEquals(0, summary1.compareTo(summary5));
    }

    @Test
    void testDefaultValues() {
        WorkflowDefSummary summary = new WorkflowDefSummary();

        // Check default version value is 1
        assertEquals(1, summary.getVersion());

        // Name and createTime should be null by default
        assertNull(summary.getName());
        assertNull(summary.getCreateTime());
    }
}