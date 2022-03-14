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
package com.netflix.conductor.contribs.lock;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalOnlyLockTest {

    // Lock can be global since it uses global cache internally
    private final LocalOnlyLock localOnlyLock = new LocalOnlyLock();

    @Test
    public void testLockUnlock() {
        localOnlyLock.acquireLock("a", 100, 1000, TimeUnit.MILLISECONDS);
        assertEquals(localOnlyLock.cache().size(), 1);
        assertEquals(localOnlyLock.cache().getUnchecked("a").availablePermits(), 0);
        assertEquals(localOnlyLock.scheduledFutures().size(), 1);
        localOnlyLock.releaseLock("a");
        assertEquals(localOnlyLock.scheduledFutures().size(), 0);
        assertEquals(localOnlyLock.cache().getUnchecked("a").availablePermits(), 1);
        localOnlyLock.deleteLock("a");
        assertEquals(localOnlyLock.cache().size(), 0);
    }

    @Test(timeout = 10 * 1000)
    public void testLockTimeout() {
        localOnlyLock.acquireLock("c", 100, 1000, TimeUnit.MILLISECONDS);
        assertTrue(localOnlyLock.acquireLock("d", 100, 1000, TimeUnit.MILLISECONDS));
        assertFalse(localOnlyLock.acquireLock("c", 100, 1000, TimeUnit.MILLISECONDS));
        assertEquals(localOnlyLock.scheduledFutures().size(), 2);
        localOnlyLock.releaseLock("c");
        localOnlyLock.releaseLock("d");
        assertEquals(localOnlyLock.scheduledFutures().size(), 0);
    }

    @Test(timeout = 10 * 1000)
    public void testLockLeaseTime() {
        for (int i = 0; i < 10; i++) {
            localOnlyLock.acquireLock("a", 1000, 100, TimeUnit.MILLISECONDS);
        }
        localOnlyLock.acquireLock("a");
        assertEquals(0, localOnlyLock.cache().getUnchecked("a").availablePermits());
        localOnlyLock.releaseLock("a");
    }

    @Test(timeout = 10 * 1000)
    public void testLockLeaseWithRelease() throws Exception {
        localOnlyLock.acquireLock("b", 1000, 1000, TimeUnit.MILLISECONDS);
        localOnlyLock.releaseLock("b");

        // Wait for lease to run out and also call release
        Thread.sleep(2000);

        localOnlyLock.acquireLock("b");
        assertEquals(0, localOnlyLock.cache().getUnchecked("b").availablePermits());
        localOnlyLock.releaseLock("b");
    }

    @Test
    public void testRelease() {
        localOnlyLock.releaseLock("x54as4d2;23'4");
        localOnlyLock.releaseLock("x54as4d2;23'4");
        assertEquals(1, localOnlyLock.cache().getUnchecked("x54as4d2;23'4").availablePermits());
    }

    @Test
    public void testLockConfiguration() {
        new ApplicationContextRunner()
                .withPropertyValues("conductor.workflow-execution-lock.type=local_only")
                .withUserConfiguration(LocalOnlyLockConfiguration.class)
                .run(
                        context -> {
                            LocalOnlyLock lock = context.getBean(LocalOnlyLock.class);
                            assertNotNull(lock);
                        });
    }
}
