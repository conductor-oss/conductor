package com.netflix.conductor.locking.redis;

import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.locking.redis.config.RedisLockConfiguration;
import com.netflix.conductor.locking.redis.config.SystemPropertiesRedisLockConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

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
        redisLock = new RedisLock(redisLockConfiguration);

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
    public void testLocking() throws IOException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        redisLock.acquireLock(lockId);

        // get the lock back
        RLock lock = redisson.getLock(lockId);
        assertTrue(lock.isLocked());
    }

    @Test
    public void testLockExpiration() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        boolean isLocked = redisLock.acquireLock(lockId, 100, 1000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        Thread.sleep(2000);

        RLock lock = redisson.getLock(lockId);
        assertFalse(lock.isLocked());
    }

    @Test
    public void testLockReentry() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";
        boolean isLocked = redisLock.acquireLock(lockId, 100, 2000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        Thread.sleep(1000);

        // get the lock back
        isLocked = redisLock.acquireLock(lockId, 100, 2000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        RLock lock = redisson.getLock(lockId);
        long ttl = lock.remainTimeToLive();
        assertTrue(ttl > 0);
    }


    @Test
    public void testReleaseLock() {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        boolean isLocked = redisLock.acquireLock(lockId, 100, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        redisLock.releaseLock(lockId);

        RLock lock = redisson.getLock(lockId);
        assertFalse(lock.isLocked());
    }

    // TODO
    @Ignore
    @Test
    public void testReleaseLockThread() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        Worker worker1 = new Worker(redisLock, lockId);

        worker1.start();
        worker1.join();

        assertTrue(worker1.isLocked);
        worker1.releaseLock();

        RLock lock = redisson.getLock(lockId);
        assertFalse(lock.isLocked());
    }

    // TODO
    @Ignore
    @Test
    public void testLockReleaseAndAcquire() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        Worker worker1 = new Worker(redisLock, lockId);
        Worker worker2 = new Worker(redisLock, lockId);

        worker1.start();
        worker1.join();

        assertTrue(worker1.isLocked);
        worker1.releaseLock();

        worker2.start();
        worker2.join();
        assertTrue(worker2.isLocked);

        worker2.releaseLock();
        RLock lock = redisson.getLock(lockId);
        assertFalse(lock.isLocked());
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
    public void testReacquireLostKey() throws InterruptedException {
        redisson.getKeys().flushall();
        String lockId = "abcd-1234";

        boolean isLocked = redisLock.acquireLock(lockId, 100, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);

        // Delete key from the cluster to reacquire
        // Simulating the case when cluster goes down and possibly loses some keys.
        redisson.getKeys().flushall();

        isLocked = redisLock.acquireLock(lockId, 100, 10000, TimeUnit.MILLISECONDS);
        assertTrue(isLocked);
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

        // Wrapper to prevent "not locked by current thread" exception.
        public void releaseLock() {
            lock.releaseLock(lockID);
        }

        @Override
        public void run() {
            isLocked = lock.acquireLock(lockID, timeToTry, leaseTime, TimeUnit.MILLISECONDS);
        }
    }
}
