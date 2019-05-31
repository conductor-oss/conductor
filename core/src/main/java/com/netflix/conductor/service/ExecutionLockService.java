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
    public static final String LOCK_NAMESPACE = "executionlock";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionLockService.class);
    private final Configuration config;
    private final Provider<Lock> lockProvider;

    @Inject
    public ExecutionLockService(Configuration config, Provider<Lock> lockProvider) {
        this.config = config;
        this.lockProvider = lockProvider;
    }

    public boolean acquireLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
            Lock  lock = (Lock)lockProvider.get();
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
            Lock  lock = (Lock)lockProvider.get();
            lock.acquireLock(lockId);
            LOGGER.debug("Thread {} acquired lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }

    public void releaseLock(String lockId) {
        if (config.enableWorkflowExecutionLock()) {
            Lock  lock = (Lock)lockProvider.get();
            lock.releaseLock(lockId);
            LOGGER.debug("Thread {} released lock to lockId {}.", Thread.currentThread().getId(), lockId);
        }
    }
}
