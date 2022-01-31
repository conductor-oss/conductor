/*
 * Copyright 2022 Netflix, Inc.
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

    @Override
    public final boolean isRunning() {
        return running;
    }

    public void doStart() {}

    public void doStop() {}
}
