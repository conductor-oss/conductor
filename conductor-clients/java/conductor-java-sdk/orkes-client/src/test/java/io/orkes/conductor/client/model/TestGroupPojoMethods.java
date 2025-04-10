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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGroupPojoMethods {

    @Test
    void testDefaultConstructor() {
        Group group = new Group();
        assertNull(group.getId());
        assertNull(group.getDescription());
        assertNull(group.getDefaultAccess());
        assertNull(group.getRoles());
    }

    @Test
    void testGetAndSetId() {
        Group group = new Group();
        String id = "testId";

        group.setId(id);
        assertEquals(id, group.getId());

        String newId = "newTestId";
        group.setId(newId);
        assertEquals(newId, group.getId());
    }

    @Test
    void testIdBuilder() {
        String id = "testId";
        Group group = new Group().id(id);

        assertEquals(id, group.getId());
    }

    @Test
    void testGetAndSetDescription() {
        Group group = new Group();
        String description = "Test Description";

        group.setDescription(description);
        assertEquals(description, group.getDescription());

        String newDescription = "New Test Description";
        group.setDescription(newDescription);
        assertEquals(newDescription, group.getDescription());
    }

    @Test
    void testDescriptionBuilder() {
        String description = "Test Description";
        Group group = new Group().description(description);

        assertEquals(description, group.getDescription());
    }

    @Test
    void testGetAndSetDefaultAccess() {
        Group group = new Group();
        Map<String, List<String>> defaultAccess = new HashMap<>();
        List<String> permissions = new ArrayList<>();
        permissions.add("READ");
        permissions.add("WRITE");
        defaultAccess.put("workflow", permissions);

        group.setDefaultAccess(defaultAccess);
        assertEquals(defaultAccess, group.getDefaultAccess());

        Map<String, List<String>> newDefaultAccess = new HashMap<>();
        List<String> newPermissions = new ArrayList<>();
        newPermissions.add("EXECUTE");
        newDefaultAccess.put("task", newPermissions);

        group.setDefaultAccess(newDefaultAccess);
        assertEquals(newDefaultAccess, group.getDefaultAccess());
    }

    @Test
    void testDefaultAccessBuilder() {
        Map<String, List<String>> defaultAccess = new HashMap<>();
        List<String> permissions = new ArrayList<>();
        permissions.add("READ");
        defaultAccess.put("workflow", permissions);

        Group group = new Group().defaultAccess(defaultAccess);
        assertEquals(defaultAccess, group.getDefaultAccess());
    }

    @Test
    void testPutDefaultAccessItem() {
        Group group = new Group();
        String key = "workflow";
        List<String> permissions = new ArrayList<>();
        permissions.add("READ");

        group.putDefaultAccessItem(key, permissions);
        assertNotNull(group.getDefaultAccess());
        assertEquals(permissions, group.getDefaultAccess().get(key));

        String newKey = "task";
        List<String> newPermissions = new ArrayList<>();
        newPermissions.add("EXECUTE");

        group.putDefaultAccessItem(newKey, newPermissions);
        assertEquals(newPermissions, group.getDefaultAccess().get(newKey));
        assertEquals(2, group.getDefaultAccess().size());
    }

    @Test
    void testGetAndSetRoles() {
        Group group = new Group();
        List<Role> roles = new ArrayList<>();
        Role role = new Role().name("role1");
        roles.add(role);

        group.setRoles(roles);
        assertEquals(roles, group.getRoles());

        List<Role> newRoles = new ArrayList<>();
        Role newRole = new Role().name("role2");
        newRoles.add(newRole);

        group.setRoles(newRoles);
        assertEquals(newRoles, group.getRoles());
    }

    @Test
    void testRolesBuilder() {
        List<Role> roles = new ArrayList<>();
        Role role = new Role().name("role1");
        roles.add(role);

        Group group = new Group().roles(roles);
        assertEquals(roles, group.getRoles());
    }

    @Test
    void testAddRolesItem() {
        Group group = new Group();
        Role role1 = new Role().name("role1");

        group.addRolesItem(role1);
        assertNotNull(group.getRoles());
        assertEquals(1, group.getRoles().size());
        assertEquals(role1, group.getRoles().get(0));

        Role role2 = new Role().name("role2");
        group.addRolesItem(role2);
        assertEquals(2, group.getRoles().size());
        assertEquals(role2, group.getRoles().get(1));
    }

    @Test
    void testInnerEnum() {
        assertEquals("CREATE", Group.InnerEnum.CREATE.getValue());
        assertEquals("READ", Group.InnerEnum.READ.getValue());
        assertEquals("UPDATE", Group.InnerEnum.UPDATE.getValue());
        assertEquals("DELETE", Group.InnerEnum.DELETE.getValue());
        assertEquals("EXECUTE", Group.InnerEnum.EXECUTE.getValue());

        assertEquals(Group.InnerEnum.CREATE, Group.InnerEnum.fromValue("CREATE"));
        assertEquals(Group.InnerEnum.READ, Group.InnerEnum.fromValue("READ"));
        assertEquals(Group.InnerEnum.UPDATE, Group.InnerEnum.fromValue("UPDATE"));
        assertEquals(Group.InnerEnum.DELETE, Group.InnerEnum.fromValue("DELETE"));
        assertEquals(Group.InnerEnum.EXECUTE, Group.InnerEnum.fromValue("EXECUTE"));
        assertNull(Group.InnerEnum.fromValue("INVALID"));

        assertEquals("CREATE", Group.InnerEnum.CREATE.toString());
    }

    @Test
    void testEqualsAndHashCode() {
        Group group1 = new Group()
                .id("testId")
                .description("Test Description");

        Group group2 = new Group()
                .id("testId")
                .description("Test Description");

        Group group3 = new Group()
                .id("differentId")
                .description("Different Description");

        assertTrue(group1.equals(group2));
        assertTrue(group2.equals(group1));
        assertFalse(group1.equals(group3));

        assertEquals(group1.hashCode(), group2.hashCode());
        assertNotEquals(group1.hashCode(), group3.hashCode());
    }

    @Test
    void testToString() {
        Group group = new Group()
                .id("testId")
                .description("Test Description");

        String toString = group.toString();
        assertTrue(toString.contains("testId"));
        assertTrue(toString.contains("Test Description"));
    }
}