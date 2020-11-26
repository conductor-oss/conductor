/*
 * Copyright 2020 Netflix, Inc.
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

/**
 * This interface provides a means to help handle objects, especially those that are injected, that have a lifecycle
 * component.  Guice explicitly does not support this and recommends a patter much like this.  This should be used by
 * anything that needs to create resources or clean them up when the application is started or stopped, such as server
 * listeners, clients, etc.
 *
 * @see <a href="https://github.com/google/guice/wiki/ModulesShouldBeFastAndSideEffectFree">ModulesShouldBeFastAndSideEffectFree</a>
 */
public interface Lifecycle {

    Logger LOGGER = LoggerFactory.getLogger(Lifecycle.class);

    default void start() throws Exception {
        registerShutdownHook();
    }

    void stop() throws Exception;

    default void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (Exception e) {
                LOGGER.error("Error when trying to shutdown a lifecycle component: " + this.getClass().getName(), e);
            }
        }));
    }
}
