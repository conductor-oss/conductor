package com.netflix.conductor.grpc.server.service;

import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.EventServicePb;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.proto.EventHandlerPb;
import com.netflix.conductor.service.EventService;
import com.netflix.conductor.service.MetadataService;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;

public class EventServiceImpl extends EventServiceGrpc.EventServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);
    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;

    private final EventService eventService;
    private final MetadataService metadataService;

    @Inject
    public EventServiceImpl(MetadataService metadataService, EventService eventService) {
        this.metadataService = metadataService;
        this.eventService = eventService;
    }

    @Override
    public void addEventHandler(EventServicePb.AddEventHandlerRequest req, StreamObserver<EventServicePb.AddEventHandlerResponse> response) {
        metadataService.addEventHandler(PROTO_MAPPER.fromProto(req.getHandler()));
        response.onNext(EventServicePb.AddEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void updateEventHandler(EventServicePb.UpdateEventHandlerRequest req, StreamObserver<EventServicePb.UpdateEventHandlerResponse> response) {
        metadataService.updateEventHandler(PROTO_MAPPER.fromProto(req.getHandler()));
        response.onNext(EventServicePb.UpdateEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void removeEventHandler(EventServicePb.RemoveEventHandlerRequest req, StreamObserver<EventServicePb.RemoveEventHandlerResponse> response) {
        metadataService.removeEventHandlerStatus(req.getName());
        response.onNext(EventServicePb.RemoveEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void getEventHandlers(EventServicePb.GetEventHandlersRequest req, StreamObserver<EventHandlerPb.EventHandler> response) {
        metadataService.getAllEventHandlers().stream().map(PROTO_MAPPER::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getEventHandlersForEvent(EventServicePb.GetEventHandlersForEventRequest req, StreamObserver<EventHandlerPb.EventHandler> response) {
        metadataService.getEventHandlersForEvent(req.getEvent(), req.getActiveOnly())
                .stream().map(PROTO_MAPPER::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getQueues(EventServicePb.GetQueuesRequest req, StreamObserver<EventServicePb.GetQueuesResponse> response) {
        response.onNext(
                EventServicePb.GetQueuesResponse.newBuilder()
                .putAllEventToQueueUri((Map<String, String>) eventService.getEventQueues(false))
                .build()
        );
        response.onCompleted();
    }

    @Override
    public void getQueueSizes(EventServicePb.GetQueueSizesRequest req, StreamObserver<EventServicePb.GetQueueSizesResponse> response) {
        EventServicePb.GetQueueSizesResponse.Builder builder = EventServicePb.GetQueueSizesResponse.newBuilder();
        for (Map.Entry<String, Map<String, Long>> pair : ((Map<String, Map<String, Long>>)eventService.getEventQueues(true)).entrySet()) {
            builder.putEventToQueueInfo(pair.getKey(),
                    EventServicePb.GetQueueSizesResponse.QueueInfo.newBuilder()
                            .putAllQueueSizes(pair.getValue()).build()
            );
        }
        response.onNext(builder.build());
        response.onCompleted();
    }

    @Override
    public void getQueueProviders(EventServicePb.GetQueueProvidersRequest req, StreamObserver<EventServicePb.GetQueueProvidersResponse> response) {
        response.onNext(
                EventServicePb.GetQueueProvidersResponse.newBuilder()
                        .addAllProviders(eventService.getEventQueueProviders())
                        .build()
        );
        response.onCompleted();
    }
}
