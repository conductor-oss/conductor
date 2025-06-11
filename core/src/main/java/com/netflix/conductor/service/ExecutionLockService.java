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
package com.netflix.conductor.service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.metrics.Monitors;

@Service
@Trace
public class ExecutionLockService {

    public class LockInstance implements AutoCloseable {
        private final String lockId;
        private final long lockLeaseEndTime;

        public LockInstance(String lockId, long lockLeaseEndTime) {
            this.lockId = lockId;
            this.lockLeaseEndTime = lockLeaseEndTime;
        }

        public void close() {
            releaseLock(lockId, lockLeaseEndTime);
            deleteLock(lockId);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionLockService.class);
    private final ConductorProperties properties;
    private final Lock distributedLock;
    private final long lockLeaseTime;
    private final long lockTimeToTry;

    // The conductor code can acquire the same lock multiple times in the same thread.
    // Each lockId has an associated ReentrantLock instance.
    private final Map<String, ReentrantLock> reentrantLocks = new HashMap<>();

    public ExecutionLockService(ConductorProperties properties, Lock distributedLock) {
        this.properties = properties;
        this.distributedLock = distributedLock;
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
    public LockInstance acquireLock(String lockId) {
        return acquireLock(lockId, lockTimeToTry, lockLeaseTime);
    }

    public LockInstance acquireLock(String lockId, long timeToTryMs) {
        return acquireLock(lockId, timeToTryMs, lockLeaseTime);
    }

    public LockInstance acquireLock(String lockId, long timeToTryMs, long leaseTimeMs) {
        if (!properties.isWorkflowExecutionLockEnabled()) {
            return new LockInstance(lockId, System.currentTimeMillis() + leaseTimeMs);
        }

        boolean acquireDistributedLock;

        // synchronized block must be non-blocking and run fast
        synchronized (reentrantLocks) {
            var reentrantLock = reentrantLocks.get(lockId);
            acquireDistributedLock =
                    reentrantLock == null || !reentrantLock.isHeldByCurrentThread();
        }

        // We only try to acquire the distributed lock
        // if the in-process lock is not already acquired by the current thread.
        // This is a remote db call so should be done outside the critical section
        if (acquireDistributedLock
                && !distributedLock.acquireLock(
                        lockId, timeToTryMs, leaseTimeMs, TimeUnit.MILLISECONDS)) {
            LOGGER.debug(
                    "Thread {} failed to acquire lock {}.", Thread.currentThread().getId(), lockId);
            Monitors.recordAcquireLockUnsuccessful();
            return null;
        }

        // synchronized block must be non-blocking and run fast
        synchronized (reentrantLocks) {
            var reentrantLock = reentrantLocks.computeIfAbsent(lockId, k -> new ReentrantLock());
            if (!reentrantLock.tryLock()) {
                // we hold the distributed lock
                // but failed to acquire the reentrant lock
                // this should never happen in practice
                throw new IllegalStateException("Failed to acquire the lock");
            }
            LOGGER.debug(
                    "Thread {} acquired lock {} with count {}.",
                    Thread.currentThread().getId(),
                    lockId,
                    reentrantLock.getHoldCount());
        }

        return new LockInstance(lockId, System.currentTimeMillis() + leaseTimeMs);
    }

    // made private to encourage use of the try-with-resources pattern
    private void releaseLock(String lockId, long lockLeaseEndTime) {
        if (!properties.isWorkflowExecutionLockEnabled()) {
            return;
        }

        var releaseDistributedLock = false;

        // synchronized block must be non-blocking and run fast
        synchronized (reentrantLocks) {
            var reentrantLock = reentrantLocks.get(lockId);
            if (reentrantLock == null || !reentrantLock.isHeldByCurrentThread()) {
                var message =
                        String.format(
                                "Thread %d tried to release lock to lockId %s which was not acquired by the thread.",
                                Thread.currentThread().getId(), lockId);
                LOGGER.warn(message, Thread.currentThread().getId(), lockId);
                throw new IllegalStateException(message);
            }
            if (reentrantLock.getHoldCount() == 1) {
                // If we are here, we can release the distributed lock, provided the lease time
                // has not expired.
                releaseDistributedLock = lockLeaseEndTime > System.currentTimeMillis();
                reentrantLocks.remove(lockId);
            }
            reentrantLock.unlock();
            LOGGER.debug("Thread {} released lock {}.", Thread.currentThread().getId(), lockId);
        }

        if (releaseDistributedLock) {
            // This is a remote db call so should be done outside the critical section.
            distributedLock.releaseLock(lockId);
        }
    }

    private void deleteLock(String lockId) {
        if (properties.isWorkflowExecutionLockEnabled()) {
            distributedLock.deleteLock(lockId);
            LOGGER.debug("Thread {} deleted lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }
}
