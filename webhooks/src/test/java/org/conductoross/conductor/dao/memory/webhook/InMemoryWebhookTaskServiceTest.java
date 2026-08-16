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

    @Test
    public void concurrentPutsAndRemovesByWebhookIdAreSafe() throws InterruptedException {
        // Tests that concurrent put/removeByWebhookId operations don't throw or corrupt state.
        // We add 1600 tasks (16 threads x 100 tasks), then concurrently remove half while adding
        // more.
        int threads = 16;
        int perThread = 100;
        String webhookId = "wh-concurrent";
        Map<String, Object> matches = Map.of("webhookId", webhookId, "a", "1");

        // Pre-populate with tasks
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                service.put(task("task-" + t + "-" + i, "wf1", "ref1", matches), 1);
            }
        }
        assertEquals(threads * perThread, service.getByWebhookId(webhookId).size());

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        // Half threads do removes, half do puts
        for (int t = 0; t < threads; t++) {
            int tid = t;
            boolean doRemoves = (t % 2 == 0);
            pool.submit(
                    () -> {
                        ready.countDown();
                        try {
                            go.await();
                            for (int i = 0; i < perThread; i++) {
                                if (doRemoves) {
                                    // Remove tasks from our thread's pre-populated set
                                    service.removeByWebhookId(webhookId, "task-" + tid + "-" + i);
                                } else {
                                    // Add new tasks with different prefix
                                    service.put(
                                            task(
                                                    "newtask-" + tid + "-" + i,
                                                    "wf1",
                                                    "ref1",
                                                    matches),
                                            1);
                                }
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

        // Verify no exception was thrown and state is consistent
        Set<String> remaining = service.getByWebhookId(webhookId);
        // Original: 1600, removed: 800 (8 threads x 100), added: 800 (8 threads x 100)
        // Expected: 1600
        assertEquals(threads * perThread, remaining.size());
        // All removed tasks should be gone
        for (int t = 0; t < threads; t += 2) { // even threads did removes
            for (int i = 0; i < perThread; i++) {
                assertTrue(
                        "Removed task should not be present",
                        !remaining.contains("task-" + t + "-" + i));
            }
        }
        // All new tasks should be present
        for (int t = 1; t < threads; t += 2) { // odd threads did puts
            for (int i = 0; i < perThread; i++) {
                assertTrue(
                        "New task should be present", remaining.contains("newtask-" + t + "-" + i));
            }
        }
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

    // ========== getByWebhookId / removeByWebhookId tests ==========

    @Test
    public void getByWebhookIdReturnsEmptySetForUnknownWebhookId() {
        assertTrue(service.getByWebhookId("unknown-webhook").isEmpty());
    }

    @Test
    public void putWithWebhookIdEnablesLookupByWebhookId() {
        Map<String, Object> matches = Map.of("webhookId", "wh-123", "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertEquals(Set.of("task-1"), service.getByWebhookId("wh-123"));
    }

    @Test
    public void multipleTasksCanShareSameWebhookId() {
        Map<String, Object> matches1 = Map.of("webhookId", "wh-shared", "a", "1");
        Map<String, Object> matches2 = Map.of("webhookId", "wh-shared", "a", "2");
        service.put(task("task-1", "wf1", "ref1", matches1), 1);
        service.put(task("task-2", "wf1", "ref1", matches2), 1);
        assertEquals(Set.of("task-1", "task-2"), service.getByWebhookId("wh-shared"));
    }

    @Test
    public void removeByWebhookIdDropsOnlyTargetedTask() {
        Map<String, Object> matches = Map.of("webhookId", "wh-123", "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.put(task("task-2", "wf1", "ref1", matches), 1);
        service.removeByWebhookId("wh-123", "task-1");
        assertEquals(Set.of("task-2"), service.getByWebhookId("wh-123"));
    }

    @Test
    public void removeByWebhookIdOfLastTaskClearsTheBucket() {
        Map<String, Object> matches = Map.of("webhookId", "wh-123", "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.removeByWebhookId("wh-123", "task-1");
        assertTrue(service.getByWebhookId("wh-123").isEmpty());
    }

    @Test
    public void removeByWebhookIdOfUnknownWebhookIdIsNoOp() {
        service.removeByWebhookId("unknown-webhook", "task-1");
        assertTrue(service.getByWebhookId("unknown-webhook").isEmpty());
    }

    @Test
    public void removeByWebhookIdOfUnknownTaskIdIsNoOp() {
        Map<String, Object> matches = Map.of("webhookId", "wh-123", "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        service.removeByWebhookId("wh-123", "task-other");
        assertEquals(Set.of("task-1"), service.getByWebhookId("wh-123"));
    }

    @Test
    public void removeByHashAlsoRemovesFromWebhookIdIndex() {
        Map<String, Object> matches = Map.of("webhookId", "wh-123", "a", "1");
        String h = hash("wf1", 1, "ref1", matches);
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        // Remove via hash (the original remove method)
        service.remove(h, "task-1");
        // Should also be gone from webhookId index
        assertTrue(service.getByWebhookId("wh-123").isEmpty());
    }

    @Test
    public void getByWebhookIdReturnsSnapshotIndependentFromInternalStorage() {
        Map<String, Object> matches = Map.of("webhookId", "wh-123", "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        Set<String> snapshot = service.getByWebhookId("wh-123");
        snapshot.add("task-injected");
        assertEquals(
                "internal storage must not be affected by mutations on a returned snapshot",
                Set.of("task-1"),
                service.getByWebhookId("wh-123"));
    }

    // ========== webhookId validation tests ==========

    @Test
    public void emptyWebhookIdIsNotRegistered() {
        Map<String, Object> matches = Map.of("webhookId", "", "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        // Task is registered by hash, but not by webhookId
        assertTrue(service.getByWebhookId("").isEmpty());
        // Verify task is still findable by hash
        assertEquals(Set.of("task-1"), service.get(hash("wf1", 1, "ref1", matches)));
    }

    @Test
    public void webhookIdOver256CharsIsNotRegistered() {
        String longWebhookId = "x".repeat(257);
        Map<String, Object> matches = Map.of("webhookId", longWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertTrue(service.getByWebhookId(longWebhookId).isEmpty());
        // Verify task is still findable by hash
        assertEquals(Set.of("task-1"), service.get(hash("wf1", 1, "ref1", matches)));
    }

    @Test
    public void webhookIdExactly256CharsIsAccepted() {
        String maxWebhookId = "x".repeat(256);
        Map<String, Object> matches = Map.of("webhookId", maxWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertEquals(Set.of("task-1"), service.getByWebhookId(maxWebhookId));
    }

    @Test
    public void webhookIdWithControlCharIsNotRegistered() {
        String badWebhookId = "webhook\u0000id"; // null char
        Map<String, Object> matches = Map.of("webhookId", badWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertTrue(service.getByWebhookId(badWebhookId).isEmpty());
    }

    @Test
    public void webhookIdWithNewlineIsNotRegistered() {
        String badWebhookId = "webhook\nid"; // newline is control char < 0x20
        Map<String, Object> matches = Map.of("webhookId", badWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertTrue(service.getByWebhookId(badWebhookId).isEmpty());
    }

    @Test
    public void webhookIdWithTabIsNotRegistered() {
        String badWebhookId = "webhook\tid"; // tab is control char < 0x20
        Map<String, Object> matches = Map.of("webhookId", badWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertTrue(service.getByWebhookId(badWebhookId).isEmpty());
    }

    @Test
    public void webhookIdWithHighControlCharIsNotRegistered() {
        String badWebhookId = "webhook\u0080id"; // 0x80 is in 0x7F-0x9F range
        Map<String, Object> matches = Map.of("webhookId", badWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertTrue(service.getByWebhookId(badWebhookId).isEmpty());
    }

    @Test
    public void validWebhookIdWithSpecialCharsIsAccepted() {
        // Valid: printable ASCII, unicode letters, common URL-safe chars
        String validWebhookId = "wh-123_test.example:456/path";
        Map<String, Object> matches = Map.of("webhookId", validWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertEquals(Set.of("task-1"), service.getByWebhookId(validWebhookId));
    }

    @Test
    public void validWebhookIdWithUnicodeIsAccepted() {
        String unicodeWebhookId = "webhook-日本語-émoji-🎉";
        Map<String, Object> matches = Map.of("webhookId", unicodeWebhookId, "a", "1");
        service.put(task("task-1", "wf1", "ref1", matches), 1);
        assertEquals(Set.of("task-1"), service.getByWebhookId(unicodeWebhookId));
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
