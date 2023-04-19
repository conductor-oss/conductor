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
package com.netflix.conductor.core.sync.local;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.core.sync.Lock;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class LocalOnlyLock implements Lock {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalOnlyLock.class);

    private static final CacheLoader<String, ReentrantLock> LOADER = key -> new ReentrantLock(true);
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> SCHEDULEDFUTURES =
            new ConcurrentHashMap<>();
    private static final LoadingCache<String, ReentrantLock> LOCKIDTOSEMAPHOREMAP =
            Caffeine.newBuilder().build(LOADER);
    private static final ThreadGroup THREAD_GROUP = new ThreadGroup("LocalOnlyLock-scheduler");
    private static final ThreadFactory THREAD_FACTORY =
            runnable -> new Thread(THREAD_GROUP, runnable);
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(1, THREAD_FACTORY);

    @Override
    public void acquireLock(String lockId) {
        LOGGER.trace("Locking {}", lockId);
        LOCKIDTOSEMAPHOREMAP.get(lockId).lock();
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        try {
            LOGGER.trace("Locking {} with timeout {} {}", lockId, timeToTry, unit);
            return LOCKIDTOSEMAPHOREMAP.get(lockId).tryLock(timeToTry, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        LOGGER.trace(
                "Locking {} with timeout {} {} for {} {}",
                lockId,
                timeToTry,
                unit,
                leaseTime,
                unit);
        if (acquireLock(lockId, timeToTry, unit)) {
            LOGGER.trace("Releasing {} automatically after {} {}", lockId, leaseTime, unit);
            SCHEDULEDFUTURES.put(
                    lockId, SCHEDULER.schedule(() -> deleteLock(lockId), leaseTime, unit));
            return true;
        }
        return false;
    }

    private void removeLeaseExpirationJob(String lockId) {
        ScheduledFuture<?> schedFuture = SCHEDULEDFUTURES.get(lockId);
        if (schedFuture != null && schedFuture.cancel(false)) {
            SCHEDULEDFUTURES.remove(lockId);
            LOGGER.trace("lockId {} removed from lease expiration job", lockId);
        }
    }

    @Override
    public void releaseLock(String lockId) {
        // Synchronized to prevent race condition between semaphore check and actual release
        synchronized (LOCKIDTOSEMAPHOREMAP) {
            if (LOCKIDTOSEMAPHOREMAP.getIfPresent(lockId) == null) {
                return;
            }
            LOGGER.trace("Releasing {}", lockId);
            LOCKIDTOSEMAPHOREMAP.get(lockId).unlock();
            removeLeaseExpirationJob(lockId);
        }
    }

    @Override
    public void deleteLock(String lockId) {
        LOGGER.trace("Deleting {}", lockId);
        LOCKIDTOSEMAPHOREMAP.invalidate(lockId);
    }

    @VisibleForTesting
    LoadingCache<String, ReentrantLock> cache() {
        return LOCKIDTOSEMAPHOREMAP;
    }

    @VisibleForTesting
    ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutures() {
        return SCHEDULEDFUTURES;
    }
}
