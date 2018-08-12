package com.netflix.conductor.grpc.server;

import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

import io.grpc.BindableService;
import io.grpc.health.v1.HealthGrpc;

public class GRPCServerProvider implements Provider<Optional<GRPCServer>> {

    private final GRPCServerConfiguration configuration;
    private final BindableService healthServiceImpl;
    private final BindableService eventServiceImpl;
    private final BindableService metadataServiceImpl;
    private final BindableService taskServiceImpl;
    private final BindableService workflowServiceImpl;

    @Inject
    public GRPCServerProvider(
            GRPCServerConfiguration conf,
            HealthGrpc.HealthImplBase healthServiceImpl,
            EventServiceGrpc.EventServiceImplBase eventServiceImpl,
            MetadataServiceGrpc.MetadataServiceImplBase metadataServiceImpl,
            TaskServiceGrpc.TaskServiceImplBase taskServiceImpl,
            WorkflowServiceGrpc.WorkflowServiceImplBase workflowServiceImpl
    ) {
        this.configuration = conf;
        this.healthServiceImpl = healthServiceImpl;

        this.eventServiceImpl = eventServiceImpl;
        this.metadataServiceImpl = metadataServiceImpl;
        this.taskServiceImpl = taskServiceImpl;
        this.workflowServiceImpl = workflowServiceImpl;
    }

    @Override
    public Optional<GRPCServer> get() {
        return configuration.isEnabled() ?
                Optional.of(
                        new GRPCServer(
                                configuration.getPort(),
                                healthServiceImpl,
                                eventServiceImpl,
                                metadataServiceImpl,
                                taskServiceImpl,
                                workflowServiceImpl
                        ))
                : Optional.empty();
    }
}
