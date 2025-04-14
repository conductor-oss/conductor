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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestUpsertUserRequestPojoMethods {

    @Test
    void testDefaultConstructor() {
        UpsertUserRequest request = new UpsertUserRequest();
        assertNull(request.getGroups());
        assertNull(request.getName());
        assertNull(request.getRoles());
    }

    @Test
    void testGroups() {
        UpsertUserRequest request = new UpsertUserRequest();
        List<String> groups = Arrays.asList("group1", "group2");

        // Test setter
        request.setGroups(groups);
        assertEquals(groups, request.getGroups());

        // Test fluent setter
        UpsertUserRequest result = request.groups(Arrays.asList("group3", "group4"));
        assertSame(request, result);
        assertEquals(Arrays.asList("group3", "group4"), request.getGroups());
    }

    @Test
    void testAddGroupsItem() {
        UpsertUserRequest request = new UpsertUserRequest();

        // Add to null list
        UpsertUserRequest result = request.addGroupsItem("group1");
        assertSame(request, result);
        assertEquals(1, request.getGroups().size());
        assertEquals("group1", request.getGroups().get(0));

        // Add to existing list
        request.addGroupsItem("group2");
        assertEquals(2, request.getGroups().size());
        assertEquals("group2", request.getGroups().get(1));
    }

    @Test
    void testName() {
        UpsertUserRequest request = new UpsertUserRequest();

        // Test setter
        request.setName("John Doe");
        assertEquals("John Doe", request.getName());

        // Test fluent setter
        UpsertUserRequest result = request.name("Jane Doe");
        assertSame(request, result);
        assertEquals("Jane Doe", request.getName());
    }

    @Test
    void testRoles() {
        UpsertUserRequest request = new UpsertUserRequest();
        List<UpsertUserRequest.RolesEnum> roles = Arrays.asList(
                UpsertUserRequest.RolesEnum.ADMIN,
                UpsertUserRequest.RolesEnum.USER
        );

        // Test setter
        request.setRoles(roles);
        assertEquals(roles, request.getRoles());

        // Test fluent setter
        List<UpsertUserRequest.RolesEnum> newRoles = Arrays.asList(
                UpsertUserRequest.RolesEnum.WORKER,
                UpsertUserRequest.RolesEnum.METADATA_MANAGER
        );
        UpsertUserRequest result = request.roles(newRoles);
        assertSame(request, result);
        assertEquals(newRoles, request.getRoles());
    }

    @Test
    void testAddRolesItem() {
        UpsertUserRequest request = new UpsertUserRequest();

        // Add to null list
        UpsertUserRequest result = request.addRolesItem(UpsertUserRequest.RolesEnum.ADMIN);
        assertSame(request, result);
        assertEquals(1, request.getRoles().size());
        assertEquals(UpsertUserRequest.RolesEnum.ADMIN, request.getRoles().get(0));

        // Add to existing list
        request.addRolesItem(UpsertUserRequest.RolesEnum.USER);
        assertEquals(2, request.getRoles().size());
        assertEquals(UpsertUserRequest.RolesEnum.USER, request.getRoles().get(1));
    }

    @Test
    void testEqualsAndHashCode() {
        UpsertUserRequest request1 = new UpsertUserRequest()
                .name("John Doe")
                .groups(Arrays.asList("group1", "group2"))
                .roles(Arrays.asList(UpsertUserRequest.RolesEnum.ADMIN));

        UpsertUserRequest request2 = new UpsertUserRequest()
                .name("John Doe")
                .groups(Arrays.asList("group1", "group2"))
                .roles(Arrays.asList(UpsertUserRequest.RolesEnum.ADMIN));

        UpsertUserRequest request3 = new UpsertUserRequest()
                .name("Jane Doe")
                .groups(Arrays.asList("group1", "group2"))
                .roles(Arrays.asList(UpsertUserRequest.RolesEnum.ADMIN));

        // Test equals
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);

        // Test hashCode
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    void testToString() {
        UpsertUserRequest request = new UpsertUserRequest()
                .name("John Doe")
                .groups(Arrays.asList("group1", "group2"))
                .roles(Arrays.asList(UpsertUserRequest.RolesEnum.ADMIN));

        String toString = request.toString();

        // Verify toString contains key fields
        assertTrue(toString.contains("John Doe"));
        assertTrue(toString.contains("group1"));
        assertTrue(toString.contains("group2"));
        assertTrue(toString.contains("ADMIN"));
    }

    @Test
    void testRolesEnum() {
        // Test getValue
        assertEquals("ADMIN", UpsertUserRequest.RolesEnum.ADMIN.getValue());
        assertEquals("USER", UpsertUserRequest.RolesEnum.USER.getValue());

        // Test toString
        assertEquals("ADMIN", UpsertUserRequest.RolesEnum.ADMIN.toString());
        assertEquals("USER", UpsertUserRequest.RolesEnum.USER.toString());

        // Test fromValue
        assertEquals(UpsertUserRequest.RolesEnum.ADMIN, UpsertUserRequest.RolesEnum.fromValue("ADMIN"));
        assertEquals(UpsertUserRequest.RolesEnum.USER, UpsertUserRequest.RolesEnum.fromValue("USER"));
        assertEquals(UpsertUserRequest.RolesEnum.WORKER, UpsertUserRequest.RolesEnum.fromValue("WORKER"));
        assertEquals(UpsertUserRequest.RolesEnum.METADATA_MANAGER, UpsertUserRequest.RolesEnum.fromValue("METADATA_MANAGER"));
        assertEquals(UpsertUserRequest.RolesEnum.WORKFLOW_MANAGER, UpsertUserRequest.RolesEnum.fromValue("WORKFLOW_MANAGER"));

        // Test invalid value
        assertNull(UpsertUserRequest.RolesEnum.fromValue("INVALID"));
    }
}