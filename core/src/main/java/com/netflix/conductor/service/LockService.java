package com.netflix.conductor.service;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class LockService  {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockService.class);
    private final Configuration config;
    private final Lock lock;

    @Inject
    public LockService(Configuration config, Lock lock) {
        this.config = config;
        this.lock = lock;
    }

    public boolean acquireLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
            if (!lock.acquireLock(lockId, 2, TimeUnit.MILLISECONDS)) {
                LOGGER.info("Thread {} failed to acquire lock to lockId {}.", Thread.currentThread().getId(), lockId);
                Monitors.recordAcquireLockUnsuccessful(lockId);
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
            lock.acquireLock(lockId);
            LOGGER.debug("Thread {} acquired lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }

    public void releaseLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
                lock.releaseLock(lockId);
                LOGGER.debug("Thread {} released lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }
}
