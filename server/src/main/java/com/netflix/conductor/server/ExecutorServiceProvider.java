package com.netflix.conductor.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.netflix.conductor.core.config.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.inject.Inject;
import javax.inject.Provider;

public class ExecutorServiceProvider implements Provider<ExecutorService> {
    private static final int MAX_THREADS = 50;

    private final Configuration configuration;
    private final ExecutorService executorService;

    @Inject
    public ExecutorServiceProvider(Configuration configuration) {
        this.configuration = configuration;
        // TODO Use configuration to set max threads.
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(MAX_THREADS, buildThreadFactory());
    }

    @Override
    public ExecutorService get() {
        return executorService;
    }

    private ThreadFactory buildThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat("conductor-worker-%d")
                .setDaemon(true)
                .build();
    }
}
