/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

public class LocalOnlyLockTest {

    // Lock can be global since it uses global cache internally
    private final LocalOnlyLock lck = new LocalOnlyLock();

    @Test
    public void testLockUnlock() {
        lck.acquireLock("a");
        Assert.assertEquals(lck.cache().size(), 1);
        Assert.assertEquals(lck.cache().getUnchecked("a").availablePermits(), 0);
        lck.releaseLock("a");
        Assert.assertEquals(lck.cache().getUnchecked("a").availablePermits(), 1);
        lck.deleteLock("a");
        Assert.assertEquals(lck.cache().size(), 0);
    }

    @Test(timeout = 10 * 1000)
    public void testLockTimeout() {
        lck.acquireLock("c");
        Assert.assertTrue(lck.acquireLock("d", 100, TimeUnit.MILLISECONDS));
        Assert.assertFalse(lck.acquireLock("c", 100, TimeUnit.MILLISECONDS));
        lck.releaseLock("c");
        lck.releaseLock("d");
    }

    @Test(timeout = 10 * 1000)
    public void testLockLeaseTime() {
        for (int i = 0; i < 10; i++) {
            lck.acquireLock("a", 1000, 100, TimeUnit.MILLISECONDS);
        }
        lck.acquireLock("a");
        Assert.assertEquals(0, lck.cache().getUnchecked("a").availablePermits());
        lck.releaseLock("a");
    }

    @Test(timeout = 10 * 1000)
    public void testLockLeaseWithRelease() throws Exception {
        lck.acquireLock("b", 1000, 1000, TimeUnit.MILLISECONDS);
        lck.releaseLock("b");

        // Wait for lease to run out and also call release
        Thread.sleep(2000);

        lck.acquireLock("b");
        Assert.assertEquals(0, lck.cache().getUnchecked("b").availablePermits());
        lck.releaseLock("b");
    }

    @Test
    public void testRelease() {
        lck.releaseLock("x54as4d2;23'4");
        lck.releaseLock("x54as4d2;23'4");
        Assert.assertEquals(1, lck.cache().getUnchecked("x54as4d2;23'4").availablePermits());
    }

    private final int ITER = 1;

    @Test(timeout = ITER * 10 * 1000)
    public void multithreaded() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (int i = 0; i < ITER * 1000; i++) {
            if (i % 3 == 0) {
                pool.submit(() -> {
                    lck.acquireLock("a");
                    lck.releaseLock("a");
                });
            } else if (i % 3 == 1) {
                pool.submit(() -> {
                    if (lck.acquireLock("a", ITER, TimeUnit.SECONDS)) {
                        lck.releaseLock("a");
                    }
                });
            } else {
                pool.submit(() -> {
                    lck.acquireLock("a", ITER * 1000, 5, TimeUnit.MILLISECONDS);
                });
            }
        }
        // Wait till pool has no more tasks in queue
        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(ITER * 5, TimeUnit.SECONDS));
        // Wait till last possible lease time runs out (lease time == 5 seconds)
        Thread.sleep(100);
        // We should end up with lock with value 1
        Assert.assertEquals(1, lck.cache().getUnchecked("a").availablePermits());
    }

    @Test
    public void testProvider() {
        Assert.assertNotNull(new LocalOnlyLockModule().provideLock());
    }
}
