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
package com.netflix.conductor.common.metadata.events;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.events.EventHandler.Action;

import static org.junit.jupiter.api.Assertions.*;

class TestEventExecutionPojoMethods {

    @Test
    void testNoArgsConstructor() {
        EventExecution execution = new EventExecution();
        assertNotNull(execution);
        assertNull(execution.getId());
        assertNull(execution.getMessageId());
        assertNull(execution.getName());
        assertNull(execution.getEvent());
        assertEquals(0, execution.getCreated());
        assertNull(execution.getStatus());
        assertNull(execution.getAction());
        assertNotNull(execution.getOutput());
        assertTrue(execution.getOutput().isEmpty());
    }

    @Test
    void testParameterizedConstructor() {
        String id = "test-id";
        String messageId = "test-message-id";

        EventExecution execution = new EventExecution(id, messageId);

        assertEquals(id, execution.getId());
        assertEquals(messageId, execution.getMessageId());
        assertNull(execution.getName());
        assertNull(execution.getEvent());
        assertEquals(0, execution.getCreated());
        assertNull(execution.getStatus());
        assertNull(execution.getAction());
        assertNotNull(execution.getOutput());
        assertTrue(execution.getOutput().isEmpty());
    }

    @Test
    void testSetAndGetId() {
        EventExecution execution = new EventExecution();
        String id = "test-id";

        execution.setId(id);
        assertEquals(id, execution.getId());
    }

    @Test
    void testSetAndGetMessageId() {
        EventExecution execution = new EventExecution();
        String messageId = "test-message-id";

        execution.setMessageId(messageId);
        assertEquals(messageId, execution.getMessageId());
    }

    @Test
    void testSetAndGetName() {
        EventExecution execution = new EventExecution();
        String name = "test-name";

        execution.setName(name);
        assertEquals(name, execution.getName());
    }

    @Test
    void testSetAndGetEvent() {
        EventExecution execution = new EventExecution();
        String event = "test-event";

        execution.setEvent(event);
        assertEquals(event, execution.getEvent());
    }

    @Test
    void testSetAndGetCreated() {
        EventExecution execution = new EventExecution();
        long created = System.currentTimeMillis();

        execution.setCreated(created);
        assertEquals(created, execution.getCreated());
    }

    @Test
    void testSetAndGetStatus() {
        EventExecution execution = new EventExecution();
        EventExecution.Status status = EventExecution.Status.COMPLETED;

        execution.setStatus(status);
        assertEquals(status, execution.getStatus());
    }

    @Test
    void testSetAndGetAction() {
        EventExecution execution = new EventExecution();
        Action.Type action = Action.Type.start_workflow;

        execution.setAction(action);
        assertEquals(action, execution.getAction());
    }

    @Test
    void testSetAndGetOutput() {
        EventExecution execution = new EventExecution();
        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");
        output.put("key2", 100);

        execution.setOutput(output);
        assertEquals(output, execution.getOutput());
        assertEquals(2, execution.getOutput().size());
        assertEquals("value1", execution.getOutput().get("key1"));
        assertEquals(100, execution.getOutput().get("key2"));
    }

    @Test
    void testEqualsAndHashCodeWithSameInstance() {
        EventExecution execution = new EventExecution();
        assertTrue(execution.equals(execution));
        assertEquals(execution.hashCode(), execution.hashCode());
    }

    @Test
    void testEqualsWithNull() {
        EventExecution execution = new EventExecution();
        assertFalse(execution.equals(null));
    }

    @Test
    void testEqualsWithDifferentClass() {
        EventExecution execution = new EventExecution();
        assertFalse(execution.equals(new Object()));
    }

    @Test
    void testEqualsAndHashCodeWithEqualObjects() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();

        assertTrue(execution1.equals(execution2));
        assertTrue(execution2.equals(execution1));
        assertEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentId() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setId("different-id");

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentMessageId() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setMessageId("different-message-id");

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentName() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setName("different-name");

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentEvent() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setEvent("different-event");

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentCreated() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setCreated(execution1.getCreated() + 1000);

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentStatus() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setStatus(EventExecution.Status.FAILED);

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentAction() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();
        execution2.setAction(Action.Type.complete_task);

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    @Test
    void testEqualsAndHashCodeWithDifferentOutput() {
        EventExecution execution1 = createFullEventExecution();
        EventExecution execution2 = createFullEventExecution();

        Map<String, Object> differentOutput = new HashMap<>();
        differentOutput.put("different-key", "different-value");
        execution2.setOutput(differentOutput);

        assertFalse(execution1.equals(execution2));
        assertFalse(execution2.equals(execution1));
        assertNotEquals(execution1.hashCode(), execution2.hashCode());
    }

    private EventExecution createFullEventExecution() {
        EventExecution execution = new EventExecution("test-id", "test-message-id");
        execution.setName("test-name");
        execution.setEvent("test-event");
        execution.setCreated(1234567890);
        execution.setStatus(EventExecution.Status.COMPLETED);
        execution.setAction(Action.Type.start_workflow);

        Map<String, Object> output = new HashMap<>();
        output.put("key1", "value1");
        output.put("key2", 100);
        execution.setOutput(output);

        return execution;
    }
}