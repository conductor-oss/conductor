package com.netflix.conductor.grpc.server.service;

import com.google.protobuf.Empty;

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
    private static final Logger logger = LoggerFactory.getLogger(EventServiceImpl.class);
    private static final ProtoMapper protoMapper = ProtoMapper.INSTANCE;
    private static final GRPCHelper grpcHelper = new GRPCHelper(logger);

    private final MetadataService service;
    private final EventProcessor ep;

    @Inject
    public EventServiceImpl(MetadataService service, EventProcessor ep) {
        this.service = service;
        this.ep = ep;
    }

    @Override
    public void addEventHandler(EventServicePb.AddEventHandlerRequest req, StreamObserver<EventServicePb.AddEventHandlerResponse> response) {
        service.addEventHandler(protoMapper.fromProto(req.getHandler()));
        response.onNext(EventServicePb.AddEventHandlerResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void updateEventHandler(EventServicePb.UpdateEventHandlerRequest req, StreamObserver<EventServicePb.UpdateEventHandlerResponse> response) {
        service.updateEventHandler(protoMapper.fromProto(req.getHandler()));
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
        service.getEventHandlers().stream().map(protoMapper::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getEventHandlersForEvent(EventServicePb.GetEventHandlersForEventRequest req, StreamObserver<EventHandlerPb.EventHandler> response) {
        service.getEventHandlersForEvent(req.getEvent(), req.getActiveOnly())
                .stream().map(protoMapper::toProto).forEach(response::onNext);
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
                        .addAllProviders(EventQueues.providers())
                        .build()
        );
        response.onCompleted();
    }
}
