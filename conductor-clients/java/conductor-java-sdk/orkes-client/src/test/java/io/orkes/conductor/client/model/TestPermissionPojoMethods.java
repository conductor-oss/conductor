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

public class TestPermissionPojoMethods {

    @Test
    public void testConstructor() {
        Permission permission = new Permission();
        assertNull(permission.getName());
    }

    @Test
    public void testGetAndSetName() {
        Permission permission = new Permission();
        assertNull(permission.getName());

        permission.setName("read");
        assertEquals("read", permission.getName());

        permission.setName("write");
        assertEquals("write", permission.getName());
    }

    @Test
    public void testNameBuilder() {
        Permission permission = new Permission().name("execute");
        assertEquals("execute", permission.getName());
    }

    @Test
    public void testEqualsAndHashCode() {
        Permission permission1 = new Permission().name("read");
        Permission permission2 = new Permission().name("read");
        Permission permission3 = new Permission().name("write");

        // Test equals
        assertEquals(permission1, permission2);
        assertNotEquals(permission1, permission3);
        assertNotEquals(permission1, null);
        assertNotEquals(permission1, "read");

        // Test hash code
        assertEquals(permission1.hashCode(), permission2.hashCode());
    }

    @Test
    public void testToString() {
        Permission permission = new Permission().name("read");
        String toString = permission.toString();

        assertTrue(toString.contains("Permission"));
        assertTrue(toString.contains("name=read"));
    }
}