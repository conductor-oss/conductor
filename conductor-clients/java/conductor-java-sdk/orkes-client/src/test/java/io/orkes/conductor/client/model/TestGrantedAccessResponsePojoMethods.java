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

class TestGrantedAccessResponsePojoMethods {

    @Test
    void testDefaultConstructor() {
        GrantedAccessResponse response = new GrantedAccessResponse();
        assertNull(response.getGrantedAccess());
    }

    @Test
    void testSetAndGetGrantedAccess() {
        GrantedAccessResponse response = new GrantedAccessResponse();
        List<GrantedAccess> grantedAccessList = new ArrayList<>();
        grantedAccessList.add(new GrantedAccess());

        response.setGrantedAccess(grantedAccessList);
        assertEquals(grantedAccessList, response.getGrantedAccess());
    }

    @Test
    void testGrantedAccessBuilderMethod() {
        List<GrantedAccess> grantedAccessList = new ArrayList<>();
        grantedAccessList.add(new GrantedAccess());

        GrantedAccessResponse response = new GrantedAccessResponse();
        GrantedAccessResponse returnedResponse = response.grantedAccess(grantedAccessList);

        assertEquals(grantedAccessList, response.getGrantedAccess());
        assertSame(response, returnedResponse);
    }

    @Test
    void testAddGrantedAccessItemWithNullList() {
        GrantedAccess grantedAccess = new GrantedAccess();

        GrantedAccessResponse response = new GrantedAccessResponse();
        GrantedAccessResponse returnedResponse = response.addGrantedAccessItem(grantedAccess);

        assertNotNull(response.getGrantedAccess());
        assertEquals(1, response.getGrantedAccess().size());
        assertSame(grantedAccess, response.getGrantedAccess().get(0));
        assertSame(response, returnedResponse);
    }

    @Test
    void testAddGrantedAccessItemWithExistingList() {
        GrantedAccess grantedAccess1 = new GrantedAccess();
        GrantedAccess grantedAccess2 = new GrantedAccess();

        List<GrantedAccess> grantedAccessList = new ArrayList<>();
        grantedAccessList.add(grantedAccess1);

        GrantedAccessResponse response = new GrantedAccessResponse();
        response.setGrantedAccess(grantedAccessList);
        GrantedAccessResponse returnedResponse = response.addGrantedAccessItem(grantedAccess2);

        assertEquals(2, response.getGrantedAccess().size());
        assertSame(grantedAccess1, response.getGrantedAccess().get(0));
        assertSame(grantedAccess2, response.getGrantedAccess().get(1));
        assertSame(response, returnedResponse);
    }

    @Test
    void testEqualsAndHashCode() {
        GrantedAccessResponse response1 = new GrantedAccessResponse();
        GrantedAccessResponse response2 = new GrantedAccessResponse();

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());

        List<GrantedAccess> grantedAccessList = new ArrayList<>();
        grantedAccessList.add(new GrantedAccess());

        response1.setGrantedAccess(grantedAccessList);
        assertNotEquals(response1, response2);
        assertNotEquals(response1.hashCode(), response2.hashCode());

        response2.setGrantedAccess(grantedAccessList);
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testToString() {
        GrantedAccessResponse response = new GrantedAccessResponse();
        assertNotNull(response.toString());

        List<GrantedAccess> grantedAccessList = new ArrayList<>();
        grantedAccessList.add(new GrantedAccess());

        response.setGrantedAccess(grantedAccessList);
        String toStringResult = response.toString();

        assertNotNull(toStringResult);
        assertTrue(toStringResult.contains("grantedAccess"));
    }
}