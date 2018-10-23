package com.netflix.conductor.grpc.server.service;

import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.EventServicePb;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.proto.EventHandlerPb;
import com.netflix.conductor.service.MetadataService;

import java.util.Map;

import javax.inject.Inject;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventServiceImpl extends EventServiceGrpc.EventServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);
    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;
    private static final GRPCHelper GRPC_HELPER = new GRPCHelper(LOGGER);

    private final MetadataService service;
    private final EventProcessor ep;
    private final EventQueues eventQueues;

    @Inject
    public EventServiceImpl(MetadataService service, EventProcessor ep, EventQueues eventQueues) {
        this.service = service;
        this.ep = ep;
        this.eventQueues = eventQueues;
    }

    @Override
    public void addEventHandler(EventServicePb.AddEventHandlerRequest req, StreamObserver<EventServicePb.AddEventHandlerResponse> response) {
        service.addEventHandler(PROTO_MAPPER.fromProto(req.getHandler()));
        response.onNext(EventServicePb.AddEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void updateEventHandler(EventServicePb.UpdateEventHandlerRequest req, StreamObserver<EventServicePb.UpdateEventHandlerResponse> response) {
        service.updateEventHandler(PROTO_MAPPER.fromProto(req.getHandler()));
        response.onNext(EventServicePb.UpdateEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void removeEventHandler(EventServicePb.RemoveEventHandlerRequest req, StreamObserver<EventServicePb.RemoveEventHandlerResponse> response) {
        service.removeEventHandlerStatus(req.getName());
        response.onNext(EventServicePb.RemoveEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void getEventHandlers(EventServicePb.GetEventHandlersRequest req, StreamObserver<EventHandlerPb.EventHandler> response) {
        service.getEventHandlers().stream().map(PROTO_MAPPER::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getEventHandlersForEvent(EventServicePb.GetEventHandlersForEventRequest req, StreamObserver<EventHandlerPb.EventHandler> response) {
        service.getEventHandlersForEvent(req.getEvent(), req.getActiveOnly())
                .stream().map(PROTO_MAPPER::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getQueues(EventServicePb.GetQueuesRequest req, StreamObserver<EventServicePb.GetQueuesResponse> response) {
        response.onNext(
                EventServicePb.GetQueuesResponse.newBuilder()
                .putAllEventToQueueUri(ep.getQueues())
                .build()
        );
        response.onCompleted();
    }

    @Override
    public void getQueueSizes(EventServicePb.GetQueueSizesRequest req, StreamObserver<EventServicePb.GetQueueSizesResponse> response) {
        EventServicePb.GetQueueSizesResponse.Builder builder = EventServicePb.GetQueueSizesResponse.newBuilder();
        for (Map.Entry<String, Map<String, Long>> pair : ep.getQueueSizes().entrySet()) {
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
                        .addAllProviders(eventQueues.getProviders())
                        .build()
        );
        response.onCompleted();
    }
}
