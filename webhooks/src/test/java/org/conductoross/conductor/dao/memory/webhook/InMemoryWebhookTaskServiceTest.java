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
package org.conductoross.conductor.dao.memory.webhook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.model.TaskModel;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InMemoryWebhookTaskServiceTest {

    private InMemoryWebhookTaskService service;

    @Before
    public void setUp() {
        service = new InMemoryWebhookTaskService();
    }

    @Test
    public void getReturnsEmptySetForUnknownHash() {
        assertTrue(service.get("does-not-exist").isEmpty());
    }

    @Test
    public void putThenGetReturnsTaskId() {
        TaskModel task = task("task-1", "wf1", "ref1", Map.of("a", "1"));
        service.put(task, 1);
        assertEquals(Set.of("task-1"), service.get(hash("wf1", 1, "ref1", Map.of("a", "1"))));
    }

    @Test
    public void putAccumulatesMultipleTaskIdsAtSameHash() {
        Map<String, Object> matches = Map.of("a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.put(task("task-2", "wf1", "ref1", matches), 1);
        service.put(task("task-3", "wf1", "ref1", matches), 1);
        assertEquals(
                Set.of("task-1", "task-2", "task-3"), service.get(hash("wf1", 1, "ref1", matches)));
    }

    @Test
    public void putIsIdempotentForSameTaskId() {
        Map<String, Object> matches = Map.of("a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertEquals(Set.of("task-1"), service.get(hash("wf1", 1, "ref1", matches)));
    }

    @Test
    public void removeDropsOnlyTheTargetedTaskId() {
        Map<String, Object> matches = Map.of("a", "1");
        String h = hash("wf1", 1, "ref1", matches);
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.put(task("task-2", "wf1", "ref1", matches), 1);
        service.remove(h, "task-1");
        assertEquals(Set.of("task-2"), service.get(h));
    }

    @Test
    public void removeOfLastTaskIdClearsTheBucket() {
        Map<String, Object> matches = Map.of("a", "1");
        String h = hash("wf1", 1, "ref1", matches);
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.remove(h, "task-1");
        assertTrue(service.get(h).isEmpty());
    }

    @Test
    public void removeOfUnknownHashIsNoOp() {
        service.remove("does-not-exist", "task-1");
        assertTrue(service.get("does-not-exist").isEmpty());
    }

    @Test
    public void removeOfUnknownTaskIdAtKnownHashIsNoOp() {
        Map<String, Object> matches = Map.of("a", "1");
        String h = hash("wf1", 1, "ref1", matches);
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.remove(h, "task-other");
        assertEquals(Set.of("task-1"), service.get(h));
    }

    @Test
    public void getReturnsSnapshotIndependentFromInternalStorage() {
        Map<String, Object> matches = Map.of("a", "1");
        String h = hash("wf1", 1, "ref1", matches);
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        Set<String> snapshot = service.get(h);
        snapshot.add("task-injected");
        assertEquals(
                "internal storage must not be affected by mutations on a returned snapshot",
                Set.of("task-1"),
                service.get(h));
    }

    @Test
    public void differentWorkflowVersionsProduceDifferentHashes() {
        Map<String, Object> matches = Map.of("a", "1");
        service.put(task("task-v1", "wf1", "ref1", matches), 1);
        service.put(task("task-v2", "wf1", "ref1", matches), 2);
        assertEquals(Set.of("task-v1"), service.get(hash("wf1", 1, "ref1", matches)));
        assertEquals(Set.of("task-v2"), service.get(hash("wf1", 2, "ref1", matches)));
    }

    @Test
    public void differentMatchesProduceDifferentHashes() {
        service.put(task("task-1", "wf1", "ref1", Map.of("a", "1")), 1);
        service.put(task("task-2", "wf1", "ref1", Map.of("a", "2")), 1);
        assertEquals(Set.of("task-1"), service.get(hash("wf1", 1, "ref1", Map.of("a", "1"))));
        assertEquals(Set.of("task-2"), service.get(hash("wf1", 1, "ref1", Map.of("a", "2"))));
    }

    @Test
    public void hashIncludesSortedMatchValues() {
        // matches map ordering should not affect hash — keys are sorted before hashing
        LinkedHashMap<String, Object> orderA = new LinkedHashMap<>();
        orderA.put("alpha", "1");
        orderA.put("beta", "2");
        LinkedHashMap<String, Object> orderB = new LinkedHashMap<>();
        orderB.put("beta", "2");
        orderB.put("alpha", "1");
        service.put(task("task-1", "wf1", "ref1", orderA), 1);
        service.put(task("task-2", "wf1", "ref1", orderB), 1);
        // both tasks should land at the same hash bucket
        assertEquals(Set.of("task-1", "task-2"), service.get(hash("wf1", 1, "ref1", orderA)));
    }

    @Test
    public void matchesWithPrimitiveValuesHashStably() {
        // matches is declared Map<String, Object>; verify Integer / Long / Boolean values
        // produce stable hashes (toString is well-defined for these).
        Map<String, Object> primitives = Map.of("count", 42, "size", 1024L, "active", true);
        service.put(task("task-1", "wf1", "ref1", primitives), 1);
        service.put(task("task-2", "wf1", "ref1", primitives), 1);
        assertEquals(Set.of("task-1", "task-2"), service.get(hash("wf1", 1, "ref1", primitives)));
    }

    @Test
    public void taskReferenceNameIterationSuffixIsStrippedFromHash() {
        // refName "loop__1" and "loop" should produce the same hash
        Map<String, Object> matches = Map.of("a", "1");
        service.put(task("task-1", "wf1", "loop__1", matches), 1);
        service.put(task("task-2", "wf1", "loop", matches), 1);
        assertEquals(Set.of("task-1", "task-2"), service.get(hash("wf1", 1, "loop", matches)));
    }

    @Test(expected = NonTransientException.class)
    public void putWithoutMatchesFieldThrows() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setWorkflowType("wf1");
        task.setReferenceTaskName("ref1");
        task.setInputData(Map.of()); // no "matches" key
        service.put(task, 1);
    }

    @Test(expected = NonTransientException.class)
    public void putWithNullInputDataThrows() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setWorkflowType("wf1");
        task.setReferenceTaskName("ref1");
        // inputData not set (null)
        service.put(task, 1);
    }

    @Test
    public void concurrentPutsAtSameHashAllSurvive() throws InterruptedException {
        int threads = 16;
        int perThread = 200;
        Map<String, Object> matches = Map.of("a", "1");
        String h = hash("wf1", 1, "ref1", matches);
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
                                service.put(
                                        task("task-" + tid + "-" + i, "wf1", "ref1", matches), 1);
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
        assertEquals(threads * perThread, service.get(h).size());
    }

    private static TaskModel task(
            String taskId, String workflowType, String refName, Map<String, Object> matches) {
        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setWorkflowType(workflowType);
        task.setReferenceTaskName(refName);
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("matches", matches);
        task.setInputData(inputData);
        return task;
    }

    /** Mirror of InMemoryWebhookTaskService.computeHash — kept independent so tests catch drift. */
    private static String hash(
            String workflowType, int version, String refName, Map<String, Object> matches) {
        StringBuilder sb =
                new StringBuilder(
                        workflowType + WEBHOOK_DELIMITER + version + WEBHOOK_DELIMITER + refName);
        new TreeSet<>(matches.keySet())
                .forEach(k -> sb.append(WEBHOOK_DELIMITER).append(matches.get(k)));
        return sb.toString();
    }
}
