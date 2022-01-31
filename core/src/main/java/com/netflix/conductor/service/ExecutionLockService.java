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
package com.netflix.conductor.service;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.metrics.Monitors;

@Service
public class ExecutionLockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionLockService.class);
    private final ConductorProperties properties;
    private final Lock lock;
    private final long lockLeaseTime;
    private final long lockTimeToTry;

    @Autowired
    public ExecutionLockService(ConductorProperties properties, Lock lock) {
        this.properties = properties;
        this.lock = lock;
        this.lockLeaseTime = properties.getLockLeaseTime().toMillis();
        this.lockTimeToTry = properties.getLockTimeToTry().toMillis();
    }

    /**
     * Tries to acquire lock with reasonable timeToTry duration and lease time. Exits if a lock
     * cannot be acquired. Considering that the workflow decide can be triggered through multiple
     * entry points, and periodically through the sweeper service, do not block on acquiring the
     * lock, as the order of execution of decides on a workflow doesn't matter.
     *
     * @param lockId
     * @return
     */
    public boolean acquireLock(String lockId) {
        return acquireLock(lockId, lockTimeToTry, lockLeaseTime);
    }

    public boolean acquireLock(String lockId, long timeToTryMs) {
        return acquireLock(lockId, timeToTryMs, lockLeaseTime);
    }

    public boolean acquireLock(String lockId, long timeToTryMs, long leaseTimeMs) {
        if (properties.isWorkflowExecutionLockEnabled()) {
            if (!lock.acquireLock(lockId, timeToTryMs, leaseTimeMs, TimeUnit.MILLISECONDS)) {
                LOGGER.debug(
                        "Thread {} failed to acquire lock to lockId {}.",
                        Thread.currentThread().getId(),
                        lockId);
                Monitors.recordAcquireLockUnsuccessful();
                return false;
            }
            LOGGER.debug(
                    "Thread {} acquired lock to lockId {}.",
                    Thread.currentThread().getId(),
                    lockId);
        }
        return true;
    }

    /**
     * Blocks until it gets the lock for workflowId
     *
     * @param lockId
     */
    public void waitForLock(String lockId) {
        if (properties.isWorkflowExecutionLockEnabled()) {
            lock.acquireLock(lockId);
            LOGGER.debug(
                    "Thread {} acquired lock to lockId {}.",
                    Thread.currentThread().getId(),
                    lockId);
        }
    }

    public void releaseLock(String lockId) {
        if (properties.isWorkflowExecutionLockEnabled()) {
            lock.releaseLock(lockId);
            LOGGER.debug(
                    "Thread {} released lock to lockId {}.",
                    Thread.currentThread().getId(),
                    lockId);
        }
    }

    public void deleteLock(String lockId) {
        if (properties.isWorkflowExecutionLockEnabled()) {
            lock.deleteLock(lockId);
            LOGGER.debug("Thread {} deleted lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }
}
