/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.grpc.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.EventServicePb;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.proto.EventHandlerPb;
import com.netflix.conductor.service.MetadataService;

import io.grpc.stub.StreamObserver;

@Service("grpcEventService")
public class EventServiceImpl extends EventServiceGrpc.EventServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);

    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;

    private final MetadataService metadataService;

    public EventServiceImpl(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public void addEventHandler(
            EventServicePb.AddEventHandlerRequest req,
            StreamObserver<EventServicePb.AddEventHandlerResponse> response) {
        metadataService.addEventHandler(PROTO_MAPPER.fromProto(req.getHandler()));
        response.onNext(EventServicePb.AddEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void updateEventHandler(
            EventServicePb.UpdateEventHandlerRequest req,
            StreamObserver<EventServicePb.UpdateEventHandlerResponse> response) {
        metadataService.updateEventHandler(PROTO_MAPPER.fromProto(req.getHandler()));
        response.onNext(EventServicePb.UpdateEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void removeEventHandler(
            EventServicePb.RemoveEventHandlerRequest req,
            StreamObserver<EventServicePb.RemoveEventHandlerResponse> response) {
        metadataService.removeEventHandlerStatus(req.getName());
        response.onNext(EventServicePb.RemoveEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void getEventHandlers(
            EventServicePb.GetEventHandlersRequest req,
            StreamObserver<EventHandlerPb.EventHandler> response) {
        metadataService.getAllEventHandlers().stream()
                .map(PROTO_MAPPER::toProto)
                .forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getEventHandlersForEvent(
            EventServicePb.GetEventHandlersForEventRequest req,
            StreamObserver<EventHandlerPb.EventHandler> response) {
        metadataService.getEventHandlersForEvent(req.getEvent(), req.getActiveOnly()).stream()
                .map(PROTO_MAPPER::toProto)
                .forEach(response::onNext);
        response.onCompleted();
    }
}
