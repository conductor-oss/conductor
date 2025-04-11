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

public class TestCreateOrUpdateApplicationRequestPojoMethods {

    @Test
    public void testDefaultConstructor() {
        CreateOrUpdateApplicationRequest request = new CreateOrUpdateApplicationRequest();
        assertNull(request.getName(), "Default name should be null");
    }

    @Test
    public void testGetterAndSetter() {
        CreateOrUpdateApplicationRequest request = new CreateOrUpdateApplicationRequest();

        assertNull(request.getName(), "Initial name should be null");

        String appName = "Test Application";
        request.setName(appName);

        assertEquals(appName, request.getName(), "Name should match the set value");
    }

    @Test
    public void testFluentSetter() {
        String appName = "Payment Processors";

        CreateOrUpdateApplicationRequest request = new CreateOrUpdateApplicationRequest()
                .name(appName);

        assertEquals(appName, request.getName(), "Name should match the set value using fluent setter");
    }

    @Test
    public void testEqualsAndHashCode() {
        CreateOrUpdateApplicationRequest request1 = new CreateOrUpdateApplicationRequest().name("App1");
        CreateOrUpdateApplicationRequest request2 = new CreateOrUpdateApplicationRequest().name("App1");
        CreateOrUpdateApplicationRequest request3 = new CreateOrUpdateApplicationRequest().name("App2");

        // Test equals
        assertEquals(request1, request2, "Equal objects should be equal");
        assertNotEquals(request1, request3, "Objects with different names should not be equal");
        assertNotEquals(request1, null, "Object should not equal null");
        assertNotEquals(request1, new Object(), "Object should not equal different type");

        // Test hashCode
        assertEquals(request1.hashCode(), request2.hashCode(), "Equal objects should have same hash code");
    }

    @Test
    public void testToString() {
        String appName = "Test App";
        CreateOrUpdateApplicationRequest request = new CreateOrUpdateApplicationRequest().name(appName);

        String toString = request.toString();

        assertTrue(toString.contains(appName), "toString should contain the name value");
        assertTrue(toString.contains("CreateOrUpdateApplicationRequest"), "toString should contain class name");
    }
}