package com.netflix.conductor.grpc.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class GRPCModule extends AbstractModule {

    // FIXME Eventually this should be shared with the Jersey code and provided by the server module.
    private final int maxThreads = 50;
    private final Configuration configuration;
    private ExecutorService es;

    public GRPCModule(Configuration configuration){
        this.configuration = configuration;
    }

    @Override
    protected void configure() {
        configureExecutorService();

        bind(Configuration.class).toInstance(configuration);
        bind(TaskServiceGrpc.TaskServiceImplBase.class).to(TaskServiceImpl.class);
        bind(MetadataServiceGrpc.MetadataServiceImplBase.class).to(MetadataServiceImpl.class);
        bind(WorkflowServiceGrpc.WorkflowServiceImplBase.class).to(WorkflowServiceImpl.class);
        bind(EventServiceGrpc.EventServiceImplBase.class).to(EventServiceImpl.class);

        bind(GRPCServer.class).to(GRPCServer.class);
    }

    @Provides
    public ExecutorService getExecutorService(){
        return this.es;
    }

    private void configureExecutorService(){
        AtomicInteger count = new AtomicInteger(0);
        this.es = java.util.concurrent.Executors.newFixedThreadPool(maxThreads, runnable -> {
            Thread conductorWorkerThread = new Thread(runnable);
            conductorWorkerThread.setName("conductor-worker-" + count.getAndIncrement());
            return conductorWorkerThread;
        });
    }
}
