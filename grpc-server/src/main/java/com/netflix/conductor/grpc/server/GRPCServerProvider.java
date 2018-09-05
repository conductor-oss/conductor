package com.netflix.conductor.grpc.server;

import com.google.common.collect.ImmutableList;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

import io.grpc.BindableService;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.protobuf.services.ProtoReflectionService;

public class GRPCServerProvider implements Provider<Optional<GRPCServer>> {

    private final GRPCServerConfiguration configuration;
    private final BindableService healthServiceImpl;
    private final BindableService eventServiceImpl;
    private final BindableService metadataServiceImpl;
    private final BindableService taskServiceImpl;
    private final BindableService workflowServiceImpl;

    @Inject
    public GRPCServerProvider(
            GRPCServerConfiguration grpcServerConfiguration,
            HealthGrpc.HealthImplBase healthServiceImpl,
            EventServiceGrpc.EventServiceImplBase eventServiceImpl,
            MetadataServiceGrpc.MetadataServiceImplBase metadataServiceImpl,
            TaskServiceGrpc.TaskServiceImplBase taskServiceImpl,
            WorkflowServiceGrpc.WorkflowServiceImplBase workflowServiceImpl
    ) {
        this.configuration = grpcServerConfiguration;
        this.healthServiceImpl = healthServiceImpl;

        this.eventServiceImpl = eventServiceImpl;
        this.metadataServiceImpl = metadataServiceImpl;
        this.taskServiceImpl = taskServiceImpl;
        this.workflowServiceImpl = workflowServiceImpl;
    }

    @Override
    public Optional<GRPCServer> get() {
        return configuration.isEnabled() ?
                Optional.of(buildGRPCServer(configuration))
                : Optional.empty();
    }

    private GRPCServer buildGRPCServer(GRPCServerConfiguration grpcServerConfiguration) {
        ImmutableList.Builder<BindableService> services = ImmutableList.<BindableService>builder().add(
                healthServiceImpl,
                eventServiceImpl,
                metadataServiceImpl,
                taskServiceImpl,
                workflowServiceImpl);

        if (grpcServerConfiguration.isReflectionEnabled()) {
            services.add(ProtoReflectionService.newInstance());
        }

        return new GRPCServer(
                grpcServerConfiguration.getPort(),
                services.build().toArray(new BindableService[]{})
        );
    }
}
