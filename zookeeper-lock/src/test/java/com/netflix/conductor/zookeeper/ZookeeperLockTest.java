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

package com.netflix.conductor.zookeeper;

import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.service.ExecutionLockService;
import com.netflix.conductor.zookeeper.config.SystemPropertiesZookeeperConfiguration;
import com.netflix.conductor.zookeeper.config.ZookeeperConfiguration;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ZookeeperLockTest {
    TestingServer zkServer;
    ZookeeperConfiguration zkConfig;
    Provider<Lock> mockProvider;
    Lock zkLock;

    @Before
    public void setUp() throws Exception {
        zkServer = new TestingServer(2181);
        zkConfig = new SystemPropertiesZookeeperConfiguration();
    }

    @After
    public void tearDown() throws Exception {
        zkServer.stop();
    }

    @Test
    public void testLockReentrance() {
        Lock zkLock = new ZookeeperLock(zkConfig);
        Boolean hasLock = zkLock.acquireLock("reentrantLock1", 50, TimeUnit.MILLISECONDS);
        Assert.assertTrue(hasLock);

        hasLock = zkLock.acquireLock("reentrantLock1", 50, TimeUnit.MILLISECONDS);
        Assert.assertTrue(hasLock);
        zkLock.releaseLock("reentrantLock1");
        zkLock.releaseLock("reentrantLock1");
    }

    @Test
    public void testZkLock() throws InterruptedException {
        Lock zkLock = new ZookeeperLock(zkConfig);
        String lock1 = "lock1";
        String lock2 = "lock2";

        Worker worker1 = new Worker(zkLock, lock1);
        worker1.start();
        worker1.lockNotify.acquire();
        Assert.assertTrue(worker1.isLocked);
        Thread.sleep(30000);

        Worker worker2 = new Worker(zkLock, lock1);
        worker2.start();
        Assert.assertTrue(worker2.isAlive());
        Assert.assertFalse(worker2.isLocked);
        Thread.sleep(30000);

        Worker worker3 = new Worker(zkLock, lock2);
        worker3.start();
        worker3.lockNotify.acquire();
        Assert.assertTrue(worker3.isLocked);
        Thread.sleep(30000);

        worker1.unlockNotify.release();
        worker1.join();

        Thread.sleep(30000);
        worker2.lockNotify.acquire();
        Assert.assertTrue(worker2.isLocked);
        worker2.unlockNotify.release();
        worker2.join();

        worker3.unlockNotify.release();
        worker3.join();
    }

    @Ignore
    public void testExecutionLockService() throws Exception {
        int numLocks = 10;
        int contentionFactor = 4;
        int numThreads = 10;
        List<String> locksIDs = new ArrayList<>(numLocks * 2 ^ (contentionFactor - 1));
        for (int i = 0; i < numLocks; i++) {
            locksIDs.add("testlock-" + i);
        }
        for (int i = 0 ; i < contentionFactor; i++) {
            locksIDs.addAll(locksIDs);
        }
        List<MultiLockWorker> workers = new ArrayList<>(numThreads);
        ExecutionLockService executionLock = new ExecutionLockService(zkConfig, mockProvider);
        for (int i = 0; i < numThreads; i++) {
            List<String> workerLockIDs = new ArrayList<>(locksIDs);
            Collections.shuffle(workerLockIDs);
            MultiLockWorker lockWorker = new MultiLockWorker(executionLock, workerLockIDs);
            lockWorker.start();
            workers.add(lockWorker);
        }
        for (int i = 0; i <numThreads; i++) {
            for (MultiLockWorker worker: workers) {
                worker.join();
                Assert.assertTrue(worker.isFinishedSuccessfully());
            }
        }
    }

    private static class Worker extends Thread {
        private Lock lock;
        private String lockID;
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
            }finally {
                isLocked = false;
                lock.releaseLock(lockID);
            }
        }
    }

    private static class MultiLockWorker extends Thread {
        private final ExecutionLockService lock;
        private Iterable<String> lockIDs;
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
                for (String lockID: lockIDs) {
                    lock.acquireLock(lockID);
                    Thread.sleep(100);
                    lock.releaseLock(lockID);
                    iterations++;
                    if (iterations % 10 == 0) {
                        System.out.println("Finished iterations:" + iterations);
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
