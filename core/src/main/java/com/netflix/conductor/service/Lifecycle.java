package com.netflix.conductor.service;

public interface Lifecycle {

    default void start() throws Exception {
        registerShutdownHook();
    }

    void stop() throws Exception;

    default void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (Exception e) {}
        }));
    }
}
