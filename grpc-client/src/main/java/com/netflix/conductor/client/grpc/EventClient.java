/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.client.grpc;

import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.EventServicePb;
import com.netflix.conductor.proto.EventHandlerPb;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

public class EventClient extends ClientBase {

    private final EventServiceGrpc.EventServiceBlockingStub stub;

    public EventClient(String address, int port) {
        super(address, port);
        this.stub = EventServiceGrpc.newBlockingStub(this.channel);
    }

    /**
     * Register an event handler with the server
     *
     * @param eventHandler the event handler definition
     */
    public void registerEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler, "Event handler definition cannot be null");
        stub.addEventHandler(
                EventServicePb.AddEventHandlerRequest.newBuilder()
                        .setHandler(protoMapper.toProto(eventHandler))
                        .build());
    }

    /**
     * Updates an existing event handler
     *
     * @param eventHandler the event handler to be updated
     */
    public void updateEventHandler(EventHandler eventHandler) {
        Preconditions.checkNotNull(eventHandler, "Event handler definition cannot be null");
        stub.updateEventHandler(
                EventServicePb.UpdateEventHandlerRequest.newBuilder()
                        .setHandler(protoMapper.toProto(eventHandler))
                        .build());
    }

    /**
     * @param event name of the event
     * @param activeOnly if true, returns only the active handlers
     * @return Returns the list of all the event handlers for a given event
     */
    public Iterator<EventHandler> getEventHandlers(String event, boolean activeOnly) {
        Preconditions.checkArgument(StringUtils.isNotBlank(event), "Event cannot be blank");

        EventServicePb.GetEventHandlersForEventRequest.Builder request =
                EventServicePb.GetEventHandlersForEventRequest.newBuilder()
                        .setEvent(event)
                        .setActiveOnly(activeOnly);
        Iterator<EventHandlerPb.EventHandler> it = stub.getEventHandlersForEvent(request.build());
        return Iterators.transform(it, protoMapper::fromProto);
    }

    /**
     * Removes the event handler from the conductor server
     *
     * @param name the name of the event handler
     */
    public void unregisterEventHandler(String name) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "Name cannot be blank");
        stub.removeEventHandler(
                EventServicePb.RemoveEventHandlerRequest.newBuilder().setName(name).build());
    }
}
