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
package io.orkes.conductor.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSubjectRefPojoMethods {

    @Test
    void testDefaultConstructor() {
        SubjectRef subjectRef = new SubjectRef();
        assertNull(subjectRef.getId());
        assertNull(subjectRef.getType());
    }

    @Test
    void testSetAndGetId() {
        SubjectRef subjectRef = new SubjectRef();
        String id = "user123";
        subjectRef.setId(id);
        assertEquals(id, subjectRef.getId());
    }

    @Test
    void testIdFluentSetter() {
        String id = "user123";
        SubjectRef subjectRef = new SubjectRef().id(id);
        assertEquals(id, subjectRef.getId());
    }

    @Test
    void testSetAndGetType() {
        SubjectRef subjectRef = new SubjectRef();
        SubjectRef.TypeEnum type = SubjectRef.TypeEnum.USER;
        subjectRef.setType(type);
        assertEquals(type, subjectRef.getType());
    }

    @Test
    void testTypeFluentSetter() {
        SubjectRef.TypeEnum type = SubjectRef.TypeEnum.ROLE;
        SubjectRef subjectRef = new SubjectRef().type(type);
        assertEquals(type, subjectRef.getType());
    }

    @Test
    void testEquals() {
        SubjectRef ref1 = new SubjectRef().id("user1").type(SubjectRef.TypeEnum.USER);
        SubjectRef ref2 = new SubjectRef().id("user1").type(SubjectRef.TypeEnum.USER);
        SubjectRef ref3 = new SubjectRef().id("user2").type(SubjectRef.TypeEnum.USER);
        SubjectRef ref4 = new SubjectRef().id("user1").type(SubjectRef.TypeEnum.GROUP);

        assertEquals(ref1, ref2);
        assertNotEquals(ref1, ref3);
        assertNotEquals(ref1, ref4);
        assertNotEquals(ref1, null);
        assertNotEquals(ref1, new Object());
    }

    @Test
    void testHashCode() {
        SubjectRef ref1 = new SubjectRef().id("user1").type(SubjectRef.TypeEnum.USER);
        SubjectRef ref2 = new SubjectRef().id("user1").type(SubjectRef.TypeEnum.USER);
        SubjectRef ref3 = new SubjectRef().id("user2").type(SubjectRef.TypeEnum.ROLE);

        assertEquals(ref1.hashCode(), ref2.hashCode());
        assertNotEquals(ref1.hashCode(), ref3.hashCode());
    }

    @Test
    void testToString() {
        SubjectRef ref = new SubjectRef().id("user1").type(SubjectRef.TypeEnum.USER);
        String toString = ref.toString();

        assertTrue(toString.contains("user1"));
        assertTrue(toString.contains("USER"));
    }

    @Test
    void testNullValues() {
        SubjectRef ref1 = new SubjectRef();
        SubjectRef ref2 = new SubjectRef();

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void testAllTypeEnumValues() {
        SubjectRef refUser = new SubjectRef().type(SubjectRef.TypeEnum.USER);
        assertEquals(SubjectRef.TypeEnum.USER, refUser.getType());

        SubjectRef refRole = new SubjectRef().type(SubjectRef.TypeEnum.ROLE);
        assertEquals(SubjectRef.TypeEnum.ROLE, refRole.getType());

        SubjectRef refGroup = new SubjectRef().type(SubjectRef.TypeEnum.GROUP);
        assertEquals(SubjectRef.TypeEnum.GROUP, refGroup.getType());

        SubjectRef refTag = new SubjectRef().type(SubjectRef.TypeEnum.TAG);
        assertEquals(SubjectRef.TypeEnum.TAG, refTag.getType());
    }

    @Test
    void testTypeEnumGetValue() {
        assertEquals("USER", SubjectRef.TypeEnum.USER.getValue());
        assertEquals("ROLE", SubjectRef.TypeEnum.ROLE.getValue());
        assertEquals("GROUP", SubjectRef.TypeEnum.GROUP.getValue());
        assertEquals("TAG", SubjectRef.TypeEnum.TAG.getValue());
    }

    @Test
    void testTypeEnumToString() {
        assertEquals("USER", SubjectRef.TypeEnum.USER.toString());
        assertEquals("ROLE", SubjectRef.TypeEnum.ROLE.toString());
        assertEquals("GROUP", SubjectRef.TypeEnum.GROUP.toString());
        assertEquals("TAG", SubjectRef.TypeEnum.TAG.toString());
    }

    @Test
    void testTypeEnumFromValue() {
        assertEquals(SubjectRef.TypeEnum.USER, SubjectRef.TypeEnum.fromValue("USER"));
        assertEquals(SubjectRef.TypeEnum.ROLE, SubjectRef.TypeEnum.fromValue("ROLE"));
        assertEquals(SubjectRef.TypeEnum.GROUP, SubjectRef.TypeEnum.fromValue("GROUP"));
        assertEquals(SubjectRef.TypeEnum.TAG, SubjectRef.TypeEnum.fromValue("TAG"));
        assertNull(SubjectRef.TypeEnum.fromValue("INVALID"));
    }

    @Test
    void testTypeEnumFromInvalidValue() {
        assertNull(SubjectRef.TypeEnum.fromValue("INVALID"));
    }
}