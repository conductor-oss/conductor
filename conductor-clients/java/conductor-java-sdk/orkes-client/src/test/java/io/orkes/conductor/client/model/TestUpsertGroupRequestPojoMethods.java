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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestUpsertGroupRequestPojoMethods {

    @Test
    void testDefaultConstructor() {
        UpsertGroupRequest request = new UpsertGroupRequest();
        assertNull(request.getDefaultAccess());
        assertNull(request.getDescription());
        assertNull(request.getRoles());
    }

    @Test
    void testDefaultAccessMethods() {
        UpsertGroupRequest request = new UpsertGroupRequest();

        // Test setDefaultAccess and getDefaultAccess
        Map<String, List<String>> defaultAccess = new HashMap<>();
        defaultAccess.put("WORKFLOW_DEF", Arrays.asList("READ", "EXECUTE"));

        request.setDefaultAccess(defaultAccess);
        assertEquals(defaultAccess, request.getDefaultAccess());

        // Test defaultAccess fluent setter
        Map<String, List<String>> newDefaultAccess = new HashMap<>();
        newDefaultAccess.put("TASK_DEF", Arrays.asList("CREATE", "UPDATE"));

        UpsertGroupRequest returnedRequest = request.defaultAccess(newDefaultAccess);
        assertSame(request, returnedRequest);
        assertEquals(newDefaultAccess, request.getDefaultAccess());

        // Test putDefaultAccessItem
        List<String> accessList = Arrays.asList("DELETE");
        returnedRequest = request.putDefaultAccessItem("WORKFLOW_DEF", accessList);
        assertSame(request, returnedRequest);
        assertTrue(request.getDefaultAccess().containsKey("WORKFLOW_DEF"));
        assertEquals(accessList, request.getDefaultAccess().get("WORKFLOW_DEF"));

        // Test putDefaultAccessItem with null defaultAccess
        request = new UpsertGroupRequest();
        assertNull(request.getDefaultAccess());

        returnedRequest = request.putDefaultAccessItem("WORKFLOW_DEF", accessList);
        assertSame(request, returnedRequest);
        assertNotNull(request.getDefaultAccess());
        assertEquals(accessList, request.getDefaultAccess().get("WORKFLOW_DEF"));
    }

    @Test
    void testDescriptionMethods() {
        UpsertGroupRequest request = new UpsertGroupRequest();

        // Test setDescription and getDescription
        String description = "Test group description";
        request.setDescription(description);
        assertEquals(description, request.getDescription());

        // Test description fluent setter
        String newDescription = "Updated description";
        UpsertGroupRequest returnedRequest = request.description(newDescription);
        assertSame(request, returnedRequest);
        assertEquals(newDescription, request.getDescription());
    }

    @Test
    void testRolesMethods() {
        UpsertGroupRequest request = new UpsertGroupRequest();

        // Test setRoles and getRoles
        List<UpsertGroupRequest.RolesEnum> roles = new ArrayList<>();
        roles.add(UpsertGroupRequest.RolesEnum.ADMIN);
        roles.add(UpsertGroupRequest.RolesEnum.USER);

        request.setRoles(roles);
        assertEquals(roles, request.getRoles());

        // Test roles fluent setter
        List<UpsertGroupRequest.RolesEnum> newRoles = new ArrayList<>();
        newRoles.add(UpsertGroupRequest.RolesEnum.WORKER);
        newRoles.add(UpsertGroupRequest.RolesEnum.METADATA_MANAGER);

        UpsertGroupRequest returnedRequest = request.roles(newRoles);
        assertSame(request, returnedRequest);
        assertEquals(newRoles, request.getRoles());

        // Test addRolesItem
        request = new UpsertGroupRequest();
        assertNull(request.getRoles());

        returnedRequest = request.addRolesItem(UpsertGroupRequest.RolesEnum.WORKFLOW_MANAGER);
        assertSame(request, returnedRequest);
        assertNotNull(request.getRoles());
        assertEquals(1, request.getRoles().size());
        assertTrue(request.getRoles().contains(UpsertGroupRequest.RolesEnum.WORKFLOW_MANAGER));

        // Test addRolesItem with existing roles
        returnedRequest = request.addRolesItem(UpsertGroupRequest.RolesEnum.ADMIN);
        assertSame(request, returnedRequest);
        assertEquals(2, request.getRoles().size());
        assertTrue(request.getRoles().contains(UpsertGroupRequest.RolesEnum.ADMIN));
    }

    @Test
    void testInnerEnumValues() {
        assertEquals("CREATE", UpsertGroupRequest.InnerEnum.CREATE.getValue());
        assertEquals("READ", UpsertGroupRequest.InnerEnum.READ.getValue());
        assertEquals("UPDATE", UpsertGroupRequest.InnerEnum.UPDATE.getValue());
        assertEquals("DELETE", UpsertGroupRequest.InnerEnum.DELETE.getValue());
        assertEquals("EXECUTE", UpsertGroupRequest.InnerEnum.EXECUTE.getValue());

        assertEquals("CREATE", UpsertGroupRequest.InnerEnum.CREATE.toString());

        assertEquals(UpsertGroupRequest.InnerEnum.CREATE, UpsertGroupRequest.InnerEnum.fromValue("CREATE"));
        assertEquals(UpsertGroupRequest.InnerEnum.READ, UpsertGroupRequest.InnerEnum.fromValue("READ"));
        assertEquals(UpsertGroupRequest.InnerEnum.UPDATE, UpsertGroupRequest.InnerEnum.fromValue("UPDATE"));
        assertEquals(UpsertGroupRequest.InnerEnum.DELETE, UpsertGroupRequest.InnerEnum.fromValue("DELETE"));
        assertEquals(UpsertGroupRequest.InnerEnum.EXECUTE, UpsertGroupRequest.InnerEnum.fromValue("EXECUTE"));

        assertNull(UpsertGroupRequest.InnerEnum.fromValue("INVALID"));
    }

    @Test
    void testRolesEnumValues() {
        assertEquals("ADMIN", UpsertGroupRequest.RolesEnum.ADMIN.getValue());
        assertEquals("USER", UpsertGroupRequest.RolesEnum.USER.getValue());
        assertEquals("WORKER", UpsertGroupRequest.RolesEnum.WORKER.getValue());
        assertEquals("METADATA_MANAGER", UpsertGroupRequest.RolesEnum.METADATA_MANAGER.getValue());
        assertEquals("WORKFLOW_MANAGER", UpsertGroupRequest.RolesEnum.WORKFLOW_MANAGER.getValue());

        assertEquals("ADMIN", UpsertGroupRequest.RolesEnum.ADMIN.toString());

        assertEquals(UpsertGroupRequest.RolesEnum.ADMIN, UpsertGroupRequest.RolesEnum.fromValue("ADMIN"));
        assertEquals(UpsertGroupRequest.RolesEnum.USER, UpsertGroupRequest.RolesEnum.fromValue("USER"));
        assertEquals(UpsertGroupRequest.RolesEnum.WORKER, UpsertGroupRequest.RolesEnum.fromValue("WORKER"));
        assertEquals(UpsertGroupRequest.RolesEnum.METADATA_MANAGER, UpsertGroupRequest.RolesEnum.fromValue("METADATA_MANAGER"));
        assertEquals(UpsertGroupRequest.RolesEnum.WORKFLOW_MANAGER, UpsertGroupRequest.RolesEnum.fromValue("WORKFLOW_MANAGER"));

        assertNull(UpsertGroupRequest.RolesEnum.fromValue("INVALID"));
    }

    @Test
    void testEqualsAndHashCode() {
        UpsertGroupRequest request1 = new UpsertGroupRequest()
                .description("Test Description")
                .addRolesItem(UpsertGroupRequest.RolesEnum.ADMIN)
                .putDefaultAccessItem("WORKFLOW_DEF", Arrays.asList("READ", "EXECUTE"));

        UpsertGroupRequest request2 = new UpsertGroupRequest()
                .description("Test Description")
                .addRolesItem(UpsertGroupRequest.RolesEnum.ADMIN)
                .putDefaultAccessItem("WORKFLOW_DEF", Arrays.asList("READ", "EXECUTE"));

        UpsertGroupRequest request3 = new UpsertGroupRequest()
                .description("Different Description")
                .addRolesItem(UpsertGroupRequest.RolesEnum.USER)
                .putDefaultAccessItem("TASK_DEF", Arrays.asList("CREATE", "UPDATE"));

        // Test equals
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);

        // Test hashCode
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    void testToString() {
        UpsertGroupRequest request = new UpsertGroupRequest()
                .description("Test Description")
                .addRolesItem(UpsertGroupRequest.RolesEnum.ADMIN)
                .putDefaultAccessItem("WORKFLOW_DEF", Arrays.asList("READ", "EXECUTE"));

        String toString = request.toString();

        // Basic assertions on toString content
        assertTrue(toString.contains("Test Description"));
        assertTrue(toString.contains("ADMIN"));
        assertTrue(toString.contains("WORKFLOW_DEF"));
    }
}