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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTargetRefPojoMethods {

    @Test
    void testDefaultConstructor() {
        TargetRef targetRef = new TargetRef();
        assertNull(targetRef.getId());
        assertNull(targetRef.getType());
    }

    @Test
    void testGettersAndSetters() {
        TargetRef targetRef = new TargetRef();

        targetRef.setId("test-id");
        assertEquals("test-id", targetRef.getId());

        targetRef.setType(TargetRef.TypeEnum.WORKFLOW_DEF);
        assertEquals(TargetRef.TypeEnum.WORKFLOW_DEF, targetRef.getType());
    }

    @Test
    void testFluentSetters() {
        TargetRef targetRef = new TargetRef()
                .id("test-id")
                .type(TargetRef.TypeEnum.TASK_DEF);

        assertEquals("test-id", targetRef.getId());
        assertEquals(TargetRef.TypeEnum.TASK_DEF, targetRef.getType());
    }

    @Test
    void testEqualsAndHashCode() {
        TargetRef targetRef1 = new TargetRef()
                .id("test-id")
                .type(TargetRef.TypeEnum.WORKFLOW_DEF);

        TargetRef targetRef2 = new TargetRef()
                .id("test-id")
                .type(TargetRef.TypeEnum.WORKFLOW_DEF);

        TargetRef targetRef3 = new TargetRef()
                .id("different-id")
                .type(TargetRef.TypeEnum.WORKFLOW_DEF);

        // Test equality
        assertEquals(targetRef1, targetRef2);
        assertNotEquals(targetRef1, targetRef3);

        // Test hashCode
        assertEquals(targetRef1.hashCode(), targetRef2.hashCode());
        assertNotEquals(targetRef1.hashCode(), targetRef3.hashCode());
    }

    @Test
    void testToString() {
        TargetRef targetRef = new TargetRef()
                .id("test-id")
                .type(TargetRef.TypeEnum.WORKFLOW_DEF);

        String toString = targetRef.toString();
        assertTrue(toString.contains("test-id"));
        assertTrue(toString.contains("WORKFLOW_DEF"));
    }

    @Test
    void testTypeEnum() {
        assertEquals("WORKFLOW_DEF", TargetRef.TypeEnum.WORKFLOW_DEF.getValue());
        assertEquals("TASK_DEF", TargetRef.TypeEnum.TASK_DEF.getValue());
        assertEquals("APPLICATION", TargetRef.TypeEnum.APPLICATION.getValue());
        assertEquals("USER", TargetRef.TypeEnum.USER.getValue());
        assertEquals("SECRET_NAME", TargetRef.TypeEnum.SECRET.getValue());
        assertEquals("TAG", TargetRef.TypeEnum.TAG.getValue());
        assertEquals("DOMAIN", TargetRef.TypeEnum.DOMAIN.getValue());

        assertEquals(TargetRef.TypeEnum.WORKFLOW_DEF, TargetRef.TypeEnum.fromValue("WORKFLOW_DEF"));
        assertEquals(TargetRef.TypeEnum.TASK_DEF, TargetRef.TypeEnum.fromValue("TASK_DEF"));
        assertEquals(TargetRef.TypeEnum.APPLICATION, TargetRef.TypeEnum.fromValue("APPLICATION"));
        assertEquals(TargetRef.TypeEnum.USER, TargetRef.TypeEnum.fromValue("USER"));
        assertEquals(TargetRef.TypeEnum.SECRET, TargetRef.TypeEnum.fromValue("SECRET_NAME"));
        assertEquals(TargetRef.TypeEnum.TAG, TargetRef.TypeEnum.fromValue("TAG"));
        assertEquals(TargetRef.TypeEnum.DOMAIN, TargetRef.TypeEnum.fromValue("DOMAIN"));

        assertNull(TargetRef.TypeEnum.fromValue("INVALID"));
    }
}