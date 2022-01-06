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
package com.netflix.conductor.zookeeper.lock;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.zookeeper.config.ZookeeperProperties;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@SuppressWarnings("UnstableApiUsage")
public class ZookeeperLock implements Lock {

    public static final int CACHE_MAXSIZE = 20000;
    public static final int CACHE_EXPIRY_TIME = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperLock.class);
    private final CuratorFramework client;
    private final LoadingCache<String, InterProcessMutex> zkLocks;
    private final String zkPath;

    public ZookeeperLock(ZookeeperProperties properties) {
        String lockNamespace = properties.getNamespace();
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client =
                CuratorFrameworkFactory.newClient(
                        properties.getConnectionString(),
                        (int) properties.getSessionTimeout().toMillis(),
                        (int) properties.getConnectionTimeout().toMillis(),
                        retryPolicy);
        client.start();
        zkLocks =
                CacheBuilder.newBuilder()
                        .maximumSize(CACHE_MAXSIZE)
                        .expireAfterAccess(CACHE_EXPIRY_TIME, TimeUnit.MINUTES)
                        .build(
                                new CacheLoader<String, InterProcessMutex>() {
                                    @Override
                                    public InterProcessMutex load(String key) {
                                        return new InterProcessMutex(client, zkPath.concat(key));
                                    }
                                });

        zkPath =
                StringUtils.isEmpty(lockNamespace)
                        ? ("/conductor/")
                        : ("/conductor/" + lockNamespace + "/");
    }

    public void acquireLock(String lockId) {
        if (StringUtils.isEmpty(lockId)) {
            throw new IllegalArgumentException("lockId cannot be NULL or empty: lockId=" + lockId);
        }
        try {
            InterProcessMutex mutex = zkLocks.get(lockId);
            mutex.acquire();
        } catch (Exception e) {
            LOGGER.debug("Failed in acquireLock: ", e);
        }
    }

    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        if (StringUtils.isEmpty(lockId)) {
            throw new IllegalArgumentException("lockId cannot be NULL or empty: lockId=" + lockId);
        }
        try {
            InterProcessMutex mutex = zkLocks.get(lockId);
            return mutex.acquire(timeToTry, unit);
        } catch (Exception e) {
            LOGGER.debug("Failed in acquireLock: ", e);
        }
        return false;
    }

    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        return acquireLock(lockId, timeToTry, unit);
    }

    public void releaseLock(String lockId) {
        if (StringUtils.isEmpty(lockId)) {
            throw new IllegalArgumentException("lockId cannot be NULL or empty: lockId=" + lockId);
        }
        try {
            InterProcessMutex lock = zkLocks.getIfPresent(lockId);
            if (lock != null) {
                lock.release();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed in releaseLock: ", e);
        }
    }

    public void deleteLock(String lockId) {
        try {
            LOGGER.debug("Deleting lock {}", zkPath.concat(lockId));
            client.delete().guaranteed().forPath(zkPath.concat(lockId));
        } catch (Exception e) {
            LOGGER.debug("Failed to removeLock: ", e);
        }
    }
}
