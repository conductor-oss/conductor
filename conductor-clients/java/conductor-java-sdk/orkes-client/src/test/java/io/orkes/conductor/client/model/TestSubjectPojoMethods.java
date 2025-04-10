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

class TestSubjectPojoMethods {

    @Test
    void testDefaultConstructor() {
        Subject subject = new Subject();
        assertNull(subject.getId());
        assertNull(subject.getType());
    }

    @Test
    void testGetSetId() {
        Subject subject = new Subject();
        assertNull(subject.getId());

        subject.setId("user123");
        assertEquals("user123", subject.getId());

        subject.setId("role456");
        assertEquals("role456", subject.getId());
    }

    @Test
    void testGetSetType() {
        Subject subject = new Subject();
        assertNull(subject.getType());

        subject.setType(Subject.TypeEnum.USER);
        assertEquals(Subject.TypeEnum.USER, subject.getType());

        subject.setType(Subject.TypeEnum.ROLE);
        assertEquals(Subject.TypeEnum.ROLE, subject.getType());

        subject.setType(Subject.TypeEnum.GROUP);
        assertEquals(Subject.TypeEnum.GROUP, subject.getType());
    }

    @Test
    void testIdFluentApi() {
        Subject subject = new Subject();
        Subject result = subject.id("user123");

        // Test that it returns itself for chaining
        assertSame(subject, result);
        assertEquals("user123", subject.getId());
    }

    @Test
    void testTypeFluentApi() {
        Subject subject = new Subject();
        Subject result = subject.type(Subject.TypeEnum.USER);

        // Test that it returns itself for chaining
        assertSame(subject, result);
        assertEquals(Subject.TypeEnum.USER, subject.getType());
    }

    @Test
    void testChainedFluentApi() {
        Subject subject = new Subject()
                .id("admin")
                .type(Subject.TypeEnum.ROLE);

        assertEquals("admin", subject.getId());
        assertEquals(Subject.TypeEnum.ROLE, subject.getType());
    }

    @Test
    void testTypeEnumFromValue() {
        assertEquals(Subject.TypeEnum.USER, Subject.TypeEnum.fromValue("USER"));
        assertEquals(Subject.TypeEnum.ROLE, Subject.TypeEnum.fromValue("ROLE"));
        assertEquals(Subject.TypeEnum.GROUP, Subject.TypeEnum.fromValue("GROUP"));
        assertNull(Subject.TypeEnum.fromValue("INVALID"));
    }

    @Test
    void testTypeEnumGetValue() {
        assertEquals("USER", Subject.TypeEnum.USER.getValue());
        assertEquals("ROLE", Subject.TypeEnum.ROLE.getValue());
        assertEquals("GROUP", Subject.TypeEnum.GROUP.getValue());
    }

    @Test
    void testTypeEnumToString() {
        assertEquals("USER", Subject.TypeEnum.USER.toString());
        assertEquals("ROLE", Subject.TypeEnum.ROLE.toString());
        assertEquals("GROUP", Subject.TypeEnum.GROUP.toString());
    }

    @Test
    void testEqualsAndHashCode() {
        Subject subject1 = new Subject().id("user1").type(Subject.TypeEnum.USER);
        Subject subject2 = new Subject().id("user1").type(Subject.TypeEnum.USER);
        Subject subject3 = new Subject().id("user2").type(Subject.TypeEnum.USER);
        Subject subject4 = new Subject().id("user1").type(Subject.TypeEnum.ROLE);

        // Test equals
        assertEquals(subject1, subject2);
        assertNotEquals(subject1, subject3);
        assertNotEquals(subject1, subject4);

        // Test hashCode
        assertEquals(subject1.hashCode(), subject2.hashCode());
        assertNotEquals(subject1.hashCode(), subject3.hashCode());
        assertNotEquals(subject1.hashCode(), subject4.hashCode());
    }

    @Test
    void testToString() {
        Subject subject = new Subject().id("user1").type(Subject.TypeEnum.USER);
        String toString = subject.toString();

        // Verify toString contains the field values
        assertTrue(toString.contains("user1"));
        assertTrue(toString.contains("USER"));
    }
}