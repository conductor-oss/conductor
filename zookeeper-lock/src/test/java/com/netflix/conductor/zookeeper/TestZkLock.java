package com.netflix.conductor.zookeeper;

import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.zookeeper.config.ZookeeperConfiguration;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TestZkLock {
    TestingServer zkServer;
    ZookeeperConfiguration mockConfig;

    @Before
    public void setUp() throws Exception {
        zkServer = new TestingServer(2181);
        mockConfig = Mockito.mock(ZookeeperConfiguration.class);
        Mockito.when(mockConfig.getZkConnection()).thenReturn("localhost:2181");
        Mockito.when(mockConfig.enableWorkflowExecutionLock()).thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        zkServer.stop();
    }

    @Test
    public void testLockReentrance() {
        Lock zkLock = new ZkLock(mockConfig, "wfexecution");
        Boolean hasLock = zkLock.acquireLock("reentrantLock1", 50, TimeUnit.MILLISECONDS);
        Assert.assertTrue(hasLock);

        hasLock = zkLock.acquireLock("reentrantLock1", 50, TimeUnit.MILLISECONDS);
        Assert.assertTrue(hasLock);
        zkLock.releaseLock("reentrantLock1");
        zkLock.releaseLock("reentrantLock1");
    }

    @Test
    public void testZkLock() throws InterruptedException {
        Lock zkLock = new ZkLock(mockConfig, "wfexecution");
        String lock1 = "lock1";
        String lock2 = "lock2";

        Worker worker1 = new Worker(zkLock, lock1);
        worker1.start();
        worker1.lockNotify.acquire();
        Assert.assertTrue(worker1.isLocked);

        Worker worker2 = new Worker(zkLock, lock1);
        worker2.start();
        Assert.assertTrue(worker2.isAlive());
        Assert.assertFalse(worker2.isLocked);

        Worker worker3 = new Worker(zkLock, lock2);
        worker3.start();
        worker3.lockNotify.acquire();
        Assert.assertTrue(worker3.isLocked);

        worker1.unlockNotify.release();
        worker1.join();

        worker2.lockNotify.acquire();
        Assert.assertTrue(worker2.isLocked);
        worker2.unlockNotify.release();
        worker2.join();

        worker3.unlockNotify.release();
        worker3.join();
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
            lock.acquireLock(lockID);
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
}
