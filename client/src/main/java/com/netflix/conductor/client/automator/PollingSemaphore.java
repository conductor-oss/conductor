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
package com.netflix.conductor.client.automator;

import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class wrapping a semaphore which holds the number of permits available for polling and
 * executing tasks.
 */
class PollingSemaphore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingSemaphore.class);
    private final Semaphore semaphore;

    PollingSemaphore(int numSlots) {
        LOGGER.debug("Polling semaphore initialized with {} permits", numSlots);
        semaphore = new Semaphore(numSlots);
    }

    /** Signals that processing is complete and the specified number of permits can be released. */
    void complete(int numSlots) {
        LOGGER.debug("Completed execution; releasing permit");
        semaphore.release(numSlots);
    }

    /**
     * Gets the number of threads available for processing.
     *
     * @return number of available permits
     */
    int availableSlots() {
        int available = semaphore.availablePermits();
        LOGGER.debug("Number of available permits: {}", available);
        return available;
    }

    /**
     * Signals if processing is allowed based on whether specified number of permits can be
     * acquired.
     *
     * @param numSlots the number of permits to acquire
     * @return {@code true} - if permit is acquired {@code false} - if permit could not be acquired
     */
    public boolean acquireSlots(int numSlots) {
        boolean acquired = semaphore.tryAcquire(numSlots);
        LOGGER.debug("Trying to acquire {} permit: {}", numSlots, acquired);
        return acquired;
    }
}
