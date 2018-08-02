package com.netflix.conductor.grpc.server;

import com.google.inject.AbstractModule;

import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;
import com.netflix.conductor.grpc.server.service.EventServiceImpl;
import com.netflix.conductor.grpc.server.service.HealthServiceImpl;
import com.netflix.conductor.grpc.server.service.MetadataServiceImpl;
import com.netflix.conductor.grpc.server.service.TaskServiceImpl;
import com.netflix.conductor.grpc.server.service.WorkflowServiceImpl;

import io.grpc.health.v1.HealthGrpc;

public class GRPCModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(HealthGrpc.HealthImplBase.class).to(HealthServiceImpl.class);

        bind(EventServiceGrpc.EventServiceImplBase.class).to(EventServiceImpl.class);
        bind(MetadataServiceGrpc.MetadataServiceImplBase.class).to(MetadataServiceImpl.class);
        bind(TaskServiceGrpc.TaskServiceImplBase.class).to(TaskServiceImpl.class);
        bind(WorkflowServiceGrpc.WorkflowServiceImplBase.class).to(WorkflowServiceImpl.class);

        bind(GRPCServerConfiguration.class).to(GRPCServerSystemConfiguration.class);
        bind(GRPCServerProvider.class);
    }
}
