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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestRolePojoMethods {

    @Test
    void testEmptyConstructor() {
        Role role = new Role();
        assertNull(role.getName());
        assertNull(role.getPermissions());
    }

    @Test
    void testGetSetName() {
        Role role = new Role();
        assertNull(role.getName());

        role.setName("admin");
        assertEquals("admin", role.getName());

        role.setName("user");
        assertEquals("user", role.getName());
    }

    @Test
    void testNameBuilder() {
        Role role = new Role().name("admin");
        assertEquals("admin", role.getName());
    }

    @Test
    void testGetSetPermissions() {
        Role role = new Role();
        assertNull(role.getPermissions());

        List<Permission> permissions = new ArrayList<>();
        permissions.add(new Permission());

        role.setPermissions(permissions);
        assertEquals(permissions, role.getPermissions());

        List<Permission> newPermissions = new ArrayList<>();
        role.setPermissions(newPermissions);
        assertEquals(newPermissions, role.getPermissions());
    }

    @Test
    void testPermissionsBuilder() {
        List<Permission> permissions = new ArrayList<>();
        permissions.add(new Permission());

        Role role = new Role().permissions(permissions);
        assertEquals(permissions, role.getPermissions());
    }

    @Test
    void testAddPermissionsItem() {
        Role role = new Role();
        Permission permission1 = new Permission();
        Permission permission2 = new Permission();

        // Add to null permissions list
        role.addPermissionsItem(permission1);
        assertNotNull(role.getPermissions());
        assertEquals(1, role.getPermissions().size());
        assertTrue(role.getPermissions().contains(permission1));

        // Add to existing permissions list
        role.addPermissionsItem(permission2);
        assertEquals(2, role.getPermissions().size());
        assertTrue(role.getPermissions().contains(permission1));
        assertTrue(role.getPermissions().contains(permission2));
    }

    @Test
    void testFluentInterface() {
        Permission permission1 = new Permission();
        Permission permission2 = new Permission();

        Role role = new Role()
                .name("admin")
                .permissions(new ArrayList<>())
                .addPermissionsItem(permission1)
                .addPermissionsItem(permission2);

        assertEquals("admin", role.getName());
        assertEquals(2, role.getPermissions().size());
        assertTrue(role.getPermissions().contains(permission1));
        assertTrue(role.getPermissions().contains(permission2));
    }

    @Test
    void testEqualsAndHashCode() {
        Role role1 = new Role().name("admin");
        Role role2 = new Role().name("admin");
        Role role3 = new Role().name("user");

        // Test equals
        assertEquals(role1, role2);
        assertNotEquals(role1, role3);
        assertNotEquals(role1, null);
        assertNotEquals(role1, new Object());

        // Test hashCode
        assertEquals(role1.hashCode(), role2.hashCode());
    }

    @Test
    void testToString() {
        Role role = new Role().name("admin");
        String toString = role.toString();

        assertTrue(toString.contains("Role"));
        assertTrue(toString.contains("admin"));
    }
}