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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestEventHandlerPojoMethods {

    @Test
    void testDefaultConstructor() {
        EventHandler eventHandler = new EventHandler();
        assertNotNull(eventHandler);
        assertNull(eventHandler.getName());
        assertNull(eventHandler.getEvent());
        assertNull(eventHandler.getCondition());
        assertNotNull(eventHandler.getActions());
        assertTrue(eventHandler.getActions().isEmpty());
        assertFalse(eventHandler.isActive());
        assertNull(eventHandler.getEvaluatorType());
    }

    @Test
    void testNameGetterAndSetter() {
        EventHandler eventHandler = new EventHandler();
        assertNull(eventHandler.getName());

        String name = "testEventHandler";
        eventHandler.setName(name);
        assertEquals(name, eventHandler.getName());
    }

    @Test
    void testEventGetterAndSetter() {
        EventHandler eventHandler = new EventHandler();
        assertNull(eventHandler.getEvent());

        String event = "testEvent";
        eventHandler.setEvent(event);
        assertEquals(event, eventHandler.getEvent());
    }

    @Test
    void testConditionGetterAndSetter() {
        EventHandler eventHandler = new EventHandler();
        assertNull(eventHandler.getCondition());

        String condition = "testCondition";
        eventHandler.setCondition(condition);
        assertEquals(condition, eventHandler.getCondition());
    }

    @Test
    void testActionsGetterAndSetter() {
        EventHandler eventHandler = new EventHandler();
        assertNotNull(eventHandler.getActions());
        assertTrue(eventHandler.getActions().isEmpty());

        List<EventHandler.Action> actions = new ArrayList<>();
        EventHandler.Action action1 = new EventHandler.Action();
        EventHandler.Action action2 = new EventHandler.Action();
        actions.add(action1);
        actions.add(action2);

        eventHandler.setActions(actions);
        assertEquals(actions, eventHandler.getActions());
        assertEquals(2, eventHandler.getActions().size());
    }

    @Test
    void testActiveGetterAndSetter() {
        EventHandler eventHandler = new EventHandler();
        assertFalse(eventHandler.isActive());

        eventHandler.setActive(true);
        assertTrue(eventHandler.isActive());

        eventHandler.setActive(false);
        assertFalse(eventHandler.isActive());
    }

    @Test
    void testEvaluatorTypeGetterAndSetter() {
        EventHandler eventHandler = new EventHandler();
        assertNull(eventHandler.getEvaluatorType());

        String evaluatorType = "testEvaluatorType";
        eventHandler.setEvaluatorType(evaluatorType);
        assertEquals(evaluatorType, eventHandler.getEvaluatorType());
    }
}