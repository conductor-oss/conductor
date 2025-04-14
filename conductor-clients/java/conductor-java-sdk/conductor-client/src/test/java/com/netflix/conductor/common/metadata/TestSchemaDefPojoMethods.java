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
package com.netflix.conductor.common.metadata;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSchemaDefPojoMethods {

    @Test
    void testNoArgsConstructor() {
        SchemaDef schemaDef = new SchemaDef();

        assertNotNull(schemaDef);
        assertEquals(1, schemaDef.getVersion());
        assertNull(schemaDef.getName());
        assertNull(schemaDef.getType());
        assertNull(schemaDef.getData());
        assertNull(schemaDef.getExternalRef());
    }

    @Test
    void testParameterizedConstructor() {
        String name = "testSchema";
        SchemaDef.Type type = SchemaDef.Type.JSON;
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        String externalRef = "extRef";

        SchemaDef schemaDef = new SchemaDef(name, type, data, externalRef);

        assertNotNull(schemaDef);
        assertEquals(name, schemaDef.getName());
        assertEquals(type, schemaDef.getType());
        assertEquals(data, schemaDef.getData());
        assertEquals(externalRef, schemaDef.getExternalRef());
        assertEquals(1, schemaDef.getVersion());
    }

    @Test
    void testBuilder() {
        String name = "testSchema";
        SchemaDef.Type type = SchemaDef.Type.AVRO;
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        String externalRef = "extRef";

        SchemaDef schemaDef = SchemaDef.builder()
                .name(name)
                .type(type)
                .data(data)
                .externalRef(externalRef)
                .build();

        assertNotNull(schemaDef);
        assertEquals(name, schemaDef.getName());
        assertEquals(type, schemaDef.getType());
        assertEquals(data, schemaDef.getData());
        assertEquals(externalRef, schemaDef.getExternalRef());
        assertEquals(1, schemaDef.getVersion());
    }

    @Test
    void testBuilderToString() {
        String name = "testSchema";
        SchemaDef.Type type = SchemaDef.Type.PROTOBUF;
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        String externalRef = "extRef";

        SchemaDef.SchemaDefBuilder builder = SchemaDef.builder()
                .name(name)
                .type(type)
                .data(data)
                .externalRef(externalRef);

        String expectedToString = "SchemaDef.SchemaDefBuilder(name=" + name +
                ", type=" + type +
                ", data=" + data +
                ", externalRef=" + externalRef + ")";

        assertEquals(expectedToString, builder.toString());
    }

    @Test
    void testSetters() {
        SchemaDef schemaDef = new SchemaDef();

        String name = "updatedSchema";
        SchemaDef.Type type = SchemaDef.Type.JSON;
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        String externalRef = "updatedExtRef";

        schemaDef.setName(name);
        schemaDef.setType(type);
        schemaDef.setData(data);
        schemaDef.setExternalRef(externalRef);

        assertEquals(name, schemaDef.getName());
        assertEquals(type, schemaDef.getType());
        assertEquals(data, schemaDef.getData());
        assertEquals(externalRef, schemaDef.getExternalRef());
    }

    @Test
    void testEqualsAndHashCode() {
        String name = "testSchema";
        SchemaDef.Type type = SchemaDef.Type.JSON;
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        String externalRef = "extRef";

        SchemaDef schemaDef1 = new SchemaDef(name, type, data, externalRef);
        SchemaDef schemaDef2 = new SchemaDef(name, type, data, externalRef);
        SchemaDef schemaDef3 = new SchemaDef("differentName", type, data, externalRef);

        // Test equals
        assertEquals(schemaDef1, schemaDef2);
        assertNotEquals(schemaDef1, schemaDef3);

        // Test hashCode
        assertEquals(schemaDef1.hashCode(), schemaDef2.hashCode());
        assertNotEquals(schemaDef1.hashCode(), schemaDef3.hashCode());
    }

    @Test
    void testToString() {
        String name = "testSchema";
        SchemaDef.Type type = SchemaDef.Type.JSON;
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        String externalRef = "extRef";

        SchemaDef schemaDef = new SchemaDef(name, type, data, externalRef);
        String toStringResult = schemaDef.toString();

        // Since @Data annotation adds toString, we just verify it contains key fields
        assertTrue(toStringResult.contains(name));
        assertTrue(toStringResult.contains(type.toString()));
        assertTrue(toStringResult.contains(externalRef));
        assertTrue(toStringResult.contains("version=1"));
    }
}
