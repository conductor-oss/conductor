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
package io.conductor.e2e.metadata;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;

import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.Commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class EventClientTests {
    private static final String EVENT_NAME = "test_sdk_java_event_name";
    private static final String EVENT = "test_sdk_java_event";

    private final EventClient eventClient = ApiUtil.EVENT_CLIENT;

    @Test
    void testEventHandler() {
        // Clean up any existing event handler (server returns 500, not 404, for non-existent)
        try {
            eventClient.unregisterEventHandler(EVENT_NAME);
        } catch (Exception ignored) {
        }

        // Create and register event handler
        final EventHandler eventHandler = getEventHandler();
        try {
            eventClient.registerEventHandler(eventHandler);
            eventClient.updateEventHandler(eventHandler);

            // Verify registration
            List<EventHandler> events = eventClient.getEventHandlers(EVENT, false);
            assertEquals(1, events.size(), "Expected exactly 1 event handler");

            events.forEach(
                    event -> {
                        assertEquals(eventHandler.getName(), event.getName());
                        assertEquals(eventHandler.getEvent(), event.getEvent());
                    });

            // Clean up
            eventClient.unregisterEventHandler(EVENT_NAME);
            // Verify cleanup
            assertIterableEquals(List.of(), eventClient.getEventHandlers(EVENT, false));
        } catch (Exception e) {
            // Clean up on failure
            try {
                eventClient.unregisterEventHandler(EVENT_NAME);
            } catch (Exception cleanupEx) {
                // Ignore cleanup errors
            }
            throw e;
        }
    }

    EventHandler getEventHandler() {
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(EVENT_NAME);
        eventHandler.setEvent(EVENT);
        eventHandler.setActions(List.of(getEventHandlerAction()));
        // Note: tags field can be null - server handles it defensively
        return eventHandler;
    }

    Action getEventHandlerAction() {
        Action action = new Action();
        action.setAction(Action.Type.start_workflow);
        action.setStart_workflow(getStartWorkflowAction());
        return action;
    }

    StartWorkflow getStartWorkflowAction() {
        StartWorkflow startWorkflow = new StartWorkflow();
        startWorkflow.setName(Commons.WORKFLOW_NAME);
        startWorkflow.setVersion(Commons.WORKFLOW_VERSION);
        return startWorkflow;
    }
}
