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

public class TestConductorUserPojoMethods {

    @Test
    public void testDefaultConstructor() {
        ConductorUser user = new ConductorUser();
        assertNull(user.getId());
        assertNull(user.getName());
        assertNull(user.getUuid());
        assertNull(user.isApplicationUser());
        assertNull(user.getGroups());
        assertNull(user.getRoles());
    }

    @Test
    public void testSettersAndGetters() {
        ConductorUser user = new ConductorUser();

        // Testing setters
        user.setId("test-id");
        user.setName("Test User");
        user.setUuid("test-uuid");
        user.setApplicationUser(true);

        List<Group> groups = new ArrayList<>();
        groups.add(new Group());
        user.setGroups(groups);

        List<Role> roles = new ArrayList<>();
        roles.add(new Role());
        user.setRoles(roles);

        // Testing getters
        assertEquals("test-id", user.getId());
        assertEquals("Test User", user.getName());
        assertEquals("test-uuid", user.getUuid());
        assertTrue(user.isApplicationUser());
        assertEquals(1, user.getGroups().size());
        assertEquals(1, user.getRoles().size());
    }

    @Test
    public void testBuilderPattern() {
        Group group = new Group();
        Role role = new Role();

        ConductorUser user = new ConductorUser()
                .id("test-id")
                .name("Test User")
                .uuid("test-uuid")
                .applicationUser(true)
                .addGroupsItem(group)
                .addRolesItem(role);

        assertEquals("test-id", user.getId());
        assertEquals("Test User", user.getName());
        assertEquals("test-uuid", user.getUuid());
        assertTrue(user.isApplicationUser());
        assertEquals(1, user.getGroups().size());
        assertEquals(group, user.getGroups().get(0));
        assertEquals(1, user.getRoles().size());
        assertEquals(role, user.getRoles().get(0));
    }

    @Test
    public void testGroupsSetterWithBuilderPattern() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group());
        groups.add(new Group());

        ConductorUser user = new ConductorUser().groups(groups);

        assertEquals(2, user.getGroups().size());
        assertSame(groups, user.getGroups());
    }

    @Test
    public void testRolesSetterWithBuilderPattern() {
        List<Role> roles = new ArrayList<>();
        roles.add(new Role());
        roles.add(new Role());

        ConductorUser user = new ConductorUser().roles(roles);

        assertEquals(2, user.getRoles().size());
        assertSame(roles, user.getRoles());
    }

    @Test
    public void testAddGroupsItemWithNullInitially() {
        ConductorUser user = new ConductorUser();
        assertNull(user.getGroups());

        Group group = new Group();
        user.addGroupsItem(group);

        assertNotNull(user.getGroups());
        assertEquals(1, user.getGroups().size());
        assertEquals(group, user.getGroups().get(0));
    }

    @Test
    public void testAddRolesItemWithNullInitially() {
        ConductorUser user = new ConductorUser();
        assertNull(user.getRoles());

        Role role = new Role();
        user.addRolesItem(role);

        assertNotNull(user.getRoles());
        assertEquals(1, user.getRoles().size());
        assertEquals(role, user.getRoles().get(0));
    }

    @Test
    public void testEqualsAndHashCode() {
        ConductorUser user1 = new ConductorUser()
                .id("test-id")
                .name("Test User")
                .uuid("test-uuid")
                .applicationUser(true);

        ConductorUser user2 = new ConductorUser()
                .id("test-id")
                .name("Test User")
                .uuid("test-uuid")
                .applicationUser(true);

        ConductorUser user3 = new ConductorUser()
                .id("different-id")
                .name("Test User")
                .uuid("test-uuid")
                .applicationUser(true);

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());

        assertNotEquals(user1, user3);
        assertNotEquals(user1.hashCode(), user3.hashCode());
    }

    @Test
    public void testToString() {
        ConductorUser user = new ConductorUser()
                .id("test-id")
                .name("Test User")
                .uuid("test-uuid")
                .applicationUser(true);

        String toString = user.toString();

        assertTrue(toString.contains("test-id"));
        assertTrue(toString.contains("Test User"));
        assertTrue(toString.contains("test-uuid"));
        assertTrue(toString.contains("true"));
    }
}