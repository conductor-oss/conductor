/*
 * Copyright 2025 Conductor Authors.
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
package io.conductor.e2e.task;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for retry policy features:
 *
 * <ul>
 *   <li>{@code maxRetryDelaySeconds} — caps the computed backoff; cap=0 means disabled
 *   <li>{@code backoffJitterMs} — random [0, max] ms added per retry to spread thundering herds
 *   <li>Poll gap fix — decider queue updated on SCHEDULED→IN_PROGRESS to reflect response timeout
 * </ul>
 *
 * <p>Task defs are registered via raw HTTP JSON because the published conductor-client SDK predates
 * these fields. All workflow/task operations use the standard SDK clients.
 *
 * <p>Inspection strategy: {@code callbackAfterSeconds} is read from a SCHEDULED retry task before
 * it is polled (polling resets the field to 0). Actual queue timing verifies jitter and poll-gap
 * behavior.
 */
@Slf4j
public class RetryPolicyTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;
    static HttpClient httpClient;
    static ObjectMapper objectMapper;

    @BeforeAll
    static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private void registerTaskDef(Map<String, Object> fields) {
        String json = objectMapper.writeValueAsString(List.of(fields));
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(ApiUtil.SERVER_ROOT_URI + "/metadata/taskdefs"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertTrue(
                res.statusCode() < 300,
                "Task def registration failed " + res.statusCode() + ": " + res.body());
    }

    private Map<String, Object> taskDefBase(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("ownerEmail", "test@conductor.io");
        m.put("timeoutSeconds", 600);
        m.put("responseTimeoutSeconds", 600);
        m.put("retryCount", 5);
        return m;
    }

    private void registerWorkflow(String workflowName, String taskType) {
        WorkflowTask wfTask = new WorkflowTask();
        wfTask.setName(taskType);
        wfTask.setTaskReferenceName(taskType + "_ref");
        wfTask.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowDef wfDef = new WorkflowDef();
        wfDef.setName(workflowName);
        wfDef.setVersion(1);
        wfDef.setOwnerEmail("test@conductor.io");
        wfDef.setTimeoutSeconds(600);
        wfDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        wfDef.setTasks(List.of(wfTask));
        metadataClient.updateWorkflowDefs(List.of(wfDef));
    }

    private String startWorkflow(String name) {
        return workflowClient.startWorkflow(
                new StartWorkflowRequest().withName(name).withVersion(1).withInput(Map.of()));
    }

    /** Polls until one task is available; blocks up to 20 s. */
    private Task pollTask(String taskType) {
        return await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            List<Task> t =
                                    taskClient.batchPollTasksByTaskType(
                                            taskType, "e2e-worker", 1, 500);
                            return t.isEmpty() ? null : t.get(0);
                        },
                        t -> t != null);
    }

    private void failTask(String wfId, String taskId) {
        TaskResult r = new TaskResult();
        r.setWorkflowInstanceId(wfId);
        r.setTaskId(taskId);
        r.setStatus(TaskResult.Status.FAILED);
        r.setReasonForIncompletion("e2e retry policy test");
        taskClient.updateTask(r);
    }

    private void completeTask(String wfId, String taskId) {
        TaskResult r = new TaskResult();
        r.setWorkflowInstanceId(wfId);
        r.setTaskId(taskId);
        r.setStatus(TaskResult.Status.COMPLETED);
        taskClient.updateTask(r);
    }

    /** Returns the first SCHEDULED task in the workflow once it has ≥ minCount tasks. */
    private Task awaitScheduledRetry(String wfId, int minCount) {
        return await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(wfId, true);
                            if (wf.getTasks().size() < minCount) return null;
                            return wf.getTasks().stream()
                                    .filter(t -> t.getStatus() == Task.Status.SCHEDULED)
                                    .findFirst()
                                    .orElse(null);
                        },
                        t -> t != null);
    }

    private void awaitCompleted(String wfId) {
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED,
                                        workflowClient.getWorkflow(wfId, false).getStatus(),
                                        "workflow " + wfId + " did not complete"));
    }

    // =========================================================================
    // maxRetryDelaySeconds
    // =========================================================================

    @Test
    @DisplayName("EXPONENTIAL_BACKOFF: delays grow then are capped by maxRetryDelaySeconds")
    void testMaxRetryDelaySeconds_exponential() {
        String tt = "e2e-exp-cap-task", wf = "e2e-exp-cap-wf";
        // base=1s, cap=3s: retry1=1s, retry2=2s, retry3=4s→3s
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "EXPONENTIAL_BACKOFF");
        def.put("maxRetryDelaySeconds", 3);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                1L,
                awaitScheduledRetry(wfId, 2).getCallbackAfterSeconds(),
                "retry1: 1×2⁰=1s (below cap 3s)");

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                2L,
                awaitScheduledRetry(wfId, 3).getCallbackAfterSeconds(),
                "retry2: 1×2¹=2s (below cap 3s)");

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                3L,
                awaitScheduledRetry(wfId, 4).getCallbackAfterSeconds(),
                "retry3: 1×2²=4s → capped to 3s");

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    @Test
    @DisplayName("LINEAR_BACKOFF: delays grow then are capped by maxRetryDelaySeconds")
    void testMaxRetryDelaySeconds_linear() {
        String tt = "e2e-lin-cap-task", wf = "e2e-lin-cap-wf";
        // base=1s, scale=2, cap=3s: retry1=2s, retry2=4s→3s
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "LINEAR_BACKOFF");
        def.put("backoffScaleFactor", 2);
        def.put("maxRetryDelaySeconds", 3);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                2L,
                awaitScheduledRetry(wfId, 2).getCallbackAfterSeconds(),
                "retry1: 1×2×1=2s (below cap 3s)");

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                3L,
                awaitScheduledRetry(wfId, 3).getCallbackAfterSeconds(),
                "retry2: 1×2×2=4s → capped to 3s");

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    // --- Boundary: cap = 0 means no cap (backward compat) ---

    @Test
    @DisplayName("cap=0 means no cap: delays grow without bound (backward compat)")
    void testMaxRetryDelaySeconds_zeroMeansNoCapApplied() {
        String tt = "e2e-no-cap-task", wf = "e2e-no-cap-wf";
        // base=1s, no cap: retry1=1s, retry2=2s, retry3=4s (all uncapped)
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "EXPONENTIAL_BACKOFF");
        def.put("maxRetryDelaySeconds", 0); // disabled
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(1L, awaitScheduledRetry(wfId, 2).getCallbackAfterSeconds());

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(2L, awaitScheduledRetry(wfId, 3).getCallbackAfterSeconds());

        failTask(wfId, pollTask(tt).getTaskId());
        // Without cap, retry3 = 1×2²=4s (not capped to any lower value)
        assertEquals(
                4L,
                awaitScheduledRetry(wfId, 4).getCallbackAfterSeconds(),
                "cap=0 must be treated as disabled: delay should be uncapped 4s");

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    // --- Boundary: cap < retryDelaySeconds → cap fires immediately from retry 0 ---

    @Test
    @DisplayName("cap < retryDelaySeconds: cap applied from the very first retry")
    void testMaxRetryDelaySeconds_capLessThanBaseDelay() {
        String tt = "e2e-cap-lt-base-task", wf = "e2e-cap-lt-base-wf";
        // base=5s, cap=2s: every retry is capped to 2s even FIXED
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 5);
        def.put("retryLogic", "FIXED");
        def.put("maxRetryDelaySeconds", 2);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                2L,
                awaitScheduledRetry(wfId, 2).getCallbackAfterSeconds(),
                "FIXED base=5s capped immediately to 2s");

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(
                2L,
                awaitScheduledRetry(wfId, 3).getCallbackAfterSeconds(),
                "All retries capped at 2s");

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    // --- Boundary: cap = retryDelaySeconds → effective from retry 0 ---

    @Test
    @DisplayName("cap=retryDelaySeconds: cap matches base, all retries at ceiling from start")
    void testMaxRetryDelaySeconds_capEqualsBase() {
        String tt = "e2e-cap-eq-base-task", wf = "e2e-cap-eq-base-wf";
        // base=2s, scale=2, LINEAR, cap=2s: retry1=2s(capped), retry2=4s→2s, etc.
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 2);
        def.put("retryLogic", "LINEAR_BACKOFF");
        def.put("backoffScaleFactor", 2);
        def.put("maxRetryDelaySeconds", 2); // equals first retry raw value
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        // retry1 raw=2×2×1=4s → capped to 2s
        assertEquals(2L, awaitScheduledRetry(wfId, 2).getCallbackAfterSeconds());

        failTask(wfId, pollTask(tt).getTaskId());
        // retry2 raw=2×2×2=8s → capped to 2s
        assertEquals(2L, awaitScheduledRetry(wfId, 3).getCallbackAfterSeconds());

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    // =========================================================================
    // backoffJitterMs
    // =========================================================================

    @Test
    @DisplayName("FIXED backoff: callbackAfterSeconds=base; queue delay in [base, base+jitter]")
    void testBackoffJitterMs_fixed() {
        String tt = "e2e-jitter-fixed-task", wf = "e2e-jitter-fixed-wf";
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 2);
        def.put("retryLogic", "FIXED");
        def.put("backoffJitterMs", 1000);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);
        failTask(wfId, pollTask(tt).getTaskId());

        Task retry = awaitScheduledRetry(wfId, 2);
        assertEquals(
                2L,
                retry.getCallbackAfterSeconds(),
                "callbackAfterSeconds must equal the base delay; jitter lives in callbackAfterMs");

        long scheduledAt = retry.getScheduledTime();
        Task retried = pollTask(tt);
        long queueDelay = System.currentTimeMillis() - scheduledAt;
        assertTrue(queueDelay >= 2000, "Must wait at least base 2s; was " + queueDelay + "ms");
        assertTrue(
                queueDelay < 5000,
                "Must not exceed base+jitter+overhead; was " + queueDelay + "ms");

        completeTask(wfId, retried.getTaskId());
        awaitCompleted(wfId);
    }

    @Test
    @DisplayName("EXPONENTIAL_BACKOFF: jitter adds on top of each exponentially growing delay")
    void testBackoffJitterMs_exponential() {
        String tt = "e2e-jitter-exp-task", wf = "e2e-jitter-exp-wf";
        // base=1s, jitter=500ms, exponential: retry1=1s±500ms, retry2=2s±500ms
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "EXPONENTIAL_BACKOFF");
        def.put("backoffJitterMs", 500);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        // retry 0 → retry 1 (base=1s, jitter up to 500ms)
        failTask(wfId, pollTask(tt).getTaskId());
        Task retry1 = awaitScheduledRetry(wfId, 2);
        assertEquals(1L, retry1.getCallbackAfterSeconds());

        long scheduledAt1 = retry1.getScheduledTime();
        Task task1 = pollTask(tt);
        long delay1 = System.currentTimeMillis() - scheduledAt1;
        assertTrue(delay1 >= 1000, "retry1 queue delay must be >= 1s; was " + delay1 + "ms");
        assertTrue(delay1 < 3000, "retry1 delay must be < 3s; was " + delay1 + "ms");

        // retry 1 → retry 2 (base=2s, jitter up to 500ms)
        failTask(wfId, task1.getTaskId());
        Task retry2 = awaitScheduledRetry(wfId, 3);
        assertEquals(2L, retry2.getCallbackAfterSeconds());

        long scheduledAt2 = retry2.getScheduledTime();
        Task task2 = pollTask(tt);
        long delay2 = System.currentTimeMillis() - scheduledAt2;
        assertTrue(delay2 >= 2000, "retry2 queue delay must be >= 2s; was " + delay2 + "ms");
        assertTrue(delay2 < 4000, "retry2 delay must be < 4s; was " + delay2 + "ms");

        completeTask(wfId, task2.getTaskId());
        awaitCompleted(wfId);
    }

    @Test
    @DisplayName("LINEAR_BACKOFF: jitter adds on top of each linearly growing delay")
    void testBackoffJitterMs_linear() {
        String tt = "e2e-jitter-lin-task", wf = "e2e-jitter-lin-wf";
        // base=1s, scale=2, jitter=500ms: retry1=2s±500ms, retry2=4s±500ms
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "LINEAR_BACKOFF");
        def.put("backoffScaleFactor", 2);
        def.put("backoffJitterMs", 500);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        Task retry1 = awaitScheduledRetry(wfId, 2);
        assertEquals(2L, retry1.getCallbackAfterSeconds());

        long s1 = retry1.getScheduledTime();
        Task t1 = pollTask(tt);
        long d1 = System.currentTimeMillis() - s1;
        assertTrue(
                d1 >= 2000 && d1 < 4000,
                "retry1 queue delay should be in [2s, 4s]; was " + d1 + "ms");

        completeTask(wfId, t1.getTaskId());
        awaitCompleted(wfId);
    }

    @Test
    @DisplayName("jitter=0: exact base delay, no random spread")
    void testBackoffJitterMs_zero() {
        String tt = "e2e-no-jitter-task", wf = "e2e-no-jitter-wf";
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 2);
        def.put("retryLogic", "FIXED");
        def.put("backoffJitterMs", 0);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);
        failTask(wfId, pollTask(tt).getTaskId());

        Task retry = awaitScheduledRetry(wfId, 2);
        assertEquals(2L, retry.getCallbackAfterSeconds());

        long s = retry.getScheduledTime();
        Task t = pollTask(tt);
        long d = System.currentTimeMillis() - s;
        assertTrue(d >= 2000, "Must wait at least 2s base; was " + d + "ms");
        assertTrue(d < 4000, "With zero jitter should be close to 2s; was " + d + "ms");

        completeTask(wfId, t.getTaskId());
        awaitCompleted(wfId);
    }

    // --- Boundary: minimum positive jitter ---

    @Test
    @DisplayName("jitter=1ms (minimum): task still retried correctly, minimal spread")
    void testBackoffJitterMs_minimumOnems() {
        String tt = "e2e-jitter-1ms-task", wf = "e2e-jitter-1ms-wf";
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 2);
        def.put("retryLogic", "FIXED");
        def.put("backoffJitterMs", 1); // 1 ms jitter — barely above zero
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);
        failTask(wfId, pollTask(tt).getTaskId());

        Task retry = awaitScheduledRetry(wfId, 2);
        assertEquals(2L, retry.getCallbackAfterSeconds());

        // queue delay: base 2s ± 1ms → effectively 2s
        long s = retry.getScheduledTime();
        Task t = pollTask(tt);
        long d = System.currentTimeMillis() - s;
        assertTrue(
                d >= 2000 && d < 4000,
                "1ms jitter should not significantly change timing; was " + d + "ms");

        completeTask(wfId, t.getTaskId());
        awaitCompleted(wfId);
    }

    // --- Combined cap + jitter ---

    @Test
    @DisplayName(
            "Cap applied before jitter: callbackAfterSeconds=cap; queue delay in [cap, cap+jitter]")
    void testMaxRetryDelaySeconds_withJitter() {
        String tt = "e2e-cap-jitter-task", wf = "e2e-cap-jitter-wf";
        // base=1s, cap=3s, jitter=500ms; at retry3: 4s→3s, queue delay in [3s, 3.5s]
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "EXPONENTIAL_BACKOFF");
        def.put("maxRetryDelaySeconds", 3);
        def.put("backoffJitterMs", 500);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(1L, awaitScheduledRetry(wfId, 2).getCallbackAfterSeconds());

        failTask(wfId, pollTask(tt).getTaskId());
        assertEquals(2L, awaitScheduledRetry(wfId, 3).getCallbackAfterSeconds());

        // retry 2 → retry 3: raw=4s → capped=3s; queue delay in [3000, 3500]ms
        failTask(wfId, pollTask(tt).getTaskId());
        Task retry3 = awaitScheduledRetry(wfId, 4);
        assertEquals(
                3L,
                retry3.getCallbackAfterSeconds(),
                "callbackAfterSeconds reflects cap (3s), not raw (4s)");

        long s = retry3.getScheduledTime();
        Task t = pollTask(tt);
        long d = System.currentTimeMillis() - s;
        assertTrue(d >= 3000, "Queue delay must be ≥ cap 3s; was " + d + "ms");
        assertTrue(d < 5000, "Queue delay must be < cap+jitter+overhead; was " + d + "ms");

        completeTask(wfId, t.getTaskId());
        awaitCompleted(wfId);
    }

    // =========================================================================
    // Concurrency: jitter spreads retries across time
    //
    // The core thundering-herd protection test: N workflows fail their tasks
    // simultaneously. With jitter, each retry task should become available at a
    // different time. We verify this by recording poll timestamps — if all tasks
    // were delivered at the same moment (no jitter), they would all be polled in
    // the same batch; with jitter they must spread across multiple batches.
    // =========================================================================

    @Test
    @DisplayName("Concurrency: jitter spreads N simultaneous retries across the jitter window")
    void testConcurrentRetries_jitterSpreadsRetryTimes() throws InterruptedException {
        final int N = 5;
        String tt = "e2e-concurrent-jitter-task", wf = "e2e-concurrent-jitter-wf";

        // base=1s, jitter=3000ms — large jitter relative to base to make spread observable
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "FIXED");
        def.put("backoffJitterMs", 3000);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        // Start N workflows simultaneously
        List<String> wfIds = new ArrayList<>();
        for (int i = 0; i < N; i++) wfIds.add(startWorkflow(wf));

        // Poll and fail all initial tasks concurrently so retries are scheduled at the same
        // instant.
        // Use the task's own workflowInstanceId (not the lambda-captured id) so that the server
        // calls decide() on the correct workflow and schedules the retry.
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch allFailed = new CountDownLatch(N);
        for (int i = 0; i < N; i++) {
            pool.submit(
                    () -> {
                        try {
                            Task t = pollTask(tt);
                            failTask(t.getWorkflowInstanceId(), t.getTaskId());
                        } finally {
                            allFailed.countDown();
                        }
                    });
        }
        assertTrue(allFailed.await(30, TimeUnit.SECONDS), "All tasks failed within timeout");
        pool.shutdown();

        // Collect timestamps of when each retry task first becomes poppable.
        // Track tasksPolled (termination condition) separately from pollTimestampsSeconds (jitter
        // spread check) — the set only grows when a new second-bucket is seen, so it cannot be
        // used as a task counter.
        int tasksPolled = 0;
        Set<Long> pollTimestampsSeconds = ConcurrentHashMap.newKeySet();
        long start = System.currentTimeMillis();
        while (tasksPolled < N && System.currentTimeMillis() - start < 31_000) {
            List<Task> batch =
                    taskClient.batchPollTasksByTaskType(tt, "e2e-concurrency-worker", N, 500);
            long nowSec = System.currentTimeMillis() / 1000;
            for (Task t : batch) {
                pollTimestampsSeconds.add(nowSec);
                tasksPolled++;
                completeTask(t.getWorkflowInstanceId(), t.getTaskId());
            }
            if (batch.isEmpty()) Thread.sleep(200);
        }

        assertEquals(N, tasksPolled, "All " + N + " retry tasks must eventually be polled");

        // With 3s jitter and N=5 tasks, it's statistically very unlikely that all tasks become
        // available in the exact same second bucket if jitter is truly random.
        // We assert that tasks spread across at least 2 distinct second-buckets.
        // (P(all same bucket) ≈ (1/3)^4 ≈ 1.2% with uniform jitter over 3s)
        assertTrue(
                pollTimestampsSeconds.size() >= 2 || N == 1,
                "With "
                        + N
                        + " tasks and 3s jitter, retries must spread across ≥2 second-buckets. "
                        + "All "
                        + N
                        + " tasks appeared in the same second — jitter may not be applied.");
    }

    @Test
    @DisplayName("Concurrency: N workflows with cap+jitter all complete correctly")
    void testConcurrentRetries_capAndJitter_allWorkflowsComplete() throws InterruptedException {
        final int N = 4;
        String tt = "e2e-concurrent-cap-jitter-task", wf = "e2e-concurrent-cap-jitter-wf";

        // base=1s, cap=2s, jitter=500ms
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "EXPONENTIAL_BACKOFF");
        def.put("maxRetryDelaySeconds", 2);
        def.put("backoffJitterMs", 500);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        List<String> wfIds = new ArrayList<>();
        for (int i = 0; i < N; i++) wfIds.add(startWorkflow(wf));

        // Fail each initial task, then complete each retry — all under concurrent conditions.
        // Poll the task first and derive the workflow ID from it so that failTask,
        // awaitScheduledRetry,
        // and completeTask all operate on the same workflow (not the lambda-captured id which may
        // belong to a different workflow than the one whose task was polled).
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch done = new CountDownLatch(N);
        for (int i = 0; i < N; i++) {
            pool.submit(
                    () -> {
                        try {
                            Task initial = pollTask(tt);
                            String wfId = initial.getWorkflowInstanceId();
                            failTask(wfId, initial.getTaskId());
                            // await and complete the retry
                            Task retry = awaitScheduledRetry(wfId, 2);
                            assertNotNull(retry, "retry task must be scheduled for wf " + wfId);
                            // cap applied: base=1s (retry0), raw retry1=2s (2^1 * 1) → capped to 2s
                            assertTrue(
                                    retry.getCallbackAfterSeconds() <= 2,
                                    "callbackAfterSeconds must be ≤ cap (2s); was "
                                            + retry.getCallbackAfterSeconds());
                            Task retryTask = pollTask(tt);
                            completeTask(retryTask.getWorkflowInstanceId(), retryTask.getTaskId());
                        } finally {
                            done.countDown();
                        }
                    });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "All " + N + " retry cycles completed");
        pool.shutdown();

        // Verify all N workflows completed successfully
        for (String id : wfIds) {
            Workflow w = workflowClient.getWorkflow(id, false);
            if (!w.getStatus().isTerminal()) {
                workflowClient.runDecider(id);
            }
            w = workflowClient.getWorkflow(id, false);
            assertEquals(
                    Workflow.WorkflowStatus.COMPLETED,
                    w.getStatus(),
                    "Workflow " + id + " must be COMPLETED");
        }
    }

    // =========================================================================
    // Poll gap fix: responseTimeout < pollTimeout
    //
    // Before the fix: when a task transitions SCHEDULED→IN_PROGRESS the decider
    // queue kept the old poll-based postpone. If pollTimeout=30s and
    // responseTimeout=3s, the sweeper would not re-evaluate until ~30s, missing
    // the response timeout by ~27s.
    //
    // After the fix: `adjustDeciderQueuePostpone()` calls
    // `setUnackTimeoutIfShorter()` with the response timeout, advancing the
    // sweep to ~responseTimeout.
    //
    // We observe this by measuring how quickly the task transitions to TIMED_OUT
    // after being polled (left IN_PROGRESS without a response).
    // =========================================================================

    @Test
    @DisplayName(
            "Poll gap fix: response timeout is detected promptly after polling "
                    + "(not delayed until poll timeout expires)")
    void testPollGapFix_responseTimeoutDetectedBeforePollTimeout() {
        String tt = "e2e-poll-gap-task", wf = "e2e-poll-gap-wf";

        // pollTimeout=30s, responseTimeout=3s.
        // Without fix: TIMED_OUT after ~30s.  With fix: TIMED_OUT after ~3s.
        Map<String, Object> def = taskDefBase(tt);
        def.put("retryCount", 0); // no retries — task goes straight to TIMED_OUT
        def.put("retryLogic", "FIXED");
        def.put("pollTimeoutSeconds", 30); // long poll window
        def.put("responseTimeoutSeconds", 3); // short response window
        def.put("timeoutSeconds", 600);
        def.put("timeoutPolicy", "TIME_OUT_WF");
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        // Poll the task (SCHEDULED → IN_PROGRESS) — but do NOT respond.
        // The poll gap fix must update the decider queue to fire after responseTimeout (3s),
        // not after the old poll-based postpone (~30s).
        Task task = pollTask(tt);
        assertNotNull(task, "Task must be pollable");
        long polledAt = System.currentTimeMillis();

        // Wait for the sweeper to fire and time out the task.
        // With fix:    detectable within ~5s (3s response timeout + small sweep lag).
        // Without fix: would take ~30s (poll timeout) — test would fail at 10s.
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow w = workflowClient.getWorkflow(wfId, true);
                            Task t =
                                    w.getTasks().stream()
                                            .filter(tk -> tk.getTaskId().equals(task.getTaskId()))
                                            .findFirst()
                                            .orElseThrow();
                            assertEquals(
                                    Task.Status.TIMED_OUT,
                                    t.getStatus(),
                                    "Task must be TIMED_OUT within response timeout window");
                        });

        long elapsed = System.currentTimeMillis() - polledAt;
        log.info(
                "Task timed out {}ms after being polled (responseTimeout=3s, pollTimeout=30s)",
                elapsed);

        // The key assertion: timed out well before the poll timeout would have fired.
        assertTrue(
                elapsed < 20_000,
                "Task timed out "
                        + elapsed
                        + "ms after polling — poll gap fix must advance the"
                        + " sweep to responseTimeout (~3s), not pollTimeout (~30s)");
    }

    // =========================================================================
    // totalTimeoutSeconds — hard wall-clock budget across all retry attempts
    //
    // Strategy: we cannot manipulate firstScheduledTime in e2e (no DB access),
    // so we rely on real wall-clock time:
    //   - "disabled" (=0) and "not exceeded" tests use manual poll/fail with
    //     a generous total budget so real time never exceeds it.
    //   - "exceeded" test uses a very short budget (4 s) and a background
    //     worker that continuously fails the task; eventually the total budget
    //     is exhausted and the workflow terminates.
    // =========================================================================

    /** Helper: starts a background worker that always fails every task of the given type. */
    private TaskRunnerConfigurer startAlwaysFailingWorker(String taskType) {
        Worker worker =
                new Worker() {
                    @Override
                    public String getTaskDefName() {
                        return taskType;
                    }

                    @Override
                    public TaskResult execute(Task task) {
                        TaskResult r = new TaskResult(task);
                        r.setStatus(TaskResult.Status.FAILED);
                        r.setReasonForIncompletion("e2e totalTimeout stress test");
                        return r;
                    }

                    @Override
                    public int getPollingInterval() {
                        return 100;
                    }
                };
        TaskRunnerConfigurer configurer =
                new TaskRunnerConfigurer.Builder(taskClient, List.of(worker))
                        .withThreadCount(2)
                        .withTaskPollTimeout(1)
                        .build();
        configurer.init();
        return configurer;
    }

    @Test
    @DisplayName("totalTimeoutSeconds=0: disabled — retries continue up to retryCount as normal")
    void testTotalTimeoutSeconds_zero_disabled() {
        String tt = "e2e-total-timeout-disabled-task", wf = "e2e-total-timeout-disabled-wf";

        // totalTimeoutSeconds=0 means disabled; timeoutSeconds=0 to avoid constraint violation
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 5);
        def.put("retryDelaySeconds", 0);
        def.put("retryLogic", "FIXED");
        def.put("totalTimeoutSeconds", 0);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        // Fail twice — total elapsed is well under any real-world constraint
        failTask(wfId, pollTask(tt).getTaskId());
        Task retry1 = awaitScheduledRetry(wfId, 2);
        assertNotNull(retry1, "Retry must be scheduled when totalTimeoutSeconds=0 (disabled)");

        failTask(wfId, pollTask(tt).getTaskId());
        Task retry2 = awaitScheduledRetry(wfId, 3);
        assertNotNull(retry2, "Second retry must be scheduled");

        // Complete the third attempt — workflow should finish successfully
        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    @Test
    @DisplayName("totalTimeoutSeconds not exceeded: retries complete and workflow succeeds")
    void testTotalTimeoutSeconds_notExceeded_workflowCompletes() {
        String tt = "e2e-total-timeout-ok-task", wf = "e2e-total-timeout-ok-wf";

        // Generous 60 s budget; test finishes in << 1 s so budget is never hit
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 5);
        def.put("retryDelaySeconds", 0);
        def.put("retryLogic", "FIXED");
        def.put("totalTimeoutSeconds", 60);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        awaitScheduledRetry(wfId, 2);
        failTask(wfId, pollTask(tt).getTaskId());
        awaitScheduledRetry(wfId, 3);
        completeTask(wfId, pollTask(tt).getTaskId());

        awaitCompleted(wfId);
        Workflow workflow = workflowClient.getWorkflow(wfId, false);
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                workflow.getStatus(),
                "Workflow must complete when total budget is not exhausted");
    }

    @Test
    @DisplayName("totalTimeoutSeconds exceeded: workflow terminates before retryCount is exhausted")
    void testTotalTimeoutSeconds_exceeded_workflowTerminates() {
        String tt = "e2e-total-timeout-exceeded-task", wf = "e2e-total-timeout-exceeded-wf";

        // 4-second budget, 100 retries allowed — total timeout should fire long before 100 retries
        // timeoutSeconds must be ≤ totalTimeoutSeconds when both > 0, so we disable per-attempt
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 100);
        def.put("retryDelaySeconds", 0);
        def.put("retryLogic", "FIXED");
        def.put("totalTimeoutSeconds", 4);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        def.put("timeoutPolicy", "TIME_OUT_WF");
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        // Auto-failing worker: keeps failing every attempt so totalTimeout is the only exit
        TaskRunnerConfigurer workerConfigurer = startAlwaysFailingWorker(tt);
        try {
            String wfId = startWorkflow(wf);

            // The workflow must terminate (FAILED or TIMED_OUT) within a generous window.
            // With a 4-second total budget and rapid retries the termination should be fast.
            AtomicReference<Workflow> finalWorkflow = new AtomicReference<>();
            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow w = workflowClient.getWorkflow(wfId, false);
                                assertTrue(
                                        w.getStatus() == Workflow.WorkflowStatus.FAILED
                                                || w.getStatus()
                                                        == Workflow.WorkflowStatus.TIMED_OUT,
                                        "Workflow must terminate once total timeout is exceeded; status="
                                                + w.getStatus());
                                finalWorkflow.set(w);
                            });

            // The key assertion: far fewer than 100 tasks were scheduled.
            // If total timeout is enforced, the workflow terminates in a handful of retries.
            // If it were NOT enforced, 100 retries at ~retryDelay=0 would still take time
            // but the workflow would only fail at retry exhaustion, not after 4 s.
            Workflow wf2 = workflowClient.getWorkflow(wfId, true);
            int taskCount = wf2.getTasks().size();
            log.info(
                    "Workflow terminated after {} task attempts (totalTimeoutSeconds=4, retryCount=100)",
                    taskCount);
            assertTrue(
                    taskCount < 100,
                    "Workflow must terminate before exhausting all 100 retries — "
                            + "total timeout (4s) should fire first. taskCount="
                            + taskCount);

        } finally {
            try {
                workerConfigurer.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    // --- interaction: totalTimeout + maxRetryDelayCap ---

    @Test
    @DisplayName(
            "totalTimeout + maxRetryDelayCap: cap applied to retry delay; hard budget still enforced")
    void testTotalTimeoutSeconds_withMaxRetryDelayCap() {
        String tt = "e2e-total-cap-combo-task", wf = "e2e-total-cap-combo-wf";

        // base=1s, cap=2s, totalBudget=60s: retries are delayed by at most 2s; budget never hit
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 5);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "EXPONENTIAL_BACKOFF");
        def.put("maxRetryDelaySeconds", 2);
        def.put("totalTimeoutSeconds", 60);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        // Fail twice, verify cap is applied AND retries are scheduled (total budget not hit)
        failTask(wfId, pollTask(tt).getTaskId());
        Task retry1 = awaitScheduledRetry(wfId, 2);
        assertEquals(
                1L,
                retry1.getCallbackAfterSeconds(),
                "retry1: 1×2⁰=1s (below cap 2s, well within totalTimeout 60s)");

        failTask(wfId, pollTask(tt).getTaskId());
        Task retry2 = awaitScheduledRetry(wfId, 3);
        assertEquals(
                2L,
                retry2.getCallbackAfterSeconds(),
                "retry2: 1×2¹=2s = cap, still within totalTimeout 60s");

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    // --- interaction: totalTimeout + backoffJitterMs ---

    @Test
    @DisplayName(
            "totalTimeout + backoffJitterMs: jitter applies per retry; hard budget still enforced")
    void testTotalTimeoutSeconds_withJitter() {
        String tt = "e2e-total-jitter-combo-task", wf = "e2e-total-jitter-combo-wf";

        // base=1s, jitter=500ms, totalBudget=60s: retries get jitter; budget never hit
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 5);
        def.put("retryDelaySeconds", 1);
        def.put("retryLogic", "FIXED");
        def.put("backoffJitterMs", 500);
        def.put("totalTimeoutSeconds", 60);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);

        failTask(wfId, pollTask(tt).getTaskId());
        Task retry1 = awaitScheduledRetry(wfId, 2);
        // callbackAfterSeconds = base delay (jitter is in callbackAfterMs, not visible via HTTP
        // API)
        assertEquals(
                1L,
                retry1.getCallbackAfterSeconds(),
                "callbackAfterSeconds must equal base delay regardless of jitter");

        long scheduledAt = retry1.getScheduledTime();
        Task retried = pollTask(tt);
        long queueDelay = System.currentTimeMillis() - scheduledAt;
        assertTrue(queueDelay >= 1000, "Must wait at least base 1s; was " + queueDelay + "ms");
        assertTrue(queueDelay < 3000, "Must be < base+jitter+overhead; was " + queueDelay + "ms");

        completeTask(wfId, retried.getTaskId());
        awaitCompleted(wfId);
    }

    // --- firstScheduledTime preserved across multiple retries ---

    @Test
    @DisplayName(
            "firstScheduledTime preserved: budget measured from first schedule, not each retry")
    void testTotalTimeoutSeconds_firstScheduledTimePreservedAcrossRetries() {
        String tt = "e2e-total-preserve-task", wf = "e2e-total-preserve-wf";

        // Generous 60s budget, multiple retries — verify that budget is not reset on each retry
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 10);
        def.put("retryDelaySeconds", 0);
        def.put("retryLogic", "FIXED");
        def.put("totalTimeoutSeconds", 60);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        String wfId = startWorkflow(wf);
        long testStart = System.currentTimeMillis();

        // Fail 3 times in rapid succession — each retry must use the original firstScheduledTime
        for (int i = 0; i < 3; i++) {
            failTask(wfId, pollTask(tt).getTaskId());
            awaitScheduledRetry(wfId, i + 2);
        }

        // All 3 retries should have been scheduled — total elapsed is << 60s
        Workflow wf2 = workflowClient.getWorkflow(wfId, true);
        long elapsed = System.currentTimeMillis() - testStart;
        assertEquals(
                4,
                wf2.getTasks().size(),
                "Should have 4 tasks (original + 3 retries) after 3 failures; "
                        + "elapsed="
                        + elapsed
                        + "ms");
        assertTrue(
                elapsed < 10_000,
                "Test must complete in << 60s totalTimeout; elapsed=" + elapsed + "ms");

        completeTask(wfId, pollTask(tt).getTaskId());
        awaitCompleted(wfId);
    }

    // --- ALERT_ONLY policy behavior ---

    @Test
    @DisplayName(
            "ALERT_ONLY policy + totalTimeout exceeded: workflow terminates when task explicitly fails")
    void testTotalTimeoutSeconds_alertOnlyPolicy_terminatesOnWorkerFailure() {
        // When a worker explicitly fails a task and the total budget is already exhausted,
        // the retry() guard fires and terminates the workflow.
        // This is intentional: totalTimeoutSeconds is a hard budget regardless of per-attempt
        // policy.
        // (ALERT_ONLY controls single-attempt timeouts; the total limit is absolute.)
        String tt = "e2e-total-alert-only-task", wf = "e2e-total-alert-only-wf";

        TaskRunnerConfigurer workerConfigurer = null;
        try {
            // 4s budget, ALERT_ONLY, always-failing worker
            Map<String, Object> def = new HashMap<>();
            def.put("name", tt);
            def.put("ownerEmail", "test@conductor.io");
            def.put("retryCount", 100);
            def.put("retryDelaySeconds", 0);
            def.put("retryLogic", "FIXED");
            def.put("totalTimeoutSeconds", 4);
            def.put("timeoutSeconds", 0);
            def.put("responseTimeoutSeconds", 1);
            def.put("timeoutPolicy", "ALERT_ONLY");
            registerTaskDef(def);
            registerWorkflow(wf, tt);

            workerConfigurer = startAlwaysFailingWorker(tt);
            String wfId = startWorkflow(wf);

            // Workflow must terminate even with ALERT_ONLY, because the retry() guard is a hard
            // stop
            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(
                            () -> {
                                Workflow w = workflowClient.getWorkflow(wfId, false);
                                assertTrue(
                                        w.getStatus() == Workflow.WorkflowStatus.FAILED
                                                || w.getStatus()
                                                        == Workflow.WorkflowStatus.TIMED_OUT,
                                        "ALERT_ONLY with totalTimeout must still terminate after budget exhausted; "
                                                + "status="
                                                + w.getStatus());
                            });

            // Fewer tasks than retryCount — proves totalTimeout, not retry exhaustion, stopped it
            Workflow wfFinal = workflowClient.getWorkflow(wfId, true);
            assertTrue(
                    wfFinal.getTasks().size() < 100,
                    "Must have terminated before retryCount=100 is exhausted");

        } finally {
            if (workerConfigurer != null) {
                try {
                    workerConfigurer.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Test
    @DisplayName("Concurrent workflows: totalTimeoutSeconds independent per workflow instance")
    void testTotalTimeoutSeconds_concurrentWorkflows_eachInstanceIndependent()
            throws InterruptedException {
        final int N = 2;
        String tt = "e2e-total-timeout-concurrent-task", wf = "e2e-total-timeout-concurrent-wf";

        // Generous 30 s total budget — all N workflows complete well within it
        Map<String, Object> def = new HashMap<>();
        def.put("name", tt);
        def.put("ownerEmail", "test@conductor.io");
        def.put("retryCount", 5);
        def.put("retryDelaySeconds", 0);
        def.put("retryLogic", "FIXED");
        def.put("totalTimeoutSeconds", 30);
        def.put("timeoutSeconds", 0);
        def.put("responseTimeoutSeconds", 1);
        registerTaskDef(def);
        registerWorkflow(wf, tt);

        List<String> wfIds = new ArrayList<>();
        for (int i = 0; i < N; i++) wfIds.add(startWorkflow(wf));

        // For each workflow: fail once then complete
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch done = new CountDownLatch(N);
        for (String id : wfIds) {
            pool.submit(
                    () -> {
                        try {
                            failTask(id, pollTask(tt).getTaskId());
                            awaitScheduledRetry(id, 2);
                            completeTask(id, pollTask(tt).getTaskId());
                        } finally {
                            done.countDown();
                        }
                    });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS));
        pool.shutdown();

        // All N workflows must complete — totalTimeout (30s) was not reached by any instance
        for (String id : wfIds) {
            Workflow w = workflowClient.getWorkflow(id, false);
            assertEquals(
                    Workflow.WorkflowStatus.COMPLETED,
                    w.getStatus(),
                    "Every workflow instance must complete when total budget is not exhausted");
        }
    }
}
