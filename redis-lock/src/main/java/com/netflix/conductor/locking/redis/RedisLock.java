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

package com.netflix.conductor.locking.redis;

import com.google.inject.Inject;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.locking.redis.config.RedisLockConfiguration;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RedisLock implements Lock {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLock.class);

    private RedisLockConfiguration configuration;
    private RedissonClient redisson;
    private static String LOCK_NAMESPACE = "";

    @Inject
    public RedisLock(Redisson redisson, RedisLockConfiguration configuration) {
        this.configuration = configuration;
        this.redisson = redisson;
        LOCK_NAMESPACE = configuration.getProperty("workflow.decider.locking.namespace", "");
    }

    @Override
    public void acquireLock(String lockId) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        lock.lock();
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            return lock.tryLock(timeToTry, unit);
        } catch (Exception e) {
            return handleAcquireLockFailure(lockId, e);
        }
    }

    /**
     *
     * @param lockId resource to lock on
     * @param timeToTry blocks up to timeToTry duration in attempt to acquire the lock
     * @param leaseTime Lock lease expiration duration. Redisson default is -1, meaning it holds the lock until explicitly unlocked.
     * @param unit time unit
     * @return
     */
    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            return lock.tryLock(timeToTry, leaseTime, unit);
        } catch (Exception e) {
            return handleAcquireLockFailure(lockId, e);
        }
    }

    @Override
    public void releaseLock(String lockId) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException e) {
            // Releasing a lock twice using Redisson can cause this exception, which can be ignored.
        }
    }

    @Override
    public void deleteLock(String lockId) {
        // Noop for Redlock algorithm as releaseLock / unlock deletes it.
    }

    private String parseLockId(String lockId) {
        if (StringUtils.isEmpty(lockId)) {
            throw new IllegalArgumentException("lockId cannot be NULL or empty: lockId=" + lockId);
        }
        return LOCK_NAMESPACE + "." + lockId;
    }

    private boolean handleAcquireLockFailure(String lockId, Exception e) {
        LOGGER.error("Failed to acquireLock for lockId: {}", lockId, e);
        Monitors.recordAcquireLockFailure(e.getClass().getName());
        // A Valid failure to acquire lock when another thread has acquired it returns false.
        // However, when an exception is thrown while acquiring lock, due to connection or others issues,
        // we can optionally continue without a "lock" to not block executions until Locking service is available.
        return configuration.ignoreLockingExceptions();
    }
}
