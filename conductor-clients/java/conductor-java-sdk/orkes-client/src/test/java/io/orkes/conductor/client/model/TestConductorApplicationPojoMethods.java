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

class TestConductorApplicationPojoMethods {

    @Test
    void testConstructor() {
        ConductorApplication application = new ConductorApplication();
        assertNotNull(application);
        assertNull(application.getId());
        assertNull(application.getName());
        assertNull(application.getCreatedBy());
    }

    @Test
    void testGetSetId() {
        ConductorApplication application = new ConductorApplication();
        String id = "app123";
        application.setId(id);
        assertEquals(id, application.getId());
    }

    @Test
    void testGetSetName() {
        ConductorApplication application = new ConductorApplication();
        String name = "testApp";
        application.setName(name);
        assertEquals(name, application.getName());
    }

    @Test
    void testGetSetCreatedBy() {
        ConductorApplication application = new ConductorApplication();
        String createdBy = "user1";
        application.setCreatedBy(createdBy);
        assertEquals(createdBy, application.getCreatedBy());
    }

    @Test
    void testFluentId() {
        String id = "app123";
        ConductorApplication application = new ConductorApplication().id(id);
        assertEquals(id, application.getId());
    }

    @Test
    void testFluentName() {
        String name = "testApp";
        ConductorApplication application = new ConductorApplication().name(name);
        assertEquals(name, application.getName());
    }

    @Test
    void testFluentCreatedBy() {
        String createdBy = "user1";
        ConductorApplication application = new ConductorApplication().createdBy(createdBy);
        assertEquals(createdBy, application.getCreatedBy());
    }

    @Test
    void testChainedFluent() {
        String id = "app123";
        String name = "testApp";
        String createdBy = "user1";

        ConductorApplication application = new ConductorApplication()
                .id(id)
                .name(name)
                .createdBy(createdBy);

        assertEquals(id, application.getId());
        assertEquals(name, application.getName());
        assertEquals(createdBy, application.getCreatedBy());
    }

    @Test
    void testEquals() {
        ConductorApplication app1 = new ConductorApplication()
                .id("app123")
                .name("testApp")
                .createdBy("user1");

        ConductorApplication app2 = new ConductorApplication()
                .id("app123")
                .name("testApp")
                .createdBy("user1");

        ConductorApplication app3 = new ConductorApplication()
                .id("app456")
                .name("testApp")
                .createdBy("user1");

        assertEquals(app1, app2);
        assertNotEquals(app1, app3);
        assertNotEquals(app1, null);
        assertNotEquals(app1, new Object());
    }

    @Test
    void testHashCode() {
        ConductorApplication app1 = new ConductorApplication()
                .id("app123")
                .name("testApp")
                .createdBy("user1");

        ConductorApplication app2 = new ConductorApplication()
                .id("app123")
                .name("testApp")
                .createdBy("user1");

        assertEquals(app1.hashCode(), app2.hashCode());
    }

    @Test
    void testToString() {
        ConductorApplication application = new ConductorApplication()
                .id("app123")
                .name("testApp")
                .createdBy("user1");

        String toString = application.toString();

        assertTrue(toString.contains("app123"));
        assertTrue(toString.contains("testApp"));
        assertTrue(toString.contains("user1"));
    }
}