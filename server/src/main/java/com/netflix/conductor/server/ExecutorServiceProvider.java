package com.netflix.conductor.server;

import com.netflix.conductor.core.config.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Provider;

public class ExecutorServiceProvider implements Provider<ExecutorService> {
    private static final int MAX_THREADS = 50;

    private final Configuration configuration;
    private final ExecutorService executorService;

    @Inject
    public ExecutorServiceProvider(Configuration configuration){
        this.configuration = configuration;

        AtomicInteger count = new AtomicInteger(0);
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(MAX_THREADS, runnable -> {
            Thread conductorWorkerThread = new Thread(runnable);
            conductorWorkerThread.setName("conductor-worker-" + count.getAndIncrement());
            return conductorWorkerThread;
        });
    }
    @Override
    public ExecutorService get() {
        return executorService;
    }
}
