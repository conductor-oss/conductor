/*
 * Copyright 2022 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public abstract class LifecycleAwareComponent implements SmartLifecycle {

    private volatile boolean running = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleAwareComponent.class);

    @Override
    public final void start() {
        running = true;
        LOGGER.info("{} started.", getClass().getSimpleName());
        doStart();
    }

    @Override
    public final void stop() {
        running = false;
        LOGGER.info("{} stopped.", getClass().getSimpleName());
        doStop();
    }

    /**
     * Called by Spring's {@code DefaultLifecycleProcessor}, which stops the members of a phase one
     * after another on a single thread. A component that drains in-flight work returns that drain
     * from {@link #beginStop()} so it runs on its own thread. Components then drain in parallel,
     * and {@code spring.lifecycle.timeout-per-shutdown-phase} bounds the total wait.
     */
    @Override
    public final void stop(Runnable callback) {
        running = false;
        LOGGER.info("{} stopped.", getClass().getSimpleName());
        Runnable drain;
        try {
            drain = beginStop();
        } catch (RuntimeException | Error e) {
            callback.run();
            throw e;
        }
        if (drain == null) {
            callback.run();
            return;
        }
        Thread.ofPlatform()
                .name(getClass().getSimpleName() + "-drain")
                .daemon(true)
                .start(
                        () -> {
                            try {
                                drain.run();
                            } catch (Throwable t) {
                                LOGGER.error(
                                        "{} failed to drain on stop",
                                        getClass().getSimpleName(),
                                        t);
                            } finally {
                                callback.run();
                            }
                        });
    }

    @Override
    public final boolean isRunning() {
        return running;
    }

    public void doStart() {}

    /** Synchronous stop, used by {@link #stop()}. Must not return before the stop is complete. */
    public void doStop() {}

    /**
     * Non-blocking part of the asynchronous stop used by {@link #stop(Runnable)}. Signals the
     * component to stop and returns the blocking drain to run off Spring's lifecycle thread, or
     * {@code null} when nothing is left to wait for. The default runs {@link #doStop()} inline.
     */
    protected Runnable beginStop() {
        doStop();
        return null;
    }
}
