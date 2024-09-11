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
package io.orkes.conductor.client.http;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;

import io.orkes.conductor.client.util.ClientTestUtil;
import io.orkes.conductor.client.util.Commons;

public class EventClientTests {
    private static final String EVENT_NAME = "test_sdk_java_event_name";
    private static final String EVENT = "test_sdk_java_event";
    private final OrkesEventClient eventClient = ClientTestUtil.getOrkesClients().getEventClient();

    @Test
    void testEventHandler() {
        try {
            eventClient.unregisterEventHandler(EVENT_NAME);
        } catch (ConductorClientException e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }
        EventHandler eventHandler = getEventHandler();
        eventClient.registerEventHandler(eventHandler);
        eventClient.updateEventHandler(eventHandler);
        List<EventHandler> events = eventClient.getEventHandlers(EVENT, false);
        Assertions.assertEquals(1, events.size());
        events.forEach(
                event -> {
                    Assertions.assertEquals(eventHandler.getName(), event.getName());
                    Assertions.assertEquals(eventHandler.getEvent(), event.getEvent());
                });
        eventClient.unregisterEventHandler(EVENT_NAME);
        Assertions.assertIterableEquals(List.of(), eventClient.getEventHandlers(EVENT, false));
    }

    EventHandler getEventHandler() {
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(EVENT_NAME);
        eventHandler.setEvent(EVENT);
        eventHandler.setActions(List.of(getEventHandlerAction()));
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
