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

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.service.ExecutionLockService;
import com.netflix.conductor.zookeeper.config.ZookeeperProperties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ZookeeperLockTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperLockTest.class);

    TestingServer zkServer;
    ZookeeperProperties properties;

    @Before
    public void setUp() throws Exception {
        zkServer = new TestingServer(2181);
        properties = mock(ZookeeperProperties.class);
        when(properties.getConnectionString()).thenReturn("localhost:2181");
        when(properties.getSessionTimeout())
                .thenReturn(
                        Duration.ofMillis(CuratorFrameworkFactory.builder().getSessionTimeoutMs()));
        when(properties.getConnectionTimeout())
                .thenReturn(
                        Duration.ofMillis(
                                CuratorFrameworkFactory.builder().getConnectionTimeoutMs()));
        when(properties.getNamespace()).thenReturn("");
    }

    @After
    public void tearDown() throws Exception {
        zkServer.stop();
    }

    @Test
    public void testLockReentrance() {
        Lock zkLock = new ZookeeperLock(properties);
        boolean hasLock = zkLock.acquireLock("reentrantLock1", 50, TimeUnit.MILLISECONDS);
        assertTrue(hasLock);

        hasLock = zkLock.acquireLock("reentrantLock1", 50, TimeUnit.MILLISECONDS);
        assertTrue(hasLock);
        zkLock.releaseLock("reentrantLock1");
        zkLock.releaseLock("reentrantLock1");
    }

    @Test
    public void testZkLock() throws InterruptedException {
        Lock zkLock = new ZookeeperLock(properties);
        String lock1 = "lock1";
        String lock2 = "lock2";

        Worker worker1 = new Worker(zkLock, lock1);
        worker1.start();
        worker1.lockNotify.acquire();
        assertTrue(worker1.isLocked);
        Thread.sleep(30000);

        Worker worker2 = new Worker(zkLock, lock1);
        worker2.start();
        assertTrue(worker2.isAlive());
        assertFalse(worker2.isLocked);
        Thread.sleep(30000);

        Worker worker3 = new Worker(zkLock, lock2);
        worker3.start();
        worker3.lockNotify.acquire();
        assertTrue(worker3.isLocked);
        Thread.sleep(30000);

        worker1.unlockNotify.release();
        worker1.join();

        Thread.sleep(30000);
        worker2.lockNotify.acquire();
        assertTrue(worker2.isLocked);
        worker2.unlockNotify.release();
        worker2.join();

        worker3.unlockNotify.release();
        worker3.join();
    }

    private static class Worker extends Thread {

        private final Lock lock;
        private final String lockID;
        Semaphore unlockNotify = new Semaphore(0);
        Semaphore lockNotify = new Semaphore(0);
        boolean isLocked = false;

        Worker(Lock lock, String lockID) {
            super("TestWorker-" + lockID);
            this.lock = lock;
            this.lockID = lockID;
        }

        @Override
        public void run() {
            lock.acquireLock(lockID, 5, TimeUnit.MILLISECONDS);
            isLocked = true;
            lockNotify.release();
            try {
                unlockNotify.acquire();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLocked = false;
                lock.releaseLock(lockID);
            }
        }
    }

    private static class MultiLockWorker extends Thread {

        private final ExecutionLockService lock;
        private final Iterable<String> lockIDs;
        private boolean finishedSuccessfully = false;

        public MultiLockWorker(ExecutionLockService executionLock, Iterable<String> lockIDs) {
            super();
            this.lock = executionLock;
            this.lockIDs = lockIDs;
        }

        @Override
        public void run() {
            try {
                int iterations = 0;
                for (String lockID : lockIDs) {
                    lock.acquireLock(lockID);
                    Thread.sleep(100);
                    lock.releaseLock(lockID);
                    iterations++;
                    if (iterations % 10 == 0) {
                        LOGGER.info("Finished iterations: {}", iterations);
                    }
                }
                finishedSuccessfully = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public boolean isFinishedSuccessfully() {
            return finishedSuccessfully;
        }
    }
}
