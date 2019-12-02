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

import com.netflix.conductor.locking.redis.config.RedisLockConfiguration;
import com.netflix.conductor.locking.redis.config.SystemPropertiesRedisLockConfiguration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import redis.embedded.RedisServer;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RedisLockTest {

    private static RedisLock redisLock;
    private static Config config;
    private static RedissonClient redisson;
    private static RedisServer redisServer = null;

    @BeforeClass
    public static void setUp() throws Exception {
        String testServerAddress = "redis://127.0.0.1:6371";
        redisServer = new RedisServer(6371);
        if (redisServer.isActive()) {
            redisServer.stop();
        }
        redisServer.start();

        RedisLockConfiguration redisLockConfiguration = new SystemPropertiesRedisLockConfiguration() {
            @Override
            public String getRedisServerAddress() {
                return testServerAddress;
            }
        };

        Config redissonConfig = new Config();
        redissonConfig.useSingleServer().setAddress(testServerAddress).setTimeout(10000);
        redisLock = new RedisLock((Redisson) Redisson.create(redissonConfig), redisLockConfiguration);

        // Create another instance of redisson for tests.
        config = new Config();
        config.useSingleServer().setAddress(testServerAddress).setTimeout(10000);
        redisson = Redisson.create(config);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        redisServer.stop();
    }

    @Test
    public void testLocking() {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        assertTrue(redisLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testLockExpiration() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        boolean isLocked = redisLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        Thread.sleep(2000);

        RLock lock = redisson.getLock(lockId);
        assertFalse(lock.isLocked());
    }

    @Test
    public void testLockReentry() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        boolean isLocked = redisLock.acquireLock(lockId, 1000, 60000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        Thread.sleep(1000);

        // get the lock back
        isLocked = redisLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        RLock lock = redisson.getLock(lockId);
        assertTrue(isLocked);
    }


    @Test
    public void testReleaseLock() {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        boolean isLocked = redisLock.acquireLock(lockId, 1000, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        redisLock.releaseLock(lockId);

        RLock lock = redisson.getLock(lockId);
        assertFalse(lock.isLocked());
    }

    @Test
    public void testLockReleaseAndAcquire() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        boolean isLocked = redisLock.acquireLock(lockId, 1000, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        redisLock.releaseLock(lockId);

        Worker worker1 = new Worker(redisLock, lockId);

        worker1.start();
        worker1.join();

        assertTrue(worker1.isLocked);
    }

    @Test
    public void testLockingDuplicateThreads() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        Worker worker1 = new Worker(redisLock, lockId);
        Worker worker2 = new Worker(redisLock, lockId);

        worker1.start();
        worker2.start();

        worker1.join();
        worker2.join();

        // Ensure only one of them had got the lock.
        assertFalse(worker1.isLocked && worker2.isLocked);
        assertTrue(worker1.isLocked || worker2.isLocked);
    }

    @Test
    public void testDuplicateLockAcquireFailure() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        Worker worker1 = new Worker(redisLock, lockId, 100L, 60000L);

        worker1.start();
        worker1.join();

        boolean isLocked = redisLock.acquireLock(lockId, 500L, 1000L, TimeUnit.MILLISECONDS);

        // Ensure only one of them had got the lock.
        assertFalse(isLocked);
        assertTrue(worker1.isLocked);
    }

    @Test
    public void testReacquireLostKey() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        boolean isLocked = redisLock.acquireLock(lockId, 1000, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        // Delete key from the cluster to reacquire
        // Simulating the case when cluster goes down and possibly loses some keys.
        redisson.getKeys().flushall();

        isLocked = redisLock.acquireLock(lockId, 100, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);
    }

    @Test
    public void testReleaseLockTwice() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        boolean isLocked = redisLock.acquireLock(lockId, 1000, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);


        redisLock.releaseLock(lockId);
        redisLock.releaseLock(lockId);
    }

    private static class Worker extends Thread {
        private RedisLock lock;
        private String lockID;
        boolean isLocked;
        private Long timeToTry = 50L;
        private Long leaseTime = 1000L;

        Worker(RedisLock lock, String lockID) {
            super("TestWorker-" + lockID);
            this.lock = lock;
            this.lockID = lockID;
        }

        Worker(RedisLock lock, String lockID, Long timeToTry, Long leaseTime) {
            super("TestWorker-" + lockID);
            this.lock = lock;
            this.lockID = lockID;
            this.timeToTry = timeToTry;
            this.leaseTime = leaseTime;
        }

        @Override
        public void run() {
            isLocked = lock.acquireLock(lockID, timeToTry, leaseTime, TimeUnit.MILLISECONDS);
        }
    }
}
