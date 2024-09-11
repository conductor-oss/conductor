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
import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;
import com.netflix.conductor.common.metadata.events.EventHandler;

import com.fasterxml.jackson.core.type.TypeReference;

class EventResource {

    private final ConductorClient client;

    EventResource(ConductorClient client) {
        this.client = client;
    }

    List<EventHandler> getEventHandlers() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/event")
                .build();

        ConductorClientResponse<List<EventHandler>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    void handleIncomingEvent(Map<String, Object> body) {
        Objects.requireNonNull(body, "EventHandler cannot be null");
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/event/handleIncomingEvent")
                .body(body)
                .build();
        client.execute(request);
    }

    void removeEventHandlerStatus(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/event/{name}")
                .addPathParam("name", name)
                .build();
        client.execute(request);
    }

    Map<String, Object> getQueueConfig(String queueType, String queueName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path( "/event/queue/config/{queueType}/{queueName}")
                .addPathParam("queueType", queueType)
                .addPathParam("queueName", queueName)
                .build();
        ConductorClientResponse<Map<String, Object>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    void deleteQueueConfig(String queueType, String queueName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path( "/event/queue/config/{queueType}/{queueName}")
                .addPathParam("queueType", queueType)
                .addPathParam("queueName", queueName)
                .build();

        client.execute(request);
    }

    void putQueueConfig(String queueType, String queueName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path( "/event/queue/config/{queueType}/{queueName}")
                .addPathParam("queueType", queueType)
                .addPathParam("queueName", queueName)
                .build();

        client.execute(request);
    }
}
