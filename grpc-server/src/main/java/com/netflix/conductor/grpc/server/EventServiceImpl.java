package com.netflix.conductor.grpc.server;

import com.google.protobuf.Empty;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.EventServicePb;
import com.netflix.conductor.proto.EventHandlerPb;
import com.netflix.conductor.service.MetadataService;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import java.util.Map;

public class EventServiceImpl extends EventServiceGrpc.EventServiceImplBase {
    private MetadataService service;
    private EventProcessor ep;

    @Inject
    public EventServiceImpl(MetadataService service, EventProcessor ep) {
        this.service = service;
        this.ep = ep;
    }

    @Override
    public void addEventHandler(EventHandlerPb.EventHandler req, StreamObserver<Empty> response) {
        service.addEventHandler(ProtoMapper.fromProto(req));
        response.onCompleted();
    }

    @Override
    public void updateEventHandler(EventHandlerPb.EventHandler req, StreamObserver<Empty> response) {
        service.updateEventHandler(ProtoMapper.fromProto(req));
        response.onCompleted();
    }

    @Override
    public void removeEventHandler(EventServicePb.RemoveEventHandlerRequest req, StreamObserver<Empty> response) {
        service.removeEventHandlerStatus(req.getName());
    }

    @Override
    public void getEventHandlers(Empty req, StreamObserver<EventHandlerPb.EventHandler> response) {
        for (EventHandler eh : service.getEventHandlers()) {
            response.onNext(ProtoMapper.toProto(eh));
        }
        response.onCompleted();
    }

    @Override
    public void getEventHandlersForEvent(EventServicePb.GetEventHandlersRequest req, StreamObserver<EventHandlerPb.EventHandler> response) {
        for (EventHandler eh : service.getEventHandlersForEvent(req.getEvent(), req.getActiveOnly())) {
            response.onNext(ProtoMapper.toProto(eh));
        }
        response.onCompleted();
    }

    @Override
    public void getQueues(Empty req, StreamObserver<EventServicePb.GetQueuesResponse> response) {
        response.onNext(
                EventServicePb.GetQueuesResponse.newBuilder()
                .putAllEventToQueueUri(ep.getQueues())
                .build()
        );
        response.onCompleted();
    }

    @Override
    public void getQueueSizes(Empty req, StreamObserver<EventServicePb.GetQueueSizesResponse> response) {
        EventServicePb.GetQueueSizesResponse.Builder builder = EventServicePb.GetQueueSizesResponse.newBuilder();
        for (Map.Entry<String, Map<String, Long>> pair : ep.getQueueSizes().entrySet()) {
            EventServicePb.GetQueueSizesResponse.QueueInfo info =
                    EventServicePb.GetQueueSizesResponse.QueueInfo.newBuilder()
                            .putAllQueueSizes(pair.getValue())
                            .build();
            builder.putEventToQueueInfo(pair.getKey(), info);
        }
        response.onNext(builder.build());
        response.onCompleted();
    }

    @Override
    public void getQueueProviders(Empty req, StreamObserver<EventServicePb.GetQueueProvidersResponse> response) {
        response.onNext(
                EventServicePb.GetQueueProvidersResponse.newBuilder()
                        .addAllProviders(EventQueues.providers())
                        .build()
        );
        response.onCompleted();
    }
}
