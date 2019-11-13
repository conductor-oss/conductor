/*
 * Copyright (c) 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.service;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.TimeUnit;

public class ExecutionLockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionLockService.class);
    private final Configuration config;
    private final Provider<Lock> lockProvider;
    private static long LOCK_TIME_TO_TRY;
    private static long LOCK_LEASE_TIME;

    @Inject
    public ExecutionLockService(Configuration config, Provider<Lock> lockProvider) {
        this.config = config;
        this.lockProvider = lockProvider;
        LOCK_LEASE_TIME = config.getLongProperty("workflow.locking.lease.time.ms", 60000);
        LOCK_TIME_TO_TRY = config.getLongProperty("workflow.locking.time.to.try.ms", 500);
    }

    /**
     * Tries to acquire lock with reasonable timeToTry duration and lease time. Exits if a lock cannot be acquired.
     * Considering that the workflow decide can be triggered through multiple entry points, and periodically through the sweeper service,
     * do not block on acquiring the lock, as the order of execution of decides on a workflow doesn't matter.
     * @param lockId
     * @return
     */
    public boolean acquireLock(String lockId) {
        return acquireLock(lockId, LOCK_TIME_TO_TRY, LOCK_LEASE_TIME);
    }

    public boolean acquireLock(String lockId, long timeToTryMs) {
        return acquireLock(lockId, timeToTryMs, LOCK_LEASE_TIME);
    }

    public boolean acquireLock(String lockId, long timeToTryMs, long leaseTimeMs) {
        if (config.enableWorkflowExecutionLock()) {
            Lock lock = lockProvider.get();
            if (!lock.acquireLock(lockId, timeToTryMs, leaseTimeMs, TimeUnit.MILLISECONDS)) {
                LOGGER.debug("Thread {} failed to acquire lock to lockId {}.", Thread.currentThread().getId(), lockId);
                Monitors.recordAcquireLockUnsuccessful();
                return false;
            }
            LOGGER.debug("Thread {} acquired lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
        return true;
    }

    /**
     * Blocks until it gets the lock for workflowId
     * @param lockId
     */
    public void waitForLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
            Lock  lock = lockProvider.get();
            lock.acquireLock(lockId);
            LOGGER.debug("Thread {} acquired lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }

    public void releaseLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
            Lock lock = lockProvider.get();
            lock.releaseLock(lockId);
            LOGGER.debug("Thread {} released lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }

    public void deleteLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
            lockProvider.get().deleteLock(lockId);
            LOGGER.debug("Thread {} deleted lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }
}
