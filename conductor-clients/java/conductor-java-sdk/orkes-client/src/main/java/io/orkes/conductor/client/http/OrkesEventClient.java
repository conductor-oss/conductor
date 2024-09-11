/*
 * Copyright 2022 Orkes, Inc.
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
import java.util.Map;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.common.metadata.events.EventHandler;

import io.orkes.conductor.client.model.event.QueueConfiguration;

public class OrkesEventClient {

    private final EventResource eventResource;

    private final EventClient eventClient;

    public OrkesEventClient(ConductorClient client) {
        this.eventResource = new EventResource(client);
        this.eventClient = new EventClient(client);
    }

    public List<EventHandler> getEventHandlers() {
        return eventResource.getEventHandlers();
    }

    public void handleIncomingEvent(Map<String, Object> payload) {
        eventResource.handleIncomingEvent(payload);
    }

    public Map<String, Object> getQueueConfig(QueueConfiguration queueConfiguration) {
        return eventResource.getQueueConfig(queueConfiguration.getQueueType(), queueConfiguration.getQueueName());
    }

    public void deleteQueueConfig(QueueConfiguration queueConfiguration) {
        eventResource.deleteQueueConfig(queueConfiguration.getQueueType(), queueConfiguration.getQueueName());
    }

    public void putQueueConfig(QueueConfiguration queueConfiguration) {
        eventResource.putQueueConfig(queueConfiguration.getQueueType(), queueConfiguration.getQueueName());
    }

    public void registerEventHandler(EventHandler eventHandler) {
        eventClient.registerEventHandler(eventHandler);
    }

    public void updateEventHandler(EventHandler eventHandler) {
        eventClient.updateEventHandler(eventHandler);
    }

    public List<EventHandler> getEventHandlers(String event, boolean activeOnly) {
        return eventClient.getEventHandlers(event, activeOnly);
    }

    public void unregisterEventHandler(String name) {
        eventClient.unregisterEventHandler(name);
    }
}
