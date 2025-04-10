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
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGrantedAccessPojoMethods {

    @Test
    void testNoArgsConstructor() {
        GrantedAccess grantedAccess = new GrantedAccess();
        assertNull(grantedAccess.getAccess());
        assertNull(grantedAccess.getTarget());
    }

    @Test
    void testAllArgsConstructor() {
        List<GrantedAccess.AccessEnum> accessList = Arrays.asList(
                GrantedAccess.AccessEnum.READ,
                GrantedAccess.AccessEnum.UPDATE
        );
        TargetRef targetRef = new TargetRef();

        GrantedAccess grantedAccess = new GrantedAccess(accessList, targetRef);

        assertEquals(accessList, grantedAccess.getAccess());
        assertEquals(targetRef, grantedAccess.getTarget());
    }

    @Test
    void testBuilder() {
        List<GrantedAccess.AccessEnum> accessList = Arrays.asList(
                GrantedAccess.AccessEnum.CREATE,
                GrantedAccess.AccessEnum.DELETE
        );
        TargetRef targetRef = new TargetRef();

        GrantedAccess grantedAccess = GrantedAccess.builder()
                .access(accessList)
                .target(targetRef)
                .build();

        assertEquals(accessList, grantedAccess.getAccess());
        assertEquals(targetRef, grantedAccess.getTarget());
    }

    @Test
    void testGettersAndSetters() {
        GrantedAccess grantedAccess = new GrantedAccess();

        List<GrantedAccess.AccessEnum> accessList = Arrays.asList(
                GrantedAccess.AccessEnum.CREATE,
                GrantedAccess.AccessEnum.READ
        );
        TargetRef targetRef = new TargetRef();

        grantedAccess.setAccess(accessList);
        grantedAccess.setTarget(targetRef);

        assertEquals(accessList, grantedAccess.getAccess());
        assertEquals(targetRef, grantedAccess.getTarget());
    }

    @Test
    void testFluentAccessMethod() {
        GrantedAccess grantedAccess = new GrantedAccess();

        List<GrantedAccess.AccessEnum> accessList = Arrays.asList(
                GrantedAccess.AccessEnum.EXECUTE,
                GrantedAccess.AccessEnum.UPDATE
        );

        GrantedAccess result = grantedAccess.access(accessList);

        // Verify the access is set
        assertEquals(accessList, grantedAccess.getAccess());
        // Verify fluent interface returns this
        assertSame(grantedAccess, result);
    }

    @Test
    void testAddAccessItemWithNullAccess() {
        GrantedAccess grantedAccess = new GrantedAccess();
        // Access is null initially
        assertNull(grantedAccess.getAccess());

        GrantedAccess result = grantedAccess.addAccessItem(GrantedAccess.AccessEnum.READ);

        // Verify list was initialized and item added
        assertNotNull(grantedAccess.getAccess());
        assertEquals(1, grantedAccess.getAccess().size());
        assertEquals(GrantedAccess.AccessEnum.READ, grantedAccess.getAccess().get(0));
        // Verify fluent interface returns this
        assertSame(grantedAccess, result);
    }

    @Test
    void testAddAccessItemWithExistingAccess() {
        GrantedAccess grantedAccess = new GrantedAccess();
        List<GrantedAccess.AccessEnum> accessList = new ArrayList<>();
        accessList.add(GrantedAccess.AccessEnum.CREATE);
        grantedAccess.setAccess(accessList);

        GrantedAccess result = grantedAccess.addAccessItem(GrantedAccess.AccessEnum.READ);

        // Verify new item was added to existing list
        assertEquals(2, grantedAccess.getAccess().size());
        assertEquals(GrantedAccess.AccessEnum.CREATE, grantedAccess.getAccess().get(0));
        assertEquals(GrantedAccess.AccessEnum.READ, grantedAccess.getAccess().get(1));
        // Verify fluent interface returns this
        assertSame(grantedAccess, result);
    }

    @Test
    void testFluentTargetMethod() {
        GrantedAccess grantedAccess = new GrantedAccess();
        TargetRef targetRef = new TargetRef();

        GrantedAccess result = grantedAccess.target(targetRef);

        // Verify the target is set
        assertEquals(targetRef, grantedAccess.getTarget());
        // Verify fluent interface returns this
        assertSame(grantedAccess, result);
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical objects
        List<GrantedAccess.AccessEnum> accessList1 = Arrays.asList(GrantedAccess.AccessEnum.READ);
        TargetRef targetRef1 = new TargetRef();

        GrantedAccess grantedAccess1 = new GrantedAccess(accessList1, targetRef1);
        GrantedAccess grantedAccess2 = new GrantedAccess(accessList1, targetRef1);

        // Test equals
        assertEquals(grantedAccess1, grantedAccess2);
        // Test hashCode
        assertEquals(grantedAccess1.hashCode(), grantedAccess2.hashCode());

        // Create different object
        List<GrantedAccess.AccessEnum> accessList2 = Arrays.asList(GrantedAccess.AccessEnum.UPDATE);
        GrantedAccess grantedAccess3 = new GrantedAccess(accessList2, targetRef1);

        // Test not equals
        assertNotEquals(grantedAccess1, grantedAccess3);
    }

    @Test
    void testToString() {
        GrantedAccess grantedAccess = new GrantedAccess();
        grantedAccess.setAccess(Arrays.asList(GrantedAccess.AccessEnum.READ));

        String toString = grantedAccess.toString();

        // Basic verification that toString contains class name and field values
        assertTrue(toString.contains("GrantedAccess"));
        assertTrue(toString.contains("access"));
        assertTrue(toString.contains("READ"));
    }

    @Test
    void testAccessEnumFromValue() {
        // Test valid values
        assertEquals(GrantedAccess.AccessEnum.CREATE, GrantedAccess.AccessEnum.fromValue("CREATE"));
        assertEquals(GrantedAccess.AccessEnum.READ, GrantedAccess.AccessEnum.fromValue("READ"));
        assertEquals(GrantedAccess.AccessEnum.UPDATE, GrantedAccess.AccessEnum.fromValue("UPDATE"));
        assertEquals(GrantedAccess.AccessEnum.DELETE, GrantedAccess.AccessEnum.fromValue("DELETE"));
        assertEquals(GrantedAccess.AccessEnum.EXECUTE, GrantedAccess.AccessEnum.fromValue("EXECUTE"));

        // Test invalid value
        assertNull(GrantedAccess.AccessEnum.fromValue("INVALID"));
    }

    @Test
    void testAccessEnumGetValue() {
        assertEquals("CREATE", GrantedAccess.AccessEnum.CREATE.getValue());
        assertEquals("READ", GrantedAccess.AccessEnum.READ.getValue());
        assertEquals("UPDATE", GrantedAccess.AccessEnum.UPDATE.getValue());
        assertEquals("DELETE", GrantedAccess.AccessEnum.DELETE.getValue());
        assertEquals("EXECUTE", GrantedAccess.AccessEnum.EXECUTE.getValue());
    }

    @Test
    void testAccessEnumToString() {
        assertEquals("CREATE", GrantedAccess.AccessEnum.CREATE.toString());
        assertEquals("READ", GrantedAccess.AccessEnum.READ.toString());
        assertEquals("UPDATE", GrantedAccess.AccessEnum.UPDATE.toString());
        assertEquals("DELETE", GrantedAccess.AccessEnum.DELETE.toString());
        assertEquals("EXECUTE", GrantedAccess.AccessEnum.EXECUTE.toString());
    }
}
