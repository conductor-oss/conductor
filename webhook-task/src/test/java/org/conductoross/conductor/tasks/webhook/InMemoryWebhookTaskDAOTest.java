/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InMemoryWebhookTaskDAOTest {

    private InMemoryWebhookTaskDAO dao;

    @Before
    public void setUp() {
        dao = new InMemoryWebhookTaskDAO();
    }

    @Test
    public void getReturnsEmptySetForUnknownHash() {
        assertTrue(dao.get("does-not-exist").isEmpty());
    }

    @Test
    public void putThenGetReturnsTaskId() {
        dao.put("h1", "task-1");
        assertEquals(Set.of("task-1"), dao.get("h1"));
    }

    @Test
    public void putAccumulatesMultipleTaskIdsAtSameHash() {
        dao.put("h1", "task-1");
        dao.put("h1", "task-2");
        dao.put("h1", "task-3");
        assertEquals(Set.of("task-1", "task-2", "task-3"), dao.get("h1"));
    }

    @Test
    public void putIsIdempotentForSameTaskId() {
        dao.put("h1", "task-1");
        dao.put("h1", "task-1");
        assertEquals(Set.of("task-1"), dao.get("h1"));
    }

    @Test
    public void removeDropsOnlyTheTargetedTaskId() {
        dao.put("h1", "task-1");
        dao.put("h1", "task-2");
        dao.remove("h1", "task-1");
        assertEquals(Set.of("task-2"), dao.get("h1"));
    }

    @Test
    public void removeOfLastTaskIdClearsTheBucket() {
        dao.put("h1", "task-1");
        dao.remove("h1", "task-1");
        assertTrue(dao.get("h1").isEmpty());
    }

    @Test
    public void removeOfUnknownHashIsNoOp() {
        dao.remove("does-not-exist", "task-1");
        assertTrue(dao.get("does-not-exist").isEmpty());
    }

    @Test
    public void removeOfUnknownTaskIdAtKnownHashIsNoOp() {
        dao.put("h1", "task-1");
        dao.remove("h1", "task-other");
        assertEquals(Set.of("task-1"), dao.get("h1"));
    }

    @Test
    public void popAllReturnsAllTaskIdsAndClearsBucket() {
        dao.put("h1", "task-1");
        dao.put("h1", "task-2");
        Set<String> popped = dao.popAll("h1");
        assertEquals(Set.of("task-1", "task-2"), popped);
        assertTrue(dao.get("h1").isEmpty());
    }

    @Test
    public void popAllOfUnknownHashReturnsEmptySet() {
        assertTrue(dao.popAll("does-not-exist").isEmpty());
    }

    @Test
    public void getReturnsSnapshotIndependentFromInternalStorage() {
        dao.put("h1", "task-1");
        Set<String> snapshot = dao.get("h1");
        snapshot.add("task-injected");
        assertEquals(
                "internal storage must not be affected by mutations on a returned snapshot",
                Set.of("task-1"),
                dao.get("h1"));
    }

    @Test
    public void popAllReturnsSnapshotIndependentFromInternalStorage() {
        dao.put("h1", "task-1");
        Set<String> popped = dao.popAll("h1");
        popped.add("task-injected");
        dao.put("h1", "task-2");
        assertEquals(Set.of("task-2"), dao.get("h1"));
    }

    @Test
    public void differentHashesAreIsolated() {
        dao.put("h1", "task-1");
        dao.put("h2", "task-2");
        assertEquals(Set.of("task-1"), dao.get("h1"));
        assertEquals(Set.of("task-2"), dao.get("h2"));
    }

    @Test
    public void concurrentPutsAtSameHashAllSurvive() throws InterruptedException {
        int threads = 16;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int tid = t;
            pool.submit(
                    () -> {
                        ready.countDown();
                        try {
                            go.await();
                            for (int i = 0; i < perThread; i++) {
                                dao.put("h1", "task-" + tid + "-" + i);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await();
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(threads * perThread, dao.get("h1").size());
    }

    @Test
    public void concurrentPopAllAndPutDoesNotLoseTaskIds() throws InterruptedException {
        int producers = 8;
        int perProducer = 500;
        Set<String> popped = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(producers + 1);
        CountDownLatch done = new CountDownLatch(producers);
        for (int t = 0; t < producers; t++) {
            int tid = t;
            pool.submit(
                    () -> {
                        for (int i = 0; i < perProducer; i++) {
                            dao.put("h1", "task-" + tid + "-" + i);
                        }
                        done.countDown();
                    });
        }
        pool.submit(
                () -> {
                    while (done.getCount() > 0) {
                        synchronized (popped) {
                            popped.addAll(dao.popAll("h1"));
                        }
                    }
                });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        synchronized (popped) {
            popped.addAll(dao.popAll("h1"));
        }
        pool.shutdownNow();
        assertEquals(producers * perProducer, popped.size());
    }

    @Test(expected = NullPointerException.class)
    public void nullHashIsRejected() {
        dao.put(null, "task-1");
    }

    @Test(expected = NullPointerException.class)
    public void nullTaskIdIsRejected() {
        dao.put("h1", null);
    }
}
