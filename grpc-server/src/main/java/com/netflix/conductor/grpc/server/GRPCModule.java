package com.netflix.conductor.grpc.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.CoreModule;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import com.netflix.conductor.dao.es5.index.ElasticSearchModuleV5;
import com.netflix.conductor.dao.mysql.MySQLWorkflowModule;
import com.netflix.conductor.grpc.EventServiceGrpc;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Singleton;

public class GRPCModule extends AbstractModule {

    // FIXME Eventually this should be shared with the Jersey code and provided by the server module.
    private final int maxThreads = 50;
    private ExecutorService es;


    @Override
    protected void configure() {
        configureExecutorService();

        install(new CoreModule());
        install(new ElasticSearchModuleV5());
        install(new MySQLWorkflowModule());
        bind(Configuration.class).to(SystemPropertiesConfiguration.class).in(Singleton.class);
        bind(TaskServiceGrpc.TaskServiceImplBase.class).to(TaskServiceImpl.class);
        bind(MetadataServiceGrpc.MetadataServiceImplBase.class).to(MetadataServiceImpl.class);
        bind(WorkflowServiceGrpc.WorkflowServiceImplBase.class).to(WorkflowServiceImpl.class);
        bind(GRPCServer.class).toProvider(GRPCServerProvider.class).asEagerSingleton();
        bind(EventServiceGrpc.EventServiceImplBase.class).to(EventServiceImpl.class);
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
