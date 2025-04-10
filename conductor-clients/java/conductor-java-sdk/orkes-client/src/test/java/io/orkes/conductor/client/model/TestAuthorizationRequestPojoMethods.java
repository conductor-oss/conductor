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

class TestAuthorizationRequestPojoMethods {

    @Test
    void testAccessEnumValues() {
        assertEquals("CREATE", AuthorizationRequest.AccessEnum.CREATE.getValue());
        assertEquals("READ", AuthorizationRequest.AccessEnum.READ.getValue());
        assertEquals("UPDATE", AuthorizationRequest.AccessEnum.UPDATE.getValue());
        assertEquals("DELETE", AuthorizationRequest.AccessEnum.DELETE.getValue());
        assertEquals("EXECUTE", AuthorizationRequest.AccessEnum.EXECUTE.getValue());
    }

    @Test
    void testAccessEnumToString() {
        assertEquals("CREATE", AuthorizationRequest.AccessEnum.CREATE.toString());
        assertEquals("READ", AuthorizationRequest.AccessEnum.READ.toString());
        assertEquals("UPDATE", AuthorizationRequest.AccessEnum.UPDATE.toString());
        assertEquals("DELETE", AuthorizationRequest.AccessEnum.DELETE.toString());
        assertEquals("EXECUTE", AuthorizationRequest.AccessEnum.EXECUTE.toString());
    }

    @Test
    void testAccessEnumFromValue() {
        assertEquals(AuthorizationRequest.AccessEnum.CREATE, AuthorizationRequest.AccessEnum.fromValue("CREATE"));
        assertEquals(AuthorizationRequest.AccessEnum.READ, AuthorizationRequest.AccessEnum.fromValue("READ"));
        assertEquals(AuthorizationRequest.AccessEnum.UPDATE, AuthorizationRequest.AccessEnum.fromValue("UPDATE"));
        assertEquals(AuthorizationRequest.AccessEnum.DELETE, AuthorizationRequest.AccessEnum.fromValue("DELETE"));
        assertEquals(AuthorizationRequest.AccessEnum.EXECUTE, AuthorizationRequest.AccessEnum.fromValue("EXECUTE"));
        assertNull(AuthorizationRequest.AccessEnum.fromValue("INVALID"));
    }

    @Test
    void testDefaultConstructor() {
        AuthorizationRequest request = new AuthorizationRequest();
        assertNotNull(request.getAccess());
        assertTrue(request.getAccess().isEmpty());
        assertNull(request.getSubject());
        assertNull(request.getTarget());
    }

    @Test
    void testAccessGetterSetter() {
        AuthorizationRequest request = new AuthorizationRequest();
        List<AuthorizationRequest.AccessEnum> accessList = Arrays.asList(
                AuthorizationRequest.AccessEnum.READ,
                AuthorizationRequest.AccessEnum.EXECUTE
        );

        request.setAccess(accessList);
        assertEquals(accessList, request.getAccess());
        assertEquals(2, request.getAccess().size());
        assertTrue(request.getAccess().contains(AuthorizationRequest.AccessEnum.READ));
        assertTrue(request.getAccess().contains(AuthorizationRequest.AccessEnum.EXECUTE));
    }

    @Test
    void testAccessFluentSetter() {
        AuthorizationRequest request = new AuthorizationRequest();
        List<AuthorizationRequest.AccessEnum> accessList = Arrays.asList(
                AuthorizationRequest.AccessEnum.CREATE,
                AuthorizationRequest.AccessEnum.UPDATE
        );

        AuthorizationRequest returnedRequest = request.access(accessList);

        assertSame(request, returnedRequest);
        assertEquals(accessList, request.getAccess());
        assertEquals(2, request.getAccess().size());
        assertTrue(request.getAccess().contains(AuthorizationRequest.AccessEnum.CREATE));
        assertTrue(request.getAccess().contains(AuthorizationRequest.AccessEnum.UPDATE));
    }

    @Test
    void testAddAccessItem() {
        AuthorizationRequest request = new AuthorizationRequest();

        AuthorizationRequest returnedRequest = request.addAccessItem(AuthorizationRequest.AccessEnum.READ);
        assertSame(request, returnedRequest);
        assertEquals(1, request.getAccess().size());
        assertTrue(request.getAccess().contains(AuthorizationRequest.AccessEnum.READ));

        request.addAccessItem(AuthorizationRequest.AccessEnum.DELETE);
        assertEquals(2, request.getAccess().size());
        assertTrue(request.getAccess().contains(AuthorizationRequest.AccessEnum.DELETE));
    }

    @Test
    void testSubjectGetterSetter() {
        AuthorizationRequest request = new AuthorizationRequest();
        SubjectRef subject = new SubjectRef();

        request.setSubject(subject);
        assertEquals(subject, request.getSubject());
    }

    @Test
    void testSubjectFluentSetter() {
        AuthorizationRequest request = new AuthorizationRequest();
        SubjectRef subject = new SubjectRef();

        AuthorizationRequest returnedRequest = request.subject(subject);

        assertSame(request, returnedRequest);
        assertEquals(subject, request.getSubject());
    }

    @Test
    void testTargetGetterSetter() {
        AuthorizationRequest request = new AuthorizationRequest();
        TargetRef target = new TargetRef();

        request.setTarget(target);
        assertEquals(target, request.getTarget());
    }

    @Test
    void testTargetFluentSetter() {
        AuthorizationRequest request = new AuthorizationRequest();
        TargetRef target = new TargetRef();

        AuthorizationRequest returnedRequest = request.target(target);

        assertSame(request, returnedRequest);
        assertEquals(target, request.getTarget());
    }

    @Test
    void testEqualsAndHashCode() {
        AuthorizationRequest request1 = new AuthorizationRequest();
        AuthorizationRequest request2 = new AuthorizationRequest();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());

        SubjectRef subject = new SubjectRef();
        TargetRef target = new TargetRef();

        request1.setSubject(subject);
        assertNotEquals(request1, request2);

        request2.setSubject(subject);
        assertEquals(request1, request2);

        request1.setTarget(target);
        assertNotEquals(request1, request2);

        request2.setTarget(target);
        assertEquals(request1, request2);

        request1.addAccessItem(AuthorizationRequest.AccessEnum.READ);
        assertNotEquals(request1, request2);

        request2.addAccessItem(AuthorizationRequest.AccessEnum.READ);
        assertEquals(request1, request2);
    }

    @Test
    void testToString() {
        AuthorizationRequest request = new AuthorizationRequest();

        // Just verify that toString produces something and doesn't throw
        String toString = request.toString();
        assertNotNull(toString);

        // Add some data and make sure toString still works
        SubjectRef subject = new SubjectRef();
        TargetRef target = new TargetRef();
        request.setSubject(subject);
        request.setTarget(target);
        request.addAccessItem(AuthorizationRequest.AccessEnum.READ);

        toString = request.toString();
        assertNotNull(toString);
    }
}