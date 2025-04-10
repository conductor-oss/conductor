/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestStateChangeEventPojoMethods {

    @Test
    public void testSetAndGetType() {
        // Given
        StateChangeEvent stateChangeEvent = new StateChangeEvent();
        String type = "TEST_TYPE";

        // When
        stateChangeEvent.setType(type);

        // Then
        assertEquals(type, stateChangeEvent.getType());
    }

    @Test
    public void testSetAndGetPayload() {
        // Given
        StateChangeEvent stateChangeEvent = new StateChangeEvent();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key1", "value1");
        payload.put("key2", 123);

        // When
        stateChangeEvent.setPayload(payload);

        // Then
        assertEquals(payload, stateChangeEvent.getPayload());
        assertEquals("value1", stateChangeEvent.getPayload().get("key1"));
        assertEquals(123, stateChangeEvent.getPayload().get("key2"));
    }

    @Test
    public void testToString() {
        // Given
        StateChangeEvent stateChangeEvent = new StateChangeEvent();
        String type = "EVENT_TYPE";
        Map<String, Object> payload = new HashMap<>();
        payload.put("workflowId", "w123");
        payload.put("status", "COMPLETED");

        stateChangeEvent.setType(type);
        stateChangeEvent.setPayload(payload);

        // When
        String result = stateChangeEvent.toString();

        // Then
        assertTrue(result.contains("type='" + type + "'"));
        assertTrue(result.contains("payload=" + payload));
    }

    @Test
    public void testNullValues() {
        // Given
        StateChangeEvent stateChangeEvent = new StateChangeEvent();

        // When & Then
        assertNull(stateChangeEvent.getType());
        assertNull(stateChangeEvent.getPayload());

        // When
        stateChangeEvent.setType(null);
        stateChangeEvent.setPayload(null);

        // Then
        assertNull(stateChangeEvent.getType());
        assertNull(stateChangeEvent.getPayload());
    }

    @Test
    public void testEmptyPayload() {
        // Given
        StateChangeEvent stateChangeEvent = new StateChangeEvent();
        Map<String, Object> emptyPayload = new HashMap<>();

        // When
        stateChangeEvent.setPayload(emptyPayload);

        // Then
        assertNotNull(stateChangeEvent.getPayload());
        assertTrue(stateChangeEvent.getPayload().isEmpty());
    }
}