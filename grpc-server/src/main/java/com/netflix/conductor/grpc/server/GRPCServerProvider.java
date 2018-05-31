package com.netflix.conductor.grpc.server;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;

import javax.inject.Inject;
import javax.inject.Provider;

public class GRPCServerProvider implements Provider<GRPCServer> {
    private final TaskServiceGrpc.TaskServiceImplBase taskServiceImplBase;
    private final WorkflowServiceGrpc.WorkflowServiceImplBase workflowServiceImplBase;
    private final MetadataServiceGrpc.MetadataServiceImplBase metadataServiceImplBase;
    private final EventServiceGrpc.EventServiceImplBase eventServiceImplBase;
    private final Configuration configuration;

    @Inject
    public GRPCServerProvider(TaskServiceGrpc.TaskServiceImplBase taskImpl,
                              WorkflowServiceGrpc.WorkflowServiceImplBase workflowImpl,
                              MetadataServiceGrpc.MetadataServiceImplBase metaImpl,
                              EventServiceGrpc.EventServiceImplBase eventImpl,
                              Configuration conf) {
        this.taskServiceImplBase = taskImpl;
        this.workflowServiceImplBase = workflowImpl;
        this.metadataServiceImplBase = metaImpl;
        this.eventServiceImplBase = eventImpl;
        this.configuration = conf;
    }

    @Override
    public GRPCServer get() {
        return new GRPCServer(configuration,
                taskServiceImplBase,
                workflowServiceImplBase,
                metadataServiceImplBase,
                eventServiceImplBase);
    }
}
